package com.example.data.rtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class RtcConnectionState {
    object Idle : RtcConnectionState()
    object OutgoingRinging : RtcConnectionState()
    object IncomingRinging : RtcConnectionState()
    object Connecting : RtcConnectionState()
    data class Connected(val channelName: String, val isVideo: Boolean) : RtcConnectionState()
    data class Ended(val reason: String, val durationSeconds: Int) : RtcConnectionState()
    data class Error(val message: String) : RtcConnectionState()
}

class AgoraCallManager(private val context: Context) {

    private val _connectionState = MutableStateFlow<RtcConnectionState>(RtcConnectionState.Idle)
    val connectionState = _connectionState.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted = _isMicMuted.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled = _isCameraEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera = _isFrontCamera.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn = _isSpeakerOn.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds = _callDurationSeconds.asStateFlow()

    private var durationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun startOutgoingCall(channelName: String, isVideo: Boolean) {
        _callDurationSeconds.value = 0
        _isMicMuted.value = false
        _isCameraEnabled.value = isVideo
        _isSpeakerOn.value = true
        _connectionState.value = RtcConnectionState.OutgoingRinging
    }

    fun onCallAccepted(channelName: String, isVideo: Boolean) {
        _connectionState.value = RtcConnectionState.Connected(channelName, isVideo)
        startTimer()
        audioManager?.isSpeakerphoneOn = true
    }

    fun onCallDeclined() {
        stopTimer()
        _connectionState.value = RtcConnectionState.Ended("Declined by user", _callDurationSeconds.value)
    }

    fun onCallBusy() {
        stopTimer()
        _connectionState.value = RtcConnectionState.Ended("User busy", _callDurationSeconds.value)
    }

    fun endCall(reason: String = "Call ended") {
        stopTimer()
        val duration = _callDurationSeconds.value
        _connectionState.value = RtcConnectionState.Ended(reason, duration)
        audioManager?.isSpeakerphoneOn = false
    }

    fun reset() {
        stopTimer()
        _connectionState.value = RtcConnectionState.Idle
        _callDurationSeconds.value = 0
    }

    fun toggleMic() {
        _isMicMuted.value = !_isMicMuted.value
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
