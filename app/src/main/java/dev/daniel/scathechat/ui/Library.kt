@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.daniel.scathechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import dev.daniel.scathechat.net.LinkPreview
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("MMM d")
private val shortDateYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun dateOf(ts: Long): String {
    val d = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        d == today -> "Today " + timeOf(ts)
        d == today.minusDays(1) -> "Yesterday"
        d.year == today.year -> d.format(shortDate)
        else -> d.format(shortDateYear)
    }
}

@Composable
private fun OverlayHeader(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp)
            .height(62.dp).glass(RoundedCornerShape(24.dp), fill = Color(0x40140A06)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Bone) }
        Text(title, style = fireText(19.sp, glow = 0.5f), modifier = Modifier.weight(1f))
        trailing()
    }
}

// ---------------------------------------------------------------- media & files

/** Everything shared in the chat: photos & videos, files, and links. Long-press anything to find it in the chat. */
@Composable
fun MediaLibrary(onClose: () -> Unit, onJump: (Msg) -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.chat.collectAsState()
    val members by Repo.members.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var viewer by remember { mutableStateOf<Msg?>(null) }
    val newest = remember(state.messages) { state.messages.asReversed() }
    val media = remember(newest) { newest.filter { it.media?.kind.let { k -> k == "image" || k == "gif" || k == "video" } } }
    val files = remember(newest) { newest.filter { it.media?.kind == "file" } }
    val links = remember(newest) { newest.filter { it.link != null || (it.text?.let { t -> LinkPreview.firstUrl(t) } != null) } }

    Box(Modifier.fillMaxSize().background(C.Char)) {
        EmberBackground(Modifier.fillMaxSize(), intensity = 0.7f)
        Column(Modifier.fillMaxSize()) {
            OverlayHeader("Media & files", onClose)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Media" to media.size, "Files" to files.size, "Links" to links.size).forEachIndexed { i, (label, n) ->
                    val on = tab == i
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        "$label  $n", color = if (on) Color.White else C.Smoke, fontFamily = Display, fontSize = 14.sp,
                        modifier = Modifier
                            .then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x30140A06), rim = 0.5f))
                            .clickable { tab = i }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            when (tab) {
                0 -> if (media.isEmpty()) EmptyNote("Photos and videos you share show up here.") else MediaGrid(media, onOpen = { viewer = it }, onJump = onJump)
                1 -> if (files.isEmpty()) EmptyNote("Files you share show up here.") else LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(files, key = { it.id }) { m ->
                        val media = m.media!!
                        Row(
                            Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), fill = Color(0x30140A06))
                                .combinedClickable(
                                    onClick = { scope.launch { Repo.ensureMedia(media)?.let { MediaUtil.open(ctx, it, media) } } },
                                    onLongClick = { onJump(m) },
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0x26FFFFFF)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Description, null, tint = C.Flame)
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(media.name ?: "File", color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    MediaUtil.humanSize(media.size) + " · " + (members[m.from]?.name ?: "Someone") + " · " + dateOf(m.ts),
                                    color = C.Smoke, fontSize = 12.sp, maxLines = 1,
                                )
                            }
                        }
                    }
                }
                else -> if (links.isEmpty()) EmptyNote("Links you share show up here.") else LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(links, key = { it.id }) { m -> LinkRow(m, members) { onJump(m) } }
                }
            }
        }
    }
    viewer?.let { MediaViewer(it) { viewer = null } }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.Smoke, fontSize = 14.sp)
    }
}

@Composable
private fun MediaGrid(items: List<Msg>, onOpen: (Msg) -> Unit, onJump: (Msg) -> Unit) {
    val ready by Repo.ready.collectAsState()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.navigationBarsPadding(),
    ) {
        items(items, key = { it.id }) { m ->
            val media = m.media!!
            val isReady = media.blob in ready
            if (media.kind != "video") LaunchedEffect(media.blob) { Repo.requestMedia(m) }
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))
                    .combinedClickable(onClick = { onOpen(m) }, onLongClick = { onJump(m) }),
                contentAlignment = Alignment.Center,
            ) {
                if (isReady && media.kind != "video") {
                    AsyncImage(model = Repo.mediaFile(media), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    val thumb = remember(media.thumb) { MediaUtil.decodeThumb(media.thumb) }
                    if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (media.kind == "video") {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, "Video", tint = Color.White)
                    }
                    Text(
                        MediaUtil.duration(media.dur), color = Color.White, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp))
                            .background(Color(0x99000000)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkRow(m: Msg, members: Map<String, Member>, onJump: () -> Unit) {
    val ctx = LocalContext.current
    val url = m.link?.url ?: m.text?.let { LinkPreview.firstUrl(it) } ?: return
    val bmp = remember(m.link?.image) { MediaUtil.decodeThumb(m.link?.image) }
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), fill = Color(0x30140A06))
            .combinedClickable(onClick = { openUrl(ctx, url) }, onLongClick = onJump)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Filled.Link, null, tint = C.Flame)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(m.link?.title ?: url, color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (m.link?.site ?: url.removePrefix("https://").removePrefix("http://").substringBefore('/')) +
                    " · " + (members[m.from]?.name ?: "Someone") + " · " + dateOf(m.ts),
                color = C.Smoke, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- search

private fun searchable(m: Msg): String = listOfNotNull(
    m.text, m.link?.title, m.link?.desc, m.link?.site, m.media?.name, m.event?.title, m.event?.note,
).joinToString("  ")

/** Search every message in the chat. Tap a result to jump to it. */
@Composable
fun ChatSearch(onClose: () -> Unit, onJump: (Msg) -> Unit) {
    BackHandler(onBack = onClose)
    val state by Repo.chat.collectAsState()
    val members by Repo.members.collectAsState()
    var query by remember { mutableStateOf("") }
    var who by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val q = query.trim()
    val results = remember(q, who, state.messages) {
        if (q.length < 2 && who == null) emptyList()
        else state.messages.asReversed().filter { m ->
            (who == null || m.from == who) && (q.isEmpty() || searchable(m).contains(q, ignoreCase = true))
        }.take(300)
    }

    Box(Modifier.fillMaxSize().background(C.Char)) {
        EmberBackground(Modifier.fillMaxSize(), intensity = 0.6f)
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp)
                    .glass(RoundedCornerShape(24.dp), fill = Color(0x40140A06)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Bone) }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search the chat", color = C.Faint) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.Flame) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear", tint = C.Smoke) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = C.Ember, focusedTextColor = C.Bone, unfocusedTextColor = C.Bone,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf<Member?>(null) + members.values.sortedBy { it.name.lowercase() }).forEach { m ->
                    val on = who == m?.id
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        m?.name ?: "Everyone", color = if (on) Color.White else C.Smoke, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x30140A06), rim = 0.5f))
                            .clickable { who = m?.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            when {
                q.length < 2 && who == null -> EmptyNote("Type at least two letters.")
                results.isEmpty() -> EmptyNote("Nothing matches “$q”.")
                else -> {
                    Text(
                        "${results.size}${if (results.size == 300) "+" else ""} result${if (results.size == 1) "" else "s"}",
                        color = C.Smoke, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        items(results, key = { it.id }) { m -> SearchRow(m, members[m.from], q) { onJump(m) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(m: Msg, sender: Member?, q: String, onClick: () -> Unit) {
    val full = searchable(m).ifBlank { m.preview() }
    val snippet = remember(full, q) {
        val i = if (q.isEmpty()) -1 else full.indexOf(q, ignoreCase = true)
        val start = if (i > 40) i - 40 else 0
        val cut = full.substring(start).take(160)
        buildAnnotatedString {
            if (start > 0) append("…")
            var last = 0
            if (q.isNotEmpty()) {
                var idx = cut.indexOf(q, ignoreCase = true)
                while (idx >= 0) {
                    append(cut.substring(last, idx))
                    withStyle(SpanStyle(color = Color.White, background = C.Ember.copy(alpha = 0.55f), fontWeight = FontWeight.Bold)) {
                        append(cut.substring(idx, idx + q.length))
                    }
                    last = idx + q.length
                    idx = cut.indexOf(q, last, ignoreCase = true)
                }
            }
            append(cut.substring(last))
        }
    }
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), fill = Color(0x30140A06)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (sender != null) MemberDot(sender, 32) else Box(Modifier.size(32.dp).clip(CircleShape).background(C.Faint))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sender?.name ?: "Someone", color = C.member(sender?.color ?: 0), fontFamily = Display, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(dateOf(m.ts), color = C.Smoke, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(snippet, color = C.Bone, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
