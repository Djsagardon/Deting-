package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.data.repository.ImageUploadRepository
import com.example.data.repository.ImageUploadResult
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingProfileScreen(
    currentUserId: String,
    repository: BondlyRepository,
    initialProfile: UserProfile? = null,
    onProfileCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploadRepo = remember { ImageUploadRepository(context) }
    val googleUser = remember { Firebase.auth.currentUser }

    // Multi-step index: 0 = Basic Info, 1 = Location & Phone, 2 = 5 Photos, 3 = Hobbies, 4 = Bio & Rules
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = 5

    // Step 1: Basic Info
    var fullName by remember {
        mutableStateOf(
            initialProfile?.displayName?.takeIf { it.isNotBlank() }
                ?: googleUser?.displayName?.takeIf { it.isNotBlank() }
                ?: ""
        )
    }
    var birthDay by remember { mutableStateOf("15") }
    var birthMonth by remember { mutableStateOf("06") }
    var birthYear by remember { mutableStateOf("1998") }
    var gender by remember {
        mutableStateOf(initialProfile?.gender?.takeIf { it.isNotBlank() } ?: "Female")
    }

    // Calculate age from DOB
    val calculatedAge = remember(birthDay, birthMonth, birthYear) {
        val y = birthYear.toIntOrNull() ?: 2000
        val m = (birthMonth.toIntOrNull() ?: 1).coerceIn(1, 12)
        val d = (birthDay.toIntOrNull() ?: 1).coerceIn(1, 31)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        (currentYear - y).coerceAtLeast(0)
    }

    // Step 2: Location & Mobile
    var selectedState by remember {
        mutableStateOf(initialProfile?.state?.takeIf { it.isNotBlank() } ?: "Maharashtra")
    }
    var city by remember {
        mutableStateOf(initialProfile?.city?.takeIf { it.isNotBlank() } ?: "Mumbai")
    }
    var showStateDropdown by remember { mutableStateOf(false) }
    var mobileNumber by remember { mutableStateOf("") }
    var isVerifyingMobile by remember { mutableStateOf(false) }
    var isMobileVerified by remember { mutableStateOf(initialProfile?.isMobileVerified ?: false) }
    var otpCode by remember { mutableStateOf("") }
    var showOtpDialog by remember { mutableStateOf(false) }

    // Step 3: Exactly 5 Photos
    var photoUrls by remember {
        mutableStateOf<List<String>>(
            initialProfile?.profilePhotoUrls?.takeIf { it.isNotEmpty() }
                ?: (if (googleUser?.photoUrl != null) listOf(googleUser.photoUrl.toString()) else emptyList())
        )
    }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var selectedPhotoIndexForPreview by remember { mutableStateOf<Int?>(null) }
    var activeReplacingIndex by remember { mutableStateOf<Int?>(null) }

    // Step 4: Predefined Hobbies (min 3, max 7)
    val availableHobbies = remember {
        listOf(
            "Traveling", "Music & Concerts", "Movies & Cinema", "Reading",
            "Cooking & Baking", "Photography", "Fitness & Gym", "Dancing",
            "Gaming & Esports", "Cricket & Sports", "Nature & Wildlife", "Art & Creativity",
            "Food Exploration", "Trekking & Outdoors", "Yoga & Meditation", "Tech & Startups"
        )
    }
    var selectedHobbies by remember { mutableStateOf(setOf("Traveling", "Music & Concerts", "Food Exploration")) }

    // Step 5: Bio, Dating Preferences & Rules Consent
    var bio by remember { mutableStateOf("") }
    var datingPreference by remember { mutableStateOf("Long-term relationship") }
    val datingPreferencesList = listOf("Long-term relationship", "Marriage & Matrimony", "Dating & Companionship")
    var hasAcceptedRules by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Indian States & UTs
    val indianStates = remember {
        listOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Delhi NCR",
            "Goa", "Gujarat", "Haryana", "Himachal Pradesh", "Jammu & Kashmir", "Jharkhand",
            "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim",
            "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPhoto = true
            uploadProgress = 15
            scope.launch {
                val result = uploadRepo.uploadImage(uri) { p -> uploadProgress = p }
                isUploadingPhoto = false
                val newUrl = when (result) {
                    is ImageUploadResult.Success -> result.imageUrl
                    is ImageUploadResult.Error -> uri.toString()
                }

                val replaceIdx = activeReplacingIndex
                if (replaceIdx != null && replaceIdx in photoUrls.indices) {
                    val updated = photoUrls.toMutableList()
                    updated[replaceIdx] = newUrl
                    photoUrls = updated
                    activeReplacingIndex = null
                } else if (photoUrls.size < 5) {
                    photoUrls = photoUrls + newUrl
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("onboarding_profile_screen")
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Progress Bar & Step Tracker
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Step ${currentStep + 1} of $totalSteps",
                color = BondlyPink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = when (currentStep) {
                    0 -> "Basic Details"
                    1 -> "Location & Phone"
                    2 -> "Five Photos"
                    3 -> "Hobbies & Interests"
                    else -> "Bio & Guidelines"
                },
                color = TextLightGrey,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { (currentStep + 1) / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = BondlyPink,
            trackColor = CharcoalBorder
        )

        Spacer(modifier = Modifier.height(24.dp))

        // STEP 0: BASIC DETAILS
        if (currentStep == 0) {
            Text(
                text = "Let's Get Started",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Enter your real name, birth date, and gender. You must be at least 18.",
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Legal Name", color = TextMuted) },
                placeholder = { Text("e.g. Priya Sharma or Rahul Verma", color = TextMuted) },
                modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
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

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Date of Birth (Calculated Age: $calculatedAge yrs)",
                color = TextLightGrey,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = birthDay,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) birthDay = it },
                    label = { Text("Day", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("DD", color = TextMuted) },
                    modifier = Modifier.weight(1f),
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

                OutlinedTextField(
                    value = birthMonth,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) birthMonth = it },
                    label = { Text("Month", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("MM", color = TextMuted) },
                    modifier = Modifier.weight(1f),
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

                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) birthYear = it },
                    label = { Text("Year", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("YYYY", color = TextMuted) },
                    modifier = Modifier.weight(1.5f),
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
            }

            if (calculatedAge < 18) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ You must be at least 18 years old to join Bondly.",
                    color = Color(0xFFFF4455),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Gender",
                color = TextLightGrey,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Female", "Male").forEach { option ->
                    val isSelected = gender == option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0x33FF2D75) else CharcoalCard)
                            .border(1.5.dp, if (isSelected) BondlyPink else CharcoalBorder, RoundedCornerShape(14.dp))
                            .clickable { gender = option }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (option == "Female") "👩 Female" else "👨 Male",
                            color = if (isSelected) TextWhite else TextLightGrey,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Note: To ensure real dating compatibility, male members discover female profiles and female members discover male profiles.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        // STEP 1: LOCATION & MOBILE VERIFICATION
        if (currentStep == 1) {
            Text(
                text = "Location & Mobile",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bondly is tailored for singles across India. Your mobile number remains 100% private and is never shown to other users.",
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // State Selector
            Text(text = "State / Union Territory", color = TextLightGrey, fontSize = 13.sp)
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

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City / Town", color = TextMuted) },
                placeholder = { Text("e.g. Pune, Bengaluru, Jaipur, Delhi", color = TextMuted) },
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

            Spacer(modifier = Modifier.height(20.dp))

            // Mobile Number
            Text(text = "Indian Mobile Number (Mandatory Verification)", color = TextLightGrey, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "+91 🇮🇳", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = {
                        if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                            mobileNumber = it
                            isMobileVerified = false
                        }
                    },
                    placeholder = { Text("10-digit number", color = TextMuted) },
                    modifier = Modifier.weight(1f),
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isMobileVerified) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (mobileNumber.length == 10 && !isVerifyingMobile) Color(0x33FF2D75) else CharcoalCard)
                        .border(1.dp, if (mobileNumber.length == 10) BondlyPink else CharcoalBorder, RoundedCornerShape(12.dp))
                        .clickable(enabled = mobileNumber.length == 10 && !isVerifyingMobile) {
                            isVerifyingMobile = true
                            scope.launch {
                                delay(1200L) // Instant OTP simulator
                                isVerifyingMobile = false
                                showOtpDialog = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVerifyingMobile) {
                        CircularProgressIndicator(color = BondlyPink, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = "Verify Number via SMS OTP",
                            color = if (mobileNumber.length == 10) BondlyPink else TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2200E676))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+91 $mobileNumber Verified Successfully",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // STEP 2: EXACTLY FIVE PHOTOS
        if (currentStep == 2) {
            Text(
                text = "Exactly 5 Photos Required",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "To guarantee real, high-quality profiles and protect against fake accounts, you must add 5 clear photos of yourself before completing your profile.",
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Uploaded Photos: ${photoUrls.size}/5",
                color = if (photoUrls.size == 5) Color(0xFF00E676) else BondlyPink,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 5 Photo Grid Slots
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Row 1: 3 slots
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until 3) {
                        val photoUrl = photoUrls.getOrNull(i)
                        PhotoSlotCard(
                            index = i,
                            url = photoUrl,
                            isPrimary = (i == 0),
                            modifier = Modifier.weight(1f),
                            onAdd = {
                                if (!isUploadingPhoto) {
                                    activeReplacingIndex = null
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            onPreview = { selectedPhotoIndexForPreview = i },
                            onReplace = {
                                activeReplacingIndex = i
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onDelete = {
                                photoUrls = photoUrls.filterIndexed { idx, _ -> idx != i }
                            }
                        )
                    }
                }

                // Row 2: 2 slots
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 3 until 5) {
                        val photoUrl = photoUrls.getOrNull(i)
                        PhotoSlotCard(
                            index = i,
                            url = photoUrl,
                            isPrimary = false,
                            modifier = Modifier.weight(1f),
                            onAdd = {
                                if (!isUploadingPhoto) {
                                    activeReplacingIndex = null
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                            onPreview = { selectedPhotoIndexForPreview = i },
                            onReplace = {
                                activeReplacingIndex = i
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onDelete = {
                                photoUrls = photoUrls.filterIndexed { idx, _ -> idx != i }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f)) // Empty balance slot for layout symmetry
                }
            }

            if (isUploadingPhoto) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { uploadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = BondlyPink,
                    trackColor = CharcoalBorder
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Uploading image... $uploadProgress%", color = TextMuted, fontSize = 12.sp)
            }

            if (photoUrls.size < 5) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Please upload ${5 - photoUrls.size} more photo${if (5 - photoUrls.size > 1) "s" else ""} to proceed.",
                    color = BondlyPink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // STEP 3: HOBBIES & INTERESTS
        if (currentStep == 3) {
            Text(
                text = "Select Your Hobbies",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose between 3 and 7 interests that represent your lifestyle.",
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Selected (${selectedHobbies.size}/7)",
                color = if (selectedHobbies.size in 3..7) Color(0xFF00E676) else BondlyPink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                availableHobbies.forEach { hobby ->
                    val isSelected = selectedHobbies.contains(hobby)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) BondlyGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(CharcoalCard, CharcoalCard)))
                            .border(1.5.dp, if (isSelected) BondlyPink else CharcoalBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                if (isSelected) {
                                    selectedHobbies = selectedHobbies - hobby
                                } else if (selectedHobbies.size < 7) {
                                    selectedHobbies = selectedHobbies + hobby
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = hobby,
                            color = if (isSelected) TextWhite else TextLightGrey,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // STEP 4: BIO, DATING INTENTION & RULES
        if (currentStep == 4) {
            Text(
                text = "About You & Intentions",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "A sincere bio helps matches get to know the authentic you.",
                color = TextMuted,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 500) bio = it },
                label = { Text("Personal Bio", color = TextMuted) },
                placeholder = { Text("What are your passions, values, and ideal weekend vibes?", color = TextMuted) },
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
                maxLines = 6
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "What are you looking for?", color = TextLightGrey, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                datingPreferencesList.forEach { pref ->
                    val isSelected = datingPreference == pref
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0x33FF2D75) else CharcoalCard)
                            .border(1.5.dp, if (isSelected) BondlyPink else CharcoalBorder, RoundedCornerShape(12.dp))
                            .clickable { datingPreference = pref }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pref,
                                color = if (isSelected) TextWhite else TextLightGrey,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = BondlyPink)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Safety Consent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .clickable { hasAcceptedRules = !hasAcceptedRules }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (hasAcceptedRules) BondlyPink else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "I commit to respectful behavior",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "No harassment, hate speech, or financial requests. Personal phone numbers cannot be shared in chat for safety.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.material3.Checkbox(
                    checked = hasAcceptedRules,
                    onCheckedChange = { hasAcceptedRules = it },
                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                        checkedColor = BondlyPink,
                        checkmarkColor = TextWhite
                    )
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = errorMessage!!,
                color = Color(0xFFFF4455),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Navigation Footer Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(26.dp))
                        .clickable { currentStep-- },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextLightGrey, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Back", color = TextLightGrey, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            val canAdvance = when (currentStep) {
                0 -> fullName.trim().length >= 2 && calculatedAge >= 18
                1 -> city.trim().isNotBlank() && isMobileVerified
                2 -> photoUrls.size == 5 && !isUploadingPhoto
                3 -> selectedHobbies.size in 3..7
                4 -> bio.trim().length >= 10 && hasAcceptedRules
                else -> false
            }

            Box(
                modifier = Modifier
                    .weight(if (currentStep > 0) 1.5f else 1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (canAdvance && !isSaving) BondlyGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(CharcoalBorder, CharcoalBorder)))
                    .clickable(enabled = canAdvance && !isSaving) {
                        errorMessage = null
                        if (currentStep < totalSteps - 1) {
                            currentStep++
                        } else {
                            // Final Save Profile
                            isSaving = true
                            val dobString = "$birthYear-$birthMonth-$birthDay"
                            val profile = UserProfile(
                                userId = currentUserId,
                                displayName = fullName.trim(),
                                dob = dobString,
                                age = calculatedAge,
                                gender = gender,
                                city = city.trim(),
                                state = selectedState,
                                bio = bio.trim(),
                                interests = selectedHobbies.toList(),
                                profilePhotoUrls = photoUrls,
                                totalPhotoCount = photoUrls.size,
                                datingPreference = datingPreference,
                                rulesAccepted = hasAcceptedRules,
                                profileCompleted = true,
                                isMobileVerified = true,
                                accountStatus = "active"
                            )

                            scope.launch {
                                val saveResult = repository.saveUserProfile(profile, rawMobileNumber = mobileNumber)
                                isSaving = false
                                if (saveResult.isSuccess) {
                                    onProfileCreated()
                                } else {
                                    errorMessage = saveResult.exceptionOrNull()?.localizedMessage ?: "Failed to save profile."
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (currentStep == totalSteps - 1) "Save Profile & Discover" else "Next Step",
                            color = if (canAdvance) TextWhite else TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (currentStep == totalSteps - 1) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (canAdvance) TextWhite else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // OTP Verification Dialog
    if (showOtpDialog) {
        Dialog(onDismissRequest = { showOtpDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Verify Mobile Number", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We sent a 4-digit code to +91 $mobileNumber. (Enter 1234 to verify)",
                        color = TextLightGrey,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 4) otpCode = it },
                        placeholder = { Text("4-digit OTP", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(20.dp))
                    PrimaryButton(
                        text = "Confirm & Verify",
                        enabled = otpCode.length >= 4,
                        onClick = {
                            isMobileVerified = true
                            showOtpDialog = false
                        }
                    )
                }
            }
        }
    }

    // Photo Preview Modal
    if (selectedPhotoIndexForPreview != null) {
        val previewUrl = photoUrls.getOrNull(selectedPhotoIndexForPreview!!)
        Dialog(onDismissRequest = { selectedPhotoIndexForPreview = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (previewUrl != null) {
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = "Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    IconButton(
                        onClick = { selectedPhotoIndexForPreview = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSlotCard(
    index: Int,
    url: String?,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onPreview: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(1.5.dp, if (url != null) BondlyPink else CharcoalBorder, RoundedCornerShape(16.dp))
            .clickable {
                if (url == null) onAdd() else onPreview()
            },
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Photo ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Primary badge on slot 0
            if (isPrimary) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BondlyPink)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "Main", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Quick actions: delete & replace
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable { onReplace() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Replace", tint = TextWhite, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xAA000000))
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextWhite, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, contentDescription = "Add Photo", tint = BondlyPink, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Photo ${index + 1}", color = TextMuted, fontSize = 11.sp)
                if (isPrimary) {
                    Text(text = "(Main Photo)", color = BondlyPink, fontSize = 9.sp)
                }
            }
        }
    }
}
