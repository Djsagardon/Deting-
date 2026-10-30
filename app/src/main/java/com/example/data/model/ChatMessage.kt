package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String = "",
    val status: String = "sent", // sending, sent, delivered, read
    val isDeletedForEveryone: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val deletedForUserIds: List<String> = emptyList(),
    val deletedAt: Timestamp? = null,
    val localTimestamp: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    /**
     * Determines if this message has been deleted for everyone by the sender.
     */
    fun isDeleted(): Boolean = isDeletedForEveryone || deletedForEveryone

    /**
     * Determines if this message has been deleted only for the specified user.
     */
    fun isDeletedForUser(userId: String): Boolean = deletedForUserIds.contains(userId)
}
