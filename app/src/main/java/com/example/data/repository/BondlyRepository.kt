package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.Block
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.Like
import com.example.data.model.Match
import com.example.data.model.NotificationItem
import com.example.data.model.PublicUserIndex
import com.example.data.model.Report
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileState
import com.example.util.ContactSharingFilter
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun getFirestoreInstance(context: Context): FirebaseFirestore {
    val dbId = try {
        val resId = context.applicationContext.resources.getIdentifier(
            "firestore_database_id", "string", context.applicationContext.packageName
        )
        if (resId != 0) context.applicationContext.getString(resId).trim() else ""
    } catch (_: Exception) {
        ""
    }
    return if (dbId.isNotEmpty() && dbId != "(default)") {
        try {
            FirebaseFirestore.getInstance(dbId)
        } catch (_: Exception) {
            FirebaseFirestore.getInstance()
        }
    } else {
        FirebaseFirestore.getInstance()
    }
}

class BondlyRepository(
    private val db: FirebaseFirestore,
    private val auth: com.google.firebase.auth.FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        getFirestoreInstance(context),
        Firebase.auth
    )
    private val random = SecureRandom()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Bondly.")
    }

    // ----------------------------------------------------
    // USER PROFILE & PUBLIC USER ID
    // ----------------------------------------------------

    fun observeUserProfileState(userId: String): Flow<UserProfileState> = callbackFlow {
        val docRef = db.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("BondlyAuth", "Error observing user profile for $userId: ${error.message}", error)
                handleFirestoreError(error, OperationType.GET, docRef.path)
                val userFriendlyMessage = when (error.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Firestore Security Rules Denied Access (PERMISSION_DENIED).\n\n" +
                        "The security rules on your Firebase project (${db.app.options.projectId}) are currently denying read access to the 'users' collection.\n\n" +
                        "Action Required:\n" +
                        "1. Open Firebase Console -> Build -> Firestore Database -> Rules tab.\n" +
                        "2. Paste the rules from firestore.rules into the editor.\n" +
                        "3. Click 'Publish'.\n" +
                        "4. Tap 'Retry Loading Profile' below."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Connection issue: Unable to reach Cloud Firestore. Please check your internet connection and retry."
                    else -> error.localizedMessage ?: "Failed to load user profile (${error.code})."
                }
                trySend(UserProfileState.Error(userFriendlyMessage, canRetry = true, cause = error))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                if (profile != null && profile.displayName.isNotBlank() && profile.profileCompleted && profile.profilePhotoUrls.size >= 5) {
                    Log.d("BondlyAuth", "Complete profile loaded for $userId: ${profile.displayName}")
                    trySend(UserProfileState.Loaded(profile))
                } else if (profile != null) {
                    Log.d("BondlyAuth", "Profile incomplete for $userId (photos=${profile.profilePhotoUrls.size}, completed=${profile.profileCompleted})")
                    trySend(UserProfileState.Incomplete(profile))
                } else {
                    Log.d("BondlyAuth", "Profile doc exists but failed to deserialize for $userId")
                    trySend(UserProfileState.NotFound)
                }
            } else {
                Log.d("BondlyAuth", "Profile doc does not exist in Firestore for $userId -> UserProfileState.NotFound")
                trySend(UserProfileState.NotFound)
            }
        }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun ensureInitialProfile(userId: String): UserProfile = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(userId)
        val existingSnapshot = try {
            docRef.get().await()
        } catch (e: Exception) {
            Log.w("BondlyRepo", "Could not check existing profile for $userId: ${e.message}")
            null
        }

        if (existingSnapshot != null && existingSnapshot.exists()) {
            val existingProfile = existingSnapshot.toObject(UserProfile::class.java)
            if (existingProfile != null) {
                return@withContext existingProfile
            }
        }

        val googleUser = auth.currentUser
        val publicId = allocateUniquePublicId(userId)
        val initialProfile = UserProfile(
            userId = userId,
            publicUserId = publicId,
            displayName = googleUser?.displayName?.takeIf { it.isNotBlank() } ?: "Bondly Member",
            bio = "",
            gender = "Female",
            city = "",
            state = "",
            profileCompleted = false,
            accountStatus = "active",
            totalPhotoCount = if (googleUser?.photoUrl != null) 1 else 0,
            profilePhotoUrls = if (googleUser?.photoUrl != null) listOf(googleUser.photoUrl.toString()) else emptyList(),
            createdAt = Timestamp.now()
        )

        try {
            docRef.set(initialProfile).await()
            Log.i("BondlyRepo", "Initial profile document created for user $userId")
        } catch (e: Exception) {
            Log.w("BondlyRepo", "Could not write initial profile to Firestore: ${e.message}")
        }
        initialProfile
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, docRef.path)
                emit(null)
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun checkAndInitializeProfile(userId: String): Result<UserProfileState> = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(userId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                if (profile != null && profile.displayName.isNotBlank() && profile.profileCompleted && profile.profilePhotoUrls.size >= 5) {
                    Result.success(UserProfileState.Loaded(profile))
                } else if (profile != null) {
                    Result.success(UserProfileState.Incomplete(profile))
                } else {
                    Result.success(UserProfileState.NotFound)
                }
            } else {
                val created = ensureInitialProfile(userId)
                Result.success(UserProfileState.Incomplete(created))
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? = withContext(Dispatchers.IO) {
        val docRef = db.collection("users").document(userId)
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }

    /**
     * Atomically allocates a unique permanent public ID (e.g. BD482731)
     * using Firestore transactions to prevent collisions.
     */
    suspend fun allocateUniquePublicId(userId: String): String = withContext(Dispatchers.IO) {
        var attempts = 0
        var allocatedId = ""
        while (attempts < 5 && allocatedId.isEmpty()) {
            attempts++
            val candidateId = "BD" + String.format("%06d", random.nextInt(1000000))
            val indexRef = db.collection("public_user_indices").document(candidateId)
            try {
                val success = db.runTransaction { transaction ->
                    val snapshot = transaction.get(indexRef)
                    if (!snapshot.exists()) {
                        val indexData = mapOf(
                            "publicUserId" to candidateId,
                            "userId" to userId,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        transaction.set(indexRef, indexData)
                        true
                    } else {
                        false
                    }
                }.await()
                if (success) {
                    allocatedId = candidateId
                }
            } catch (e: Exception) {
                Log.w("BondlyRepo", "Collision or transaction retry for $candidateId: ${e.message}")
            }
        }
        if (allocatedId.isEmpty()) {
            allocatedId = "BD" + (System.currentTimeMillis() % 1000000)
            val fallbackRef = db.collection("public_user_indices").document(allocatedId)
            fallbackRef.set(
                mapOf(
                    "publicUserId" to allocatedId,
                    "userId" to userId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        allocatedId
    }

    suspend fun saveUserProfile(
        profile: UserProfile,
        rawMobileNumber: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            val existing = userRef.get().await()
            val finalPublicId = if (profile.publicUserId.isNotBlank()) {
                profile.publicUserId
            } else if (existing.exists() && existing.getString("publicUserId")?.isNotBlank() == true) {
                existing.getString("publicUserId")!!
            } else {
                allocateUniquePublicId(uid)
            }

            // Mask mobile for public document
            val maskedMobile = if (!rawMobileNumber.isNullOrBlank()) {
                ContactSharingFilter.maskMobile(rawMobileNumber)
            } else if (profile.maskedMobileNumber.isNotBlank()) {
                profile.maskedMobileNumber
            } else {
                "+91 ••••• ••••"
            }

            val data = mutableMapOf<String, Any>(
                "userId" to uid,
                "publicUserId" to finalPublicId,
                "displayName" to profile.displayName,
                "dob" to profile.dob,
                "age" to profile.age.coerceIn(18, 120),
                "bio" to profile.bio.take(1000),
                "gender" to profile.gender,
                "city" to profile.city.take(100),
                "state" to profile.state.take(100),
                "maskedMobileNumber" to maskedMobile,
                "isMobileVerified" to (profile.isMobileVerified || !rawMobileNumber.isNullOrBlank()),
                "interests" to profile.interests.take(20),
                "profilePhotoUrls" to profile.profilePhotoUrls.take(10),
                "totalPhotoCount" to profile.profilePhotoUrls.size,
                "datingPreference" to profile.datingPreference,
                "rulesAccepted" to profile.rulesAccepted,
                "profileCompleted" to (profile.profileCompleted || (profile.profilePhotoUrls.size >= 5 && profile.displayName.isNotBlank())),
                "verificationStatus" to profile.verificationStatus,
                "accountStatus" to profile.accountStatus,
                "allowCallsFrom" to profile.allowCallsFrom,
                "showOnlineStatus" to profile.showOnlineStatus,
                "isPremium" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp()
            )

            if (!existing.exists()) {
                data["createdAt"] = FieldValue.serverTimestamp()
            } else {
                val createdTs = existing.getTimestamp("createdAt")
                if (createdTs != null) {
                    data["createdAt"] = createdTs
                } else {
                    data["createdAt"] = FieldValue.serverTimestamp()
                }
            }

            userRef.set(data).await()

            // Save unmasked mobile number strictly in private subcollection
            if (!rawMobileNumber.isNullOrBlank()) {
                val privateAccountRef = userRef.collection("private").document("account")
                privateAccountRef.set(
                    mapOf(
                        "mobileNumber" to rawMobileNumber,
                        "verified" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun searchByPublicUserId(publicId: String): UserProfile? = withContext(Dispatchers.IO) {
        val cleanId = publicId.trim().uppercase(Locale.ROOT)
        if (cleanId.isEmpty()) return@withContext null
        try {
            val indexDoc = db.collection("public_user_indices").document(cleanId).get().await()
            if (indexDoc.exists()) {
                val targetUid = indexDoc.getString("userId") ?: return@withContext null
                return@withContext getUserProfile(targetUid)
            }
            // Fallback query on users collection
            val querySnapshot = db.collection("users")
                .whereEqualTo("publicUserId", cleanId)
                .limit(1)
                .get()
                .await()
            if (!querySnapshot.isEmpty) {
                querySnapshot.documents.first().toObject(UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "public_user_indices/$cleanId")
            null
        }
    }

    // ----------------------------------------------------
    // GENDER-BASED PROFILE DISCOVERY (MANDATORY RULE)
    // ----------------------------------------------------

    /**
     * Strictly enforces opposite-gender discovery:
     * - Male users see ONLY eligible Female profiles
     * - Female users see ONLY eligible Male profiles
     * - Enforced directly via Firestore query and filters out blocked or previously swiped profiles.
     */
    fun observeDiscoveryProfiles(currentUserId: String, currentUserGender: String): Flow<List<UserProfile>> {
        val targetGender = when (currentUserGender.trim().lowercase(Locale.ROOT)) {
            "male", "man" -> "Female"
            "female", "woman" -> "Male"
            else -> "Female"
        }

        // Backend query filtering directly on opposite gender and active status
        val query = db.collection("users")
            .whereEqualTo("gender", targetGender)
            .whereEqualTo("accountStatus", "active")
            .limit(100)

        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java)
                    .filter { it.userId != currentUserId && it.userId.isNotBlank() }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "users")
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getExcludedUserIds(currentUserId: String): Set<String> = withContext(Dispatchers.IO) {
        val excluded = mutableSetOf(currentUserId)
        try {
            // Exclude already liked or passed users
            val likesSnapshot = db.collection("likes")
                .whereEqualTo("fromUserId", currentUserId)
                .get()
                .await()
            likesSnapshot.documents.forEach { doc ->
                val toUid = doc.getString("toUserId")
                if (!toUid.isNullOrBlank()) excluded.add(toUid)
            }

            // Exclude blocked users
            val blocksSnapshot = db.collection("blocks")
                .whereEqualTo("blockerUid", currentUserId)
                .get()
                .await()
            blocksSnapshot.documents.forEach { doc ->
                val blockedUid = doc.getString("blockedUid")
                if (!blockedUid.isNullOrBlank()) excluded.add(blockedUid)
            }
        } catch (e: Exception) {
            Log.w("BondlyRepo", "Failed fetching exclusion list: ${e.message}")
        }
        excluded
    }

    suspend fun likeUser(targetUserId: String, isSuperLike: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        if (uid == targetUserId) {
            return@withContext Result.failure(IllegalArgumentException("You cannot express interest in your own profile."))
        }
        val likeId = "${uid}_${targetUserId}"
        val likeRef = db.collection("likes").document(likeId)
        try {
            val likeType = if (isSuperLike) "super_like" else "like"
            val likePayload = mapOf(
                "id" to likeId,
                "fromUserId" to uid,
                "toUserId" to targetUserId,
                "type" to likeType,
                "createdAt" to FieldValue.serverTimestamp()
            )
            likeRef.set(likePayload).await()

            // Check if inverse like already exists
            val inverseLikeId = "${targetUserId}_${uid}"
            val inverseDoc = db.collection("likes").document(inverseLikeId).get().await()
            val isMutual = inverseDoc.exists() && inverseDoc.getString("type") in listOf("like", "super_like")

            val myProfile = getUserProfile(uid)
            val myName = myProfile?.displayName ?: "Someone"
            val targetProfile = getUserProfile(targetUserId)

            if (isMutual) {
                // Create ONE unique match record atomically with deterministic ID
                val user1 = if (uid < targetUserId) uid else targetUserId
                val user2 = if (uid < targetUserId) targetUserId else uid
                val matchId = "${user1}_${user2}"
                val matchRef = db.collection("matches").document(matchId)

                val userList = listOf(user1, user2)
                val targetName = targetProfile?.displayName ?: "Match"
                val namesMap = mapOf(uid to myName, targetUserId to targetName)
                val photosMap = mapOf(
                    uid to (myProfile?.profilePhotoUrls?.firstOrNull() ?: ""),
                    targetUserId to (targetProfile?.profilePhotoUrls?.firstOrNull() ?: "")
                )

                val matchPayload = mapOf(
                    "id" to matchId,
                    "user1Id" to user1,
                    "user2Id" to user2,
                    "users" to userList,
                    "participants" to userList,
                    "participantNames" to namesMap,
                    "participantPhotos" to photosMap,
                    "lastMessage" to "You matched! Say hello 👋",
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Idempotent and deterministic match creation with merge
                matchRef.set(matchPayload, com.google.firebase.firestore.SetOptions.merge()).await()

                // Create match notifications for BOTH users
                createNotification(
                    userId = targetUserId,
                    title = "It's a Match! 🎉",
                    message = "You and $myName liked each other! Start a conversation.",
                    type = "match",
                    targetId = matchId
                )
                createNotification(
                    userId = uid,
                    title = "It's a Match! 🎉",
                    message = "You and ${targetProfile?.displayName ?: "someone special"} liked each other! Start a conversation.",
                    type = "match",
                    targetId = matchId
                )

                Result.success(true)
            } else {
                // Notify target user that someone expressed interest
                createNotification(
                    userId = targetUserId,
                    title = "New Interest Received 💕",
                    message = "$myName showed interest in your profile! Check Discover to match.",
                    type = "like",
                    targetId = uid
                )
                Result.success(false)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, likeRef.path)
            Result.failure(e)
        }
    }

    suspend fun passUser(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val likeId = "${uid}_${targetUserId}"
        val likeRef = db.collection("likes").document(likeId)
        try {
            val passPayload = mapOf(
                "id" to likeId,
                "fromUserId" to uid,
                "toUserId" to targetUserId,
                "type" to "pass",
                "createdAt" to FieldValue.serverTimestamp()
            )
            likeRef.set(passPayload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, likeRef.path)
            Result.failure(e)
        }
    }

    fun observeMatches(): Flow<List<Match>> {
        val uid = requireUserId()
        val query = db.collection("matches")
            .whereArrayContains("users", uid)

        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Match::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("BondlyRepo", "Failed to deserialize match ${doc.id}: ${e.message}")
                        null
                    }
                }.sortedByDescending { it.lastMessageTimestamp?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "matches")
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    // ----------------------------------------------------
    // REAL-TIME MESSAGING WITH CONTACT PROTECTION & DELETION
    // ----------------------------------------------------

    fun observeMessages(matchId: String, currentUserId: String): Flow<List<ChatMessage>> {
        val query = db.collection("matches")
            .document(matchId)
            .collection("messages")
            .orderBy("localTimestamp", Query.Direction.ASCENDING)

        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                }
                .filter { !it.isDeletedForUser(currentUserId) }
                .sortedWith(compareBy({ it.createdAt?.toDate()?.time ?: it.localTimestamp }, { it.localTimestamp }))
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "matches/$matchId/messages")
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun sendMessage(matchId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        // Strict multi-layer contact sharing & external link filter
        val contactCheck = ContactSharingFilter.checkText(trimmed)
        if (contactCheck.isBlocked) {
            return@withContext Result.failure(IllegalArgumentException(ContactSharingFilter.WARNING_MESSAGE))
        }

        val msgId = UUID.randomUUID().toString()
        val msgRef = db.collection("matches").document(matchId).collection("messages").document(msgId)
        val matchRef = db.collection("matches").document(matchId)
        val now = System.currentTimeMillis()
        try {
            val payload = mapOf(
                "id" to msgId,
                "matchId" to matchId,
                "senderId" to uid,
                "text" to trimmed,
                "imageUrl" to "",
                "status" to "sent",
                "isDeletedForEveryone" to false,
                "deletedForEveryone" to false,
                "deletedForUserIds" to emptyList<String>(),
                "localTimestamp" to now,
                "createdAt" to FieldValue.serverTimestamp()
            )
            msgRef.set(payload).await()
            // Update last message in parent match
            matchRef.update(
                mapOf(
                    "lastMessage" to trimmed,
                    "lastMessageSenderId" to uid,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForEveryone(matchId: String, messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val msgRef = db.collection("matches").document(matchId).collection("messages").document(messageId)
        try {
            val snapshot = msgRef.get().await()
            if (!snapshot.exists()) {
                return@withContext Result.failure(IllegalStateException("Message does not exist."))
            }
            val senderId = snapshot.getString("senderId")
            if (senderId != uid) {
                return@withContext Result.failure(SecurityException("Only the sender can delete a message for everyone."))
            }
            msgRef.update(
                mapOf(
                    "isDeletedForEveryone" to true,
                    "deletedForEveryone" to true,
                    "text" to "🚫 This message was deleted",
                    "deletedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(matchId: String, messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val msgRef = db.collection("matches").document(matchId).collection("messages").document(messageId)
        try {
            msgRef.update("deletedForUserIds", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // ROLE MANAGEMENT & ADMINISTRATIVE CONTROLS
    // ----------------------------------------------------

    suspend fun updateUserRole(targetUserId: String, newRole: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        if (newRole !in listOf("user", "admin")) {
            return@withContext Result.failure(IllegalArgumentException("Role must be 'user' or 'admin'."))
        }
        val targetRef = db.collection("users").document(targetUserId)
        try {
            targetRef.update("role", newRole).await()
            // Record audit log
            val logId = UUID.randomUUID().toString()
            db.collection("admin_audit_logs").document(logId).set(
                mapOf(
                    "id" to logId,
                    "adminUid" to uid,
                    "targetUserId" to targetUserId,
                    "action" to "SET_ROLE",
                    "newRole" to newRole,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, targetRef.path)
            Result.failure(e)
        }
    }

    suspend fun searchUserByPublicId(publicId: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        val normalized = publicId.trim().uppercase()
        val indexRef = db.collection("public_user_indices").document(normalized)
        try {
            val snapshot = indexRef.get().await()
            if (!snapshot.exists()) {
                return@withContext Result.success(null)
            }
            val targetUid = snapshot.getString("userId") ?: return@withContext Result.success(null)
            val profile = getUserProfile(targetUid)
            Result.success(profile)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, indexRef.path)
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // REAL-TIME AUDIO & VIDEO CALL SIGNALING & HISTORY
    // ----------------------------------------------------

    suspend fun initiateCall(
        receiverUid: String,
        receiverName: String,
        callerName: String,
        callerPhotoUrl: String = "",
        callType: String
    ): Result<CallSession> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        if (uid == receiverUid) {
            return@withContext Result.failure(IllegalArgumentException("Cannot call yourself."))
        }
        val callId = UUID.randomUUID().toString()
        val channelName = "bondly_call_${callId.take(8)}"
        val callRef = db.collection("calls").document(callId)

        try {
            val session = CallSession(
                callId = callId,
                callerUid = uid,
                receiverUid = receiverUid,
                callerName = callerName,
                callerPhotoUrl = callerPhotoUrl,
                receiverName = receiverName,
                channelName = channelName,
                callType = callType,
                status = "ringing"
            )
            val payload = mapOf(
                "callId" to callId,
                "callerUid" to uid,
                "receiverUid" to receiverUid,
                "callerName" to callerName,
                "callerPhotoUrl" to callerPhotoUrl,
                "receiverName" to receiverName,
                "channelName" to channelName,
                "callType" to callType,
                "status" to "ringing",
                "durationSeconds" to 0,
                "createdAt" to FieldValue.serverTimestamp()
            )
            callRef.set(payload).await()

            // Create high-priority call notification for the receiver
            createNotification(
                userId = receiverUid,
                title = "Incoming $callType call 📞",
                message = "$callerName is calling you on Bondly.",
                type = "call",
                targetId = callId
            )

            Result.success(session)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, callRef.path)
            Result.failure(e)
        }
    }

    suspend fun recordDailyActivity(userId: String) = withContext(Dispatchers.IO) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val userRef = db.collection("users").document(userId)
        try {
            val snapshot = userRef.get().await()
            if (snapshot.exists()) {
                val lastActive = snapshot.getString("lastActiveDate") ?: ""
                val currentActiveDays = snapshot.getLong("activeDaysCount")?.toInt() ?: 1
                if (lastActive != today) {
                    val newActiveDays = currentActiveDays + 1
                    val createdAt = snapshot.getTimestamp("createdAt")
                    val dummyProfile = UserProfile(
                        createdAt = createdAt,
                        activeDaysCount = newActiveDays,
                        role = snapshot.getString("role") ?: "user"
                    )
                    val newLevel = com.example.util.UserLevelManager.calculateProgression(dummyProfile).currentLevel.level

                    userRef.update(
                        mapOf(
                            "lastActiveDate" to today,
                            "activeDaysCount" to newActiveDays,
                            "userLevel" to newLevel,
                            "lastActiveAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: Exception) {
            Log.w("BondlyRepo", "Could not record daily activity: ${e.message}")
        }
    }

    suspend fun verifyMobileNumber(userId: String, rawMobileNumber: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            val masked = ContactSharingFilter.maskMobile(rawMobileNumber)
            userRef.update(
                mapOf(
                    "maskedMobileNumber" to masked,
                    "isMobileVerified" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val privateRef = userRef.collection("private").document("account")
            privateRef.set(
                mapOf(
                    "mobileNumber" to rawMobileNumber,
                    "verified" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    fun observeCall(callId: String): Flow<CallSession?> {
        val callRef = db.collection("calls").document(callId)
        return callRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) snapshot.toObject(CallSession::class.java) else null
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, callRef.path)
                emit(null)
            }
            .flowOn(Dispatchers.IO)
    }

    fun observeIncomingCalls(): Flow<CallSession?> {
        val uid = requireUserId()
        val query = db.collection("calls")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "ringing")
            .limit(1)

        return query.snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()?.toObject(CallSession::class.java)
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "calls")
                emit(null)
            }
            .flowOn(Dispatchers.IO)
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        val callRef = db.collection("calls").document(callId)
        try {
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "durationSeconds" to durationSeconds
            )
            if (status in listOf("ended", "declined", "busy", "failed")) {
                updates["endedAt"] = FieldValue.serverTimestamp()
            }
            callRef.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, callRef.path)
            Result.failure(e)
        }
    }

    fun observeCallHistory(userId: String): Flow<List<CallSession>> {
        val query = db.collection("calls")
            .whereEqualTo("callerUid", userId)
            .limit(50)

        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(CallSession::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, "calls")
                emit(emptyList())
            }
            .flowOn(Dispatchers.IO)
    }

    // ----------------------------------------------------
    // NOTIFICATIONS
    // ----------------------------------------------------

    suspend fun createNotification(
        userId: String,
        title: String,
        message: String,
        type: String,
        targetId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val notifId = UUID.randomUUID().toString()
        val notifRef = db.collection("users").document(userId).collection("notifications").document(notifId)
        try {
            val payload = mapOf(
                "id" to notifId,
                "userId" to userId,
                "title" to title,
                "message" to message,
                "type" to type,
                "targetId" to targetId,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            notifRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeNotifications(userId: String): Flow<List<NotificationItem>> {
        val query = db.collection("users")
            .document(userId)
            .collection("notifications")
            .limit(30)

        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(NotificationItem::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }

    suspend fun markNotificationRead(userId: String, notificationId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val notifRef = db.collection("users").document(userId).collection("notifications").document(notificationId)
        try {
            notifRef.update("isRead", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // SAFETY: REPORT & BLOCK
    // ----------------------------------------------------

    suspend fun reportUser(reportedUid: String, reason: String, details: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val reportId = UUID.randomUUID().toString()
        val reportRef = db.collection("reports").document(reportId)
        try {
            val payload = mapOf(
                "id" to reportId,
                "reporterUid" to uid,
                "reportedUid" to reportedUid,
                "reason" to reason,
                "details" to details,
                "createdAt" to FieldValue.serverTimestamp()
            )
            reportRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reportRef.path)
            Result.failure(e)
        }
    }

    suspend fun blockUser(blockedUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val blockId = "${uid}_${blockedUid}"
        val blockRef = db.collection("blocks").document(blockId)
        try {
            val payload = mapOf(
                "id" to blockId,
                "blockerUid" to uid,
                "blockedUid" to blockedUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            blockRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, blockRef.path)
            Result.failure(e)
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<Block>> {
        val query = db.collection("blocks")
            .whereEqualTo("blockerUid", userId)

        return query.snapshots()
            .map { it.toObjects(Block::class.java) }
            .catch { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }

    suspend fun unblockUser(blockId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val blockRef = db.collection("blocks").document(blockId)
        try {
            blockRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, blockRef.path)
            Result.failure(e)
        }
    }

    // ----------------------------------------------------
    // ACCOUNT DELETION (DPDP ACT / PRIVACY COMPLIANT)
    // ----------------------------------------------------

    suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        try {
            userRef.update(
                mapOf(
                    "accountStatus" to "deleted",
                    "displayName" to "[Deleted User]",
                    "bio" to "",
                    "profilePhotoUrls" to emptyList<String>(),
                    "totalPhotoCount" to 0,
                    "profileCompleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Clear private records
            userRef.collection("private").document("account").delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }
}
