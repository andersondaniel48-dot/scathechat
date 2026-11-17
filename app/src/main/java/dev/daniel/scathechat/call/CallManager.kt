package dev.daniel.scathechat.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.service.CallService
import dev.daniel.scathechat.service.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

/**
 * Group voice/video calls for up to a handful of people: every phone connects directly to every
 * other phone (a mesh). WebRTC encrypts the media end to end (DTLS-SRTP). The setup messages
 * (offers, answers, network candidates) travel through the group's Firebase database, sealed
 * with the group key, so the database never sees them in the clear either.
 */
object CallManager {
    private const val TAG = "ScatheCall"
    private const val FRESH_MS = 40_000L

    data class Active(val cid: String, val video: Boolean, val startedAt: Long, val startedBy: String)
    data class Incoming(val cid: String, val from: String, val video: Boolean, val at: Long)
    data class Tile(val member: String, val state: String, val video: VideoTrack?)

    val active = MutableStateFlow<Active?>(null)
    val incoming = MutableStateFlow<Incoming?>(null)
    val tiles = MutableStateFlow<List<Tile>>(emptyList())
    val localVideo = MutableStateFlow<VideoTrack?>(null)
    val muted = MutableStateFlow(false)
    val cameraOn = MutableStateFlow(true)
    val speaker = MutableStateFlow(false)
    val minimized = MutableStateFlow(false)
    val answerRequested = MutableStateFlow(false)

    /** Call id -> members currently in that call. */
    val presence = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    @Volatile var egl: EglBase? = null
        private set
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var texHelper: SurfaceTextureHelper? = null

    /** All WebRTC state changes happen on this one thread. */
    private val exec = Executors.newSingleThreadExecutor()

    private class Peer(val id: String, val pc: PeerConnection) {
        var video: VideoTrack? = null
        var state = "connecting"
        var remoteSet = false
        val pending = ArrayList<IceCandidate>()
    }

    private val peers = HashMap<String, Peer>()
    private val earlyIce = HashMap<String, ArrayList<IceCandidate>>()
    private val handled = HashSet<String>()
    private var heartbeat: Job? = null
    private var ringtone: Ringtone? = null

    private val ctx: Context get() = Repo.app
    private val myId: String get() = Repo.me.value?.id ?: ""

    // ------------------------------------------------------------ incoming

    fun onCallMessage(m: Msg) {
        val o = m.data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        val cid = o.optString("cid")
        when (o.optString("a", "start")) {
            "end" -> if (incoming.value?.cid == cid) stopRinging()
            "start" -> {
                if (m.from == myId || active.value != null) return
                if (Repo.now() - m.ts > 45_000) return
                incoming.value = Incoming(cid, m.from, o.optBoolean("v"), m.ts)
                startRinging()
                val name = Repo.members.value[m.from]?.name ?: "Your brother"
                Notifier.incomingCall(ctx, name, o.optBoolean("v"))
                Repo.scope.launch {
                    delay(45_000)
                    if (incoming.value?.cid == cid) stopRinging()
                }
            }
        }
    }

    fun decline() = stopRinging()

    private fun startRinging() {
        try {
            val r = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            r?.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            if (Build.VERSION.SDK_INT >= 28) r?.isLooping = true
            r?.play()
            ringtone = r
            ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 900), 1))
        } catch (e: Exception) {
            Log.w(TAG, "ring", e)
        }
    }

    private fun stopRinging() {
        incoming.value = null
        answerRequested.value = false
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { ctx.getSystemService(Vibrator::class.java)?.cancel() } catch (_: Exception) {}
        Notifier.cancelIncomingCall(ctx)
    }

    // ------------------------------------------------------------ presence & signals (from the state stream)

    fun onStateTree(tree: JSONObject) {
        val now = Repo.now()
        val map = HashMap<String, MutableSet<String>>()
        tree.keys().forEach { id ->
            val k = tree.optJSONObject(id)?.optString("k").orEmpty()
            if (k.isEmpty()) return@forEach
            val p = Repo.openBox(k, Repo.presenceAad(id)) ?: return@forEach
            if (now - p.optLong("at") > FRESH_MS) return@forEach
            map.getOrPut(p.optString("cid")) { HashSet() }.add(id)
        }
        presence.value = map

        val inbox = tree.optJSONObject(myId)?.optJSONObject("q")
        if (inbox != null) {
            val keys = ArrayList<String>()
            inbox.keys().forEach { keys.add(it) }
            for (key in keys.sorted()) {
                if (!handled.add(key)) continue
                Repo.deleteSignal(key)
                val c = inbox.optJSONObject(key)?.optString("c").orEmpty()
                val s = Repo.openBox(c, Repo.signalAad(myId)) ?: continue
                if (now - s.optLong("ts") > 90_000) continue
                onSignal(s)
            }
        }

        val inc = incoming.value
        if (inc != null && now - inc.at > 10_000 && map[inc.cid].isNullOrEmpty()) stopRinging()
        exec.execute { reconcile() }
    }

    private fun onSignal(s: JSONObject) {
        val a = active.value ?: return
        if (s.optString("cid") != a.cid) return
        val from = s.optString("f")
        if (from.isEmpty() || from == myId) return
        exec.execute {
            try {
                when (s.optString("t")) {
                    "offer" -> onOffer(from, s.optString("sdp"))
                    "answer" -> onAnswer(from, s.optString("sdp"))
                    "ice" -> onIce(from, IceCandidate(s.optString("mid"), s.optInt("idx"), s.optString("c")))
                    "bye" -> closePeer(from)
                }
            } catch (e: Exception) {
                Log.w(TAG, "signal", e)
            }
        }
    }

    private fun send(to: String, type: String, fill: (JSONObject) -> Unit = {}) {
        val a = active.value ?: return
        val o = JSONObject().put("t", type).put("f", myId).put("cid", a.cid).put("ts", Repo.now())
        fill(o)
        Repo.sendSignal(to, o)
    }

    // ------------------------------------------------------------ starting / joining / leaving

    fun start(video: Boolean) {
        if (active.value != null) return
        val cid = Crypto.hex(Crypto.random(8))
        begin(cid, video, myId)
        Repo.postData("call", JSONObject().put("cid", cid).put("v", video).put("a", "start"))
    }

    fun accept() {
        val inc = incoming.value ?: return
        stopRinging()
        begin(inc.cid, inc.video, inc.from)
    }

    fun join(cid: String, video: Boolean, startedBy: String) {
        if (active.value != null) return
        stopRinging()
        begin(cid, video, startedBy)
    }

    private fun begin(cid: String, video: Boolean, startedBy: String) {
        active.value = Active(cid, video, System.currentTimeMillis(), startedBy)
        muted.value = false
        cameraOn.value = video
        speaker.value = video
        minimized.value = false
        CallService.start(ctx, video)
        exec.execute {
            try {
                ensureFactory()
                startLocal(video)
                route(true, video)
                publishTiles()
                reconcile()
            } catch (e: Exception) {
                Log.e(TAG, "begin", e)
            }
        }
        heartbeat?.cancel()
        heartbeat = Repo.scope.launch {
            var aloneFor = 0
            while (isActive && active.value?.cid == cid) {
                Repo.setPresence(JSONObject().put("cid", cid).put("v", video).put("at", Repo.now()))
                delay(15_000)
                val others = presence.value[cid].orEmpty() - myId
                aloneFor = if (others.isEmpty()) aloneFor + 1 else 0
                if (aloneFor >= 4) { // about a minute with nobody else here
                    Repo.toast("Nobody else is in the call")
                    hangUp()
                }
            }
        }
    }

    fun hangUp() {
        val a = active.value ?: return
        active.value = null
        heartbeat?.cancel()
        Repo.setPresence(null)
        val others = presence.value[a.cid].orEmpty() - myId
        if (others.isEmpty()) {
            Repo.postData("call", JSONObject().put("cid", a.cid).put("v", a.video).put("a", "end")
                .put("dur", System.currentTimeMillis() - a.startedAt))
        }
        exec.execute {
            for (id in peers.keys.toList()) {
                sendBye(id, a.cid)
                closePeer(id)
            }
            earlyIce.clear()
            stopLocal()
            route(false, false)
            tiles.value = emptyList()
        }
        CallService.stop(ctx)
    }

    private fun sendBye(to: String, cid: String) {
        Repo.sendSignal(to, JSONObject().put("t", "bye").put("f", myId).put("cid", cid).put("ts", Repo.now()))
    }

    // ------------------------------------------------------------ controls

    fun setMuted(m: Boolean) {
        muted.value = m
        exec.execute { audioTrack?.setEnabled(!m) }
    }

    fun setCamera(on: Boolean) {
        cameraOn.value = on
        exec.execute { videoTrack?.setEnabled(on) }
    }

    fun flipCamera() {
        exec.execute { capturer?.switchCamera(null) }
    }

    fun setSpeaker(on: Boolean) {
        speaker.value = on
        exec.execute { route(true, on) }
    }

    // ------------------------------------------------------------ WebRTC plumbing (exec thread only)

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions())
        val e = EglBase.create()
        egl = e
        val adm = JavaAudioDeviceModule.builder(ctx)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(e.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(e.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun startLocal(video: Boolean) {
        val f = factory ?: return
        val constraints = MediaConstraints()
        audioSource = f.createAudioSource(constraints)
        audioTrack = f.createAudioTrack("scathe-audio", audioSource)
        if (!video) return
        val enumerator = Camera2Enumerator(ctx)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull() ?: return
        val cap = enumerator.createCapturer(name, null) ?: return
        val helper = SurfaceTextureHelper.create("scathe-capture", egl!!.eglBaseContext)
        val source = f.createVideoSource(false)
        cap.initialize(helper, ctx, source.capturerObserver)
        cap.startCapture(960, 540, 24)
        capturer = cap
        texHelper = helper
        videoSource = source
        val track = f.createVideoTrack("scathe-video", source)
        videoTrack = track
        localVideo.value = track
    }

    private fun stopLocal() {
        localVideo.value = null
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        capturer = null
        try { videoTrack?.dispose() } catch (_: Exception) {}
        videoTrack = null
        try { videoSource?.dispose() } catch (_: Exception) {}
        videoSource = null
        try { texHelper?.dispose() } catch (_: Exception) {}
        texHelper = null
        try { audioTrack?.dispose() } catch (_: Exception) {}
        audioTrack = null
        try { audioSource?.dispose() } catch (_: Exception) {}
        audioSource = null
    }

    private fun iceServers(): List<PeerConnection.IceServer> {
        val list = ArrayList<PeerConnection.IceServer>()
        list.add(PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")).createIceServer())
        Repo.groupConfig.value?.optJSONObject("turn")?.let { t ->
            val urls = t.optString("urls").split(',', ' ', '\n').map { it.trim() }.filter { it.startsWith("turn:") || it.startsWith("turns:") }
            if (urls.isNotEmpty()) {
                list.add(PeerConnection.IceServer.builder(urls).setUsername(t.optString("u")).setPassword(t.optString("p")).createIceServer())
            }
        }
        return list
    }

    /** Lower member id makes the offer, so two phones never both wait (or both offer) forever. */
    private fun reconcile() {
        val a = active.value ?: return
        if (factory == null) return
        val here = presence.value[a.cid].orEmpty() - myId
        for (id in here) {
            if (!peers.containsKey(id) && myId < id) makeOffer(createPeer(id) ?: continue)
        }
        for (id in peers.keys.toList()) {
            val p = peers[id] ?: continue
            if (id !in here && p.state != "connected") closePeer(id)
        }
        publishTiles()
    }

    private fun createPeer(id: String): Peer? {
        val f = factory ?: return null
        val cfg = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        var self: Peer? = null
        val pc = f.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate?) {
                c ?: return
                send(id, "ice") { it.put("mid", c.sdpMid).put("idx", c.sdpMLineIndex).put("c", c.sdp) }
            }
            override fun onIceCandidatesRemoved(cs: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) exec.execute {
                    self?.video = track
                    publishTiles()
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                exec.execute {
                    val p = self ?: return@execute
                    if (peers[id] !== p) return@execute
                    p.state = when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> "connected"
                        PeerConnection.PeerConnectionState.DISCONNECTED -> "reconnecting"
                        PeerConnection.PeerConnectionState.FAILED -> "failed"
                        else -> p.state
                    }
                    publishTiles()
                    if (newState == PeerConnection.PeerConnectionState.FAILED) {
                        closePeer(id)
                        // Try once more from scratch; the lower id re-offers on the next reconcile.
                        reconcile()
                    }
                }
            }
        }) ?: return null
        val peer = Peer(id, pc)
        self = peer
        audioTrack?.let { pc.addTrack(it, listOf("scathe")) }
        videoTrack?.let { pc.addTrack(it, listOf("scathe")) }
        if (videoTrack == null && active.value?.video == true) {
            pc.addTransceiver(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        }
        peers[id] = peer
        return peer
    }

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) { Log.w(TAG, "sdp create: $error") }
        override fun onSetFailure(error: String?) { Log.w(TAG, "sdp set: $error") }
    }

    private fun makeOffer(p: Peer) {
        p.pc.createOffer(object : Sdp() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                exec.execute {
                    if (peers[p.id] !== p) return@execute
                    p.pc.setLocalDescription(object : Sdp() {
                        override fun onSetSuccess() { send(p.id, "offer") { it.put("sdp", sdp.description) } }
                    }, sdp)
                }
            }
        }, MediaConstraints())
    }

    private fun onOffer(from: String, sdp: String) {
        var p = peers[from]
        if (p != null && p.pc.signalingState() == PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            if (myId < from) return // both offered: mine wins, they will answer it
            closePeer(from)
            p = null
        } else if (p != null && p.remoteSet) {
            closePeer(from) // they restarted: start fresh
            p = null
        }
        val peer = p ?: createPeer(from) ?: return
        peer.pc.setRemoteDescription(object : Sdp() {
            override fun onSetSuccess() {
                exec.execute {
                    if (peers[from] !== peer) return@execute
                    peer.remoteSet = true
                    drainIce(peer)
                    peer.pc.createAnswer(object : Sdp() {
                        override fun onCreateSuccess(answer: SessionDescription?) {
                            answer ?: return
                            exec.execute {
                                peer.pc.setLocalDescription(object : Sdp() {
                                    override fun onSetSuccess() { send(from, "answer") { it.put("sdp", answer.description) } }
                                }, answer)
                            }
                        }
                    }, MediaConstraints())
                }
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
        publishTiles()
    }

    private fun onAnswer(from: String, sdp: String) {
        val p = peers[from] ?: return
        if (p.pc.signalingState() != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) return
        p.pc.setRemoteDescription(object : Sdp() {
            override fun onSetSuccess() {
                exec.execute {
                    p.remoteSet = true
                    drainIce(p)
                }
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun onIce(from: String, c: IceCandidate) {
        val p = peers[from]
        if (p == null) {
            earlyIce.getOrPut(from) { ArrayList() }.add(c)
        } else if (!p.remoteSet) {
            p.pending.add(c)
        } else {
            p.pc.addIceCandidate(c)
        }
    }

    private fun drainIce(p: Peer) {
        earlyIce.remove(p.id)?.forEach { p.pc.addIceCandidate(it) }
        p.pending.forEach { p.pc.addIceCandidate(it) }
        p.pending.clear()
    }

    private fun closePeer(id: String) {
        val p = peers.remove(id) ?: return
        try { p.pc.dispose() } catch (_: Exception) {}
        publishTiles()
    }

    private fun publishTiles() {
        val a = active.value
        val here = if (a == null) emptySet() else presence.value[a.cid].orEmpty() - myId
        val out = ArrayList<Tile>()
        for (p in peers.values) out.add(Tile(p.id, p.state, p.video))
        for (id in here) if (!peers.containsKey(id)) out.add(Tile(id, "connecting", null))
        tiles.value = out.sortedBy { Repo.members.value[it.member]?.name?.lowercase() ?: it.member }
    }

    @Suppress("DEPRECATION")
    private fun route(inCall: Boolean, speakerOn: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            if (!inCall) {
                if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice() else am.isSpeakerphoneOn = false
                am.mode = AudioManager.MODE_NORMAL
                return
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) {
                val devices = am.availableCommunicationDevices
                val headset = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                val want = when {
                    speakerOn -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    headset != null -> headset
                    else -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                }
                want?.let { am.setCommunicationDevice(it) }
            } else {
                am.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "audio route", e)
        }
    }
}
