package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.data.repository.ImageUploadRepository
import com.example.data.repository.ImageUploadResult
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

@Composable
fun PhotoManagementScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uploadRepo = remember { ImageUploadRepository(context) }

    var photos by remember(userProfile) {
        mutableStateOf(userProfile?.profilePhotoUrls ?: emptyList())
    }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var activeReplacingIndex by remember { mutableStateOf<Int?>(null) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploading = true
            uploadProgress = 10
            scope.launch {
                val result = uploadRepo.uploadImage(uri) { p -> uploadProgress = p }
                isUploading = false
                val newUrl = when (result) {
                    is ImageUploadResult.Success -> result.imageUrl
                    is ImageUploadResult.Error -> uri.toString()
                }

                val replaceIdx = activeReplacingIndex
                if (replaceIdx != null && replaceIdx in photos.indices) {
                    val updated = photos.toMutableList()
                    updated[replaceIdx] = newUrl
                    photos = updated
                    activeReplacingIndex = null
                } else if (photos.size < 5) {
                    photos = photos + newUrl
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("photo_management_screen")
    ) {
        // Top bar
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
                text = "Photo Management",
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Your 5 Profile Photos (${photos.size}/5)",
            color = if (photos.size == 5) Color(0xFF00E676) else BondlyPink,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Slot 1 is your Main Profile Photo shown in Discover. Tap any photo to preview, replace, or reorder.",
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isUploading) {
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = BondlyPink
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Photo Slots List
        for (i in 0 until 5) {
            val photoUrl = photos.getOrNull(i)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (photoUrl != null) BondlyPink else CharcoalBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp, 90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalDark)
                            .clickable {
                                if (photoUrl != null) previewUrl = photoUrl
                                else {
                                    activeReplacingIndex = null
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Slot ${i + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = BondlyPink)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Slot ${i + 1}",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (i == 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BondlyPink)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "Main Photo", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (photoUrl != null) "Uploaded & Verified" else "Empty Slot",
                            color = if (photoUrl != null) Color(0xFF00E676) else TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    // Actions: Move up/down, replace, remove
                    if (photoUrl != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Move Up
                            if (i > 0) {
                                IconButton(
                                    onClick = {
                                        val list = photos.toMutableList()
                                        val temp = list[i]
                                        list[i] = list[i - 1]
                                        list[i - 1] = temp
                                        photos = list
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = TextLightGrey)
                                }
                            }

                            // Move Down
                            if (i < photos.size - 1) {
                                IconButton(
                                    onClick = {
                                        val list = photos.toMutableList()
                                        val temp = list[i]
                                        list[i] = list[i + 1]
                                        list[i + 1] = temp
                                        photos = list
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = TextLightGrey)
                                }
                            }

                            // Replace
                            IconButton(
                                onClick = {
                                    activeReplacingIndex = i
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Replace", tint = BondlyPink)
                            }
                        }
                    } else {
                        IconButton(
                            onClick = {
                                activeReplacingIndex = null
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Upload", tint = BondlyPink)
                        }
                    }
                }
            }
        }

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusMessage!!,
                color = Color(0xFF00E676),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        PrimaryButton(
            text = "Save Photo Changes",
            isLoading = isSaving,
            enabled = photos.size == 5 && !isSaving,
            onClick = {
                if (userProfile != null) {
                    isSaving = true
                    scope.launch {
                        val result = repository.saveUserProfile(
                            userProfile.copy(
                                profilePhotoUrls = photos,
                                totalPhotoCount = photos.size
                            )
                        )
                        isSaving = false
                        if (result.isSuccess) {
                            statusMessage = "Photos successfully updated!"
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (previewUrl != null) {
        Dialog(onDismissRequest = { previewUrl = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(440.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = "Full Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { previewUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(36.dp).clip(CircleShape).background(Color(0x99000000))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                    }
                }
            }
        }
    }
}
