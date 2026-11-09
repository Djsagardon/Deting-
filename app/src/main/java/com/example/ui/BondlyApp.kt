package com.example.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.repository.BondlyRepository
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.components.IncomingCallDialog
import com.example.ui.components.NoInternetScreen
import com.example.ui.components.PrimaryButton
import androidx.compose.material.icons.filled.Warning
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.util.NetworkConnectivityManager
import com.example.util.UserLevelManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.AppAuthState
import com.google.firebase.auth.FirebaseUser

import com.example.NotificationPayload

@Composable
fun BondlyApp(
    repository: BondlyRepository = remember { BondlyRepository() },
    incomingNotification: NotificationPayload? = null,
    onConsumeNotification: () -> Unit = {}
) {

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NetworkConnectivityManager.initialize(context)
    }

    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val isOnline by NetworkConnectivityManager.isOnline.collectAsState()

    // Strict online-only barrier for authenticated users
    if (!isOnline && authState !is AppAuthState.Unauthenticated) {
        NoInternetScreen(
            onRetry = {
                authViewModel.retry()
            }
        )
        return
    }

    when (val state = authState) {
        is AppAuthState.Initializing -> {
            Box(
                modifier = Modifier.fillMaxSize().background(CharcoalDark),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BondlyPink, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting to Bondly...", color = TextWhite, fontSize = 14.sp)
                }
            }
        }

        is AppAuthState.Unauthenticated, is AppAuthState.AuthFailed -> {
            AuthScreen(
                onAuthSuccess = { authedUser ->
                    authViewModel.resolveUserProfile(authedUser)
                }
            )
        }

        is AppAuthState.NetworkUnavailable -> {
            NoInternetScreen(
                onRetry = {
                    authViewModel.retry()
                }
            )
        }

        is AppAuthState.RequestFailed -> {
            Box(
                modifier = Modifier.fillMaxSize().background(CharcoalDark).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                    Text("Could not connect to Bondly", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(state.message, color = TextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (state.canRetry) {
                        PrimaryButton(text = "Retry", onClick = { authViewModel.retry() })
                    }
                }
            }
        }

        is AppAuthState.AuthenticatedProfileNotCreated -> {
            OnboardingProfileScreen(
                currentUserId = state.userId,
                repository = repository,
                initialProfile = null,
                onProfileCreated = { completedProfile ->
                    authViewModel.onProfileCreationSuccess(completedProfile)
                }
            )
        }

        is AppAuthState.AuthenticatedProfileIncomplete -> {
            OnboardingProfileScreen(
                currentUserId = state.userId,
                repository = repository,
                initialProfile = state.profile,
                onProfileCreated = { completedProfile ->
                    authViewModel.onProfileCreationSuccess(completedProfile)
                }
            )
        }

        is AppAuthState.ProfileSetupInProgress -> {
            OnboardingProfileScreen(
                currentUserId = state.userId,
                repository = repository,
                initialProfile = null,
                onProfileCreated = { completedProfile ->
                    authViewModel.onProfileCreationSuccess(completedProfile)
                }
            )
        }

        is AppAuthState.AuthenticatedProfileCompleted -> {
            AuthenticatedAppContent(
                currentUserId = state.userId,
                initialProfile = state.profile,
                repository = repository,
                incomingNotification = incomingNotification,
                onConsumeNotification = onConsumeNotification,
                onSignOut = {
                    authViewModel.signOut()
                }
            )
        }

    }
}

@Composable
private fun AuthenticatedAppContent(
    currentUserId: String,
    initialProfile: UserProfile,
    repository: BondlyRepository,
    incomingNotification: NotificationPayload? = null,
    onConsumeNotification: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isOnline by NetworkConnectivityManager.isOnline.collectAsState()
    var refreshTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isOnline) {
        if (isOnline) {
            refreshTrigger++
        }
    }

    // Strict Online-Only Barrier: When no internet connection, block all operations with clear message and Retry
    if (!isOnline) {
        NoInternetScreen(
            onRetry = {
                coroutineScope.launch {
                    val live = NetworkConnectivityManager.probeLiveInternet()
                    if (live) {
                        NetworkConnectivityManager.checkConnectivity(false)
                        refreshTrigger++
                    }
                }
            }
        )
        return
    }

    // Notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Sync FCM Token
    LaunchedEffect(currentUserId) {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    coroutineScope.launch {
                        repository.registerFcmToken(token)
                    }
                }
        } catch (_: Exception) {}
    }

    // Real-time live incoming notification listener for system tray alerts
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            com.example.service.BondlyNotificationManager.createNotificationChannels(context)
            try {
                repository.observeLiveIncomingNotifications(currentUserId).collect { notif ->
                    com.example.service.BondlyNotificationManager.showSystemNotification(
                        context = context,
                        title = notif.title,
                        message = notif.message,
                        type = notif.type,
                        targetId = notif.targetId,
                        senderName = notif.senderName
                    )
                    repository.markNotificationAsDelivered(notif.id, currentUserId)
                }
            } catch (e: Exception) {
                android.util.Log.e("BondlyApp", "Live notification listener error: ${e.message}")
            }
        }
    }

    // Accurate User Session Tracking
    DisposableEffect(currentUserId) {
        val sessionStart = System.currentTimeMillis()
        onDispose {
            val duration = (System.currentTimeMillis() - sessionStart) / 1000L
            coroutineScope.launch {
                repository.logUserSession(duration)
            }
        }
    }

    val liveProfile by remember(currentUserId) { repository.observeProfile(currentUserId) }.collectAsState(initial = initialProfile)
    val currentProfile = liveProfile ?: initialProfile
    val incomingCall by remember(currentUserId) { repository.observeIncomingCalls() }.collectAsState(initial = null)

    // Accurate real-time online presence management
    DisposableEffect(currentUserId, isOnline) {
        if (isOnline && currentUserId.isNotEmpty()) {
            coroutineScope.launch {
                repository.setUserOnline(true)
            }
        }
        onDispose {
            if (currentUserId.isNotEmpty()) {
                coroutineScope.launch {
                    repository.setUserOnline(false)
                }
            }
        }
    }

    // Live heartbeat every 45s while user is active
    LaunchedEffect(currentUserId, isOnline) {
        while (isOnline && currentUserId.isNotEmpty()) {
            kotlinx.coroutines.delay(45_000L)
            repository.setUserOnline(true)
        }
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    val screenStack = remember { mutableStateListOf<Screen>() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Discover, 1: Matches, 2: Alerts, 3: Profile
    var pendingChatPartnerUid by remember { mutableStateOf<String?>(null) }

    fun navigateTo(screen: Screen) {
        screenStack.add(currentScreen)
        currentScreen = screen
    }

    // Automatic Notification Tap Navigation handler
    LaunchedEffect(incomingNotification) {
        val payload = incomingNotification ?: return@LaunchedEffect
        when (payload.type) {
            "match_request", "profile" -> {
                if (payload.targetId.isNotBlank()) {
                    navigateTo(Screen.PublicProfile(payload.targetId))
                } else {
                    selectedTab = 2 // Alerts tab
                    currentScreen = Screen.Main
                }
            }
            "chat", "message" -> {
                if (payload.targetId.isNotBlank()) {
                    pendingChatPartnerUid = payload.targetId
                }
                selectedTab = 1 // Matches and Chat tab
                currentScreen = Screen.Main
            }
            "match", "match_accepted" -> {
                if (payload.targetId.isNotBlank()) {
                    pendingChatPartnerUid = payload.targetId
                }
                selectedTab = 1 // Matches tab
                currentScreen = Screen.Main
            }
            "admin_announcement", "announcement", "system" -> {
                selectedTab = 2 // Alerts / Notifications tab
                currentScreen = Screen.Main
            }
            else -> {
                if (payload.targetId.isNotBlank()) {
                    navigateTo(Screen.PublicProfile(payload.targetId))
                }
            }
        }
        onConsumeNotification()
    }


    fun navigateBack() {
        if (screenStack.isNotEmpty()) {
            currentScreen = screenStack.removeAt(screenStack.lastIndex)
        } else {
            currentScreen = Screen.Main
        }
    }

    BackHandler(enabled = currentScreen != Screen.Main) {
        navigateBack()
    }

    // Call permissions launcher
    var pendingCallParams by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val pending = pendingCallParams
        if (recordAudioGranted && pending != null) {
            val (targetUid, targetName, isVideo) = pending
            coroutineScope.launch {
                val result = repository.initiateCall(targetUid, isVideo)
                result.onSuccess { session ->
                    navigateTo(Screen.Call(session.callId, targetUid, targetName, isVideo, false, targetPhoto = session.receiverPhotoUrl))
                }.onFailure {
                    Toast.makeText(context, "Could not start call: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Microphone and camera permissions are required to call.", Toast.LENGTH_SHORT).show()
        }
        pendingCallParams = null
    }

    fun handleStartCall(targetUid: String, targetName: String, isVideo: Boolean) {
        if (!NetworkConnectivityManager.isConnected()) {
            Toast.makeText(context, "No internet connection. Please connect to the internet and try again.", Toast.LENGTH_SHORT).show()
            return
        }
        val profile = currentProfile
        val progression = UserLevelManager.calculateProgression(profile)
        if (isVideo) {
            if (!progression.isVideoCallAllowed) {
                Toast.makeText(
                    context,
                    "Video Calling is unlocked at Level 3 (Trusted). Keep using Bondly to progress!\n\nRequirement: 30+ days on Bondly and 15 active days.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        } else {
            if (!progression.isVoiceCallAllowed) {
                Toast.makeText(
                    context,
                    "Voice Calling is unlocked at Level 2 (Member). Keep using Bondly to progress!\n\nRequirement: 7+ days on Bondly and 5 active days.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        pendingCallParams = Triple(targetUid, targetName, isVideo)
        val perms = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        callPermissionLauncher.launch(perms)
    }

    // Incoming Call Dialog
    val call = incomingCall
    if (call != null && currentScreen !is Screen.Call) {
        IncomingCallDialog(
            call = call,
            onAccept = {
                coroutineScope.launch {
                    repository.updateCallStatus(call.callId, "active")
                    navigateTo(
                        Screen.Call(
                            callId = call.callId,
                            targetUid = call.callerUid,
                            targetName = call.callerName,
                            isVideo = call.callType == "video",
                            isIncoming = true,
                            targetPhoto = call.callerPhotoUrl
                        )
                    )
                }
            },
            onDecline = {
                coroutineScope.launch {
                    repository.updateCallStatus(call.callId, "rejected")
                }
            }
        )
    }

    // Main Scaffold
    Scaffold(
        bottomBar = {
            if (currentScreen == Screen.Main) {
                NavigationBar(
                    containerColor = CharcoalCard,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    // Tab 0: Discover
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Discover",
                                tint = if (selectedTab == 0) BondlyPink else TextMuted
                            )
                        },
                        label = {
                            Text(
                                "Discover",
                                fontSize = 11.sp,
                                color = if (selectedTab == 0) TextWhite else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CharcoalCardElevated
                        ),
                        modifier = Modifier.testTag("nav_discover")
                    )

                    // Tab 1: Matches
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
                                "Matches",
                                fontSize = 11.sp,
                                color = if (selectedTab == 1) TextWhite else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CharcoalCardElevated
                        ),
                        modifier = Modifier.testTag("nav_matches")
                    )

                    // Tab 2: Alerts
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 2) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Alerts",
                                tint = if (selectedTab == 2) BondlyPink else TextMuted
                            )
                        },
                        label = {
                            Text(
                                "Alerts",
                                fontSize = 11.sp,
                                color = if (selectedTab == 2) TextWhite else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CharcoalCardElevated
                        ),
                        modifier = Modifier.testTag("nav_alerts")
                    )

                    // Tab 3: Profile
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "Profile",
                                tint = if (selectedTab == 3) BondlyPink else TextMuted
                            )
                        },
                        label = {
                            Text(
                                "Profile",
                                fontSize = 11.sp,
                                color = if (selectedTab == 3) TextWhite else TextMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = CharcoalCardElevated
                        ),
                        modifier = Modifier.testTag("nav_profile")
                    )
                }
            }
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (currentScreen == Screen.Main) innerPadding else PaddingValues(0.dp))
                .background(CharcoalDark)
        ) {
            when (val screen = currentScreen) {
                is Screen.Main -> {
                    when (selectedTab) {
                        0 -> DiscoverScreen(
                            currentUserId = currentUserId,
                            currentUserProfile = currentProfile,
                            repository = repository,
                            onNavigateToSearchById = { navigateTo(Screen.SearchById) },
                            onNavigateToProfileDetails = { uid -> navigateTo(Screen.PublicProfile(uid)) },
                            onNavigateToChat = { targetUid ->
                                pendingChatPartnerUid = targetUid
                                selectedTab = 1
                            },
                            onStartCall = { targetUid, targetName, isVideo ->
                                handleStartCall(targetUid, targetName, isVideo)
                            }
                        )
                        1 -> MatchesAndChatScreen(
                            currentUserId = currentUserId,
                            currentUserProfile = currentProfile ?: UserProfile(),
                            repository = repository,
                            onStartCall = { targetUid, targetName, isVideo ->
                                handleStartCall(targetUid, targetName, isVideo)
                            },
                            onNavigateToPublicProfile = { uid ->
                                navigateTo(Screen.PublicProfile(uid))
                            },
                            initialPartnerUid = pendingChatPartnerUid,
                            onClearInitialPartner = { pendingChatPartnerUid = null }
                        )
                        2 -> NotificationsScreen(
                            currentUserId = currentUserId,
                            repository = repository,
                            onBack = { selectedTab = 0 },
                            onNavigateToMatches = { selectedTab = 1 },
                            onNavigateToProfile = { uid -> navigateTo(Screen.PublicProfile(uid)) }
                        )
                        3 -> ProfileScreen(
                            currentUserId = currentUserId,
                            userProfile = currentProfile,
                            onNavigateToSettings = { navigateTo(Screen.Settings) }
                        )
                    }
                }

                is Screen.SearchById -> {
                    SearchByIdScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() },
                        onViewProfile = { uid -> navigateTo(Screen.PublicProfile(uid)) },
                        onStartCall = { targetUid, targetName, isVideo ->
                            handleStartCall(targetUid, targetName, isVideo)
                        },
                        onNavigateToAdminDashboard = { navigateTo(Screen.Admin) }
                    )
                }

                is Screen.PublicProfile -> {
                    PublicProfileScreen(
                        targetUserId = screen.userId,
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() },
                        onStartCall = { targetUid, targetName, isVideo ->
                            handleStartCall(targetUid, targetName, isVideo)
                        },
                        onReportUser = { targetUid, targetName ->
                            navigateTo(Screen.ReportUser(targetUid, targetName))
                        },
                        onNavigateToChat = { targetUid ->
                            pendingChatPartnerUid = targetUid
                            selectedTab = 1
                            currentScreen = Screen.Main
                        }
                    )
                }

                is Screen.EditProfile -> {
                    EditProfileScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.PhotoManagement -> {
                    PhotoManagementScreen(
                        currentUserId = currentUserId,
                        profile = currentProfile ?: UserProfile(),
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.CallHistory -> {
                    CallHistoryScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() },
                        onStartCall = { targetUid, targetName, isVideo ->
                            handleStartCall(targetUid, targetName, isVideo)
                        }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        currentUserId = currentUserId,
                        userProfile = currentProfile,
                        repository = repository,
                        onBack = { navigateBack() },
                        onNavigateToEditProfile = { navigateTo(Screen.EditProfile) },
                        onNavigateToPhotoManagement = { navigateTo(Screen.PhotoManagement) },
                        onNavigateToCallHistory = { navigateTo(Screen.CallHistory) },
                        onNavigateToPrivacySafety = { navigateTo(Screen.PrivacySafetySettings) },
                        onNavigateToBlockedUsers = { navigateTo(Screen.BlockedUsers) },
                        onNavigateToAccountDeletion = { navigateTo(Screen.AccountDeletion) },
                        onNavigateToHelp = { navigateTo(Screen.HelpSupport) },
                        onNavigateToAdmin = { navigateTo(Screen.Admin) },
                        onSignOut = onSignOut
                    )
                }

                is Screen.PrivacySafetySettings -> {
                    PrivacySafetySettingsScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.BlockedUsers -> {
                    BlockedUsersScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.ReportUser -> {
                    ReportUserScreen(
                        targetUid = screen.targetUid,
                        targetName = screen.targetName,
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.AccountDeletion -> {
                    AccountDeletionScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() },
                        onAccountDeleted = onSignOut
                    )
                }

                is Screen.HelpSupport -> {
                    HelpAndSupportScreen(
                        onBack = { navigateBack() }
                    )
                }

                is Screen.Admin -> {
                    AdminDashboardScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() },
                        onViewUserProfile = { uid -> navigateTo(Screen.PublicProfile(uid)) }
                    )
                }

                is Screen.Call -> {
                    CallScreen(
                        callId = screen.callId,
                        targetUid = screen.targetUid,
                        targetName = screen.targetName,
                        targetPhoto = screen.targetPhoto,
                        isVideo = screen.isVideo,
                        isIncoming = screen.isIncoming,
                        repository = repository,
                        onCallFinished = { navigateBack() }
                    )
                }
            }
        }
    }
}
