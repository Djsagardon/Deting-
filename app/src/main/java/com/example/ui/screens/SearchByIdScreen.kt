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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.components.OnlineIndicator
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.UserIdChip
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

@Composable
fun SearchByIdScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onViewProfile: (String) -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<UserProfile?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var callPermissionError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(20.dp)
            .testTag("search_by_id_screen")
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CharcoalCard)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Search by User ID",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Find friends or matches with their unique public ID",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Input Field
        OutlinedTextField(
            value = searchInput,
            onValueChange = {
                searchInput = it.uppercase()
                callPermissionError = null
            },
            placeholder = { Text("Enter Public ID (e.g. BD482731)", color = TextMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = BondlyPink
                )
            },
            trailingIcon = {
                if (searchInput.isNotEmpty()) {
                    IconButton(onClick = { searchInput = ""; searchResult = null; hasSearched = false }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("search_by_id_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CharcoalCard,
                unfocusedContainerColor = CharcoalCard,
                focusedBorderColor = BondlyPink,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryButton(
            text = "Search User",
            isLoading = isSearching,
            enabled = searchInput.trim().length >= 4 && !isSearching,
            onClick = {
                isSearching = true
                hasSearched = true
                callPermissionError = null
                scope.launch {
                    val result = repository.searchByPublicUserId(searchInput.trim())
                    searchResult = result
                    isSearching = false
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (callPermissionError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FF4455))
                    .border(1.dp, Color(0x66FF4455), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = callPermissionError!!,
                    color = Color(0xFFFF6677),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Search Result Card
        if (isSearching) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BondlyPink)
            }
        } else if (searchResult != null) {
            val user = searchResult!!
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
                    .testTag("search_result_card"),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photo = user.profilePhotoUrls.firstOrNull() ?: ""
                        if (photo.isNotBlank()) {
                            AsyncImage(
                                model = photo,
                                contentDescription = user.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(BondlyGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayName.take(1).uppercase(),
                                    color = TextWhite,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${user.displayName}, ${user.age}",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                if (user.verificationStatus == "verified") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VerifiedBadge()
                                }
                                if (user.showOnlineStatus) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OnlineIndicator()
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            UserIdChip(publicUserId = user.publicUserId, showCopy = false)
                            if (user.city.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = user.city, color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }

                    if (user.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = user.bio, color = TextLightGrey, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Calling and Viewing Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = "View Profile",
                            onClick = { onViewProfile(user.userId) },
                            modifier = Modifier.weight(1f)
                        )

                        // Call Action based on target user's call permission
                        PrimaryButton(
                            text = "Call",
                            onClick = {
                                if (user.allowCallsFrom == "none") {
                                    callPermissionError = "${user.displayName} has disabled incoming calls."
                                } else {
                                    onStartCall(user.userId, user.displayName, true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = TextWhite)
                            }
                        )
                    }
                }
            }
        } else if (hasSearched) {
            EmptyState(
                title = "No User Found",
                description = "No active profile matches the public ID \"$searchInput\". Please verify the code and try again."
            )
        }
    }
}
