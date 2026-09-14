package de.xdarkixx.matrixcamera

import android.content.Context
import org.json.JSONObject
import org.webrtc.*
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebRtcEngine(
    private val context: Context,
    private val signalingUrl: String,
    private val room: String,
    private val cameraMode: Boolean,
    private val remoteRenderer: SurfaceViewRenderer?,
    private val status: (String) -> Unit
) {
    private val egl = EglBase.create()
    private lateinit var factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var capturer: CameraVideoCapturer? = null
    private var socket: WebSocket? = null
    private var remoteTrack: VideoTrack? = null

    fun start() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(egl.eglBaseContext)
        factory = PeerConnectionFactory.builder().setVideoEncoderFactory(encoder).setVideoDecoderFactory(decoder).createPeerConnectionFactory()
        remoteRenderer?.init(egl.eglBaseContext, null)
        remoteRenderer?.setMirror(false)
        connectSignal()
    }

    private fun connectSignal() {
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        socket = client.newWebSocket(Request.Builder().url(signalingUrl).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(JSONObject().put("type", "join").put("room", room).toString())
                status("Server verbunden")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                handle(JSONObject(text))
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                status("Signaling-Fehler: ${t.message ?: "unbekannt"}")
            }
        })
    }

    private fun makePeer() {
        val config = PeerConnection.RTCConfiguration(emptyList())
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                socket?.send(JSONObject().put("type", "ice").put("sdpMid", c.sdpMid).put("sdpMLineIndex", c.sdpMLineIndex).put("candidate", c.sdp).toString())
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    remoteTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                    status("LIVE")
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) { status("WebRTC: ${p0?.name}") }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onConnectionChange(p0: PeerConnection.PeerConnectionState?) {}
            override fun onStandardizedIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onSelectedCandidatePairChanged(p0: CandidatePairChangeEvent?) {}
            override fun onIceCandidateError(p0: IceCandidateErrorEvent?) {}
        })
    }

    private fun startCamera() {
        val source = factory.createVideoSource(false)
        val names = Camera2Enumerator(context).deviceNames
        val name = names.firstOrNull { !Camera2Enumerator(context).isFrontFacing(it) } ?: names.firstOrNull()
        if (name == null) { status("Keine Kamera gefunden"); return }
        val enumerator = Camera2Enumerator(context)
        val camera = enumerator.createCapturer(name, null)
        capturer = camera
        val helper = SurfaceTextureHelper.create("MatrixCamera", egl.eglBaseContext)
        camera.initialize(helper, context, source.capturerObserver)
        camera.startCapture(1280, 720, 24)
        peer?.addTrack(factory.createVideoTrack("matrix-camera", source))
    }

    private fun offer() {
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                socket?.send(JSONObject().put("type", "offer").put("sdp", sdp.description).toString())
            }
        }, MediaConstraints())
    }

    private fun answer() {
        peer?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                socket?.send(JSONObject().put("type", "answer").put("sdp", sdp.description).toString())
            }
        }, MediaConstraints())
    }

    private fun handle(msg: JSONObject) {
        when (msg.optString("type")) {
            "peer_ready" -> {
                makePeer()
                if (cameraMode) {
                    startCamera()
                    offer()
                }
            }
            "offer" -> {
                if (cameraMode) return
                if (peer == null) makePeer()
                peer?.setRemoteDescription(SimpleSdpObserver { answer() }, SessionDescription(SessionDescription.Type.OFFER, msg.getString("sdp")))
            }
            "answer" -> peer?.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, msg.getString("sdp")))
            "ice" -> peer?.addIceCandidate(IceCandidate(msg.optString("sdpMid"), msg.getInt("sdpMLineIndex"), msg.getString("candidate")))
            "peer_left" -> status("Gegenstelle getrennt")
            "error" -> status("Server: ${msg.optString("error")}")
        }
    }

    fun stop() {
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        peer?.close()
        socket?.close(1000, "stop")
        remoteRenderer?.release()
        egl.release()
    }
}

open class SimpleSdpObserver(private val success: (() -> Unit)? = null) : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() { success?.invoke() }
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
