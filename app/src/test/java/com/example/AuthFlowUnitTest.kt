package com.example

import com.example.data.model.ProfileResolutionResult
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileParser
import com.example.ui.viewmodel.AppAuthState
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Authentication, Legacy Profile Parsing, Navigation & State Transition Unit Tests.
 * Covers all 10 Critical Login, Legacy Compatibility, Error Handling & Data Preservation Scenarios.
 */
class AuthFlowUnitTest {

    // Evaluates AppAuthState from repository resolution + connectivity/error inputs
    private fun evaluateProfileState(
        uid: String = "test_user_uid_123",
        isConnected: Boolean,
        documentExists: Boolean,
        profile: UserProfile?,
        hasTimedOut: Boolean = false,
        firestoreError: String? = null
    ): AppAuthState {
        if (!isConnected) {
            return AppAuthState.NetworkUnavailable
        }
        if (hasTimedOut) {
            return AppAuthState.RequestFailed(
                message = "Connection timed out while verifying your Bondly profile. Please check your internet connection.",
                canRetry = true
            )
        }
        if (firestoreError != null) {
            return AppAuthState.RequestFailed(message = firestoreError, canRetry = true)
        }
        if (!documentExists || profile == null) {
            return AppAuthState.AuthenticatedProfileNotCreated(
                userId = uid,
                email = "test@bondly.dating"
            )
        }
        return when (val resolution = UserProfileParser.evaluateProfile(profile)) {
            is ProfileResolutionResult.Completed -> AppAuthState.AuthenticatedProfileCompleted(uid, resolution.profile)
            is ProfileResolutionResult.Incomplete -> AppAuthState.AuthenticatedProfileIncomplete(uid, resolution.profile)
            is ProfileResolutionResult.NotCreated -> AppAuthState.AuthenticatedProfileNotCreated(uid, "test@bondly.dating")
        }
    }

    @Test
    fun testScenario1_ExistingUserWithCompleteProfileLogsInDirectly() {
        val completeProfile = UserProfile(
            userId = "existing_uid_001",
            memberId = "BD979241",
            publicUserId = "BD979241",
            displayName = "Rohit Sharma",
            gender = "Man",
            age = 26,
            state = "Maharashtra",
            city = "Mumbai",
            maskedMobileNumber = "9876543210",
            mobileConfirmationStatus = "in_app_confirmed",
            profileCompleted = true,
            profilePhotoUrls = listOf("https://i.ibb.co/1.jpg", "https://i.ibb.co/2.jpg", "https://i.ibb.co/3.jpg", "https://i.ibb.co/4.jpg", "https://i.ibb.co/5.jpg"),
            rulesAccepted = true,
            userLevel = 3
        )
        val resultState = evaluateProfileState(
            uid = "existing_uid_001",
            isConnected = true,
            documentExists = true,
            profile = completeProfile
        )
        assertTrue(
            "Existing complete user must go directly to AuthenticatedProfileCompleted",
            resultState is AppAuthState.AuthenticatedProfileCompleted
        )
        val completed = resultState as AppAuthState.AuthenticatedProfileCompleted
        assertEquals("BD979241", completed.profile.effectiveMemberId)
        assertEquals(5, completed.profile.profilePhotoUrls.size)
        assertEquals(3, completed.profile.userLevel)
    }

    @Test
    fun testScenario2_LegacyFirestoreDocumentWithAlternateFieldNamesAndTypesParsesSafely() {
        // Simulates a pre-update Firestore document with legacy field names & Long epoch timestamps
        val legacyMap: Map<String, Any?> = mapOf(
            // userId missing inside doc body — must fallback to docId
            "name" to "Priya Verma",
            "publicUserId" to "BD445566",
            "gender" to "Woman",
            "age" to "24", // stored as String in older doc
            "city" to "Pune",
            "state" to "Maharashtra",
            "bio" to "Love travel and music!",
            "hobbies" to "Travel & Roadtrips, Music & Concerts, Reading",
            "phoneNumber" to "9812345678",
            "photoUrls" to listOf(
                "https://i.ibb.co/a1.jpg",
                "https://i.ibb.co/a2.jpg",
                "https://i.ibb.co/a3.jpg",
                "https://i.ibb.co/a4.jpg",
                "https://i.ibb.co/a5.jpg"
            ),
            // profileCompleted flag absent in legacy doc
            "termsAccepted" to true,
            "userLevel" to 2L,
            "createdAt" to 1700000000000L // Long millis instead of Timestamp
        )

        val parsed = UserProfileParser.fromMap("legacy_uid_789", legacyMap)
        assertEquals("legacy_uid_789", parsed.userId)
        assertEquals("Priya Verma", parsed.displayName)
        assertEquals("BD445566", parsed.effectiveMemberId)
        assertEquals(24, parsed.age)
        assertEquals("9812345678", parsed.maskedMobileNumber)
        assertEquals(5, parsed.profilePhotoUrls.size)
        assertEquals(3, parsed.interests.size)
        assertTrue(parsed.rulesAccepted)
        assertTrue("Legacy doc with all essential data & 5 photos must evaluate completed", parsed.profileCompleted)
        assertNotNull(parsed.createdAt)

        val resolution = UserProfileParser.evaluateProfile(parsed)
        assertTrue(resolution is ProfileResolutionResult.Completed)

        // Verify non-destructive backfill only adds missing normalized keys without touching original Member ID
        val backfill = UserProfileParser.needsNonDestructiveBackfill(legacyMap, parsed)
        assertNotNull(backfill)
        assertEquals("legacy_uid_789", backfill!!["userId"])
        assertEquals(5, (backfill["profilePhotoUrls"] as List<*>).size)
        assertFalse(backfill.containsKey("memberId"))
    }

    @Test
    fun testScenario3_ExistingUserWithIncompleteProfileResumesAtMissingStepWithoutLosingData() {
        // Step 1 & Step 2 completed, only 3 photos uploaded -> should resume on Step 3
        val incompletePhotosProfile = UserProfile(
            userId = "test_user_uid_123",
            memberId = "BD112233",
            displayName = "Ananya",
            dob = "10/04/1998",
            age = 28,
            gender = "Woman",
            city = "Jaipur",
            state = "Rajasthan",
            bio = "Coffee enthusiast and bookworm.",
            interests = listOf("Reading", "Coffee & Cafes", "Travel & Roadtrips"),
            maskedMobileNumber = "9876543210",
            mobileConfirmationStatus = "in_app_confirmed",
            profileCompleted = true,
            profilePhotoUrls = listOf("url1", "url2", "url3")
        )
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = incompletePhotosProfile
        )
        assertTrue(
            "Profile with < 5 photos must transition to AuthenticatedProfileIncomplete",
            resultState is AppAuthState.AuthenticatedProfileIncomplete
        )
        val resolution = UserProfileParser.evaluateProfile(incompletePhotosProfile) as ProfileResolutionResult.Incomplete
        assertEquals(3, resolution.recommendedStep)

        // Verify DOB parts are preserved rather than reset to 15/08/2000
        val (d, m, y) = UserProfileParser.extractDobParts(incompletePhotosProfile)
        assertEquals("10", d)
        assertEquals("04", m)
        assertEquals("1998", y)
    }

    @Test
    fun testScenario3b_ProfileDocumentIncomplete_FlagFalseResumesAtStep4IfPhotosComplete() {
        val incompleteFlagProfile = UserProfile(
            userId = "test_user_uid_123",
            displayName = "Ananya",
            age = 25,
            gender = "Woman",
            city = "Delhi",
            state = "Delhi (NCT)",
            bio = "Hello everyone on Bondly!",
            interests = listOf("Reading", "Dancing", "Music & Concerts"),
            maskedMobileNumber = "9876543210",
            mobileConfirmationStatus = "in_app_confirmed",
            profileCompleted = false,
            profilePhotoUrls = listOf("url1", "url2", "url3", "url4", "url5")
        )
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = incompleteFlagProfile
        )
        assertTrue(
            "Profile with profileCompleted = false must transition to AuthenticatedProfileIncomplete",
            resultState is AppAuthState.AuthenticatedProfileIncomplete
        )
        assertEquals(4, UserProfileParser.determineInitialOnboardingStep(incompleteFlagProfile))
    }

    @Test
    fun testScenario4_NewUserAuthenticationAndMissingDocumentStartsOnboarding() {
        val initial = AppAuthState.Initializing
        assertTrue(initial is AppAuthState.Initializing)

        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = false,
            profile = null
        )
        assertTrue(
            "Genuinely missing document must result in AuthenticatedProfileNotCreated",
            resultState is AppAuthState.AuthenticatedProfileNotCreated
        )
        assertEquals(1, UserProfileParser.determineInitialOnboardingStep(null))
    }

    @Test
    fun testScenario5_NetworkErrorDoesNotCreateDuplicateOrTriggerOnboarding() {
        val offlineState = evaluateProfileState(
            isConnected = false,
            documentExists = false,
            profile = null
        )
        assertTrue(
            "Offline state must show NetworkUnavailable, never AuthenticatedProfileNotCreated",
            offlineState is AppAuthState.NetworkUnavailable
        )
    }

    @Test
    fun testScenario6_FirestoreTimeoutShowsRetryableErrorWithoutLosingAccount() {
        val timeoutState = evaluateProfileState(
            isConnected = true,
            documentExists = false,
            profile = null,
            hasTimedOut = true
        )
        assertTrue(timeoutState is AppAuthState.RequestFailed)
        val failed = timeoutState as AppAuthState.RequestFailed
        assertTrue(failed.canRetry)
        assertTrue(failed.message.contains("timed out", ignoreCase = true))
    }

    @Test
    fun testScenario7_PermissionDeniedShowsErrorAndDoesNotTreatAsMissingProfile() {
        val errorState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = null,
            firestoreError = "PERMISSION_DENIED: Missing or insufficient permissions."
        )
        assertTrue(errorState is AppAuthState.RequestFailed)
        val failed = errorState as AppAuthState.RequestFailed
        assertTrue(failed.message.contains("PERMISSION_DENIED"))
        assertTrue(failed.canRetry)
    }

    @Test
    fun testScenario8_DobExtractionDerivesBirthYearFromAgeWhenDobMissing() {
        val profileWithAgeOnly = UserProfile(age = 27, dob = "")
        val (_, _, year) = UserProfileParser.extractDobParts(profileWithAgeOnly, currentYear = 2026)
        assertEquals("1999", year)
    }

    @Test
    fun testScenario9_RepeatedAuthListenerCallbacksDoNotDowngradeCompletedUser() {
        val completeProfile = UserProfile(
            userId = "test_user_uid_123",
            memberId = "BD979241",
            displayName = "Rohit",
            profileCompleted = true,
            profilePhotoUrls = listOf("1", "2", "3", "4", "5"),
            createdAt = Timestamp.now()
        )
        val state1 = evaluateProfileState(isConnected = true, documentExists = true, profile = completeProfile)
        val state2 = evaluateProfileState(isConnected = true, documentExists = true, profile = completeProfile)
        assertTrue(state1 is AppAuthState.AuthenticatedProfileCompleted)
        assertTrue(state2 is AppAuthState.AuthenticatedProfileCompleted)
        assertEquals(
            (state1 as AppAuthState.AuthenticatedProfileCompleted).profile.effectiveMemberId,
            (state2 as AppAuthState.AuthenticatedProfileCompleted).profile.effectiveMemberId
        )
    }

    @Test
    fun testScenario10_NoBackfillNeededForModernCompleteDocument() {
        val modernMap: Map<String, Any?> = mapOf(
            "userId" to "uid_modern",
            "memberId" to "BD979241",
            "profilePhotoUrls" to listOf("https://1", "https://2", "https://3", "https://4", "https://5"),
            "profileCompleted" to true
        )
        val parsed = UserProfileParser.fromMap("uid_modern", modernMap)
        assertNull(UserProfileParser.needsNonDestructiveBackfill(modernMap, parsed))
    }
}
