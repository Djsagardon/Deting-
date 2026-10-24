package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Like(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val type: String = "like", // like, super_like, pass
    @ServerTimestamp val createdAt: Timestamp? = null
)
