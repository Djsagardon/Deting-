package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onNavigateToPhotoManagement: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var displayName by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var city by remember(userProfile) { mutableStateOf(userProfile?.city ?: "") }
    var selectedState by remember(userProfile) { mutableStateOf(userProfile?.state?.ifBlank { "Maharashtra" } ?: "Maharashtra") }
    var showStateDropdown by remember { mutableStateOf(false) }
    var bio by remember(userProfile) { mutableStateOf(userProfile?.bio ?: "") }
    var datingPreference by remember(userProfile) { mutableStateOf(userProfile?.datingPreference ?: "Long-term relationship") }
    var selectedHobbies by remember(userProfile) { mutableStateOf(userProfile?.interests?.toSet() ?: emptySet()) }

    var isSaving by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val indianStates = remember {
        listOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Delhi NCR",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu & Kashmir", "Jharkhand",
            "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim",
            "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
        )
    }

    val availableHobbies = remember {
        listOf(
            "Traveling", "Music & Concerts", "Movies & Cinema", "Reading",
            "Cooking & Baking", "Photography", "Fitness & Gym", "Dancing",
            "Gaming & Esports", "Cricket & Sports", "Nature & Wildlife", "Art & Creativity",
            "Food Exploration", "Trekking & Outdoors", "Yoga & Meditation", "Tech & Startups"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("edit_profile_screen")
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = "Edit Profile",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manage Photos Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToPhotoManagement() },
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FF2D75)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = BondlyPink)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Manage 5 Profile Photos",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${userProfile?.profilePhotoUrls?.size ?: 0}/5 photos uploaded • Tap to reorder",
                            color = TextLightGrey,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Display Name
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(14.dp))

        // State Dropdown
        Text(text = "State / UT", color = TextLightGrey, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CharcoalCard)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                .clickable { showStateDropdown = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedState, color = TextWhite, fontSize = 15.sp)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextLightGrey)
            }
            DropdownMenu(
                expanded = showStateDropdown,
                onDismissRequest = { showStateDropdown = false },
                modifier = Modifier.background(CharcoalCard)
            ) {
                indianStates.forEach { st ->
                    DropdownMenuItem(
                        text = { Text(st, color = TextWhite) },
                        onClick = {
                            selectedState = st
                            showStateDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // City
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(14.dp))

        // Bio
        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 500) bio = it },
            label = { Text("About Me / Bio", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CharcoalCard,
                unfocusedContainerColor = CharcoalCard,
                focusedBorderColor = BondlyPink,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            minLines = 3,
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Hobbies
        Text(
            text = "Interests & Hobbies (${selectedHobbies.size}/7)",
            color = TextLightGrey,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableHobbies.forEach { hobby ->
                val isSelected = selectedHobbies.contains(hobby)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) BondlyGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(CharcoalCard, CharcoalCard)))
                        .border(1.dp, if (isSelected) BondlyPink else CharcoalBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            if (isSelected) {
                                selectedHobbies = selectedHobbies - hobby
                            } else if (selectedHobbies.size < 7) {
                                selectedHobbies = selectedHobbies + hobby
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = hobby,
                        color = if (isSelected) TextWhite else TextLightGrey,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if (successMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = successMessage!!, color = Color(0xFF00E676), fontSize = 13.sp)
        }
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = errorMessage!!, color = Color(0xFFFF4455), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Save Profile Details",
            isLoading = isSaving,
            enabled = displayName.isNotBlank() && selectedHobbies.size in 3..7 && !isSaving,
            onClick = {
                if (userProfile != null) {
                    isSaving = true
                    errorMessage = null
                    successMessage = null
                    scope.launch {
                        val result = repository.saveUserProfile(
                            userProfile.copy(
                                displayName = displayName.trim(),
                                city = city.trim(),
                                state = selectedState,
                                bio = bio.trim(),
                                interests = selectedHobbies.toList(),
                                datingPreference = datingPreference
                            )
                        )
                        isSaving = false
                        if (result.isSuccess) {
                            successMessage = "Profile updated successfully!"
                        } else {
                            errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to save profile."
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
