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
    val age: Int = 0,
    val bio: String = "",
    val gender: String = "",
    val interests: List<String> = emptyList(),
    val city: String = "",
    val state: String = "",
    val maskedMobileNumber: String = "",
    val isMobileVerified: Boolean = false,
    val profilePhotoUrls: List<String> = emptyList(),
    val totalPhotoCount: Int = 0,
    val datingPreference: String = "Anyone",
    val rulesAccepted: Boolean = false,
    val profileCompleted: Boolean = false,
    val verificationStatus: String = "unverified",
    val accountStatus: String = "active",
    val allowCallsFrom: String = "matches",
    val showOnlineStatus: Boolean = true,
    val isPremium: Boolean = false,
    val userLevel: Int = 1,
    val activeDaysCount: Int = 1,
    val lastActiveDate: String = "",
    val role: String = "user",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
)

sealed class UserProfileState {
    data object Loading : UserProfileState()
    data object NotFound : UserProfileState()
    data class Incomplete(val profile: UserProfile) : UserProfileState()
    data class Loaded(val profile: UserProfile) : UserProfileState()
    data class Error(val message: String) : UserProfileState()
}

@IgnoreExtraProperties
data class ChatMessage(
    val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val status: String = "sent",
    val deletedForEveryone: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val localTimestamp: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val deletedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    val callId: String = "",
    val callerUid: String = "",
    val receiverUid: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val receiverName: String = "",
    val channelName: String = "",
    val callType: String = "voice",
    val status: String = "initiated",
    val durationSeconds: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Match(
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val user1Name: String = "",
    val user2Name: String = "",
    val user1Photo: String = "",
    val user2Photo: String = "",
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Like(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val type: String = "like",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Block(
    val id: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Report(
    val id: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "system",
    val targetId: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicUserIndex(
    val publicUserId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val city: String = "",
    val gender: String = "",
    val age: Int = 0,
    val photoUrl: String = ""
)
