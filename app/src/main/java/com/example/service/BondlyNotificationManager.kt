package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object BondlyNotificationManager {

    private const val TAG = "BondlyNotifManager"

    const val CHANNEL_CALLS = "bondly_calls_channel"
    const val CHANNEL_MATCHES = "bondly_matches_channel"
    const val CHANNEL_MESSAGES = "bondly_messages_channel"
    const val CHANNEL_ANNOUNCEMENTS = "bondly_announcements_channel"

    /**
     * Initializes notification channels on Android O+.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

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
                "Announcements & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Official announcements, admin messages and community alerts"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(callChannel, matchChannel, messageChannel, announcementChannel)
            )
        }
    }

    /**
     * Displays a system tray notification on the Android device.
     */
    fun showSystemNotification(
        context: Context,
        title: String,
        message: String,
        type: String,
        targetId: String = "",
        senderName: String = ""
    ) {
        try {
            createNotificationChannels(context)

            val managerCompat = NotificationManagerCompat.from(context)
            if (!managerCompat.areNotificationsEnabled()) {
                Log.w(TAG, "Notification permission is not granted by user.")
                return
            }

            val channelId = when (type) {
                "call", "voice_call", "video_call" -> CHANNEL_CALLS
                "match_request", "match", "match_accepted" -> CHANNEL_MATCHES
                "chat", "message" -> CHANNEL_MESSAGES
                else -> CHANNEL_ANNOUNCEMENTS
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("notification_type", type)
                putExtra("target_id", targetId)
                putExtra("sender_name", senderName)
            }

            val requestCode = (title.hashCode() + targetId.hashCode() + System.currentTimeMillis().toInt()) and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(
                if (type in listOf("call", "voice_call", "video_call")) RingtoneManager.TYPE_RINGTONE
                else RingtoneManager.TYPE_NOTIFICATION
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.bondly_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setSound(soundUri)
                .setContentIntent(pendingIntent)
                .setPriority(
                    if (type in listOf("call", "voice_call", "video_call", "match_request", "admin_announcement"))
                        NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )

            if (type in listOf("call", "voice_call", "video_call")) {
                builder.setCategory(NotificationCompat.CATEGORY_CALL)
                builder.setOngoing(true)
            } else if (type == "admin_announcement") {
                builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
            }

            val notificationId = if (targetId.isNotEmpty()) {
                (targetId.hashCode() and 0x7FFFFFFF) % 100000 + 1000
            } else {
                (System.currentTimeMillis() % 100000).toInt()
            }

            managerCompat.notify(notificationId, builder.build())
            Log.d(TAG, "System notification posted: '$title' [ID: $notificationId, Channel: $channelId]")

        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification: ${e.message}", e)
        }
    }
}
