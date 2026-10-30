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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.util.ContactSharingFilter
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.EmptyState
import com.example.ui.components.OnlineIndicator
import com.example.ui.components.ReportAndBlockSheet
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MatchesAndChatScreen(
    currentUserId: String,
    repository: BondlyRepository,
    selectedTargetUid: String? = null,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val matchesFlow = remember { repository.observeMatches() }
    val matches by matchesFlow.collectAsState(initial = null)

    var activeChatMatchId by remember { mutableStateOf<String?>(null) }
    var activeChatOtherUser by remember { mutableStateOf<UserProfile?>(null) }

    // Resolve active chat if targeted
    LaunchedEffect(selectedTargetUid, matches) {
        if (!selectedTargetUid.isNullOrBlank()) {
            val otherProfile = repository.getUserProfile(selectedTargetUid)
            activeChatOtherUser = otherProfile
            val mId = if (currentUserId < selectedTargetUid) "${currentUserId}_$selectedTargetUid" else "${selectedTargetUid}_$currentUserId"
            activeChatMatchId = mId
        }
    }

    if (activeChatMatchId != null && activeChatOtherUser != null) {
        ActiveChatView(
            matchId = activeChatMatchId!!,
            otherUser = activeChatOtherUser!!,
            currentUserId = currentUserId,
            repository = repository,
            onBack = {
                activeChatMatchId = null
                activeChatOtherUser = null
            },
            onStartCall = onStartCall
        )
    } else {
        ConversationListView(
            currentUserId = currentUserId,
            matches = matches,
            repository = repository,
            onSelectMatch = { match, otherUser ->
                activeChatMatchId = match.id
                activeChatOtherUser = otherUser
            }
        )
    }
}

@Composable
private fun ConversationListView(
    currentUserId: String,
    matches: List<Match>?,
    repository: BondlyRepository,
    onSelectMatch: (Match, UserProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var userProfilesMap by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }

    LaunchedEffect(matches) {
        matches?.forEach { match ->
            val otherUid = match.users.firstOrNull { it != currentUserId }
            if (otherUid != null && !userProfilesMap.containsKey(otherUid)) {
                scope.launch {
                    val p = repository.getUserProfile(otherUid)
                    if (p != null) {
                        userProfilesMap = userProfilesMap + (otherUid to p)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(top = 16.dp)
            .testTag("matches_and_chat_screen")
    ) {
        Text(
            text = "Matches & Chats",
            color = TextWhite,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (matches == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BondlyPink)
            }
        } else if (matches.isEmpty()) {
            EmptyState(
                title = "No Matches Yet",
                description = "Swipe and like people in Discover to get matched! Once you match, conversations will appear here.",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(40.dp)
                    )
                }
            )
        } else {
            // New Matches Horizontal Row
            Text(
                text = "New Matches (${matches.size})",
                color = TextLightGrey,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(modifier = Modifier.width(6.dp)) }
                items(matches) { match ->
                    val otherUid = match.users.firstOrNull { it != currentUserId } ?: ""
                    val otherProfile = userProfilesMap[otherUid]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (otherProfile != null) onSelectMatch(match, otherProfile)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .border(2.dp, BondlyPink, CircleShape)
                                .background(CharcoalCard),
                            contentAlignment = Alignment.Center
                        ) {
                            val photo = otherProfile?.profilePhotoUrls?.firstOrNull() ?: ""
                            if (photo.isNotBlank()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = otherProfile?.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = (otherProfile?.displayName ?: "U").take(1).uppercase(),
                                    color = TextWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = otherProfile?.displayName ?: "User",
                            color = TextLightGrey,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item { Spacer(modifier = Modifier.width(6.dp)) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Conversations",
                color = TextLightGrey,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(matches) { match ->
                    val otherUid = match.users.firstOrNull { it != currentUserId } ?: ""
                    val otherProfile = userProfilesMap[otherUid]

                    val timeStr = match.lastMessageTimestamp?.toDate()?.let {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (otherProfile != null) onSelectMatch(match, otherProfile)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(54.dp)) {
                            val photo = otherProfile?.profilePhotoUrls?.firstOrNull() ?: ""
                            if (photo.isNotBlank()) {
                                AsyncImage(
                                    model = photo,
                                    contentDescription = otherProfile?.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(BondlyGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (otherProfile?.displayName ?: "U").take(1).uppercase(),
                                        color = TextWhite,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (otherProfile?.showOnlineStatus == true) {
                                OnlineIndicator(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .border(2.dp, CharcoalDark, CircleShape)
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
                                    text = otherProfile?.displayName ?: "User",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = timeStr,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = match.lastMessage.ifBlank { "Start a conversation" },
                                color = TextLightGrey,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveChatView(
    matchId: String,
    otherUser: UserProfile,
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    val messagesFlow = remember(matchId) { repository.observeMessages(matchId) }
    val messages by messagesFlow.collectAsState(initial = emptyList())

    var messageInput by remember { mutableStateOf("") }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .testTag("active_chat_view")
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CharcoalCard)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }

            Box(modifier = Modifier.size(40.dp)) {
                val photo = otherUser.profilePhotoUrls.firstOrNull() ?: ""
                if (photo.isNotBlank()) {
                    AsyncImage(
                        model = photo,
                        contentDescription = otherUser.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(BondlyGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = otherUser.displayName.take(1).uppercase(),
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (otherUser.showOnlineStatus) {
                    OnlineIndicator(modifier = Modifier.align(Alignment.BottomEnd))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherUser.displayName,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (otherUser.showOnlineStatus) "Online now" else "ID: ${otherUser.publicUserId}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Audio Call Action
            IconButton(onClick = { onStartCall(otherUser.userId, otherUser.displayName, false) }) {
                Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = TextLightGrey)
            }

            // Video Call Action
            IconButton(onClick = { onStartCall(otherUser.userId, otherUser.displayName, true) }) {
                Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = BondlyPink)
            }

            // Menu
            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextLightGrey)
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                    modifier = Modifier.background(CharcoalCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Report User", color = Color(0xFFFF4455)) },
                        onClick = {
                            showOptionsMenu = false
                            showReportSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Block User", color = Color(0xFFFF4455)) },
                        onClick = {
                            showOptionsMenu = false
                            scope.launch {
                                repository.blockUser(otherUser.userId)
                                onBack()
                            }
                        }
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                ChatMessageBubble(
                    message = message,
                    isFromMe = message.senderId == currentUserId
                )
            }
        }

        // Contact Sharing Prevention Warning Banner
        val contactCheck = remember(messageInput) { ContactSharingFilter.checkText(messageInput) }
        if (contactCheck.isBlocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3B151E))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚠️ ${ContactSharingFilter.WARNING_MESSAGE}",
                    color = Color(0xFFFF6B7D),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Input Bottom Bar (Text Only, photo attachment removed for safety)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CharcoalCard)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageInput,
                onValueChange = { messageInput = it },
                placeholder = { Text("Type a message...", color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f).testTag("chat_message_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CharcoalDark,
                    unfocusedContainerColor = CharcoalDark,
                    focusedBorderColor = if (contactCheck.isBlocked) Color(0xFFFF4455) else BondlyPink,
                    unfocusedBorderColor = if (contactCheck.isBlocked) Color(0xFFFF4455) else CharcoalBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button - Disabled if contact information detected
            val canSend = messageInput.isNotBlank() && !contactCheck.isBlocked
            IconButton(
                onClick = {
                    val text = messageInput.trim()
                    if (text.isNotBlank() && !contactCheck.isBlocked) {
                        messageInput = ""
                        scope.launch {
                            repository.sendMessage(matchId, text = text)
                        }
                    }
                },
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) BondlyPink else CharcoalBorder)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = TextWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showReportSheet) {
            ReportAndBlockSheet(
                targetUserName = otherUser.displayName,
                targetUserId = otherUser.userId,
                onDismiss = { showReportSheet = false },
                onReport = { reason, details ->
                    scope.launch {
                        repository.reportUser(otherUser.userId, reason, details)
                    }
                },
                onBlock = {
                    scope.launch {
                        repository.blockUser(otherUser.userId)
                        onBack()
                    }
                }
            )
        }
    }
}
