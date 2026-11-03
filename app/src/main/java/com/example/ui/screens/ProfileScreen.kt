package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.UserLevelManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val profile = userProfile ?: UserProfile()
    val progression = remember(profile) { UserLevelManager.calculateProgression(profile) }
    val photos = profile.profilePhotoUrls.ifEmpty {
        listOf("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80")
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalDark)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite
                )

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CharcoalCardElevated)
                        .border(1.dp, CharcoalBorder, CircleShape)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = TextLightGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photos Carousel
            item {
                Column {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(photos) { idx, photoUrl ->
                            Box(
                                modifier = Modifier
                                    .width(260.dp)
                                    .height(340.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
                            ) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (idx == 0) {
                                    Box(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .align(Alignment.TopStart)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BondlyPink)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "PRIMARY",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextWhite
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CharcoalDark.copy(alpha = 0.75f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                    Text(
                                        text = "${idx + 1}/${photos.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // User Info Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${profile.displayName}, ${profile.age}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                VerifiedBadge()
                            }
                            OnlineIndicator(isOnline = profile.isOnline)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            UserIdChip(publicUserId = profile.publicUserId.ifEmpty { "BONDLY-NEW" })

                            IconButton(
                                onClick = {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Connect with me on Bondly! My Public ID is ${profile.publicUserId}.")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Public ID"))
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share ID",
                                    tint = BondlyPink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // User Level Progression Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trust & User Level",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            LevelBadge(level = profile.userLevel, isAdmin = profile.isAdmin)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = progression.currentLevelName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BondlyPink
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { progression.overallProgressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = BondlyPink,
                            trackColor = CharcoalCardElevated
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Calling unlock status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (progression.isVoiceCallAllowed) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (progression.isVoiceCallAllowed) OnlineGreen else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Voice Calling",
                                    fontSize = 12.sp,
                                    color = if (progression.isVoiceCallAllowed) TextLightGrey else TextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (progression.isVideoCallAllowed) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (progression.isVideoCallAllowed) OnlineGreen else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Video Calling",
                                    fontSize = 12.sp,
                                    color = if (progression.isVideoCallAllowed) TextLightGrey else TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Personal Details Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Personal Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )

                        ProfileDetailItem(
                            icon = Icons.Filled.Person,
                            label = "Gender",
                            value = profile.gender.ifEmpty { "Not set" }
                        )
                        ProfileDetailItem(
                            icon = Icons.Filled.Favorite,
                            label = "Looking For",
                            value = profile.datingPreference.ifEmpty { "Everyone" }
                        )
                        ProfileDetailItem(
                            icon = Icons.Filled.LocationOn,
                            label = "Location",
                            value = if (profile.city.isNotEmpty()) "${profile.city}, ${profile.state}" else "India"
                        )
                    }
                }
            }

            // About Me / Bio Card
            if (profile.bio.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "About Me",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = profile.bio,
                                fontSize = 14.sp,
                                color = TextLightGrey,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Hobbies & Interests Card
            if (profile.interests.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Hobbies & Interests",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                profile.interests.forEach { hobby ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CharcoalCardElevated)
                                            .border(1.dp, BondlyPink.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = hobby,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BondlyPink,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = TextMuted
            )
        }
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextWhite
        )
    }
}
