package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.theme.*
import com.example.util.ContactSharingFilter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesAndChatScreen(
    currentUserId: String,
    currentUserProfile: UserProfile,
    repository: BondlyRepository,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit,
    onNavigateToPublicProfile: (String) -> Unit
) {
    val matches by repository.observeMatches().collectAsState(initial = emptyList())
    var selectedMatch by remember { mutableStateOf<Match?>(null) }

    // If a chat is open, render ActiveChatView
    if (selectedMatch != null) {
        val match = selectedMatch!!
        val partnerUid = if (match.user1Id == currentUserId) match.user2Id else match.user1Id
        val partnerName = if (match.user1Id == currentUserId) match.user2Name else match.user1Name
        val partnerPhoto = if (match.user1Id == currentUserId) match.user2Photo else match.user1Photo

        ActiveChatView(
            match = match,
            partnerUid = partnerUid,
            partnerName = partnerName,
            partnerPhoto = partnerPhoto,
            currentUserId = currentUserId,
            userLevel = currentUserProfile.userLevel,
            isAdmin = currentUserProfile.isAdmin,
            repository = repository,
            onBack = { selectedMatch = null },
            onStartCall = { isVideo -> onStartCall(partnerUid, partnerName, isVideo) },
            onViewProfile = { onNavigateToPublicProfile(partnerUid) }
        )
        return
    }

    // MAIN CONVERSATIONS LIST
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
    ) {
        // Top Header
        Surface(
            color = CharcoalSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Matches & Chats",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // CONFIRMED MATCHES CAROUSEL
        if (matches.isNotEmpty()) {
            Text(
                text = "New Connections",
                color = BondlyPink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(matches, key = { "avatar_${it.id}" }) { match ->
                    val partnerName = if (match.user1Id == currentUserId) match.user2Name else match.user1Name
                    val partnerPhoto = if (match.user1Id == currentUserId) match.user2Photo else match.user1Photo

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedMatch = match }
                            .testTag("match_avatar_${match.id}")
                    ) {
                        Box(modifier = Modifier.size(62.dp)) {
                            AsyncImage(
                                model = partnerPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" },
                                contentDescription = partnerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(2.dp, BondlyGradient, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = partnerName.split(" ").firstOrNull() ?: partnerName,
                            color = TextLightGrey,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = CharcoalBorder, thickness = 0.5.dp)
        }

        // ALL CONFIRMED MATCHES LIST
        if (matches.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No connections yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Explore profiles in Discover and show interest to start conversations!",
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Text(
                text = "All Confirmed Matches",
                color = TextLightGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(matches, key = { it.id }) { match ->
                    val partnerName = if (match.user1Id == currentUserId) match.user2Name else match.user1Name
                    val partnerPhoto = if (match.user1Id == currentUserId) match.user2Photo else match.user1Photo

                    Surface(
                        color = CharcoalSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMatch = match }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, CharcoalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(52.dp)) {
                                AsyncImage(
                                    model = partnerPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" },
                                    contentDescription = partnerName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = partnerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = match.lastMessage.ifEmpty { "Connected! Say hello 🎉" },
                                    color = if (match.lastMessage.isNotEmpty()) TextLightGrey else BondlyPink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveChatView(
    match: Match,
    partnerUid: String,
    partnerName: String,
    partnerPhoto: String,
    currentUserId: String,
    userLevel: Int,
    isAdmin: Boolean = false,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (Boolean) -> Unit,
    onViewProfile: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by repository.observeMessages(match.id).collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var showFullPhoto by remember { mutableStateOf(false) }

    // Auto mark all incoming messages as seen when reading
    LaunchedEffect(match.id, messages.size) {
        repository.markAllMessagesAsSeen(match.id)
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showFullPhoto && partnerPhoto.isNotBlank()) {
        com.example.ui.components.FullScreenImageViewer(
            photoUrls = listOf(partnerPhoto),
            onDismiss = { showFullPhoto = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = partnerPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" },
                            contentDescription = partnerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (partnerPhoto.isNotBlank()) {
                                        showFullPhoto = true
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.clickable { onViewProfile() }) {
                            Text(partnerName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Online", color = OnlineGreen, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                actions = {
                    // Video Call Button
                    IconButton(onClick = { onStartCall(true) }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = BondlyPink)
                    }
                    // Voice Call Button
                    IconButton(onClick = { onStartCall(false) }) {
                        Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = BondlyPink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalDark
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // Safety & Contact sharing alert
            if (warningMessage != null) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(warningMessage!!, color = TextWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { warningMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextWhite, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Message List - Chronological, newest at the bottom
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(
                    items = messages,
                    key = { index, msg -> if (msg.id.isNotEmpty()) msg.id else "msg_${index}_${msg.localTimestamp}" }
                ) { _, msg ->
                    val isMine = msg.senderId == currentUserId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            ),
                            color = if (isMine) BondlyPink else CharcoalCard,
                            border = if (isMine) null else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(
                                    text = msg.text,
                                    color = TextWhite,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.localTimestamp))
                                    Text(
                                        text = time,
                                        color = if (isMine) TextWhite.copy(alpha = 0.7f) else TextMuted,
                                        fontSize = 10.sp
                                    )
                                    if (isMine) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = when (msg.status) {
                                                "seen" -> Icons.Default.DoneAll
                                                "delivered" -> Icons.Default.DoneAll
                                                "sent" -> Icons.Default.Done
                                                else -> Icons.Default.Schedule
                                            },
                                            contentDescription = msg.status,
                                            tint = if (msg.status == "seen") SuperLikeBlue
                                                   else if (msg.status == "delivered") TextWhite.copy(alpha = 0.9f)
                                                   else TextWhite.copy(alpha = 0.6f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = CharcoalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Write a message...", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = BondlyPink
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                if (!com.example.util.NetworkConnectivityManager.isConnected()) {
                                    Toast.makeText(context, "No internet connection. Please connect to the internet and try again.", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                val check = ContactSharingFilter.checkMessage(inputText, userLevel, isAdmin)
                                if (check.isRestricted) {
                                    warningMessage = check.reason
                                    return@IconButton
                                }
                                val toSend = inputText
                                inputText = ""
                                warningMessage = null
                                coroutineScope.launch {
                                    val sendResult = repository.sendMessage(match.id, toSend)
                                    sendResult.onFailure { err ->
                                        inputText = toSend
                                        val msg = com.example.util.BondlyErrorClassifier.getUserFriendlyMessage(err)
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(BondlyGradient)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
