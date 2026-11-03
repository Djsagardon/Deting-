package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAndSupportScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Safety", fontWeight = FontWeight.Bold, color = TextWhite) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BondlyPink),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = BondlyPink)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Safe Indian Dating", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Bondly is engineered to foster genuine bonds with dignity and privacy. Our multi-level trust progression and anti-harassment safeguards protect all users.",
                            fontSize = 13.sp,
                            color = TextLightGrey,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            item {
                Text("Frequently Asked Questions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BondlyPink)
            }

            item {
                FaqCard(
                    question = "How do I unlock Voice and Video Calls?",
                    answer = "Bondly uses trust progression. Complete your profile with 5 photos and stay active for 5 days to reach Level 2 (Voice Calling). Reach 15 active days to unlock Level 3 (Video Calling)."
                )
            }

            item {
                FaqCard(
                    question = "Why are external phone numbers masked?",
                    answer = "For your safety, our automated filter prevents sharing phone numbers and payment links in unverified chats. Continue communicating through Bondly's encrypted in-app voice and video channels."
                )
            }

            item {
                FaqCard(
                    question = "What should I do if someone violates guidelines?",
                    answer = "Open the member's profile or chat menu and select 'Report or Block'. Our 24/7 moderation team investigates every report and applies bans when necessary."
                )
            }

            item {
                FaqCard(
                    question = "How do I search for friends?",
                    answer = "Every verified profile has a unique Bondly ID (e.g. BONDLY-1234). Use the 'Search by Bondly ID' feature in the Discover tab or Settings to find someone directly."
                )
            }
        }
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Spacer(modifier = Modifier.height(6.dp))
            Text(answer, fontSize = 12.sp, color = TextLightGrey, lineHeight = 17.sp)
        }
    }
}
