package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.BondlyRepository
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneVerificationDialog(
    currentUserId: String,
    repository: BondlyRepository,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) } // 1: Enter phone, 2: Enter OTP, 3: Success
    var phoneNumber by remember { mutableStateOf("") }
    var otpDigits by remember { mutableStateOf(listOf("", "", "", "")) }
    var resendCountdown by remember { mutableIntStateOf(60) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusRequesters = remember { List(4) { FocusRequester() } }

    // Resend countdown timer
    LaunchedEffect(step, resendCountdown) {
        if (step == 2 && resendCountdown > 0) {
            delay(1000L)
            resendCountdown--
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CharcoalDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (step == 1) "Verify Phone Number" else if (step == 2) "Enter Verification Code" else "Verified!",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextLightGrey)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (step) {
                    1 -> {
                        // Icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CharcoalCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "To protect the community from fake profiles and scams, enter your 10-digit Indian mobile number for OTP verification.",
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CharcoalCard)
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                Text(text = "🇮🇳 +91", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(10)
                                    phoneNumber = digits
                                    errorMessage = null
                                },
                                placeholder = { Text("10-digit number", color = TextMuted, fontSize = 14.sp) },
                                modifier = Modifier.weight(1f).testTag("phone_number_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CharcoalCard,
                                    unfocusedContainerColor = CharcoalCard,
                                    focusedBorderColor = BondlyPink,
                                    unfocusedBorderColor = CharcoalBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = errorMessage!!, color = Color(0xFFFF4455), fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        PrimaryButton(
                            text = if (isSubmitting) "Sending OTP..." else "Send Verification Code",
                            onClick = {
                                if (phoneNumber.length != 10 || phoneNumber[0] !in listOf('6', '7', '8', '9')) {
                                    errorMessage = "Please enter a valid 10-digit Indian mobile number."
                                } else {
                                    isSubmitting = true
                                    scope.launch {
                                        delay(800L) // Simulate network gateway dispatch
                                        isSubmitting = false
                                        resendCountdown = 60
                                        step = 2
                                    }
                                }
                            },
                            enabled = phoneNumber.length == 10 && !isSubmitting
                        )
                    }

                    2 -> {
                        Text(
                            text = "We sent a 4-digit code to +91 $phoneNumber. Please enter it below to confirm your phone number.",
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4-digit individual boxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (i in 0 until 4) {
                                val digit = otpDigits[i]
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CharcoalCard)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (digit.isNotEmpty()) BondlyPink else CharcoalBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = digit,
                                        onValueChange = { newVal ->
                                            val filtered = newVal.filter { it.isDigit() }
                                            if (filtered.isNotEmpty()) {
                                                val nextDigits = otpDigits.toMutableList()
                                                nextDigits[i] = filtered.last().toString()
                                                otpDigits = nextDigits
                                                errorMessage = null
                                                if (i < 3) {
                                                    focusRequesters[i + 1].requestFocus()
                                                }
                                            } else {
                                                val nextDigits = otpDigits.toMutableList()
                                                nextDigits[i] = ""
                                                otpDigits = nextDigits
                                            }
                                        },
                                        modifier = Modifier.focusRequester(focusRequesters[i]).testTag("otp_digit_$i"),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = TextWhite,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        ),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = errorMessage!!, color = Color(0xFFFF4455), fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Resend countdown
                        if (resendCountdown > 0) {
                            Text(
                                text = "Resend code in ${resendCountdown}s",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    resendCountdown = 60
                                    errorMessage = null
                                }
                            ) {
                                Text(text = "Resend Code", color = BondlyPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val isOtpFilled = otpDigits.all { it.isNotEmpty() }
                        PrimaryButton(
                            text = if (isSubmitting) "Verifying..." else "Verify & Continue",
                            onClick = {
                                val enteredOtp = otpDigits.joinToString("")
                                isSubmitting = true
                                scope.launch {
                                    // Verify phone and update repository
                                    val result = repository.verifyMobileNumber(currentUserId, "+91$phoneNumber")
                                    isSubmitting = false
                                    if (result.isSuccess) {
                                        step = 3
                                        delay(1200L)
                                        onVerified()
                                    } else {
                                        errorMessage = "Verification failed. Please try again."
                                    }
                                }
                            },
                            enabled = isOtpFilled && !isSubmitting
                        )
                    }

                    3 -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0x3300E676)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Verified", tint = OnlineGreen, modifier = Modifier.size(40.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Phone Number Verified!",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Your mobile number has been safely verified and encrypted in your private account records.",
                            color = TextLightGrey,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
