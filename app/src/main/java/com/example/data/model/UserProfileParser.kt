package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Calendar
import java.util.Date

/**
 * Result of resolving an authenticated user's profile from Firestore.
 */
sealed class ProfileResolutionResult {
    data class Completed(val profile: UserProfile) : ProfileResolutionResult()
    data class Incomplete(val profile: UserProfile, val recommendedStep: Int) : ProfileResolutionResult()
    data object NotCreated : ProfileResolutionResult()
}

/**
 * Resilient, backward-compatible parser and completion evaluator for Bondly UserProfile documents.
 * Safely handles legacy Firestore schemas (e.g., `photoUrls` instead of `profilePhotoUrls`,
 * `name`/`fullName` instead of `displayName`, Long epoch timestamps instead of `Timestamp`,
 * missing `userId` or `profileCompleted` flags, numeric/string coercion) without throwing
 * deserialization exceptions or losing existing user data.
 */
object UserProfileParser {

    private val MOBILE_REGEX = Regex("^[6-9][0-9]{9}$")

    fun fromSnapshot(snapshot: DocumentSnapshot?): UserProfile? {
        if (snapshot == null || !snapshot.exists()) return null
        val raw = snapshot.data ?: return null
        return fromMap(snapshot.id, raw)
    }

    fun fromMap(docId: String, raw: Map<String, Any?>): UserProfile {
        val resolvedUserId = firstNonBlankString(raw, "userId", "uid", "id") ?: docId

        val rawMemberId = firstNonBlankString(raw, "memberId", "member_id", "bondlyId") ?: ""
        val rawPublicId = firstNonBlankString(raw, "publicUserId", "public_user_id", "publicId") ?: ""
        val effectiveMemberId = rawMemberId.ifBlank { rawPublicId }

        val displayName = firstNonBlankString(raw, "displayName", "name", "fullName", "username") ?: ""
        val dob = extractDobString(raw["dob"] ?: raw["dateOfBirth"])
        val parsedAge = coerceInt(raw["age"], 0).let { age ->
            if (age > 0) age else computeAgeFromDob(dob)
        }
        val bio = firstNonBlankString(raw, "bio", "about", "aboutMe") ?: ""
        val gender = firstNonBlankString(raw, "gender", "sex") ?: ""
        val interests = extractStringList(raw["interests"] ?: raw["hobbies"])
        val city = firstNonBlankString(raw, "city") ?: ""
        val state = firstNonBlankString(raw, "state") ?: ""
        val maskedMobile = firstNonBlankString(
            raw,
            "maskedMobileNumber",
            "mobileNumber",
            "phoneNumber",
            "phone"
        ) ?: ""

        val isMobileVerified = coerceBoolean(raw["isMobileVerified"], false)
        val rawMobileStatus = firstNonBlankString(raw, "mobileConfirmationStatus")
        val mobileStatus = when {
            !rawMobileStatus.isNullOrBlank() -> rawMobileStatus
            isMobileVerified -> "verified"
            else -> "unconfirmed"
        }

        val photoUrls = extractPhotoUrls(raw)
        val totalPhotoCount = maxOf(coerceInt(raw["totalPhotoCount"], 0), photoUrls.size)

        val datingPreference = firstNonBlankString(raw, "datingPreference", "lookingFor", "preference") ?: "Anyone"
        val rulesAccepted = coerceBooleanNullable(raw["rulesAccepted"])
            ?: coerceBooleanNullable(raw["termsAccepted"])
            ?: coerceBooleanNullable(raw["acceptedRules"])
            ?: false

        val explicitCompleted = coerceBooleanNullable(raw["profileCompleted"])
            ?: coerceBooleanNullable(raw["isProfileComplete"])
            ?: coerceBooleanNullable(raw["onboardingCompleted"])

        // Backward compatibility for legacy pre-update accounts created before the
        // `profileCompleted` boolean existed: only infer true if the flag is absent AND
        // all essential profile fields + photos were already saved.
        val inferredLegacyCompleted = explicitCompleted ?: (
            displayName.isNotBlank() &&
                gender.isNotBlank() &&
                parsedAge >= 18 &&
                effectiveMemberId.isNotBlank() &&
                photoUrls.size >= 5
            )

        val verificationStatus = firstNonBlankString(raw, "verificationStatus") ?: "unverified"
        val accountStatus = firstNonBlankString(raw, "accountStatus") ?: "active"
        val allowCallsFrom = firstNonBlankString(raw, "allowCallsFrom") ?: "matches"
        val showOnlineStatus = coerceBoolean(raw["showOnlineStatus"], true)
        val isOnline = coerceBoolean(raw["isOnline"], false)
        val isPremium = coerceBoolean(raw["isPremium"], false)
        val userLevel = coerceInt(raw["userLevel"], 1).coerceAtLeast(1)
        val activeDaysCount = coerceInt(raw["activeDaysCount"], 1).coerceAtLeast(1)
        val lastActiveDate = firstNonBlankString(raw, "lastActiveDate") ?: ""
        val role = firstNonBlankString(raw, "role") ?: "user"
        val fcmTokens = extractStringList(raw["fcmTokens"])

        val createdAt = coerceTimestamp(raw["createdAt"])
        val updatedAt = coerceTimestamp(raw["updatedAt"])
        val lastActiveAt = coerceTimestamp(raw["lastActiveAt"])

        return UserProfile(
            userId = resolvedUserId,
            memberId = rawMemberId.ifBlank { effectiveMemberId },
            publicUserId = rawPublicId.ifBlank { effectiveMemberId },
            displayName = displayName,
            dob = dob,
            age = parsedAge,
            bio = bio,
            gender = gender,
            interests = interests,
            city = city,
            state = state,
            maskedMobileNumber = maskedMobile,
            isMobileVerified = isMobileVerified,
            mobileConfirmationStatus = mobileStatus,
            profilePhotoUrls = photoUrls,
            totalPhotoCount = totalPhotoCount,
            datingPreference = datingPreference,
            rulesAccepted = rulesAccepted,
            profileCompleted = inferredLegacyCompleted,
            verificationStatus = verificationStatus,
            accountStatus = accountStatus,
            allowCallsFrom = allowCallsFrom,
            showOnlineStatus = showOnlineStatus,
            isOnline = isOnline,
            isPremium = isPremium,
            userLevel = userLevel,
            activeDaysCount = activeDaysCount,
            lastActiveDate = lastActiveDate,
            role = role,
            fcmTokens = fcmTokens,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastActiveAt = lastActiveAt
        )
    }

    /**
     * Evaluates whether an existing UserProfile satisfies completion requirements.
     * Supports both current schema and normalized legacy documents.
     */
    fun isProfileComplete(profile: UserProfile): Boolean {
        return profile.profileCompleted && profile.profilePhotoUrls.size >= 5
    }

    /**
     * Classifies an existing profile into Completed or Incomplete (with first incomplete step).
     */
    fun evaluateProfile(profile: UserProfile?): ProfileResolutionResult {
        if (profile == null) return ProfileResolutionResult.NotCreated
        return if (isProfileComplete(profile)) {
            ProfileResolutionResult.Completed(profile)
        } else {
            ProfileResolutionResult.Incomplete(
                profile = profile,
                recommendedStep = determineInitialOnboardingStep(profile)
            )
        }
    }

    /**
     * Computes the first incomplete onboarding step (1..4) so existing users with incomplete profiles
     * resume right where they left off without losing or re-entering already saved data.
     */
    fun determineInitialOnboardingStep(profile: UserProfile?): Int {
        if (profile == null) return 1

        val hasStep1PersonalDetails =
            profile.displayName.isNotBlank() &&
                profile.age >= 18 &&
                profile.gender.isNotBlank() &&
                profile.city.isNotBlank() &&
                profile.state.isNotBlank() &&
                profile.bio.trim().length >= 5 &&
                profile.interests.size >= 3

        if (!hasStep1PersonalDetails) return 1

        val hasValidMobile = profile.maskedMobileNumber.trim().matches(MOBILE_REGEX)
        val isMobileDone = hasValidMobile && (
            profile.mobileConfirmationStatus == "in_app_confirmed" ||
                profile.isMobileVerified ||
                profile.profileCompleted
            )
        if (!isMobileDone) return 2

        if (profile.profilePhotoUrls.size < 5) return 3

        return 4
    }

    /**
     * Extracts and formats a stored DOB string or derives a fallback birth year from an existing age.
     * Returns Triple(day, month, year).
     */
    fun extractDobParts(profile: UserProfile?, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): Triple<String, String, String> {
        val rawDob = profile?.dob?.trim().orEmpty()
        if (rawDob.isNotEmpty()) {
            val slashParts = rawDob.split("/", "-")
            if (slashParts.size == 3) {
                return if (slashParts[0].length == 4) {
                    // YYYY-MM-DD
                    Triple(
                        slashParts[2].padStart(2, '0').take(2),
                        slashParts[1].padStart(2, '0').take(2),
                        slashParts[0].take(4)
                    )
                } else {
                    // DD/MM/YYYY or DD-MM-YYYY
                    Triple(
                        slashParts[0].padStart(2, '0').take(2),
                        slashParts[1].padStart(2, '0').take(2),
                        slashParts[2].take(4)
                    )
                }
            }
        }
        val existingAge = profile?.age ?: 0
        if (existingAge >= 18) {
            return Triple("15", "08", (currentYear - existingAge).toString())
        }
        return Triple("15", "08", "2000")
    }

    /**
     * Checks if an existing raw Firestore document is missing critical normalized fields
     * (like `userId` in document body) that should be non-destructively backfilled.
     */
    fun needsNonDestructiveBackfill(raw: Map<String, Any?>, parsed: UserProfile): Map<String, Any>? {
        val backfill = mutableMapOf<String, Any>()
        if ((raw["userId"] as? String).isNullOrBlank() && parsed.userId.isNotBlank()) {
            backfill["userId"] = parsed.userId
        }
        val existingPhotos = (raw["profilePhotoUrls"] as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotBlank() }
        if (existingPhotos.isNullOrEmpty() && parsed.profilePhotoUrls.isNotEmpty()) {
            backfill["profilePhotoUrls"] = parsed.profilePhotoUrls
            backfill["totalPhotoCount"] = parsed.profilePhotoUrls.size
        }
        return backfill.takeIf { it.isNotEmpty() }
    }

    private fun extractPhotoUrls(raw: Map<String, Any?>): List<String> {
        val candidateLists = listOf(
            raw["profilePhotoUrls"],
            raw["photoUrls"],
            raw["photos"],
            raw["profilePhotos"],
            raw["images"]
        )
        for (candidate in candidateLists) {
            val extracted = extractStringList(candidate)
                .filter { it.startsWith("http://") || it.startsWith("https://") || it.isNotBlank() }
            if (extracted.isNotEmpty()) return extracted
        }
        val singlePhoto = firstNonBlankString(raw, "photoUrl", "profilePhotoUrl", "avatarUrl")
        return if (!singlePhoto.isNullOrBlank()) listOf(singlePhoto) else emptyList()
    }

    private fun firstNonBlankString(raw: Map<String, Any?>, vararg keys: String): String? {
        for (key in keys) {
            val value = raw[key] as? String
            if (!value.isNullOrBlank()) return value.trim()
        }
        return null
    }

    private fun extractStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    private fun coerceInt(value: Any?, default: Int): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun coerceBoolean(value: Any?, default: Boolean): Boolean {
        return coerceBooleanNullable(value) ?: default
    }

    private fun coerceBooleanNullable(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
            is Number -> value.toInt() != 0
            else -> null
        }
    }

    private fun coerceTimestamp(value: Any?): Timestamp? {
        return when (value) {
            is Timestamp -> value
            is Date -> Timestamp(value)
            is Number -> {
                val raw = value.toLong()
                if (raw <= 0L) null
                else if (raw > 10_000_000_000L) Timestamp(Date(raw)) // epoch millis
                else Timestamp(raw, 0) // epoch seconds
            }
            else -> null
        }
    }

    private fun extractDobString(value: Any?): String {
        return when (value) {
            is String -> value.trim()
            is Timestamp -> {
                val cal = Calendar.getInstance().apply { time = value.toDate() }
                "%02d/%02d/%04d".format(
                    cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.YEAR)
                )
            }
            else -> ""
        }
    }

    private fun computeAgeFromDob(dob: String, currentYear: Int = Calendar.getInstance().get(Calendar.YEAR)): Int {
        if (dob.isBlank()) return 0
        val parts = dob.split("/", "-")
        if (parts.size != 3) return 0
        val year = if (parts[0].length == 4) parts[0].toIntOrNull() else parts[2].toIntOrNull()
        return if (year != null && year in 1900..currentYear) (currentYear - year).coerceAtLeast(0) else 0
    }
}
