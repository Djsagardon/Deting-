package com.example.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

sealed class CallConnectionState {
    data object Idle : CallConnectionState()
    data object OutgoingRinging : CallConnectionState()
    data object IncomingRinging : CallConnectionState()
    data object Connecting : CallConnectionState()
    data class Connected(val isVideo: Boolean) : CallConnectionState()
    data class Failed(val message: String) : CallConnectionState()
    data class Ended(val reason: String, val durationSeconds: Int) : CallConnectionState()
}

class WebRtcCallManager(
    private val context: Context,
    private val onSendOffer: (String) -> Unit,
    private val onSendAnswer: (String) -> Unit,
    private val onSendIceCandidate: (sdpMid: String, sdpMLineIndex: Int, sdp: String) -> Unit,
    private val onCallEndedCallback: (reason: String, duration: Int) -> Unit
) {
    companion object {
        private const val TAG = "WebRtcCallManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var durationJob: Job? = null
    private var ringbackJob: Job? = null

    val eglBase: EglBase by lazy { EglBase.create() }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var remoteVideoTrack: VideoTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var toneGenerator: ToneGenerator? = null
    private var ringtone: Ringtone? = null

    private var isVideoCall: Boolean = false
    private var isCaller: Boolean = false
    private var isRemoteDescriptionSet: Boolean = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private val _connectionState = MutableStateFlow<CallConnectionState>(CallConnectionState.Idle)
    val connectionState: StateFlow<CallConnectionState> = _connectionState.asStateFlow()

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

    fun initWebRtc(isVideo: Boolean, isCaller: Boolean) {
        this.isVideoCall = isVideo
        this.isCaller = isCaller
        _callDurationSeconds.value = 0
        _isMicMuted.value = false
        _isCameraEnabled.value = isVideo
        _isFrontCamera.value = true
        _isSpeakerOn.value = isVideo // Default speaker for video, earpiece/speaker for audio
        isRemoteDescriptionSet = false
        pendingRemoteCandidates.clear()

        buildPeerConnectionFactory()
        createLocalTracks(isVideo)
        createPeerConnection()

        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = _isSpeakerOn.value

        if (isCaller) {
            _connectionState.value = CallConnectionState.OutgoingRinging
            playOutgoingRingback()
        } else {
            _connectionState.value = CallConnectionState.IncomingRinging
            playIncomingRingtone()
        }
    }

    private fun buildPeerConnectionFactory() {
        if (peerConnectionFactory != null) return

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val adm = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createLocalTracks(isVideo: Boolean) {
        val factory = peerConnectionFactory ?: return

        // Audio Track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("bondly_audio_track", audioSource).apply {
            setEnabled(true)
        }

        // Video Track
        if (isVideo) {
            try {
                videoCapturer = createCameraCapturer()
                if (videoCapturer != null) {
                    surfaceTextureHelper = SurfaceTextureHelper.create("BondlyCaptureThread", eglBase.eglBaseContext)
                    videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)
                    videoCapturer!!.initialize(surfaceTextureHelper, context.applicationContext, videoSource!!.capturerObserver)
                    videoCapturer!!.startCapture(640, 480, 30)

                    localVideoTrack = factory.createVideoTrack("bondly_video_track", videoSource).apply {
                        setEnabled(true)
                    }

                    // Attach to local renderer if already initialized
                    localRenderer?.let {
                        localVideoTrack?.addSink(it)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating video capture: ${e.message}", e)
            }
        }
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        // Prefer front camera first
        val deviceNames = enumerator.deviceNames
        for (name in deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        // Fallback to back camera
        for (name in deviceNames) {
            if (!enumerator.isFrontFacing(name)) {
                val capturer = enumerator.createCapturer(name, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "Generated ICE Candidate: sdpMid=${candidate.sdpMid}, index=${candidate.sdpMLineIndex}")
                    scope.launch {
                        onSendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $newState")
                scope.launch {
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            stopRingtones()
                            _connectionState.value = CallConnectionState.Connected(isVideoCall)
                            startTimer()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            stopRingtones()
                            stopTimer()
                            _connectionState.value = CallConnectionState.Failed("Connection failed (P2P STUN path unreachable)")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            Log.w(TAG, "ICE connection disconnected")
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            endCall("Call closed")
                        }
                        else -> {}
                    }
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "PeerConnection State: $newState")
                scope.launch {
                    if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
                        stopRingtones()
                        _connectionState.value = CallConnectionState.Connected(isVideoCall)
                        startTimer()
                    } else if (newState == PeerConnection.PeerConnectionState.FAILED) {
                        _connectionState.value = CallConnectionState.Failed("Direct connection failed")
                    }
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "Received track: ${track?.kind()}")
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    scope.launch {
                        remoteRenderer?.let {
                            remoteVideoTrack?.addSink(it)
                        }
                    }
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }

        peerConnection = factory.createPeerConnection(rtcConfig, observer)

        // Add local tracks
        localAudioTrack?.let {
            peerConnection?.addTrack(it, listOf("bondly_stream"))
        }
        localVideoTrack?.let {
            peerConnection?.addTrack(it, listOf("bondly_stream"))
        }
    }

    fun startOutgoingCall() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    Log.d(TAG, "Offer created successfully")
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Set local description (offer) successful")
                            scope.launch {
                                onSendOffer(desc.description)
                            }
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Set local description failure: $err")
                        }
                    }, desc)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Create offer failure: $err")
                _connectionState.value = CallConnectionState.Failed("Failed to create call offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    fun answerIncomingCall(offerSdp: String) {
        val pc = peerConnection ?: return
        stopRingtones()
        _connectionState.value = CallConnectionState.Connecting

        val offerDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Set remote description (offer) successful. Creating answer...")
                isRemoteDescriptionSet = true
                drainPendingCandidates()

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
                }

                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc != null) {
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Set local description (answer) successful")
                                    scope.launch {
                                        onSendAnswer(answerDesc.description)
                                    }
                                }
                                override fun onCreateFailure(err: String?) {}
                                override fun onSetFailure(err: String?) {
                                    Log.e(TAG, "Set local description (answer) failure: $err")
                                }
                            }, answerDesc)
                        }
                    }

                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        Log.e(TAG, "Create answer failure: $err")
                    }
                    override fun onSetFailure(err: String?) {}
                }, constraints)
            }

            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Set remote description failure: $err")
                _connectionState.value = CallConnectionState.Failed("Failed to process incoming offer: $err")
            }
        }, offerDesc)
    }

    fun onRemoteAnswerReceived(answerSdp: String) {
        val pc = peerConnection ?: return
        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Set remote description (answer) successful")
                isRemoteDescriptionSet = true
                drainPendingCandidates()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Set remote answer description failure: $err")
            }
        }, answerDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        if (isRemoteDescriptionSet && peerConnection != null) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingRemoteCandidates.add(candidate)
        }
    }

    private fun drainPendingCandidates() {
        val pc = peerConnection ?: return
        for (cand in pendingRemoteCandidates) {
            pc.addIceCandidate(cand)
        }
        pendingRemoteCandidates.clear()
    }

    fun setLocalRenderer(renderer: SurfaceViewRenderer?) {
        localRenderer = renderer
        if (renderer != null && localVideoTrack != null) {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(true)
            localVideoTrack?.addSink(renderer)
        }
    }

    fun setRemoteRenderer(renderer: SurfaceViewRenderer?) {
        remoteRenderer = renderer
        if (renderer != null) {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(false)
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun toggleMic() {
        val nextMuted = !_isMicMuted.value
        _isMicMuted.value = nextMuted
        localAudioTrack?.setEnabled(!nextMuted)
        audioManager?.isMicrophoneMute = nextMuted
    }

    fun toggleCamera() {
        val next = !_isCameraEnabled.value
        _isCameraEnabled.value = next
        localVideoTrack?.setEnabled(next)
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun toggleSpeaker() {
        val newState = !_isSpeakerOn.value
        _isSpeakerOn.value = newState
        audioManager?.isSpeakerphoneOn = newState
    }

    private fun playOutgoingRingback() {
        stopRingtones()
        ringbackJob = scope.launch(Dispatchers.IO) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
                while (isActive && _connectionState.value is CallConnectionState.OutgoingRinging) {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 2000)
                    delay(4000L)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator error: ${e.message}")
            }
        }
    }

    private fun playIncomingRingtone() {
        stopRingtones()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri).apply {
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Incoming ringtone error: ${e.message}")
        }
    }

    fun stopRingtones() {
        ringbackJob?.cancel()
        ringbackJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
        try {
            ringtone?.stop()
            ringtone = null
        } catch (_: Exception) {}
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

    fun endCall(reason: String = "Call ended") {
        stopRingtones()
        stopTimer()
        val duration = _callDurationSeconds.value
        _connectionState.value = CallConnectionState.Ended(reason, duration)
        cleanupWebRtc()
        onCallEndedCallback(reason, duration)
    }

    private fun cleanupWebRtc() {
        try {
            localRenderer?.release()
            remoteRenderer?.release()
            localRenderer = null
            remoteRenderer = null

            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            videoCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            localVideoTrack?.dispose()
            localVideoTrack = null

            videoSource?.dispose()
            videoSource = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            audioSource?.dispose()
            audioSource = null

            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning WebRTC: ${e.message}")
        }

        audioManager?.isSpeakerphoneOn = false
        audioManager?.mode = AudioManager.MODE_NORMAL
    }

    fun release() {
        stopRingtones()
        stopTimer()
        cleanupWebRtc()
        try {
            eglBase.release()
        } catch (_: Exception) {}
        _connectionState.value = CallConnectionState.Idle
    }
}
