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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.components.PrimaryButton
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

@Composable
fun DiscoverScreen(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    repository: BondlyRepository,
    onNavigateToSearchById: () -> Unit,
    onNavigateToProfileDetails: (String) -> Unit,
    onNavigateToChat: (String) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val userGender = currentUserProfile?.gender ?: "Male"

    // Strictly enforce opposite gender discovery at query & repository level
    val profilesFlow = remember(currentUserId, userGender) {
        repository.observeDiscoveryProfiles(currentUserId, userGender)
    }
    val rawProfiles by profilesFlow.collectAsState(initial = null)

    var excludedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentPhotoIndex by remember { mutableIntStateOf(0) }
    var matchedUser by remember { mutableStateOf<UserProfile?>(null) }
    var isSwiping by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        excludedIds = repository.getExcludedUserIds(currentUserId)
    }

    // Filter out already liked, passed, or blocked user IDs
    val eligibleProfiles = remember(rawProfiles, excludedIds) {
        rawProfiles?.filter { it.userId !in excludedIds } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .testTag("discover_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discover",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    )
                    Text(
                        text = if (userGender.equals("Male", ignoreCase = true)) "Showing eligible women in India" else "Showing eligible men in India",
                        color = BondlyPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Search by Public User ID button
                IconButton(
                    onClick = onNavigateToSearchById,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, CircleShape)
                        .testTag("search_by_id_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search by ID",
                        tint = TextLightGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (rawProfiles == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BondlyPink)
                }
            } else if (eligibleProfiles.isEmpty() || currentIndex >= eligibleProfiles.size) {
                EmptyState(
                    title = "No More Profiles Right Now",
                    description = "You've viewed all eligible profiles matching your discovery preferences. Check back soon for new singles joining Bondly in your region!",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(44.dp)
                        )
                    },
                    actionButton = {
                        SecondaryButton(
                            text = "Find by Public User ID",
                            onClick = onNavigateToSearchById
                        )
                    }
                )
            } else {
                val currentCandidate = eligibleProfiles[currentIndex]
                val photoUrls = currentCandidate.profilePhotoUrls

                // Candidate Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { onNavigateToProfileDetails(currentCandidate.userId) },
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Display Current Photo
                        val photoToShow = photoUrls.getOrNull(currentPhotoIndex) ?: photoUrls.firstOrNull() ?: ""
                        if (photoToShow.isNotBlank()) {
                            AsyncImage(
                                model = photoToShow,
                                contentDescription = currentCandidate.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BondlyGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentCandidate.displayName.take(1).uppercase(),
                                    color = TextWhite,
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Gradient Shade
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x66000000),
                                            Color.Transparent,
                                            Color(0xE60F0E17)
                                        )
                                    )
                                )
                        )

                        // Top Photo Segment Indicators
                        if (photoUrls.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, start = 14.dp, end = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                photoUrls.indices.forEach { idx ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(if (idx == currentPhotoIndex) TextWhite else Color(0x66FFFFFF))
                                            .clickable { currentPhotoIndex = idx }
                                    )
                                }
                            }
                        }

                        // Left/Right Tap Area to Cycle Photos
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        if (currentPhotoIndex > 0) currentPhotoIndex--
                                    }
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable {
                                        if (currentPhotoIndex < photoUrls.size - 1) currentPhotoIndex++
                                    }
                            )
                        }

                        // Candidate Information Overlay (Bottom)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${currentCandidate.displayName}, ${currentCandidate.age}",
                                    color = TextWhite,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedBadge()
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // City & State
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = BondlyPink,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${currentCandidate.city.ifBlank { "India" }}, ${currentCandidate.state.ifBlank { "Verified" }}",
                                    color = TextLightGrey,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (currentCandidate.publicUserId.isNotBlank()) {
                                    UserIdChip(publicUserId = currentCandidate.publicUserId)
                                }
                            }

                            if (currentCandidate.bio.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentCandidate.bio,
                                    color = TextLightGrey,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    lineHeight = 18.sp
                                )
                            }

                            if (currentCandidate.interests.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    currentCandidate.interests.take(3).forEach { hobby ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x55000000))
                                                .border(0.5.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = hobby, color = TextWhite, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Pass, Super Like, Like
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pass (Dislike)
                    IconButton(
                        onClick = {
                            if (!isSwiping) {
                                isSwiping = true
                                val candidate = currentCandidate
                                scope.launch {
                                    repository.passUser(candidate.userId)
                                    excludedIds = excludedIds + candidate.userId
                                    currentIndex++
                                    currentPhotoIndex = 0
                                    isSwiping = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CharcoalCard)
                            .border(1.5.dp, Color(0xFFFF4455), CircleShape)
                            .testTag("discover_pass_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Pass",
                            tint = Color(0xFFFF4455),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Super Like
                    IconButton(
                        onClick = {
                            if (!isSwiping) {
                                isSwiping = true
                                val candidate = currentCandidate
                                scope.launch {
                                    val matchResult = repository.likeUser(candidate.userId, isSuperLike = true)
                                    excludedIds = excludedIds + candidate.userId
                                    if (matchResult.getOrDefault(false)) {
                                        matchedUser = candidate
                                    }
                                    currentIndex++
                                    currentPhotoIndex = 0
                                    isSwiping = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(CharcoalCard)
                            .border(1.5.dp, Color(0xFF00B0FF), CircleShape)
                            .testTag("discover_super_like_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Super Like",
                            tint = Color(0xFF00B0FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Like
                    IconButton(
                        onClick = {
                            if (!isSwiping) {
                                isSwiping = true
                                val candidate = currentCandidate
                                scope.launch {
                                    val matchResult = repository.likeUser(candidate.userId, isSuperLike = false)
                                    excludedIds = excludedIds + candidate.userId
                                    if (matchResult.getOrDefault(false)) {
                                        matchedUser = candidate
                                    }
                                    currentIndex++
                                    currentPhotoIndex = 0
                                    isSwiping = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(BondlyGradient)
                            .testTag("discover_like_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Like",
                            tint = TextWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Mutual Match Pop-Up Dialog
        if (matchedUser != null) {
            Dialog(onDismissRequest = { matchedUser = null }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(BondlyGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "It's a Match! 🎉",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "You and ${matchedUser?.displayName} liked each other! Start talking or initiate a live HD call.",
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Actions: Start Call or Chat
                        PrimaryButton(
                            text = "Start Video Call",
                            onClick = {
                                val match = matchedUser
                                matchedUser = null
                                if (match != null) {
                                    onStartCall(match.userId, match.displayName, true)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SecondaryButton(
                            text = "Send Message",
                            onClick = {
                                val match = matchedUser
                                matchedUser = null
                                if (match != null) {
                                    onNavigateToChat(match.userId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
