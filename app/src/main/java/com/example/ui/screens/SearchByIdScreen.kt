package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.util.NoInternetException
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.BondlyErrorClassifier
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import java.util.Locale

private enum class SearchErrorCategory {
    NONE,
    NETWORK,
    PERMISSION,
    NOT_FOUND,
    GENERAL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchByIdScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onViewProfile: (String) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit,
    onNavigateToAdminDashboard: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<UserProfile?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var errorCategory by remember { mutableStateOf(SearchErrorCategory.NONE) }
    var showFullImageViewer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search by Member ID",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalDark)
            )
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "Enter an exact Member ID (e.g. BD979241 or bd979241) to locate their live profile.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            // Input Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = {
                            searchInput = it
                            hasSearched = false
                            searchError = null
                            errorCategory = SearchErrorCategory.NONE
                        },
                        placeholder = { Text("BD979241", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_id_input"),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalCard,
                            unfocusedContainerColor = CharcoalCard,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        leadingIcon = {
                            Icon(Icons.Filled.Tag, contentDescription = null, tint = BondlyPink)
                        }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = {
                            val rawQuery = searchInput.trim()
                            if (rawQuery.isNotEmpty()) {
                                if (!com.example.util.NetworkConnectivityManager.isConnected()) {
                                    hasSearched = true
                                    errorCategory = SearchErrorCategory.NETWORK
                                    searchError = "No internet connection. Please connect to the internet and try again."
                                    return@IconButton
                                }
                                isSearching = true
                                hasSearched = true
                                searchError = null
                                errorCategory = SearchErrorCategory.NONE
                                coroutineScope.launch {
                                    try {
                                        // Secret admin access shortcut
                                        if (rawQuery.equals("admin@123", ignoreCase = true)) {
                                            val isAdmin = repository.checkIsAdmin(currentUserId)
                                            if (isAdmin) {
                                                isSearching = false
                                                onNavigateToAdminDashboard()
                                                return@launch
                                            } else {
                                                errorCategory = SearchErrorCategory.NOT_FOUND
                                                searchError = "No member found with ID: $rawQuery"
                                                isSearching = false
                                                return@launch
                                            }
                                        }

                                        val user = repository.findUserByPublicId(rawQuery, currentUserId)
                                        searchResult = user
                                        if (user == null) {
                                            errorCategory = SearchErrorCategory.NOT_FOUND
                                            val normalizedUpper = rawQuery.uppercase(Locale.ROOT)
                                            searchError = "No member found with ID: $normalizedUpper. Please verify the ID and try again."
                                        }
                                    } catch (e: Exception) {
                                        when {
                                            e is NoInternetException -> {
                                                errorCategory = SearchErrorCategory.NETWORK
                                                searchError = "No internet connection. Please verify your connection and retry."
                                            }
                                            e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                                                errorCategory = SearchErrorCategory.PERMISSION
                                                searchError = "Access denied: Missing permissions to query member index."
                                            }
                                            else -> {
                                                errorCategory = SearchErrorCategory.GENERAL
                                                searchError = BondlyErrorClassifier.getUserFriendlyMessage(e)
                                            }
                                        }
                                    } finally {
                                        isSearching = false
                                    }
                                }
                            }
                        },
                        enabled = searchInput.isNotBlank() && !isSearching,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (searchInput.isNotBlank()) BondlyPink else CharcoalBorder)
                            .testTag("search_submit_button")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextWhite)
                        }
                    }
                }
            }

            // Results View
            val result = searchResult
            if (result != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_result_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val photo = result.profilePhotoUrls.firstOrNull() ?: ""
                                AsyncImage(
                                    model = photo,
                                    contentDescription = result.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, BondlyPink, CircleShape)
                                        .clickable {
                                            if (photo.isNotBlank()) {
                                                showFullImageViewer = true
                                            }
                                        }
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${result.displayName}, ${if (result.age > 0) result.age else 24}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhite
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        VerifiedBadge()
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        UserIdChip(publicUserId = result.effectiveMemberId, showCopy = true)
                                        LevelBadge(level = result.userLevel, isAdmin = result.isAdmin)
                                    }

                                    // Online indicator
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val online = result.isCurrentlyOnline
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (online) OnlineGreen else TextMuted)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (online) "Online now" else "Offline",
                                            fontSize = 11.sp,
                                            color = if (online) OnlineGreen else TextMuted,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (result.city.isNotEmpty() || result.state.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${result.city}${if (result.state.isNotEmpty()) ", ${result.state}" else ""}",
                                        fontSize = 12.sp,
                                        color = TextLightGrey
                                    )
                                }
                            }

                            if (result.bio.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = result.bio,
                                    fontSize = 13.sp,
                                    color = TextLightGrey,
                                    maxLines = 3
                                )
                            }

                            if (result.interests.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    result.interests.take(4).forEach { hobby ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = CharcoalCardElevated,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                                        ) {
                                            Text(
                                                text = hobby,
                                                fontSize = 11.sp,
                                                color = TextWhite,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PrimaryButton(
                                    text = "View Profile",
                                    onClick = { onViewProfile(result.userId) },
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { onStartCall(result.userId, result.displayName, false) },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(CharcoalCardElevated)
                                        .border(1.dp, CharcoalBorder, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Voice Call", tint = OnlineGreen)
                                }

                                IconButton(
                                    onClick = { onStartCall(result.userId, result.displayName, true) },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(CharcoalCardElevated)
                                        .border(1.dp, CharcoalBorder, CircleShape)
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = "Video Call", tint = BondlyPink)
                                }
                            }
                        }
                    }
                }
            } else if (hasSearched && searchError != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (errorCategory) {
                            SearchErrorCategory.NETWORK -> {
                                EmptyState(
                                    title = "Connection Error",
                                    description = searchError ?: "Unable to connect to live database. Please check your internet connection.",
                                    icon = {
                                        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                    }
                                )
                            }
                            SearchErrorCategory.PERMISSION -> {
                                EmptyState(
                                    title = "Access Restricted",
                                    description = searchError ?: "Security rules prevented searching this member ID.",
                                    icon = {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(48.dp))
                                    }
                                )
                            }
                            else -> {
                                EmptyState(
                                    title = "No Member Found",
                                    description = searchError ?: "Double check the Bondly Member ID (e.g. BD979241) and try again.",
                                    icon = {
                                        Icon(Icons.Filled.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFullImageViewer && searchResult?.profilePhotoUrls?.isNotEmpty() == true) {
        FullScreenImageViewer(
            photoUrls = searchResult!!.profilePhotoUrls,
            initialIndex = 0,
            onDismiss = { showFullImageViewer = false }
        )
    }
}
