@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.daniel.scathechat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dev.daniel.scathechat.R
import dev.daniel.scathechat.data.Conn
import dev.daniel.scathechat.data.LinkInfo
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import dev.daniel.scathechat.net.LinkPreview
import dev.daniel.scathechat.net.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed class Row {
    abstract val key: String

    class Day(override val key: String, val label: String) : Row()
    class Bubble(val m: Msg, val first: Boolean, val last: Boolean) : Row() {
        override val key: String get() = m.id
    }
}

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when {
        d == today -> "Today"
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        else -> d.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

/** Newest first (the list is drawn bottom-up). */
private fun buildRows(msgs: List<Msg>): List<Row> {
    val out = ArrayList<Row>(msgs.size + 8)
    val gap = 5 * 60_000L
    for (i in msgs.indices.reversed()) {
        val m = msgs[i]
        val prev = msgs.getOrNull(i - 1)
        val next = msgs.getOrNull(i + 1)
        val d = dayOf(m.ts)
        val newDay = prev == null || dayOf(prev.ts) != d
        val first = newDay || prev!!.from != m.from || m.ts - prev.ts > gap
        val last = next == null || next.from != m.from || next.ts - m.ts > gap || dayOf(next.ts) != d
        out.add(Row.Bubble(m, first, last))
        if (newDay) out.add(Row.Day("day-$d", dayLabel(d)))
    }
    return out
}

private enum class Sheet { INVITE, MEMBERS, SETTINGS }

@Composable
fun ChatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.chat.collectAsState()
    val members by Repo.members.collectAsState()
    val me by Repo.me.collectAsState()
    val group by Repo.group.collectAsState()
    val conn by Repo.conn.collectAsState()
    val connError by Repo.connError.collectAsState()
    val showInvite by Repo.showInvite.collectAsState()
    val shares by Repo.pendingShare.collectAsState()
    val update by Updater.available.collectAsState()
    val fg by Repo.fg.collectAsState()
    val myId = me?.id ?: return

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Msg?>(null) }
    var viewer by remember { mutableStateOf<Msg?>(null) }
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    var askBattery by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val rows = remember(state.messages) { buildRows(state.messages) }
    val now by produceState(Repo.now()) {
        while (true) {
            delay(30_000); value = Repo.now()
        }
    }

    LaunchedEffect(showInvite) {
        if (showInvite) {
            sheet = Sheet.INVITE; Repo.showInvite.value = false
        }
    }
    LaunchedEffect(Unit) {
        delay(2500)
        Updater.check()
    }

    // The chat counts as "being read" while it's on screen and scrolled to the newest message.
    DisposableEffect(Unit) {
        Repo.viewing = true
        onDispose { Repo.viewing = false }
    }
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    LaunchedEffect(atBottom, state.messages.size, fg) {
        if (atBottom && fg) Repo.markRead()
    }
    // Follow new messages when already at the bottom, or when I send one.
    val newest = state.messages.lastOrNull()
    LaunchedEffect(newest?.id) {
        if (newest != null && (listState.firstVisibleItemIndex <= 2 || newest.from == myId)) listState.animateScrollToItem(0)
    }

    // Notifications permission (Android 13+), then battery optimisation, once each.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!Repo.prefs.askedBattery) askBattery = true
    }
    LaunchedEffect(Unit) {
        delay(800)
        val needNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needNotif && !Repo.prefs.askedNotif) {
            Repo.prefs.askedNotif = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!Repo.prefs.askedBattery && !ignoringBattery(ctx)) {
            askBattery = true
        }
    }

    // Attachments
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uris.forEach { Repo.sendMedia(it, replyTo?.id) }
        if (uris.isNotEmpty()) replyTo = null
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { Repo.sendMedia(it, replyTo?.id) }
        if (uris.isNotEmpty()) replyTo = null
    }
    var camUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) {
            Repo.sendMedia(u, replyTo?.id); replyTo = null
        }
    }
    fun launchCamera() {
        val f = File(ctx.cacheDir, "camera/${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        camUri = u
        try {
            takePicture.launch(u)
        } catch (e: Exception) {
            Toast.makeText(ctx, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(ctx, "Camera permission is needed to take photos", Toast.LENGTH_SHORT).show()
    }

    val others = members.values.filter { it.id != myId }
    Column(
        Modifier
            .fillMaxSize()
            .background(C.Bg)
    ) {
        TopBar(
            title = group?.name ?: "Scathe Chat",
            subtitle = when {
                conn == Conn.ERROR -> connError ?: "Connection problem"
                conn == Conn.OFFLINE -> "Offline · messages will send when you're back"
                conn == Conn.CONNECTING && state.messages.isEmpty() -> "Connecting…"
                else -> subtitleFor(members, myId, now)
            },
            subtitleAlert = conn == Conn.ERROR || conn == Conn.OFFLINE,
            onMembers = { sheet = Sheet.MEMBERS },
            onInvite = { sheet = Sheet.INVITE },
            onSettings = { sheet = Sheet.SETTINGS },
            onCalendar = { showCalendar = true },
            upcoming = state.events.count { e ->
                val s = e.info.start
                !e.info.cancelled && (e.info.isPoll || (s != null && s + e.info.dur * 60_000L > now))
            },
        )
        if (update != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3A1417))
                    .clickable { openUrl(ctx, update!!) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SystemUpdate, null, tint = C.Crimson, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("A new version of Scathe Chat is ready — tap to download", color = C.Text, fontSize = 13.sp)
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                items(rows, key = { it.key }, contentType = { if (it is Row.Day) 0 else 1 }) { row ->
                    when (row) {
                        is Row.Day -> DayChip(row.label)
                        is Row.Bubble -> if (row.m.type == "event") {
                            EventChatCard(row.m, members, myId) { showCalendar = true }
                        } else {
                            val m = row.m
                            val mine = m.from == myId
                            val readByAll = mine && m.key != null && others.isNotEmpty() &&
                                others.all { o -> o.read != null && o.read >= m.key }
                            MessageRow(
                                m = m,
                                mine = mine,
                                first = row.first,
                                last = row.last,
                                sender = members[m.from],
                                reactions = state.reactions[m.id],
                                members = members,
                                replyTarget = m.replyTo?.let { state.byId[it] },
                                readByAll = readByAll,
                                myId = myId,
                                onLongPress = { actionsFor = m },
                                onOpenMedia = {
                                    val media = m.media
                                    if (media != null) {
                                        if (media.kind == "file") {
                                            scope.launch {
                                                Repo.ensureMedia(media)?.let { MediaUtil.open(ctx, it, media) }
                                            }
                                        } else viewer = m
                                    }
                                },
                                onReact = { Repo.react(m, it) },
                            )
                        }
                    }
                }
            }
            if (state.messages.isEmpty() && conn != Conn.CONNECTING) {
                EmptyState(onInvite = { sheet = Sheet.INVITE }, Modifier.align(Alignment.Center))
            }
            if (!atBottom) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = C.Field,
                    contentColor = C.Text,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                ) { Icon(Icons.Filled.KeyboardArrowDown, "Newest") }
            }
        }
        Composer(
            replyTo = replyTo,
            replyName = replyTo?.let { members[it.from]?.name },
            onCancelReply = { replyTo = null },
            onSend = { text, link, allowPreview ->
                Repo.sendText(text, link, replyTo?.id, allowPreview)
                replyTo = null
            },
            onPhotos = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onCamera = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else camPermission.launch(Manifest.permission.CAMERA)
            },
            onFiles = { pickFiles.launch(arrayOf("*/*")) },
        )
    }

    if (showCalendar) CalendarScreen(onClose = { showCalendar = false })

    actionsFor?.let { m ->
        ActionsSheet(
            m = m,
            mine = m.from == myId,
            onDismiss = { actionsFor = null },
            onReact = { Repo.react(m, it); actionsFor = null },
            onReply = { replyTo = m; actionsFor = null },
        )
    }
    when (sheet) {
        Sheet.INVITE -> group?.let { InviteSheet(it) { sheet = null } }
        Sheet.MEMBERS -> MembersSheet(members, myId, now) { sheet = null }
        Sheet.SETTINGS -> SettingsSheet { sheet = null }
        null -> {}
    }
    viewer?.let { MediaViewer(it) { viewer = null } }

    if (shares.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { Repo.pendingShare.value = emptyList() },
            title = { Text("Send to ${group?.name ?: "the group"}?") },
            text = { Text(if (shares.size == 1) "1 item will be encrypted and sent." else "${shares.size} items will be encrypted and sent.") },
            confirmButton = {
                TextButton(onClick = {
                    shares.forEach { Repo.sendMedia(it, null) }
                    Repo.pendingShare.value = emptyList()
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { Repo.pendingShare.value = emptyList() }) { Text("Cancel", color = C.Dim) } },
        )
    }

    if (askBattery) {
        AlertDialog(
            onDismissRequest = { askBattery = false; Repo.prefs.askedBattery = true },
            title = { Text("Instant notifications") },
            text = { Text("To get messages the moment they're sent, let Scathe Chat run in the background. It keeps one quiet connection open and uses very little battery.") },
            confirmButton = {
                TextButton(onClick = {
                    askBattery = false; Repo.prefs.askedBattery = true
                    requestIgnoreBattery(ctx)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { askBattery = false; Repo.prefs.askedBattery = true }) { Text("Not now", color = C.Dim) }
            },
        )
    }
}

fun ignoringBattery(ctx: android.content.Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@android.annotation.SuppressLint("BatteryLife")
fun requestIgnoreBattery(ctx: android.content.Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

fun isOnline(m: Member, now: Long): Boolean = now - m.active < 100_000

fun lastSeen(m: Member, now: Long): String {
    if (isOnline(m, now)) return "online"
    if (m.active <= 0) return "not seen yet"
    val mins = (now - m.active) / 60_000
    return when {
        mins < 60 -> "last seen ${mins}m ago"
        mins < 24 * 60 -> "last seen ${mins / 60}h ago"
        else -> "last seen ${mins / (24 * 60)}d ago"
    }
}

private fun subtitleFor(members: Map<String, Member>, myId: String, now: Long): String {
    val others = members.values.filter { it.id != myId }
    if (others.isEmpty()) return "Just you so far · tap Invite"
    val online = others.filter { isOnline(it, now) }
    val names = others.sortedBy { it.name.lowercase() }.joinToString(", ") { it.name }
    return if (online.isNotEmpty()) "${online.joinToString(", ") { it.name }} online" else names
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    subtitleAlert: Boolean,
    onMembers: () -> Unit,
    onInvite: () -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    upcoming: Int,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.Bar)
            .statusBarsPadding()
            .height(62.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Bg), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), null, modifier = Modifier.size(44.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onMembers)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = if (subtitleAlert) Color(0xFFFFB4B4) else C.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCalendar) {
            BadgedBox(badge = { if (upcoming > 0) Badge(containerColor = C.Crimson) { Text("$upcoming") } }) {
                Icon(Icons.Filled.CalendarMonth, "Calendar", tint = C.Text)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = C.Text) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Calendar") }, leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) }, onClick = { menu = false; onCalendar() })
                DropdownMenuItem(text = { Text("Members") }, leadingIcon = { Icon(Icons.Filled.Group, null) }, onClick = { menu = false; onMembers() })
                DropdownMenuItem(text = { Text("Invite a brother") }, leadingIcon = { Icon(Icons.Filled.QrCode, null) }, onClick = { menu = false; onInvite() })
                DropdownMenuItem(text = { Text("Settings") }, leadingIcon = { Icon(Icons.Filled.Settings, null) }, onClick = { menu = false; onSettings() })
            }
        }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label, color = C.Dim, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Bar).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun EmptyState(onInvite: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.ic_logo), null, modifier = Modifier.size(110.dp))
        Text("No messages yet", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Everything here is end-to-end encrypted. Only phones with your group's invite can read it.",
            color = C.Dim, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onInvite) { Text("Invite your brothers") }
    }
}

@Composable
private fun Composer(
    replyTo: Msg?,
    replyName: String?,
    onCancelReply: () -> Unit,
    onSend: (String, LinkInfo?, Boolean) -> Unit,
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val draft by Repo.draft.collectAsState()
    LaunchedEffect(draft) {
        draft?.let {
            text = if (text.isBlank()) it else "$text $it"
            Repo.draft.value = null
        }
    }
    val url = remember(text) { LinkPreview.firstUrl(text) }
    var dismissed by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<LinkInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        preview = null
        if (url != null && url != dismissed) {
            delay(400)
            loading = true
            preview = LinkPreview.fetch(url)
            loading = false
        } else {
            loading = false
        }
    }
    var menu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(C.Bar)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        if (replyTo != null) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = C.Crimson, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("Replying to ${replyName ?: "message"}", color = C.Crimson, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(replyTo.preview(), color = C.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onCancelReply) { Icon(Icons.Filled.Close, "Cancel reply", tint = C.Dim) }
            }
        }
        if (url != null && url != dismissed && (loading || preview != null)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.Field),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val p = preview
                val bmp = remember(p?.image) { MediaUtil.decodeThumb(p?.image) }
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp))
                } else {
                    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(p?.title ?: if (loading) "Loading preview…" else url, color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p?.site ?: "", color = C.Dim, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { dismissed = url; preview = null }) { Icon(Icons.Filled.Close, "Remove preview", tint = C.Dim) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, "Attach", tint = C.Text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Photos & videos") }, leadingIcon = { Icon(Icons.Filled.Image, null) }, onClick = { menu = false; onPhotos() })
                    DropdownMenuItem(text = { Text("Camera") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = { menu = false; onCamera() })
                    DropdownMenuItem(text = { Text("File") }, leadingIcon = { Icon(Icons.Filled.AttachFile, null) }, onClick = { menu = false; onFiles() })
                }
            }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message", color = C.Faint) },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = C.Field,
                    unfocusedContainerColor = C.Field,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = C.Crimson,
                    focusedTextColor = C.Text,
                    unfocusedTextColor = C.Text,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        val link = if (url != null && url != dismissed) preview?.takeIf { it.url == url } else null
                        onSend(t, link, url == null || url != dismissed)
                        text = ""
                        preview = null
                        dismissed = null
                    }
                },
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = C.Crimson, disabledContainerColor = C.Field),
                modifier = Modifier.size(52.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }
}

@Composable
fun MemberDot(m: Member, size: Int = 36) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(C.member(m.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(m.name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = (size / 2.4).sp)
    }
}

@Composable
fun Pill(text: String) {
    Text(
        text, color = C.Dim, fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(C.Field).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
