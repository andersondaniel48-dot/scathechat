@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.daniel.scathechat.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.daniel.scathechat.data.LinkInfo
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import dev.daniel.scathechat.net.LinkPreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
fun timeOf(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)

fun openUrl(ctx: android.content.Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun isJumbo(m: Msg): Boolean {
    val t = m.text ?: return false
    if (m.media != null || m.link != null || m.replyTo != null) return false
    if (t.length > 12 || t.isBlank()) return false
    return t.none { it.isLetterOrDigit() } && t.any { Character.isSurrogate(it) || it.code in 0x2600..0x27BF }
}

@Composable
fun MessageRow(
    m: Msg,
    mine: Boolean,
    first: Boolean,
    last: Boolean,
    sender: Member?,
    reactions: Map<String, String>?,
    members: Map<String, Member>,
    replyTarget: Msg?,
    readByAll: Boolean,
    myId: String,
    onLongPress: () -> Unit,
    onOpenMedia: () -> Unit,
    onReact: (String) -> Unit,
) {
    val bubbleColor = if (mine) C.Mine else C.Other
    val big = 18.dp
    val small = 5.dp
    val shape = if (mine) {
        RoundedCornerShape(topStart = big, topEnd = if (first) big else small, bottomEnd = if (last) big else small, bottomStart = big)
    } else {
        RoundedCornerShape(topStart = if (first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (last) big else small)
    }
    val jumbo = isJumbo(m)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 8.dp else 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine && first) {
            Text(
                sender?.name ?: "Unknown",
                color = C.member(sender?.color ?: 0),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        val clickMod = Modifier.combinedClickable(
            onClick = { if (m.status == Msg.FAILED) Repo.retry(m) else if (m.media != null) onOpenMedia() },
            onLongClick = onLongPress,
        )
        if (jumbo) {
            Column(clickMod.padding(horizontal = 6.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                Text(m.text ?: "", fontSize = 44.sp)
                Meta(m, mine, readByAll, onDark = false)
            }
        } else {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .then(clickMod)
            ) {
                Column {
                    if (replyTarget != null) ReplyQuote(replyTarget, members, mine)
                    m.media?.let { MediaBlock(m, onOpenMedia) }
                    m.link?.let { LinkCard(it, mine) }
                    val hasText = !m.text.isNullOrBlank()
                    Column(Modifier.padding(start = 12.dp, end = 10.dp, top = if (hasText) 7.dp else 2.dp, bottom = 6.dp)) {
                        if (hasText) LinkText(m.text!!, C.Text, if (mine) Color.White else Color(0xFF8AB4F8))
                        Meta(m, mine, readByAll, onDark = true, modifier = Modifier.align(Alignment.End))
                    }
                }
            }
        }
        if (!reactions.isNullOrEmpty()) {
            Row(
                Modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                reactions.entries.groupBy { it.value }.forEach { (emoji, who) ->
                    val minePicked = who.any { it.key == myId }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (minePicked) Color(0xFF4A1C20) else C.Field)
                            .border(1.dp, if (minePicked) C.Crimson else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { onReact(emoji) }
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(emoji, fontSize = 14.sp)
                        if (who.size > 1) Text(" ${who.size}", color = C.Dim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (m.media != null) {
        LaunchedEffect(m.media.blob) {
            val k = m.media.kind
            if (k == "image" || k == "gif" || (k == "video" && m.media.size < 25L * 1024 * 1024)) Repo.requestMedia(m)
        }
    }
}

@Composable
private fun Meta(m: Msg, mine: Boolean, readByAll: Boolean, onDark: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(timeOf(m.ts), color = if (mine && onDark) Color(0xCCFFFFFF) else C.Dim, fontSize = 11.sp)
        if (mine) {
            Spacer(Modifier.width(4.dp))
            when {
                m.status == Msg.FAILED -> {
                    Icon(Icons.Filled.ErrorOutline, "Failed", tint = Color(0xFFFFC2C2), modifier = Modifier.size(14.dp))
                    Text(" Tap to retry", color = Color(0xFFFFC2C2), fontSize = 11.sp)
                }
                m.key == null -> Icon(Icons.Filled.Schedule, "Sending", tint = Color(0xCCFFFFFF), modifier = Modifier.size(13.dp))
                readByAll -> Icon(Icons.Filled.DoneAll, "Seen", tint = Color(0xFF9AD7FF), modifier = Modifier.size(15.dp))
                else -> Icon(Icons.Filled.Check, "Sent", tint = Color(0xCCFFFFFF), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun LinkText(text: String, color: Color, linkColor: Color, fontSize: Int = 16) {
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var last = 0
            for (match in LinkPreview.URL_REGEX.findAll(text)) {
                val raw = LinkPreview.trimUrl(match.value)
                if (raw.isEmpty()) continue
                val start = match.range.first
                val end = start + raw.length
                if (start < last) continue
                append(text.substring(last, start))
                val href = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
                withLink(LinkAnnotation.Url(href, TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(raw)
                }
                last = end
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    Text(annotated, color = color, fontSize = fontSize.sp, lineHeight = (fontSize + 5).sp)
}

@Composable
private fun ReplyQuote(target: Msg, members: Map<String, Member>, mine: Boolean) {
    val who = members[target.from]
    Row(
        Modifier
            .padding(start = 6.dp, end = 6.dp, top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (mine) Color(0x33000000) else Color(0x22FFFFFF))
            .padding(end = 10.dp)
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(C.member(who?.color ?: 0)))
        Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(who?.name ?: "Someone", color = C.member(who?.color ?: 0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(target.preview(), color = C.Text.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LinkCard(link: LinkInfo, mine: Boolean) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .padding(start = 6.dp, end = 6.dp, top = 6.dp)
            .width(288.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (mine) Color(0x33000000) else Color(0x1AFFFFFF))
            .clickable { openUrl(ctx, link.url) }
    ) {
        val bmp = remember(link.image) { MediaUtil.decodeThumb(link.image) }
        if (bmp != null) {
            val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1)).coerceIn(1.2f, 2.2f)
            Image(
                bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            link.site?.let { Text(it.uppercase(), color = C.Dim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            link.title?.let { Text(it, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            link.desc?.let { Text(it, color = C.Text.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun MediaBlock(m: Msg, onOpen: () -> Unit) {
    val media = m.media ?: return
    val ready by Repo.ready.collectAsState()
    val progress by Repo.progress.collectAsState()
    val isReady = media.blob in ready
    val p = progress[media.blob]
    val ctx = LocalContext.current

    if (media.kind == "file") {
        Row(
            Modifier
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .width(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x22000000))
                .clickable {
                    if (isReady) MediaUtil.open(ctx, Repo.mediaFile(media), media) else Repo.requestMedia(m)
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                if (p != null) CircularProgressIndicator(progress = { p }, modifier = Modifier.size(30.dp), strokeWidth = 3.dp, color = Color.White)
                else Icon(Icons.Filled.Description, null, tint = Color.White)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(media.name ?: "File", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    MediaUtil.humanSize(media.size) + if (isReady) "" else " · tap to download",
                    color = C.Text.copy(alpha = 0.7f), fontSize = 12.sp,
                )
            }
        }
        return
    }

    val ratio = if (media.w > 0 && media.h > 0) (media.w.toFloat() / media.h).coerceIn(0.56f, 1.9f) else 1.33f
    Box(
        Modifier
            .padding(3.dp)
            .width(264.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black)
            .clickable { onOpen() },
        contentAlignment = Alignment.Center,
    ) {
        val thumb = remember(media.thumb) { MediaUtil.decodeThumb(media.thumb) }
        val showFull = isReady && media.kind != "video"
        if (thumb != null && !showFull) {
            Image(
                thumb.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (media.kind != "video" && Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp) else Modifier),
            )
        }
        if (showFull) {
            AsyncImage(model = Repo.mediaFile(media), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (media.kind == "video") {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                if (p != null) CircularProgressIndicator(progress = { p }, modifier = Modifier.size(40.dp), strokeWidth = 3.dp, color = Color.White)
                else Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text(
                MediaUtil.duration(media.dur),
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else if (!isReady) {
            if (p != null) CircularProgressIndicator(progress = { p }, modifier = Modifier.size(40.dp), strokeWidth = 3.dp, color = Color.White)
            else CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = Color(0xAAFFFFFF))
        }
        if (media.kind == "gif") {
            Text(
                "GIF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (m.key == null && p != null && media.kind != "video") {
            // Sending: show upload progress over the photo.
            CircularProgressIndicator(progress = { p }, modifier = Modifier.size(40.dp), strokeWidth = 3.dp, color = Color.White)
        }
    }
}
