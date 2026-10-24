package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.OnlineIndicator
import com.example.ui.components.PrimaryButton
import com.example.ui.components.ReportAndBlockSheet
import com.example.ui.components.SecondaryButton
import com.example.ui.components.UserIdChip
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicProfileScreen(
    targetUserId: String,
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showReportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(targetUserId) {
        isLoading = true
        userProfile = repository.getUserProfile(targetUserId)
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(CharcoalDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BondlyPink)
        }
        return
    }

    val user = userProfile
    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(CharcoalDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Profile not available", color = TextMuted)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .testTag("public_profile_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CharcoalCard)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Share Profile Action (Native Share Sheet)
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Connect with ${user.displayName} on Bondly! Search Public ID: ${user.publicUserId}"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Profile"))
                        },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(CharcoalCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Profile",
                            tint = TextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Report/Block Sheet Action
                    IconButton(
                        onClick = { showReportSheet = true },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(CharcoalCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Photo Gallery
            if (user.profilePhotoUrls.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(user.profilePhotoUrls) { photoUrl ->
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = user.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(280.dp)
                                .height(340.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(BondlyGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName.take(1).uppercase(),
                        color = TextWhite,
                        fontSize = 84.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Profile Info Content
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.displayName}, ${user.age}",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    )
                    if (user.verificationStatus == "verified") {
                        Spacer(modifier = Modifier.width(8.dp))
                        VerifiedBadge()
                    }
                    if (user.showOnlineStatus) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OnlineIndicator()
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserIdChip(publicUserId = user.publicUserId, showCopy = true)
                    if (user.city.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "📍 ${user.city}", color = TextLightGrey, fontSize = 13.sp)
                    }
                }

                if (user.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "About",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = user.bio,
                        color = TextLightGrey,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }

                if (user.interests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Interests",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        user.interests.forEach { interest ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CharcoalCard)
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = interest,
                                    color = TextLightGrey,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Actions: Chat, Audio Call, Video Call
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = "Chat",
                        onClick = { onNavigateToChat(user.userId) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = TextLightGrey)
                        }
                    )
                    PrimaryButton(
                        text = "Call",
                        onClick = { onStartCall(user.userId, user.displayName, true) },
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = TextWhite)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SecondaryButton(
                    text = "Audio Call Only",
                    onClick = { onStartCall(user.userId, user.displayName, false) },
                    leadingIcon = {
                        Icon(Icons.Default.Call, contentDescription = null, tint = TextLightGrey)
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Report & Block Bottom Sheet
        if (showReportSheet) {
            ReportAndBlockSheet(
                targetUserName = user.displayName,
                targetUserId = user.userId,
                onDismiss = { showReportSheet = false },
                onReport = { reason, details ->
                    scope.launch {
                        repository.reportUser(user.userId, reason, details)
                    }
                },
                onBlock = {
                    scope.launch {
                        repository.blockUser(user.userId)
                        onBack()
                    }
                }
            )
        }
    }
}
