package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PublicUserIndex(
    val publicUserId: String = "",
    val userId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
