package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch

val ReportReasons = listOf(
    "Inappropriate content or photos",
    "Harassment or abusive language",
    "Fake profile or impersonation",
    "Attempting to share external contacts or links",
    "Spam, commercial solicitation or scam",
    "Underage member"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUserScreen(
    targetUid: String,
    targetName: String,
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf(ReportReasons.first()) }
    var additionalDetails by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Member", fontWeight = FontWeight.Bold, color = TextWhite) },
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
            Text(
                text = "Report $targetName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            Text(
                text = "Your report is strictly confidential. Our moderation team reviews every flag within 24 hours.",
                fontSize = 13.sp,
                color = TextMuted
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reason for reporting", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BondlyPink)
                    ReportReasons.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedReason = reason }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = BondlyPink,
                                    unselectedColor = CharcoalBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(reason, fontSize = 13.sp, color = if (isSelected) TextWhite else TextLightGrey)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = additionalDetails,
                onValueChange = { additionalDetails = it },
                placeholder = { Text("Additional details (optional)...", color = TextMuted) },
                modifier = Modifier.fillMaxWidth().height(100.dp),
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { alsoBlock = !alsoBlock },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = alsoBlock,
                    onCheckedChange = { alsoBlock = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BondlyPink,
                        uncheckedColor = CharcoalBorder,
                        checkmarkColor = TextWhite
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Also block this member immediately", fontSize = 13.sp, color = TextLightGrey)
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Submit Report",
                isLoading = isSubmitting,
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        try {
                            repository.submitReport(
                                reportedUid = targetUid,
                                reportedName = targetName,
                                reason = selectedReason,
                                details = additionalDetails
                            )
                            if (alsoBlock) {
                                repository.blockUser(targetUid)
                            }
                            isSubmitting = false
                            Toast.makeText(context, "Report submitted. Thank you for keeping Bondly safe.", Toast.LENGTH_LONG).show()
                            onBack()
                        } catch (e: Exception) {
                            isSubmitting = false
                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
