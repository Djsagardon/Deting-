package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch

val AvailableHobbies = listOf(
    "Cricket", "Bollywood", "Chai & Conversations", "Travel & Roadtrips", "Cooking & Foodie",
    "Fitness & Yoga", "Music & Concerts", "Reading", "Photography", "Tech & Gaming",
    "Art & Painting", "Pets & Animals", "Coffee & Cafes", "Dancing", "Movies & OTT"
)

val IndianStates = listOf(
    "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa",
    "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala",
    "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland",
    "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura",
    "Uttar Pradesh", "Uttarakhand", "West Bengal", "Delhi NCR", "Jammu and Kashmir"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by repository.observeProfile(currentUserId).collectAsState(initial = null)

    var displayName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var selectedState by remember { mutableStateOf("") }
    var datingPreference by remember { mutableStateOf("Everyone") }
    var selectedHobbies by remember { mutableStateOf(setOf<String>()) }
    var isSaving by remember { mutableStateOf(false) }
    var stateDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        val p = userProfile ?: return@LaunchedEffect
        displayName = p.displayName
        gender = p.gender
        bio = p.bio
        city = p.city
        selectedState = p.state
        datingPreference = if (p.datingPreference.isNotEmpty()) p.datingPreference else "Everyone"
        selectedHobbies = p.interests.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
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
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Display Name
            item {
                Text("Full Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = Modifier.fillMaxWidth().testTag("edit_display_name_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard,
                        focusedBorderColor = BondlyPink,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )
            }

            // Gender Selection
            item {
                Text("Gender", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Woman", "Man", "Non-binary").forEach { option ->
                        val isSelected = gender.equals(option, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) BondlyPink else CharcoalCard)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BondlyPink else CharcoalBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { gender = option }
                                .padding(vertical = 12.dp)
                                .testTag("edit_gender_$option"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) TextWhite else TextLightGrey
                            )
                        }
                    }
                }
            }

            // Location
            item {
                Text("City & State", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        placeholder = { Text("City", color = TextMuted) },
                        modifier = Modifier.weight(1f).testTag("edit_city_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalCard,
                            unfocusedContainerColor = CharcoalCard,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedState.ifEmpty { "Select State" },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextLightGrey)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { stateDropdownExpanded = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CharcoalCard,
                                unfocusedContainerColor = CharcoalCard,
                                focusedBorderColor = BondlyPink,
                                unfocusedBorderColor = CharcoalBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            )
                        )
                        DropdownMenu(
                            expanded = stateDropdownExpanded,
                            onDismissRequest = { stateDropdownExpanded = false },
                            modifier = Modifier.background(CharcoalCard)
                        ) {
                            IndianStates.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text(st, color = TextWhite) },
                                    onClick = {
                                        selectedState = st
                                        stateDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // About Me
            item {
                Text("About Me (Bio)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    placeholder = { Text("Share a little about who you are and what you enjoy...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().height(120.dp).testTag("edit_bio_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard,
                        focusedBorderColor = BondlyPink,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    )
                )
            }

            // Dating Preference
            item {
                Text("Looking For", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("Women", "Men", "Everyone").forEach { pref ->
                        val isSelected = datingPreference == pref
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) BondlyPink else CharcoalCard)
                                .border(1.dp, if (isSelected) BondlyPink else CharcoalBorder, RoundedCornerShape(14.dp))
                                .clickable { datingPreference = pref }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pref,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) TextWhite else TextLightGrey
                            )
                        }
                    }
                }
            }

            // Hobbies & Interests
            item {
                Text("Hobbies & Interests", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AvailableHobbies.forEach { hobby ->
                        val isSelected = selectedHobbies.contains(hobby)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BondlyPink.copy(alpha = 0.2f) else CharcoalCard)
                                .border(
                                    1.dp,
                                    if (isSelected) BondlyPink else CharcoalBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedHobbies = if (isSelected) {
                                        selectedHobbies - hobby
                                    } else {
                                        selectedHobbies + hobby
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = hobby,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BondlyPink else TextLightGrey
                            )
                        }
                    }
                }
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(
                    text = "Save Profile",
                    isLoading = isSaving,
                    onClick = {
                        val base = userProfile ?: return@PrimaryButton
                        if (displayName.isBlank()) {
                            Toast.makeText(context, "Display name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@PrimaryButton
                        }
                        if (!com.example.util.NetworkConnectivityManager.isConnected()) {
                            Toast.makeText(context, "No internet connection. Please connect to the internet and try again.", Toast.LENGTH_SHORT).show()
                            return@PrimaryButton
                        }
                        isSaving = true
                        coroutineScope.launch {
                            val updated = base.copy(
                                displayName = displayName.trim(),
                                gender = gender.ifEmpty { base.gender },
                                bio = bio.trim(),
                                city = city.trim(),
                                state = selectedState.trim(),
                                datingPreference = datingPreference,
                                interests = selectedHobbies.toList()
                            )
                            val saveResult = repository.saveProfile(updated)
                            isSaving = false
                            if (saveResult.isSuccess) {
                                Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                                onBack()
                            } else {
                                val msg = com.example.util.BondlyErrorClassifier.getUserFriendlyMessage(saveResult.exceptionOrNull())
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_profile_button")
                )
            }
        }
    }
}
