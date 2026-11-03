package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallSession
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val calls by repository.observeCallHistory().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call History", fontWeight = FontWeight.Bold, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalDark)
            )
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        if (calls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "No Recent Calls",
                    description = "When you voice or video call with trusted connections, your history appears here.",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CharcoalCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(36.dp))
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(calls, key = { it.callId }) { call ->
                    val isOutgoing = call.callerUid == currentUserId
                    val partnerName = if (isOutgoing) call.receiverName else call.callerName
                    val partnerUid = if (isOutgoing) call.receiverUid else call.callerUid
                    val isVideo = call.callType == "video"

                    val timeString = remember(call.startedAt) {
                        val d = call.startedAt?.toDate() ?: Date()
                        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(d)
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isVideo) BondlyPink.copy(alpha = 0.15f) else OnlineGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = if (isVideo) BondlyPink else OnlineGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partnerName.ifEmpty { "Bondly Member" },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOutgoing) Icons.Filled.CallMade else Icons.Filled.CallReceived,
                                        contentDescription = null,
                                        tint = if (call.status == "rejected" || call.status == "missed") ErrorRed else OnlineGreen,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${if (isOutgoing) "Outgoing" else "Incoming"} · $timeString",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onStartCall(partnerUid, partnerName, isVideo) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalCardElevated)
                                    .border(1.dp, CharcoalBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                                    contentDescription = "Call Back",
                                    tint = if (isVideo) BondlyPink else OnlineGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
