package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.UserProfile
import com.example.data.repository.BondlyRepository
import com.example.ui.components.LevelBadge
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

data class ModerationReportItem(
    val id: String,
    val reportedUser: String,
    val publicId: String,
    val reason: String,
    val date: String,
    var status: String = "Pending"
)

@Composable
fun AdminModerationScreen(
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchedUser by remember { mutableStateOf<UserProfile?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var userPendingRoleChange by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }
    var isUpdatingRole by remember { mutableStateOf(false) }

    var reportsList by remember {
        mutableStateOf(
            listOf(
                ModerationReportItem("rep_1", "Marcus V.", "BD918234", "Commercial spam / links in bio", "10m ago"),
                ModerationReportItem("rep_2", "Elena R.", "BD412095", "Suspected impersonation / fake photo", "1h ago"),
                ModerationReportItem("rep_3", "Alex K.", "BD732189", "Abusive language in chat", "3h ago")
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("admin_moderation_screen")
    ) {
        // Header
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = BondlyPink,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Safety & Admin Console",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Platform KPIs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Active Members", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "12,480", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(text = "+14% this week", color = Color(0xFF00E676), fontSize = 10.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Live RTC Calls", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "142", color = BondlyPink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(text = "HD Quality 99.8%", color = TextLightGrey, fontSize = 10.sp)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Pending Reports", color = TextMuted, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${reportsList.count { it.status == "Pending" }}", color = Color(0xFFFF4455), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(text = "Response < 1h", color = TextMuted, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ----------------------------------------------------------------
        // USER ROLE MANAGEMENT SECTION (PART 2)
        // ----------------------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ManageAccounts,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "User Role Management",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Look up any member by their Public ID (e.g. BD123456) or UID to inspect their details and manage administrator privileges.",
                    color = TextLightGrey,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Public ID (e.g. BD847291) or UID", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    PrimaryButton(
                        text = if (isSearching) "..." else "Find",
                        onClick = {
                            val q = searchQuery.trim()
                            if (q.isNotBlank()) {
                                isSearching = true
                                searchError = null
                                searchedUser = null
                                scope.launch {
                                    val result = if (q.startsWith("BD", ignoreCase = true)) {
                                        repository.searchUserByPublicId(q)
                                    } else {
                                        val p = repository.getUserProfile(q)
                                        Result.success(p)
                                    }
                                    isSearching = false
                                    result.onSuccess { profile ->
                                        if (profile != null) {
                                            searchedUser = profile
                                        } else {
                                            searchError = "No account found matching \"$q\"."
                                        }
                                    }.onFailure { err ->
                                        searchError = err.message ?: "Search failed."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.width(84.dp)
                    )
                }

                if (searchError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = searchError ?: "",
                        color = Color(0xFFFF6B7D),
                        fontSize = 12.sp
                    )
                }

                if (searchedUser != null) {
                    val user = searchedUser!!
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CharcoalDark),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (user.role == "admin") Color(0xFFAB47BC) else CharcoalBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = user.displayName,
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "ID: ${user.publicUserId} • UID: ${user.userId.take(8)}...",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                LevelBadge(
                                    level = user.userLevel,
                                    isAdmin = user.role == "admin"
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current Role: ${user.role.uppercase()}",
                                    color = if (user.role == "admin") Color(0xFFFFD700) else TextLightGrey,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )

                                if (user.role == "user") {
                                    PrimaryButton(
                                        text = "Promote to Admin",
                                        onClick = {
                                            userPendingRoleChange = Pair(user, "admin")
                                        },
                                        modifier = Modifier.height(38.dp)
                                    )
                                } else {
                                    SecondaryButton(
                                        text = "Demote to User",
                                        onClick = {
                                            userPendingRoleChange = Pair(user, "user")
                                        },
                                        modifier = Modifier.height(38.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Moderation Queue Section
        Text(
            text = "Active Incident Reports",
            color = TextLightGrey,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        reportsList.forEach { report ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CharcoalBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = report.reportedUser,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "(${report.publicId})",
                                color = BondlyPink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (report.status) {
                                        "Pending" -> Color(0x33FFB800)
                                        "Suspended" -> Color(0x33FF4455)
                                        else -> Color(0x3300E676)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = report.status,
                                color = when (report.status) {
                                    "Pending" -> Color(0xFFFFB800)
                                    "Suspended" -> Color(0xFFFF4455)
                                    else -> Color(0xFF00E676)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = "Reason: ${report.reason}", color = TextLightGrey, fontSize = 13.sp)
                    Text(text = "Reported ${report.date}", color = TextMuted, fontSize = 11.sp)

                    if (report.status == "Pending") {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SecondaryButton(
                                text = "Dismiss",
                                onClick = {
                                    reportsList = reportsList.map {
                                        if (it.id == report.id) it.copy(status = "Dismissed") else it
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryButton(
                                text = "Suspend User",
                                onClick = {
                                    reportsList = reportsList.map {
                                        if (it.id == report.id) it.copy(status = "Suspended") else it
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Platform Policies & Audit Logs
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF00E676))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Security & Access Controls", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Role elevations are cryptographically audited and recorded.\n• Non-admin users cannot alter their own role fields.\n• Mutual match deduplication enforced via atomic keys.\n• Message deletions use WhatsApp-style soft-deletion markers.",
                    color = TextLightGrey,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Role Change Confirmation Dialog
    if (userPendingRoleChange != null) {
        val (targetUser, newRole) = userPendingRoleChange!!
        val isPromoting = newRole == "admin"

        AlertDialog(
            onDismissRequest = {
                if (!isUpdatingRole) userPendingRoleChange = null
            },
            title = {
                Text(
                    text = if (isPromoting) "Promote to Administrator?" else "Demote to Regular User?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isPromoting) {
                        "Are you sure you want to promote ${targetUser.displayName} (${targetUser.publicUserId}) to Administrator? This grants them access to safety moderation, incident reports, and user role controls."
                    } else {
                        "Are you sure you want to demote ${targetUser.displayName} (${targetUser.publicUserId}) to regular user? They will lose access to administrative controls."
                    },
                    color = TextLightGrey,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isUpdatingRole = true
                        scope.launch {
                            val res = repository.updateUserRole(targetUser.userId, newRole)
                            isUpdatingRole = false
                            userPendingRoleChange = null
                            res.onSuccess {
                                searchedUser = targetUser.copy(role = newRole)
                                Toast.makeText(context, "Successfully updated role to $newRole", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "Role update failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isUpdatingRole
                ) {
                    if (isUpdatingRole) {
                        CircularProgressIndicator(color = BondlyPink, modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = if (isPromoting) "Promote" else "Demote",
                            color = if (isPromoting) BondlyPink else Color(0xFFFF4455),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { userPendingRoleChange = null },
                    enabled = !isUpdatingRole
                ) {
                    Text("Cancel", color = TextLightGrey)
                }
            },
            containerColor = CharcoalCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}
