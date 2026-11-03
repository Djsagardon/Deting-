package com.example.ui.screens

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.CallSession
import com.example.data.repository.BondlyRepository
import com.example.data.rtc.AgoraCallManager
import com.example.data.rtc.RtcConnectionState
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callId: String,
    targetUid: String,
    targetName: String,
    targetPhoto: String = "",
    isVideo: Boolean,
    isIncoming: Boolean,
    repository: BondlyRepository,
    onCallFinished: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val callManager = remember { AgoraCallManager(context) }

    val connectionState by callManager.connectionState.collectAsState()
    val isMicMuted by callManager.isMicMuted.collectAsState()
    val isCameraEnabled by callManager.isCameraEnabled.collectAsState()
    val isFrontCamera by callManager.isFrontCamera.collectAsState()
    val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
    val callDuration by callManager.callDurationSeconds.collectAsState()

    // Observe call session in Firestore
    var callSession by remember { mutableStateOf<CallSession?>(null) }
    LaunchedEffect(callId) {
        if (callId.isNotEmpty()) {
            if (!isIncoming) {
                callManager.startOutgoingCall(callId, isVideo)
            } else {
                callManager.onCallAccepted(callId, isVideo)
            }

            repository.observeIncomingCalls().collect { session ->
                if (session?.callId == callId) {
                    callSession = session
                    if (session.status == "rejected" || session.status == "ended") {
                        callManager.endCall("Call finished")
                        onCallFinished()
                    }
                }
            }
        }
    }

    // Auto-connect simulation for outgoing call
    LaunchedEffect(Unit) {
        if (!isIncoming) {
            kotlinx.coroutines.delay(2500L)
            callManager.onCallAccepted(callId, isVideo)
            repository.updateCallStatus(callId, "active")
        }
    }

    // Pulsing animation for audio calls
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
    ) {
        if (isVideo) {
            // VIDEO CALL VIEW
            if (isCameraEnabled) {
                // Live CameraX Feed
                LocalCameraFeed(
                    isFrontCamera = isFrontCamera,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Camera Off placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CharcoalDark),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Camera is turned off", color = TextLightGrey)
                    }
                }
            }

            // Top gradient overlay with call info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = targetName,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = if (connectionState is RtcConnectionState.Connected) OnlineGreen else WarningOrange
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (connectionState) {
                                        is RtcConnectionState.Connected -> "Live • ${formatDuration(callDuration)}"
                                        is RtcConnectionState.OutgoingRinging -> "Ringing..."
                                        is RtcConnectionState.IncomingRinging -> "Incoming call..."
                                        else -> "Connecting..."
                                    },
                                    color = TextLightGrey,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Bondly Encrypted Badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CharcoalCard.copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Agora HD", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Remote Picture-in-Picture Preview (Simulated / Connected)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 90.dp, end = 20.dp)
                    .size(width = 110.dp, height = 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalCard)
                    .border(2.dp, BondlyPink, RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = targetPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80" },
                    contentDescription = targetName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = targetName.take(6),
                        color = TextWhite,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            // VOICE CALL VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    // Pulsating ring
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(BondlyPink.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(BondlyPurple.copy(alpha = 0.25f))
                    )
                    // Profile Photo
                    AsyncImage(
                        model = targetPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80" },
                        contentDescription = targetName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, BondlyPink, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = targetName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (connectionState) {
                        is RtcConnectionState.Connected -> "Connected • ${formatDuration(callDuration)}"
                        is RtcConnectionState.OutgoingRinging -> "Ringing..."
                        is RtcConnectionState.IncomingRinging -> "Incoming call..."
                        else -> "Connecting..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (connectionState is RtcConnectionState.Connected) OnlineGreen else TextLightGrey
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🔒 Bondly End-to-End Encrypted Voice Call",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        // BOTTOM CONTROLS BAR (For both Voice and Video)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 0f
                    )
                )
                .padding(bottom = 36.dp, top = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Mute Toggle
                CallControlButton(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    isActive = !isMicMuted,
                    onClick = { callManager.toggleMic() },
                    contentDescription = "Toggle Mic"
                )

                if (isVideo) {
                    // Video Camera Toggle
                    CallControlButton(
                        icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        isActive = isCameraEnabled,
                        onClick = { callManager.toggleCamera() },
                        contentDescription = "Toggle Video"
                    )

                    // Switch Camera (Front / Back)
                    CallControlButton(
                        icon = Icons.Default.FlipCameraAndroid,
                        isActive = true,
                        onClick = { callManager.switchCamera() },
                        contentDescription = "Switch Camera"
                    )
                }

                // Speaker Toggle
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    isActive = isSpeakerOn,
                    onClick = { callManager.toggleSpeaker() },
                    contentDescription = "Toggle Speaker"
                )

                // End Call Button
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            callManager.endCall()
                            repository.updateCallStatus(callId, "ended")
                            onCallFinished()
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(ErrorRed)
                        .testTag("end_call_button")
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = TextWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isActive) CharcoalCardElevated else CharcoalBorder)
            .border(1.dp, if (isActive) BondlyPink.copy(alpha = 0.5f) else CharcoalBorder, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) TextWhite else TextMuted,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LocalCameraFeed(
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (_: Exception) {
                    // Fallback to back camera if front camera is unavailable
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview
                        )
                    } catch (_: Exception) {}
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier
    )
}
