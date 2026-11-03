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
import com.example.ui.components.IncomingCallDialog
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.util.UserLevelManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun BondlyApp(
    repository: BondlyRepository = remember { BondlyRepository() }
) {
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

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { authedUser ->
                currentUser = authedUser
            }
        )
    } else {
        AuthenticatedAppContent(
            currentUserId = user.uid,
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
    val coroutineScope = rememberCoroutineScope()

    val currentProfile by repository.observeProfile(currentUserId).collectAsState(initial = null)
    val incomingCall by repository.observeIncomingCalls().collectAsState(initial = null)

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    val screenStack = remember { mutableStateListOf<Screen>() }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Discover, 1: Matches, 2: Alerts, 3: Profile

    fun navigateTo(screen: Screen) {
        screenStack.add(currentScreen)
        currentScreen = screen
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
                    navigateTo(Screen.Call(session.callId, targetUid, targetName, isVideo, false))
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
        val profile = currentProfile ?: UserProfile()
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

    // Check for mandatory profile completion
    val profile = currentProfile
    if (profile != null && !profile.profileCompleted) {
        OnboardingProfileScreen(
            currentUserId = currentUserId,
            repository = repository,
            initialProfile = profile,
            onProfileCreated = {
                currentScreen = Screen.Main
                selectedTab = 0
            }
        )
        return
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
                            isIncoming = true
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
                            onNavigateToChat = {
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
                            }
                        )
                        2 -> NotificationsScreen(
                            currentUserId = currentUserId,
                            repository = repository,
                            onBack = { selectedTab = 0 },
                            onNavigateToMatches = { selectedTab = 1 }
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
                        }
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
                    AdminModerationScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { navigateBack() }
                    )
                }

                is Screen.Call -> {
                    CallScreen(
                        callId = screen.callId,
                        targetUid = screen.targetUid,
                        targetName = screen.targetName,
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
