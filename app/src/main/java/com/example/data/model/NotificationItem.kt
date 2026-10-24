package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class NotificationItem(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "match", // match, call, safety, system
    val targetId: String = "", // userId, callId, or matchId
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
