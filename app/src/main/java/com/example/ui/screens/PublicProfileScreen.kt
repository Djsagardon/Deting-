package com.example.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublicProfileScreen(
    targetUserId: String,
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit,
    onReportUser: (targetUid: String, targetName: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val targetProfile by repository.observeProfile(targetUserId).collectAsState(initial = null)
    val incomingRequest by repository.observeMatchRequest(senderUid = targetUserId, receiverUid = currentUserId).collectAsState(initial = null)
    val existingMatch by repository.observeMatchBetween(currentUserId, targetUserId).collectAsState(initial = null)
    var likeSent by remember { mutableStateOf(false) }
    var isProcessingAction by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = targetProfile?.displayName ?: "Profile",
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    val profile = targetProfile
                    if (profile != null) {
                        IconButton(
                            onClick = { onReportUser(profile.userId, profile.displayName) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Report or Block",
                                tint = TextMuted
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalDark)
            )
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        val profile = targetProfile
        if (profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                LoadingState(message = "Loading profile...")
            }
        } else {
            val photos = profile.profilePhotoUrls.ifEmpty {
                listOf("https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80")
            }
            var selectedPhotoIndex by remember { mutableIntStateOf(0) }
            var showFullScreenViewer by remember { mutableStateOf(false) }

            if (showFullScreenViewer) {
                com.example.ui.components.FullScreenImageViewer(
                    photoUrls = photos,
                    initialIndex = selectedPhotoIndex,
                    onDismiss = { showFullScreenViewer = false }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Photos Carousel
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(photos) { idx, photoUrl ->
                            Box(
                                modifier = Modifier
                                    .width(280.dp)
                                    .height(360.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
                                    .clickable {
                                        selectedPhotoIndex = idx
                                        showFullScreenViewer = true
                                    }
                            ) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

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

                // Header Card
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
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserIdChip(publicUserId = profile.publicUserId)
                                LevelBadge(level = profile.userLevel, isAdmin = profile.isAdmin)
                            }
                        }
                    }
                }

                // Details Card
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
                                text = "About",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )

                            if (profile.bio.isNotEmpty()) {
                                Text(
                                    text = profile.bio,
                                    fontSize = 14.sp,
                                    color = TextLightGrey,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            DetailRow(Icons.Filled.Person, "Gender", profile.gender.ifEmpty { "Not specified" })
                            DetailRow(Icons.Filled.Favorite, "Looking For", profile.datingPreference.ifEmpty { "Everyone" })
                            DetailRow(
                                Icons.Filled.LocationOn,
                                "Location",
                                if (profile.city.isNotEmpty()) "${profile.city}, ${profile.state}" else "India"
                            )
                        }
                    }
                }

                // Hobbies
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
                                    text = "Interests",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    profile.interests.forEach { interest ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(CharcoalCardElevated)
                                                .border(1.dp, BondlyPink.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = interest,
                                                fontSize = 12.sp,
                                                color = TextWhite
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Actions Card
                item {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var actionFeedback by remember { mutableStateOf<String?>(null) }
                    val isPendingRequest = incomingRequest?.status == "pending"
                    val isMatched = existingMatch != null

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPendingRequest) BondlyPink else if (isMatched) OnlineGreen else CharcoalBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (actionFeedback != null) {
                                Text(actionFeedback!!, color = BondlyPink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            if (isPendingRequest) {
                                // CASE 1: INCOMING MATCH REQUEST PENDING
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = BondlyPink.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${profile.displayName} wants to match with you!",
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!isProcessingAction) {
                                                isProcessingAction = true
                                                coroutineScope.launch {
                                                    repository.rejectUser(profile.userId)
                                                    isProcessingAction = false
                                                    android.widget.Toast.makeText(context, "Match request passed", android.widget.Toast.LENGTH_SHORT).show()
                                                    onBack()
                                                }
                                            }
                                        },
                                        enabled = !isProcessingAction,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLightGrey)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp), tint = ErrorRed)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reject Match", fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            if (!isProcessingAction) {
                                                isProcessingAction = true
                                                coroutineScope.launch {
                                                    val res = repository.acceptMatchRequest(profile.userId)
                                                    isProcessingAction = false
                                                    res.onSuccess {
                                                        actionFeedback = "It's a Match! 🎉 Chat and Calls are now unlocked!"
                                                        android.widget.Toast.makeText(context, "Matched with ${profile.displayName}! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                                    }.onFailure {
                                                        actionFeedback = "Error: ${it.localizedMessage}"
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isProcessingAction,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink)
                                    ) {
                                        if (isProcessingAction) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextWhite, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextWhite)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Accept Match", fontWeight = FontWeight.Bold, color = TextWhite)
                                        }
                                    }
                                }
                            } else if (isMatched) {
                                // CASE 2: CONFIRMED MUTUAL MATCH
                                Row(
                                    modifier = Modifier.padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Celebration, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("You and ${profile.displayName} are matched 🎉", color = OnlineGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Voice Call
                                    IconButton(
                                        onClick = { onStartCall(profile.userId, profile.displayName, false) },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalCardElevated)
                                            .border(1.5.dp, OnlineGreen, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Voice Call",
                                            tint = OnlineGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Video Call
                                    IconButton(
                                        onClick = { onStartCall(profile.userId, profile.displayName, true) },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalCardElevated)
                                            .border(1.5.dp, SuperLikeBlue, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Videocam,
                                            contentDescription = "Video Call",
                                            tint = SuperLikeBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            } else {
                                // CASE 3: STANDARD PROFILE BROWSING
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Reject / Pass Button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.rejectUser(profile.userId)
                                                actionFeedback = "Passed profile"
                                                likeSent = false
                                            }
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalCardElevated)
                                            .border(1.5.dp, CharcoalBorder, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Pass",
                                            tint = TextMuted,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    // Match / Like Button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val matchRes = repository.sendLike(profile.userId)
                                                matchRes.onSuccess { mutual ->
                                                    likeSent = true
                                                    actionFeedback = if (mutual) "It's a Mutual Match! 🎉" else "Match request sent! 💖"
                                                }.onFailure { err ->
                                                    actionFeedback = err.localizedMessage ?: "Failed to send request"
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(if (likeSent) BondlyPink else CharcoalCardElevated)
                                            .border(1.5.dp, BondlyPink, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = "Match Profile",
                                            tint = if (likeSent) TextWhite else BondlyPink,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Voice Call
                                    IconButton(
                                        onClick = { onStartCall(profile.userId, profile.displayName, false) },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalCardElevated)
                                            .border(1.5.dp, OnlineGreen, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Voice Call",
                                            tint = OnlineGreen,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Video Call
                                    IconButton(
                                        onClick = { onStartCall(profile.userId, profile.displayName, true) },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalCardElevated)
                                            .border(1.5.dp, SuperLikeBlue, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Videocam,
                                            contentDescription = "Video Call",
                                            tint = SuperLikeBlue,
                                            modifier = Modifier.size(28.dp)
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
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 13.sp, color = TextMuted)
        }
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
    }
}
