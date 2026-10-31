package com.example.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.repository.BondlyRepository
import com.example.ui.theme.*
import com.example.util.ContactSharingFilter
import com.example.util.UserLevelManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

sealed class Screen {
    data object Discover : Screen()
    data object Search : Screen()
    data object Matches : Screen()
    data object Notifications : Screen()
    data object Profile : Screen()
    data object Settings : Screen()
    data object EditProfile : Screen()
    data object Onboarding : Screen()
    data object HelpSupport : Screen()
    data object Admin : Screen()
    data object AccountDeletion : Screen()
    data class Chat(val matchId: String, val partnerName: String, val partnerPhoto: String, val partnerUid: String) : Screen()
    data class Call(val callId: String, val targetUid: String, val targetName: String, val isVideo: Boolean, val isIncoming: Boolean) : Screen()
    data class PublicProfile(val profile: UserProfile) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BondlyApp(repository: BondlyRepository = remember { BondlyRepository() }) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val currentUser by repository.observeAuthState().collectAsState(initial = repository.currentUser)
    val currentProfile by repository.observeProfile(currentUser?.uid ?: "").collectAsState(initial = null)
    val incomingCall by repository.observeIncomingCalls().collectAsState(initial = null)

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Discover) }
    val screenStack = remember { mutableStateListOf<Screen>() }

    fun navigateTo(screen: Screen) {
        screenStack.add(currentScreen)
        currentScreen = screen
    }

    fun navigateBack() {
        if (screenStack.isNotEmpty()) {
            currentScreen = screenStack.removeAt(screenStack.lastIndex)
        } else {
            currentScreen = Screen.Discover
        }
    }

    BackHandler(enabled = currentScreen != Screen.Discover) {
        navigateBack()
    }

    // Permission launcher for calls
    var pendingCallParams by remember { mutableStateOf<Pair<UserProfile, Boolean>?>(null) }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val pending = pendingCallParams
        if (recordAudioGranted && pending != null) {
            coroutineScope.launch {
                val result = repository.initiateCall(pending.first.userId, pending.second)
                result.onSuccess { session ->
                    navigateTo(Screen.Call(session.callId, pending.first.userId, pending.first.displayName, pending.second, false))
                }.onFailure {
                    Toast.makeText(context, "Could not start call: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for calling", Toast.LENGTH_SHORT).show()
        }
        pendingCallParams = null
    }

    fun requestCall(target: UserProfile, isVideo: Boolean) {
        val myLevel = currentProfile?.userLevel ?: 1
        if (myLevel < 2) {
            Toast.makeText(context, "Calling unlocks at Level 2. Keep active on Bondly to unlock!", Toast.LENGTH_LONG).show()
            return
        }
        pendingCallParams = target to isVideo
        val perms = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        callPermissionLauncher.launch(perms)
    }

    // Incoming call dialog
    if (incomingCall != null && currentScreen !is Screen.Call) {
        AlertDialog(
            onDismissRequest = { /* Require user response */ },
            title = { Text("Incoming ${if (incomingCall?.callType == "video") "Video" else "Voice"} Call") },
            text = { Text("${incomingCall?.callerName} is calling you on Bondly...") },
            confirmButton = {
                Button(
                    onClick = {
                        val call = incomingCall ?: return@Button
                        coroutineScope.launch {
                            repository.updateCallStatus(call.callId, "active")
                            navigateTo(Screen.Call(call.callId, call.callerUid, call.callerName, call.callType == "video", true))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen)
                ) {
                    Text("Answer")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val call = incomingCall ?: return@OutlinedButton
                        coroutineScope.launch {
                            repository.updateCallStatus(call.callId, "rejected")
                        }
                    }
                ) {
                    Text("Decline")
                }
            }
        )
    }

    // Unauthenticated State
    if (currentUser == null) {
        AuthScreen(
            onGoogleSignIn = {
                coroutineScope.launch {
                    signInWithGoogle(context, repository) { err ->
                        Toast.makeText(context, err ?: "Signed in successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        return
    }

    // Onboarding / Profile completion check
    if (currentProfile != null && !currentProfile!!.profileCompleted && currentScreen !is Screen.Onboarding) {
        OnboardingScreen(
            initialProfile = currentProfile!!,
            onSave = { updated ->
                coroutineScope.launch {
                    val publicId = if (updated.publicUserId.isEmpty()) repository.allocateUniquePublicId(currentUser!!.uid) else updated.publicUserId
                    val toSave = updated.copy(
                        publicUserId = publicId,
                        profileCompleted = true,
                        rulesAccepted = true
                    )
                    repository.saveProfile(toSave)
                    currentScreen = Screen.Discover
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            if (currentScreen in listOf(Screen.Discover, Screen.Search, Screen.Matches, Screen.Notifications, Screen.Profile)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                Screen.Discover -> "Bondly"
                                Screen.Search -> "Search Bondly ID"
                                Screen.Matches -> "Matches & Chats"
                                Screen.Notifications -> "Notifications"
                                Screen.Profile -> "My Profile"
                                else -> "Bondly"
                            },
                            fontWeight = FontWeight.Bold,
                            color = PinkPrimary
                        )
                    },
                    actions = {
                        if (currentScreen == Screen.Profile) {
                            IconButton(
                                onClick = { navigateTo(Screen.Settings) },
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen in listOf(Screen.Discover, Screen.Search, Screen.Matches, Screen.Notifications, Screen.Profile)) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen == Screen.Discover,
                        onClick = { currentScreen = Screen.Discover },
                        icon = { Icon(if (currentScreen == Screen.Discover) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = "Discover") },
                        label = { Text("Discover") },
                        modifier = Modifier.testTag("nav_discover")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Search,
                        onClick = { currentScreen = Screen.Search },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        modifier = Modifier.testTag("nav_search")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Matches,
                        onClick = { currentScreen = Screen.Matches },
                        icon = { Icon(if (currentScreen == Screen.Matches) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline, contentDescription = "Matches") },
                        label = { Text("Matches") },
                        modifier = Modifier.testTag("nav_matches")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Notifications,
                        onClick = { currentScreen = Screen.Notifications },
                        icon = { Icon(if (currentScreen == Screen.Notifications) Icons.Filled.Notifications else Icons.Outlined.Notifications, contentDescription = "Notifications") },
                        label = { Text("Alerts") },
                        modifier = Modifier.testTag("nav_notifications")
                    )
                    NavigationBarItem(
                        selected = currentScreen == Screen.Profile,
                        onClick = { currentScreen = Screen.Profile },
                        icon = { Icon(if (currentScreen == Screen.Profile) Icons.Filled.Person else Icons.Outlined.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        modifier = Modifier.testTag("nav_profile")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is Screen.Discover -> {
                    DiscoverScreen(
                        repository = repository,
                        myProfile = currentProfile ?: UserProfile(),
                        onProfileClick = { target -> navigateTo(Screen.PublicProfile(target)) },
                        onRequestCall = { target, isVideo -> requestCall(target, isVideo) }
                    )
                }
                is Screen.Search -> {
                    SearchByIdScreen(
                        repository = repository,
                        onUserFound = { userProfile -> navigateTo(Screen.PublicProfile(userProfile)) }
                    )
                }
                is Screen.Matches -> {
                    MatchesScreen(
                        repository = repository,
                        onOpenChat = { match, partnerId, partnerName, partnerPhoto ->
                            navigateTo(Screen.Chat(match.id, partnerName, partnerPhoto, partnerId))
                        }
                    )
                }
                is Screen.Notifications -> {
                    NotificationsScreen(repository = repository)
                }
                is Screen.Profile -> {
                    ProfileOverviewScreen(
                        profile = currentProfile ?: UserProfile(),
                        onOpenSettings = { navigateTo(Screen.Settings) }
                    )
                }
                is Screen.Settings -> {
                    SettingsScreen(
                        profile = currentProfile ?: UserProfile(),
                        onEditProfile = { navigateTo(Screen.EditProfile) },
                        onOpenHelp = { navigateTo(Screen.HelpSupport) },
                        onOpenAdmin = { navigateTo(Screen.Admin) },
                        onDeleteAccount = { navigateTo(Screen.AccountDeletion) },
                        onSignOut = {
                            repository.signOut()
                            currentScreen = Screen.Discover
                        },
                        onBack = { navigateBack() }
                    )
                }
                is Screen.EditProfile -> {
                    EditProfileScreen(
                        profile = currentProfile ?: UserProfile(),
                        onSave = { updated ->
                            coroutineScope.launch {
                                repository.saveProfile(updated)
                                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                navigateBack()
                            }
                        },
                        onBack = { navigateBack() }
                    )
                }
                is Screen.Chat -> {
                    ChatScreen(
                        repository = repository,
                        matchId = screen.matchId,
                        partnerName = screen.partnerName,
                        partnerPhoto = screen.partnerPhoto,
                        partnerUid = screen.partnerUid,
                        myLevel = currentProfile?.userLevel ?: 1,
                        onRequestCall = { isVideo ->
                            coroutineScope.launch {
                                val partnerProfile = repository.getProfile(screen.partnerUid)
                                if (partnerProfile != null) {
                                    requestCall(partnerProfile, isVideo)
                                }
                            }
                        },
                        onBack = { navigateBack() }
                    )
                }
                is Screen.Call -> {
                    CallScreen(
                        repository = repository,
                        callId = screen.callId,
                        targetName = screen.targetName,
                        isVideo = screen.isVideo,
                        onEndCall = {
                            coroutineScope.launch {
                                repository.updateCallStatus(screen.callId, "ended")
                                navigateBack()
                            }
                        }
                    )
                }
                is Screen.PublicProfile -> {
                    PublicProfileDetailScreen(
                        profile = screen.profile,
                        onLike = {
                            coroutineScope.launch {
                                repository.sendLike(screen.profile.userId)
                                Toast.makeText(context, "Interest sent to ${screen.profile.displayName}!", Toast.LENGTH_SHORT).show()
                                navigateBack()
                            }
                        },
                        onBlock = {
                            coroutineScope.launch {
                                repository.blockUser(screen.profile.userId)
                                Toast.makeText(context, "User blocked", Toast.LENGTH_SHORT).show()
                                navigateBack()
                            }
                        },
                        onReport = { reason ->
                            coroutineScope.launch {
                                repository.reportUser(screen.profile.userId, reason, "Flagged from profile")
                                Toast.makeText(context, "Report submitted", Toast.LENGTH_SHORT).show()
                                navigateBack()
                            }
                        },
                        onRequestCall = { isVideo -> requestCall(screen.profile, isVideo) },
                        onBack = { navigateBack() }
                    )
                }
                is Screen.HelpSupport -> {
                    HelpSupportScreen(onBack = { navigateBack() })
                }
                is Screen.Admin -> {
                    AdminModerationScreen(onBack = { navigateBack() })
                }
                is Screen.AccountDeletion -> {
                    AccountDeletionScreen(
                        onConfirmDelete = {
                            coroutineScope.launch {
                                repository.deleteAccount()
                                Toast.makeText(context, "Account permanently deleted", Toast.LENGTH_SHORT).show()
                                currentScreen = Screen.Discover
                            }
                        },
                        onBack = { navigateBack() }
                    )
                }
                is Screen.Onboarding -> {
                    OnboardingScreen(
                        initialProfile = currentProfile ?: UserProfile(),
                        onSave = { updated ->
                            coroutineScope.launch {
                                repository.saveProfile(updated.copy(profileCompleted = true))
                                currentScreen = Screen.Discover
                            }
                        }
                    )
                }
            }
        }
    }
}

// Google Sign-In helper using Credential Manager
suspend fun signInWithGoogle(context: Context, repository: BondlyRepository, onResult: (String?) -> Unit) {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetSignInWithGoogleOption.Builder("148637707279-ls6kl35m66at95q60ufmdfgb8nf07tds.apps.googleusercontent.com")
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(context = context, request = request)
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val idToken = credential.idToken
            val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
            val user = authResult.user
            if (user != null) {
                // Ensure initial profile exists
                val existing = repository.getProfile(user.uid)
                if (existing == null) {
                    val newProfile = UserProfile(
                        userId = user.uid,
                        displayName = user.displayName ?: "New User",
                        profilePhotoUrls = if (user.photoUrl != null) listOf(user.photoUrl.toString()) else emptyList()
                    )
                    repository.saveProfile(newProfile)
                }
                onResult(null)
            }
        }
    } catch (e: GetCredentialException) {
        onResult(e.localizedMessage)
    } catch (e: Exception) {
        onResult(e.localizedMessage)
    }
}
