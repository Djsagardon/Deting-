package com.example.data.rtc

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class RtcConnectionState {
    data object Idle : RtcConnectionState()
    data object OutgoingRinging : RtcConnectionState()
    data object IncomingRinging : RtcConnectionState()
    data class Connected(val channelName: String, val isVideo: Boolean) : RtcConnectionState()
    data class Ended(val reason: String, val durationSeconds: Int) : RtcConnectionState()
}

class AgoraCallManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var durationJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _connectionState = MutableStateFlow<RtcConnectionState>(RtcConnectionState.Idle)
    val connectionState: StateFlow<RtcConnectionState> = _connectionState.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    fun startOutgoingCall(channelName: String, isVideo: Boolean) {
        _callDurationSeconds.value = 0
        _isMicMuted.value = false
        _isCameraEnabled.value = isVideo
        _isFrontCamera.value = true
        _isSpeakerOn.value = true
        _connectionState.value = RtcConnectionState.OutgoingRinging
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun onCallAccepted(channelName: String, isVideo: Boolean) {
        _isCameraEnabled.value = isVideo
        _connectionState.value = RtcConnectionState.Connected(channelName, isVideo)
        startTimer()
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        }
    }

    fun onCallDeclined() {
        stopTimer()
        val dur = _callDurationSeconds.value
        _connectionState.value = RtcConnectionState.Ended("Declined by user", dur)
        audioManager?.mode = AudioManager.MODE_NORMAL
    }

    fun onCallBusy() {
        stopTimer()
        val dur = _callDurationSeconds.value
        _connectionState.value = RtcConnectionState.Ended("User busy", dur)
        audioManager?.mode = AudioManager.MODE_NORMAL
    }

    fun endCall(reason: String = "Call ended") {
        stopTimer()
        val dur = _callDurationSeconds.value
        _connectionState.value = RtcConnectionState.Ended(reason, dur)
        audioManager?.let { am ->
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    fun reset() {
        stopTimer()
        _connectionState.value = RtcConnectionState.Idle
        _callDurationSeconds.value = 0
        _isMicMuted.value = false
        _isCameraEnabled.value = true
        _isFrontCamera.value = true
        audioManager?.mode = AudioManager.MODE_NORMAL
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
        audioManager?.isMicrophoneMute = _isMicMuted.value
    }

    fun toggleCamera() {
        _isCameraEnabled.value = !_isCameraEnabled.value
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        audioManager?.isSpeakerphoneOn = newState
    }

    private fun startTimer() {
        durationJob?.cancel()
        _callDurationSeconds.value = 0
        durationJob = scope.launch {
            while (isActive) {
                delay(1000L)
                _callDurationSeconds.value += 1
            }
        }
    }

    private fun stopTimer() {
        durationJob?.cancel()
        durationJob = null
    }
}
