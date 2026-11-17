@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.daniel.scathechat.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.daniel.scathechat.call.CallManager
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Shows the call screen or the incoming-call screen on top of everything. */
@Composable
fun CallOverlay(acceptWithPermissions: (Boolean, () -> Unit) -> Unit) {
    val active by CallManager.active.collectAsState()
    val incoming by CallManager.incoming.collectAsState()
    val minimized by CallManager.minimized.collectAsState()
    val answer by CallManager.answerRequested.collectAsState()
    val inc = incoming
    LaunchedEffect(answer, inc) {
        if (answer && inc != null) {
            CallManager.answerRequested.value = false
            acceptWithPermissions(inc.video) { CallManager.accept() }
        }
    }
    when {
        active != null && !minimized -> CallScreen()
        active == null && inc != null -> IncomingScreen(inc, onAccept = { acceptWithPermissions(inc.video) { CallManager.accept() } })
    }
}

@Composable
private fun IncomingScreen(inc: CallManager.Incoming, onAccept: () -> Unit) {
    val members by Repo.members.collectAsState()
    val who = members[inc.from]
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize()) {
            EmberBackground(Modifier.fillMaxSize(), intensity = 1.3f)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(90.dp))
                Box(Modifier.scale(pulse.value).size(132.dp).clip(CircleShape).border(3.dp, C.Flame, CircleShape), contentAlignment = Alignment.Center) {
                    if (who != null) MemberDot(who, 120) else Box(Modifier.size(120.dp).clip(CircleShape).background(C.Faint))
                }
                Spacer(Modifier.height(24.dp))
                Text(who?.name ?: "Your brother", style = fireText(38.sp))
                Spacer(Modifier.height(6.dp))
                Text(if (inc.video) "Incoming video call" else "Incoming voice call", color = C.Bone, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).background(Color(0xFFC8102E)).clickable { CallManager.decline() },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.CallEnd, "Decline", tint = Color.White, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text("Decline", color = C.Smoke, fontSize = 13.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(76.dp).scale(pulse.value).molten(CircleShape).clickable(onClick = onAccept),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (inc.video) Icons.Filled.Videocam else Icons.Filled.Call, "Answer", tint = Color.White, modifier = Modifier.size(34.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text("Answer", color = C.Smoke, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun CallScreen() {
    val active by CallManager.active.collectAsState()
    val a = active ?: return
    val tiles by CallManager.tiles.collectAsState()
    val local by CallManager.localVideo.collectAsState()
    val muted by CallManager.muted.collectAsState()
    val camOn by CallManager.cameraOn.collectAsState()
    val speaker by CallManager.speaker.collectAsState()
    val members by Repo.members.collectAsState()
    val elapsed by produceState(0L, a.cid) {
        while (true) {
            value = System.currentTimeMillis() - a.startedAt
            delay(1000)
        }
    }
    BackHandler { CallManager.minimized.value = true }
    Dialog(onDismissRequest = { CallManager.minimized.value = true }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(C.Char)) {
            EmberBackground(Modifier.fillMaxSize(), intensity = 0.6f)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { CallManager.minimized.value = true }) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = C.Bone) }
                    Column(Modifier.weight(1f)) {
                        Text(if (a.video) "Video call" else "Voice call", color = C.Bone, fontFamily = Display, fontSize = 17.sp)
                        val s = elapsed / 1000
                        Text(
                            (if (tiles.any { it.state == "connected" }) "%d:%02d".format(s / 60, s % 60) else "Waiting for the others…") +
                                " · end-to-end encrypted",
                            color = C.Smoke, fontSize = 12.sp,
                        )
                    }
                }
                // Participants
                Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tiles.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Ringing your brothers…", color = C.Bone, fontSize = 16.sp, textAlign = TextAlign.Center)
                        }
                    } else {
                        val rows = if (tiles.size <= 2) tiles.map { listOf(it) } else tiles.chunked(2)
                        rows.forEach { row ->
                            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { t -> ParticipantTile(t, members[t.member], a.video, Modifier.weight(1f).fillMaxSize()) }
                            }
                        }
                    }
                }
                // Controls
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().glass(RoundedCornerShape(30.dp), fill = Color(0x40140A06)).padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CallButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (muted) "Unmute" else "Mute", on = muted) { CallManager.setMuted(!muted) }
                    if (a.video) {
                        CallButton(if (camOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (camOn) "Camera off" else "Camera on", on = !camOn) { CallManager.setCamera(!camOn) }
                        CallButton(Icons.Filled.Cameraswitch, "Flip camera", on = false) { CallManager.flipCamera() }
                    }
                    CallButton(Icons.AutoMirrored.Filled.VolumeUp, if (speaker) "Speaker on" else "Speaker off", on = speaker) { CallManager.setSpeaker(!speaker) }
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(Color(0xFFC8102E)).clickable { CallManager.hangUp() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.CallEnd, "Hang up", tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
            // My own camera, picture-in-picture
            val lv = local
            if (a.video && lv != null && camOn) {
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 16.dp)
                        .width(104.dp).aspectRatio(0.75f).clip(RoundedCornerShape(16.dp)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(16.dp))
                ) { VideoView(lv, mirror = true, overlay = true, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun ParticipantTile(t: CallManager.Tile, m: Member?, video: Boolean, modifier: Modifier) {
    Box(modifier.glass(RoundedCornerShape(24.dp), fill = Color(0x33140A06)), contentAlignment = Alignment.Center) {
        val v = t.video
        if (video && v != null && t.state == "connected") {
            VideoView(v, mirror = false, overlay = false, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (m != null) MemberDot(m, 84) else Box(Modifier.size(84.dp).clip(CircleShape).background(C.Faint))
                Spacer(Modifier.height(10.dp))
                Text(m?.name ?: "…", color = C.Bone, fontFamily = Display, fontSize = 18.sp)
            }
        }
        Text(
            (m?.name ?: "") + when (t.state) {
                "connected" -> ""
                "reconnecting" -> " · reconnecting"
                "failed" -> " · can't connect"
                else -> " · connecting"
            },
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0x88000000)).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun VideoView(track: VideoTrack, mirror: Boolean, overlay: Boolean, modifier: Modifier) {
    val egl = CallManager.egl ?: return
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            SurfaceViewRenderer(c).apply {
                init(egl.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                if (overlay) setZOrderMediaOverlay(true)
                renderer = this
            }
        },
        onRelease = { it.release() },
    )
    DisposableEffect(track, renderer) {
        val r = renderer
        if (r != null) try { track.addSink(r) } catch (_: Exception) {}
        onDispose { if (r != null) try { track.removeSink(r) } catch (_: Exception) {} }
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).then(if (on) Modifier.molten(CircleShape) else Modifier.glass(CircleShape, fill = Color(0x26FFFFFF))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

/** The card in the chat for a call starting or ending. */
@Composable
fun CallChatCard(m: Msg, members: Map<String, Member>, myId: String, onJoin: (String, Boolean, String) -> Unit) {
    val o = remember(m.data) { m.data?.let { runCatching { JSONObject(it) }.getOrNull() } } ?: return
    val presence by CallManager.presence.collectAsState()
    val active by CallManager.active.collectAsState()
    val cid = o.optString("cid")
    val video = o.optBoolean("v")
    val ended = o.optString("a") == "end"
    val live = !ended && presence[cid].orEmpty().isNotEmpty()
    val who = if (m.from == myId) "You" else members[m.from]?.name ?: "Someone"
    val line = when {
        ended -> {
            val d = o.optLong("dur") / 1000
            "Call ended" + if (d > 5) " · %d:%02d".format(d / 60, d % 60) else ""
        }
        else -> "$who started a ${if (video) "video" else "voice"} call"
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 320.dp).glass(RoundedCornerShape(18.dp), fill = if (live) C.Ember.copy(alpha = 0.14f) else Color(0x33140A06), rim = if (live) 1.5f else 1f)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (video) Icons.Filled.Videocam else Icons.Filled.Call, null, tint = if (live) C.Flame else C.Smoke)
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(line, color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(timeOf(m.ts) + if (live) " · ${presence[cid].orEmpty().size} in the call" else "", color = C.Smoke, fontSize = 12.sp)
            }
            if (live && active == null) {
                FireButton(onClick = { onJoin(cid, video, m.from) }, shape = RoundedCornerShape(14.dp), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    ButtonLabel("Join", 14)
                }
            }
        }
    }
}

/** Optional TURN relay for the whole group, for when phones can't reach each other directly. */
@Composable
fun RelaySheet(onDismiss: () -> Unit) {
    val cfg by Repo.groupConfig.collectAsState()
    val turn = cfg?.optJSONObject("turn")
    var urls by remember { mutableStateOf(turn?.optString("urls") ?: "") }
    var user by remember { mutableStateOf(turn?.optString("u") ?: "") }
    var pass by remember { mutableStateOf(turn?.optString("p") ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Relay server", style = fireText(24.sp, glow = 0.5f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Most calls connect phone to phone. Some networks (often two phones on cellular data) need a relay in the middle. " +
                    "Get free relay details from a TURN provider such as Open Relay (metered.ca) or ExpressTURN, and paste them here once. " +
                    "They're shared with the group, encrypted. The relay only passes along call audio and video, which stays end-to-end encrypted.",
                color = C.Smoke, fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(urls, { urls = it }, label = { Text("TURN URLs") }, placeholder = { Text("turn:relay.example.com:3478, turns:relay.example.com:443") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            FireButton(onClick = {
                val next = JSONObject(cfg?.toString() ?: "{}")
                if (urls.isBlank()) next.remove("turn")
                else next.put("turn", JSONObject().put("urls", urls.trim()).put("u", user.trim()).put("p", pass.trim()))
                Repo.postData("cfg", next)
                onDismiss()
            }, modifier = Modifier.fillMaxWidth()) { ButtonLabel(if (urls.isBlank()) "Save (no relay)" else "Save relay for the group") }
        }
    }
}
