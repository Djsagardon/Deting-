package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.*
import com.example.util.NetworkConnectivityManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val DEFAULT_WEB_CLIENT_ID_FALLBACK = "148637707279-ls6kl35m66at95q60ufmdfgb8nf07tds.apps.googleusercontent.com"

private fun findActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var isAgeConfirmed by remember { mutableStateOf(false) }
    var isTermsAccepted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(CharcoalDark, CharcoalSurface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // New Brand Logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, BondlyPink, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.bondly_logo),
                    contentDescription = "Bondly Logo",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Bondly",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextWhite,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Authentic Indian Dating & Social Connections",
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Checkboxes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isAgeConfirmed = !isAgeConfirmed }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAgeConfirmed,
                    onCheckedChange = { isAgeConfirmed = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BondlyPink,
                        uncheckedColor = CharcoalBorder,
                        checkmarkColor = TextWhite
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I confirm I am 18 years of age or older",
                    fontSize = 13.sp,
                    color = TextLightGrey
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isTermsAccepted = !isTermsAccepted }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isTermsAccepted,
                    onCheckedChange = { isTermsAccepted = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = BondlyPink,
                        uncheckedColor = CharcoalBorder,
                        checkmarkColor = TextWhite
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I accept Bondly's Safety Rules & Community Guidelines",
                    fontSize = 13.sp,
                    color = TextLightGrey
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage ?: "",
                    fontSize = 13.sp,
                    color = ErrorRed,
                    textAlign = TextAlign.Center
                )
            }

            val isOnline by NetworkConnectivityManager.isOnline.collectAsState()
            if (!isOnline) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BondlyPink.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = BondlyPink)
                        Text(
                            text = "No internet connection. Please connect to the internet and try again.",
                            color = TextWhite,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Google Sign In Button
            PrimaryButton(
                text = "Continue with Google",
                onClick = {
                    if (!NetworkConnectivityManager.isConnected()) {
                        errorMessage = "No internet connection. Please connect to the internet and try again."
                        return@PrimaryButton
                    }
                    isLoading = true
                    errorMessage = null
                    val activity = findActivity(context)
                    if (activity == null) {
                        isLoading = false
                        errorMessage = "Cannot start Google Sign-In: Activity not found."
                        return@PrimaryButton
                    }

                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.getPackageName())
                    val clientId = if (resId != 0) {
                        context.getString(resId).trim().ifEmpty { DEFAULT_WEB_CLIENT_ID_FALLBACK }
                    } else {
                        DEFAULT_WEB_CLIENT_ID_FALLBACK
                    }

                    coroutineScope.launch {
                        try {
                            val googleIdOption = GetSignInWithGoogleOption.Builder(clientId)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(activity, request)
                            val cred = result.credential
                            val idToken = if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                GoogleIdTokenCredential.createFrom(cred.data).idToken
                            } else {
                                cred.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                    ?: cred.data.getString("id_token")
                            }

                            if (!idToken.isNullOrEmpty()) {
                                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                                val user = authResult.user
                                if (user != null) {
                                    onAuthSuccess(user)
                                } else {
                                    errorMessage = "Google authentication returned null user."
                                }
                            } else {
                                errorMessage = "Could not extract Google ID Token."
                            }
                        } catch (e: GetCredentialCancellationException) {
                            Log.i("BondlyAuth", "Google sign-in was cancelled by user.")
                        } catch (e: Exception) {
                            Log.e("BondlyAuth", "Sign in error: ${e.message}", e)
                            errorMessage = "Sign-in error: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = isAgeConfirmed && isTermsAccepted,
                isLoading = isLoading,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = TextWhite
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_signin_button")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Protected with end-to-end user verification and anti-harassment policy.",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
