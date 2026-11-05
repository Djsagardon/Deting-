package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPhotoManagement: () -> Unit,
    onNavigateToCallHistory: () -> Unit,
    onNavigateToPrivacySafety: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit,
    onNavigateToAccountDeletion: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile & Media Section
            item {
                SectionHeader("Profile & Media")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Edit Profile Details",
                            subtitle = "Update bio, city, hobbies and dating preferences",
                            icon = Icons.Filled.Edit,
                            iconTint = BondlyPink,
                            onClick = onNavigateToEditProfile
                        )
                        Divider(color = CharcoalBorder, thickness = 0.5.dp)
                        SettingsNavigationRow(
                            title = "Manage Photos (5 Slots)",
                            subtitle = "Update your verified 5 photo slots",
                            icon = Icons.Filled.PhotoLibrary,
                            iconTint = BondlyPurple,
                            onClick = onNavigateToPhotoManagement
                        )
                    }
                }
            }

            // Calls & Activity Section
            item {
                SectionHeader("Calls & Activity")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Call History",
                            subtitle = "Review recent voice and video calls",
                            icon = Icons.Filled.Call,
                            iconTint = OnlineGreen,
                            onClick = onNavigateToCallHistory
                        )
                        Divider(color = CharcoalBorder, thickness = 0.5.dp)
                        SettingsNavigationRow(
                            title = "Privacy & Safety",
                            subtitle = "Control who can call and see your activity",
                            icon = Icons.Filled.Security,
                            iconTint = SuperLikeBlue,
                            onClick = onNavigateToPrivacySafety
                        )
                        Divider(color = CharcoalBorder, thickness = 0.5.dp)
                        SettingsNavigationRow(
                            title = "Blocked Members",
                            subtitle = "Manage restricted accounts",
                            icon = Icons.Filled.Block,
                            iconTint = ErrorRed,
                            onClick = onNavigateToBlockedUsers
                        )
                    }
                }
            }

            // Community & Help Section
            item {
                SectionHeader("Community & Safety")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Help, Guidelines & Safety",
                            subtitle = "Anti-harassment rules, zero contact sharing",
                            icon = Icons.Filled.HelpOutline,
                            iconTint = BondlyGold,
                            onClick = onNavigateToHelp
                        )
                    }
                }
            }

            // Account Actions
            item {
                SectionHeader("Account")
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            title = "Delete Account",
                            subtitle = "Permanently remove your profile and chats",
                            icon = Icons.Filled.DeleteForever,
                            iconTint = ErrorRed,
                            onClick = onNavigateToAccountDeletion
                        )
                        Divider(color = CharcoalBorder, thickness = 0.5.dp)
                        SettingsNavigationRow(
                            title = "Sign Out",
                            subtitle = "Log out of Bondly on this device",
                            icon = Icons.Filled.ExitToApp,
                            iconTint = TextMuted,
                            onClick = { showSignOutDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out of Bondly?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("You can sign back in at any time with your Google account.", color = TextLightGrey) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Sign Out", color = TextWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = BondlyPink,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
