package com.example.data.repository

import com.example.data.model.*
import com.example.util.UserLevelManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class BondlyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUid: String
        get() = currentUser?.uid ?: ""

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun getProfile(uid: String = currentUid): UserProfile? {
        if (uid.isEmpty()) return null
        val snap = firestore.collection("users").document(uid).get().await()
        return snap.toObject(UserProfile::class.java)
    }

    fun observeProfile(uid: String = currentUid): Flow<UserProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        return try {
            val uid = profile.userId.ifEmpty { currentUid }
            if (uid.isEmpty()) return Result.failure(Exception("Not logged in"))
            
            val level = UserLevelManager.calculateLevel(profile)
            val updated = profile.copy(
                userId = uid,
                userLevel = level,
                updatedAt = Timestamp.now()
            )
            firestore.collection("users").document(uid).set(updated).await()

            // Update public search index if publicUserId exists
            if (updated.publicUserId.isNotEmpty()) {
                val indexDoc = PublicUserIndex(
                    publicUserId = updated.publicUserId,
                    userId = uid,
                    displayName = updated.displayName,
                    city = updated.city,
                    gender = updated.gender,
                    age = updated.age,
                    photoUrl = updated.profilePhotoUrls.firstOrNull() ?: ""
                )
                firestore.collection("public_user_index").document(updated.publicUserId).set(indexDoc).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun allocateUniquePublicId(uid: String): String {
        var attempts = 0
        while (attempts < 5) {
            val candidate = "BND-${Random.nextInt(100000, 999999)}"
            val snap = firestore.collection("public_user_index").document(candidate).get().await()
            if (!snap.exists()) {
                return candidate
            }
            attempts++
        }
        return "BND-${System.currentTimeMillis() % 1000000}"
    }

    suspend fun searchUserByPublicId(publicId: String): PublicUserIndex? {
        val clean = publicId.trim().uppercase()
        val snap = firestore.collection("public_user_index").document(clean).get().await()
        return snap.toObject(PublicUserIndex::class.java)
    }

    fun observeDiscoveryUsers(myGender: String, myPreference: String): Flow<List<UserProfile>> = callbackFlow {
        val reg = firestore.collection("users")
            .whereEqualTo("profileCompleted", true)
            .limit(40)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(UserProfile::class.java) }
                    ?.filter { it.userId != currentUid }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendLike(targetUid: String, type: String = "like"): Result<Boolean> {
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))

            // 1. Save like record
            val likeId = "${myUid}_$targetUid"
            val like = Like(
                id = likeId,
                fromUserId = myUid,
                toUserId = targetUid,
                type = type,
                createdAt = Timestamp.now()
            )
            firestore.collection("likes").document(likeId).set(like).await()

            // 2. Check if mutual like exists
            val reciprocalLikeId = "${targetUid}_$myUid"
            val recipSnap = firestore.collection("likes").document(reciprocalLikeId).get().await()

            var isMatch = false
            if (recipSnap.exists()) {
                isMatch = true
                val matchId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                val myProfile = getProfile(myUid)
                val targetProfile = getProfile(targetUid)

                val match = Match(
                    id = matchId,
                    user1Id = myUid,
                    user2Id = targetUid,
                    user1Name = myProfile?.displayName ?: "Bondly User",
                    user2Name = targetProfile?.displayName ?: "Bondly User",
                    user1Photo = myProfile?.profilePhotoUrls?.firstOrNull() ?: "",
                    user2Photo = targetProfile?.profilePhotoUrls?.firstOrNull() ?: "",
                    lastMessage = "It's a match! Say hello 🎉",
                    lastMessageSenderId = "system",
                    lastMessageTimestamp = Timestamp.now(),
                    createdAt = Timestamp.now()
                )
                firestore.collection("matches").document(matchId).set(match).await()

                createNotification(targetUid, "New Match!", "${myProfile?.displayName ?: "Someone"} matched with you!", "match", matchId)
            } else {
                val myProfile = getProfile(myUid)
                createNotification(targetUid, "New Interest", "${myProfile?.displayName ?: "Someone"} showed interest in you!", "like", myUid)
            }

            Result.success(isMatch)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeMatches(): Flow<List<Match>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("matches")
            .whereArrayContainsAny("users", listOf(myUid))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback to client filter if index is not ready
                    firestore.collection("matches")
                        .addSnapshotListener { snap2, _ ->
                            val list = snap2?.documents?.mapNotNull { it.toObject(Match::class.java) }
                                ?.filter { it.user1Id == myUid || it.user2Id == myUid }
                                ?: emptyList()
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Match::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = firestore.collection("matches").document(matchId)
            .collection("messages")
            .orderBy("localTimestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) }
                    ?.filter { !it.deletedFor.contains(currentUid) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(matchId: String, text: String, imageUrl: String = ""): Result<Unit> {
        return try {
            val myUid = currentUid
            val docRef = firestore.collection("matches").document(matchId).collection("messages").document()
            val msg = ChatMessage(
                id = docRef.id,
                matchId = matchId,
                senderId = myUid,
                text = text,
                imageUrl = imageUrl,
                status = "sent",
                localTimestamp = System.currentTimeMillis(),
                createdAt = Timestamp.now()
            )
            docRef.set(msg).await()

            // Update match last message
            firestore.collection("matches").document(matchId).update(
                mapOf(
                    "lastMessage" to if (text.isNotEmpty()) text else "📷 Photo",
                    "lastMessageSenderId" to myUid,
                    "lastMessageTimestamp" to Timestamp.now()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(matchId: String, messageId: String): Result<Unit> {
        return try {
            firestore.collection("matches").document(matchId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "deletedForEveryone" to true,
                        "text" to "This message was deleted",
                        "deletedAt" to Timestamp.now()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(matchId: String, messageId: String): Result<Unit> {
        return try {
            firestore.collection("matches").document(matchId)
                .collection("messages").document(messageId)
                .update("deletedFor", FieldValue.arrayUnion(currentUid))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUid: String): Result<Unit> {
        return try {
            val id = "${currentUid}_$targetUid"
            val block = Block(id = id, blockerUid = currentUid, blockedUid = targetUid, createdAt = Timestamp.now())
            firestore.collection("blocks").document(id).set(block).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(targetUid: String, reason: String, details: String): Result<Unit> {
        return try {
            val docRef = firestore.collection("reports").document()
            val report = Report(
                id = docRef.id,
                reporterUid = currentUid,
                reportedUid = targetUid,
                reason = reason,
                details = details,
                createdAt = Timestamp.now()
            )
            docRef.set(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeNotifications(): Flow<List<NotificationItem>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("users").document(myUid)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(NotificationItem::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createNotification(targetUid: String, title: String, message: String, type: String, targetId: String) {
        try {
            val docRef = firestore.collection("users").document(targetUid).collection("notifications").document()
            val notif = NotificationItem(
                id = docRef.id,
                userId = targetUid,
                title = title,
                message = message,
                type = type,
                targetId = targetId,
                read = false,
                createdAt = Timestamp.now()
            )
            docRef.set(notif).await()
        } catch (_: Exception) {}
    }

    suspend fun initiateCall(targetUid: String, isVideo: Boolean): Result<CallSession> {
        return try {
            val myProfile = getProfile(currentUid)
            val targetProfile = getProfile(targetUid)
            val docRef = firestore.collection("calls").document()
            val session = CallSession(
                callId = docRef.id,
                callerUid = currentUid,
                receiverUid = targetUid,
                callerName = myProfile?.displayName ?: "Caller",
                callerPhotoUrl = myProfile?.profilePhotoUrls?.firstOrNull() ?: "",
                receiverName = targetProfile?.displayName ?: "Receiver",
                channelName = "bondly_${docRef.id}",
                callType = if (isVideo) "video" else "voice",
                status = "ringing",
                createdAt = Timestamp.now()
            )
            docRef.set(session).await()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeIncomingCalls(): Flow<CallSession?> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("calls")
            .whereEqualTo("receiverUid", myUid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, _ ->
                val call = snapshot?.documents?.mapNotNull { it.toObject(CallSession::class.java) }?.firstOrNull()
                trySend(call)
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateCallStatus(callId: String, status: String): Result<Unit> {
        return try {
            firestore.collection("calls").document(callId).update(
                mapOf(
                    "status" to status,
                    "endedAt" to if (status in listOf("ended", "rejected", "missed")) Timestamp.now() else null
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val uid = currentUid
            if (uid.isNotEmpty()) {
                val profile = getProfile(uid)
                if (profile?.publicUserId?.isNotEmpty() == true) {
                    firestore.collection("public_user_index").document(profile.publicUserId).delete().await()
                }
                firestore.collection("users").document(uid).delete().await()
            }
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
