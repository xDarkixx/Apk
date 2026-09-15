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
    private var cameraSource: VideoSource? = null
    private var cameraHelper: SurfaceTextureHelper? = null
    private var socket: WebSocket? = null
    private var remoteTrack: VideoTrack? = null
    private var stopped = false
    private var iceServers: List<PeerConnection.IceServer> = emptyList()

    fun start() {
        stopped = false
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(egl.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()

        remoteRenderer?.init(egl.eglBaseContext, null)
        remoteRenderer?.setMirror(false)
        connectSignal()
    }

    private fun connectSignal() {
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = try {
            Request.Builder().url(signalingUrl).build()
        } catch (e: IllegalArgumentException) {
            status("Ungültige Signaling-URL: ${e.message ?: "unbekannt"}")
            return
        }

        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send(JSONObject().put("type", "join").put("room", room).toString())
                status("Server verbunden")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    handle(JSONObject(text))
                } catch (e: Exception) {
                    status("Ungültige Servernachricht")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!stopped) status("Signaling-Fehler: ${t.message ?: "unbekannt"}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!stopped) status("Signaling getrennt")
            }
        })
    }

    private fun makePeer() {
        if (peer != null) return

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peer = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(c: IceCandidate) {
                socket?.send(
                    JSONObject()
                        .put("type", "ice")
                        .put("sdpMid", c.sdpMid)
                        .put("sdpMLineIndex", c.sdpMLineIndex)
                        .put("candidate", c.sdp)
                        .toString()
                )
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    remoteTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                    status("LIVE")
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (state != null) status("WebRTC: ${state.name}")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
            override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {}
            override fun onIceCandidateError(event: IceCandidateErrorEvent?) {}
        })

        if (peer == null) status("WebRTC Peer konnte nicht erstellt werden")
    }

    private fun startCamera(): Boolean {
        val source = factory.createVideoSource(false)
        val enumerator = Camera2Enumerator(context)
        val name = enumerator.deviceNames.firstOrNull { !enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
        if (name == null) {
            status("Keine Kamera gefunden")
            source.dispose()
            return false
        }

        val camera = enumerator.createCapturer(name, null)
        if (camera == null) {
            status("Kamera konnte nicht geöffnet werden")
            source.dispose()
            return false
        }

        val helper = SurfaceTextureHelper.create("MatrixCamera", egl.eglBaseContext)
        if (helper == null) {
            camera.dispose()
            source.dispose()
            status("Kamera-Thread konnte nicht erstellt werden")
            return false
        }

        capturer = camera
        cameraSource = source
        cameraHelper = helper
        camera.initialize(helper, context, source.capturerObserver)

        return try {
            camera.startCapture(1280, 720, 24)
            peer?.addTrack(factory.createVideoTrack("matrix-camera", source)) != null
        } catch (e: Exception) {
            status("Kamera-Start fehlgeschlagen: ${e.message ?: "unbekannt"}")
            false
        }
    }

    private fun offer() {
        peer?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                socket?.send(JSONObject().put("type", "offer").put("sdp", sdp.description).toString())
            }

            override fun onCreateFailure(error: String?) {
                status("Offer fehlgeschlagen: ${error ?: "unbekannt"}")
            }
        }, MediaConstraints())
    }

    private fun answer() {
        peer?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peer?.setLocalDescription(SimpleSdpObserver(), sdp)
                socket?.send(JSONObject().put("type", "answer").put("sdp", sdp.description).toString())
            }

            override fun onCreateFailure(error: String?) {
                status("Answer fehlgeschlagen: ${error ?: "unbekannt"}")
            }
        }, MediaConstraints())
    }

    private fun setIceConfiguration(msg: JSONObject) {
        val servers = msg.optJSONObject("ice")?.optJSONArray("iceServers") ?: return
        val result = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until servers.length()) {
            val item = servers.optJSONObject(i) ?: continue
            val urls = item.optJSONArray("urls") ?: continue
            val username = item.optString("username", "")
            val credential = item.optString("credential", "")
            for (j in 0 until urls.length()) {
                val url = urls.optString(j)
                if (url.isBlank()) continue
                val builder = PeerConnection.IceServer.builder(url)
                if (username.isNotEmpty()) builder.setUsername(username)
                if (credential.isNotEmpty()) builder.setPassword(credential)
                result += builder.createIceServer()
            }
        }
        iceServers = result
    }

    private fun handle(msg: JSONObject) {
        when (msg.optString("type")) {
            "joined" -> {
                setIceConfiguration(msg)
                status("Warte auf Gegenstelle …")
            }
            "peer_ready" -> {
                makePeer()
                if (cameraMode && startCamera()) offer()
            }
            "offer" -> {
                if (cameraMode) return
                if (peer == null) makePeer()
                peer?.setRemoteDescription(
                    SimpleSdpObserver { answer() },
                    SessionDescription(SessionDescription.Type.OFFER, msg.getString("sdp"))
                )
            }
            "answer" -> peer?.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(SessionDescription.Type.ANSWER, msg.getString("sdp"))
            )
            "ice" -> {
                val candidate = msg.optString("candidate")
                if (candidate.isNotBlank()) {
                    peer?.addIceCandidate(
                        IceCandidate(
                            msg.optString("sdpMid", "0"),
                            msg.optInt("sdpMLineIndex", 0),
                            candidate
                        )
                    )
                }
            }
            "peer_left" -> status("Gegenstelle getrennt")
            "error" -> status("Server: ${msg.optString("error", "unbekannt")}")
        }
    }

    fun stop() {
        stopped = true
        try { capturer?.stopCapture() } catch (_: Exception) {}
        capturer?.dispose()
        cameraHelper?.dispose()
        cameraSource?.dispose()
        capturer = null
        cameraHelper = null
        cameraSource = null
        remoteTrack?.let { remoteRenderer?.removeSink(it) }
        remoteTrack = null
        peer?.close()
        peer = null
        socket?.close(1000, "stop")
        socket = null
        remoteRenderer?.release()
        if (::factory.isInitialized) factory.dispose()
        egl.release()
    }
}

open class SimpleSdpObserver(private val success: (() -> Unit)? = null) : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() { success?.invoke() }
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
