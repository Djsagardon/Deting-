package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.data.repository.ImageUploadRepository
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingProfileScreen(
    currentUserId: String,
    repository: BondlyRepository,
    initialProfile: UserProfile? = null,
    onProfileCreated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageRepo = remember { ImageUploadRepository() }

    var currentStep by remember { mutableIntStateOf(1) } // 1: Personal, 2: Mobile, 3: Photos, 4: Bio/Hobbies, 5: Rules

    // Step 1: Personal
    var fullName by remember { mutableStateOf(initialProfile?.displayName ?: "") }
    var birthDay by remember { mutableStateOf("15") }
    var birthMonth by remember { mutableStateOf("08") }
    var birthYear by remember { mutableStateOf("2000") }
    var gender by remember { mutableStateOf(initialProfile?.gender ?: "Woman") }
    var selectedState by remember { mutableStateOf(initialProfile?.state ?: "Maharashtra") }
    var city by remember { mutableStateOf(initialProfile?.city ?: "Mumbai") }
    var stateDropdownExpanded by remember { mutableStateOf(false) }

    // Step 2: Mobile
    var mobileNumber by remember { mutableStateOf(initialProfile?.maskedMobileNumber ?: "") }
    var isMobileVerified by remember { mutableStateOf(initialProfile?.isMobileVerified ?: false) }
    var isVerifyingMobile by remember { mutableStateOf(false) }

    // Step 3: Photos (exactly 5 slots)
    var photoUrls by remember {
        mutableStateOf(
            if (!initialProfile?.profilePhotoUrls.isNullOrEmpty()) {
                val list = initialProfile!!.profilePhotoUrls.take(5).toMutableList()
                while (list.size < 5) list.add("")
                list
            } else {
                mutableListOf(
                    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=600&q=80",
                    "", "", "", ""
                )
            }
        )
    }
    var activePhotoSlot by remember { mutableIntStateOf(-1) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activePhotoSlot in 0..4) {
            isUploadingPhoto = true
            coroutineScope.launch {
                val uploadResult = imageRepo.uploadProfilePhoto(uri, currentUserId, activePhotoSlot)
                uploadResult.onSuccess { url ->
                    val newList = photoUrls.toMutableList()
                    newList[activePhotoSlot] = url
                    photoUrls = newList
                }.onFailure {
                    // Fallback to local URI
                    val newList = photoUrls.toMutableList()
                    newList[activePhotoSlot] = uri.toString()
                    photoUrls = newList
                }
                isUploadingPhoto = false
            }
        }
    }

    // Step 4: Bio & Hobbies
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "") }
    var selectedHobbies by remember { mutableStateOf(initialProfile?.interests?.toSet() ?: setOf("Chai & Conversations", "Bollywood", "Music & Concerts")) }
    var datingPreference by remember { mutableStateOf(initialProfile?.datingPreference ?: "Everyone") }

    // Step 5: Rules
    var hasAcceptedRules by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CharcoalDark)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Complete Profile",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Text(
                        text = "Step $currentStep of 5",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BondlyPink
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentStep / 5f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BondlyPink,
                    trackColor = CharcoalCardElevated
                )
            }
        },
        containerColor = CharcoalDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    1 -> {
                        // Personal Information
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("Tell us about yourself", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text("This information will help matches find you.", fontSize = 13.sp, color = TextMuted)
                            }

                            item {
                                Text("Your Name", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    placeholder = { Text("First and last name", color = TextMuted) },
                                    modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
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

                            item {
                                Text("Birth Date (DD / MM / YYYY)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = birthDay,
                                        onValueChange = { if (it.length <= 2) birthDay = it },
                                        placeholder = { Text("DD", color = TextMuted) },
                                        modifier = Modifier.weight(1f),
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
                                    OutlinedTextField(
                                        value = birthMonth,
                                        onValueChange = { if (it.length <= 2) birthMonth = it },
                                        placeholder = { Text("MM", color = TextMuted) },
                                        modifier = Modifier.weight(1f),
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
                                    OutlinedTextField(
                                        value = birthYear,
                                        onValueChange = { if (it.length <= 4) birthYear = it },
                                        placeholder = { Text("YYYY", color = TextMuted) },
                                        modifier = Modifier.weight(1.5f),
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
                            }

                            item {
                                Text("Gender", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf("Woman", "Man", "Non-binary").forEach { g ->
                                        val isSel = gender == g
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isSel) BondlyPink else CharcoalCard)
                                                .border(1.dp, if (isSel) BondlyPink else CharcoalBorder, RoundedCornerShape(14.dp))
                                                .clickable { gender = g }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(g, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSel) TextWhite else TextLightGrey)
                                        }
                                    }
                                }
                            }

                            item {
                                Text("City & State", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = city,
                                        onValueChange = { city = it },
                                        placeholder = { Text("City", color = TextMuted) },
                                        modifier = Modifier.weight(1f),
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
                                            value = selectedState,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextLightGrey) },
                                            modifier = Modifier.fillMaxWidth().clickable { stateDropdownExpanded = true },
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
                        }
                    }

                    2 -> {
                        // Mobile Verification
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Mobile Number Verification", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Text("Used to prevent bots and maintain genuine safety on Bondly. Never displayed publicly.", fontSize = 13.sp, color = TextMuted)

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = mobileNumber,
                                onValueChange = { mobileNumber = it },
                                placeholder = { Text("+91 XXXXX XXXXX", color = TextMuted) },
                                modifier = Modifier.fillMaxWidth().testTag("onboarding_mobile_input"),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CharcoalCard,
                                    unfocusedContainerColor = CharcoalCard,
                                    focusedBorderColor = BondlyPink,
                                    unfocusedBorderColor = CharcoalBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite
                                ),
                                leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = BondlyPink) }
                            )

                            if (isMobileVerified) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Phone verified successfully!", fontSize = 13.sp, color = OnlineGreen)
                                }
                            } else {
                                SecondaryButton(
                                    text = if (isVerifyingMobile) "Verifying..." else "Verify via SMS OTP",
                                    enabled = mobileNumber.length >= 10 && !isVerifyingMobile,
                                    onClick = {
                                        isVerifyingMobile = true
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(1200)
                                            isMobileVerified = true
                                            isVerifyingMobile = false
                                            Toast.makeText(context, "Mobile number verified!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    3 -> {
                        // 5 Photos Slots
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Add 5 Verified Photos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Text("Mandatory 5 photos build higher Trust Levels and prevent catfish profiles.", fontSize = 13.sp, color = TextMuted)

                            if (isUploadingPhoto) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = BondlyPink, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading photo...", fontSize = 12.sp, color = BondlyPink)
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(5) { idx ->
                                    val url = photoUrls.getOrElse(idx) { "" }
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(0.8f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CharcoalCard)
                                            .border(
                                                1.5.dp,
                                                if (url.isNotEmpty()) BondlyPink else CharcoalBorder,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                activePhotoSlot = idx
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (url.isNotEmpty()) {
                                            AsyncImage(
                                                model = url,
                                                contentDescription = "Photo ${idx + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (idx == 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(6.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(BondlyPink)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("PRIMARY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                                }
                                            }
                                        } else {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Filled.AddAPhoto, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Slot ${idx + 1}", fontSize = 10.sp, color = TextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // Bio, Hobbies & Preference
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            item {
                                Text("About & Lifestyle", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text("Express your personality and who you want to meet.", fontSize = 13.sp, color = TextMuted)
                            }

                            item {
                                Text("About You (Bio)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = bio,
                                    onValueChange = { bio = it },
                                    placeholder = { Text("Write a fun summary about yourself...", color = TextMuted) },
                                    modifier = Modifier.fillMaxWidth().height(100.dp).testTag("onboarding_bio_input"),
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

                            item {
                                Text("Select Hobbies & Passions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AvailableHobbies.forEach { hobby ->
                                        val isSel = selectedHobbies.contains(hobby)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) BondlyPink.copy(alpha = 0.2f) else CharcoalCard)
                                                .border(1.dp, if (isSel) BondlyPink else CharcoalBorder, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedHobbies = if (isSel) selectedHobbies - hobby else selectedHobbies + hobby
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(hobby, fontSize = 12.sp, color = if (isSel) BondlyPink else TextLightGrey)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    5 -> {
                        // Community Rules & Safety Acceptance
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Bondly Community Code", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Text("To create a secure and respectful dating environment, all members agree to the following standards:", fontSize = 13.sp, color = TextMuted)

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RuleItem("1. Zero Tolerance for Harassment", "Inappropriate messages, unsolicited content, and hate speech lead to permanent bans.")
                                    RuleItem("2. Keep Communications on Bondly", "Do not share phone numbers, social handles, or UPI details in public bios or initial chats.")
                                    RuleItem("3. Mutual Consent & Respect", "Calling features unlock as you build genuine activity. Respect your partner's boundaries at all times.")
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hasAcceptedRules = !hasAcceptedRules }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = hasAcceptedRules,
                                    onCheckedChange = { hasAcceptedRules = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BondlyPink,
                                        uncheckedColor = CharcoalBorder,
                                        checkmarkColor = TextWhite
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "I agree to follow the Bondly Community Code and Safety Rules.",
                                    fontSize = 13.sp,
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Buttons
            Column {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        fontSize = 12.sp,
                        color = ErrorRed,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStep > 1) {
                        SecondaryButton(
                            text = "Back",
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PrimaryButton(
                        text = if (currentStep == 5) "Finish & Start Bonding" else "Next",
                        isLoading = isSaving,
                        onClick = {
                            when (currentStep) {
                                1 -> {
                                    if (fullName.isBlank()) {
                                        errorMessage = "Please enter your full name"
                                        return@PrimaryButton
                                    }
                                    errorMessage = null
                                    currentStep = 2
                                }
                                2 -> {
                                    if (mobileNumber.length < 10) {
                                        errorMessage = "Please enter a valid mobile number"
                                        return@PrimaryButton
                                    }
                                    isMobileVerified = true
                                    errorMessage = null
                                    currentStep = 3
                                }
                                3 -> {
                                    val validPhotos = photoUrls.filter { it.isNotEmpty() }
                                    if (validPhotos.isEmpty()) {
                                        errorMessage = "Please upload at least 1 verified profile photo"
                                        return@PrimaryButton
                                    }
                                    errorMessage = null
                                    currentStep = 4
                                }
                                4 -> {
                                    errorMessage = null
                                    currentStep = 5
                                }
                                5 -> {
                                    if (!hasAcceptedRules) {
                                        errorMessage = "You must accept the Community Rules to continue."
                                        return@PrimaryButton
                                    }
                                    isSaving = true
                                    coroutineScope.launch {
                                        try {
                                            val validPhotos = photoUrls.filter { it.isNotEmpty() }.toMutableList()
                                            while (validPhotos.size < 5) {
                                                validPhotos.add(validPhotos.first())
                                            }
                                            val uniquePublicId = repository.allocateUniquePublicId(currentUserId)
                                            val ageInt = (2026 - (birthYear.toIntOrNull() ?: 2000)).coerceAtLeast(18)

                                            val profileToSave = (initialProfile ?: UserProfile()).copy(
                                                userId = currentUserId,
                                                publicUserId = uniquePublicId,
                                                displayName = fullName.trim(),
                                                age = ageInt,
                                                gender = gender,
                                                state = selectedState,
                                                city = city.trim(),
                                                maskedMobileNumber = mobileNumber.trim(),
                                                isMobileVerified = isMobileVerified,
                                                profilePhotoUrls = validPhotos,
                                                totalPhotoCount = validPhotos.size,
                                                bio = bio.trim(),
                                                interests = selectedHobbies.toList(),
                                                datingPreference = datingPreference,
                                                rulesAccepted = true,
                                                profileCompleted = true,
                                                userLevel = 1
                                            )
                                            repository.saveProfile(profileToSave)
                                            isSaving = false
                                            onProfileCreated()
                                        } catch (e: Exception) {
                                            isSaving = false
                                            errorMessage = e.localizedMessage ?: "Failed to save profile."
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("onboarding_next_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleItem(title: String, desc: String) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        Spacer(modifier = Modifier.height(2.dp))
        Text(desc, fontSize = 12.sp, color = TextLightGrey, lineHeight = 16.sp)
    }
}
