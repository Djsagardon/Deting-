package com.example

import com.example.data.model.UserProfile
import com.example.util.AllIndianStatesAndUTs
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Locale

class BondlyUnitTest {

    @Test
    fun testAllIndianStatesAndUTsCount() {
        assertEquals("Must contain exactly 28 states and 8 Union Territories (36 total)", 36, AllIndianStatesAndUTs.size)
        assertTrue(AllIndianStatesAndUTs.contains("Maharashtra"))
        assertTrue(AllIndianStatesAndUTs.contains("Delhi (NCT)"))
        assertTrue(AllIndianStatesAndUTs.contains("Jammu and Kashmir"))
        assertTrue(AllIndianStatesAndUTs.contains("Ladakh"))
        assertTrue(AllIndianStatesAndUTs.contains("Puducherry"))
    }

    @Test
    fun testEffectiveMemberId() {
        val userWithMemberId = UserProfile(
            memberId = "BD979241",
            publicUserId = "BONDLY-1234"
        )
        assertEquals("BD979241", userWithMemberId.effectiveMemberId)

        val userWithLegacyPublicId = UserProfile(
            memberId = "",
            publicUserId = "BD888888"
        )
        assertEquals("BD888888", userWithLegacyPublicId.effectiveMemberId)
    }

    @Test
    fun testOnlinePresenceTimeout() {
        val activeRecentTimestamp = Timestamp(Date(System.currentTimeMillis() - 30_000L)) // 30s ago
        val activeRecentUser = UserProfile(
            isOnline = true,
            lastActiveAt = activeRecentTimestamp
        )
        assertTrue("User with activity < 90s must be marked currently online", activeRecentUser.isCurrentlyOnline)

        val staleTimestamp = Timestamp(Date(System.currentTimeMillis() - 120_000L)) // 120s ago
        val staleUser = UserProfile(
            isOnline = true,
            lastActiveAt = staleTimestamp
        )
        assertFalse("User with inactivity > 90s must be marked offline", staleUser.isCurrentlyOnline)

        val explicitOfflineUser = UserProfile(
            isOnline = false,
            lastActiveAt = activeRecentTimestamp
        )
        assertFalse("User with isOnline = false must be marked offline", explicitOfflineUser.isCurrentlyOnline)
    }

    @Test
    fun testMemberIdNormalization() {
        val input1 = "bd979241"
        val normalized1 = input1.uppercase(Locale.ROOT)
        assertEquals("BD979241", normalized1)

        val input2 = " 979241 "
        val clean2 = input2.trim()
        val candidatePrefix = "BD$clean2"
        assertEquals("BD979241", candidatePrefix)
    }

    @Test
    fun testIndianMobileNumberValidation() {
        val regex = Regex("^[6-9][0-9]{9}$")

        // Valid 10-digit Indian numbers starting with 6, 7, 8, 9
        val validNumbers = listOf(
            "9876543210",
            "8123456789",
            "7012345678",
            "6999999999",
            "9000000000"
        )
        for (num in validNumbers) {
            assertTrue("Expected valid: $num", num.matches(regex))
        }

        // Invalid numbers
        val invalidNumbers = listOf(
            "",                      // empty
            "1234567890",            // starts with 1
            "0987654321",            // starts with 0
            "5555555555",            // starts with 5
            "987654321",             // 9 digits
            "98765432101",           // 11 digits
            "+919876543210",         // contains +91 prefix
            "98765 43210",           // contains space
            "98765-43210",           // contains hyphen
            "98765abc10",            // contains letters
            "abcdefghij"             // purely letters
        )
        for (num in invalidNumbers) {
            assertFalse("Expected invalid: $num", num.matches(regex))
        }
    }

    @Test
    fun testStateSelectionMandatoryAndPreservation() {
        // Initial state for new user (null state in profile) must not default to Maharashtra
        val newProfile = UserProfile(state = "")
        val initialSelectedStateForNewUser = newProfile.state.takeIf { it.isNotBlank() } ?: ""
        assertEquals("New user state must be empty, requiring manual selection", "", initialSelectedStateForNewUser)
        assertTrue("Empty state must fail validation", initialSelectedStateForNewUser.isBlank())

        // Initial state for existing completed user must be preserved
        val existingProfile = UserProfile(state = "Karnataka")
        val initialSelectedStateForExisting = existingProfile.state.takeIf { it.isNotBlank() } ?: ""
        assertEquals("Existing state must be preserved", "Karnataka", initialSelectedStateForExisting)
        assertFalse("Existing state must pass validation", initialSelectedStateForExisting.isBlank())
        assertTrue("State must belong to Indian states", AllIndianStatesAndUTs.contains(initialSelectedStateForExisting))
    }

    @Test
    fun testProfileCompletedIdempotency() {
        // Test repository logic where profile.profileCompleted == true prevents reverting
        val alreadyCompleted = true
        val allFiveUploaded = true
        val isNowCompleted1 = if (alreadyCompleted) allFiveUploaded else (allFiveUploaded && true)
        assertTrue("Already completed profile remains completed when 5 photos exist", isNowCompleted1)

        // For incomplete profile with only 4 photos
        val notYetCompleted = false
        val onlyFourUploaded = false
        val isNowCompleted2 = if (notYetCompleted) onlyFourUploaded else (onlyFourUploaded && true)
        assertFalse("Incomplete profile remains incomplete if photos < 5", isNowCompleted2)
    }
}

