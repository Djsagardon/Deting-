package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isAgeConfirmed by remember { mutableStateOf(false) }
    var isTermsAccepted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canProceed = isAgeConfirmed && isTermsAccepted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(24.dp)
            .testTag("auth_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header & Branding
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(BondlyGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Bondly",
                        tint = TextWhite,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Bondly",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Real Dating for India.\nVerified Singles. Real Video & Voice Calls.",
                    color = TextLightGrey,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FF2D75))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = BondlyPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Free • Zero Paywalls • Real Verified Users",
                        color = BondlyPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Age Verification and Terms
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "Safety & Community Rules",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAgeConfirmed = !isAgeConfirmed }
                ) {
                    Checkbox(
                        checked = isAgeConfirmed,
                        onCheckedChange = { isAgeConfirmed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BondlyPink,
                            checkmarkColor = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I confirm that I am at least 18 years old.",
                        color = TextLightGrey,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isTermsAccepted = !isTermsAccepted }
                ) {
                    Checkbox(
                        checked = isTermsAccepted,
                        onCheckedChange = { isTermsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BondlyPink,
                            checkmarkColor = TextWhite
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I agree to Community Safety Guidelines & Anti-Harassment Rules.",
                        color = TextLightGrey,
                        fontSize = 13.sp
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF4455),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Google Sign-In Action
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (canProceed && !isLoading) BondlyGradient else androidx.compose.ui.graphics.Brush.linearGradient(listOf(CharcoalBorder, CharcoalBorder)))
                        .clickable(enabled = canProceed && !isLoading) {
                            isLoading = true
                            errorMessage = null

                            val clientId = try {
                                context.getString(com.example.R.string.default_web_client_id)
                            } catch (e: Exception) {
                                Log.e("BondlyAuth", "Failed to resolve default_web_client_id: ${e.message}")
                                errorMessage = "Google Sign-In configuration missing: default_web_client_id not found"
                                isLoading = false
                                return@clickable
                            }

                            Log.d("BondlyAuth", "Initiating Google Sign-In with serverClientId=$clientId")

                            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInOption)
                                .build()

                            scope.launch {
                                try {
                                    val result = credentialManager.getCredential(context as Activity, request)
                                    val credential = result.credential
                                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                                        val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                                        Log.d("BondlyAuth", "Firebase auth succeeded for UID: ${authResult.user?.uid}")
                                        isLoading = false
                                        onAuthSuccess()
                                    } else {
                                        Log.e("BondlyAuth", "Unexpected credential format: ${credential::class.java.name}")
                                        isLoading = false
                                        errorMessage = "Unexpected credential format from Google Identity."
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.i("BondlyAuth", "Google account selection cancelled by user")
                                    isLoading = false
                                } catch (e: NoCredentialException) {
                                    Log.w("BondlyAuth", "No Google account available on this device", e)
                                    isLoading = false
                                    errorMessage = "No Google account found on device. Please sign in to Google in Android Settings."
                                } catch (e: GetCredentialProviderConfigurationException) {
                                    Log.e("BondlyAuth", "OAuth credential configuration mismatch", e)
                                    isLoading = false
                                    errorMessage = "Google Sign-In configuration mismatch. Check project OAuth settings."
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    Log.e("BondlyAuth", "Firebase invalid credentials error", e)
                                    isLoading = false
                                    errorMessage = "Google credential could not be validated by Firebase. Please try again."
                                } catch (e: FirebaseNetworkException) {
                                    Log.e("BondlyAuth", "Firebase network timeout / unreachable", e)
                                    isLoading = false
                                    errorMessage = "Network timeout connecting to Firebase Authentication. Check your connection."
                                } catch (e: Exception) {
                                    Log.e("BondlyAuth", "Unexpected authentication exception: ${e.javaClass.simpleName}", e)
                                    isLoading = false
                                    errorMessage = e.localizedMessage ?: "Google Sign-In failed. Please try again."
                                }
                            }
                        }
                        .testTag("google_sign_in_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = TextWhite,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sign in with Google",
                                color = if (canProceed) TextWhite else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Protected by Firebase Security & Google Identity",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}
