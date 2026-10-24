package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val callId: String = "",
    val callerUid: String = "",
    val receiverUid: String = "",
    val callerName: String = "",
    val receiverName: String = "",
    val channelName: String = "",
    val callType: String = "video", // audio or video
    val status: String = "ringing", // ringing, accepted, declined, busy, ended, failed
    val durationSeconds: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null
)
