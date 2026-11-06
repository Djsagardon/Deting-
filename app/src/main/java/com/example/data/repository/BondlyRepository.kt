package com.example.data.repository

import com.example.data.model.*
import com.example.util.BondlyErrorClassifier
import com.example.util.NoInternetException
import com.example.util.NetworkConnectivityManager
import com.example.util.UserLevelManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class BondlyRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    init {
        try {
            // Strictly disable persistent SQLite disk cache. Use memory cache only for the active session.
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
            Log.d("BondlyRepo", "Firestore configured for strict online operation (memory-only cache).")
        } catch (e: Exception) {
            Log.w("BondlyRepo", "Firestore settings adjustment note: ${e.message}")
        }
    }

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
        if (!NetworkConnectivityManager.isConnected()) return null
        return try {
            val snap = firestore.collection("users").document(uid).get(Source.SERVER).await()
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        // Do not present stale cached data when offline
                        return@addSnapshotListener
                    }
                    val profile = snapshot.toObject(UserProfile::class.java)
                    trySend(profile)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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

    /**
     * Persists an uploaded photo URL directly into the user's Firestore document on the live server.
     * Guarantees server acknowledgement before reporting slot success.
     */
    suspend fun updatePhotoSlotUrl(uid: String, slotIndex: Int, url: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val docRef = firestore.collection("users").document(uid)
            val snap = try {
                docRef.get(Source.SERVER).await()
            } catch (_: Exception) {
                null
            }
            val profile = snap?.toObject(UserProfile::class.java) ?: UserProfile(userId = uid)
            val currentUrls = profile.profilePhotoUrls.toMutableList()
            while (currentUrls.size <= slotIndex) {
                currentUrls.add("")
            }
            currentUrls[slotIndex] = url
            val cleanUrls = currentUrls.filter { it.isNotBlank() }

            val allFiveCompleted = cleanUrls.size == 5
            val isNowCompleted = allFiveCompleted &&
                    profile.displayName.isNotBlank() &&
                    profile.gender.isNotBlank() &&
                    profile.age >= 18 &&
                    profile.rulesAccepted

            val updatePayload = mapOf(
                "userId" to uid,
                "profilePhotoUrls" to cleanUrls,
                "totalPhotoCount" to cleanUrls.size,
                "profileCompleted" to isNowCompleted,
                "updatedAt" to Timestamp.now()
            )
            docRef.set(updatePayload, SetOptions.merge()).await()

            // Update search index if primary photo changed
            if (slotIndex == 0 && profile.publicUserId.isNotEmpty()) {
                try {
                    firestore.collection("public_user_index").document(profile.publicUserId).update(
                        mapOf("photoUrl" to url)
                    ).await()
                } catch (_: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun allocateUniquePublicId(uid: String): String {
        if (!NetworkConnectivityManager.isConnected()) {
            throw NoInternetException("No internet connection. Please connect to the internet and try again.")
        }
        var attempts = 0
        while (attempts < 5) {
            val candidate = "BND-${Random.nextInt(100000, 999999)}"
            val snap = firestore.collection("public_user_index").document(candidate).get(Source.SERVER).await()
            if (!snap.exists()) {
                return candidate
            }
            attempts++
        }
        return "BND-${System.currentTimeMillis() % 1000000}"
    }

    suspend fun searchUserByPublicId(publicId: String): PublicUserIndex? {
        if (!NetworkConnectivityManager.isConnected()) {
            throw NoInternetException("No internet connection. Please connect to the internet and try again.")
        }
        val query = publicId.trim()
        if (query.isEmpty()) return null

        val clean = query.uppercase(Locale.ROOT)

        // 1. Check exact ID in public_user_index
        try {
            val snap = firestore.collection("public_user_index").document(clean).get(Source.SERVER).await()
            if (snap.exists()) {
                val index = snap.toObject(PublicUserIndex::class.java)
                if (index != null && index.userId.isNotBlank()) return index
            }
        } catch (_: Exception) {}

        // 2. Query public_user_index by field publicUserId
        try {
            val qSnap = firestore.collection("public_user_index")
                .whereEqualTo("publicUserId", clean)
                .limit(1).get(Source.SERVER).await()
            val firstDoc = qSnap.documents.firstOrNull()
            if (firstDoc != null) {
                val index = firstDoc.toObject(PublicUserIndex::class.java)
                if (index != null && index.userId.isNotBlank()) return index
            }
        } catch (_: Exception) {}

        // 3. Fallback: Search in users collection and auto-heal index
        try {
            val profile = findUserByPublicId(query)
            if (profile != null) {
                val indexDoc = PublicUserIndex(
                    publicUserId = profile.publicUserId.ifEmpty { clean },
                    userId = profile.userId,
                    displayName = profile.displayName,
                    city = profile.city,
                    gender = profile.gender,
                    age = profile.age,
                    photoUrl = profile.profilePhotoUrls.firstOrNull() ?: ""
                )
                healPublicUserIndex(profile)
                return indexDoc
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun findUserByPublicId(publicId: String, requesterUid: String = currentUid): UserProfile? {
        if (!NetworkConnectivityManager.isConnected()) {
            throw NoInternetException("No internet connection. Please connect to the internet and try again.")
        }
        val query = publicId.trim()
        if (query.isEmpty()) return null

        val clean = query.uppercase(Locale.ROOT)
        val isAdminRequester = checkIsAdmin(requesterUid)

        // Build list of all candidate keys to test
        val candidateKeys = mutableListOf(clean, query)
        val stripped = clean.removePrefix("BONDLY-").removePrefix("BND-").removePrefix("@").trim()
        if (stripped.isNotEmpty()) {
            candidateKeys.add(stripped)
            candidateKeys.add("BND-$stripped")
            candidateKeys.add("BONDLY-$stripped")
        }

        // Layer 1: Check public_user_index document lookup
        for (candidate in candidateKeys.distinct()) {
            try {
                val snap = firestore.collection("public_user_index").document(candidate).get(Source.SERVER).await()
                if (snap.exists()) {
                    val uid = snap.getString("userId")
                    if (!uid.isNullOrEmpty()) {
                        val profile = getProfile(uid)
                        if (profile != null) {
                            if (!isAdminRequester && profile.accountStatus in listOf("banned", "suspended")) {
                                return null
                            }
                            return profile
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Layer 2: Query public_user_index by field publicUserId
        for (candidate in candidateKeys.distinct()) {
            try {
                val qSnap = firestore.collection("public_user_index")
                    .whereEqualTo("publicUserId", candidate)
                    .limit(1).get(Source.SERVER).await()
                val doc = qSnap.documents.firstOrNull()
                val uid = doc?.getString("userId")
                if (!uid.isNullOrEmpty()) {
                    val profile = getProfile(uid)
                    if (profile != null) {
                        if (!isAdminRequester && profile.accountStatus in listOf("banned", "suspended")) {
                            return null
                        }
                        return profile
                    }
                }
            } catch (_: Exception) {}
        }

        // Layer 3: Query users collection directly by publicUserId
        for (candidate in candidateKeys.distinct()) {
            try {
                val qSnap = firestore.collection("users")
                    .whereEqualTo("publicUserId", candidate)
                    .limit(1).get(Source.SERVER).await()
                val doc = qSnap.documents.firstOrNull()
                if (doc != null) {
                    val profile = doc.toObject(UserProfile::class.java)?.let { p ->
                        p.copy(userId = if (p.userId.isNotBlank()) p.userId else doc.id)
                    }
                    if (profile != null) {
                        healPublicUserIndex(profile)
                        if (!isAdminRequester && profile.accountStatus in listOf("banned", "suspended")) {
                            return null
                        }
                        return profile
                    }
                }
            } catch (_: Exception) {}
        }

        // Layer 4: Direct document ID lookup in users collection (by Firebase UID)
        try {
            val userSnap = firestore.collection("users").document(query).get(Source.SERVER).await()
            if (userSnap.exists()) {
                val profile = userSnap.toObject(UserProfile::class.java)?.let { p ->
                    p.copy(userId = if (p.userId.isNotBlank()) p.userId else userSnap.id)
                }
                if (profile != null) {
                    healPublicUserIndex(profile)
                    if (!isAdminRequester && profile.accountStatus in listOf("banned", "suspended")) {
                        return null
                    }
                    return profile
                }
            }
        } catch (_: Exception) {}

        // Layer 5: Admin extended search by exact display name
        if (isAdminRequester) {
            try {
                val nameSnap = firestore.collection("users")
                    .whereEqualTo("displayName", query)
                    .limit(1).get(Source.SERVER).await()
                val doc = nameSnap.documents.firstOrNull()
                if (doc != null) {
                    val profile = doc.toObject(UserProfile::class.java)?.let { p ->
                        p.copy(userId = if (p.userId.isNotBlank()) p.userId else doc.id)
                    }
                    if (profile != null) return profile
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private suspend fun healPublicUserIndex(profile: UserProfile) {
        if (profile.publicUserId.isNotBlank() && profile.userId.isNotBlank()) {
            try {
                val indexDoc = PublicUserIndex(
                    publicUserId = profile.publicUserId,
                    userId = profile.userId,
                    displayName = profile.displayName,
                    city = profile.city,
                    gender = profile.gender,
                    age = profile.age,
                    photoUrl = profile.profilePhotoUrls.firstOrNull() ?: ""
                )
                firestore.collection("public_user_index")
                    .document(profile.publicUserId.trim().uppercase(Locale.ROOT))
                    .set(indexDoc).await()
            } catch (_: Exception) {}
        }
    }

    // =========================================================================
    // 2. DISCOVERY & 48-HOUR HIDE RULE
    // =========================================================================

    suspend fun getExcludedUserIds(myUid: String): Set<String> {
        if (!NetworkConnectivityManager.isConnected()) return emptySet()
        val excluded = mutableSetOf<String>()
        val now = Timestamp.now()

        // 1. Rejections (48 hour hide)
        try {
            val rejSnap = firestore.collection("rejections")
                .whereEqualTo("rejectorUid", myUid)
                .get(Source.SERVER).await()
            for (doc in rejSnap.documents) {
                val rej = doc.toObject(Rejection::class.java)
                if (rej != null && rej.expiresAt != null && rej.expiresAt.seconds > now.seconds) {
                    excluded.add(rej.rejectedUid)
                }
            }
        } catch (_: Exception) {}

        // 2. Blocks
        try {
            val blockSnap = firestore.collection("blocks")
                .whereEqualTo("blockerUid", myUid)
                .get(Source.SERVER).await()
            for (doc in blockSnap.documents) {
                doc.getString("blockedUid")?.let { excluded.add(it) }
            }
        } catch (_: Exception) {}

        // 3. Confirmed Matches
        try {
            val matchSnap = firestore.collection("matches")
                .whereArrayContains("users", myUid)
                .get(Source.SERVER).await()
            for (doc in matchSnap.documents) {
                val m = doc.toObject(Match::class.java)
                if (m != null) {
                    if (m.user1Id == myUid) excluded.add(m.user2Id) else excluded.add(m.user1Id)
                }
            }
        } catch (_: Exception) {}

        // 4. Match Requests sent by me (pending, accepted, rejected)
        try {
            val sentReqSnap = firestore.collection("match_requests")
                .whereEqualTo("senderId", myUid)
                .get(Source.SERVER).await()
            for (doc in sentReqSnap.documents) {
                doc.getString("receiverId")?.let { excluded.add(it) }
            }
        } catch (_: Exception) {}

        // 5. Match Requests received by me (pending or accepted)
        try {
            val recReqSnap = firestore.collection("match_requests")
                .whereEqualTo("receiverId", myUid)
                .get(Source.SERVER).await()
            for (doc in recReqSnap.documents) {
                val status = doc.getString("status")
                if (status == "pending" || status == "accepted") {
                    doc.getString("senderId")?.let { excluded.add(it) }
                }
            }
        } catch (_: Exception) {}

        // 6. Likes sent by me
        try {
            val likeSnap = firestore.collection("likes")
                .whereEqualTo("fromUserId", myUid)
                .get(Source.SERVER).await()
            for (doc in likeSnap.documents) {
                doc.getString("toUserId")?.let { excluded.add(it) }
            }
        } catch (_: Exception) {}

        return excluded
    }

    fun observeDiscoveryUsers(myGender: String, myPreference: String): Flow<List<UserProfile>> = callbackFlow {
        val myUid = currentUid
        if (myUid.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Determine target genders based on user preference first, then fallback to complementary gender
        val targetGenders = when (myPreference.trim().lowercase()) {
            "men", "man", "male" -> listOf("Man", "Male", "Men")
            "women", "woman", "female" -> listOf("Woman", "Female", "Women")
            "everyone", "anyone", "both", "all", "" -> emptyList() // No gender restriction
            else -> when (myGender.trim().lowercase()) {
                "man", "male" -> listOf("Woman", "Female", "Women")
                "woman", "female" -> listOf("Man", "Male", "Men")
                else -> emptyList()
            }
        }

        val reg = firestore.collection("users")
            .limit(100)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e("BondlyRepo", "observeDiscoveryUsers snapshot error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        // Do not show stale cached profiles while offline
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                }

                val allCandidates = snapshot?.documents?.mapNotNull { doc ->
                    val p = doc.toObject(UserProfile::class.java)
                    p?.copy(userId = if (p.userId.isNotBlank()) p.userId else doc.id)
                }?.filter { candidate ->
                    val isNotMe = candidate.userId != myUid
                    val isStatusActive = candidate.accountStatus.equals("active", ignoreCase = true) || candidate.accountStatus.isEmpty()
                    // Eligible if marked complete OR has essential fields (at least 1 photo and name)
                    val isComplete = candidate.profileCompleted || (candidate.profilePhotoUrls.isNotEmpty() && candidate.displayName.isNotBlank())
                    isNotMe && isStatusActive && isComplete
                } ?: emptyList()

                // Filter by target gender if preference/gender specified
                val genderFiltered = if (targetGenders.isNotEmpty()) {
                    allCandidates.filter { candidate ->
                        targetGenders.any { it.equals(candidate.gender, ignoreCase = true) }
                    }
                } else {
                    allCandidates
                }

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    val excluded = getExcludedUserIds(myUid)
                    val eligible = genderFiltered.filter { it.userId !in excluded }
                    trySend(eligible)
                }
            }
        awaitClose { reg.remove() }
    }

    /**
     * Saves a 48-hour rejection record for the target profile.
     * The profile is hidden from Discover for exactly 48 hours, then automatically eligible again.
     */
    suspend fun rejectUser(targetUid: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))
            if (targetUid == myUid) return Result.failure(Exception("Cannot match with yourself"))

            val myProfile = getProfile(myUid) ?: return Result.failure(Exception("My profile not found"))
            val targetProfile = getProfile(targetUid) ?: return Result.failure(Exception("Target profile not found"))

            // Check if already matched
            val matchId = if (myUid < targetUid) "${myUid}_$targetUid" else "${targetUid}_$myUid"
            val existingMatchSnap = firestore.collection("matches").document(matchId).get(Source.SERVER).await()
            if (existingMatchSnap.exists()) {
                return Result.failure(Exception("You are already matched with ${targetProfile.displayName}"))
            }

            // Check if request already pending from myUid
            val reqId = "${myUid}_$targetUid"
            val existingReqSnap = firestore.collection("match_requests").document(reqId).get(Source.SERVER).await()
            if (existingReqSnap.exists()) {
                val status = existingReqSnap.getString("status")
                if (status == "pending") {
                    return Result.failure(Exception("Match request is already pending for ${targetProfile.displayName}"))
                }
            }

            // Check if target has an existing pending request or like for me
            val reverseReqId = "${targetUid}_$myUid"
            val reverseReqSnap = firestore.collection("match_requests").document(reverseReqId).get(Source.SERVER).await()
            val reverseLikeSnap = firestore.collection("likes").document(reverseReqId).get(Source.SERVER).await()

            val isMutual = (reverseReqSnap.exists() && reverseReqSnap.getString("status") == "pending") ||
                           reverseLikeSnap.exists()

            if (isMutual) {
                // CONFIRMED MUTUAL MATCH
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
                val matchRequest = MatchRequest(
                    id = reqId,
                    senderId = myUid,
                    receiverId = targetUid,
                    senderName = myProfile.displayName,
                    senderPhotoUrl = myProfile.profilePhotoUrls.firstOrNull() ?: "",
                    senderAge = myProfile.age,
                    senderCity = myProfile.city,
                    senderBio = myProfile.bio,
                    status = "accepted",
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
                firestore.collection("match_requests").document(reqId).set(matchRequest).await()

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
                // Save match/interest request with full sender info
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

                // Send push notification to target user showing who initiated the match
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
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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

            // Also update reverse request if present
            val reverseReqId = "${myUid}_$senderUid"
            try {
                firestore.collection("match_requests").document(reverseReqId).update(
                    mapOf("status" to "accepted", "updatedAt" to Timestamp.now())
                )
            } catch (_: Exception) {}

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

    fun observeMatchRequest(senderUid: String, receiverUid: String): Flow<MatchRequest?> = callbackFlow {
        val reqId = "${senderUid}_$receiverUid"
        val reg = firestore.collection("match_requests").document(reqId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    trySend(snapshot.toObject(MatchRequest::class.java))
                }
            }
        awaitClose { reg.remove() }
    }

    fun observeMatchBetween(user1Id: String, user2Id: String): Flow<Match?> = callbackFlow {
        val matchId = if (user1Id < user2Id) "${user1Id}_$user2Id" else "${user2Id}_$user1Id"
        val reg = firestore.collection("matches").document(matchId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    trySend(snapshot.toObject(Match::class.java))
                }
            }
        awaitClose { reg.remove() }
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    // Fallback to fetch all and filter client side
                    firestore.collection("matches").get(Source.SERVER)
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(Match::class.java) }
                                .filter { it.user1Id == myUid || it.user2Id == myUid || it.users.contains(myUid) }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    val list = snapshot.documents.mapNotNull { it.toObject(Match::class.java) }
                    trySend(list)
                }
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    // Fallback query without orderBy if index is absent
                    firestore.collection("matches").document(matchId)
                        .collection("messages")
                        .get(Source.SERVER)
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                                .sortedBy { it.localTimestamp }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    val list = snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) }
                        .filter { !it.deletedFor.contains(currentUid) }
                    trySend(list)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendMessage(matchId: String, text: String, imageUrl: String = ""): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
                .get(Source.SERVER).await()

            for (doc in snap.documents) {
                if (doc.getString("status") != "seen") {
                    doc.reference.update(mapOf("status" to "seen", "seenAt" to Timestamp.now()))
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteMessageForEveryone(matchId: String, messageId: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    // Fallback to fetch without order to avoid index or timestamp crashes
                    firestore.collection("users").document(myUid)
                        .collection("notifications")
                        .limit(50)
                        .get(Source.SERVER)
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

                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot.documents.mapNotNull { doc ->
                        try {
                            val item = doc.toObject(NotificationItem::class.java)
                            item?.copy(id = if (item.id.isNotEmpty()) item.id else doc.id)
                        } catch (_: Exception) { null }
                    }
                    trySend(list)
                }
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
    ): String {
        if (!NetworkConnectivityManager.isConnected()) return ""
        return try {
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
                delivered = false,
                deliveryStatus = "saved",
                createdAt = Timestamp.now()
            )
            docRef.set(notif).await()
            docRef.id
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun markNotificationAsDelivered(notificationId: String, targetUid: String = currentUid) {
        if (!NetworkConnectivityManager.isConnected()) return
        try {
            if (targetUid.isNotEmpty() && notificationId.isNotEmpty()) {
                firestore.collection("users").document(targetUid)
                    .collection("notifications").document(notificationId)
                    .update(
                        mapOf(
                            "delivered" to true,
                            "deliveryStatus" to "delivered",
                            "deliveredAt" to Timestamp.now()
                        )
                    ).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun markNotificationAsRead(notificationId: String) {
        if (!NetworkConnectivityManager.isConnected()) return
        try {
            val myUid = currentUid
            if (myUid.isNotEmpty() && notificationId.isNotEmpty()) {
                firestore.collection("users").document(myUid)
                    .collection("notifications").document(notificationId)
                    .update(
                        mapOf(
                            "read" to true,
                            "delivered" to true,
                            "deliveryStatus" to "read",
                            "readAt" to Timestamp.now()
                        )
                    ).await()
            }
        } catch (_: Exception) {}
    }

    /**
     * Real-time stream of newly arrived incoming notifications for the current active user.
     * Emits newly arrived notifications so the app can post an immediate system tray notification.
     */
    fun observeLiveIncomingNotifications(myUid: String = currentUid): Flow<NotificationItem> = callbackFlow {
        if (myUid.isEmpty()) {
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(myUid)
            .collection("notifications")
            .whereEqualTo("read", false)
            .limit(20)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                    return@addSnapshotListener
                }
                for (change in snapshot.documentChanges) {
                    if (change.type == DocumentChange.Type.ADDED) {
                        val notif = change.document.toObject(NotificationItem::class.java)
                        val safeNotif = notif.copy(
                            id = if (notif.id.isNotEmpty()) notif.id else change.document.id
                        )
                        if (!safeNotif.isDelivered && !safeNotif.read) {
                            trySend(safeNotif)
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    // =========================================================================
    // 7. WEBRTC CALL SESSIONS & SIGNALING
    // =========================================================================

    suspend fun isMutuallyMatched(targetUid: String): Boolean {
        val myUid = currentUid
        if (myUid.isEmpty() || targetUid.isEmpty()) return false
        val myProfile = getProfile(myUid)
        if (myProfile?.isAdmin == true) return true // Admins bypass restrictions

        return try {
            val q1 = firestore.collection("matches")
                .whereEqualTo("user1Id", myUid)
                .whereEqualTo("user2Id", targetUid)
                .limit(1)
                .get(Source.SERVER)
                .await()
            if (!q1.isEmpty) return true

            val q2 = firestore.collection("matches")
                .whereEqualTo("user1Id", targetUid)
                .whereEqualTo("user2Id", myUid)
                .limit(1)
                .get(Source.SERVER)
                .await()
            !q2.isEmpty
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isUserBusy(uid: String): Boolean {
        return try {
            val ringingAsReceiver = firestore.collection("calls")
                .whereEqualTo("receiverUid", uid)
                .whereEqualTo("status", "ringing")
                .limit(1).get(Source.SERVER).await()
            if (!ringingAsReceiver.isEmpty) return true

            val activeAsCaller = firestore.collection("calls")
                .whereEqualTo("callerUid", uid)
                .whereIn("status", listOf("active", "accepted"))
                .limit(1).get(Source.SERVER).await()
            if (!activeAsCaller.isEmpty) return true

            val activeAsReceiver = firestore.collection("calls")
                .whereEqualTo("receiverUid", uid)
                .whereIn("status", listOf("active", "accepted"))
                .limit(1).get(Source.SERVER).await()
            !activeAsReceiver.isEmpty
        } catch (_: Exception) {
            false
        }
    }

    suspend fun initiateCall(targetUid: String, isVideo: Boolean): Result<CallSession> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val myUid = currentUid
            if (myUid.isEmpty()) return Result.failure(Exception("Not logged in"))
            if (myUid == targetUid) return Result.failure(Exception("Cannot call yourself"))

            // Verify mutual match unless admin
            if (!isMutuallyMatched(targetUid)) {
                return Result.failure(Exception("Calling is only available for mutually confirmed matches"))
            }

            // Check if receiver is currently on another call
            if (isUserBusy(targetUid)) {
                return Result.failure(Exception("User is currently on another call or unavailable"))
            }

            val myProfile = getProfile(myUid)
            val targetProfile = getProfile(targetUid)
            val docRef = firestore.collection("calls").document()
            val session = CallSession(
                callId = docRef.id,
                callerUid = myUid,
                receiverUid = targetUid,
                callerName = myProfile?.displayName ?: "Caller",
                callerPhotoUrl = myProfile?.profilePhotoUrls?.firstOrNull() ?: "",
                receiverName = targetProfile?.displayName ?: "Receiver",
                receiverPhotoUrl = targetProfile?.profilePhotoUrls?.firstOrNull() ?: "",
                channelName = "bondly_${docRef.id}",
                callType = if (isVideo) "video" else "voice",
                status = "ringing",
                createdAt = Timestamp.now()
            )
            docRef.set(session).await()

            // Create notification for receiver
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    val call = snapshot.documents.mapNotNull { it.toObject(CallSession::class.java) }.firstOrNull()
                    trySend(call)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Int = 0): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to status
            )
            if (status in listOf("active", "accepted")) {
                updates["acceptedAt"] = Timestamp.now()
            }
            if (status in listOf("ended", "rejected", "missed", "busy", "timeout", "failed")) {
                updates["endedAt"] = Timestamp.now()
                if (durationSeconds > 0) {
                    updates["durationSeconds"] = durationSeconds
                }
            }
            firestore.collection("calls").document(callId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCallOffer(callId: String, sdp: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val offerMap = mapOf(
                "type" to "offer",
                "sdp" to sdp
            )
            firestore.collection("calls").document(callId).update("offer", offerMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setCallAnswer(callId: String, sdp: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val answerMap = mapOf(
                "type" to "answer",
                "sdp" to sdp
            )
            firestore.collection("calls").document(callId).update(
                mapOf(
                    "answer" to answerMap,
                    "status" to "accepted",
                    "acceptedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCallerCandidate(callId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String, serverUrl: String = ""): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val docRef = firestore.collection("calls").document(callId).collection("callerCandidates").document()
            val candidate = IceCandidateModel(
                id = docRef.id,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                sdp = sdp,
                serverUrl = serverUrl,
                createdAt = Timestamp.now()
            )
            docRef.set(candidate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addReceiverCandidate(callId: String, sdpMid: String, sdpMLineIndex: Int, sdp: String, serverUrl: String = ""): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val docRef = firestore.collection("calls").document(callId).collection("receiverCandidates").document()
            val candidate = IceCandidateModel(
                id = docRef.id,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                sdp = sdp,
                serverUrl = serverUrl,
                createdAt = Timestamp.now()
            )
            docRef.set(candidate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeCallerCandidates(callId: String): Flow<List<IceCandidateModel>> = callbackFlow {
        if (callId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("calls").document(callId)
            .collection("callerCandidates")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(IceCandidateModel::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeReceiverCandidates(callId: String): Flow<List<IceCandidateModel>> = callbackFlow {
        if (callId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("calls").document(callId)
            .collection("receiverCandidates")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(IceCandidateModel::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeCallSession(callId: String): Flow<CallSession?> = callbackFlow {
        if (callId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(CallSession::class.java))
            }
        awaitClose { reg.remove() }
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

    // 8. BLOCKS, REPORTS & ACCOUNT DELETION
    // =========================================================================

    suspend fun blockUser(targetUid: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    val list = snapshot.documents.mapNotNull { it.getString("blockedUid") }
                    trySend(list)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val id = "${currentUid}_$targetUid"
            firestore.collection("blocks").document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reportUser(
        targetUid: String,
        reason: String,
        details: String,
        reasons: List<String> = emptyList(),
        reportedName: String = "",
        reportedPhotoUrl: String = "",
        reportedPublicId: String = ""
    ): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            val docRef = firestore.collection("reports").document()
            val allReasons = reasons.ifEmpty { listOf(reason) }
            val report = Report(
                id = docRef.id,
                reporterUid = currentUid,
                reportedUid = targetUid,
                reportedName = reportedName,
                reportedPhotoUrl = reportedPhotoUrl,
                reportedPublicId = reportedPublicId,
                reason = allReasons.joinToString(", "),
                reasons = allReasons,
                details = details,
                status = "pending",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            docRef.set(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitReport(
        reportedUid: String,
        reportedName: String,
        reasons: List<String>,
        details: String,
        reportedPhotoUrl: String = "",
        reportedPublicId: String = ""
    ): Result<Unit> = reportUser(
        targetUid = reportedUid,
        reason = reasons.joinToString(", "),
        details = details,
        reasons = reasons,
        reportedName = reportedName,
        reportedPhotoUrl = reportedPhotoUrl,
        reportedPublicId = reportedPublicId
    )

    suspend fun submitReport(reportedUid: String, reportedName: String, reason: String, details: String): Result<Unit> =
        reportUser(reportedUid, reason, details, listOf(reason), reportedName)

    suspend fun updateReportStatus(reportId: String, newStatus: String, notes: String = ""): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            if (!checkIsAdmin()) return Result.failure(Exception("Admin access required"))
            firestore.collection("reports").document(reportId).update(
                mapOf(
                    "status" to newStatus,
                    "adminNotes" to notes,
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            recordAdminAudit(
                action = "REPORT_${newStatus.uppercase()}",
                targetUserId = reportId,
                details = "Report status set to $newStatus. Notes: $notes"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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
        if (!NetworkConnectivityManager.isConnected()) return
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
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    firestore.collection("users").limit(100).get(Source.SERVER)
                        .addOnSuccessListener { snap ->
                            val list = snap.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                            trySend(list)
                        }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    if (snapshot.metadata.isFromCache && !NetworkConnectivityManager.isConnected()) {
                        return@addSnapshotListener
                    }
                    val list = snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }
                    trySend(list)
                }
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateAccountStatusByAdmin(targetUid: String, newStatus: String, reason: String): Result<Unit> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
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

    suspend fun sendAdminCustomMessage(targetUid: String, title: String, messageText: String): Result<AdminMessageDeliveryReport> {
        if (!NetworkConnectivityManager.isConnected()) {
            return Result.failure(NoInternetException("No internet connection. Please connect to the internet and try again."))
        }
        return try {
            if (!checkIsAdmin()) {
                return Result.failure(Exception("Unauthorized. Admin access required."))
            }
            if (targetUid.isBlank()) {
                return Result.failure(Exception("Recipient user ID cannot be empty."))
            }

            val finalTitle = if (title.isNotBlank()) title else "Official Bondly Announcement"

            // 1. Save notification to user's database inbox in Firestore
            val notifId = createNotification(
                targetUid = targetUid,
                title = finalTitle,
                message = messageText,
                type = "admin_announcement",
                targetId = "admin_announcement",
                senderName = "Bondly Official",
                senderPhotoUrl = ""
            )

            if (notifId.isEmpty()) {
                return Result.failure(Exception("Failed to save message to user's database inbox."))
            }

            // 2. Fetch recipient's active FCM tokens to verify device push registration
            val recipientProfile = getProfile(targetUid)
            val tokens = recipientProfile?.fcmTokens ?: emptyList()
            val tokenCount = tokens.size

            // 3. Record Admin Audit Log with token count
            recordAdminAudit(
                action = "SEND_CUSTOM_MESSAGE",
                targetUserId = targetUid,
                details = "Title: $title | Tokens: $tokenCount | Message: $messageText"
            )

            val summary = if (tokenCount > 0) {
                "Message saved to inbox and push queued for $tokenCount registered device(s)."
            } else {
                "Message saved to user's inbox. Recipient currently has 0 registered push devices."
            }

            Result.success(
                AdminMessageDeliveryReport(
                    isSuccess = true,
                    messageId = notifId,
                    isSavedInDatabase = true,
                    registeredDeviceCount = tokenCount,
                    deliveryStatus = if (tokenCount > 0) "queued" else "saved",
                    statusSummary = summary
                )
            )
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
