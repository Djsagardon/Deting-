package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.Match
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
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
import com.example.util.ContactSharingFilter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchesAndChatScreen(
    currentUserId: String,
    repository: BondlyRepository,
    selectedTargetUid: String? = null,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val matchesFlow = remember(currentUserId) { repository.observeMatches() }
    val matches by matchesFlow.collectAsState(initial = null)

    var activeChatMatchId by remember { mutableStateOf<String?>(null) }
    var activeChatOtherUser by remember { mutableStateOf<UserProfile?>(null) }

    // Resolve active chat if targeted from Discover or Search
    LaunchedEffect(selectedTargetUid, matches) {
        if (!selectedTargetUid.isNullOrBlank()) {
            val otherProfile = repository.getUserProfile(selectedTargetUid)
            activeChatOtherUser = otherProfile ?: UserProfile(
                userId = selectedTargetUid,
                displayName = "Match"
            )
            val user1 = if (currentUserId < selectedTargetUid) currentUserId else selectedTargetUid
            val user2 = if (currentUserId < selectedTargetUid) selectedTargetUid else currentUserId
            activeChatMatchId = "${user1}_${user2}"
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
            val otherUid = match.getOtherUserId(currentUserId)
            if (otherUid.isNotBlank() && !userProfilesMap.containsKey(otherUid)) {
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
                description = "Swipe and like singles in Discover to connect! When both of you like each other, mutual matches appear here.",
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
                    val otherUid = match.getOtherUserId(currentUserId)
                    val otherProfile = userProfilesMap[otherUid]
                    val displayName = otherProfile?.displayName ?: match.getOtherUserName(currentUserId)
                    val photoUrl = otherProfile?.profilePhotoUrls?.firstOrNull() ?: match.getOtherUserPhoto(currentUserId)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val targetToOpen = otherProfile ?: UserProfile(
                                userId = otherUid,
                                displayName = displayName,
                                profilePhotoUrls = listOfNotNull(photoUrl.takeIf { it.isNotBlank() })
                            )
                            onSelectMatch(match, targetToOpen)
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
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = displayName.take(1).uppercase(),
                                    color = TextWhite,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayName,
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
                    val otherUid = match.getOtherUserId(currentUserId)
                    val otherProfile = userProfilesMap[otherUid]
                    val displayName = otherProfile?.displayName ?: match.getOtherUserName(currentUserId)
                    val photoUrl = otherProfile?.profilePhotoUrls?.firstOrNull() ?: match.getOtherUserPhoto(currentUserId)

                    val timeStr = match.lastMessageTimestamp?.toDate()?.let {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(it)
                    } ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val targetToOpen = otherProfile ?: UserProfile(
                                    userId = otherUid,
                                    displayName = displayName,
                                    profilePhotoUrls = listOfNotNull(photoUrl.takeIf { it.isNotBlank() })
                                )
                                onSelectMatch(match, targetToOpen)
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(54.dp)) {
                            if (photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = displayName,
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
                                        text = displayName.take(1).uppercase(),
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
                                    text = displayName,
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
                                text = match.lastMessage.ifBlank { "You matched! Say hello 👋" },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveChatView(
    matchId: String,
    otherUser: UserProfile,
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messagesFlow = remember(matchId, currentUserId) { repository.observeMessages(matchId, currentUserId) }
    val messages by messagesFlow.collectAsState(initial = emptyList())

    var messageInput by remember { mutableStateOf("") }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

    // Message Deletion State
    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteAction by remember { mutableStateOf<String?>(null) } // "everyone" or "me"

    // Auto-scroll to bottom on opening or receiving new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

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

        // Messages List with Date Separators
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "You're connected with ${otherUser.displayName}!",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Say hello and start getting to know each other.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val dayFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
            val labelFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    // Calculate date separator
                    val currentMsgDate = message.createdAt?.toDate() ?: Date(message.localTimestamp)
                    val prevMsgDate = if (index > 0) {
                        messages[index - 1].createdAt?.toDate() ?: Date(messages[index - 1].localTimestamp)
                    } else null

                    val showDateHeader = prevMsgDate == null || dayFormat.format(currentMsgDate) != dayFormat.format(prevMsgDate)

                    if (showDateHeader) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labelFormat.format(currentMsgDate),
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CharcoalCard)
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    ChatMessageBubble(
                        message = message,
                        isFromMe = message.senderId == currentUserId,
                        onLongClick = {
                            selectedMessageForAction = message
                        }
                    )
                }
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

        // WhatsApp-Style Contextual Message Action Bottom Sheet
        if (selectedMessageForAction != null) {
            val msg = selectedMessageForAction!!
            val isSender = msg.senderId == currentUserId
            val sheetState = rememberModalBottomSheetState()

            ModalBottomSheet(
                onDismissRequest = { selectedMessageForAction = null },
                sheetState = sheetState,
                containerColor = CharcoalCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "Message Options",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!msg.isDeleted() && msg.text.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Bondly Message", msg.text))
                                    Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                                    selectedMessageForAction = null
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextLightGrey)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Copy Text", color = TextWhite, fontSize = 15.sp)
                        }
                    }

                    if (isSender && !msg.isDeleted()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pendingDeleteAction = "everyone"
                                    showDeleteConfirmDialog = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color(0xFFFF4455))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Delete for everyone", color = Color(0xFFFF4455), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pendingDeleteAction = "me"
                                showDeleteConfirmDialog = true
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextLightGrey)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Delete for me", color = TextWhite, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog && selectedMessageForAction != null) {
            val msg = selectedMessageForAction!!
            val isEveryone = pendingDeleteAction == "everyone"

            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    pendingDeleteAction = null
                },
                title = {
                    Text(
                        text = if (isEveryone) "Delete for everyone?" else "Delete for you?",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = if (isEveryone) {
                            "This message will be removed for both you and ${otherUser.displayName}. A deletion marker will be displayed."
                        } else {
                            "This message will be removed from your chat history only. Other participants will still be able to view it."
                        },
                        color = TextLightGrey,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            val action = pendingDeleteAction
                            pendingDeleteAction = null
                            selectedMessageForAction = null
                            scope.launch {
                                if (action == "everyone") {
                                    repository.deleteMessageForEveryone(matchId, msg.id)
                                } else {
                                    repository.deleteMessageForMe(matchId, msg.id)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFFF4455),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            pendingDeleteAction = null
                        }
                    ) {
                        Text("Cancel", color = TextLightGrey)
                    }
                },
                containerColor = CharcoalCard,
                shape = RoundedCornerShape(20.dp)
            )
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
