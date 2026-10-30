package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val userId: String = "",
    val publicUserId: String = "",
    val displayName: String = "",
    val dob: String = "",
    val age: Int = 18,
    val bio: String = "",
    val gender: String = "Female", // Strictly "Male" or "Female"
    val interests: List<String> = emptyList(), // Predefined Indian hobbies/interests
    val city: String = "",
    val state: String = "", // Indian State or Union Territory
    val maskedMobileNumber: String = "", // e.g. "+91 ••••• 9876", actual number stored in private subcollection
    val isMobileVerified: Boolean = false,
    val profilePhotoUrls: List<String> = emptyList(), // Exactly 5 photos required for profile completion
    val totalPhotoCount: Int = 0,
    val datingPreference: String = "Long-term relationship",
    val rulesAccepted: Boolean = false,
    val profileCompleted: Boolean = false,
    val verificationStatus: String = "verified",
    val accountStatus: String = "active",
    val allowCallsFrom: String = "matches",
    val showOnlineStatus: Boolean = true,
    val isPremium: Boolean = false,
    val userLevel: Int = 1,
    val activeDaysCount: Int = 1,
    val lastActiveDate: String = "",
    val role: String = "user", // "user" or "admin"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
)
