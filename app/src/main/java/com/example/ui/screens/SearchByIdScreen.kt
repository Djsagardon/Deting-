package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchByIdScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onViewProfile: (String) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<UserProfile?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Search by Bondly ID",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Enter a unique Bondly Public ID (e.g., BONDLY-1234) to connect with someone directly.",
                fontSize = 13.sp,
                color = TextMuted
            )

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = {
                        searchInput = it.uppercase(Locale.ROOT)
                        hasSearched = false
                        searchError = null
                    },
                    placeholder = { Text("BONDLY-XXXX", color = TextMuted) },
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
                        val query = searchInput.trim()
                        if (query.isNotEmpty()) {
                            isSearching = true
                            hasSearched = true
                            searchError = null
                            coroutineScope.launch {
                                try {
                                    val user = repository.findUserByPublicId(query)
                                    searchResult = user
                                    if (user == null) {
                                        searchError = "No member found with ID: $query"
                                    }
                                } catch (e: Exception) {
                                    searchError = e.localizedMessage ?: "Failed to search"
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

            // Results
            val result = searchResult
            if (result != null) {
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
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, BondlyPink, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${result.displayName}, ${result.age}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VerifiedBadge()
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                UserIdChip(publicUserId = result.publicUserId, showCopy = false)
                            }
                        }

                        if (result.bio.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = result.bio,
                                fontSize = 13.sp,
                                color = TextLightGrey,
                                maxLines = 2
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

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
            } else if (hasSearched && searchError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "No Member Found",
                        description = searchError ?: "Double check the Bondly ID and try again.",
                        icon = {
                            Icon(Icons.Filled.SearchOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        }
                    )
                }
            }
        }
    }
}
