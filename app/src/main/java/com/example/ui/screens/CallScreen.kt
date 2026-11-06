package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.CallSession
import com.example.data.repository.BondlyRepository
import com.example.data.webrtc.CallConnectionState
import com.example.data.webrtc.WebRtcCallManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

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

    var handledAnswer by remember { mutableStateOf(false) }
    var handledOffer by remember { mutableStateOf(false) }
    val receivedCandidateIds = remember { mutableSetOf<String>() }

    val callManager = remember(callId) {
        WebRtcCallManager(
            context = context,
            onSendOffer = { sdp ->
                coroutineScope.launch {
                    repository.setCallOffer(callId, sdp)
                }
            },
            onSendAnswer = { sdp ->
                coroutineScope.launch {
                    repository.setCallAnswer(callId, sdp)
                }
            },
            onSendIceCandidate = { sdpMid, sdpMLineIndex, sdp ->
                coroutineScope.launch {
                    if (!isIncoming) {
                        repository.addCallerCandidate(callId, sdpMid, sdpMLineIndex, sdp)
                    } else {
                        repository.addReceiverCandidate(callId, sdpMid, sdpMLineIndex, sdp)
                    }
                }
            },
            onCallEndedCallback = { _, duration ->
                coroutineScope.launch {
                    repository.updateCallStatus(callId, "ended", duration)
                }
            }
        )
    }

    val connectionState by callManager.connectionState.collectAsState()
    val isMicMuted by callManager.isMicMuted.collectAsState()
    val isCameraEnabled by callManager.isCameraEnabled.collectAsState()
    val isFrontCamera by callManager.isFrontCamera.collectAsState()
    val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
    val callDuration by callManager.callDurationSeconds.collectAsState()

    DisposableEffect(callId) {
        onDispose {
            callManager.release()
        }
    }

    // Initialize WebRTC signaling lifecycle
    LaunchedEffect(callId) {
        if (callId.isNotEmpty()) {
            callManager.initWebRtc(isVideo = isVideo, isCaller = !isIncoming)

            if (!isIncoming) {
                // Caller generates offer
                callManager.startOutgoingCall()

                // Listen for receiver's answer and status updates
                launch {
                    repository.observeCallSession(callId).collect { session ->
                        if (session != null) {
                            if (!handledAnswer && session.answer != null) {
                                val answerSdp = session.answer["sdp"]
                                if (!answerSdp.isNullOrEmpty()) {
                                    handledAnswer = true
                                    callManager.onRemoteAnswerReceived(answerSdp)
                                }
                            }

                            if (session.status == "rejected") {
                                Toast.makeText(context, "$targetName declined the call", Toast.LENGTH_SHORT).show()
                                callManager.endCall("Call declined")
                                onCallFinished()
                            } else if (session.status == "ended") {
                                callManager.endCall("Call ended")
                                onCallFinished()
                            } else if (session.status == "busy") {
                                Toast.makeText(context, "$targetName is currently busy", Toast.LENGTH_SHORT).show()
                                callManager.endCall("User busy")
                                onCallFinished()
                            }
                        }
                    }
                }

                // Listen for receiver's ICE candidates
                launch {
                    repository.observeReceiverCandidates(callId).collect { candidates ->
                        for (cand in candidates) {
                            if (cand.id.isNotEmpty() && cand.id !in receivedCandidateIds) {
                                receivedCandidateIds.add(cand.id)
                                callManager.addRemoteIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.sdp)
                            }
                        }
                    }
                }
            } else {
                // Receiver listens for offer
                launch {
                    repository.observeCallSession(callId).collect { session ->
                        if (session != null) {
                            if (!handledOffer && session.offer != null) {
                                val offerSdp = session.offer["sdp"]
                                if (!offerSdp.isNullOrEmpty()) {
                                    handledOffer = true
                                    callManager.answerIncomingCall(offerSdp)
                                }
                            }

                            if (session.status == "ended") {
                                callManager.endCall("Call ended")
                                onCallFinished()
                            }
                        }
                    }
                }

                // Listen for caller's ICE candidates
                launch {
                    repository.observeCallerCandidates(callId).collect { candidates ->
                        for (cand in candidates) {
                            if (cand.id.isNotEmpty() && cand.id !in receivedCandidateIds) {
                                receivedCandidateIds.add(cand.id)
                                callManager.addRemoteIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.sdp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Pulsing animation for audio calls and connecting state
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
            // =========================================================================
            // VIDEO CALL VIEW (REAL WEBRTC RENDERING)
            // =========================================================================
            Box(modifier = Modifier.fillMaxSize()) {
                // Remote Video Renderer (Full Screen)
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            callManager.setRemoteRenderer(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Placeholder if remote video not connected yet
                if (connectionState !is CallConnectionState.Connected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CharcoalDark.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(140.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(BondlyPink.copy(alpha = 0.2f))
                                )
                                AsyncImage(
                                    model = targetPhoto.ifEmpty { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80" },
                                    contentDescription = targetName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, BondlyPink, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = targetName,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (connectionState) {
                                    is CallConnectionState.OutgoingRinging -> "Ringing..."
                                    is CallConnectionState.IncomingRinging -> "Incoming video call..."
                                    is CallConnectionState.Connecting -> "Connecting WebRTC..."
                                    is CallConnectionState.Failed -> "Connection failed"
                                    else -> "Starting video call..."
                                },
                                color = if (connectionState is CallConnectionState.Failed) ErrorRed else TextLightGrey,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Local Video Preview (Picture-in-Picture in Top-Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 90.dp, end = 20.dp)
                        .size(width = 110.dp, height = 150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CharcoalCard)
                        .border(2.dp, BondlyPink, RoundedCornerShape(16.dp))
                ) {
                    if (isCameraEnabled) {
                        AndroidView(
                            factory = { ctx ->
                                SurfaceViewRenderer(ctx).apply {
                                    callManager.setLocalRenderer(this)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CharcoalCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "You",
                            color = TextWhite,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
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
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(20.dp)
            ) {
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
                                color = if (connectionState is CallConnectionState.Connected) OnlineGreen else WarningOrange
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (connectionState) {
                                    is CallConnectionState.Connected -> "Live • ${formatDuration(callDuration)}"
                                    is CallConnectionState.OutgoingRinging -> "Ringing..."
                                    is CallConnectionState.IncomingRinging -> "Incoming call..."
                                    is CallConnectionState.Connecting -> "Connecting WebRTC..."
                                    is CallConnectionState.Failed -> "Connection Failed"
                                    else -> "Connecting..."
                                },
                                color = TextLightGrey,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Native WebRTC P2P Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalCard.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("WebRTC P2P", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // =========================================================================
            // VOICE CALL VIEW (NATIVE WEBRTC AUDIO)
            // =========================================================================
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
                        is CallConnectionState.Connected -> "Connected • ${formatDuration(callDuration)}"
                        is CallConnectionState.OutgoingRinging -> "Ringing..."
                        is CallConnectionState.IncomingRinging -> "Incoming call..."
                        is CallConnectionState.Connecting -> "Connecting WebRTC audio..."
                        is CallConnectionState.Failed -> "Connection failed"
                        else -> "Connecting..."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (connectionState is CallConnectionState.Connected) OnlineGreen else if (connectionState is CallConnectionState.Failed) ErrorRed else TextLightGrey
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CharcoalCardElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = BondlyPink, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Native WebRTC Peer-to-Peer Audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLightGrey
                        )
                    }
                }
            }
        }

        // Connection failure notice if STUN is blocked on the network
        if (connectionState is CallConnectionState.Failed) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = CharcoalCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.SignalWifiBad, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connection Failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (connectionState as CallConnectionState.Failed).message + "\n\nDirect STUN peer-to-peer NAT traversal failed. Both devices must be on network environments permitting UDP P2P traffic.",
                        color = TextLightGrey,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // =========================================================================
        // BOTTOM CONTROLS BAR (For both Voice and Video)
        // =========================================================================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
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
                            val duration = callDuration
                            callManager.endCall("Call ended by user")
                            repository.updateCallStatus(callId, "ended", duration)
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
