package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.BondlyRepository
import com.example.ui.components.EmptyState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val blockedIds by repository.observeBlockedUserIds().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked Members", fontWeight = FontWeight.Bold, color = TextWhite) },
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
        if (blockedIds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "No Blocked Members",
                    description = "When you block accounts, they will appear here and cannot view your profile or contact you.",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CharcoalCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(blockedIds) { uid ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(CharcoalCardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = TextMuted)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Member ID", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                                    Text(uid.take(12) + "...", fontSize = 12.sp, color = TextMuted)
                                }
                            }

                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.unblockUser(uid)
                                        Toast.makeText(context, "Member unblocked", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Unblock", color = BondlyPink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
