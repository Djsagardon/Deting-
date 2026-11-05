package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.BondlyRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BondlyFirebaseMessagingService : FirebaseMessagingService() {

    private val repository by lazy { BondlyRepository() }
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_CALLS = "bondly_calls_channel"
        const val CHANNEL_MATCHES = "bondly_matches_channel"
        const val CHANNEL_MESSAGES = "bondly_messages_channel"
        const val CHANNEL_ANNOUNCEMENTS = "bondly_announcements_channel"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            repository.registerFcmToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = data["title"] ?: notification?.title ?: "Bondly"
        val body = data["body"] ?: data["message"] ?: notification?.body ?: "New notification"
        val type = data["type"] ?: "system"
        val targetId = data["targetId"] ?: ""
        val senderName = data["senderName"] ?: ""

        sendPushNotification(title, body, type, targetId, senderName)
    }

    private fun sendPushNotification(
        title: String,
        body: String,
        type: String,
        targetId: String,
        senderName: String
    ) {
        val channelId = when (type) {
            "call", "voice_call", "video_call" -> CHANNEL_CALLS
            "match_request", "match", "match_accepted" -> CHANNEL_MATCHES
            "chat", "message" -> CHANNEL_MESSAGES
            else -> CHANNEL_ANNOUNCEMENTS
        }

        createNotificationChannels()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", type)
            putExtra("target_id", targetId)
            putExtra("sender_name", senderName)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(
            if (type in listOf("call", "voice_call", "video_call")) RingtoneManager.TYPE_RINGTONE
            else RingtoneManager.TYPE_NOTIFICATION
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.bondly_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (type in listOf("call", "voice_call", "video_call", "match_request"))
                    NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )

        if (type in listOf("call", "voice_call", "video_call")) {
            builder.setCategory(NotificationCompat.CATEGORY_CALL)
            builder.setOngoing(true)
        }

        try {
            val notificationManager = NotificationManagerCompat.from(this)
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Handled when notification permission is not granted
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls & Urgent Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming voice and video call alerts"
                enableVibration(true)
            }

            val matchChannel = NotificationChannel(
                CHANNEL_MATCHES,
                "Matches & Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New matches and like requests"
                enableVibration(true)
            }

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming chat messages from matches"
                enableVibration(true)
            }

            val announcementChannel = NotificationChannel(
                CHANNEL_ANNOUNCEMENTS,
                "Announcements & System",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Official announcements and safety alerts"
            }

            notificationManager.createNotificationChannels(
                listOf(callChannel, matchChannel, messageChannel, announcementChannel)
            )
        }
    }
}
