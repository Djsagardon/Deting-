package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.BondlyRepository
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionScreen(
    currentUserId: String,
    repository: BondlyRepository,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var confirmationInput by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delete Account", fontWeight = FontWeight.Bold, color = TextWhite) },
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
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Permanent Deletion", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Deleting your account will permanently wipe your profile, 5 photos, match history, and active messages. This action cannot be reversed.",
                            fontSize = 13.sp,
                            color = TextLightGrey,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Text(
                text = "Type DELETE in all capital letters to confirm:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextLightGrey
            )

            OutlinedTextField(
                value = confirmationInput,
                onValueChange = { confirmationInput = it },
                placeholder = { Text("DELETE", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard,
                    focusedBorderColor = ErrorRed,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    isDeleting = true
                    coroutineScope.launch {
                        try {
                            repository.deleteCurrentUserAccount()
                            isDeleting = false
                            Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                            onAccountDeleted()
                        } catch (e: Exception) {
                            isDeleting = false
                            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = confirmationInput == "DELETE" && !isDeleting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ErrorRed,
                    disabledContainerColor = CharcoalBorder
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Permanently Delete Account", fontWeight = FontWeight.Bold, color = TextWhite)
                }
            }
        }
    }
}
