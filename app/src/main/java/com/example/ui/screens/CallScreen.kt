package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallSession
import com.example.data.repository.BondlyRepository
import com.example.data.rtc.AgoraCallManager
import com.example.ui.components.ReportAndBlockSheet
import com.example.ui.theme.BondlyGradient
import com.example.ui.theme.BondlyPink
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callId: String,
    targetUid: String,
    targetName: String,
    isVideo: Boolean,
    isIncoming: Boolean,
    repository: BondlyRepository,
    onCallFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val callManager = remember { AgoraCallManager(context) }

    val callFlow = remember(callId) { repository.observeCall(callId) }
    val callSession by callFlow.collectAsState(initial = null)

    val isMicMuted by callManager.isMicMuted.collectAsState()
    val isCameraEnabled by callManager.isCameraEnabled.collectAsState()
    val isFrontCamera by callManager.isFrontCamera.collectAsState()
    val isSpeakerOn by callManager.isSpeakerOn.collectAsState()
    val callDuration by callManager.callDurationSeconds.collectAsState()

    var showReportSheet by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ringing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Call state lifecycle sync
    LaunchedEffect(callSession?.status) {
        val status = callSession?.status
        if (status == "accepted") {
            callManager.onCallAccepted(callSession?.channelName ?: "", isVideo)
        } else if (status in listOf("declined", "ended", "busy", "failed")) {
            callManager.endCall(status ?: "ended")
            delay(1200L)
            onCallFinished()
        }
    }

    LaunchedEffect(Unit) {
        if (!isIncoming) {
            callManager.startOutgoingCall("channel_$callId", isVideo)
        }
    }

    val isConnected = callSession?.status == "accepted"

    val durationText = remember(callDuration) {
        val mins = callDuration / 60
        val secs = callDuration % 60
        String.format("%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .testTag("call_screen")
    ) {
        // Video Preview / Ambient Background
        if (isVideo && isConnected && isCameraEnabled) {
            // Live Video Feed Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF231C30), Color(0xFF14101E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(BondlyGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetName.take(1).uppercase(),
                            color = TextWhite,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "HD Video Connected",
                        color = TextLightGrey,
                        fontSize = 14.sp
                    )
                }

                // PiP Local Camera Preview Box (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 20.dp)
                        .size(100.dp, 140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CharcoalCard)
                        .border(1.5.dp, BondlyPink, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFrontCamera) "Front Cam" else "Back Cam",
                        color = TextLightGrey,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            // Audio Call Ambient Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2E193C), CharcoalDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .let { if (!isConnected) it.scale(pulseScale) else it }
                            .clip(CircleShape)
                            .background(BondlyGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = targetName.take(1).uppercase(),
                            color = TextWhite,
                            fontSize = 60.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = targetName,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isConnected) durationText else if (isIncoming) "Incoming call..." else "Calling...",
                        color = if (isConnected) BondlyPink else TextMuted,
                        fontSize = if (isConnected) 18.sp else 15.sp,
                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Top Status Bar (Duration & Info)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isVideo) "HD Video Call" else "HD Audio Call",
                    color = TextLightGrey,
                    fontSize = 13.sp
                )
                if (isConnected) {
                    Text(
                        text = durationText,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33FFFFFF))
                    .clickable { showReportSheet = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = "Safety", color = TextWhite, fontSize = 12.sp)
            }
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC0F0E17), CharcoalDark)
                    )
                )
                .padding(bottom = 44.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Mute Toggle
                IconButton(
                    onClick = { callManager.toggleMic() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isMicMuted) Color(0xFFFF334B) else CharcoalCard)
                        .border(1.dp, CharcoalBorder, CircleShape)
                        .testTag("call_mic_toggle")
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Mic",
                        tint = TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isVideo) {
                    // Camera Enable/Disable
                    IconButton(
                        onClick = { callManager.toggleCamera() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (!isCameraEnabled) Color(0xFFFF334B) else CharcoalCard)
                            .border(1.dp, CharcoalBorder, CircleShape)
                            .testTag("call_camera_toggle")
                    ) {
                        Icon(
                            imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Video",
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Flip Camera (Front / Back)
                    IconButton(
                        onClick = { callManager.switchCamera() },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(CharcoalCard)
                            .border(1.dp, CharcoalBorder, CircleShape)
                            .testTag("call_flip_camera")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Camera",
                            tint = TextWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Speakerphone Toggle
                IconButton(
                    onClick = { callManager.toggleSpeaker() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) Color(0x33FF2D75) else CharcoalCard)
                        .border(1.dp, if (isSpeakerOn) BondlyPink else CharcoalBorder, CircleShape)
                        .testTag("call_speaker_toggle")
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Speaker",
                        tint = if (isSpeakerOn) BondlyPink else TextWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // End Call Button (Big Red)
                IconButton(
                    onClick = {
                        scope.launch {
                            repository.updateCallStatus(callId, "ended", callDuration)
                            callManager.endCall()
                            onCallFinished()
                        }
                    },
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF334B))
                        .testTag("call_end_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = TextWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        if (showReportSheet) {
            ReportAndBlockSheet(
                targetUserName = targetName,
                targetUserId = targetUid,
                onDismiss = { showReportSheet = false },
                onReport = { reason, details ->
                    scope.launch {
                        repository.reportUser(targetUid, reason, details)
                    }
                },
                onBlock = {
                    scope.launch {
                        repository.blockUser(targetUid)
                        repository.updateCallStatus(callId, "ended", callDuration)
                        callManager.endCall()
                        onCallFinished()
                    }
                }
            )
        }
    }
}
