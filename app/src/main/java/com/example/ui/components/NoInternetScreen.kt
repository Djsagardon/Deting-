package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.NetworkConnectivityManager
import kotlinx.coroutines.launch

@Composable
fun NoInternetScreen(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var isProbing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(28.dp)
            .testTag("no_internet_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Warning Wifi Off Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(BondlyPink.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "No internet connection",
                        tint = BondlyPink,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Mandatory Title
                Text(
                    text = "No internet connection",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    textAlign = TextAlign.Center
                )

                // Mandatory Subtitle
                Text(
                    text = "Please connect to the internet and try again.",
                    fontSize = 15.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                // Feedback message if still offline after manual retry
                feedbackMessage?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = BondlyPink,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Retry Button
                PrimaryButton(
                    text = if (isProbing) "Checking connection..." else "Retry",
                    onClick = {
                        if (!isProbing) {
                            isProbing = true
                            feedbackMessage = null
                            coroutineScope.launch {
                                val isNowOnline = NetworkConnectivityManager.probeLiveInternet()
                                isProbing = false
                                if (isNowOnline) {
                                    NetworkConnectivityManager.checkConnectivity(forceProbe = false)
                                    onRetry()
                                } else {
                                    feedbackMessage = "Still offline. Please check your Wi-Fi or mobile data."
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("retry_connection_button")
                )
            }
        }
    }
}
