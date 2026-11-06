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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.data.repository.ImageUploadConfig
import com.example.data.repository.ImageUploadRepository
import com.example.data.repository.ServerHealthResult
import com.example.data.upload.PhotoSlotState
import com.example.data.upload.ProfilePhotoUploadManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoManagementScreen(
    currentUserId: String = "",
    profile: UserProfile,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageRepo = remember { ImageUploadRepository(context) }

    // Upload manager manages the 5 slots and sequential uploads
    val uploadManager = remember {
        ProfilePhotoUploadManager(imageRepo).apply {
            initializeFromExisting(profile.profilePhotoUrls)
        }
    }

    val photoSlots by uploadManager.slots.collectAsState()
    val isSequentialUploading by uploadManager.isSequentialUploading.collectAsState()
    val activeSlotIndex by uploadManager.activeSlotIndex.collectAsState()

    var targetSingleSlotIndex by remember { mutableIntStateOf(-1) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var currentWorkerUrl by remember { mutableStateOf(ImageUploadConfig.getWorkerUrl(context)) }

    // Multi-photo picker launcher: Selects up to 5 photos in ONE operation
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
        if (uri != null && targetSingleSlotIndex in 0 until ProfilePhotoUploadManager.MAX_SLOTS) {
            uploadManager.onSinglePhotoSelected(context, targetSingleSlotIndex, uri, currentUserId)
        }
    }

    // Automatically sync successfully uploaded remote URLs to Firestore
    LaunchedEffect(photoSlots) {
        val successfulUrls = uploadManager.getSuccessfulUrls().filter {
            it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://"))
        }

        // Only save if the URLs have genuinely changed
        if (successfulUrls != profile.profilePhotoUrls) {
            val allFiveCompleted = successfulUrls.size == ProfilePhotoUploadManager.MAX_SLOTS
            val isNowCompleted = allFiveCompleted &&
                    profile.displayName.isNotBlank() &&
                    profile.gender.isNotBlank() &&
                    profile.age >= 18 &&
                    profile.rulesAccepted

            val updatedProfile = profile.copy(
                profilePhotoUrls = successfulUrls,
                totalPhotoCount = successfulUrls.size,
                profileCompleted = isNowCompleted
            )
            repository.saveProfile(updatedProfile)
        }
    }

    // Count successful and failed slots
    val successCount = photoSlots.count { it is PhotoSlotState.Success }
    val failedSlots = photoSlots.mapIndexedNotNull { idx, state ->
        if (state is PhotoSlotState.Failed) idx to state else null
    }

    val isPlaceholderServer = ImageUploadConfig.isPlaceholderUrl(currentWorkerUrl)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Manage Photos ($successCount/5)",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (successCount == 5) "All 5 photos uploaded" else "5 photos required for connection",
                            color = if (successCount == 5) OnlineGreen else TextMuted,
                            fontSize = 11.sp
                        )
                    }
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
                actions = {
                    // Upload Server Diagnostics & Settings button
                    IconButton(
                        onClick = {
                            currentWorkerUrl = ImageUploadConfig.getWorkerUrl(context)
                            showServerSettingsDialog = true
                        },
                        modifier = Modifier.testTag("server_settings_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (isPlaceholderServer || failedSlots.isNotEmpty()) {
                                    Badge(containerColor = ErrorRed)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Upload Server Settings",
                                tint = if (isPlaceholderServer) WarningOrange else TextWhite
                            )
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Server warning / unconfigured alert banner
            if (isPlaceholderServer) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningOrange.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { showServerSettingsDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upload Server Unconfigured",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Using placeholder URL. Tap to set your Cloudflare Worker URL.",
                                color = TextLightGrey,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { showServerSettingsDialog = true }) {
                            Text("Configure", color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (failedSlots.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ErrorRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${failedSlots.size} photo(s) failed to upload",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            val firstError = failedSlots.firstOrNull()?.second?.errorMessage ?: "Connection error"
                            Text(
                                text = firstError,
                                color = TextLightGrey,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = {
                                failedSlots.forEach { (idx, _) ->
                                    uploadManager.retrySlot(context, idx, currentUserId)
                                }
                            }
                        ) {
                            Text("Retry All", color = BondlyPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top action button: Select up to 5 photos together in 1 operation
            Button(
                onClick = {
                    multiPhotoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("select_all_photos_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                enabled = !isSequentialUploading
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = TextWhite,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSequentialUploading) "Uploading Photos Sequentially..." else "Select Up to 5 Photos Together",
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation / guidance text
            Text(
                text = "Bondly requires exactly 5 photos. Your 1st photo is your primary profile cover. Uploads proceed securely one at a time via encrypted storage.",
                color = TextLightGrey,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // EXACTLY 5 PHOTO SLOTS in a clean, responsive 2-column or 3-column layout
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(ProfilePhotoUploadManager.MAX_SLOTS) { slotIndex ->
                    val slotState = photoSlots.getOrElse(slotIndex) { PhotoSlotState.Empty }
                    val isPrimary = slotIndex == 0
                    val isCurrentlyUploadingThisSlot = isSequentialUploading && activeSlotIndex == slotIndex

                    PhotoSlotCard(
                        slotIndex = slotIndex,
                        isPrimary = isPrimary,
                        slotState = slotState,
                        isUploadingThis = isCurrentlyUploadingThisSlot,
                        onSlotClick = {
                            when (slotState) {
                                is PhotoSlotState.Failed -> {
                                    uploadManager.retrySlot(context, slotIndex, currentUserId)
                                }
                                is PhotoSlotState.Uploading -> {
                                    // Busy uploading, ignore clicks
                                }
                                else -> {
                                    targetSingleSlotIndex = slotIndex
                                    singlePhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            }
                        },
                        onDeleteClick = {
                            uploadManager.clearSlot(slotIndex)
                        },
                        onRetryClick = {
                            uploadManager.retrySlot(context, slotIndex, currentUserId)
                        }
                    )
                }
            }
        }
    }

    // Cloudflare Upload Server Settings & Diagnostics Dialog
    if (showServerSettingsDialog) {
        UploadServerSettingsDialog(
            currentUrl = currentWorkerUrl,
            imageRepo = imageRepo,
            onDismiss = { showServerSettingsDialog = false },
            onSaveUrl = { newUrl ->
                ImageUploadConfig.setWorkerUrl(context, newUrl)
                currentWorkerUrl = ImageUploadConfig.getWorkerUrl(context)
                Toast.makeText(context, "Upload server URL saved!", Toast.LENGTH_SHORT).show()
                showServerSettingsDialog = false
            },
            onResetToDefault = {
                ImageUploadConfig.resetWorkerUrl(context)
                currentWorkerUrl = ImageUploadConfig.getWorkerUrl(context)
                Toast.makeText(context, "Reset to build default URL.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * Individual Photo Slot Card supporting Empty, Selected, Uploading, Success, and Failed states.
 */
@Composable
private fun PhotoSlotCard(
    slotIndex: Int,
    isPrimary: Boolean,
    slotState: PhotoSlotState,
    isUploadingThis: Boolean,
    onSlotClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val borderColor = when {
        slotState is PhotoSlotState.Success -> OnlineGreen
        isUploadingThis || slotState is PhotoSlotState.Uploading -> BondlyPink
        slotState is PhotoSlotState.Failed -> ErrorRed
        slotState is PhotoSlotState.Selected -> BondlyGold
        isPrimary -> BondlyPink.copy(alpha = 0.8f)
        else -> CharcoalBorder
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(enabled = !isUploadingThis) { onSlotClick() }
            .testTag("photo_slot_$slotIndex"),
        contentAlignment = Alignment.Center
    ) {
        val displayUrl = slotState.displayUrl

        if (!displayUrl.isNullOrEmpty() && (slotState is PhotoSlotState.Success || slotState is PhotoSlotState.Selected || slotState is PhotoSlotState.Uploading)) {
            // Display loaded image
            AsyncImage(
                model = displayUrl,
                contentDescription = "Photo Slot ${slotIndex + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Primary Cover badge
            if (isPrimary) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BondlyPink,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Primary Cover",
                        color = TextWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Success checkmark indicator
            if (slotState is PhotoSlotState.Success) {
                Surface(
                    shape = CircleShape,
                    color = OnlineGreen,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Uploaded",
                            tint = CharcoalDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Remove/Delete photo button
            if (!isUploadingThis) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .testTag("delete_photo_slot_$slotIndex")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove photo",
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Uploading indicator overlay
        if (isUploadingThis || slotState is PhotoSlotState.Uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = BondlyPink,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Uploading...",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "via secure cloud",
                        color = TextLightGrey,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // Failed state with retry affordance
        if (slotState is PhotoSlotState.Failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CharcoalCardElevated.copy(alpha = 0.95f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Upload Failed",
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Upload Failed",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = slotState.errorMessage,
                        color = TextLightGrey,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("retry_slot_$slotIndex")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 11.sp, color = TextWhite)
                    }
                }
            }
        }

        // Empty state placeholder
        if (slotState is PhotoSlotState.Empty) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = CharcoalCardElevated,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Add photo to slot ${slotIndex + 1}",
                            tint = if (isPrimary) BondlyPink else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isPrimary) "Slot 1 (Primary)" else "Slot ${slotIndex + 1}",
                    color = if (isPrimary) BondlyPink else TextLightGrey,
                    fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
                Text(
                    text = "Tap to choose",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Upload Server Settings & Diagnostics Dialog
 * Allows users to inspect connectivity, test /health, and update the Worker URL without recompiling.
 */
@Composable
private fun UploadServerSettingsDialog(
    currentUrl: String,
    imageRepo: ImageUploadRepository,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onResetToDefault: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf(currentUrl) }
    var isTestingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<ServerHealthResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CharcoalCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload Server Settings",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Bondly uses a secure server proxy to protect user privacy and encrypt all image transfers.",
                    color = TextLightGrey,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Cloudflare Worker Endpoint URL:",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    placeholder = { Text("https://your-worker.workers.dev/upload", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CharcoalDark,
                        unfocusedContainerColor = CharcoalDark,
                        focusedBorderColor = BondlyPink,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_url_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Test Connection button
                Button(
                    onClick = {
                        isTestingHealth = true
                        healthResult = null
                        // Temporarily apply input URL to test it
                        ImageUploadConfig.setWorkerUrl(context, inputUrl)
                        coroutineScope.launch {
                            val result = imageRepo.testHealth(context)
                            healthResult = result
                            isTestingHealth = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_connection_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTestingHealth && inputUrl.isNotBlank()
                ) {
                    if (isTestingHealth) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BondlyPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing /health...", color = TextWhite, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection (/health)", color = TextWhite, fontSize = 12.sp)
                    }
                }

                // Health test results display
                if (healthResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val res = healthResult!!
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (res.isHealthy) OnlineGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (res.isHealthy) OnlineGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isHealthy) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (res.isHealthy) OnlineGreen else ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (res.isHealthy) "Connection Successful" else "Connection Test Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isHealthy) OnlineGreen else ErrorRed,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.message,
                                color = TextWhite,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", color = TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onSaveUrl(inputUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("save_worker_url_button")
                    ) {
                        Text("Save & Apply", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
