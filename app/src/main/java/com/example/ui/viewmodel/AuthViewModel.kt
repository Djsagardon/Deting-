package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ProfileResolutionResult
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.util.BondlyErrorClassifier
import com.example.util.NetworkConnectivityManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * AuthViewModel manages the authentication lifecycle and profile resolution.
 * Uses a single-flight Source.SERVER check followed by a safe real-time listener,
 * deduplicates concurrent AuthStateListener + Google Sign-In callbacks,
 * prevents legacy schema crashes, and strictly separates network/permission errors
 * from genuinely missing or incomplete profiles.
 */
class AuthViewModel(
    val repository: BondlyRepository = BondlyRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "BondlyAuthVM"
        private const val SERVER_FETCH_TIMEOUT_MS = 12_000L
    }

    private val _authState = MutableStateFlow<AppAuthState>(AppAuthState.Initializing)
    val authState: StateFlow<AppAuthState> = _authState.asStateFlow()

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var profileListenerRegistration: ListenerRegistration? = null
    private var resolutionJob: Job? = null

    private var currentFirebaseUser: FirebaseUser? = null
    private var resolvingUid: String? = null
    private var resolvedUid: String? = null
    private var isProfileAlreadyCompleted: Boolean = false

    init {
        listenToAuthChanges()
    }

    private fun listenToAuthChanges() {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            currentFirebaseUser = user
            if (user == null) {
                isProfileAlreadyCompleted = false
                resolvingUid = null
                resolvedUid = null
                cleanupResolution()
                if (_authState.value !is AppAuthState.AuthFailed) {
                    _authState.value = AppAuthState.Unauthenticated
                }
            } else {
                resolveUserProfile(user, forceRefresh = false)
            }
        }
        authListener = listener
        Firebase.auth.addAuthStateListener(listener)
    }

    /**
     * Resolves an authenticated user's Firestore profile via an authoritative server read first,
     * deduplicating overlapping callbacks from FirebaseAuth.AuthStateListener and Google Sign-In UI.
     */
    fun resolveUserProfile(user: FirebaseUser, forceRefresh: Boolean = false) {
        val uid = user.uid
        currentFirebaseUser = user

        if (!forceRefresh) {
            // Single-flight guard: ignore duplicate calls while already resolving the same UID
            if (resolvingUid == uid && resolutionJob?.isActive == true) {
                Log.d(TAG, "Deduplicating concurrent resolveUserProfile for uid=$uid")
                return
            }
            // Already confirmed completed for this exact UID; keep active listener without flicker
            if (resolvedUid == uid &&
                isProfileAlreadyCompleted &&
                _authState.value is AppAuthState.AuthenticatedProfileCompleted
            ) {
                Log.d(TAG, "Profile already resolved & completed for uid=$uid")
                return
            }
        }

        if (!NetworkConnectivityManager.isConnected()) {
            if (!isProfileAlreadyCompleted || resolvedUid != uid) {
                _authState.value = AppAuthState.NetworkUnavailable
            }
            return
        }

        cleanupResolution()
        resolvingUid = uid
        if (resolvedUid != uid) {
            isProfileAlreadyCompleted = false
            resolvedUid = null
        }

        // Keep current screen state for same UID if already in onboarding/main, otherwise Initializing
        val current = _authState.value
        val sameUidInProgress = when (current) {
            is AppAuthState.AuthenticatedProfileCompleted -> current.userId == uid
            is AppAuthState.AuthenticatedProfileIncomplete -> current.userId == uid
            is AppAuthState.AuthenticatedProfileNotCreated -> current.userId == uid
            is AppAuthState.ProfileSetupInProgress -> current.userId == uid
            else -> false
        }
        if (!sameUidInProgress) {
            _authState.value = AppAuthState.Initializing
        }

        resolutionJob = viewModelScope.launch {
            val result = withTimeoutOrNull(SERVER_FETCH_TIMEOUT_MS) {
                repository.fetchUserProfileResolution(uid)
            }

            // Discard stale result if user signed out or switched account while fetching
            val activeUid = currentFirebaseUser?.uid ?: Firebase.auth.currentUser?.uid
            if (activeUid != uid) {
                if (resolvingUid == uid) resolvingUid = null
                return@launch
            }

            resolvingUid = null

            if (result == null) {
                if (!isProfileAlreadyCompleted) {
                    _authState.value = AppAuthState.RequestFailed(
                        message = "Connection timed out while verifying your Bondly profile. Please check your internet connection.",
                        canRetry = true
                    )
                }
                return@launch
            }

            result.onFailure { err ->
                Log.e(TAG, "Server profile resolution failed for uid=$uid: ${err.message}")
                if (isProfileAlreadyCompleted || _authState.value is AppAuthState.AuthenticatedProfileCompleted) {
                    return@onFailure
                }
                if (!NetworkConnectivityManager.isConnected()) {
                    _authState.value = AppAuthState.NetworkUnavailable
                } else {
                    _authState.value = AppAuthState.RequestFailed(
                        message = BondlyErrorClassifier.getUserFriendlyMessage(err),
                        canRetry = true
                    )
                }
            }.onSuccess { resolution ->
                applyResolution(user, resolution)
                attachProfileRealtimeListener(user)
            }
        }
    }

    private fun applyResolution(user: FirebaseUser, resolution: ProfileResolutionResult) {
        val uid = user.uid
        resolvedUid = uid
        when (resolution) {
            is ProfileResolutionResult.Completed -> {
                isProfileAlreadyCompleted = true
                _authState.value = AppAuthState.AuthenticatedProfileCompleted(
                    userId = uid,
                    profile = resolution.profile
                )
            }

            is ProfileResolutionResult.Incomplete -> {
                // CRITICAL: Once a user has completed onboarding and entered the Main App,
                // never allow transient partial writes (presence/token sync) to kick them back.
                if (isProfileAlreadyCompleted || _authState.value is AppAuthState.AuthenticatedProfileCompleted) {
                    return
                }
                _authState.value = AppAuthState.AuthenticatedProfileIncomplete(
                    userId = uid,
                    profile = resolution.profile
                )
            }

            is ProfileResolutionResult.NotCreated -> {
                if (isProfileAlreadyCompleted || _authState.value is AppAuthState.AuthenticatedProfileCompleted) {
                    return
                }
                _authState.value = AppAuthState.AuthenticatedProfileNotCreated(
                    userId = uid,
                    email = user.email ?: ""
                )
            }
        }
    }

    private fun attachProfileRealtimeListener(user: FirebaseUser) {
        profileListenerRegistration?.remove()
        profileListenerRegistration = repository.listenToProfileResolution(
            uid = user.uid,
            onResolution = { resolution ->
                val activeUid = currentFirebaseUser?.uid ?: Firebase.auth.currentUser?.uid
                if (activeUid == user.uid) {
                    applyResolution(user, resolution)
                }
            },
            onError = { error ->
                Log.w(TAG, "Profile listener note for uid=${user.uid}: ${error.message}")
                // Never demote an already loaded/completed session on transient listener error
            }
        )
    }

    fun onProfileCreationSuccess(profile: UserProfile) {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user != null) {
            isProfileAlreadyCompleted = true
            resolvedUid = user.uid
            resolvingUid = null
            _authState.value = AppAuthState.AuthenticatedProfileCompleted(user.uid, profile)
        }
    }

    fun setProfileSetupStep(step: Int) {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user != null && !isProfileAlreadyCompleted) {
            _authState.value = AppAuthState.ProfileSetupInProgress(user.uid, step)
        }
    }

    fun retry() {
        val user = currentFirebaseUser ?: Firebase.auth.currentUser
        if (user == null) {
            _authState.value = AppAuthState.Unauthenticated
        } else {
            resolveUserProfile(user, forceRefresh = true)
        }
    }

    fun signOut(context: Context? = null) {
        isProfileAlreadyCompleted = false
        resolvingUid = null
        resolvedUid = null
        cleanupResolution()
        repository.signOut()
        currentFirebaseUser = null
        _authState.value = AppAuthState.Unauthenticated

        if (context != null) {
            val appCtx = context.applicationContext
            viewModelScope.launch {
                try {
                    CredentialManager.create(appCtx).clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Credential state clear skipped: ${e.message}")
                }
            }
        }
    }

    private fun cleanupResolution() {
        resolutionJob?.cancel()
        resolutionJob = null
        profileListenerRegistration?.remove()
        profileListenerRegistration = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupResolution()
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        authListener = null
    }
}
