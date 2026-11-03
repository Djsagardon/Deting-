package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.BondlyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySafetySettingsScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userProfile by repository.observeProfile(currentUserId).collectAsState(initial = null)

    var showOnlineStatus by remember { mutableStateOf(true) }
    var allowCallsFromMatchesOnly by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            showOnlineStatus = it.showOnlineStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Safety", fontWeight = FontWeight.Bold, color = TextWhite) },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Online Activity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Let matches see when you are active on Bondly", fontSize = 12.sp, color = TextMuted)
                        }
                        Switch(
                            checked = showOnlineStatus,
                            onCheckedChange = { checked ->
                                showOnlineStatus = checked
                                coroutineScope.launch {
                                    userProfile?.let {
                                        repository.saveProfile(it.copy(showOnlineStatus = checked))
                                        Toast.makeText(context, "Privacy setting saved", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextWhite,
                                checkedTrackColor = BondlyPink,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = CharcoalCardElevated
                            )
                        )
                    }

                    Divider(color = CharcoalBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Only Matches Can Call", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Restrict incoming calls strictly to mutual matches", fontSize = 12.sp, color = TextMuted)
                        }
                        Switch(
                            checked = allowCallsFromMatchesOnly,
                            onCheckedChange = { checked ->
                                allowCallsFromMatchesOnly = checked
                                Toast.makeText(context, "Call preference updated", Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextWhite,
                                checkedTrackColor = BondlyPink,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = CharcoalCardElevated
                            )
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Zero Contact Sharing Protection", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BondlyPink)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Bondly's safety engine actively redacts external contact info (phone numbers, WhatsApp links, payment handles) in initial chats to safeguard all community members.",
                        fontSize = 12.sp,
                        color = TextLightGrey,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
