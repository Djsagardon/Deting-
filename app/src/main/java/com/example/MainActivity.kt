package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.BondlyApp
import com.example.ui.theme.BondlyTheme

data class NotificationPayload(
    val type: String = "",
    val targetId: String = "",
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private var activeNotificationPayload by mutableStateOf<NotificationPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractNotificationPayload(intent)
        setContent {
            BondlyTheme {
                BondlyApp(
                    incomingNotification = activeNotificationPayload,
                    onConsumeNotification = { activeNotificationPayload = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractNotificationPayload(intent)
    }

    private fun extractNotificationPayload(intent: Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("notification_type") ?: intent.getStringExtra("type") ?: ""
        val targetId = intent.getStringExtra("target_id") ?: intent.getStringExtra("targetId") ?: ""
        val senderName = intent.getStringExtra("sender_name") ?: intent.getStringExtra("senderName") ?: ""
        if (type.isNotBlank() || targetId.isNotBlank()) {
            activeNotificationPayload = NotificationPayload(
                type = type,
                targetId = targetId,
                senderName = senderName
            )
        }
    }
}

