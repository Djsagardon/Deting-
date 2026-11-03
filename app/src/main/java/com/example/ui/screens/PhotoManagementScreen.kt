package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
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
    val coroutineScope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(profile.profilePhotoUrls) }
    var selectedSlotIndex by remember { mutableIntStateOf(-1) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val list = photos.toMutableList()
            if (selectedSlotIndex in 0 until list.size) {
                list[selectedSlotIndex] = uri.toString()
            } else if (list.size < 5) {
                list.add(uri.toString())
            }
            photos = list
            coroutineScope.launch {
                repository.saveProfile(profile.copy(profilePhotoUrls = list, totalPhotoCount = list.size))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Photos (5 slots)", color = TextWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalDark)
            )
        },
        containerColor = CharcoalDark
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
        ) {
            Text(
                text = "Bondly requires 5 photos for high connection rates and safety. Your first photo is your primary profile cover.",
                color = TextLightGrey,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(5) { index ->
                    val photoUrl = photos.getOrNull(index)
                    val isPrimary = index == 0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CharcoalCard)
                            .border(
                                1.5.dp,
                                if (isPrimary) BondlyPink else CharcoalBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selectedSlotIndex = index
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("photo_slot_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Photo $index",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Primary badge
                            if (isPrimary) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BondlyPink,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Primary",
                                        color = TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    val list = photos.toMutableList()
                                    if (index < list.size) {
                                        list.removeAt(index)
                                        photos = list
                                        coroutineScope.launch {
                                            repository.saveProfile(profile.copy(profilePhotoUrls = list, totalPhotoCount = list.size))
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Delete", tint = TextWhite, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            // Empty slot placeholder
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = CharcoalCardElevated,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(22.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Slot ${index + 1}", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
