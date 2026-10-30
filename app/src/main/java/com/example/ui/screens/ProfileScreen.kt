package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.components.LevelBadge
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
import com.example.util.UserLevelManager

/**
 * Pure Profile Overview screen displaying exclusively the user's personal profile information.
 * All editing, account management, and privacy controls are relocated to SettingsScreen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val progression = remember(userProfile) { UserLevelManager.calculateProgression(userProfile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
            .testTag("profile_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "My Profile",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text(
                    text = "Public profile preview as seen by matches",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CharcoalCard)
                    .testTag("profile_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextLightGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Profile Photos Gallery
        val photos = userProfile?.profilePhotoUrls ?: emptyList()
        if (photos.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Primary Large Featured Photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(24.dp))
                ) {
                    AsyncImage(
                        model = photos.first(),
                        contentDescription = "Primary Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Gradient overlay at bottom of photo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC121212))
                                )
                            )
                    )

                    // Verification Badge on Photo
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${userProfile?.displayName ?: "User"}, ${userProfile?.age ?: 18}",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        VerifiedBadge()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal Carousel of All Photos (strictly 5 required)
                Text(
                    text = "Verified Photos (${photos.size})",
                    color = TextLightGrey,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(photos) { index, url ->
                        Box(
                            modifier = Modifier
                                .size(76.dp, 100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (index == 0) BondlyPink else CharcoalBorder, RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Photo ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "${index + 1}", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Placeholder when no photos yet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CharcoalCard),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(BondlyGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userProfile?.displayName ?: "U").take(1).uppercase(),
                            color = TextWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${userProfile?.displayName ?: "User"}, ${userProfile?.age ?: 18}",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Identification & Badges Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // User Level Badge
                LevelBadge(level = progression.currentLevel.level, isAdmin = progression.isAdmin)

                // Public User ID & Share
                if (userProfile?.publicUserId?.isNotBlank() == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserIdChip(publicUserId = userProfile.publicUserId, showCopy = true)
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Connect with me on Bondly! My Public ID is ${userProfile.publicUserId}."
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Public ID"))
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CharcoalCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share ID",
                                tint = TextLightGrey,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Level & Reputation Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = progression.currentLevel.badgeName,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text(
                        text = "${(progression.progressPercentage * 100).toInt()}% Progress",
                        color = BondlyPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = progression.currentLevel.description,
                    color = TextLightGrey,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progression.progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = BondlyPink,
                    trackColor = CharcoalBorder
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Active Days: ${progression.activeDays}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Account Age: ${progression.accountAgeDays} days",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bio Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Me",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val bio = userProfile?.bio?.takeIf { it.isNotBlank() }
                    ?: "No bio provided yet. Add your personal bio in Settings."
                Text(
                    text = bio,
                    color = TextLightGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hobbies & Interests Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Interests & Hobbies",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val interests = userProfile?.interests ?: emptyList()
                if (interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        interests.forEach { interest ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF261D2B))
                                    .border(1.dp, BondlyPink.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = interest,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select your favorite hobbies and interests in Settings.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Location & Personal Details Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Profile Details",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Location
                val location = listOfNotNull(
                    userProfile?.city?.takeIf { it.isNotBlank() },
                    userProfile?.state?.takeIf { it.isNotBlank() }
                ).joinToString(", ").ifBlank { "Location not set" }

                DetailItem(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    value = location
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Gender
                DetailItem(
                    icon = Icons.Default.Person,
                    label = "Gender",
                    value = userProfile?.gender ?: "Not specified"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Age & DOB
                DetailItem(
                    icon = Icons.Default.Cake,
                    label = "Age",
                    value = "${userProfile?.age ?: 18} years old"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Dating Preference
                DetailItem(
                    icon = Icons.Default.Favorite,
                    label = "Looking For",
                    value = userProfile?.datingPreference ?: "Meaningful connection"
                )
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BondlyPink,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$label: ",
            color = TextMuted,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = TextWhite,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}
