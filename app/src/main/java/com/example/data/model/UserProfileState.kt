package com.example.data.model

sealed interface UserProfileState {
    object Loading : UserProfileState
    object NotFound : UserProfileState
    data class Incomplete(val profile: UserProfile) : UserProfileState
    data class Loaded(val profile: UserProfile) : UserProfileState
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val cause: Throwable? = null
    ) : UserProfileState
}
