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
        return try {
            val snap = firestore.collection("users").document(uid).get().await()
            snap.toObject(UserProfile::class.java)
        } catch (_: Exception) {
            null
        }
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
        return try {
            val snap = firestore.collection("public_user_index").document(clean).get().await()
            snap.toObject(PublicUserIndex::class.java)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findUserByPublicId(publicId: String): UserProfile? {
        val index = searchUserByPublicId(publicId) ?: return null
        return getProfile(index.userId)
    }

    // =========================================================================
    // 2. DISCOVERY & 48-HOUR HIDE RULE
    // =========================================================================

    fun observeDiscoveryUsers(myGender: String, myPreference: String): Flow<List<UserProfile>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Determine target gender for visibility
        val targetGenders = when (myGender.trim().lowercase()) {
            "man", "male" -> listOf("Woman", "Female")
            "woman", "female" -> listOf("Man", "Male")
            else -> emptyList()
        }

        val reg = firestore.collection("users")
            .whereEqualTo("profileCompleted", true)
            .limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val allCandidates = snapshot?.documents?.mapNotNull { it.toObject(UserProfile::class.java) }
                    ?.filter { it.userId != myUid && it.accountStatus == "active" }
                    ?: emptyList()

                // Filter by target gender if specified
                val genderFiltered = if (targetGenders.isNotEmpty()) {
                    allCandidates.filter { candidate ->
                        targetGenders.any { it.equals(candidate.gender, ignoreCase = true) }
                    }
                } else {
                    allCandidates
                }

                // Check active 48-hour rejections, blocks, and mutual matches asynchronously
                firestore.collection("rejections")
                    .whereEqualTo("rejectorUid", myUid)
                    .get()
                    .addOnSuccessListener { rejSnap ->
                        val now = Timestamp.now()
                        val activeRejectedUids = rejSnap.documents.mapNotNull { doc ->
                            val rej = doc.toObject(Rejection::class.java)
                            if (rej != null && rej.expiresAt != null && rej.expiresAt.seconds > now.seconds) {
                                rej.rejectedUid
                            } else null
                        }.toSet()

                        firestore.collection("blocks")
                            .whereEqualTo("blockerUid", myUid)
                            .get()
                            .addOnSuccessListener { blockSnap ->
                                val blockedUids = blockSnap.documents.mapNotNull {
                                    it.getString("blockedUid")
                                }.toSet()

                                firestore.collection("matches")
                                    .whereArrayContains("users", myUid)
                                    .get()
                                    .addOnSuccessListener { matchSnap ->
                                        val matchedUids = matchSnap.documents.mapNotNull { doc ->
                                            val m = doc.toObject(Match::class.java)
                                            if (m != null) {
                                                if (m.user1Id == myUid) m.user2Id else m.user1Id
                                            } else null
                                        }.toSet()

                                        val eligible = genderFiltered.filter { c ->
                                            c.userId !in activeRejectedUids &&
                                            c.userId !in blockedUids &&
                                            c.userId !in matchedUids
                                        }
                                        trySend(eligible)
                                    }
                                    .addOnFailureListener {
                                        val eligible = genderFiltered.filter { c ->
                                            c.userId !in activeRejectedUids && c.userId !in blockedUids
                                        }
                                        trySend(eligible)
                                    }
                            }
                            .addOnFailureListener {
                                val eligible = genderFiltered.filter { c -> c.userId !in activeRejectedUids }
                                trySend(eligible)
                            }
                    }
                    .addOnFailureListener {
                        trySend(genderFiltered)
                    }
            }
        awaitClose { reg.remove() }
    }

    /**
     * Saves a 48-hour rejection record for the target profile.
     * The profile is hidden from Discover for exactly 48 hours, then automatically eligible again.
     */
    suspend fun rejectUser(targetUid: String): Result<Unit> {
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))

            val docId = "${myUid}_$targetUid"
            val nowMillis = System.currentTimeMillis()
            val expiryMillis = nowMillis + (48L * 60L * 60L * 1000L) // Exactly 48 hours

            val rejection = Rejection(
                id = docId,
                rejectorUid = myUid,
                rejectedUid = targetUid,
                rejectedAt = Timestamp.now(),
                expiresAt = Timestamp(Date(expiryMillis))
            )
            firestore.collection("rejections").document(docId).set(rejection).await()

            // Also check if there was a pending match request from targetUid and mark it rejected
            val pendingReqId = "${targetUid}_$myUid"
            firestore.collection("match_requests").document(pendingReqId)
                .update(
                    mapOf(
                        "status" to "rejected",
                        "updatedAt" to Timestamp.now()
                    )
                )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. MATCH REQUEST & MUTUAL MATCH WORKFLOW
    // =========================================================================

    /**
     * Initiates a Match/Interest request.
     * If targetUid has already sent a pending request to myUid, this creates a MUTUAL MATCH!
     */
    suspend fun sendLike(targetUid: String, type: String = "like"): Result<Boolean> {
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))

            val myProfile = getProfile(myUid) ?: return Result.failure(Exception("My profile not found"))
            val targetProfile = getProfile(targetUid) ?: return Result.failure(Exception("Target profile not found"))

            // Check if target has an existing pending request or like for me
            val reverseReqId = "${targetUid}_$myUid"
            val reverseReqSnap = firestore.collection("match_requests").document(reverseReqId).get().await()
            val reverseLikeSnap = firestore.collection("likes").document(reverseReqId).get().await()

            val isMutual = (reverseReqSnap.exists() && reverseReqSnap.getString("status") == "pending") ||
                           reverseLikeSnap.exists()

            if (isMutual) {
                // CONFIRMED MUTUAL MATCH
                val matchId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
                val match = Match(
                    id = matchId,
                    user1Id = myUid,
                    user2Id = targetUid,
                    user1Name = myProfile.displayName,
                    user2Name = targetProfile.displayName,
                    user1Photo = myProfile.profilePhotoUrls.firstOrNull() ?: "",
                    user2Photo = targetProfile.profilePhotoUrls.firstOrNull() ?: "",
                    users = listOf(myUid, targetUid),
                    lastMessage = "It's a match! Say hello 🎉",
                    lastMessageSenderId = "system",
                    lastMessageTimestamp = Timestamp.now(),
                    createdAt = Timestamp.now()
                )
                firestore.collection("matches").document(matchId).set(match).await()

                // Update request status to accepted
                firestore.collection("match_requests").document(reverseReqId).update(
                    mapOf("status" to "accepted", "updatedAt" to Timestamp.now())
                )

                // Send mutual match notification to both
                createNotification(
                    targetUid = targetUid,
                    title = "New Match!",
                    message = "${myProfile.displayName} matched with you! Start a conversation now.",
                    type = "match_accepted",
                    targetId = matchId,
                    senderName = myProfile.displayName,
                    senderPhotoUrl = myProfile.profilePhotoUrls.firstOrNull() ?: ""
                )
                createNotification(
                    targetUid = myUid,
                    title = "New Match!",
                    message = "You matched with ${targetProfile.displayName}! Start chatting now.",
                    type = "match_accepted",
                    targetId = matchId,
                    senderName = targetProfile.displayName,
                    senderPhotoUrl = targetProfile.profilePhotoUrls.firstOrNull() ?: ""
                )

                Result.success(true)
            } else {
                // Step 2: Save match/interest request with full sender info
                val reqId = "${myUid}_$targetUid"
                val matchRequest = MatchRequest(
                    id = reqId,
                    senderId = myUid,
                    receiverId = targetUid,
                    senderName = myProfile.displayName,
                    senderPhotoUrl = myProfile.profilePhotoUrls.firstOrNull() ?: "",
                    senderAge = myProfile.age,
                    senderCity = myProfile.city,
                    senderBio = myProfile.bio,
                    status = "pending",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                firestore.collection("match_requests").document(reqId).set(matchRequest).await()

                // Also save like
                val like = Like(
                    id = reqId,
                    fromUserId = myUid,
                    toUserId = targetUid,
                    type = type,
                    createdAt = Timestamp.now()
                )
                firestore.collection("likes").document(reqId).set(like).await()

                // Step 3: Send push notification to target user showing who initiated the match
                createNotification(
                    targetUid = targetUid,
                    title = "Someone wants to match with you!",
                    message = "${myProfile.displayName} sent you a match request!",
                    type = "match_request",
                    targetId = myUid,
                    senderName = myProfile.displayName,
                    senderPhotoUrl = myProfile.profilePhotoUrls.firstOrNull() ?: ""
                )

                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptMatchRequest(senderUid: String): Result<Match> {
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))

            val myProfile = getProfile(myUid) ?: return Result.failure(Exception("Profile not found"))
            val senderProfile = getProfile(senderUid) ?: return Result.failure(Exception("Sender profile not found"))

            val matchId = if (myUid < senderUid) "${myUid}_$senderUid" else "${senderUid}_$myUid"
            val match = Match(
                id = matchId,
                user1Id = myUid,
                user2Id = senderUid,
                user1Name = myProfile.displayName,
                user2Name = senderProfile.displayName,
                user1Photo = myProfile.profilePhotoUrls.firstOrNull() ?: "",
                user2Photo = senderProfile.profilePhotoUrls.firstOrNull() ?: "",
                users = listOf(myUid, senderUid),
                lastMessage = "It's a match! Say hello 🎉",
                lastMessageSenderId = "system",
                lastMessageTimestamp = Timestamp.now(),
                createdAt = Timestamp.now()
            )
            firestore.collection("matches").document(matchId).set(match).await()

            // Update request status
            val reqId = "${senderUid}_$myUid"
            firestore.collection("match_requests").document(reqId).update(
                mapOf("status" to "accepted", "updatedAt" to Timestamp.now())
            )

            // Notify sender
            createNotification(
                targetUid = senderUid,
                title = "Match Accepted!",
                message = "${myProfile.displayName} accepted your match request! Say hello 🎉",
                type = "match_accepted",
                targetId = matchId,
                senderName = myProfile.displayName,
                senderPhotoUrl = myProfile.profilePhotoUrls.firstOrNull() ?: ""
            )

            Result.success(match)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 4. OBSERVING MATCHES & CONVERSATIONS
    // =========================================================================

    fun observeMatches(): Flow<List<Match>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("matches")
            .whereArrayContains("users", myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback to fetch all and filter client side
                    firestore.collection("matches").get()
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(Match::class.java) }
                                .filter { it.user1Id == myUid || it.user2Id == myUid || it.users.contains(myUid) }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(Match::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    // =========================================================================
    // 5. CHAT MESSAGING WITH DELIVERY & SEEN STATUS
    // =========================================================================

    fun observeMessages(matchId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = firestore.collection("matches").document(matchId)
            .collection("messages")
            .orderBy("localTimestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback query without orderBy if index is absent
                    firestore.collection("matches").document(matchId)
                        .collection("messages")
                        .get()
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                                .sortedBy { it.localTimestamp }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
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

    suspend fun markMessageAsDelivered(matchId: String, messageId: String) {
        try {
            firestore.collection("matches").document(matchId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "status" to "delivered",
                        "deliveredAt" to Timestamp.now()
                    )
                ).await()
        } catch (_: Exception) {}
    }

    suspend fun markMessageAsSeen(matchId: String, messageId: String) {
        try {
            firestore.collection("matches").document(matchId)
                .collection("messages").document(messageId)
                .update(
                    mapOf(
                        "status" to "seen",
                        "seenAt" to Timestamp.now()
                    )
                ).await()
        } catch (_: Exception) {}
    }

    suspend fun markAllMessagesAsSeen(matchId: String) {
        try {
            val myUid = currentUid
            val snap = firestore.collection("matches").document(matchId)
                .collection("messages")
                .whereNotEqualTo("senderId", myUid)
                .limit(30)
                .get().await()

            for (doc in snap.documents) {
                if (doc.getString("status") != "seen") {
                    doc.reference.update(mapOf("status" to "seen", "seenAt" to Timestamp.now()))
                }
            }
        } catch (_: Exception) {}
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

    // =========================================================================
    // 6. ROBUST NOTIFICATIONS (ZERO CRASH & ALL TYPES)
    // =========================================================================

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
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Fallback to fetch without order to avoid index or timestamp crashes
                    firestore.collection("users").document(myUid)
                        .collection("notifications")
                        .limit(50)
                        .get()
                        .addOnSuccessListener { fallbackSnap ->
                            val safeList = fallbackSnap.documents.mapNotNull { doc ->
                                try {
                                    val item = doc.toObject(NotificationItem::class.java)
                                    item?.copy(id = if (item.id.isNotEmpty()) item.id else doc.id)
                                } catch (_: Exception) { null }
                            }.sortedByDescending { it.timestamp?.seconds ?: 0L }
                            trySend(safeList)
                        }
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(NotificationItem::class.java)
                        item?.copy(id = if (item.id.isNotEmpty()) item.id else doc.id)
                    } catch (_: Exception) { null }
                } ?: emptyList()

                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun createNotification(
        targetUid: String,
        title: String,
        message: String,
        type: String,
        targetId: String,
        senderName: String = "",
        senderPhotoUrl: String = ""
    ) {
        try {
            val docRef = firestore.collection("users").document(targetUid).collection("notifications").document()
            val notif = NotificationItem(
                id = docRef.id,
                userId = targetUid,
                title = title,
                message = message,
                type = type,
                targetId = targetId,
                senderName = senderName,
                senderPhotoUrl = senderPhotoUrl,
                read = false,
                createdAt = Timestamp.now()
            )
            docRef.set(notif).await()
        } catch (_: Exception) {}
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        try {
            val myUid = currentUid
            if (myUid.isNotEmpty() && notificationId.isNotEmpty()) {
                firestore.collection("users").document(myUid)
                    .collection("notifications").document(notificationId)
                    .update("read", true).await()
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 7. CALL SESSIONS (AGORA RTC)
    // =========================================================================

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

            // Also create incoming call notification for receiver
            createNotification(
                targetUid = targetUid,
                title = if (isVideo) "Incoming Video Call" else "Incoming Voice Call",
                message = "${myProfile?.displayName ?: "Someone"} is calling you...",
                type = if (isVideo) "video_call" else "voice_call",
                targetId = docRef.id,
                senderName = myProfile?.displayName ?: "Caller",
                senderPhotoUrl = myProfile?.profilePhotoUrls?.firstOrNull() ?: ""
            )

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

    fun observeCallHistory(): Flow<List<CallSession>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("calls")
            .whereEqualTo("callerUid", myUid)
            .limit(30)
            .addSnapshotListener { snap1, _ ->
                val list1 = snap1?.documents?.mapNotNull { it.toObject(CallSession::class.java) } ?: emptyList()
                firestore.collection("calls")
                    .whereEqualTo("receiverUid", myUid)
                    .limit(30)
                    .get()
                    .addOnSuccessListener { snap2 ->
                        val list2 = snap2.documents.mapNotNull { it.toObject(CallSession::class.java) }
                        val combined = (list1 + list2).distinctBy { it.callId }
                            .sortedByDescending { it.createdAt?.seconds ?: 0L }
                        trySend(combined)
                    }
                    .addOnFailureListener {
                        trySend(list1)
                    }
            }
        awaitClose { reg.remove() }
    }

    // =========================================================================
    // 8. BLOCKS, REPORTS & ACCOUNT DELETION
    // =========================================================================

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

    fun observeBlockedUserIds(): Flow<List<String>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("blocks")
            .whereEqualTo("blockerUid", myUid)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.getString("blockedUid") } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        return try {
            val id = "${currentUid}_$targetUid"
            firestore.collection("blocks").document(id).delete().await()
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

    suspend fun submitReport(reportedUid: String, reportedName: String, reason: String, details: String): Result<Unit> =
        reportUser(reportedUid, reason, details)

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

    suspend fun deleteCurrentUserAccount(): Result<Unit> = deleteAccount()

    // =========================================================================
    // 9. FCM TOKEN MANAGEMENT
    // =========================================================================

    suspend fun registerFcmToken(token: String) {
        val uid = currentUid
        if (uid.isEmpty() || token.isEmpty()) return
        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "fcmTokens" to FieldValue.arrayUnion(token),
                    "lastActiveAt" to Timestamp.now()
                )
            ).await()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 10. ADMIN DASHBOARD & MANAGEMENT (AUTHORIZED ONLY)
    // =========================================================================

    suspend fun checkIsAdmin(uid: String = currentUid): Boolean {
        if (uid.isEmpty()) return false
        val profile = getProfile(uid) ?: return false
        return profile.role == "admin"
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = callbackFlow {
        val reg = firestore.collection("users")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    firestore.collection("users").limit(100).get()
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(UserProfile::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateAccountStatusByAdmin(targetUid: String, newStatus: String, reason: String): Result<Unit> {
        return try {
            if (!checkIsAdmin()) return Result.failure(Exception("Unauthorized. Admin access required."))
            firestore.collection("users").document(targetUid).update(
                mapOf(
                    "accountStatus" to newStatus,
                    "updatedAt" to Timestamp.now()
                )
            ).await()

            recordAdminAudit(
                action = "UPDATE_STATUS_$newStatus",
                targetUserId = targetUid,
                details = reason
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAdminCustomMessage(targetUid: String, title: String, messageText: String): Result<Unit> {
        return try {
            if (!checkIsAdmin()) return Result.failure(Exception("Unauthorized. Admin access required."))

            createNotification(
                targetUid = targetUid,
                title = if (title.isNotBlank()) title else "Official Bondly Announcement",
                message = messageText,
                type = "admin_announcement",
                targetId = "admin_announcement",
                senderName = "Bondly Official",
                senderPhotoUrl = ""
            )

            recordAdminAudit(
                action = "SEND_CUSTOM_MESSAGE",
                targetUserId = targetUid,
                details = "Title: $title | Message: $messageText"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordAdminAudit(action: String, targetUserId: String, details: String) {
        try {
            val docRef = firestore.collection("admin_audit_logs").document()
            val log = AdminAuditLog(
                logId = docRef.id,
                adminUid = currentUid,
                adminEmail = currentUser?.email ?: "",
                action = action,
                targetUserId = targetUserId,
                details = details,
                createdAt = Timestamp.now()
            )
            docRef.set(log).await()
        } catch (_: Exception) {}
    }

    fun observeAdminAuditLogs(): Flow<List<AdminAuditLog>> = callbackFlow {
        val reg = firestore.collection("admin_audit_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(AdminAuditLog::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeModerationReports(): Flow<List<Report>> = callbackFlow {
        val reg = firestore.collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(Report::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun dismissReport(reportId: String): Result<Unit> {
        return try {
            firestore.collection("reports").document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun banUser(targetUid: String, reportId: String): Result<Unit> {
        return try {
            firestore.collection("users").document(targetUid).update("accountStatus", "banned").await()
            dismissReport(reportId)
            recordAdminAudit("BAN_USER", targetUid, "Banned via report: $reportId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // 11. USER SESSION ANALYTICS
    // =========================================================================

    suspend fun logUserSession(durationSeconds: Long) {
        val uid = currentUid
        if (uid.isEmpty() || durationSeconds < 1) return
        try {
            val userProfile = getProfile(uid)
            val docRef = firestore.collection("user_sessions").document()
            val now = Timestamp.now()
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val session = UserSession(
                sessionId = docRef.id,
                userId = uid,
                userGender = userProfile?.gender ?: "Unknown",
                startedAt = Timestamp(Date(System.currentTimeMillis() - durationSeconds * 1000L)),
                endedAt = now,
                durationSeconds = durationSeconds,
                dateString = dateStr
            )
            docRef.set(session).await()
        } catch (_: Exception) {}
    }

    fun observeUserSessions(): Flow<List<UserSession>> = callbackFlow {
        val reg = firestore.collection("user_sessions")
            .orderBy("endedAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(UserSession::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun signOut() {
        auth.signOut()
    }
}
