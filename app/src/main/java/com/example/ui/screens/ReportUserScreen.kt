package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.BondlyRepository
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch

val AllReportReasons = listOf(
    "Fake Profile",
    "Impersonation",
    "Harassment",
    "Inappropriate Photos",
    "Abusive Language",
    "Spam",
    "Scam or Fraud",
    "Underage User",
    "Threatening Behaviour",
    "Other"
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

    val targetProfile by repository.observeProfile(targetUid).collectAsState(initial = null)
    val selectedReasons = remember { mutableStateListOf<String>() }
    var additionalMessage by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    text = "Report $targetName",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select all reasons that apply. Your report is 100% confidential and immediately escalated to the Trust & Safety team.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }

            if (validationError != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ErrorRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(validationError!!, color = TextWhite, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Select Reason(s) *",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BondlyPink
                            )
                            if (selectedReasons.isNotEmpty()) {
                                Text(
                                    "${selectedReasons.size} selected",
                                    fontSize = 12.sp,
                                    color = TextLightGrey
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AllReportReasons.forEach { reason ->
                            val isChecked = selectedReasons.contains(reason)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isChecked) {
                                            selectedReasons.remove(reason)
                                        } else {
                                            selectedReasons.add(reason)
                                            validationError = null
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            selectedReasons.add(reason)
                                            validationError = null
                                        } else {
                                            selectedReasons.remove(reason)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BondlyPink,
                                        uncheckedColor = CharcoalBorder,
                                        checkmarkColor = TextWhite
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = reason,
                                    fontSize = 14.sp,
                                    color = if (isChecked) TextWhite else TextLightGrey,
                                    fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        "Additional Message",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = additionalMessage,
                        onValueChange = { additionalMessage = it },
                        placeholder = { Text("Provide details about the incident (optional)...", color = TextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
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
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alsoBlock = !alsoBlock }
                        .padding(vertical = 4.dp),
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
                    Text(
                        "Also block $targetName immediately from discovering or calling me",
                        fontSize = 13.sp,
                        color = TextLightGrey
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(
                    text = "Submit Report",
                    isLoading = isSubmitting,
                    enabled = !isSubmitting,
                    onClick = {
                        if (selectedReasons.isEmpty()) {
                            validationError = "Please select at least one reason to submit your report."
                            return@PrimaryButton
                        }

                        isSubmitting = true
                        validationError = null
                        coroutineScope.launch {
                            try {
                                val photoUrl = targetProfile?.profilePhotoUrls?.firstOrNull() ?: ""
                                val publicId = targetProfile?.publicUserId ?: ""

                                val result = repository.submitReport(
                                    reportedUid = targetUid,
                                    reportedName = targetName,
                                    reasons = selectedReasons.toList(),
                                    details = additionalMessage.trim(),
                                    reportedPhotoUrl = photoUrl,
                                    reportedPublicId = publicId
                                )

                                if (alsoBlock) {
                                    repository.blockUser(targetUid)
                                }

                                result.onSuccess {
                                    isSubmitting = false
                                    Toast.makeText(
                                        context,
                                        "Report submitted successfully. Thank you for keeping Bondly safe.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onBack()
                                }.onFailure { error ->
                                    isSubmitting = false
                                    Toast.makeText(context, "Error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                isSubmitting = false
                                Toast.makeText(context, "Failed to submit: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_report_button")
                )
            }
        }
    }
}
