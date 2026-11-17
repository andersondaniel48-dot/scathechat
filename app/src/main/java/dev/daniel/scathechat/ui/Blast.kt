package dev.daniel.scathechat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.R
import dev.daniel.scathechat.data.Member
import kotlin.math.sin

// ---------------------------------------------------------------- timing helpers

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p
private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p) * (1f - p)
private fun easeOutBack(p: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = p - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private class Shard(val dx: Float, val dy: Float, val rot: Float, val size: Float, val color: Color, val shape: Int)
private class Drop(val dx: Float, val dy: Float, val r: Float, val color: Color)

private val shards = listOf(
    Shard(-150f, -120f, -260f, 40f, C.Bone, 0), Shard(140f, -150f, 300f, 46f, C.Bone, 1),
    Shard(-165f, 40f, 200f, 38f, Color(0xFFE8D8C6), 2), Shard(160f, 30f, -220f, 40f, Color(0xFFE8D8C6), 0),
    Shard(-40f, -190f, 140f, 32f, C.Bone, 1), Shard(60f, 170f, -160f, 28f, C.Char, 2),
    Shard(-120f, 150f, 280f, 26f, C.Ember, 0), Shard(100f, -200f, -320f, 24f, C.Flame, 1),
    Shard(-200f, -30f, 120f, 22f, C.Bone, 2), Shard(190f, 110f, -90f, 24f, C.Flame, 0),
)
private val drops = listOf(
    Drop(-110f, -60f, 17f, Color(0xFFC8102E)), Drop(120f, -40f, 13f, Color(0xFFC8102E)),
    Drop(-70f, 90f, 10f, C.Ember), Drop(80f, 100f, 15f, Color(0xFFC8102E)),
    Drop(10f, -140f, 11f, C.Ember), Drop(-150f, 20f, 9f, Color(0xFF7A0F12)), Drop(150f, 60f, 8f, Color(0xFF7A0F12)),
)

/**
 * The "filthification scheduled" moment: a scope locks on, hitmarker, the skull blows apart over an
 * airstrike fireball, the title slams in, and each brother's dog tag drops. Tap anywhere to skip.
 */
@Composable
fun FilthBlast(whenText: String, members: List<Member>, myId: String, onDone: () -> Unit) {
    val anim = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        anim.animateTo(4.6f, tween(4600, easing = LinearEasing))
        onDone()
    }
    val t = anim.value
    LaunchedEffect(t >= 0.55f) { if (t >= 0.55f) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val out = 1f - seg(t, 4.15f, 4.6f)
    val density = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = out * seg(t, 0f, 0.12f) }
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone)
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cx = w / 2f
        val cy = h * 0.32f
        val shake = if (t in 0.55f..0.8f) (1f - seg(t, 0.55f, 0.8f)) else 0f
        val sx = sin(t * 95f) * 9f * shake
        val sy = sin(t * 71f + 1f) * 7f * shake

        Box(Modifier.fillMaxSize().graphicsLayer { translationX = sx * density.density; translationY = sy * density.density }) {
            Canvas(Modifier.fillMaxSize()) {
                val u = density.density
                // Airstrike fireball behind everything
                val fb = seg(t, 0.55f, 1.3f)
                if (t > 0.55f) {
                    val r = lerp(20f, 190f, easeOut(fb)) * u
                    val a = (1f - seg(t, 1.2f, 2.6f)).coerceIn(0f, 1f)
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(0xFFFFF1D6).copy(alpha = a), 0.2f to C.Flame.copy(alpha = a), 0.45f to C.Ember.copy(alpha = 0.9f * a),
                            0.7f to C.Blood.copy(alpha = 0.6f * a), 1f to Color.Transparent,
                            center = Offset(cx, cy), radius = r,
                        ),
                        radius = r, center = Offset(cx, cy),
                    )
                }
                // Scope locking on
                if (t < 0.65f) {
                    val p = easeOut(seg(t, 0f, 0.5f))
                    val s = lerp(3.2f, 1f, p)
                    val a = seg(t, 0.05f, 0.25f) * (1f - seg(t, 0.55f, 0.65f))
                    rotate(lerp(-25f, 0f, p), Offset(cx, cy)) {
                        val r = 108f * u * s
                        drawCircle(C.Ember.copy(alpha = a), r, Offset(cx, cy), style = Stroke(2.5f * u))
                        drawCircle(C.Ember.copy(alpha = a), 4f * u, Offset(cx, cy))
                        val gap = 35f * u * s
                        drawLine(C.Ember.copy(alpha = a), Offset(cx, cy - r), Offset(cx, cy - gap), 2.5f * u)
                        drawLine(C.Ember.copy(alpha = a), Offset(cx, cy + gap), Offset(cx, cy + r), 2.5f * u)
                        drawLine(C.Ember.copy(alpha = a), Offset(cx - r, cy), Offset(cx - gap, cy), 2.5f * u)
                        drawLine(C.Ember.copy(alpha = a), Offset(cx + gap, cy), Offset(cx + r, cy), 2.5f * u)
                    }
                }
                // Shock ring
                if (t > 0.55f && t < 1.0f) {
                    val p = seg(t, 0.55f, 0.95f)
                    drawCircle(C.Flame.copy(alpha = 1f - p), lerp(30f, 280f, easeOut(p)) * u, Offset(cx, cy), style = Stroke(6f * u * (1f - p) + 1f))
                }
                // Blood
                if (t > 0.56f) {
                    val p = easeOut(seg(t, 0.56f, 0.9f))
                    val a = 1f - seg(t, 1.0f, 1.6f)
                    for (d in drops) {
                        val fall = seg(t, 0.9f, 1.6f) * 30f
                        drawCircle(d.color.copy(alpha = 0.95f * a), d.r * u * p, Offset(cx + d.dx * u * p, cy + (d.dy * p + fall) * u))
                    }
                }
                // Skull shards
                if (t > 0.55f) {
                    val p = easeOut(seg(t, 0.55f, 1.05f))
                    val a = 1f - seg(t, 1.05f, 1.45f)
                    val fall = seg(t, 1.0f, 1.45f) * 60f
                    for ((i, sh) in shards.withIndex()) {
                        val px = cx + sh.dx * u * 1.1f * p
                        val py = cy + (sh.dy * 1.1f * p + fall) * u
                        val s = sh.size * u
                        val path = Path().apply {
                            when (sh.shape) {
                                0 -> { moveTo(0f, s * 0.2f); lineTo(s, 0f); lineTo(s * 0.7f, s) }
                                1 -> { moveTo(s * 0.1f, 0f); lineTo(s, s * 0.4f); lineTo(0f, s) }
                                else -> { moveTo(s * 0.5f, 0f); lineTo(s, s); lineTo(0f, s * 0.8f) }
                            }
                            close()
                        }
                        translate(px - s / 2, py - s / 2) {
                            rotate(sh.rot * p + i * 7f, Offset(s / 2, s / 2)) { drawPath(path, sh.color.copy(alpha = a)) }
                        }
                    }
                }
                // Hitmarker
                if (t > 0.55f && t < 0.95f) {
                    val a = 1f - seg(t, 0.75f, 0.95f)
                    val s = lerp(1.2f, 1f, seg(t, 0.55f, 0.62f)) * u
                    val red = Color(0xFFFF2D2D).copy(alpha = a)
                    for ((dx, dy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                        drawLine(red, Offset(cx + dx * 45f * s, cy + dy * 45f * s), Offset(cx + dx * 17f * s, cy + dy * 17f * s), 7f * u, StrokeCap.Square)
                    }
                }
                // Flash
                val fl = if (t < 0.55f) 0f else 0.6f * (1f - seg(t, 0.56f, 0.75f))
                if (fl > 0f) drawRect(Color(0xFFFFF4E6).copy(alpha = fl))
            }

            // The skull itself, until it blows
            if (t < 0.62f) {
                val pop = seg(t, 0.05f, 0.35f)
                val boom = seg(t, 0.53f, 0.6f)
                Image(
                    painterResource(R.drawable.ic_skull), null,
                    modifier = Modifier
                        .size(150.dp, 175.dp)
                        .offset { androidx.compose.ui.unit.IntOffset((cx - 75 * density.density).toInt(), (cy - 87 * density.density).toInt()) }
                        .graphicsLayer {
                            val s = lerp(0.6f, 1f, easeOut(pop)) * lerp(1f, 1.35f, boom)
                            scaleX = s; scaleY = s
                            alpha = pop * (1f - seg(t, 0.56f, 0.62f))
                        },
                )
            }

            // Words and tags
            Column(
                Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(0, (h * 0.5f).toInt()) }.padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "HEADSHOT", color = C.Flame, fontFamily = Display, fontSize = 15.sp, letterSpacing = 6.sp,
                    modifier = Modifier.graphicsLayer { alpha = seg(t, 0.7f, 0.9f) },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Filthification scheduled", style = fireText(42.sp), textAlign = TextAlign.Center, lineHeight = 44.sp,
                    modifier = Modifier.graphicsLayer {
                        val p = seg(t, 0.8f, 1.0f)
                        val s = if (p < 1f) lerp(2.6f, 1f, easeOutBack(p).coerceAtMost(1.08f)) else 1f
                        scaleX = s; scaleY = s; alpha = p
                    },
                )
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .graphicsLayer {
                            val p = easeOut(seg(t, 1.15f, 1.45f))
                            alpha = p; translationY = (1f - p) * 30f * density.density
                        }
                        .glass(RoundedCornerShape(20.dp), fill = Color(0x26FFFFFF))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(whenText, color = C.Bone, fontFamily = Display, fontSize = 19.sp, textAlign = TextAlign.Center)
                    Text("Everyone's been notified", color = C.Smoke, fontSize = 13.sp)
                }
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    members.forEachIndexed { i, m ->
                        val start = 1.5f + i * 0.13f
                        val p = seg(t, start, start + 0.38f)
                        val tilt = listOf(-6f, 3f, -2f, 7f, -4f)[i % 5]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                translationY = lerp(-520f, 0f, easeOutBack(p)) * density.density
                                rotationZ = lerp(tilt * 3f, tilt, p)
                                alpha = if (p > 0f) 1f else 0f
                            },
                        ) {
                            Box(Modifier.width(2.dp).height((30 + (i % 3) * 10).dp).background(C.Smoke.copy(alpha = 0.7f)))
                            Column(
                                Modifier
                                    .width(78.dp)
                                    .height(106.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Brush.linearGradient(listOf(Color(0xFFF2E9DF), Color(0xFF9C8F84))))
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(C.Char))
                                Spacer(Modifier.height(6.dp))
                                Text(m.name.uppercase(), color = Color(0xFF1A0E0A), fontFamily = Display, fontSize = if (m.name.length > 7) 11.sp else 13.sp, maxLines = 1)
                                Text(if (m.id == myId) "IN" else "?", color = Color(0xFF1A0E0A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The everyday "LOCKED IN" stamp that slams onto a freshly scheduled card. */
@Composable
fun LockedInStamp(modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(4f, tween(4000, easing = LinearEasing)) }
    val t = anim.value
    val density = LocalDensity.current
    val slam = seg(t, 0.0f, 0.28f)
    val fade = 1f - seg(t, 3.4f, 4f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp, 140.dp)) {
            if (t > 0.28f) {
                val p = easeOut(seg(t, 0.28f, 0.75f))
                val a = 1f - seg(t, 0.5f, 0.85f)
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Brush.radialGradient(listOf(C.Flame.copy(alpha = 0.5f * a), Color.Transparent), c, 110f * density.density * p + 1f), 110f * density.density * p + 1f, c)
                for ((dx, dy) in listOf(-90f to -60f, 80f to -70f, 100f to 40f, -70f to 60f, 10f to -90f, -110f to 10f)) {
                    drawCircle(C.Flame.copy(alpha = a), 3.5f * density.density, Offset(c.x + dx * density.density * p, c.y + dy * density.density * p))
                }
            }
        }
        Text(
            "LOCKED IN", color = Color(0xFFFF3B2E), fontFamily = Display, fontSize = 30.sp, letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer {
                    val s = lerp(3f, 1f, slam * slam)
                    scaleX = s; scaleY = s
                    rotationZ = lerp(-14f, -9f, slam)
                    alpha = (if (t > 0f) 1f else 0f) * fade
                }
                .background(Color(0x59000000), RoundedCornerShape(8.dp))
                .border(4.dp, Color(0xFFFF3B2E), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
