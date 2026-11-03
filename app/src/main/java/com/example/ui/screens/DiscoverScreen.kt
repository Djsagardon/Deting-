package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.ui.components.*
import com.example.ui.theme.*
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
    val coroutineScope = rememberCoroutineScope()
    val rawProfiles by repository.observeDiscoveryUsers(
        myGender = currentUserProfile?.gender ?: "",
        myPreference = currentUserProfile?.datingPreference ?: "Everyone"
    ).collectAsState(initial = emptyList())

    var excludedIds by remember { mutableStateOf(setOf<String>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var matchedUser by remember { mutableStateOf<UserProfile?>(null) }
    var isSwiping by remember { mutableStateOf(false) }

    val candidates = remember(rawProfiles, excludedIds, currentUserId) {
        rawProfiles.filter { it.userId != currentUserId && !excludedIds.contains(it.userId) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextWhite
                    )
                    Text(
                        text = "Find meaningful Indian connections",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = onNavigateToSearchById,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CharcoalCardElevated)
                        .border(1.dp, CharcoalBorder, CircleShape)
                        .testTag("search_by_id_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search by Bondly ID",
                        tint = BondlyPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Body
            if (candidates.isEmpty() || currentIndex >= candidates.size) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No More Profiles Right Now",
                        description = "You've viewed all nearby compatible profiles. Check back later or search directly by Bondly ID.",
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalCardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonSearch,
                                    contentDescription = null,
                                    tint = BondlyPink,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        },
                        actionButton = {
                            SecondaryButton(
                                text = "Find by Public User ID",
                                onClick = onNavigateToSearchById
                            )
                        }
                    )
                }
            } else {
                val currentCandidate = candidates[currentIndex]

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ProfileCard(
                        profile = currentCandidate,
                        onLike = {
                            if (!isSwiping) {
                                isSwiping = true
                                coroutineScope.launch {
                                    val matchResult = repository.sendLike(currentCandidate.userId).getOrDefault(false)
                                    excludedIds = excludedIds + currentCandidate.userId
                                    if (matchResult) {
                                        matchedUser = currentCandidate
                                    }
                                    if (currentIndex < candidates.size - 1) {
                                        currentIndex++
                                    } else {
                                        currentIndex = 0
                                    }
                                    isSwiping = false
                                }
                            }
                        },
                        onPass = {
                            if (!isSwiping) {
                                isSwiping = true
                                excludedIds = excludedIds + currentCandidate.userId
                                if (currentIndex < candidates.size - 1) {
                                    currentIndex++
                                } else {
                                    currentIndex = 0
                                }
                                isSwiping = false
                            }
                        },
                        onSuperLike = {
                            if (!isSwiping) {
                                isSwiping = true
                                coroutineScope.launch {
                                    val matchResult = repository.sendLike(currentCandidate.userId).getOrDefault(false)
                                    excludedIds = excludedIds + currentCandidate.userId
                                    if (matchResult) {
                                        matchedUser = currentCandidate
                                    }
                                    if (currentIndex < candidates.size - 1) {
                                        currentIndex++
                                    } else {
                                        currentIndex = 0
                                    }
                                    isSwiping = false
                                }
                            }
                        },
                        onViewDetails = {
                            onNavigateToProfileDetails(currentCandidate.userId)
                        }
                    )
                }
            }
        }

        // MATCH CELEBRATION DIALOG
        val match = matchedUser
        if (match != null) {
            Dialog(onDismissRequest = { matchedUser = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "It's a Match! 🎉",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BondlyPink,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You and ${match.displayName} liked each other!",
                            fontSize = 14.sp,
                            color = TextLightGrey,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Avatars side by side
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val myPhoto = currentUserProfile?.profilePhotoUrls?.firstOrNull() ?: ""
                            val matchPhoto = match.profilePhotoUrls.firstOrNull() ?: ""

                            AsyncImage(
                                model = myPhoto,
                                contentDescription = "You",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BondlyPink, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = BondlyPink,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            AsyncImage(
                                model = matchPhoto,
                                contentDescription = match.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BondlyPurple, CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        PrimaryButton(
                            text = "Send Message",
                            onClick = {
                                val targetId = match.userId
                                matchedUser = null
                                onNavigateToChat(targetId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        SecondaryButton(
                            text = "Keep Browsing",
                            onClick = { matchedUser = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
