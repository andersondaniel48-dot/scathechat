package dev.daniel.scathechat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.daniel.scathechat.call.CallManager
import dev.daniel.scathechat.data.Conn
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Things the logo reacts to. Fired from anywhere; every visible logo plays them. */
object LogoFx {
    enum class Ev { SENT, RECEIVED, FAILED, TAP }
    val events = MutableSharedFlow<Ev>(extraBufferCapacity = 16)
    fun emit(e: Ev) { events.tryEmit(e) }
}

// ---------------------------------------------------------------- geometry (200-unit box, same as the icon)

private fun poly(vararg p: Float) = Path().apply {
    moveTo(p[0], p[1]); var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
    close()
}
private fun line(vararg p: Float) = Path().apply {
    moveTo(p[0], p[1]); var i = 2
    while (i < p.size) { lineTo(p[i], p[i + 1]); i += 2 }
}

private val OUTER = poly(62f, 14f, 138f, 14f, 168f, 44f, 168f, 170f, 152f, 186f, 48f, 186f, 32f, 170f, 32f, 44f)
private val INNER = poly(66f, 23f, 134f, 23f, 159f, 48f, 159f, 166f, 148f, 177f, 52f, 177f, 41f, 166f, 41f, 48f)
private val S = line(146f, 58f, 78f, 58f, 58f, 78f, 58f, 84f, 78f, 100f, 122f, 100f, 142f, 116f, 142f, 122f, 122f, 142f, 54f, 142f)
private val LIT_OUT = line(32f, 170f, 32f, 44f, 62f, 14f, 138f, 14f, 168f, 44f)
private val DARK_OUT = line(168f, 44f, 168f, 170f, 152f, 186f, 48f, 186f, 32f, 170f)
private val DARK_IN = line(41f, 166f, 41f, 48f, 66f, 23f, 134f, 23f, 159f, 48f)
private val LIT_IN = line(159f, 48f, 159f, 166f, 148f, 177f, 52f, 177f, 41f, 166f)
private val CRACK = line(168f, 64f, 150f, 70f, 141f, 62f, 126f, 79f, 114f, 74f, 104f, 90f, 92f, 88f)
private val RIVETS = listOf(Offset(74f, 32f), Offset(126f, 32f), Offset(52f, 166f), Offset(148f, 166f))
private val BANDS = listOf(Triple(-20f, 79f, -6f), Triple(85f, 115f, 0f), Triple(121f, 220f, 6f))
private val SEAMS = floatArrayOf(82f, 118f)

private val TEMPER = listOf(Color(0xFFF3DA9E), Color(0xFFD38B45), Color(0xFF8C3A62), Color(0xFF3C5BB0), Color(0xFF1D2A5A))
private val TEMPER_STOPS = floatArrayOf(0f, 0.28f, 0.52f, 0.78f, 1f)
private val Seam = Color(0xFFFF5A1F)
private val SeamCore = Color(0xFFFFC56B)

private fun cold(c: Color, k: Float): Color {
    if (k <= 0f) return c
    val l = 0.3f * c.red + 0.59f * c.green + 0.11f * c.blue
    return lerp(c, Color(l * 0.8f, l * 0.82f, l * 0.88f, c.alpha), k)
}

private class Look(
    val spread: Float, val drops: FloatArray, val heat: Float, val shimmer: Float,
    val shakeX: Float, val tilt: Float, val punch: Float, val crack: Float, val cold: Float, val alpha: Float,
)

private fun DrawScope.drawPlate(l: Look) {
    val s = minOf(size.width, size.height) / 200f
    translate((size.width - 200f * s) / 2f + l.shakeX * s, (size.height - 200f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            scale(l.punch, l.punch, pivot = Offset(100f, 100f)) {
                rotate(l.tilt, pivot = Offset(100f, 100f)) { plate(l) }
            }
        }
    }
}

private fun DrawScope.plate(l: Look) {
    val stops = TEMPER.map { cold(it, l.cold) }
    val sh = l.shimmer
    val temper = Brush.linearGradient(
        colorStops = Array(stops.size) { TEMPER_STOPS[it] to stops[it] },
        start = Offset(32f + sh, 14f - sh * 0.6f), end = Offset(168f + sh, 186f - sh * 0.6f),
    )
    val sheen = Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.35f), 0.32f to Color.White.copy(alpha = 0.16f),
        0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.4f), startX = 32f, endX = 168f,
    )
    val heat = (l.heat * (1f - l.cold * 0.85f)).coerceIn(0f, 2f)

    // heat bloom behind the plate
    if (heat > 0.05f) {
        drawCircle(
            Brush.radialGradient(listOf(Seam.copy(alpha = (0.33f * heat).coerceAtMost(0.7f)), Color.Transparent), center = Offset(100f, 110f), radius = 120f),
            radius = 120f, center = Offset(100f, 110f), alpha = l.alpha,
        )
    }
    // glowing seams between the plates
    clipPath(OUTER) {
        for (y in SEAMS) {
            val h = 7f + 5f * heat * (0.6f + 0.4f * l.spread.coerceAtMost(2f))
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Seam.copy(alpha = (0.55f + 0.35f * heat).coerceAtMost(1f)), Color.Transparent), startY = y - h, endY = y + h),
                topLeft = Offset(0f, y - h), size = androidx.compose.ui.geometry.Size(200f, 2 * h), alpha = l.alpha,
            )
            drawRect(cold(SeamCore, l.cold), topLeft = Offset(0f, y - 1f), size = androidx.compose.ui.geometry.Size(200f, 2f), alpha = (0.5f + 0.5f * heat).coerceAtMost(1f) * l.alpha)
        }
    }
    // the three plates
    BANDS.forEachIndexed { i, (y0, y1, dx) ->
        translate(dx * l.spread, l.drops[i]) {
            clipRect(-20f, y0, 220f, y1) {
                val a = l.alpha
                drawPath(OUTER, temper, alpha = a)
                drawPath(OUTER, Color.Black.copy(alpha = 0.28f), alpha = a)
                drawPath(LIT_OUT, Color.White.copy(alpha = 0.35f), style = Stroke(1.6f), alpha = a)
                drawPath(DARK_OUT, Color.Black.copy(alpha = 0.5f), style = Stroke(1.6f), alpha = a)
                drawPath(INNER, temper, alpha = a)
                drawPath(INNER, sheen, alpha = a)
                drawPath(DARK_IN, Color.Black.copy(alpha = 0.45f), style = Stroke(1.4f), alpha = a)
                drawPath(LIT_IN, Color.White.copy(alpha = 0.22f), style = Stroke(1.4f), alpha = a)
                for (r in RIVETS) {
                    drawCircle(Color(0xFF140B08), 3.6f, r, alpha = a)
                    drawCircle(Color.White.copy(alpha = 0.35f), 1.6f, r - Offset(0.8f, 0.8f), alpha = a)
                }
                val engrave = Stroke(18f, join = StrokeJoin.Miter, cap = StrokeCap.Butt)
                translate(0f, 1.6f) { drawPath(S, Color.White.copy(alpha = 0.28f), style = engrave, alpha = a) }
                drawPath(S, Color(0xFF120806).copy(alpha = 0.85f), style = engrave, alpha = a)
                if (l.crack > 0f) {
                    val pm = android.graphics.PathMeasure(CRACK.asAndroidPath(), false)
                    val seg = android.graphics.Path()
                    pm.getSegment(0f, pm.length * l.crack, seg, true)
                    val cr = seg.asComposePath()
                    drawPath(cr, Seam.copy(alpha = 0.55f), style = Stroke(6f, join = StrokeJoin.Bevel), alpha = a)
                    drawPath(cr, Color(0xFF1A0A06), style = Stroke(2.6f, join = StrokeJoin.Bevel), alpha = a)
                    drawPath(cr, SeamCore, style = Stroke(0.9f, join = StrokeJoin.Bevel), alpha = a)
                }
            }
        }
    }
}

/**
 * The armor-plate logo, alive:
 * - idle: the seams breathe and the steel's heat tint drifts
 * - you send: the plates slam together and flash
 * - a message arrives: it takes a hit and the seams flare
 * - someone's typing: the seams flicker
 * - a send fails: the plate cracks and shakes, then heals
 * - incoming call: it rattles in time with the ring; in a call it runs hot and tight
 * - offline: the plates drift apart and go cold
 * - tap it: plate up, three plates slotted in one at a time
 */
@Composable
fun ScatheLogo(modifier: Modifier = Modifier, tappable: Boolean = true, assemble: Boolean = true) {
    val conn by Repo.conn.collectAsState()
    val members by Repo.members.collectAsState()
    val me by Repo.me.collectAsState()
    val incoming by CallManager.incoming.collectAsState()
    val active by CallManager.active.collectAsState()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val typing = remember(members, tick) {
        val now = Repo.now()
        members.values.any { it.id != me?.id && it.typing > 0 && now - it.typing < 6_000 }
    }
    val offline = conn == Conn.OFFLINE || conn == Conn.ERROR
    val connecting = conn == Conn.CONNECTING
    val ringing = incoming != null && active == null
    val inCall = active != null

    val idle = rememberInfiniteTransition(label = "logo")
    val breathe by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")
    val drift by idle.animateFloat(-14f, 14f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val wobble by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "wobble")

    val coldK by animateFloatAsState(if (offline) 1f else 0f, tween(900), label = "cold")
    val baseSpread by animateFloatAsState(
        when { offline -> 2.4f; inCall -> 0.45f; else -> 1f }, spring(dampingRatio = 0.55f, stiffness = 120f), label = "spread",
    )
    val baseHeat by animateFloatAsState(
        when { offline -> 0.05f; ringing -> 1.15f; inCall -> 0.95f; typing -> 0.8f; else -> 0.5f }, tween(500), label = "heat",
    )

    val spreadFx = remember { Animatable(if (assemble) 4f else 1f) }
    val alpha = remember { Animatable(if (assemble) 0f else 1f) }
    val flash = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val tilt = remember { Animatable(0f) }
    val punch = remember { Animatable(1f) }
    val crack = remember { Animatable(0f) }
    val flick = remember { Animatable(1f) }
    val drops = remember { List(3) { Animatable(0f) } }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // on screen for the first time: the plates fly in and lock
    LaunchedEffect(Unit) {
        if (assemble) {
            launch { alpha.animateTo(1f, tween(260)) }
            spreadFx.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f))
        }
    }

    fun plateUp() = scope.launch {
        drops.forEach { it.snapTo(-170f) }
        for (i in 2 downTo 0) {
            launch {
                drops[i].animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 520f))
            }
            delay(150)
            flash.snapTo(0.9f)
            launch { flash.animateTo(0f, tween(450)) }
        }
    }

    LaunchedEffect(Unit) {
        LogoFx.events.collect { ev ->
            when (ev) {
                LogoFx.Ev.SENT -> scope.launch {
                    launch { punch.animateTo(0.92f, tween(70)); punch.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
                    spreadFx.animateTo(0f, tween(110, easing = FastOutLinearInEasing))
                    flash.snapTo(1.3f)
                    launch { flash.animateTo(0f, tween(800)) }
                    spreadFx.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 260f))
                }
                LogoFx.Ev.RECEIVED -> scope.launch {
                    launch { flash.snapTo(0.9f); flash.animateTo(0f, tween(1000)) }
                    tilt.animateTo(-7f, tween(70))
                    tilt.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 300f))
                }
                LogoFx.Ev.FAILED -> scope.launch {
                    launch { crack.animateTo(1f, tween(280)) }
                    repeat(6) { shake.animateTo(if (it % 2 == 0) 6f else -6f, tween(40)) }
                    shake.animateTo(0f, tween(60))
                    delay(3500)
                    crack.animateTo(0f, tween(700))
                }
                LogoFx.Ev.TAP -> plateUp()
            }
        }
    }

    // incoming call: rattle in time with the ring
    LaunchedEffect(ringing) {
        while (ringing) {
            repeat(10) { tilt.animateTo(if (it % 2 == 0) 5f else -5f, tween(32)) }
            tilt.animateTo(0f, tween(50))
            delay(750)
        }
        tilt.animateTo(0f, tween(100))
    }
    // someone typing: flickering seams
    LaunchedEffect(typing) {
        while (typing) {
            flick.snapTo(Random.nextFloat() * 0.9f + 0.6f)
            delay(Random.nextLong(40, 130))
        }
        flick.animateTo(1f, tween(200))
    }

    val connectingWobble = if (connecting) 0.35f * wobble else 0f
    val tap = if (tappable) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
    ) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); LogoFx.emit(LogoFx.Ev.TAP) } else Modifier

    Canvas(modifier.then(tap).semantics { contentDescription = "Scathe Chat" }) {
        drawPlate(
            Look(
                spread = (baseSpread + connectingWobble) * spreadFx.value,
                drops = FloatArray(3) { drops[it].value },
                heat = (baseHeat + 0.18f * breathe + flash.value) * flick.value,
                shimmer = drift,
                shakeX = shake.value,
                tilt = tilt.value,
                punch = punch.value,
                crack = crack.value,
                cold = coldK,
                alpha = alpha.value,
            )
        )
    }
}
