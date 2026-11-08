package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.util.NetworkConnectivityManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Robust, production-ready Auth & Profile State Model for Bondly.
 * Represents the single source of truth for user lifecycle.
 */
sealed class AppAuthState {
    data object Initializing : AppAuthState()
    data object Unauthenticated : AppAuthState()
    data object NetworkUnavailable : AppAuthState()
    data class AuthFailed(val message: String) : AppAuthState()
    data class AuthenticatedProfileNotCreated(val userId: String, val email: String = "") : AppAuthState()
    data class AuthenticatedProfileIncomplete(val userId: String, val profile: UserProfile) : AppAuthState()
    data class ProfileSetupInProgress(val userId: String, val step: Int) : AppAuthState()
    data class AuthenticatedProfileCompleted(val userId: String, val profile: UserProfile) : AppAuthState()
    data class RequestFailed(val message: String, val canRetry: Boolean = true) : AppAuthState()
}

/**
 * AuthViewModel manages the authentication lifecycle and profile initialization.
 * Prevents Compose recomposition loops, avoids premature background service registration,
 * eliminates metadata over-firing, and enforces timeouts with retry capabilities.
 */
class AuthViewModel(
    val repository: BondlyRepository = BondlyRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AppAuthState>(AppAuthState.Initializing)
    val authState: StateFlow<AppAuthState> = _authState.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var profileListenerRegistration: ListenerRegistration? = null
    private var timeoutJob: Job? = null
    private var currentFirebaseUser: FirebaseUser? = null

    init {
        listenToAuthChanges()
    }

    private fun listenToAuthChanges() {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            currentFirebaseUser = user
            if (user == null) {
                cleanupProfileListener()
                _authState.value = AppAuthState.Unauthenticated
            } else {
                resolveUserProfile(user)
            }
        }
        authListener = listener
        Firebase.auth.addAuthStateListener(listener)
    }

    fun resolveUserProfile(user: FirebaseUser) {
        if (!NetworkConnectivityManager.isConnected()) {
            _authState.value = AppAuthState.NetworkUnavailable
            return
        }

        cleanupProfileListener()

        // Keep current state if already loaded/incomplete to prevent UI flicker, otherwise Initializing
        val current = _authState.value
        if (current !is AppAuthState.AuthenticatedProfileCompleted &&
            current !is AppAuthState.AuthenticatedProfileIncomplete &&
            current !is AppAuthState.AuthenticatedProfileNotCreated &&
            current !is AppAuthState.ProfileSetupInProgress
        ) {
            _authState.value = AppAuthState.Initializing
        }

        // 10-second timeout to prevent indefinite loading spinners
        timeoutJob = viewModelScope.launch {
            delay(10_000L)
            if (_authState.value is AppAuthState.Initializing) {
                _authState.value = AppAuthState.RequestFailed(
                    message = "Connection timed out while verifying your Bondly profile. Please check your internet connection.",
                    canRetry = true
                )
            }
        }

        // Use MetadataChanges.EXCLUDE to eliminate local write metadata flutter
        profileListenerRegistration = Firebase.firestore.collection("users").document(user.uid)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                timeoutJob?.cancel()
                timeoutJob = null

                if (error != null) {
                    if (!NetworkConnectivityManager.isConnected()) {
                        _authState.value = AppAuthState.NetworkUnavailable
                    } else {
                        _authState.value = AppAuthState.RequestFailed(
                            message = error.localizedMessage ?: "Failed to load user profile.",
                            canRetry = true
                        )
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    // Fresh account - profile document not created yet
                    _authState.value = AppAuthState.AuthenticatedProfileNotCreated(
                        userId = user.uid,
                        email = user.email ?: ""
                    )
                    return@addSnapshotListener
                }

                val profile = snapshot.toObject(UserProfile::class.java)?.let { p ->
                    val mId = snapshot.getString("memberId") ?: snapshot.getString("publicUserId") ?: ""
                    p.copy(
                        userId = if (p.userId.isNotBlank()) p.userId else snapshot.id,
                        memberId = if (p.memberId.isNotBlank()) p.memberId else mId,
                        publicUserId = if (p.publicUserId.isNotBlank()) p.publicUserId else mId
                    )
                }

                if (profile == null) {
                    _authState.value = AppAuthState.AuthenticatedProfileNotCreated(
                        userId = user.uid,
                        email = user.email ?: ""
                    )
                } else if (!profile.profileCompleted || profile.profilePhotoUrls.size < 5) {
                    // Mandatory 5 photos and profile completion flag required
                    _authState.value = AppAuthState.AuthenticatedProfileIncomplete(
                        userId = user.uid,
                        profile = profile
                    )
                } else {
                    _authState.value = AppAuthState.AuthenticatedProfileCompleted(
                        userId = user.uid,
                        profile = profile
                    )
                }
            }
    }

    fun onProfileCreationSuccess(profile: UserProfile) {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user != null) {
            _authState.value = AppAuthState.AuthenticatedProfileCompleted(user.uid, profile)
        }
    }

    fun setProfileSetupStep(step: Int) {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user != null) {
            _authState.value = AppAuthState.ProfileSetupInProgress(user.uid, step)
        }
    }

    fun retry() {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user == null) {
            _authState.value = AppAuthState.Unauthenticated
        } else {
            resolveUserProfile(user)
        }
    }

    fun signOut() {
        cleanupProfileListener()
        Firebase.auth.signOut()
        currentFirebaseUser = null
        _authState.value = AppAuthState.Unauthenticated
    }

    private fun cleanupProfileListener() {
        timeoutJob?.cancel()
        timeoutJob = null
        profileListenerRegistration?.remove()
        profileListenerRegistration = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupProfileListener()
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        authListener = null
    }
}
