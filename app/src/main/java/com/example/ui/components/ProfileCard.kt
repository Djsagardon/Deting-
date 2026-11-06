package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCard(
    profile: UserProfile,
    onLike: () -> Unit,
    onPass: () -> Unit,
    onSuperLike: () -> Unit,
    onViewDetails: () -> Unit,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    var photoIndex by remember(profile.userId) { mutableIntStateOf(0) }
    val photos = profile.profilePhotoUrls.ifEmpty {
        listOf("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(24.dp))
            .testTag("profile_card_${profile.userId}"),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            // PHOTO CAROUSEL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clickable {
                        // Tap left/right to change photo
                        if (photos.size > 1) {
                            photoIndex = (photoIndex + 1) % photos.size
                        } else {
                            onViewDetails()
                        }
                    }
            ) {
                AsyncImage(
                    model = photos.getOrElse(photoIndex) { photos[0] },
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Bottom Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CardOverlayGradient)
                )

                // Top photo indicators
                if (photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in photos.indices) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (i == photoIndex) BondlyPink else Color.White.copy(alpha = 0.4f))
                            )
                        }
                    }
                }

                // Level Badge & Bondly ID top row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (photos.size > 1) 24.dp else 12.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BondlyPink.copy(alpha = 0.9f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = TextWhite, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lvl ${profile.userLevel}", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (profile.publicUserId.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                        ) {
                            Text(
                                text = profile.publicUserId,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Overlay Info at Bottom of Image
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${profile.displayName}, ${if (profile.age > 0) profile.age else 24}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        if (profile.verificationStatus == "verified") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Verified", tint = VerifiedBlue, modifier = Modifier.size(20.dp))
                        }
                    }

                    if (profile.city.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${profile.city}${if (profile.state.isNotEmpty()) ", ${profile.state}" else ""}",
                                color = TextLightGrey,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // DETAILS & BIO
            Column(modifier = Modifier.padding(16.dp)) {
                if (profile.bio.isNotEmpty()) {
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLightGrey,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Hobbies & Interests FlowRow
                if (profile.interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profile.interests.take(5).forEach { interest ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CharcoalCardElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                            ) {
                                Text(
                                    text = interest,
                                    color = TextLightGrey,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ACTION BUTTONS (Pass, SuperLike, Like, Details)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pass Button
                    IconButton(
                        onClick = onPass,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CharcoalCardElevated)
                            .border(1.dp, CharcoalBorder, CircleShape)
                            .testTag("pass_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Pass", tint = ErrorRed, modifier = Modifier.size(26.dp))
                    }

                    // SuperLike Button
                    IconButton(
                        onClick = onSuperLike,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CharcoalCardElevated)
                            .border(1.dp, CharcoalBorder, CircleShape)
                            .testTag("superlike_button")
                    ) {
                        Icon(Icons.Default.Star, contentDescription = "Super Like", tint = SuperLikeBlue, modifier = Modifier.size(22.dp))
                    }

                    // View Details Button
                    IconButton(
                        onClick = onViewDetails,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CharcoalCardElevated)
                            .border(1.dp, CharcoalBorder, CircleShape)
                            .testTag("details_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Profile Details", tint = BondlyGold, modifier = Modifier.size(22.dp))
                    }

                    // Like / Match Button (Gradient)
                    IconButton(
                        onClick = { if (!isProcessing) onLike() },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(BondlyGradient)
                            .testTag("like_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TextWhite,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.Default.Favorite, contentDescription = "Match", tint = TextWhite, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
