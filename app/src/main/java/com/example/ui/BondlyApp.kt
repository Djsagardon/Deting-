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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    object Notifications : Screen()
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
    val scope = rememberCoroutineScope()
    var retryTrigger by remember { mutableIntStateOf(0) }
    val userProfileStateFlow = remember(currentUserId, retryTrigger) {
        repository.observeUserProfileState(currentUserId)
    }
    val profileState by userProfileStateFlow.collectAsState(initial = UserProfileState.Loading)

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var chatTargetUid by remember { mutableStateOf<String?>(null) }

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
            OnboardingProfileScreen(
                currentUserId = currentUserId,
                repository = repository,
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
                            text = "Connection Issue",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
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
            when (val screen = currentScreen) {
                is Screen.Main -> {
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = CharcoalCard,
                                tonalElevation = 8.dp
                            ) {
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

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2; chatTargetUid = null },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 2) Icons.Filled.Person else Icons.Outlined.Person,
                                            contentDescription = "Profile",
                                            tint = if (selectedTab == 2) BondlyPink else TextMuted
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "Profile",
                                            color = if (selectedTab == 2) TextWhite else TextMuted,
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
                                    onStartCall = { targetUid, targetName, isVideo ->
                                        scope.launch {
                                            val sessionResult = repository.initiateCall(
                                                receiverUid = targetUid,
                                                receiverName = targetName,
                                                callerName = userProfile.displayName,
                                                callType = if (isVideo) "video" else "audio"
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
                                )
                                1 -> MatchesAndChatScreen(
                                    currentUserId = currentUserId,
                                    repository = repository,
                                    selectedTargetUid = chatTargetUid,
                                    onStartCall = { targetUid, targetName, isVideo ->
                                        scope.launch {
                                            val sessionResult = repository.initiateCall(
                                                receiverUid = targetUid,
                                                receiverName = targetName,
                                                callerName = userProfile.displayName,
                                                callType = if (isVideo) "video" else "audio"
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
                                )
                                2 -> ProfileScreen(
                                    currentUserId = currentUserId,
                                    userProfile = userProfile,
                                    repository = repository,
                                    onNavigateToEditProfile = { currentScreen = Screen.EditProfile },
                                    onNavigateToPhotoManagement = { currentScreen = Screen.PhotoManagement },
                                    onNavigateToCallHistory = { currentScreen = Screen.CallHistory },
                                    onNavigateToNotifications = { currentScreen = Screen.Notifications },
                                    onNavigateToSettings = { currentScreen = Screen.Settings },
                                    onNavigateToHelp = { currentScreen = Screen.HelpSupport }
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
                        onStartCall = { targetUid, targetName, isVideo ->
                            scope.launch {
                                val sessionResult = repository.initiateCall(
                                    receiverUid = targetUid,
                                    receiverName = targetName,
                                    callerName = userProfile.displayName,
                                    callType = if (isVideo) "video" else "audio"
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
                        onStartCall = { targetUid, targetName, isVideo ->
                            scope.launch {
                                val sessionResult = repository.initiateCall(
                                    receiverUid = targetUid,
                                    receiverName = targetName,
                                    callerName = userProfile.displayName,
                                    callType = if (isVideo) "video" else "audio"
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
                    )
                }

                is Screen.EditProfile -> {
                    BackHandler { currentScreen = Screen.Main }
                    EditProfileScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToPhotoManagement = { currentScreen = Screen.PhotoManagement }
                    )
                }

                is Screen.PhotoManagement -> {
                    BackHandler { currentScreen = Screen.Main }
                    PhotoManagementScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main }
                    )
                }

                is Screen.CallHistory -> {
                    BackHandler { currentScreen = Screen.Main }
                    CallHistoryScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onStartCall = { targetUid, targetName, isVideo ->
                            scope.launch {
                                val sessionResult = repository.initiateCall(
                                    receiverUid = targetUid,
                                    receiverName = targetName,
                                    callerName = userProfile.displayName,
                                    callType = if (isVideo) "video" else "audio"
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
                    )
                }

                is Screen.Notifications -> {
                    BackHandler { currentScreen = Screen.Main }
                    NotificationsScreen(
                        currentUserId = currentUserId,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToMatches = {
                            selectedTab = 1
                            currentScreen = Screen.Main
                        }
                    )
                }

                is Screen.Settings -> {
                    BackHandler { currentScreen = Screen.Main }
                    SettingsScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        repository = repository,
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToPrivacySafety = { currentScreen = Screen.PrivacySafetySettings },
                        onNavigateToBlockedUsers = { currentScreen = Screen.BlockedUsers },
                        onNavigateToAccountDeletion = { currentScreen = Screen.AccountDeletion },
                        onNavigateToHelp = { currentScreen = Screen.HelpSupport },
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
                    BackHandler { currentScreen = Screen.Main }
                    AdminModerationScreen(onBack = { currentScreen = Screen.Main })
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
