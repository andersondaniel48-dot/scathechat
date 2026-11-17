package dev.daniel.scathechat.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import org.json.JSONObject

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
private fun smooth(p: Float) = p * p * (3 - 2 * p)

// ---------------------------------------------------------------- killcam

data class KillcamInfo(val by: String, val target: Msg?, val caption: String?)

fun killcamOf(m: Msg, byId: Map<String, Msg>): KillcamInfo? {
    val o = m.data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    return KillcamInfo(m.from, byId[o.optString("t")], o.optString("c").ifBlank { null })
}

/** The chat card for a killcam reply. */
@Composable
fun KillcamCard(m: Msg, members: Map<String, Member>, myId: String, byId: Map<String, Msg>, onPlay: () -> Unit) {
    val k = remember(m.data, byId) { killcamOf(m, byId) } ?: return
    val shooter = if (m.from == myId) "You" else members[m.from]?.name ?: "Someone"
    val victim = k.target?.let { t -> if (t.from == myId) "you" else members[t.from]?.name } ?: "someone"
    val rec = rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.2f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "r")
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0606), Color(0xFF0A0404))))
                .border(1.dp, Color(0xFFFF2D2D).copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                .clickable(onClick = onPlay)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF2D2D).copy(alpha = rec.value)))
                Spacer(Modifier.width(7.dp))
                Text("KILLCAM", color = Color(0xFFFF4D4D), fontFamily = Display, fontSize = 15.sp, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text(timeOf(m.ts), color = C.Smoke, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("$shooter replayed $victim", color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            k.target?.let { Text("“" + it.preview().take(80) + "”", color = C.Smoke, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            k.caption?.let { Text(it, color = C.Flame, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFFFF4D4D), modifier = Modifier.size(18.dp))
                Text("Watch replay", color = Color(0xFFFF4D4D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Full-screen slow-motion replay of a message, CoD killcam style. Tap to skip. */
@Composable
fun KillcamOverlay(info: KillcamInfo, members: Map<String, Member>, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val anim = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        anim.animateTo(6f, tween(6000, easing = LinearEasing))
        onDone()
    }
    val t = anim.value
    LaunchedEffect(t >= 3.3f) { if (t >= 3.3f) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val target = info.target
    val victim = target?.let { members[it.from] }
    val shooter = members[info.by]
    val fadeIn = seg(t, 0f, 0.25f)
    val out = 1f - seg(t, 5.5f, 6f)
    val text = target?.text ?: target?.preview() ?: "…"
    val shown = (text.length * seg(t, 0.5f, 3.0f)).toInt().coerceIn(0, text.length)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fadeIn * out }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone)
    ) {
        // The replayed message, drifting in slow motion
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .padding(horizontal = 30.dp)
                    .graphicsLayer {
                        val p = smooth(seg(t, 0f, 4.2f))
                        val s = lerp(0.75f, 1.2f, p)
                        scaleX = s; scaleY = s
                        rotationZ = lerp(-4f, 2f, p)
                        translationY = lerp(40f, -20f, p) * density.density
                    },
                horizontalAlignment = Alignment.Start,
            ) {
                Text(victim?.name ?: "Someone", color = C.member(victim?.color ?: 0).copy(alpha = 0.85f), fontFamily = Display, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF2A2A2A))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)).padding(14.dp)
                ) {
                    val thumb = remember(target?.media?.thumb, target?.link?.image) {
                        MediaUtil.decodeThumb(target?.media?.thumb ?: target?.link?.image)
                    }
                    val media = target?.media
                    val full = if (media != null && media.kind == "image" && media.blob in Repo.ready.value) Repo.mediaFile(media) else null
                    if (full != null) {
                        coil.compose.AsyncImage(model = full, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(260.dp).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.height(8.dp))
                    } else if (thumb != null) {
                        Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(260.dp).aspectRatio(1.3f).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(text.take(shown), color = Color(0xFFE8E8E8), fontSize = 20.sp, lineHeight = 26.sp)
                }
            }
        }

        // Film look: red vignette, scanlines, letterbox
        val pulse = 0.75f + 0.25f * kotlin.math.sin(t * 5f)
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    0.45f to Color.Transparent, 1f to Color(0xFFB00000).copy(alpha = 0.75f * pulse),
                    center = center, radius = size.maxDimension * 0.75f,
                )
            )
            var y = 0f
            val step = 4 * density.density
            while (y < size.height) {
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            val bar = 64 * density.density
            drawRect(Color.Black, size = size.copy(height = bar))
            drawRect(Color.Black, topLeft = Offset(0f, size.height - bar), size = size.copy(height = bar))
            // Hitmarker at the moment of the kill
            if (t > 3.3f && t < 3.8f) {
                val a = 1f - seg(t, 3.55f, 3.8f)
                val c = center
                val u = density.density
                val red = Color(0xFFFF2D2D).copy(alpha = a)
                for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                    drawLine(red, Offset(c.x + dx * 48 * u, c.y + dy * 48 * u), Offset(c.x + dx * 18 * u, c.y + dy * 18 * u), 7 * u, StrokeCap.Square)
                }
                drawRect(Color(0xFFFF2D2D).copy(alpha = 0.25f * a))
            }
        }

        // HUD
        Row(Modifier.statusBarsPadding().padding(start = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF2D2D).copy(alpha = if ((t * 2).toInt() % 2 == 0) 1f else 0.25f)))
            Spacer(Modifier.width(8.dp))
            Text("KILLCAM", color = Color.White, fontFamily = Display, fontSize = 18.sp, letterSpacing = 3.sp)
            Spacer(Modifier.width(12.dp))
            Text("0.25×", color = Color(0xFFFF8080), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        val ms = (t * 250).toInt()
        Text(
            "00:%02d.%03d".format(ms / 1000, ms % 1000), color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 18.dp, top = 16.dp),
        )

        // HEADSHOT + killed-by card
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "HEADSHOT", style = fireText(46.sp), textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = seg(t, 3.3f, 3.5f)
                    val s = lerp(2.4f, 1f, p)
                    scaleX = s; scaleY = s; alpha = p
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .graphicsLayer {
                        val p = smooth(seg(t, 3.7f, 4.1f))
                        alpha = p; translationY = (1f - p) * 24 * density.density
                    }
                    .glass(RoundedCornerShape(18.dp), fill = Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shooter != null) MemberDot(shooter, 34)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Killed by ${shooter?.name ?: "someone"}", color = C.Bone, fontFamily = Display, fontSize = 16.sp)
                    info.caption?.let { Text(it, color = C.Flame, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- location drop card

@Composable
fun LocationCard(m: Msg, mine: Boolean, sender: Member?) {
    val ctx = LocalContext.current
    val o = remember(m.data) { m.data?.let { runCatching { JSONObject(it) }.getOrNull() } } ?: return
    val lat = o.optDouble("lat")
    val lng = o.optDouble("lng")
    val place = o.optString("place").ifBlank { null }
    val note = o.optString("note").ifBlank { null }
    val acc = o.optDouble("acc", 0.0)
    val img = remember(o) { MediaUtil.decodeThumb(o.optString("img").ifBlank { null }) }
    val who = sender?.name ?: "Someone"
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (!mine) Text(who, color = C.member(sender?.color ?: 0), fontFamily = Display, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
        Column(
            Modifier
                .width(290.dp)
                .glass(RoundedCornerShape(18.dp), fill = Color(0x38140A06), rim = 1.3f)
                .clickable {
                    val label = Uri.encode(place ?: "$who's drop")
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        openUrl(ctx, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng")
                    }
                }
        ) {
            Box {
                if (img != null) {
                    Image(img.asImageBitmap(), "Map", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
                }
                Text(
                    "📍 DROP", color = Color.White, fontFamily = Display, fontSize = 12.sp, letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(10.dp).molten(RoundedCornerShape(8.dp), alpha = 0.9f).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    "© OpenStreetMap contributors", color = Color(0xAAFFFFFF), fontSize = 8.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    (if (mine) "You dropped" else "$who dropped") + (place?.let { " at $it" } ?: " a location"),
                    color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
                note?.let { Text(it, color = C.Flame, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
                Text(
                    "%.5f, %.5f".format(lat, lng) + (if (acc > 0) " · ±${acc.toInt()} m" else "") + " · " + timeOf(m.ts),
                    color = C.Smoke, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- typing

/** "David is typing" dots above the message box. */
@Composable
fun TypingBubble(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val dots = rememberInfiniteTransition(label = "dots")
    val a = dots.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "d")
    Row(
        modifier.glass(RoundedCornerShape(16.dp), fill = Color(0x40140A06), rim = 0.6f).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until 3) {
            val on = a.value.toInt() == i
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) C.Flame else C.Smoke.copy(alpha = 0.6f)))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            when (names.size) {
                1 -> "${names[0]} is typing"
                2 -> "${names[0]} and ${names[1]} are typing"
                else -> "Everyone's typing"
            },
            color = C.Bone, fontSize = 12.sp,
        )
    }
}
