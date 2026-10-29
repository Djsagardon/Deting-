package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
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
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import androidx.compose.material.icons.filled.ErrorOutline
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val DEFAULT_WEB_CLIENT_ID_FALLBACK = "148637707279-ls6kl35m66at95q60ufmdfgb8nf07tds.apps.googleusercontent.com"

@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser) -> Unit = {}
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

            }

            // Diagnostic Error Banner Card
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A151B))
                        .border(1.dp, Color(0xFFFF4455), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFF4455),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Authentication Alert",
                            color = Color(0xFFFF4455),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        color = TextLightGrey,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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

                            val activity = context.findActivity() ?: (context as? Activity)
                            if (activity == null) {
                                Log.e("BondlyAuth", "Diagnostic Failure: Cannot resolve Activity from context.")
                                errorMessage = "Unable to start Google Sign-In: Could not find host Activity."
                                isLoading = false
                                return@clickable
                            }

                            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                            val resolvedClientId = if (resId != 0) context.getString(resId).trim() else ""
                            val clientId = if (resolvedClientId.isNotEmpty()) resolvedClientId else DEFAULT_WEB_CLIENT_ID_FALLBACK

                            Log.i("BondlyAuth", "Step 1: Starting Google Sign-In with serverClientId prefix=${clientId.take(15)}...")

                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(clientId)
                                .setAutoSelectEnabled(false)
                                .build()

                            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .addCredentialOption(signInOption)
                                .build()

                            scope.launch {
                                try {
                                    Log.i("BondlyAuth", "Step 2: Calling CredentialManager.getCredential...")
                                    val result = credentialManager.getCredential(activity, request)
                                    val credential = result.credential
                                    Log.i("BondlyAuth", "Step 3: Credential received. Class=${credential.javaClass.simpleName}, Type=${(credential as? CustomCredential)?.type}")

                                    val googleIdToken: String? = when {
                                        credential is GoogleIdTokenCredential -> {
                                            credential.idToken
                                        }
                                        credential is CustomCredential && (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL || credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL) -> {
                                            try {
                                                GoogleIdTokenCredential.createFrom(credential.data).idToken
                                            } catch (parseEx: Exception) {
                                                Log.w("BondlyAuth", "GoogleIdTokenCredential.createFrom failed: ${parseEx.message}. Trying bundle fallback...")
                                                credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                                    ?: credential.data.getString("idToken")
                                                    ?: credential.data.getString("id_token")
                                            }
                                        }
                                        credential is CustomCredential -> {
                                            credential.data.getString("com.google.android.libraries.identity.googleid.BUNDLE_KEY_ID_TOKEN")
                                                ?: credential.data.getString("idToken")
                                                ?: credential.data.getString("id_token")
                                        }
                                        else -> null
                                    }

                                    if (googleIdToken.isNullOrBlank()) {
                                        Log.e("BondlyAuth", "Step 3 FAILED: Google ID Token is null or blank.")
                                        errorMessage = "Google authentication did not return an ID token. Verify Google Play Services on your device."
                                        isLoading = false
                                        return@launch
                                    }

                                    Log.i("BondlyAuth", "Step 4: ID Token acquired (length=${googleIdToken.length}). Calling Firebase.auth.signInWithCredential...")
                                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                                    val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                                    val firebaseUser = authResult.user

                                    if (firebaseUser != null) {
                                        Log.i("BondlyAuth", "Step 5 SUCCESS: Firebase Authentication completed! UID=${firebaseUser.uid}, Email=${firebaseUser.email}")
                                        isLoading = false
                                        onAuthSuccess(firebaseUser)
                                    } else {
                                        Log.e("BondlyAuth", "Step 5 FAILED: Firebase signInWithCredential returned null user.")
                                        errorMessage = "Firebase login completed but returned an empty user profile. Please try again."
                                        isLoading = false
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.i("BondlyAuth", "Google account selection was cancelled or dismissed: ${e.message}")
                                    isLoading = false
                                } catch (e: NoCredentialException) {
                                    Log.e("BondlyAuth", "NoCredentialException: No Google account available.", e)
                                    isLoading = false
                                    errorMessage = "No Google account found on this phone. Add a Google account in Android Settings -> Accounts."
                                } catch (e: GetCredentialProviderConfigurationException) {
                                    Log.e("BondlyAuth", "GetCredentialProviderConfigurationException: OAuth config error.", e)
                                    isLoading = false
                                    errorMessage = "Google Sign-In configuration mismatch: Ensure SHA-1 fingerprint (71:1E:D3:57...) is added to Firebase Console under package com.dating.bondly.app."
                                } catch (e: GetCredentialCustomException) {
                                    Log.e("BondlyAuth", "GetCredentialCustomException: type=${e.type}, msg=${e.message}", e)
                                    isLoading = false
                                    val msg = e.message.orEmpty()
                                    errorMessage = when {
                                        msg.contains("10") || msg.contains("Developer") ->
                                            "Google Sign-In Developer Error (10): The SHA-1 fingerprint is missing in Firebase Console for package com.dating.bondly.app."
                                        msg.contains("12500") ->
                                            "Google Sign-In Error (12500): Check that Support Email is configured in Firebase Console Project Settings."
                                        else -> "Google account selection error: ${e.message ?: e.type}"
                                    }
                                } catch (e: GetCredentialException) {
                                    Log.e("BondlyAuth", "GetCredentialException: [${e.javaClass.simpleName}] ${e.message}", e)
                                    isLoading = false
                                    val msg = e.message.orEmpty()
                                    errorMessage = when {
                                        msg.contains("10") || msg.contains("Developer") ->
                                            "Google Sign-In Developer Error (10): Missing SHA-1 fingerprint in Firebase Console for package com.dating.bondly.app."
                                        msg.contains("12500") ->
                                            "Google Sign-In Error (12500): Check Support Email in Firebase Console Project Settings."
                                        else -> "Google Sign-In failed: ${e.message ?: e.javaClass.simpleName}"
                                    }
                                } catch (e: FirebaseAuthInvalidCredentialsException) {
                                    Log.e("BondlyAuth", "FirebaseAuthInvalidCredentialsException: Token rejected.", e)
                                    isLoading = false
                                    errorMessage = "Firebase rejected the Google token. Verify OAuth Web Client ID matches project dating-buddy-b3581."
                                } catch (e: FirebaseAuthException) {
                                    Log.e("BondlyAuth", "FirebaseAuthException: errorCode=${e.errorCode}, msg=${e.message}", e)
                                    isLoading = false
                                    errorMessage = when {
                                        e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" || e.message?.contains("disabled", ignoreCase = true) == true ->
                                            "Google Sign-In is DISABLED in Firebase Console! Go to Firebase Console -> Authentication -> Sign-in method -> Google and click Enable."
                                        e.errorCode == "ERROR_INVALID_CREDENTIAL" ->
                                            "Firebase could not validate Google credentials. Check SHA-1 and OAuth settings."
                                        e.errorCode == "ERROR_USER_DISABLED" ->
                                            "This user account has been disabled in Firebase Authentication."
                                        else -> "Firebase Authentication error (${e.errorCode}): ${e.localizedMessage}"
                                    }
                                } catch (e: FirebaseNetworkException) {
                                    Log.e("BondlyAuth", "FirebaseNetworkException: Network timeout.", e)
                                    isLoading = false
                                    errorMessage = "Network timeout connecting to Firebase Authentication. Check your internet connection."
                                } catch (e: Exception) {
                                    Log.e("BondlyAuth", "Unexpected authentication exception: [${e.javaClass.simpleName}] ${e.message}", e)
                                    isLoading = false
                                    val msg = e.message.orEmpty()
                                    errorMessage = when {
                                        msg.contains("disabled", ignoreCase = true) ->
                                            "Google Sign-In is disabled in Firebase! Go to Firebase Console -> Authentication -> Sign-in method and enable Google."
                                        msg.contains("10") ->
                                            "Google Sign-In Developer Error (10): Missing SHA-1 fingerprint in Firebase Console."
                                        else -> e.localizedMessage ?: "Sign-In failed (${e.javaClass.simpleName}). Please try again."
                                    }
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
