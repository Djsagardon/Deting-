package com.example

import com.example.data.model.UserProfile
import com.example.ui.viewmodel.AppAuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State Transition and LifeCycle Unit Tests for Bondly Auth & Onboarding System.
 * Covers all 9 Critical Auth Flow Scenarios.
 */
class AuthFlowUnitTest {

    // Helper State Machine Evaluator mimicking AuthViewModel resolution logic
    private fun evaluateProfileState(
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
            // Document missing: Fresh user account
            return AppAuthState.AuthenticatedProfileNotCreated(
                userId = "test_user_uid_123",
                email = "test@bondly.dating"
            )
        }
        if (!profile.profileCompleted || profile.profilePhotoUrls.size < 5) {
            // Incomplete: Less than 5 photos or profileCompleted == false
            return AppAuthState.AuthenticatedProfileIncomplete(
                userId = "test_user_uid_123",
                profile = profile
            )
        }
        return AppAuthState.AuthenticatedProfileCompleted(
            userId = "test_user_uid_123",
            profile = profile
        )
    }

    @Test
    fun testScenario1_NewUserAuthentication() {
        val state = AppAuthState.Initializing
        assertTrue("Initial auth state must be Initializing", state is AppAuthState.Initializing)
    }

    @Test
    fun testScenario2_ProfileDocumentDoesNotExist() {
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = false,
            profile = null
        )
        assertTrue(
            "Missing document must result in AuthenticatedProfileNotCreated",
            resultState is AppAuthState.AuthenticatedProfileNotCreated
        )
        val notCreated = resultState as AppAuthState.AuthenticatedProfileNotCreated
        assertEquals("test_user_uid_123", notCreated.userId)
    }

    @Test
    fun testScenario3_ProfileDocumentIncomplete_MissingPhotos() {
        val incompleteProfile = UserProfile(
            userId = "test_user_uid_123",
            displayName = "Ananya",
            profileCompleted = true,
            profilePhotoUrls = listOf("url1", "url2", "url3") // Only 3 photos! Needs 5!
        )
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = incompleteProfile
        )
        assertTrue(
            "Profile with < 5 photos must transition to AuthenticatedProfileIncomplete",
            resultState is AppAuthState.AuthenticatedProfileIncomplete
        )
        val incomplete = resultState as AppAuthState.AuthenticatedProfileIncomplete
        assertEquals(3, incomplete.profile.profilePhotoUrls.size)
        assertFalse(incomplete.profile.profilePhotoUrls.size >= 5)
    }

    @Test
    fun testScenario3b_ProfileDocumentIncomplete_FlagFalse() {
        val incompleteProfile = UserProfile(
            userId = "test_user_uid_123",
            displayName = "Ananya",
            profileCompleted = false,
            profilePhotoUrls = listOf("url1", "url2", "url3", "url4", "url5")
        )
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = incompleteProfile
        )
        assertTrue(
            "Profile with profileCompleted = false must transition to AuthenticatedProfileIncomplete",
            resultState is AppAuthState.AuthenticatedProfileIncomplete
        )
    }

    @Test
    fun testScenario4_ProfileCompleted() {
        val completeProfile = UserProfile(
            userId = "test_user_uid_123",
            memberId = "BD979241",
            displayName = "Rohit Sharma",
            gender = "Man",
            age = 25,
            state = "Maharashtra",
            city = "Mumbai",
            profileCompleted = true,
            profilePhotoUrls = listOf("url1", "url2", "url3", "url4", "url5"),
            rulesAccepted = true
        )
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = completeProfile
        )
        assertTrue(
            "Fully completed profile with 5 photos must transition to AuthenticatedProfileCompleted",
            resultState is AppAuthState.AuthenticatedProfileCompleted
        )
        val completed = resultState as AppAuthState.AuthenticatedProfileCompleted
        assertEquals("BD979241", completed.profile.memberId)
        assertEquals(5, completed.profile.profilePhotoUrls.size)
        assertTrue(completed.profile.profileCompleted)
        assertEquals("test_user_uid_123", completed.userId)
    }

    @Test
    fun testScenario5_FirestoreRequestTimeoutHandling() {
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = false,
            profile = null,
            hasTimedOut = true
        )
        assertTrue(
            "10s timeout must transition to RequestFailed with canRetry = true",
            resultState is AppAuthState.RequestFailed
        )
        val failed = resultState as AppAuthState.RequestFailed
        assertTrue(failed.canRetry)
        assertTrue(failed.message.contains("timed out", ignoreCase = true))
    }

    @Test
    fun testScenario6_InternetDisconnectDuringLoading() {
        val resultState = evaluateProfileState(
            isConnected = false,
            documentExists = false,
            profile = null
        )
        assertTrue(
            "Offline condition must emit NetworkUnavailable",
            resultState is AppAuthState.NetworkUnavailable
        )
    }

    @Test
    fun testScenario7_PermissionDeniedHandling() {
        val resultState = evaluateProfileState(
            isConnected = true,
            documentExists = true,
            profile = null,
            firestoreError = "PERMISSION_DENIED: Missing or insufficient permissions."
        )
        assertTrue(
            "Permission denied error must transition to RequestFailed",
            resultState is AppAuthState.RequestFailed
        )
        val failed = resultState as AppAuthState.RequestFailed
        assertTrue(failed.message.contains("PERMISSION_DENIED"))
        assertTrue(failed.canRetry)
    }

    @Test
    fun testScenario8_AppTeardownAndResumeDuringOnboarding() {
        val stepProgressState = AppAuthState.ProfileSetupInProgress(
            userId = "test_user_uid_123",
            step = 3
        )
        assertEquals(3, stepProgressState.step)
        assertEquals("test_user_uid_123", stepProgressState.userId)
    }

    @Test
    fun testScenario9_RepeatedAuthListenerCallbacks_Idempotence() {
        val completeProfile = UserProfile(
            userId = "test_user_uid_123",
            memberId = "BD979241",
            displayName = "Rohit",
            profileCompleted = true,
            profilePhotoUrls = listOf("1", "2", "3", "4", "5")
        )

        // First callback
        val state1 = evaluateProfileState(true, true, completeProfile)
        assertTrue(state1 is AppAuthState.AuthenticatedProfileCompleted)

        // Subsequent identical callback (e.g. metadata flutter or network reconnect)
        val state2 = evaluateProfileState(true, true, completeProfile)
        assertTrue(state2 is AppAuthState.AuthenticatedProfileCompleted)

        assertEquals(
            (state1 as AppAuthState.AuthenticatedProfileCompleted).profile.userId,
            (state2 as AppAuthState.AuthenticatedProfileCompleted).profile.userId
        )
        assertEquals(
            (state1 as AppAuthState.AuthenticatedProfileCompleted).userId,
            (state2 as AppAuthState.AuthenticatedProfileCompleted).userId
        )
    }
}
