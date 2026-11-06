package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
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
import com.example.data.upload.PhotoSlotState
import com.example.data.upload.ProfilePhotoUploadManager
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

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

    // Upload manager preserved across configuration / step navigation
    val uploadManager = remember {
        ProfilePhotoUploadManager().apply {
            if (!initialProfile?.profilePhotoUrls.isNullOrEmpty()) {
                initializeFromExisting(initialProfile.profilePhotoUrls)
            }
        }
    }

    val photoSlots by uploadManager.slots.collectAsState()
    val isSequentialUploading by uploadManager.isSequentialUploading.collectAsState()

    var currentStep by remember { mutableIntStateOf(1) } // 1: Personal, 2: Mobile, 3: Photos, 4: Bio/Hobbies, 5: Rules

    // Step 1: Personal
    var fullName by remember { mutableStateOf(initialProfile?.displayName ?: "") }
    var birthDay by remember { mutableStateOf("15") }
    var birthMonth by remember { mutableStateOf("08") }
    var birthYear by remember { mutableStateOf("2000") }
    // REQUIREMENT: No gender option selected by default! Must be manually chosen.
    var gender by remember { mutableStateOf(initialProfile?.gender ?: "") }
    var selectedState by remember { mutableStateOf(initialProfile?.state ?: "Maharashtra") }
    var city by remember { mutableStateOf(initialProfile?.city ?: "Mumbai") }
    var stateDropdownExpanded by remember { mutableStateOf(false) }

    // Step 2: In-App Mobile Confirmation State (No hardcoded OTPs!)
    var mobileNumber by remember { mutableStateOf(initialProfile?.maskedMobileNumber ?: "") }
    var isMobileConfirmed by remember {
        mutableStateOf(
            initialProfile?.mobileConfirmationStatus == "in_app_confirmed" || initialProfile?.isMobileVerified == true
        )
    }
    var enteredOtpCode by remember { mutableStateOf("") }
    var generatedOtpCode by remember { mutableStateOf<String?>(null) }
    var otpExpiryTimestamp by remember { mutableLongStateOf(0L) }
    var otpCooldownSeconds by remember { mutableIntStateOf(0) }
    var failedOtpAttempts by remember { mutableIntStateOf(0) }
    var showInAppOtpBanner by remember { mutableStateOf(false) }
    var otpStatusMessage by remember { mutableStateOf<String?>(null) }
    var remainingSecondsToExpiry by remember { mutableIntStateOf(0) }

    // Live countdown timer for OTP expiry and cooldown
    LaunchedEffect(otpExpiryTimestamp, showInAppOtpBanner) {
        while (otpExpiryTimestamp > 0L) {
            val now = System.currentTimeMillis()
            val remaining = ((otpExpiryTimestamp - now) / 1000L).toInt()
            if (remaining <= 0) {
                remainingSecondsToExpiry = 0
                generatedOtpCode = null
                otpExpiryTimestamp = 0L
                showInAppOtpBanner = false
                otpStatusMessage = "Code expired. Please request a new code."
                break
            } else {
                remainingSecondsToExpiry = remaining
            }
            if (otpCooldownSeconds > 0) {
                otpCooldownSeconds--
            }
            delay(1000L)
        }
    }

    // Step 3: Photos Launchers
    var targetSingleSlotIndex by remember { mutableIntStateOf(-1) }

    // Multi-photo picker launcher: Selects up to 5 photos in ONE operation!
    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uploadManager.onMultiplePhotosSelected(context, uris, currentUserId)
        }
    }

    // Single-photo picker launcher: Target a specific slot
    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && targetSingleSlotIndex in 0..4) {
            uploadManager.onSinglePhotoSelected(context, targetSingleSlotIndex, uri, currentUserId)
        }
    }

    // Step 4: Bio & Hobbies
    var bio by remember { mutableStateOf(initialProfile?.bio ?: "") }
    var selectedHobbies by remember {
        mutableStateOf(
            initialProfile?.interests?.toSet() ?: setOf("Chai & Conversations", "Bollywood", "Music & Concerts")
        )
    }
    var datingPreference by remember { mutableStateOf(initialProfile?.datingPreference ?: "Everyone") }

    // Step 5: Rules & Saving
    var hasAcceptedRules by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(CharcoalDark)) {
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
                            // =========================================================================
                            // STEP 1: PERSONAL INFORMATION (WITH MANDATORY GENDER SELECTION)
                            // =========================================================================
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

                                // MANDATORY GENDER SELECTION (None selected by default!)
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Gender", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(Required • Select your gender)", fontSize = 11.sp, color = TextMuted)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf("Woman", "Man", "Non-binary").forEach { option ->
                                            val isSelected = gender == option
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
                                                    .clickable {
                                                        gender = option
                                                        errorMessage = null
                                                    }
                                                    .padding(vertical = 12.dp)
                                                    .testTag("gender_option_$option"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = TextWhite,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
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
                                    if (gender.isEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Please manually select an option above to continue.",
                                            fontSize = 11.sp,
                                            color = WarningOrange
                                        )
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
                            // =========================================================================
                            // STEP 2: IN-APP MOBILE NUMBER CONFIRMATION (NO HARDCODED 1234!)
                            // =========================================================================
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Mobile Number Confirmation", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text(
                                    "Used for account recovery and profile safety on Bondly. This in-app confirmation validates your entered number.",
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = mobileNumber,
                                    onValueChange = {
                                        mobileNumber = it
                                        // Reset confirmation if user modifies their number
                                        if (isMobileConfirmed) {
                                            isMobileConfirmed = false
                                            generatedOtpCode = null
                                            showInAppOtpBanner = false
                                        }
                                    },
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

                                if (isMobileConfirmed) {
                                    // Successfully confirmed status
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = OnlineGreen.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(22.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text("Mobile Number Confirmed", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                                Text("Status: in_app_confirmed • Ready to continue", fontSize = 12.sp, color = OnlineGreen)
                                            }
                                        }
                                    }
                                } else {
                                    // Action to generate in-app code
                                    SecondaryButton(
                                        text = if (generatedOtpCode == null) "Generate In-App Confirmation Code" else "Resend Code (${if (otpCooldownSeconds > 0) "${otpCooldownSeconds}s" else "Ready"})",
                                        enabled = mobileNumber.trim().length >= 10 && otpCooldownSeconds == 0,
                                        onClick = {
                                            // Generate fresh random 6-digit code
                                            val freshCode = Random.nextInt(100000, 999999).toString()
                                            generatedOtpCode = freshCode
                                            otpExpiryTimestamp = System.currentTimeMillis() + (120 * 1000L) // 2 minutes
                                            otpCooldownSeconds = 30
                                            failedOtpAttempts = 0
                                            enteredOtpCode = ""
                                            showInAppOtpBanner = true
                                            otpStatusMessage = "Code generated! See the top notification banner."
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    if (generatedOtpCode != null) {
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Enter 6-Digit In-App Code",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextWhite
                                                    )
                                                    Text(
                                                        text = if (remainingSecondsToExpiry > 0) "Expires in %02d:%02d".format(remainingSecondsToExpiry / 60, remainingSecondsToExpiry % 60) else "Expired",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (remainingSecondsToExpiry > 20) BondlyPink else ErrorRed
                                                    )
                                                }

                                                OutlinedTextField(
                                                    value = enteredOtpCode,
                                                    onValueChange = {
                                                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                                            enteredOtpCode = it
                                                        }
                                                    },
                                                    placeholder = { Text("Enter 6-digit code", color = TextMuted) },
                                                    modifier = Modifier.fillMaxWidth().testTag("otp_input_field"),
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = CharcoalDark,
                                                        unfocusedContainerColor = CharcoalDark,
                                                        focusedBorderColor = BondlyPink,
                                                        unfocusedBorderColor = CharcoalBorder,
                                                        focusedTextColor = TextWhite,
                                                        unfocusedTextColor = TextWhite
                                                    ),
                                                    singleLine = true
                                                )

                                                PrimaryButton(
                                                    text = "Confirm Code",
                                                    enabled = enteredOtpCode.length == 6,
                                                    onClick = {
                                                        val now = System.currentTimeMillis()
                                                        if (now > otpExpiryTimestamp || generatedOtpCode == null) {
                                                            otpStatusMessage = "Code has expired. Please generate a new code."
                                                            enteredOtpCode = ""
                                                            return@PrimaryButton
                                                        }

                                                        if (enteredOtpCode == generatedOtpCode) {
                                                            isMobileConfirmed = true
                                                            generatedOtpCode = null
                                                            showInAppOtpBanner = false
                                                            otpStatusMessage = null
                                                            Toast.makeText(context, "Mobile number confirmed successfully!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            failedOtpAttempts++
                                                            if (failedOtpAttempts >= 4) {
                                                                generatedOtpCode = null
                                                                showInAppOtpBanner = false
                                                                otpStatusMessage = "Too many incorrect attempts. Code revoked. Please request a new code."
                                                                enteredOtpCode = ""
                                                            } else {
                                                                val remaining = 4 - failedOtpAttempts
                                                                otpStatusMessage = "Incorrect code. $remaining attempt(s) remaining."
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    if (otpStatusMessage != null) {
                                        Text(
                                            text = otpStatusMessage ?: "",
                                            fontSize = 12.sp,
                                            color = if (isMobileConfirmed) OnlineGreen else ErrorRed
                                        )
                                    }
                                }
                            }
                        }

                        3 -> {
                            // =========================================================================
                            // STEP 3: EXACTLY 5 PHOTO SLOTS WITH SEQUENTIAL BACKGROUND UPLOAD
                            // =========================================================================
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("5 Profile Photos (Mandatory)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                        Text("Select all 5 together or one by one. Uploads happen sequentially.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }

                                // Prominent Multi-Photo Selection Button (Selects up to 5 at once!)
                                Button(
                                    onClick = {
                                        multiPhotoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BondlyPink)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextWhite)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select Up to 5 Photos Together", fontWeight = FontWeight.Bold, color = TextWhite)
                                }

                                // Background sequential status banner
                                if (isSequentialUploading) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = CharcoalCard,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = BondlyPink,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Uploading photos sequentially in background... You can proceed to next steps.",
                                                fontSize = 11.sp,
                                                color = TextLightGrey
                                            )
                                        }
                                    }
                                }

                                // EXACTLY 5 PHOTO SLOTS
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(5) { idx ->
                                        val slotState = photoSlots.getOrElse(idx) { PhotoSlotState.Empty }
                                        val isPrimary = idx == 0

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(0.8f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(CharcoalCard)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = when (slotState) {
                                                        is PhotoSlotState.Success -> OnlineGreen
                                                        is PhotoSlotState.Uploading -> BondlyPink
                                                        is PhotoSlotState.Failed -> ErrorRed
                                                        is PhotoSlotState.Selected -> BondlyGold
                                                        is PhotoSlotState.Empty -> CharcoalBorder
                                                    },
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable {
                                                    if (slotState is PhotoSlotState.Failed) {
                                                        uploadManager.retrySlot(context, idx, currentUserId)
                                                    } else {
                                                        targetSingleSlotIndex = idx
                                                        singlePhotoPickerLauncher.launch(
                                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        )
                                                    }
                                                }
                                                .testTag("photo_slot_$idx"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val displayUri = slotState.displayUrl
                                            if (displayUri != null) {
                                                AsyncImage(
                                                    model = displayUri,
                                                    contentDescription = "Photo ${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                // State Overlays
                                                when (slotState) {
                                                    is PhotoSlotState.Uploading -> {
                                                        // Circular rotating buffering/loading animation directly over the image
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(Color.Black.copy(alpha = 0.55f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(36.dp),
                                                                    color = BondlyPink,
                                                                    strokeWidth = 3.dp
                                                                )
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text("Uploading...", fontSize = 9.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                                                            }
                                                        }
                                                    }

                                                    is PhotoSlotState.Selected -> {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(Color.Black.copy(alpha = 0.35f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("Queued", fontSize = 10.sp, color = BondlyGold, fontWeight = FontWeight.Bold)
                                                        }
                                                    }

                                                    is PhotoSlotState.Success -> {
                                                        // Small success indicator checkmark
                                                        Surface(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomEnd)
                                                                .padding(6.dp)
                                                                .size(22.dp),
                                                            shape = CircleShape,
                                                            color = OnlineGreen
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Filled.Check, contentDescription = "Uploaded", tint = TextWhite, modifier = Modifier.size(14.dp))
                                                            }
                                                        }
                                                    }

                                                    is PhotoSlotState.Failed -> {
                                                        // Error overlay with Retry
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(Color.Black.copy(alpha = 0.65f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = ErrorRed, modifier = Modifier.size(28.dp))
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                                Text("Tap to Retry", fontSize = 9.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }

                                                    else -> {}
                                                }

                                                // Primary tag for slot 0
                                                if (isPrimary) {
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
                                                // Empty Slot Placeholder
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
                            // =========================================================================
                            // STEP 4: BIO & HOBBIES
                            // =========================================================================
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
                            // =========================================================================
                            // STEP 5: RULES & FINAL VALIDATION
                            // =========================================================================
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

                                // Upload status summary
                                val uploadedCount = uploadManager.countSuccessful()
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (uploadedCount == 5) OnlineGreen.copy(alpha = 0.15f) else CharcoalCard,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (uploadedCount == 5) OnlineGreen else CharcoalBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (uploadedCount == 5) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = if (uploadedCount == 5) OnlineGreen else BondlyGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (uploadedCount == 5) "All 5 photos successfully uploaded!" else "Photos uploaded: $uploadedCount of 5 (All 5 required)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextWhite
                                        )
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
                                onClick = {
                                    errorMessage = null
                                    currentStep--
                                },
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
                                        if (gender.isBlank()) {
                                            errorMessage = "Please manually select your gender to proceed."
                                            return@PrimaryButton
                                        }
                                        errorMessage = null
                                        currentStep = 2
                                    }

                                    2 -> {
                                        if (mobileNumber.trim().length < 10) {
                                            errorMessage = "Please enter a valid mobile number (at least 10 digits)"
                                            return@PrimaryButton
                                        }
                                        if (!isMobileConfirmed) {
                                            errorMessage = "Please complete the in-app confirmation code verification above."
                                            return@PrimaryButton
                                        }
                                        errorMessage = null
                                        currentStep = 3
                                    }

                                    3 -> {
                                        // Allow continuing if user wants to fill bio while photos upload in background!
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

                                        // Strict Requirement: Exactly 5 successfully uploaded photos are required!
                                        if (isSequentialUploading) {
                                            errorMessage = "Photos are still uploading in the background. Please wait a moment..."
                                            return@PrimaryButton
                                        }

                                        if (!uploadManager.allUploaded()) {
                                            val currentUploaded = uploadManager.countSuccessful()
                                            errorMessage = "All 5 photo slots must be successfully uploaded. (Currently $currentUploaded / 5 completed). Go back to Step 3 to review."
                                            return@PrimaryButton
                                        }

                                        val finalPhotoUrls = uploadManager.getSuccessfulUrls()
                                        if (finalPhotoUrls.size < 5) {
                                            errorMessage = "Could not find all 5 uploaded photo URLs. Please check Step 3."
                                            return@PrimaryButton
                                        }

                                        if (!com.example.util.NetworkConnectivityManager.isConnected()) {
                                            errorMessage = "No internet connection. Please connect to the internet and try again."
                                            return@PrimaryButton
                                        }

                                        isSaving = true
                                        coroutineScope.launch {
                                            try {
                                                val uniquePublicId = repository.allocateUniquePublicId(currentUserId)
                                                val ageInt = (2026 - (birthYear.toIntOrNull() ?: 2000)).coerceAtLeast(18)

                                                val profileToSave = (initialProfile ?: UserProfile()).copy(
                                                    userId = currentUserId,
                                                    publicUserId = uniquePublicId,
                                                    displayName = fullName.trim(),
                                                    age = ageInt,
                                                    gender = gender.trim(),
                                                    state = selectedState.trim(),
                                                    city = city.trim(),
                                                    maskedMobileNumber = mobileNumber.trim(),
                                                    isMobileVerified = false,
                                                    mobileConfirmationStatus = "in_app_confirmed",
                                                    profilePhotoUrls = finalPhotoUrls,
                                                    totalPhotoCount = finalPhotoUrls.size,
                                                    bio = bio.trim(),
                                                    interests = selectedHobbies.toList(),
                                                    datingPreference = datingPreference,
                                                    rulesAccepted = true,
                                                    profileCompleted = true,
                                                    userLevel = 1
                                                )
                                                val saveResult = repository.saveProfile(profileToSave)
                                                isSaving = false
                                                if (saveResult.isSuccess) {
                                                    onProfileCreated()
                                                } else {
                                                    val err = saveResult.exceptionOrNull()
                                                    errorMessage = com.example.util.BondlyErrorClassifier.getUserFriendlyMessage(err)
                                                }
                                            } catch (e: Exception) {
                                                isSaving = false
                                                errorMessage = com.example.util.BondlyErrorClassifier.getUserFriendlyMessage(e)
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

        // =========================================================================
        // TOP SLIDING IN-APP NOTIFICATION BANNER FOR 6-DIGIT CODE
        // =========================================================================
        AnimatedVisibility(
            visible = showInAppOtpBanner && generatedOtpCode != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = CharcoalCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BondlyPink),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = BondlyPink.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Key, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Bondly In-App Code", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                Text("Demo confirmation (Enter below)", fontSize = 10.sp, color = TextMuted)
                            }
                        }

                        IconButton(
                            onClick = { showInAppOtpBanner = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextLightGrey, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6-Digit Code Display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalDark)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedCode = generatedOtpCode?.chunked(1)?.joinToString("  ") ?: ""
                        Text(
                            text = formattedCode,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BondlyGold,
                            letterSpacing = 4.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expires in %02d:%02d".format(remainingSecondsToExpiry / 60, remainingSecondsToExpiry % 60),
                            fontSize = 11.sp,
                            color = if (remainingSecondsToExpiry > 20) BondlyPink else ErrorRed,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap X to hide banner",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
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
