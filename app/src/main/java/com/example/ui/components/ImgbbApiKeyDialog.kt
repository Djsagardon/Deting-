package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.ImageUploadConfig
import com.example.data.repository.ImageUploadRepository
import com.example.data.repository.ServerHealthResult
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ImgbbApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSaveKey: (String) -> Unit,
    onResetToDefault: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageRepo = remember { ImageUploadRepository() }

    var inputKey by remember { mutableStateOf(currentKey) }
    var isTestingHealth by remember { mutableStateOf(false) }
    var healthResult by remember { mutableStateOf<ServerHealthResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CharcoalCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ImgBB API Settings",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Bondly uploads images directly to the official ImgBB API (api.imgbb.com/1/upload).",
                    color = TextLightGrey,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ImgBB API Key:",
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it.trim() },
                    placeholder = { Text("Enter your 32-character ImgBB API Key", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CharcoalDark,
                        unfocusedContainerColor = CharcoalDark,
                        focusedBorderColor = BondlyPink,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("imgbb_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Test Connection button
                Button(
                    onClick = {
                        isTestingHealth = true
                        healthResult = null
                        ImageUploadConfig.setApiKey(context, inputKey)
                        coroutineScope.launch {
                            val result = imageRepo.testHealth(context)
                            healthResult = result
                            isTestingHealth = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_connection_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTestingHealth && inputKey.isNotBlank()
                ) {
                    if (isTestingHealth) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = BondlyPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying ImgBB...", color = TextWhite, fontSize = 12.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = BondlyPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify API Key", color = TextWhite, fontSize = 12.sp)
                    }
                }

                // Health test results display
                if (healthResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val res = healthResult!!
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (res.isHealthy) OnlineGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (res.isHealthy) OnlineGreen.copy(alpha = 0.5f) else ErrorRed.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (res.isHealthy) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (res.isHealthy) OnlineGreen else ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (res.isHealthy) "Key Valid & Connected" else "Connection Failed",
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isHealthy) OnlineGreen else ErrorRed,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.message,
                                color = TextWhite,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onResetToDefault,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Default", color = TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onSaveKey(inputKey) },
                        colors = ButtonDefaults.buttonColors(containerColor = BondlyPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("save_imgbb_key_button")
                    ) {
                        Text("Save & Apply", fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
