package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.components.ExclusiveAccountBadge
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onViewUserProfile: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Users, 2: Message, 3: Analytics, 4: Audit & Reports
    val allUsers by repository.observeAllUsersForAdmin().collectAsState(initial = emptyList())
    val allSessions by repository.observeUserSessions().collectAsState(initial = emptyList())
    val allReports by repository.observeModerationReports().collectAsState(initial = emptyList())
    val allLogs by repository.observeAdminAuditLogs().collectAsState(initial = emptyList())
    val matches by repository.observeMatches().collectAsState(initial = emptyList())

    // Verification check: ensure current user is actually admin
    var isAuthorizedAdmin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(currentUserId) {
        val isAdmin = repository.checkIsAdmin(currentUserId)
        isAuthorizedAdmin = isAdmin
    }

    if (isAuthorizedAdmin == false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CharcoalDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Access Denied", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Text("You do not have administrative privileges.", color = TextMuted)
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryButton(text = "Return", onClick = onBack)
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Admin Dashboard", fontWeight = FontWeight.ExtraBold, color = TextWhite)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD700))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ROOT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF121212))
                        }
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CharcoalCard,
                contentColor = BondlyPink,
                edgePadding = 16.dp,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Users (${allUsers.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Custom Message", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Time Analytics", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("Reports & Logs", fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                0 -> AdminOverviewTab(
                    users = allUsers,
                    matchesCount = matches.size,
                    sessions = allSessions
                )
                1 -> AdminUserManagementTab(
                    users = allUsers,
                    onViewProfile = onViewUserProfile,
                    onUpdateStatus = { uid, status, reason ->
                        coroutineScope.launch {
                            val res = repository.updateAccountStatusByAdmin(uid, status, reason)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Account status updated to $status", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                2 -> AdminCustomMessageTab(
                    users = allUsers,
                    onSendMessage = { targetUid, title, body, onReport ->
                        coroutineScope.launch {
                            val res = repository.sendAdminCustomMessage(targetUid, title, body)
                            if (res.isSuccess) {
                                val report = res.getOrNull()
                                if (report != null) {
                                    onReport(report)
                                }
                                Toast.makeText(context, report?.statusSummary ?: "Message saved to inbox", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                3 -> AdminTimeAnalyticsTab(
                    sessions = allSessions,
                    totalUsers = allUsers.size
                )
                4 -> AdminReportsAndAuditTab(
                    reports = allReports,
                    auditLogs = allLogs,
                    onViewUserProfile = onViewUserProfile,
                    onUpdateReportStatus = { reportId, status, notes ->
                        coroutineScope.launch {
                            repository.updateReportStatus(reportId, status, notes)
                            Toast.makeText(context, "Report status: $status", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSuspendReportedUser = { targetUid, reportId ->
                        coroutineScope.launch {
                            repository.updateAccountStatusByAdmin(targetUid, "suspended", "Suspended via report $reportId")
                            repository.updateReportStatus(reportId, "resolved", "User suspended")
                            Toast.makeText(context, "User suspended & report resolved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismissReport = { reportId ->
                        coroutineScope.launch {
                            repository.updateReportStatus(reportId, "dismissed", "Dismissed by admin")
                            Toast.makeText(context, "Report dismissed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBanReportedUser = { targetUid, reportId ->
                        coroutineScope.launch {
                            repository.banUser(targetUid, reportId)
                            repository.updateReportStatus(reportId, "resolved", "User banned")
                            Toast.makeText(context, "User banned and report resolved", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

// =============================================================================
// SUB-TAB 0: OVERVIEW
// =============================================================================
@Composable
private fun AdminOverviewTab(
    users: List<UserProfile>,
    matchesCount: Int,
    sessions: List<UserSession>
) {
    val totalUsers = users.size
    val maleCount = users.count { it.gender.equals("Man", ignoreCase = true) || it.gender.equals("Male", ignoreCase = true) }
    val femaleCount = users.count { it.gender.equals("Woman", ignoreCase = true) || it.gender.equals("Female", ignoreCase = true) }
    val activeUsers = users.count { it.accountStatus == "active" }

    val nowSeconds = System.currentTimeMillis() / 1000L
    val oneDaySeconds = 86400L
    val sevenDaysSeconds = 86400L * 7L

    val newToday = users.count { u ->
        val created = u.createdAt?.seconds ?: 0L
        (nowSeconds - created) <= oneDaySeconds
    }

    val newThisWeek = users.count { u ->
        val created = u.createdAt?.seconds ?: 0L
        (nowSeconds - created) <= sevenDaysSeconds
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Platform Overview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminStatCard("Total Users", "$totalUsers", Icons.Filled.People, BondlyPink, modifier = Modifier.weight(1f))
                AdminStatCard("Active Users", "$activeUsers", Icons.Filled.CheckCircle, OnlineGreen, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminStatCard("Male Members", "$maleCount", Icons.Filled.Male, SuperLikeBlue, modifier = Modifier.weight(1f))
                AdminStatCard("Female Members", "$femaleCount", Icons.Filled.Female, BondlyMagenta, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminStatCard("New Today", "+$newToday", Icons.Filled.PersonAdd, Color(0xFFFFD700), modifier = Modifier.weight(1f))
                AdminStatCard("New This Week", "+$newThisWeek", Icons.Filled.TrendingUp, Color(0xFF00E5FF), modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AdminStatCard("Confirmed Matches", "$matchesCount", Icons.Filled.Favorite, BondlyPink, modifier = Modifier.weight(1f))
                AdminStatCard("Active Sessions", "${sessions.size}", Icons.Filled.Timer, BondlyPurple, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdminStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, color = TextMuted)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
        }
    }
}

// =============================================================================
// SUB-TAB 1: USER MANAGEMENT
// =============================================================================
@Composable
private fun AdminUserManagementTab(
    users: List<UserProfile>,
    onViewProfile: (String) -> Unit,
    onUpdateStatus: (uid: String, status: String, reason: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var selectedUserForAction by remember { mutableStateOf<UserProfile?>(null) }
    var actionReason by remember { mutableStateOf("") }
    var actionType by remember { mutableStateOf("suspend") }

    val filteredUsers = remember(users, searchQuery, statusFilter) {
        val cleanQuery = searchQuery.trim()
        val strippedQuery = cleanQuery.removePrefix("BND-").removePrefix("BONDLY-")
        users.filter { user ->
            val matchQuery = cleanQuery.isBlank() ||
                    user.displayName.contains(cleanQuery, ignoreCase = true) ||
                    user.publicUserId.contains(cleanQuery, ignoreCase = true) ||
                    (strippedQuery.isNotEmpty() && user.publicUserId.contains(strippedQuery, ignoreCase = true)) ||
                    user.userId.contains(cleanQuery, ignoreCase = true) ||
                    user.city.contains(cleanQuery, ignoreCase = true)
            val matchStatus = statusFilter == "All" || user.accountStatus.equals(statusFilter, ignoreCase = true)
            matchQuery && matchStatus
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by name, ID or city...", color = TextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BondlyPink) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "active", "suspended", "banned").forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { statusFilter = status },
                    label = { Text(status.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BondlyPink,
                        selectedLabelColor = TextWhite,
                        containerColor = CharcoalCard,
                        labelColor = TextMuted
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredUsers, key = { it.userId }) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = user.profilePhotoUrls.firstOrNull() ?: "",
                                contentDescription = user.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BondlyPink, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${user.displayName}, ${user.age}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    if (user.isAdmin) {
                                        ExclusiveAccountBadge()
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("ID: ${user.publicUserId} · ${user.gender} · ${user.city}", fontSize = 12.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "Status: ${user.accountStatus.uppercase()} · Role: ${user.role.uppercase()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (user.accountStatus) {
                                        "banned" -> ErrorRed
                                        "suspended" -> Color(0xFFFF9100)
                                        else -> OnlineGreen
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { onViewProfile(user.userId) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("View Profile", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (user.accountStatus == "active") {
                                Button(
                                    onClick = {
                                        selectedUserForAction = user
                                        actionType = "suspended"
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text("Suspend", fontSize = 12.sp, color = Color(0xFF121212), fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        onUpdateStatus(user.userId, "active", "Admin reactivated account")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text("Reactivate", fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    selectedUserForAction = user
                                    actionType = "banned"
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("Ban", fontSize = 12.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Action Confirmation Dialog
    val userAction = selectedUserForAction
    if (userAction != null) {
        Dialog(onDismissRequest = { selectedUserForAction = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (actionType == "banned") ErrorRed else Color(0xFFFF9100))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (actionType == "banned") "Ban User Account" else "Suspend User Account",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to $actionType ${userAction.displayName}?",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = actionReason,
                        onValueChange = { actionReason = it },
                        placeholder = { Text("Reason for audit log...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { selectedUserForAction = null }) {
                            Text("Cancel", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdateStatus(userAction.userId, actionType, actionReason.ifBlank { "Admin action" })
                                selectedUserForAction = null
                                actionReason = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (actionType == "banned") ErrorRed else Color(0xFFFF9100))
                        ) {
                            Text("Confirm", fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 2: CUSTOM MESSAGE TO ANY USER
// =============================================================================
@Composable
private fun AdminCustomMessageTab(
    users: List<UserProfile>,
    onSendMessage: (targetUid: String, title: String, body: String, onReport: (AdminMessageDeliveryReport) -> Unit) -> Unit
) {
    var searchUserText by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var messageTitle by remember { mutableStateOf("Official Bondly Announcement") }
    var messageBody by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var deliveryReport by remember { mutableStateOf<AdminMessageDeliveryReport?>(null) }

    val matchedCandidates = remember(searchUserText, users) {
        val clean = searchUserText.trim()
        val stripped = clean.removePrefix("BND-").removePrefix("BONDLY-")
        if (clean.isBlank()) emptyList()
        else users.filter {
            it.displayName.contains(clean, ignoreCase = true) ||
            it.publicUserId.contains(clean, ignoreCase = true) ||
            (stripped.isNotEmpty() && it.publicUserId.contains(stripped, ignoreCase = true)) ||
            it.userId.contains(clean, ignoreCase = true)
        }.take(8)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Send Official Announcement to Any User", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Messages sent here appear as an official Bondly Announcement in the user's Alerts.", fontSize = 12.sp, color = TextMuted)
        }

        // Search user to select
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("1. Select Recipient", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchUserText,
                        onValueChange = { searchUserText = it },
                        placeholder = { Text("Search recipient by name or Bondly ID...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )

                    if (matchedCandidates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        matchedCandidates.forEach { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedUser = candidate
                                        searchUserText = candidate.displayName
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = candidate.profilePhotoUrls.firstOrNull() ?: "",
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(candidate.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                                    Text("ID: ${candidate.publicUserId}", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }

                    val chosen = selectedUser
                    if (chosen != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recipient: ${chosen.displayName} (${chosen.publicUserId})", fontSize = 12.sp, color = OnlineGreen)
                        }
                    }
                }
            }
        }

        // Message Content
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("2. Compose Message", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = messageTitle,
                        onValueChange = { messageTitle = it },
                        placeholder = { Text("Announcement Title", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        placeholder = { Text("Write official message here...", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalDark,
                            unfocusedContainerColor = CharcoalDark,
                            focusedBorderColor = BondlyPink,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            }
        }

        // Action Buttons
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(
                    text = "Preview",
                    onClick = { showPreview = true },
                    enabled = messageBody.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Send Message",
                    onClick = {
                        val recipient = selectedUser
                        if (recipient != null && messageBody.isNotBlank()) {
                            onSendMessage(recipient.userId, messageTitle, messageBody) { rep ->
                                deliveryReport = rep
                            }
                            messageBody = ""
                        }
                    },
                    enabled = selectedUser != null && messageBody.isNotBlank(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Delivery Status Report
        if (deliveryReport != null) {
            val rep = deliveryReport!!
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCardElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Official Delivery Status Report", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = OnlineGreen)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Database Inbox: Saved successfully", fontSize = 12.sp, color = TextWhite)
                        Text(
                            text = if (rep.registeredDeviceCount > 0)
                                "• Device Push: Queued for ${rep.registeredDeviceCount} active registered device(s)"
                            else "• Device Push: 0 push tokens active (Recipient will view in Alerts upon next login)",
                            fontSize = 12.sp,
                            color = TextLightGrey
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Status: ${rep.statusSummary}", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }

        // Live Preview Card
        if (showPreview) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCardElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(messageTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFFD700))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(messageBody.ifBlank { "Preview text..." }, fontSize = 13.sp, color = TextLightGrey)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sent officially by Bondly Trust & Safety Team", fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 3: TIME & ACTIVITY ANALYTICS
// =============================================================================
@Composable
private fun AdminTimeAnalyticsTab(
    sessions: List<UserSession>,
    totalUsers: Int
) {
    val totalSeconds = sessions.sumOf { it.durationSeconds }
    val avgSeconds = if (sessions.isNotEmpty()) totalSeconds / sessions.size else 0L

    val totalHours = String.format(Locale.getDefault(), "%.1f", totalSeconds / 3600.0)
    val avgMinutes = (avgSeconds / 60).coerceAtLeast(1)

    // DAU / WAU / MAU calculations
    val nowSeconds = System.currentTimeMillis() / 1000L
    val dau = sessions.filter { (nowSeconds - (it.endedAt?.seconds ?: 0L)) <= 86400L }.map { it.userId }.distinct().size
    val wau = sessions.filter { (nowSeconds - (it.endedAt?.seconds ?: 0L)) <= 86400L * 7L }.map { it.userId }.distinct().size
    val mau = sessions.filter { (nowSeconds - (it.endedAt?.seconds ?: 0L)) <= 86400L * 30L }.map { it.userId }.distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("User Activity & Time Analytics", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Real-time active app engagement without background time inflation", fontSize = 12.sp, color = TextMuted)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("DAU", "$dau", Icons.Filled.Today, OnlineGreen, modifier = Modifier.weight(1f))
                AdminStatCard("WAU", "$wau", Icons.Filled.DateRange, Color(0xFF00E5FF), modifier = Modifier.weight(1f))
                AdminStatCard("MAU", "$mau", Icons.Filled.CalendarMonth, BondlyPurple, modifier = Modifier.weight(1f))
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminStatCard("Avg Session", "${avgMinutes}m", Icons.Filled.Timelapse, Color(0xFFFFD700), modifier = Modifier.weight(1f))
                AdminStatCard("Total Hours", "${totalHours}h", Icons.Filled.HourglassBottom, BondlyPink, modifier = Modifier.weight(1f))
            }
        }

        item {
            Text("Recent Sessions Log", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
        }

        if (sessions.isEmpty()) {
            item {
                Text("No session records logged yet.", fontSize = 12.sp, color = TextMuted)
            }
        } else {
            items(sessions.take(20), key = { it.sessionId }) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("User: ${session.userId.take(8)}... (${session.userGender})", fontSize = 13.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                            Text("Date: ${session.dateString}", fontSize = 11.sp, color = TextMuted)
                        }
                        Text("${session.durationSeconds / 60}m ${session.durationSeconds % 60}s", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BondlyPink)
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-TAB 4: REPORTS & AUDIT LOGS
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminReportsAndAuditTab(
    reports: List<Report>,
    auditLogs: List<AdminAuditLog>,
    onViewUserProfile: (String) -> Unit,
    onUpdateReportStatus: (reportId: String, status: String, notes: String) -> Unit,
    onSuspendReportedUser: (targetUid: String, reportId: String) -> Unit,
    onDismissReport: (reportId: String) -> Unit,
    onBanReportedUser: (targetUid: String, reportId: String) -> Unit
) {
    var statusFilter by remember { mutableStateOf("All") }

    val filteredReports = remember(reports, statusFilter) {
        if (statusFilter == "All") reports
        else reports.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("User Safety & Moderation Reports", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Investigate member complaints, apply sanctions, or dismiss frivolous flags.", fontSize = 12.sp, color = TextMuted)
        }

        // Status Filter Chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("All", "pending", "under_review", "resolved", "dismissed").forEach { status ->
                    val isSelected = statusFilter == status
                    FilterChip(
                        selected = isSelected,
                        onClick = { statusFilter = status },
                        label = {
                            val count = if (status == "All") reports.size else reports.count { it.status.equals(status, ignoreCase = true) }
                            Text("${status.replace("_", " ").replaceFirstChar { it.uppercase() }} ($count)")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BondlyPink,
                            selectedLabelColor = TextWhite,
                            containerColor = CharcoalCard,
                            labelColor = TextMuted
                        )
                    )
                }
            }
        }

        if (filteredReports.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No reports matching \"$statusFilter\".", fontSize = 13.sp, color = TextMuted)
                    }
                }
            }
        } else {
            items(filteredReports, key = { it.reportId }) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (report.status) {
                            "pending" -> WarningOrange.copy(alpha = 0.6f)
                            "under_review" -> SuperLikeBlue.copy(alpha = 0.6f)
                            "resolved" -> OnlineGreen.copy(alpha = 0.6f)
                            else -> CharcoalBorder
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header with Reported user info and status badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = report.reportedPhotoUrl.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=200&q=80" },
                                contentDescription = report.reportedName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, BondlyPink, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.reportedName.ifBlank { "User ${report.reportedUid.take(6)}" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (report.reportedPublicId.isNotBlank()) "ID: ${report.reportedPublicId} · UID: ${report.reportedUid.take(8)}..."
                                           else "UID: ${report.reportedUid}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            // Status badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (report.status) {
                                    "pending" -> WarningOrange.copy(alpha = 0.2f)
                                    "under_review" -> SuperLikeBlue.copy(alpha = 0.2f)
                                    "resolved" -> OnlineGreen.copy(alpha = 0.2f)
                                    else -> TextMuted.copy(alpha = 0.2f)
                                }
                            ) {
                                Text(
                                    text = report.status.replace("_", " ").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (report.status) {
                                        "pending" -> WarningOrange
                                        "under_review" -> SuperLikeBlue
                                        "resolved" -> OnlineGreen
                                        else -> TextLightGrey
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = CharcoalBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Reporter UID
                        Text(
                            text = "Reported by: ${report.reporterUid.take(12)}...",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Selected reasons chips
                        val reasonList = report.reasons.ifEmpty { listOf(report.reason) }.filter { it.isNotBlank() }
                        if (reasonList.isNotEmpty()) {
                            Text("Reasons Flagged:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                reasonList.forEach { reasonItem ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = CharcoalCardElevated,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                                    ) {
                                        Text(
                                            text = reasonItem,
                                            fontSize = 11.sp,
                                            color = TextWhite,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Additional message
                        if (report.details.isNotBlank()) {
                            Text("Reporter Message:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextLightGrey)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = report.details,
                                fontSize = 13.sp,
                                color = TextWhite,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Timestamp
                        val timeStr = remember(report.createdAt) {
                            try {
                                val d = report.createdAt?.toDate() ?: Date()
                                SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(d)
                            } catch (_: Exception) { "Recent" }
                        }
                        Text("Submitted: $timeStr", fontSize = 11.sp, color = TextMuted)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Admin Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onViewUserProfile(report.reportedUid) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                            ) {
                                Text("Profile", fontSize = 11.sp, maxLines = 1)
                            }

                            if (report.status != "under_review") {
                                OutlinedButton(
                                    onClick = { onUpdateReportStatus(report.reportId, "under_review", "Investigating incident") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SuperLikeBlue)
                                ) {
                                    Text("Review", fontSize = 11.sp, color = SuperLikeBlue, maxLines = 1)
                                }
                            }

                            if (report.status != "resolved") {
                                OutlinedButton(
                                    onClick = { onUpdateReportStatus(report.reportId, "resolved", "Reviewed and action taken") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, OnlineGreen)
                                ) {
                                    Text("Resolve", fontSize = 11.sp, color = OnlineGreen, maxLines = 1)
                                }
                            }

                            OutlinedButton(
                                onClick = { onDismissReport(report.reportId) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                            ) {
                                Text("Dismiss", fontSize = 11.sp, color = TextMuted, maxLines = 1)
                            }

                            Button(
                                onClick = { onSuspendReportedUser(report.reportedUid, report.reportId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100)),
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Suspend", fontSize = 11.sp, color = Color(0xFF121212), fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            Button(
                                onClick = { onBanReportedUser(report.reportedUid, report.reportId) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Ban", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Admin Audit Logs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
        }

        if (auditLogs.isEmpty()) {
            item {
                Text("No recent audit entries.", fontSize = 12.sp, color = TextMuted)
            }
        } else {
            items(auditLogs, key = { it.logId }) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCardElevated)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BondlyPink)
                            Text(
                                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(log.createdAt?.toDate() ?: Date()),
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Target: ${log.targetUserId}", fontSize = 11.sp, color = TextLightGrey)
                        if (log.details.isNotBlank()) {
                            Text("Details: ${log.details}", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}
