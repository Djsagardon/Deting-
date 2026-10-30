package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.LevelBadge
import com.example.ui.components.PhoneVerificationDialog
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.util.UserLevelManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var showPhoneVerificationDialog by remember { mutableStateOf(false) }
    var allowCallsFrom by remember(userProfile) { mutableStateOf(userProfile?.allowCallsFrom ?: "matches") }
    var showOnlineStatus by remember(userProfile) { mutableStateOf(userProfile?.showOnlineStatus ?: true) }

    val progression = remember(userProfile) { UserLevelManager.calculateProgression(userProfile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("settings_screen")
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
            Column {
                Text(
                    text = "Settings & Management",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Manage your profile, account, security and levels",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: PROFILE MANAGEMENT (Relocated from Profile overview)
        SectionHeader("Profile & Details")

        SettingsNavigationCard(
            title = "Edit Profile Information",
            subtitle = "Bio, city, state, interests & preferences",
            icon = Icons.Default.Edit,
            iconTint = BondlyPink,
            onClick = onNavigateToEditProfile
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsNavigationCard(
            title = "Manage Photos (5 Required)",
            subtitle = "Upload and arrange your 5 verified photos",
            icon = Icons.Default.Image,
            iconTint = Color(0xFF00E5FF),
            onClick = onNavigateToPhotoManagement
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mobile Number Verification
        SettingsNavigationCard(
            title = if (userProfile?.isMobileVerified == true) "Phone Verified: ${userProfile.maskedMobileNumber}" else "Verify Mobile Number",
            subtitle = if (userProfile?.isMobileVerified == true) "Encrypted in private records" else "Required for community safety & badge",
            icon = if (userProfile?.isMobileVerified == true) Icons.Default.VerifiedUser else Icons.Default.Phone,
            iconTint = if (userProfile?.isMobileVerified == true) OnlineGreen else Color(0xFFFFB74D),
            onClick = { showPhoneVerificationDialog = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: COMMUNICATION & CALLING
        SectionHeader("Communication & Calling")

        SettingsNavigationCard(
            title = "Call History",
            subtitle = "Audio and HD video call logs",
            icon = Icons.Default.Call,
            iconTint = Color(0xFF69F0AE),
            onClick = onNavigateToCallHistory
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Call Permission Setting
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow Calls From Matches Only",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (allowCallsFrom == "matches") "Only mutual matches can call you" else "Open call access",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = allowCallsFrom == "matches",
                    onCheckedChange = { checked ->
                        val newSetting = if (checked) "matches" else "everyone"
                        allowCallsFrom = newSetting
                        scope.launch {
                            if (userProfile != null) {
                                repository.saveUserProfile(userProfile.copy(allowCallsFrom = newSetting))
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = BondlyPink,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CharcoalDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 3: PRIVACY & SAFETY
        SectionHeader("Privacy & Community Safety")

        // Show Online Status Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Online Activity Status",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (showOnlineStatus) "Matches can see when you are active" else "Online status hidden",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = showOnlineStatus,
                    onCheckedChange = { checked ->
                        showOnlineStatus = checked
                        scope.launch {
                            if (userProfile != null) {
                                repository.saveUserProfile(userProfile.copy(showOnlineStatus = checked))
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = BondlyPink,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CharcoalDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingsNavigationCard(
            title = "Privacy & Safety Rules",
            subtitle = "Data handling & DPDP Act compliance",
            icon = Icons.Default.Security,
            iconTint = Color(0xFF81D4FA),
            onClick = onNavigateToPrivacySafety
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsNavigationCard(
            title = "Blocked Users",
            subtitle = "Manage restricted accounts",
            icon = Icons.Default.Block,
            iconTint = Color(0xFFFF5252),
            onClick = onNavigateToBlockedUsers
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsNavigationCard(
            title = "Help & Community Guidelines",
            subtitle = "Safety advice, support & anti-harassment",
            icon = Icons.Default.HelpOutline,
            iconTint = TextLightGrey,
            onClick = onNavigateToHelp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 4: USER LEVEL & PROGRESSION DETAILS
        SectionHeader("Level & Privileges")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LevelBadge(level = progression.currentLevel.level, isAdmin = progression.isAdmin)
                    Text(
                        text = "${progression.activeDays} Active Days",
                        color = BondlyPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = progression.currentLevel.description,
                    color = TextLightGrey,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Unlocked Privileges:",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                progression.currentLevel.perks.forEach { perk ->
                    Text(
                        text = "• $perk",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (progression.nextLevel != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Next: ${progression.nextLevel.badgeName} (${progression.daysToNextLevel}d age & ${progression.activeDaysToNextLevel} active days remaining)",
                        color = BondlyPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 5: ADMIN MODERATION PANEL (For designated admin accounts)
        if (progression.isAdmin) {
            SectionHeader("Official Administration")

            SettingsNavigationCard(
                title = "Administrator Control Panel",
                subtitle = "User moderation, reports, safety controls",
                icon = Icons.Default.AdminPanelSettings,
                iconTint = Color(0xFFE040FB),
                onClick = onNavigateToAdmin
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // SECTION 6: ACCOUNT ACTIONS
        SectionHeader("Account")

        SettingsNavigationCard(
            title = "Delete Account",
            subtitle = "Permanently erase your profile & messages",
            icon = Icons.Default.DeleteForever,
            iconTint = Color(0xFFFF4455),
            onClick = onNavigateToAccountDeletion
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingsNavigationCard(
            title = "Sign Out",
            subtitle = "Log out from this device",
            icon = Icons.Default.PowerSettingsNew,
            iconTint = TextMuted,
            onClick = {
                scope.launch {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Exception) {}
                    Firebase.auth.signOut()
                    onSignOut()
                }
            }
        )
    }

    if (showPhoneVerificationDialog) {
        PhoneVerificationDialog(
            currentUserId = currentUserId,
            repository = repository,
            onDismiss = { showPhoneVerificationDialog = false },
            onVerified = {
                showPhoneVerificationDialog = false
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
