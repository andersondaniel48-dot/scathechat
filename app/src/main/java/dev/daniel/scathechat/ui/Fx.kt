package dev.daniel.scathechat.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlin.math.sin

// ---------------------------------------------------------------- ember field

private class Ember(val x: Float, val speed: Float, val size: Float, val phase: Float, val sway: Float, val hot: Float)

@Composable
private fun reducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * The one moving thing in the app: a char-black field with fire breathing at the bottom
 * and embers drifting up through it. Drawn in the draw phase only, so it never recomposes.
 */
@Composable
fun EmberBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val still = reducedMotion()
    var t by remember { mutableFloatStateOf(7f) }
    if (!still) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { t = 7f + (it - start) / 1_000_000_000f }
        }
    }
    val embers = remember {
        val r = java.util.Random(1337)
        List(46) {
            Ember(
                x = r.nextFloat(), speed = 0.025f + r.nextFloat() * 0.06f, size = 0.8f + r.nextFloat() * 2.4f,
                phase = r.nextFloat(), sway = 0.4f + r.nextFloat() * 1.4f, hot = r.nextFloat(),
            )
        }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(0f to C.Soot, 0.55f to C.Char, 1f to Color(0xFF1C0904)))
        // Fire glow: a slow, uneven breath.
        val breath = 0.85f + 0.08f * sin(t * 1.3f) + 0.05f * sin(t * 2.9f + 1f) + 0.03f * sin(t * 7.1f)
        drawRect(
            Brush.radialGradient(
                0f to C.Ember.copy(alpha = 0.42f * breath * intensity),
                0.45f to C.Blood.copy(alpha = 0.20f * breath * intensity),
                1f to Color.Transparent,
                center = Offset(w * (0.5f + 0.06f * sin(t * 0.21f)), h * 1.04f),
                radius = w * 1.05f,
            )
        )
        drawRect(
            Brush.radialGradient(
                0f to C.Flame.copy(alpha = 0.10f * breath * intensity),
                1f to Color.Transparent,
                center = Offset(w * 0.15f, h * 0.98f),
                radius = w * 0.55f,
            )
        )
        // Embers rise, sway, and burn out near the top.
        for (e in embers) {
            val life = ((t * e.speed + e.phase) % 1f)
            val y = h * (1.02f - life * 1.1f)
            val x = w * e.x + sin((t * e.sway + e.phase * 6f).toDouble()).toFloat() * 18.dp.toPx() * e.sway
            val fade = (if (life < 0.1f) life / 0.1f else 1f) * (1f - life).coerceIn(0f, 1f)
            val flicker = 0.75f + 0.25f * sin((t * 9f + e.phase * 40f).toDouble()).toFloat()
            val a = (fade * flicker * intensity).coerceIn(0f, 1f)
            val core = if (e.hot > 0.7f) C.Flame else C.Ember
            val r = e.size.dp.toPx()
            drawCircle(
                Brush.radialGradient(
                    0f to core.copy(alpha = 0.9f * a), 0.35f to core.copy(alpha = 0.35f * a), 1f to Color.Transparent,
                    center = Offset(x, y), radius = r * 4f,
                ),
                radius = r * 4f, center = Offset(x, y),
            )
        }
    }
}

// ---------------------------------------------------------------- glass

private fun Outline.path(): Path = Path().apply { addOutline(this@path) }

/**
 * Clear "liquid glass": a faint frosted fill, a white specular sheen across the top,
 * and a rim that is white at the top-left and catches fire light along the bottom edge.
 */
fun Modifier.glass(shape: Shape, fill: Color = C.Other, rim: Float = 1f): Modifier = this
    .clip(shape)
    .drawWithCache {
        val path = shape.createOutline(size, layoutDirection, this).path()
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.13f), 0.42f to Color.White.copy(alpha = 0.02f), 1f to Color.Transparent,
        )
        val warm = Brush.verticalGradient(0.55f to Color.Transparent, 1f to C.Ember.copy(alpha = 0.10f * rim))
        val edge = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.42f * rim),
            0.35f to Color.White.copy(alpha = 0.07f * rim),
            0.7f to Color.White.copy(alpha = 0.04f * rim),
            1f to C.Ember.copy(alpha = 0.7f * rim),
            start = Offset(0f, 0f), end = Offset(size.width * 0.35f, size.height),
        )
        val stroke = Stroke(width = 1.dp.toPx())
        onDrawWithContent {
            drawPath(path, fill)
            drawPath(path, warm)
            clipPath(path) { drawRect(sheen) }
            drawContent()
            drawPath(path, edge, style = stroke)
        }
    }

/** Molten glass for my own bubbles and primary buttons: fire trapped under a glossy skin. */
fun Modifier.molten(shape: Shape, alpha: Float = 0.94f): Modifier = this
    .clip(shape)
    .drawWithCache {
        val path = shape.createOutline(size, layoutDirection, this).path()
        val body = Brush.linearGradient(
            0f to C.Flame.copy(alpha = alpha), 0.38f to C.Ember.copy(alpha = alpha), 1f to C.Blood.copy(alpha = alpha),
            start = Offset(0f, 0f), end = Offset(size.width * 0.45f, size.height * 1.4f),
        )
        val sheen = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.32f), 0.38f to Color.White.copy(alpha = 0.04f), 0.39f to Color.Transparent,
        )
        val edge = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.55f), 0.5f to Color.White.copy(alpha = 0.08f), 1f to C.Flame.copy(alpha = 0.5f))
        val stroke = Stroke(width = 1.dp.toPx())
        onDrawWithContent {
            drawPath(path, body)
            clipPath(path) { drawRect(sheen, size = size.copy(height = size.height * 0.5f)) }
            drawContent()
            drawPath(path, edge, style = stroke)
        }
    }

/** Real background blur (Android 12+) for the floating bars, then the glass skin on top. */
fun Modifier.blurGlass(state: HazeState, shape: Shape, fill: Color = Color(0x52160C08)): Modifier = this
    .clip(shape)
    .hazeChild(state) {
        blurRadius = 26.dp
        noiseFactor = 0.06f
        backgroundColor = C.Char
    }
    .glass(shape, fill = fill)

// ---------------------------------------------------------------- type & buttons

/** Text that burns: flame at the top, ember in the middle, blood at the base, with a heat glow. */
fun fireText(size: TextUnit, glow: Float = 1f) = TextStyle(
    fontFamily = Display,
    fontSize = size,
    brush = Brush.verticalGradient(0f to C.Flame, 0.5f to C.Ember, 1f to C.Blood),
    shadow = Shadow(color = C.Ember.copy(alpha = 0.55f * glow), offset = Offset.Zero, blurRadius = 28f * glow),
)

@Composable
fun FireButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    padding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(if (enabled) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x14FFFFFF), rim = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else C.Faint) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    padding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier
            .defaultMinSize(minHeight = 46.dp)
            .glass(shape, fill = Color(0x1CFFFFFF))
            .clickable(onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides C.Bone) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
fun ButtonLabel(text: String, size: Int = 16) {
    Text(text, fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current)
}

/** A small glass pill for tags and status. */
@Composable
fun GlassPill(text: String, color: Color = C.Bone, hot: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = if (hot) Color.White else color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .then(if (hot) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x1FFFFFFF), rim = 0.6f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
