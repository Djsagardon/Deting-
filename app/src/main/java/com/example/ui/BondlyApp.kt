package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileState
import com.example.data.repository.BondlyRepository
import com.example.ui.components.IncomingCallDialog
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.screens.AccountDeletionScreen
import com.example.ui.screens.AdminModerationScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlockedUsersScreen
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.HelpAndSupportScreen
import com.example.ui.screens.MatchesAndChatScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingProfileScreen
import com.example.ui.screens.PhotoManagementScreen
import com.example.ui.screens.PrivacyAndSafetySettingsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.PublicProfileScreen
import com.example.ui.screens.ReportUserScreen
import com.example.ui.screens.SearchByIdScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.util.NetworkMonitor
import com.example.util.UserLevelManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

sealed class Screen {
    object Main : Screen()
    object SearchById : Screen()
    data class PublicProfile(val userId: String) : Screen()
    object EditProfile : Screen()
    object PhotoManagement : Screen()
    object CallHistory : Screen()
    object Settings : Screen()
    object PrivacySafetySettings : Screen()
    object BlockedUsers : Screen()
    data class ReportUser(val targetUid: String, val targetName: String) : Screen()
    object AccountDeletion : Screen()
    object HelpSupport : Screen()
    object Admin : Screen()
    data class Call(
        val callId: String,
        val targetUid: String,
        val targetName: String,
        val isVideo: Boolean,
        val isIncoming: Boolean
    ) : Screen()
}

@Composable
fun BondlyApp() {
    val context = LocalContext.current
    val repository = remember { BondlyRepository(context) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { user ->
                currentUser = user
            }
        )
    } else {
        AuthenticatedAppContent(
            currentUserId = currentUser!!.uid,
            repository = repository,
            onSignOut = {
                Firebase.auth.signOut()
                currentUser = null
            }
        )
    }
}

@Composable
private fun AuthenticatedAppContent(
    currentUserId: String,
    repository: BondlyRepository,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. Online-Only Network Monitoring
    val networkMonitor = remember { NetworkMonitor(context) }
    var offlineRetryTrigger by remember { mutableIntStateOf(0) }
    val isOnlineFlow = remember(offlineRetryTrigger) { networkMonitor.isOnline }
    val isOnline by isOnlineFlow.collectAsState(initial = networkMonitor.isCurrentlyOnline())

    // 2. User Profile state
    var retryTrigger by remember { mutableIntStateOf(0) }
    val userProfileStateFlow = remember(currentUserId, retryTrigger) {
        repository.observeUserProfileState(currentUserId)
    }
    val profileState by userProfileStateFlow.collectAsState(initial = UserProfileState.Loading)

    // Automatically initialize/ensure the user's initial Firestore document
    LaunchedEffect(currentUserId, retryTrigger) {
        repository.ensureInitialProfile(currentUserId)
    }

    // Record daily activity once per day for user level progression
    LaunchedEffect(currentUserId) {
        repository.recordDailyActivity(currentUserId)
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var chatTargetUid by remember { mutableStateOf<String?>(null) }
    var callLockMessage by remember { mutableStateOf<String?>(null) }

    // Real-time Incoming Call Observation
    val incomingCallFlow = remember(currentUserId) { repository.observeIncomingCalls() }
    val incomingCall by incomingCallFlow.collectAsState(initial = null)

    // Handle Incoming Call acceptance or decline
    if (incomingCall != null && currentScreen !is Screen.Call) {
        IncomingCallDialog(
            call = incomingCall!!,
            onAccept = {
                val call = incomingCall!!
                scope.launch {
                    repository.updateCallStatus(call.callId, "accepted")
                    currentScreen = Screen.Call(
                        callId = call.callId,
                        targetUid = call.callerUid,
                        targetName = call.callerName,
                        isVideo = call.callType == "video",
                        isIncoming = true
                    )
                }
            },
            onDecline = {
                val call = incomingCall!!
                scope.launch {
                    repository.updateCallStatus(call.callId, "declined")
                }
            }
        )
    }

    // Call Lock Dialog for progression requirements
    if (callLockMessage != null) {
        AlertDialog(
            onDismissRequest = { callLockMessage = null },
            title = {
                Text(
                    text = "Feature Level Locked",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = callLockMessage ?: "",
                    color = TextLightGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { callLockMessage = null }) {
                    Text("Understood", color = BondlyPink, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CharcoalCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Show full-screen offline overlay if internet connection is lost
    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CharcoalDark)
                .padding(24.dp)
                .testTag("offline_screen"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CharcoalBorder)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FF4455)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline",
                            tint = Color(0xFFFF4455),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "You are offline",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Bondly is an online-only social dating platform. An active internet connection is required to discover members, chat, and connect.\n\nPlease check your Wi-Fi or mobile data.",
                        color = TextLightGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    PrimaryButton(
                        text = "Retry Connection",
                        onClick = {
                            offlineRetryTrigger++
                        }
                    )
                }
            }
        }
        return
    }

    // Handle User Profile Lifecycle States
    when (val state = profileState) {
        is UserProfileState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize().background(CharcoalDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BondlyPink)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading your Bondly profile...",
                        color = TextLightGrey,
                        fontSize = 14.sp
                    )
                }
            }
        }

        is UserProfileState.NotFound, is UserProfileState.Incomplete -> {
            val initial = (state as? UserProfileState.Incomplete)?.profile
            OnboardingProfileScreen(
                currentUserId = currentUserId,
                repository = repository,
                initialProfile = initial,
                onProfileCreated = {
                    // Profile written to Firestore; snapshot listener updates to UserProfileState.Loaded
                }
            )
        }

        is UserProfileState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CharcoalDark)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFF4455),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (state.message.contains("PERMISSION_DENIED", ignoreCase = true)) "Firestore Rules Setup Needed" else "Connection Issue",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Start,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        if (state.canRetry) {
                            PrimaryButton(
                                text = "Retry Loading Profile",
                                onClick = { retryTrigger++ }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        SecondaryButton(
                            text = "Sign Out & Switch Account",
                            onClick = onSignOut
                        )
                    }
                }
            }
        }

        is UserProfileState.Loaded -> {
            val userProfile = state.profile

            // Centralized Call Launcher with Level Gating and caller photo
            val handleStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit = { targetUid, targetName, isVideo ->
                val progression = UserLevelManager.calculateProgression(userProfile)
                if (!isVideo && !progression.isVoiceCallAllowed) {
                    callLockMessage = "Voice Calling is unlocked at Level 2 (Member). Keep using Bondly to progress!\n\nRequirement: 7+ days on Bondly and 5 active days."
                } else if (isVideo && !progression.isVideoCallAllowed) {
                    callLockMessage = "Video Calling is unlocked at Level 3 (Trusted). Keep using Bondly to progress!\n\nRequirement: 30+ days on Bondly and 15 active days."
                } else {
                    scope.launch {
                        val sessionResult = repository.initiateCall(
                            receiverUid = targetUid,
                            receiverName = targetName,
                            callerName = userProfile.displayName,
                            callType = if (isVideo) "video" else "audio",
                            callerPhotoUrl = userProfile.profilePhotoUrls.firstOrNull() ?: ""
                        )
                        sessionResult.onSuccess { session ->
                            currentScreen = Screen.Call(
                                callId = session.callId,
                                targetUid = targetUid,
                                targetName = targetName,
                                isVideo = isVideo,
                                isIncoming = false
                            )
                        }
                    }
                }
            }

            when (val screen = currentScreen) {
                is Screen.Main -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = CharcoalCard,
                                tonalElevation = 8.dp
                            ) {
                                // 0: Discover
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0; chatTargetUid = null },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 0) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Discover",
                                            tint = if (selectedTab == 0) BondlyPink else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Discover",
                                            color = if (selectedTab == 0) TextWhite else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0x33FF2D75)
                                    )
                                )

                                // 1: Matches
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 1) Icons.Filled.Chat else Icons.Outlined.Chat,
                                            contentDescription = "Matches",
                                            tint = if (selectedTab == 1) BondlyPink else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Matches",
                                            color = if (selectedTab == 1) TextWhite else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0x33FF2D75)
                                    )
                                )

                                // 2: Notifications / Alerts (Moved to bottom navigation bar)
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2; chatTargetUid = null },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 2) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                            contentDescription = "Alerts",
                                            tint = if (selectedTab == 2) BondlyPink else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Alerts",
                                            color = if (selectedTab == 2) TextWhite else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0x33FF2D75)
                                    )
                                )

                                // 3: Profile Overview
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3; chatTargetUid = null },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                                            contentDescription = "Profile",
                                            tint = if (selectedTab == 3) BondlyPink else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Profile",
                                            color = if (selectedTab == 3) TextWhite else TextMuted,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0x33FF2D75)
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (selectedTab) {
                                0 -> DiscoverScreen(
                                    currentUserId = currentUserId,
                                    currentUserProfile = userProfile,
                                    repository = repository,
                                    onNavigateToSearchById = { currentScreen = Screen.SearchById },
                                    onNavigateToProfileDetails = { targetUid ->
                                        currentScreen = Screen.PublicProfile(targetUid)
                                    },
                                    onNavigateToChat = { targetUid ->
                                        chatTargetUid = targetUid
                                        selectedTab = 1
                                    },
                                    onStartCall = handleStartCall
                                )
                                1 -> MatchesAndChatScreen(
                                    currentUserId = currentUserId,
                                    repository = repository,
                                    selectedTargetUid = chatTargetUid,
                                    onStartCall = handleStartCall
                                )
                                2 -> NotificationsScreen(
                                    currentUserId = currentUserId,
                                    repository = repository,
                                    onBack = { selectedTab = 0 },
                                    onNavigateToMatches = {
                                        selectedTab = 1
                                    }
                                )
                                3 -> ProfileScreen(
                                    currentUserId = currentUserId,
                                    userProfile = userProfile,
                                    onNavigateToSettings = { currentScreen = Screen.Settings }
                                )
                            }
                        }
                    }
                }

                is Screen.SearchById -> {
                    BackHandler { currentScreen = Screen.Main }
                    SearchByIdScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onViewProfile = { targetUid -> currentScreen = Screen.PublicProfile(targetUid) },
                        onStartCall = handleStartCall
                    )
                }

                is Screen.PublicProfile -> {
                    BackHandler { currentScreen = Screen.Main }
                    PublicProfileScreen(
                        targetUserId = screen.userId,
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToChat = { targetUid ->
                            chatTargetUid = targetUid
                            selectedTab = 1
                            currentScreen = Screen.Main
                        },
                        onStartCall = handleStartCall
                    )
                }

                is Screen.EditProfile -> {
                    BackHandler { currentScreen = Screen.Settings }
                    EditProfileScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings },
                        onNavigateToPhotoManagement = { currentScreen = Screen.PhotoManagement }
                    )
                }

                is Screen.PhotoManagement -> {
                    BackHandler { currentScreen = Screen.Settings }
                    PhotoManagementScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings }
                    )
                }

                is Screen.CallHistory -> {
                    BackHandler { currentScreen = Screen.Settings }
                    CallHistoryScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings },
                        onStartCall = handleStartCall
                    )
                }

                is Screen.Settings -> {
                    BackHandler { currentScreen = Screen.Main }
                    SettingsScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToEditProfile = { currentScreen = Screen.EditProfile },
                        onNavigateToPhotoManagement = { currentScreen = Screen.PhotoManagement },
                        onNavigateToCallHistory = { currentScreen = Screen.CallHistory },
                        onNavigateToPrivacySafety = { currentScreen = Screen.PrivacySafetySettings },
                        onNavigateToBlockedUsers = { currentScreen = Screen.BlockedUsers },
                        onNavigateToAccountDeletion = { currentScreen = Screen.AccountDeletion },
                        onNavigateToHelp = { currentScreen = Screen.HelpSupport },
                        onNavigateToAdmin = { currentScreen = Screen.Admin },
                        onSignOut = onSignOut
                    )
                }

                is Screen.PrivacySafetySettings -> {
                    BackHandler { currentScreen = Screen.Settings }
                    PrivacyAndSafetySettingsScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings },
                        onNavigateToBlockedUsers = { currentScreen = Screen.BlockedUsers }
                    )
                }

                is Screen.BlockedUsers -> {
                    BackHandler { currentScreen = Screen.Settings }
                    BlockedUsersScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings }
                    )
                }

                is Screen.ReportUser -> {
                    BackHandler { currentScreen = Screen.Main }
                    ReportUserScreen(
                        targetUserId = screen.targetUid,
                        targetUserName = screen.targetName,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main }
                    )
                }

                is Screen.AccountDeletion -> {
                    BackHandler { currentScreen = Screen.Settings }
                    AccountDeletionScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Settings },
                        onAccountDeleted = {
                            Firebase.auth.signOut()
                            onSignOut()
                        }
                    )
                }

                is Screen.HelpSupport -> {
                    BackHandler { currentScreen = Screen.Main }
                    HelpAndSupportScreen(
                        onBack = { currentScreen = Screen.Main }
                    )
                }

                is Screen.Admin -> {
                    BackHandler { currentScreen = Screen.Settings }
                    AdminModerationScreen(onBack = { currentScreen = Screen.Settings })
                }

                is Screen.Call -> {
                    BackHandler {
                        currentScreen = Screen.Main
                    }
                    CallScreen(
                        callId = screen.callId,
                        targetUid = screen.targetUid,
                        targetName = screen.targetName,
                        isVideo = screen.isVideo,
                        isIncoming = screen.isIncoming,
                        repository = repository,
                        onCallFinished = { currentScreen = Screen.Main }
                    )
                }
            }
        }
    }
}
