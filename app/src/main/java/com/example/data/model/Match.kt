package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Match(
    @DocumentId val id: String = "",
    val user1Id: String = "",
    val user2Id: String = "",
    val users: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    @ServerTimestamp val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    /**
     * Resolves the other participant's user ID reliably across both
     * user1Id/user2Id and users/participants array fields.
     */
    fun getOtherUserId(currentUserId: String): String {
        if (user1Id.isNotBlank() && user1Id != currentUserId) return user1Id
        if (user2Id.isNotBlank() && user2Id != currentUserId) return user2Id
        return (users + participants).firstOrNull { it.isNotBlank() && it != currentUserId } ?: ""
    }

    fun getOtherUserName(currentUserId: String): String {
        val otherId = getOtherUserId(currentUserId)
        return participantNames[otherId] ?: "Match"
    }

    fun getOtherUserPhoto(currentUserId: String): String {
        val otherId = getOtherUserId(currentUserId)
        return participantPhotos[otherId] ?: ""
    }
}
