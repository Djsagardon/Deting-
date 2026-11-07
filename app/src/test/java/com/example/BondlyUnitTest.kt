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
}
