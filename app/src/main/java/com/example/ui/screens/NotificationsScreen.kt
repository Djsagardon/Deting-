package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.NotificationItem
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onNavigateToMatches: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notifications by repository.observeNotifications().collectAsState(initial = emptyList())
    var selectedAnnouncement by remember { mutableStateOf<NotificationItem?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalDark)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Alerts & Activity",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite
                )
            }
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "All Caught Up!",
                    description = "You'll be notified here when you get new likes, match requests, call alerts, or community updates.",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CharcoalCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsNone,
                                contentDescription = null,
                                tint = BondlyPink,
                                modifier = Modifier.size(36.dp)
                            )
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
                itemsIndexed(
                    items = notifications,
                    key = { index, item ->
                        if (item.id.isNotEmpty()) item.id
                        else "notif_${index}_${item.createdAt?.seconds ?: System.currentTimeMillis()}"
                    }
                ) { _, item ->
                    NotificationCard(
                        item = item,
                        onClick = {
                            coroutineScope.launch {
                                repository.markNotificationAsRead(item.id)
                            }
                            when (item.type) {
                                "match_request" -> {
                                    if (item.targetId.isNotEmpty()) {
                                        onNavigateToProfile(item.targetId)
                                    }
                                }
                                "match", "match_accepted", "chat", "message" -> {
                                    onNavigateToMatches()
                                }
                                "admin_announcement" -> {
                                    selectedAnnouncement = item
                                }
                                "call", "voice_call", "video_call" -> {
                                    onNavigateToMatches()
                                }
                                else -> {
                                    // standard acknowledge
                                }
                            }
                        },
                        onAcceptMatch = {
                            coroutineScope.launch {
                                repository.markNotificationAsRead(item.id)
                                val result = repository.acceptMatchRequest(item.targetId)
                                result.onSuccess {
                                    Toast.makeText(context, "Matched with ${item.senderName}! 🎉", Toast.LENGTH_SHORT).show()
                                    onNavigateToMatches()
                                }.onFailure {
                                    Toast.makeText(context, "Could not accept match: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRejectMatch = {
                            coroutineScope.launch {
                                repository.markNotificationAsRead(item.id)
                                repository.rejectUser(item.targetId)
                                Toast.makeText(context, "Request passed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Announcement Dialog
        val announcement = selectedAnnouncement
        if (announcement != null) {
            Dialog(onDismissRequest = { selectedAnnouncement = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Campaign,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = announcement.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = announcement.body,
                            fontSize = 14.sp,
                            color = TextLightGrey,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        PrimaryButton(
                            text = "Acknowledge",
                            onClick = { selectedAnnouncement = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    onAcceptMatch: () -> Unit,
    onRejectMatch: () -> Unit
) {
    val (icon, iconColor) = when (item.type) {
        "call", "voice_call", "video_call" -> Icons.Filled.Call to Color(0xFF00B0FF)
        "admin_announcement" -> Icons.Filled.Campaign to Color(0xFFFFD700)
        "match_accepted", "match" -> Icons.Filled.Celebration to BondlyPink
        "match_request" -> Icons.Filled.Favorite to BondlyPink
        "chat", "message" -> Icons.Filled.Chat to OnlineGreen
        "safety" -> Icons.Filled.Security to Color(0xFFFF9100)
        else -> Icons.Filled.Notifications to BondlyPink
    }

    val timeString = remember(item.timestamp) {
        try {
            val date = item.timestamp?.toDate() ?: Date()
            val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            formatter.format(date)
        } catch (_: Exception) {
            "Recent"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (!item.isRead) {
                if (item.type == "admin_announcement") Color(0xFFFFD700) else BondlyPink
            } else CharcoalBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo or Icon
                if (item.senderPhotoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.senderPhotoUrl,
                        contentDescription = item.senderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, BondlyPink, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (item.type == "admin_announcement") Color(0xFFFFD700) else TextWhite
                        )

                        if (!item.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (item.type == "admin_announcement") Color(0xFFFFD700) else BondlyPink)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.body,
                        fontSize = 13.sp,
                        color = TextLightGrey,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = timeString,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // Quick Actions for Match Request
            if (item.type == "match_request") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onRejectMatch,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLightGrey),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text("Pass", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onAcceptMatch,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = TextWhite, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Match", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
