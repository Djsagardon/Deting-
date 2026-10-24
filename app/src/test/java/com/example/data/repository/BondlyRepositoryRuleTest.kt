package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BondlyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveUserProfile_authenticatedUser_succeeds(): Unit = runBlocking {
        val uid = signInTestUser("alice@test.com")
        val repo = BondlyRepository(firestore, auth)

        val profile = UserProfile(
            userId = uid,
            publicUserId = "BD847291",
            displayName = "Alice Wonderland",
            age = 24,
            bio = "Lover of coffee, hiking, and deep chats.",
            gender = "Female",
            city = "Mumbai",
            state = "Maharashtra",
            profileCompleted = true,
            accountStatus = "active"
        )

        val result = withTimeout(5000L) {
            repo.saveUserProfile(profile, rawMobileNumber = "9876543210")
        }
        if (result.isFailure) {
            System.err.println("SAVE_USER_PROFILE ERROR: " + result.exceptionOrNull()?.message)
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue(result.isSuccess)

        val saved = withTimeout(5000L) {
            repo.getUserProfile(uid)
        }
        assertNotNull(saved)
        assertEquals("Alice Wonderland", saved?.displayName)
        assertEquals("BD847291", saved?.publicUserId)
        assertEquals("Maharashtra", saved?.state)
    }

    @Test
    fun observeUserProfile_authenticatedOwner_emitsData(): Unit = runBlocking {
        val uid = signInTestUser("bob@test.com")
        val repo = BondlyRepository(firestore, auth)

        val profile = UserProfile(
            userId = uid,
            publicUserId = "BD112233",
            displayName = "Bob Smith",
            age = 28,
            accountStatus = "active",
            profileCompleted = true
        )
        withTimeout(5000L) { repo.saveUserProfile(profile) }

        val emitted = withTimeout(5000L) {
            repo.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(emitted)
        assertEquals("Bob Smith", emitted?.displayName)
    }

    @Test
    fun searchByPublicUserId_existingUser_returnsProfile(): Unit = runBlocking {
        val uid = signInTestUser("charlie@test.com")
        val repo = BondlyRepository(firestore, auth)

        val publicId = "BD778899"
        val profile = UserProfile(
            userId = uid,
            publicUserId = publicId,
            displayName = "Charlie",
            age = 29,
            accountStatus = "active",
            profileCompleted = true
        )
        withTimeout(5000L) { repo.saveUserProfile(profile) }

        val found = withTimeout(5000L) {
            repo.searchByPublicUserId(publicId)
        }
        assertNotNull(found)
        assertEquals("Charlie", found?.displayName)
    }

    @Test
    fun saveUserProfile_unauthenticatedUser_fails(): Unit = runBlocking {
        auth.signOut()
        val repo = BondlyRepository(firestore, auth)

        val profile = UserProfile(
            userId = "unauth_user",
            publicUserId = "BD999999",
            displayName = "Unauth",
            age = 22
        )

        var failed = false
        try {
            withTimeout(3000L) { repo.saveUserProfile(profile) }
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun observeUserProfileState_nonExistingProfile_emitsNotFound(): Unit = runBlocking {
        val uid = signInTestUser("newuser@test.com")
        val repo = BondlyRepository(firestore, auth)

        val state = withTimeout(5000L) {
            repo.observeUserProfileState(uid).first()
        }
        assertEquals(com.example.data.model.UserProfileState.NotFound, state)
    }

    @Test
    fun observeUserProfileState_existingCompleteProfile_emitsLoaded(): Unit = runBlocking {
        val uid = signInTestUser("existing@test.com")
        val repo = BondlyRepository(firestore, auth)

        val profile = UserProfile(
            userId = uid,
            publicUserId = "BD554433",
            displayName = "David Miller",
            age = 27,
            accountStatus = "active",
            profileCompleted = true
        )
        withTimeout(5000L) { repo.saveUserProfile(profile) }

        val state = withTimeout(5000L) {
            repo.observeUserProfileState(uid).first { it is com.example.data.model.UserProfileState.Loaded }
        }
        assertTrue(state is com.example.data.model.UserProfileState.Loaded)
        val loaded = state as com.example.data.model.UserProfileState.Loaded
        assertEquals("David Miller", loaded.profile.displayName)
    }

    @Test
    fun checkAndInitializeProfile_nonExistingUser_returnsNotFound(): Unit = runBlocking {
        val uid = signInTestUser("init_check@test.com")
        val repo = BondlyRepository(firestore, auth)

        val result = repo.checkAndInitializeProfile(uid)
        assertTrue(result.isSuccess)
        assertEquals(com.example.data.model.UserProfileState.NotFound, result.getOrNull())
    }

    @Test
    fun sendMessage_withPhoneNumber_rejectedByContactFilter(): Unit = runBlocking {
        val uid = signInTestUser("sender@test.com")
        val repo = BondlyRepository(firestore, auth)

        val result = repo.sendMessage("match_123", "Call me on 9876543210")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
