@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.daniel.scathechat.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.daniel.scathechat.BuildConfig
import dev.daniel.scathechat.data.Group
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import dev.daniel.scathechat.net.Updater
import dev.daniel.scathechat.service.ChatService
import kotlinx.coroutines.launch

val REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "💀")

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = fireText(22.sp, glow = 0.5f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
fun InviteSheet(group: Group, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val link = remember(group) { group.inviteLink() }
    val qr = remember(link) { qrBitmap(link).asImageBitmap() }
    var confirmShare by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetTitle("Invite a brother")
            Text(
                "On their phone: install Scathe Chat → Join with an invite → Scan invite QR.",
                color = C.Dim, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                Image(qr, "Invite QR code", filterQuality = FilterQuality.None, modifier = Modifier.size(250.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "This code is the key to the whole chat. Anyone who has it can read every message, so only show it to people you trust.",
                color = C.Crimson.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { copyText(ctx, "Scathe Chat invite", group.inviteCode()) }) {
                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy code", color = C.Text)
                }
                OutlinedButton(onClick = { confirmShare = true }) {
                    Icon(Icons.Filled.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Send code", color = C.Text)
                }
            }
        }
    }
    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Send the invite code?") },
            text = { Text("Scanning in person is safest. If you send it, use a private 1-on-1 chat and delete it afterwards. They paste it under Join → “paste the invite code”.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Scathe Chat invite (paste it in the app under Join):\n${group.inviteCode()}")
                    ctx.startActivity(Intent.createChooser(send, "Send invite"))
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel", color = C.Dim) } },
        )
    }
}

@Composable
fun MembersSheet(members: Map<String, Member>, myId: String, now: Long, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Members · ${members.size}")
            Text(
                "Everyone who has joined with the invite. If you ever see someone you don't know, make a new group.",
                color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            members.values.sortedWith(compareBy({ it.id != myId }, { it.name.lowercase() })).forEach { m ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CallingCard(
                        m.card, m.name + if (m.id == myId) " (you)" else "",
                        if (m.id == myId) "this phone" else lastSeen(m, now), m.emblem, m.emblemColor, Modifier.fillMaxWidth(),
                    )
                    Column(Modifier.padding(start = 4.dp, top = 4.dp)) {
                        when {
                            m.keyChanged -> Text("⚠ Security key changed. New messages under this name may not really be from ${m.name}.", color = Color(0xFFFFB4A0), fontSize = 12.sp)
                            m.pub != null -> Text("🔒 Messages signed by this phone", color = C.Faint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by Repo.me.collectAsState()
    val group by Repo.group.collectAsState()
    var name by remember { mutableStateOf(me?.name ?: "") }
    var color by remember { mutableIntStateOf(me?.color ?: 0) }
    var bg by remember { mutableStateOf(Repo.prefs.background) }
    var confirmLeave by remember { mutableStateOf(false) }
    var codSetup by remember { mutableStateOf(false) }
    var pushSetup by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Settings", style = fireText(22.sp, glow = 0.5f))
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            ColorPicker(color) { color = it }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                Repo.updateMe(name, color)
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
            }, enabled = name.isNotBlank()) { Text("Save name & color") }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(14.dp))
            Text("Push notifications", color = C.Text, fontSize = 16.sp)
            Text(
                if (dev.daniel.scathechat.net.Push.configured()) "Set up for the group. Phones wake each other through Google; no message content is sent."
                else "Optional: lets messages and calls get through even when Android puts the app to sleep.",
                color = C.Dim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            GlassButton(onClick = { pushSetup = true }) { ButtonLabel("Push setup", 15) }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(14.dp))
            StorageSection()

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(14.dp))
            Text("Calls", color = C.Text, fontSize = 16.sp)
            Text(
                if (Repo.groupConfig.value?.optJSONObject("turn") != null) "Relay server set for the whole group."
                else "Calls connect directly. If calls between phones on cellular data won't connect, add a free relay server.",
                color = C.Dim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            GlassButton(onClick = { codSetup = true }) { ButtonLabel("Relay server", 15) }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Instant notifications", color = C.Text, fontSize = 16.sp)
                    Text("Stay connected in the background so messages arrive right away.", color = C.Dim, fontSize = 13.sp)
                }
                Switch(
                    checked = bg,
                    onCheckedChange = {
                        bg = it; Repo.prefs.background = it
                        if (it) ChatService.start(ctx) else ChatService.stop(ctx)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Crimson),
                )
            }
            if (!ignoringBattery(ctx)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { requestIgnoreBattery(ctx) }) { Text("Allow running in background", color = C.Text) }
                Text("Without this, Android may delay messages while the screen is off.", color = C.Faint, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(14.dp))
            Text("Scathe Chat ${BuildConfig.VERSION_NAME}", color = C.Text, fontSize = 16.sp)
            Text(
                "End-to-end encrypted with AES-256-GCM. Your Firebase database only stores ciphertext; the key lives in the invite and on your phones.",
                color = C.Dim, fontSize = 13.sp,
            )
            group?.let { Text("Server: ${it.dbUrl.removePrefix("https://")}", color = C.Faint, fontSize = 12.sp) }
            Spacer(Modifier.height(8.dp))
            var auto by remember { mutableStateOf(Repo.prefs.autoUpdate) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic updates", color = C.Text, fontSize = 16.sp)
                    Text("Downloads new versions and installs them while you're not using the app.", color = C.Dim, fontSize = 13.sp)
                }
                Switch(checked = auto, onCheckedChange = { auto = it; Repo.prefs.autoUpdate = it }, colors = SwitchDefaults.colors(checkedTrackColor = C.Crimson))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val has = Updater.check(force = true)
                    Toast.makeText(ctx, if (has) "Update available — see the banner" else "You're on the latest version", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Check for updates", color = C.Text) }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = C.Field); Spacer(Modifier.height(10.dp))
            TextButton(onClick = { confirmLeave = true }) { Text("Remove this group from this phone", color = C.Crimson) }
        }
    }
    if (codSetup) RelaySheet { codSetup = false }
    if (pushSetup) PushSheet { pushSetup = false }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Remove the group from this phone?") },
            text = { Text("This deletes the messages, media and key stored on this phone. Your brothers keep theirs. You'll need the invite again to rejoin.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onDismiss(); Repo.leave() }) { Text("Remove", color = C.Crimson) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = C.Dim) } },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text)
        Spacer(Modifier.width(18.dp))
        Text(label, color = C.Text, fontSize = 16.sp)
    }
}

@Composable
fun ActionsSheet(m: Msg, mine: Boolean, onDismiss: () -> Unit, onReact: (String) -> Unit, onReply: () -> Unit, onKillcam: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.chat.collectAsState()
    val myPick = Repo.me.value?.id?.let { state.reactions[m.id]?.get(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                REACTIONS.forEach { e ->
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(if (e == myPick) Color(0xFF4A1C20) else C.Field).clickable { onReact(e) },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            Spacer(Modifier.height(6.dp))
            ActionRow(Icons.AutoMirrored.Filled.Reply, "Reply", onReply)
            ActionRow(Icons.Filled.Videocam, "Killcam reply", onKillcam)
            val text = m.text
            if (!text.isNullOrBlank()) ActionRow(Icons.Filled.ContentCopy, "Copy text") { copyText(ctx, "Message", text); onDismiss() }
            m.link?.let { l -> ActionRow(Icons.AutoMirrored.Filled.OpenInNew, "Open link") { openUrl(ctx, l.url); onDismiss() } }
            m.media?.let { media ->
                ActionRow(Icons.Filled.Download, if (media.kind == "file") "Save to Downloads" else "Save to gallery") {
                    scope.launch {
                        Repo.ensureMedia(media)?.let { MediaUtil.saveToGallery(ctx, it, media) }
                            ?: Toast.makeText(ctx, "Couldn't download it", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
                ActionRow(Icons.Filled.Share, "Share") {
                    scope.launch {
                        Repo.ensureMedia(media)?.let { MediaUtil.share(ctx, it, media) }
                        onDismiss()
                    }
                }
            }
            if (mine && m.status == Msg.FAILED) ActionRow(Icons.Filled.Refresh, "Retry sending") { Repo.retry(m); onDismiss() }
        }
    }
}
