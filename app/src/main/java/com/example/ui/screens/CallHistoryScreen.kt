package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallSession
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallHistoryScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val callHistoryFlow = remember(currentUserId) { repository.observeCallHistory(currentUserId) }
    val calls by callHistoryFlow.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(top = 16.dp)
            .testTag("call_history_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CharcoalCard)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Call History",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Recent audio & video calls with your matches",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (calls == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BondlyPink)
            }
        } else if (calls!!.isEmpty()) {
            EmptyState(
                title = "No Calls Yet",
                description = "When you connect and make audio or video calls with your mutual matches, your call history and durations will appear here.",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(calls!!) { call ->
                    CallLogCard(
                        call = call,
                        currentUserId = currentUserId,
                        onCallBack = { isVideo ->
                            val otherUid = if (call.callerUid == currentUserId) call.receiverUid else call.callerUid
                            val otherName = if (call.callerUid == currentUserId) call.receiverName else call.callerName
                            onStartCall(otherUid, otherName, isVideo)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(
    call: CallSession,
    currentUserId: String,
    onCallBack: (isVideo: Boolean) -> Unit
) {
    val isOutgoing = call.callerUid == currentUserId
    val otherName = if (isOutgoing) call.receiverName else call.callerName
    val isVideo = call.callType == "video"

    val durationText = if (call.durationSeconds > 0) {
        val mins = call.durationSeconds / 60
        val secs = call.durationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    } else {
        when (call.status) {
            "declined" -> "Declined"
            "busy" -> "Busy"
            else -> "Missed"
        }
    }

    val dateFormatted = remember(call.createdAt) {
        val ts = call.createdAt?.toDate() ?: Date()
        SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(ts)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar / Icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isVideo) Color(0x33FF2D75) else Color(0x3300B0FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    tint = if (isVideo) BondlyPink else Color(0xFF00B0FF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherName.ifBlank { "Bondly User" },
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon = if (call.durationSeconds > 0) {
                        Icons.Default.Call
                    } else {
                        Icons.Default.CallMissed
                    }
                    val statusColor = if (call.durationSeconds > 0) Color(0xFF00E676) else Color(0xFFFF4455)
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOutgoing) "Outgoing • $durationText" else "Incoming • $durationText",
                        color = TextLightGrey,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = dateFormatted, color = TextMuted, fontSize = 11.sp)
            }

            // Quick Call Back Button
            IconButton(
                onClick = { onCallBack(isVideo) },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF2D75))
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneCallback,
                    contentDescription = "Call Back",
                    tint = BondlyPink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
