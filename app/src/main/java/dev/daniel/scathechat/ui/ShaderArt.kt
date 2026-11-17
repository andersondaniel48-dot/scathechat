package dev.daniel.scathechat.ui

import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import dev.daniel.scathechat.data.Repo
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GPU-rendered art for the Loadout (Android 13+). Each scene is an AGSL file in assets/shaders with the
 * shared noise/blood library in front of it. Anything that fails to load or compile reports "not available"
 * and the caller falls back to the old canvas drawing.
 */
object Shaders {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33
    private val sources = HashMap<String, String?>()

    fun source(name: String): String? = synchronized(sources) {
        sources.getOrPut(name) {
            runCatching {
                val a = Repo.app.assets
                a.open("shaders/lib.agsl").bufferedReader().readText() + "\n" + a.open("shaders/$name.agsl").bufferedReader().readText()
            }.onFailure { Log.w("Shaders", "load $name", it) }.getOrNull()
        }
    }

    private val broken = HashSet<String>()
    private val built = HashMap<String, Any>()

    /**
     * One compiled shader per scene, shared by every place that draws it. Sharing is safe: uniforms are set
     * right before each draw on the main thread, and each recorded draw snapshots the values it was given.
     */
    @RequiresApi(33)
    fun make(name: String): RuntimeShader? {
        built[name]?.let { return it as RuntimeShader }
        if (name in broken) return null
        val src = source(name) ?: return null
        return runCatching { RuntimeShader(src) }.onFailure {
            Log.w("Shaders", "compile $name", it); broken += name
        }.getOrNull()?.also { built[name] = it }
    }

    /** For callers that must not touch RuntimeShader directly (minSdk 28). */
    fun handle(name: String): ShaderHandle? = if (Build.VERSION.SDK_INT >= 33) make(name)?.let { ShaderHandle(it) } else null

    fun available(name: String?): Boolean = name != null && Build.VERSION.SDK_INT >= 33 && make(name) != null
}

class ShaderHandle(internal val impl: Any)

/** Paint a shader over the whole draw area, e.g. SrcAtop to texture only what's already drawn. */
fun DrawScope.drawShader(h: ShaderHandle, blend: BlendMode = BlendMode.SrcOver, vararg floats: Pair<String, Float>) {
    if (Build.VERSION.SDK_INT >= 33) drawShader33(h.impl as RuntimeShader, blend, floats)
}

@RequiresApi(33)
private fun DrawScope.drawShader33(sh: RuntimeShader, blend: BlendMode, floats: Array<out Pair<String, Float>>) {
    runCatching {
        sh.setFloatUniform("iResolution", size.width, size.height)
        for ((k, v) in floats) sh.setFloatUniform(k, v)
        drawRect(ShaderBrush(sh), blendMode = blend)
    }
}

/** Seconds since first composition, ticking at most [fps] times a second (0 = frozen). Read it inside draw. */
@Composable
fun rememberTicks(fps: Int, start: Float = 1.3f): State<Float> {
    val t = remember { mutableFloatStateOf(start) }
    if (fps > 0) LaunchedEffect(fps) {
        val origin = withFrameNanos { it }
        val step = 1_000_000_000L / fps
        var last = 0L
        while (true) withFrameNanos { now ->
            if (now - last >= step) { last = now; t.floatValue = start + (now - origin) / 1_000_000_000f }
        }
    }
    return t
}

/**
 * Fills [modifier]'s bounds with the named shader. [res] renders at a fraction of full resolution and scales
 * up (the scenes are soft, so 0.5 is nearly free visually and quarters the cost). The result sits in its own
 * cached layer, so scrolling over it doesn't re-run the shader; only a clock tick does.
 */
@Composable
fun ShaderArt(
    name: String,
    modifier: Modifier,
    res: Float = 0.5f,
    fps: Int = 30,
    /** Extra float uniforms, read at draw time (so state read here redraws without recomposing). */
    uniforms: () -> List<Pair<String, Float>> = { emptyList() },
) {
    if (Build.VERSION.SDK_INT < 33) return
    ShaderArt33(name, modifier, res, fps, uniforms)
}

@RequiresApi(33)
@Composable
private fun ShaderArt33(name: String, modifier: Modifier, res: Float, fps: Int, uniforms: () -> List<Pair<String, Float>>) {
    val shader = remember(name) { Shaders.make(name) } ?: return
    val time = rememberTicks(fps)
    Box(modifier) {
        Canvas(
            Modifier
                .layout { m, c ->
                    val w = if (c.hasBoundedWidth) c.maxWidth else c.minWidth
                    val h = if (c.hasBoundedHeight) c.maxHeight else c.minHeight
                    val p = m.measure(Constraints.fixed(max(1, (w * res).roundToInt()), max(1, (h * res).roundToInt())))
                    layout(w, h) { p.place(0, 0) }
                }
                .graphicsLayer {
                    scaleX = 1f / res; scaleY = 1f / res
                    transformOrigin = TransformOrigin(0f, 0f)
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            runCatching {
                shader.setFloatUniform("iResolution", size.width, size.height)
                shader.setFloatUniform("iTime", time.value)
                for ((k, v) in uniforms()) shader.setFloatUniform(k, v)
                drawRect(ShaderBrush(shader))
            }
        }
    }
}
