package dev.daniel.scathechat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.flow.collect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ================================================================ what you've equipped

data class ThemeDef(val id: String, val name: String, val ember: Color, val flame: Color, val blood: Color, val char: Color, val soot: Color)

/** Everything a player can customise. Calling card and emblem sync to the group; the rest is this phone only. */
object Style {
    val themes = listOf(
        ThemeDef("embers", "Scorched", Color(0xFFFF5A1F), Color(0xFFFFB23F), Color(0xFFA8101C), Color(0xFF0E0806), Color(0xFF1A0E0A)),
        ThemeDef("bloodbath", "Bloodbath", Color(0xFFD7101E), Color(0xFFFF6A5C), Color(0xFF5A0008), Color(0xFF0B0505), Color(0xFF170808)),
        ThemeDef("ghillie", "Ghillie", Color(0xFF8DAA3C), Color(0xFFD2DC74), Color(0xFF2F4A14), Color(0xFF080A06), Color(0xFF12160C)),
        ThemeDef("arctic", "Arctic Ops", Color(0xFF4FB3FF), Color(0xFFC4E9FF), Color(0xFF1A4C8C), Color(0xFF05080C), Color(0xFF0C1218)),
        ThemeDef("gold", "Gold Camo", Color(0xFFD9A21B), Color(0xFFFFE07A), Color(0xFF7A4A06), Color(0xFF0A0804), Color(0xFF16110A)),
        ThemeDef("darkmatter", "Dark Matter", Color(0xFFA64BFF), Color(0xFFE2AEFF), Color(0xFF3E0F7A), Color(0xFF07050C), Color(0xFF110B1A)),
        ThemeDef("nightvision", "Night Vision", Color(0xFF3DFF6E), Color(0xFFBDFFCB), Color(0xFF0E5A24), Color(0xFF030805), Color(0xFF08120A)),
    )
    private fun sp() = runCatching { Repo.app.getSharedPreferences("style", 0) }.getOrNull()
    private fun str(k: String, d: String) = mutableStateOf(sp()?.getString(k, d) ?: d)

    val themeId = str("theme", "embers")
    val background = str("bg", "embers")
    val bubble = str("bubble", "glass")
    val sendFx = str("fx", "muzzle")
    val card = str("card", "kill_confirmed")
    val emblem = str("emblem", "skull")
    /** 0 = follow the theme's accent. */
    val emblemColor = mutableIntStateOf(sp()?.getInt("emblem_color", 0) ?: 0)

    val theme: ThemeDef get() = themes.firstOrNull { it.id == themeId.value } ?: themes[0]

    fun set(state: androidx.compose.runtime.MutableState<String>, key: String, v: String, synced: Boolean = false) {
        state.value = v
        sp()?.edit()?.putString(key, v)?.apply()
        if (synced) Repo.pushProfile()
    }
    fun setEmblemColor(v: Int) {
        emblemColor.intValue = v
        sp()?.edit()?.putInt("emblem_color", v)?.apply()
        Repo.pushProfile()
    }
}

// ================================================================ catalogue

class Item(val id: String, val name: String, val blurb: String)

val CARDS = listOf(
    Item("kill_confirmed", "Kill Confirmed", "Bloodied dog tags"),
    Item("tracer_storm", "Tracer Storm", "Rounds in the dark"),
    Item("frag_out", "Frag Out", "Grenade detonation"),
    Item("glass_jaw", "Glass Jaw", "Bullet-riddled glass"),
    Item("thermal", "Thermal Kill", "Through the scope"),
    Item("body_count", "Body Count", "Tally marks in blood"),
    Item("napalm", "Napalm Dawn", "The treeline burns"),
    Item("kill_feed", "Kill Feed", "Your name, on repeat"),
    Item("armor_broke", "Armor Broke", "Plates shattered"),
    Item("redline", "Redline", "Heart monitor, flatlining"),
)

val EMBLEMS = listOf(
    Item("skull", "Skull", ""), Item("reticle", "Reticle", ""), Item("grenade", "Frag", ""), Item("knife", "Combat Knife", ""),
    Item("dogtags", "Dog Tags", ""), Item("plate", "Armor Plate", ""), Item("spade", "Death Card", ""), Item("radiation", "Fallout", ""),
    Item("rifles", "Crossed Rifles", ""), Item("chevrons", "Sergeant", ""),
)
val EMBLEM_COLORS = listOf(0, 0xFFC4161C.toInt(), 0xFFF5EADF.toInt(), 0xFFFFC23D.toInt(), 0xFF9DB35A.toInt(), 0xFF7FD2FF.toInt(),
    0xFFB48CFF.toInt(), 0xFF9DFF3D.toInt(), 0xFF9AA5B1.toInt(), 0xFF2A2A2A.toInt())

val BACKGROUNDS = listOf(
    Item("embers", "Embers", "Rising sparks"), Item("bullet_steel", "Firing Range", "Steel full of holes"),
    Item("blood_concrete", "Interrogation", "Blood on concrete"), Item("tracers", "Suppressing Fire", "Tracers overhead"),
    Item("thermal", "Thermal", "Heat signatures"), Item("tacmap", "Tac Map", "Contours and radar"), Item("smoke", "Smoke Screen", "Drifting smoke"),
)
val BUBBLES = listOf(Item("glass", "Molten Glass", "Rounded"), Item("plate", "Armor Plate", "Cut corners"), Item("tactical", "Tactical", "Hard edges"), Item("tag", "Dog Tag", "Capsule"))
val SEND_FX = listOf(Item("none", "None", ""), Item("muzzle", "Muzzle Flash", "Flash and brass"), Item("tracer", "Tracer", "A round downrange"),
    Item("frag", "Frag", "Shockwave"), Item("blood", "Splatter", "Red mist"))

// ================================================================ shared drawing helpers

@Composable
fun rememberTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1_000_000_000f }
    }
    return t
}

private val BloodDark = Color(0xFF4A0006)
private val BloodMid = Color(0xFF7E0A12)
private val BloodWet = Color(0xFFB0141E)

/** A blood spatter: a wet core, satellite drops flung outward and a few streaks. */
fun DrawScope.bloodSplat(c: Offset, r: Float, seed: Int, alpha: Float = 1f, drip: Float = 0f) {
    val rnd = Random(seed)
    repeat(7) {
        val a = rnd.nextFloat() * 2 * PI.toFloat(); val d = rnd.nextFloat() * r * 0.45f
        drawCircle(BloodDark.copy(alpha = alpha), r * (0.35f + rnd.nextFloat() * 0.35f), c + Offset(cos(a) * d, sin(a) * d))
    }
    repeat(5) {
        val a = rnd.nextFloat() * 2 * PI.toFloat(); val d = rnd.nextFloat() * r * 0.3f
        drawCircle(BloodMid.copy(alpha = alpha), r * (0.18f + rnd.nextFloat() * 0.22f), c + Offset(cos(a) * d, sin(a) * d))
    }
    repeat(26) {
        val a = rnd.nextFloat() * 2 * PI.toFloat(); val d = r * (0.8f + rnd.nextFloat() * 1.9f)
        val s = r * (0.03f + rnd.nextFloat() * 0.1f) * (1.6f - d / (r * 2.7f))
        val p = c + Offset(cos(a) * d, sin(a) * d)
        drawCircle(BloodMid.copy(alpha = alpha), max(s, 0.6f), p)
        if (rnd.nextFloat() < 0.35f) drawLine(BloodDark.copy(alpha = alpha * 0.9f), c + Offset(cos(a) * r * 0.5f, sin(a) * r * 0.5f), p, strokeWidth = max(s * 0.6f, 0.8f), cap = StrokeCap.Round)
    }
    drawCircle(Color.White.copy(alpha = 0.10f * alpha), r * 0.18f, c + Offset(-r * 0.15f, -r * 0.18f))
    if (drip > 0f) repeat(3) { i ->
        val x = c.x + (i - 1) * r * 0.35f + rnd.nextFloat() * r * 0.1f
        val len = r * (0.6f + rnd.nextFloat()) * drip
        drawLine(BloodDark.copy(alpha = alpha), Offset(x, c.y), Offset(x, c.y + len), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
        drawCircle(BloodMid.copy(alpha = alpha), r * 0.07f, Offset(x, c.y + len))
    }
}

/** A bullet hole: torn rim, black core, and (on glass) radial and ring cracks. */
fun DrawScope.bulletHole(c: Offset, r: Float, seed: Int, glass: Boolean, alpha: Float = 1f) {
    val rnd = Random(seed)
    if (glass) {
        repeat(9) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            var p = c
            val steps = 4
            repeat(steps) { k ->
                val len = r * (1.1f + rnd.nextFloat() * 1.6f)
                val aa = a + (rnd.nextFloat() - 0.5f) * 0.35f
                val q = p + Offset(cos(aa) * len, sin(aa) * len)
                drawLine(Color.White.copy(alpha = (0.55f - k * 0.1f) * alpha), p, q, strokeWidth = 1.1f)
                p = q
            }
        }
        repeat(3) { k ->
            val rr = r * (1.6f + k * 1.3f)
            drawCircle(Color.White.copy(alpha = (0.18f - k * 0.04f) * alpha), rr, c, style = Stroke(0.9f))
        }
        drawCircle(Color.White.copy(alpha = 0.25f * alpha), r * 1.3f, c)
    } else {
        drawCircle(Color(0xFF8A8F96).copy(alpha = 0.55f * alpha), r * 1.6f, c)
        drawCircle(Color(0xFF2B2F35).copy(alpha = alpha), r * 1.35f, c)
        repeat(8) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            drawLine(Color(0xFFB9BEC6).copy(alpha = 0.5f * alpha), c + Offset(cos(a) * r * 1.2f, sin(a) * r * 1.2f), c + Offset(cos(a) * r * 1.9f, sin(a) * r * 1.9f), strokeWidth = 1.2f)
        }
    }
    drawCircle(Color.Black.copy(alpha = alpha), r, c)
    drawCircle(Color(0xFF3A1A08).copy(alpha = 0.6f * alpha), r * 0.7f, c + Offset(r * 0.15f, r * 0.15f))
}

private fun DrawScope.noiseDots(seed: Int, n: Int, color: Color, maxR: Float) {
    val rnd = Random(seed)
    repeat(n) { drawCircle(color.copy(alpha = color.alpha * (0.3f + rnd.nextFloat() * 0.7f)), rnd.nextFloat() * maxR + 0.4f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
}

/**
 * Draws text at [topLeft] without the canvas-bounds constraint of DrawScope.drawText, which throws when the
 * text starts at or past the canvas edge (e.g. kill-feed rows scrolling out). Off-canvas text is just skipped.
 */
fun DrawScope.safeText(m: androidx.compose.ui.text.TextMeasurer, text: String, topLeft: Offset, style: TextStyle) {
    if (topLeft.x >= size.width || topLeft.y >= size.height) return
    val lay = m.measure(text, style, maxLines = 1)
    if (topLeft.x + lay.size.width <= 0f || topLeft.y + lay.size.height <= 0f) return
    drawText(lay, topLeft = topLeft)
}

// ================================================================ emblems

@Composable
fun Emblem(id: String?, color: Int?, size: Dp, modifier: Modifier = Modifier) {
    val col = if (color == null || color == 0) C.Ember else Color(color)
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val s = this.size.minDimension / 100f
        scale(s, s, pivot = Offset.Zero) { drawEmblem(id ?: "skull", col) }
    }
}

/** Emblems are drawn in a 100x100 box; holes are punched out so they sit on anything. */
private fun DrawScope.drawEmblem(id: String, col: Color) {
    val clear = Color.Black
    val hole = BlendMode.Clear
    val shade = Brush.verticalGradient(listOf(col, col.copy(red = col.red * 0.62f, green = col.green * 0.62f, blue = col.blue * 0.62f)), 0f, 100f)
    when (id) {
        "skull" -> {
            drawOval(shade, Offset(18f, 8f), Size(64f, 60f))
            drawRoundRect(shade, Offset(30f, 52f), Size(40f, 34f), CornerRadius(8f))
            drawOval(clear, Offset(28f, 34f), Size(18f, 20f), blendMode = hole)
            drawOval(clear, Offset(54f, 34f), Size(18f, 20f), blendMode = hole)
            drawPath(Path().apply { moveTo(50f, 54f); lineTo(44.5f, 65f); lineTo(55.5f, 65f); close() }, clear, blendMode = hole)
            for (x in listOf(39f, 46f, 54f, 61f)) drawRect(clear, Offset(x - 1.5f, 72f), Size(3f, 14f), blendMode = hole)
            drawRect(clear, Offset(32f, 71f), Size(36f, 2.5f), blendMode = hole)
            drawLine(clear, Offset(30f, 20f), Offset(42f, 30f), strokeWidth = 2f, blendMode = hole) // crack
            drawLine(clear, Offset(42f, 30f), Offset(40f, 36f), strokeWidth = 2f, blendMode = hole)
        }
        "reticle" -> {
            drawCircle(shade, 36f, Offset(50f, 50f), style = Stroke(7f))
            drawCircle(shade, 5f, Offset(50f, 50f))
            for (a in 0 until 4) rotate(a * 90f, Offset(50f, 50f)) {
                drawRect(shade, Offset(47f, 2f), Size(6f, 30f))
                drawRect(shade, Offset(48.5f, 36f), Size(3f, 6f))
            }
            for (a in listOf(45f, 135f, 225f, 315f)) rotate(a, Offset(50f, 50f)) { drawRect(shade, Offset(48.5f, 18f), Size(3f, 6f)) }
        }
        "grenade" -> {
            drawOval(shade, Offset(28f, 32f), Size(44f, 58f))
            drawRect(shade, Offset(41f, 20f), Size(18f, 16f))
            drawPath(Path().apply { moveTo(56f, 22f); quadraticTo(76f, 22f, 76f, 44f); lineTo(70f, 46f); quadraticTo(70f, 30f, 56f, 30f); close() }, shade)
            drawCircle(shade, 8f, Offset(36f, 18f), style = Stroke(3.5f))
            for (y in listOf(46f, 60f, 74f)) drawRect(clear, Offset(26f, y), Size(48f, 3f), blendMode = hole)
            for (x in listOf(42f, 56f)) drawRect(clear, Offset(x, 32f), Size(3f, 58f), blendMode = hole)
        }
        "knife" -> rotate(-40f, Offset(50f, 50f)) {
            drawPath(Path().apply { moveTo(50f, 2f); quadraticTo(60f, 20f, 59f, 56f); lineTo(41f, 56f); lineTo(41f, 18f); close() }, shade)
            drawLine(clear, Offset(45f, 22f), Offset(45f, 52f), strokeWidth = 2f, blendMode = hole) // fuller
            drawRoundRect(shade, Offset(32f, 56f), Size(36f, 7f), CornerRadius(3f))
            drawRoundRect(shade, Offset(42f, 63f), Size(16f, 31f), CornerRadius(5f))
            for (y in listOf(69f, 76f, 83f)) drawRect(clear, Offset(42f, y), Size(16f, 2.5f), blendMode = hole)
            drawCircle(shade, 5f, Offset(50f, 95f))
        }
        "dogtags" -> {
            for ((dx, rot) in listOf(-8f to -12f, 10f to 10f)) rotate(rot, Offset(50f + dx, 55f)) {
                drawRoundRect(shade, Offset(30f + dx, 26f), Size(40f, 60f), CornerRadius(14f))
                drawCircle(clear, 4f, Offset(50f + dx, 34f), blendMode = hole)
                for (y in listOf(48f, 56f, 64f)) drawRect(clear, Offset(38f + dx, y), Size(24f, 3f), blendMode = hole)
            }
            for (i in 0 until 9) drawCircle(shade, 2.2f, Offset(50f + (i - 4) * 4.5f, 22f - 10f * sin(i / 8f * PI.toFloat())))
        }
        "plate" -> {
            val outer = Path().apply {
                moveTo(36f, 8f); lineTo(64f, 8f); lineTo(80f, 24f); lineTo(80f, 86f); lineTo(72f, 94f); lineTo(28f, 94f); lineTo(20f, 86f); lineTo(20f, 24f); close()
            }
            drawPath(outer, shade)
            drawRect(clear, Offset(16f, 38f), Size(68f, 3.5f), blendMode = hole)
            drawRect(clear, Offset(16f, 60f), Size(68f, 3.5f), blendMode = hole)
            drawPath(Path().apply { moveTo(64f, 28f); lineTo(42f, 28f); lineTo(34f, 36f); lineTo(34f, 46f); lineTo(42f, 52f); lineTo(58f, 52f); lineTo(66f, 58f); lineTo(66f, 68f); lineTo(58f, 76f); lineTo(36f, 76f) },
                clear, style = Stroke(7f), blendMode = hole)
        }
        "spade" -> {
            drawCircle(shade, 20f, Offset(33f, 56f)); drawCircle(shade, 20f, Offset(67f, 56f))
            drawPath(Path().apply { moveTo(50f, 6f); lineTo(86f, 52f); lineTo(14f, 52f); close() }, shade)
            drawPath(Path().apply { moveTo(50f, 60f); lineTo(62f, 94f); lineTo(38f, 94f); close() }, shade)
            drawCircle(clear, 7f, Offset(50f, 48f), blendMode = hole)
        }
        "radiation" -> {
            drawCircle(shade, 47f, Offset(50f, 50f))
            for (a in listOf(-30f, 90f, 210f)) drawArc(clear, a - 30f + 60f, 60f, true, Offset(8f, 8f), Size(84f, 84f), blendMode = hole)
            drawCircle(clear, 15f, Offset(50f, 50f), blendMode = hole)
            drawCircle(shade, 9f, Offset(50f, 50f))
            drawCircle(clear, 47f, Offset(50f, 50f), style = Stroke(3f), blendMode = hole)
        }
        "rifles" -> {
            for (rot in listOf(35f, -35f)) rotate(rot, Offset(50f, 50f)) {
                drawRect(shade, Offset(47f, 4f), Size(5f, 62f)) // barrel
                drawRoundRect(shade, Offset(44f, 34f), Size(11f, 30f), CornerRadius(2f)) // receiver
                drawRect(shade, Offset(45f, 18f), Size(9f, 6f)) // front sight block
                drawPath(Path().apply { moveTo(44f, 64f); lineTo(55f, 64f); lineTo(58f, 96f); lineTo(41f, 96f); close() }, shade) // stock
                drawRect(shade, Offset(55f, 50f), Size(8f, 4f)) // magazine
            }
        }
        "chevrons" -> {
            for (k in 0 until 3) {
                val y = 14f + k * 26f
                drawPath(Path().apply { moveTo(10f, y + 22f); lineTo(50f, y); lineTo(90f, y + 22f); lineTo(90f, y + 36f); lineTo(50f, y + 14f); lineTo(10f, y + 36f); close() }, shade)
            }
        }
        else -> drawCircle(shade, 40f, Offset(50f, 50f))
    }
}

// ================================================================ calling cards

/** The animated banner behind a player's name. */
@Composable
fun CallingCard(card: String?, name: String, sub: String?, emblem: String?, emblemColor: Int?, modifier: Modifier = Modifier, animate: Boolean = true) {
    val t = if (animate) rememberTime() else 2.2f
    val measurer = rememberTextMeasurer()
    Box(modifier.aspectRatio(3f).clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))) {
        Canvas(Modifier.fillMaxSize()) { drawCard(card ?: "kill_confirmed", t, name, measurer) }
        // legibility scrim under the name
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xB3000000), 0.6f to Color.Transparent)))
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(emblem, emblemColor, 46.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name.uppercase(), color = Color.White, fontFamily = Display, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun DrawScope.drawCard(id: String, t: Float, name: String, measurer: androidx.compose.ui.text.TextMeasurer) {
    val w = size.width; val h = size.height
    val acc = C.Ember; val hot = C.Flame
    when (id) {
        "kill_confirmed" -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF23262B), Color(0xFF0D0E10)), Offset.Zero, Offset(w, h)))
            noiseDots(11, 260, Color(0x22FFFFFF), 1.2f)
            bloodSplat(Offset(w * 0.78f, h * 0.42f), h * 0.32f, 7, drip = (0.6f + 0.4f * ((t * 0.08f) % 1f)))
            val swing = sin(t * 1.4f) * 5f
            for ((dx, base) in listOf(0.62f to -10f, 0.70f to 14f)) rotate(base + swing, Offset(w * dx, h * 0.05f)) {
                val tl = Offset(w * dx - h * 0.16f, h * 0.30f)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFD6D9DE), Color(0xFF7D838C)), tl.y, tl.y + h * 0.5f), tl, Size(h * 0.32f, h * 0.5f), CornerRadius(h * 0.08f))
                drawCircle(Color(0xFF1A1C1F), h * 0.025f, Offset(w * dx, tl.y + h * 0.06f))
                for (k in 0 until 3) drawRect(Color(0x55303338), Offset(tl.x + h * 0.06f, tl.y + h * (0.16f + k * 0.08f)), Size(h * 0.2f, h * 0.025f))
                bloodSplat(Offset(tl.x + h * 0.2f, tl.y + h * 0.36f), h * 0.07f, 21, alpha = 0.9f)
                for (i in 0 until 10) drawCircle(Color(0xFFB0B5BC), h * 0.012f, Offset(w * dx + (i - 5) * h * 0.004f, h * (0.05f + i * 0.026f)))
            }
        }
        "tracer_storm" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF040506))))
            noiseDots(3, 60, Color(0x18FFFFFF), 1f)
            val rnd = Random(4)
            repeat(14) { i ->
                val speed = 0.55f + rnd.nextFloat() * 0.7f
                val ph = rnd.nextFloat()
                val y0 = h * (0.15f + rnd.nextFloat() * 0.85f)
                val ang = -0.18f - rnd.nextFloat() * 0.18f
                val p = ((t * speed + ph) % 1f)
                val x = -w * 0.3f + p * w * 1.6f
                val y = y0 + x * ang
                val len = w * 0.16f
                val tail = Offset(x - len, y - len * ang)
                val col = if (i % 3 == 0) Color(0xFFFF3B2F) else hot
                drawLine(col.copy(alpha = 0.18f), tail, Offset(x, y), strokeWidth = 7f, cap = StrokeCap.Round)
                drawLine(col, tail + (Offset(x, y) - tail) * 0.4f, Offset(x, y), strokeWidth = 2.4f, cap = StrokeCap.Round)
                drawCircle(Color.White, 1.8f, Offset(x, y))
            }
            val flick = 0.5f + 0.5f * abs(sin(t * 23f))
            drawCircle(Brush.radialGradient(listOf(hot.copy(alpha = 0.7f * flick), Color.Transparent), Offset(0f, h * 0.9f), h * 0.6f), h * 0.6f, Offset(0f, h * 0.9f))
        }
        "frag_out" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A120C), Color(0xFF070504))))
            val cyc = (t % 2.6f) / 2.6f
            val c = Offset(w * 0.74f, h * 0.62f)
            val boom = (cyc / 0.18f).coerceIn(0f, 1f)
            val fade = 1f - ((cyc - 0.18f) / 0.82f).coerceIn(0f, 1f)
            drawCircle(Brush.radialGradient(listOf(Color(0x66444444), Color.Transparent), c, h * (0.6f + cyc * 0.8f)), h * (0.6f + cyc * 0.8f), c)
            drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = fade), 0.2f to hot.copy(alpha = fade), 0.55f to acc.copy(alpha = 0.8f * fade), 1f to Color.Transparent, center = c, radius = h * 0.75f * boom + 1f), h * 0.75f * boom + 1f, c)
            drawCircle(Color.White.copy(alpha = 0.5f * fade), h * (0.2f + cyc * 1.6f), c, style = Stroke(2.5f))
            val rnd = Random(9)
            repeat(30) {
                val a = rnd.nextFloat() * PI.toFloat() + PI.toFloat()
                val sp = 0.4f + rnd.nextFloat()
                val d = h * sp * cyc * 1.5f
                val p = c + Offset(cos(a) * d, sin(a) * d + cyc * cyc * h * 0.8f)
                drawLine(hot.copy(alpha = fade), p, p - Offset(cos(a), sin(a)) * 6f, strokeWidth = 2f)
            }
            drawRect(Color(0xFF0A0705), Offset(0f, h * 0.84f), Size(w, h * 0.16f))
        }
        "glass_jaw" -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF1C2A33), Color(0xFF070B0E)), Offset.Zero, Offset(w, h)))
            val sweep = ((t * 0.25f) % 1.6f) - 0.3f
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x22FFFFFF), Color.Transparent), Offset(w * sweep, 0f), Offset(w * sweep + w * 0.25f, h)))
            bulletHole(Offset(w * 0.62f, h * 0.35f), h * 0.05f, 1, glass = true)
            bulletHole(Offset(w * 0.82f, h * 0.65f), h * 0.045f, 2, glass = true)
            bulletHole(Offset(w * 0.93f, h * 0.22f), h * 0.04f, 3, glass = true)
        }
        "thermal" -> {
            drawRect(Color(0xFF1A0626))
            val blobs = listOf(Triple(0.2f, 0.7f, 0xFF4A1070), Triple(0.5f, 0.3f, 0xFF7A1A8C), Triple(0.35f, 0.9f, 0xFFB0306A))
            blobs.forEachIndexed { i, (x, y, c) ->
                val o = Offset(w * (x + 0.05f * sin(t * 0.4f + i)), h * (y + 0.1f * cos(t * 0.3f + i)))
                drawCircle(Brush.radialGradient(listOf(Color(c), Color.Transparent), o, h * 0.9f), h * 0.9f, o)
            }
            val body = Offset(w * 0.78f + sin(t * 0.7f) * w * 0.03f, h * 0.55f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFE0), Color(0xFFFFC23D), Color(0xFFFF5A1F), Color.Transparent), body - Offset(0f, h * 0.27f), h * 0.16f), h * 0.16f, body - Offset(0f, h * 0.27f))
            drawRoundRect(Brush.radialGradient(listOf(Color(0xFFFFF2A8), Color(0xFFFF7A2A), Color(0x00FF3B00)), body + Offset(0f, h * 0.12f), h * 0.42f),
                body - Offset(h * 0.17f, h * 0.12f), Size(h * 0.34f, h * 0.7f), CornerRadius(h * 0.12f))
            val rc = Offset(w * 0.78f, h * 0.36f)
            drawCircle(Color.White.copy(alpha = 0.8f), h * 0.3f, rc, style = Stroke(1.4f))
            drawLine(Color.White.copy(alpha = 0.8f), rc - Offset(h * 0.42f, 0f), rc - Offset(h * 0.06f, 0f), 1.4f)
            drawLine(Color.White.copy(alpha = 0.8f), rc + Offset(h * 0.06f, 0f), rc + Offset(h * 0.42f, 0f), 1.4f)
            drawLine(Color.White.copy(alpha = 0.8f), rc - Offset(0f, h * 0.42f), rc - Offset(0f, h * 0.06f), 1.4f)
            safeText(measurer, "${212 + (t * 3).toInt() % 7}m", rc + Offset(h * 0.32f, h * 0.05f), TextStyle(color = Color.White, fontSize = 9.sp))
        }
        "body_count" -> {
            drawRect(Brush.linearGradient(listOf(Color(0xFF5A5650), Color(0xFF2E2B27)), Offset.Zero, Offset(w, h)))
            noiseDots(5, 600, Color(0x33000000), 1.6f)
            noiseDots(6, 300, Color(0x22FFFFFF), 1f)
            val rnd = Random(12)
            val groups = 3
            val shown = ((t * 2.5f) % 22f).toInt().coerceAtMost(15)
            var n = 0
            for (g in 0 until groups) for (k in 0 until 5) {
                if (n++ >= shown) continue
                val gx = w * 0.42f + g * w * 0.18f
                if (k < 4) {
                    val x = gx + k * w * 0.03f
                    drawLine(BloodWet, Offset(x + rnd.nextFloat() * 3f, h * 0.2f), Offset(x - 2f + rnd.nextFloat() * 3f, h * 0.78f), strokeWidth = 4.5f, cap = StrokeCap.Round)
                    drawLine(BloodDark, Offset(x, h * 0.78f), Offset(x, h * (0.8f + rnd.nextFloat() * 0.12f)), strokeWidth = 2f, cap = StrokeCap.Round)
                } else drawLine(BloodWet, Offset(gx - w * 0.015f, h * 0.7f), Offset(gx + w * 0.11f, h * 0.28f), strokeWidth = 4.5f, cap = StrokeCap.Round)
            }
        }
        "napalm" -> {
            drawRect(Brush.verticalGradient(0f to Color(0xFF050302), 0.5f to Color(0xFF2A0E04), 1f to Color(0xFF6A1E04)))
            for (i in 0 until 3) {
                val o = Offset(w * (0.2f + i * 0.3f) + sin(t * 0.2f + i) * 20f, h * (0.4f - ((t * 0.05f + i * 0.3f) % 1f) * 0.5f))
                drawCircle(Brush.radialGradient(listOf(Color(0x55222222), Color.Transparent), o, h * 0.6f), h * 0.6f, o)
            }
            val n = 26
            for (layer in 0 until 3) {
                val col = listOf(C.Blood, acc, hot)[layer]
                val p = Path().apply {
                    moveTo(0f, h)
                    for (i in 0..n) {
                        val x = w * i / n
                        val fl = sin(t * (5f + layer) + i * 1.7f) * 0.5f + sin(t * 9f + i * 3.1f) * 0.5f
                        val hh = h * (0.55f - layer * 0.12f + 0.12f * fl)
                        lineTo(x, h - hh * (if (i % 2 == 0) 1f else 0.6f))
                    }
                    lineTo(w, h); close()
                }
                drawPath(p, col.copy(alpha = 0.85f))
            }
            // treeline silhouettes against the fire
            val tr = Random(2)
            for (i in 0 until 18) {
                val x = w * i / 17f; val th = h * (0.25f + tr.nextFloat() * 0.25f)
                drawPath(Path().apply { moveTo(x - 8f, h); lineTo(x, h - th); lineTo(x + 8f, h); close() }, Color(0xFF050302))
            }
        }
        "kill_feed" -> {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF15181C), Color(0xFF08090A))))
            val enemies = listOf("ENEMY", "ROGUE OPERATOR", "LIEUTENANT", "SNIPER", "HEAVY", "MERC", "SHOCK TROOP")
            val rowH = h * 0.24f
            val scroll = (t * 0.6f) % 1f
            for (i in -1 until 5) {
                val y = h * 0.08f + (i - scroll) * rowH
                if (y < -rowH || y > h) continue
                val alpha = (1f - (y / h)).coerceIn(0.25f, 1f)
                val left = w * 0.42f
                drawRect(Color(0x66000000), Offset(left, y), Size(w * 0.56f, rowH * 0.8f))
                val st = TextStyle(color = Color(0xFF7FD2FF).copy(alpha = alpha), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val lay = measurer.measure(name.uppercase().take(12), st)
                drawText(lay, topLeft = Offset(left + 6f, y + (rowH * 0.8f - lay.size.height) / 2))
                val ix = left + 12f + lay.size.width
                translate(ix, y + rowH * 0.1f) { scale(rowH * 0.6f / 100f, rowH * 0.6f / 100f, Offset.Zero) { drawEmblem("rifles", Color.White.copy(alpha = alpha)) } }
                val e = enemies[((i + (t * 0.6f).toInt()) % enemies.size + enemies.size) % enemies.size]
                safeText(measurer, e, Offset(ix + rowH * 0.7f, y + (rowH * 0.8f - lay.size.height) / 2), TextStyle(color = Color(0xFFFF5A5A).copy(alpha = alpha), fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
        "armor_broke" -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF2A1A12), Color(0xFF080504)), Offset(w * 0.75f, h * 0.5f), w * 0.6f))
            val cyc = (t % 3f) / 3f
            val burst = if (cyc < 0.5f) 0f else ((cyc - 0.5f) / 0.5f)
            val e = 1f - (1f - burst) * (1f - burst)
            val c = Offset(w * 0.75f, h * 0.5f)
            val pw = h * 0.42f; val ph = h * 0.62f
            val pieces = listOf(Triple(-1f, -1.2f, -25f), Triple(1.2f, -0.6f, 30f), Triple(-0.8f, 1.1f, 40f), Triple(1f, 1.3f, -35f))
            pieces.forEachIndexed { i, (dx, dy, rot) ->
                val o = c + Offset(dx * e * h * 0.6f, dy * e * h * 0.4f + e * e * h * 0.2f)
                rotate(rot * e, o) {
                    val qx = if (i % 2 == 0) -pw / 2 else 0f; val qy = if (i < 2) -ph / 2 else 0f
                    drawRect(Brush.linearGradient(listOf(Color(0xFFD38B45), Color(0xFF8C3A62), Color(0xFF3C5BB0)), o + Offset(-pw / 2, -ph / 2), o + Offset(pw / 2, ph / 2)),
                        o + Offset(qx, qy), Size(pw / 2 - 2f, ph / 2 - 2f))
                }
            }
            if (burst > 0f) {
                val rnd = Random(4)
                repeat(22) {
                    val a = rnd.nextFloat() * 2 * PI.toFloat(); val d = h * (0.2f + rnd.nextFloat() * 0.8f) * e
                    drawCircle(hot.copy(alpha = 1f - e), 1.8f, c + Offset(cos(a) * d, sin(a) * d))
                }
                drawCircle(Color.White.copy(alpha = (1f - e) * 0.7f), h * 0.6f * e + 1f, c, style = Stroke(2f))
            }
        }
        "redline" -> {
            drawRect(Color(0xFF040806))
            val grid = Color(0x2233FF66)
            var gx = 0f; while (gx < w) { drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f); gx += h * 0.15f }
            var gy = 0f; while (gy < h) { drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f); gy += h * 0.15f }
            val cyc = (t % 6f) / 6f
            val col = if (cyc > 0.7f) Color(0xFFFF2A2A) else Color(0xFF3DFF6E)
            val path = Path()
            val mid = h * 0.55f
            val head = cyc * w * 1.25f
            var x = 0f
            path.moveTo(0f, mid)
            while (x < min(head, w)) {
                val beat = (x / (w * 0.22f)) % 1f
                val flat = x > w * 0.7f * 1.25f * 0.7f / 0.7f * 0.7f
                val y = if (flat) mid else when {
                    beat in 0.40f..0.44f -> mid - h * 0.08f
                    beat in 0.44f..0.47f -> mid + h * 0.1f
                    beat in 0.47f..0.50f -> mid - h * 0.38f
                    beat in 0.50f..0.53f -> mid + h * 0.18f
                    beat in 0.62f..0.70f -> mid - h * 0.07f
                    else -> mid
                }
                path.lineTo(x, y); x += 2f
            }
            drawPath(path, col.copy(alpha = 0.3f), style = Stroke(5f))
            drawPath(path, col, style = Stroke(1.8f))
            if (cyc > 0.75f && (t * 3).toInt() % 2 == 0) safeText(measurer, "FLATLINE", Offset(w * 0.72f, h * 0.12f), TextStyle(color = Color(0xFFFF2A2A), fontSize = 11.sp, fontFamily = Display))
        }
        else -> drawRect(C.Char)
    }
}

// ================================================================ chat backgrounds

@Composable
fun ChatBackground(modifier: Modifier = Modifier) {
    val id by Style.background
    ChatBackgroundImpl(id, modifier)
}

@Composable
fun ChatBackgroundImpl(id: String, modifier: Modifier) {
    if (id == "embers") { EmberBackground(modifier); return }
    val t = rememberTime()
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (id) {
            "bullet_steel" -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF2A2E33), Color(0xFF15171A), Color(0xFF22262B)), Offset.Zero, Offset(w, h)))
                var y = 0f; val rnd = Random(3)
                while (y < h) { drawLine(Color(0x0DFFFFFF), Offset(0f, y), Offset(w, y + rnd.nextFloat() * 6f), 1f); y += 3f }
                val holes = Random(8)
                repeat(14) { i -> bulletHole(Offset(holes.nextFloat() * w, holes.nextFloat() * h), 5f + holes.nextFloat() * 6f, i, glass = false, alpha = 0.85f) }
                val sw = ((t * 0.08f) % 1.4f) - 0.2f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color(0x14FFFFFF), Color.Transparent), Offset(w * sw, 0f), Offset(w * sw + w * 0.4f, h)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, C.Char.copy(alpha = 0.7f)), h * 0.5f, h))
            }
            "blood_concrete" -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF3A3733), Color(0xFF1E1C1A)), Offset.Zero, Offset(w, h)))
                noiseDots(5, 1400, Color(0x30000000), 2f)
                noiseDots(6, 700, Color(0x14FFFFFF), 1.2f)
                bloodSplat(Offset(w * 0.82f, h * 0.18f), w * 0.11f, 1, alpha = 0.85f, drip = 1.4f)
                bloodSplat(Offset(w * 0.12f, h * 0.55f), w * 0.08f, 2, alpha = 0.75f, drip = 0.8f)
                bloodSplat(Offset(w * 0.6f, h * 0.86f), w * 0.14f, 3, alpha = 0.7f)
                drawRect(Brush.radialGradient(listOf(Color(0x33FFE8C0), Color.Transparent), Offset(w * 0.5f, -h * 0.05f), w * (0.9f + 0.03f * sin(t * 6f))))
                drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x99000000))))
            }
            "tracers" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF07090C), C.Char)))
                val rnd = Random(4)
                repeat(10) { i ->
                    val speed = 0.12f + rnd.nextFloat() * 0.18f
                    val ph = rnd.nextFloat()
                    val x0 = rnd.nextFloat() * w
                    val p = ((t * speed + ph) % 1f)
                    val y = h * 1.1f - p * h * 1.3f
                    val x = x0 + (h * 1.1f - y) * 0.35f
                    val len = h * 0.12f
                    val col = if (i % 4 == 0) Color(0xFFFF3B2F) else C.Flame
                    drawLine(col.copy(alpha = 0.15f), Offset(x - len * 0.35f, y + len), Offset(x, y), 6f, StrokeCap.Round)
                    drawLine(col, Offset(x - len * 0.17f, y + len * 0.5f), Offset(x, y), 2f, StrokeCap.Round)
                }
                val flick = 0.6f + 0.4f * abs(sin(t * 17f))
                drawRect(Brush.radialGradient(listOf(C.Ember.copy(alpha = 0.25f * flick), Color.Transparent), Offset(w * 0.5f, h * 1.05f), w))
            }
            "thermal" -> {
                drawRect(Color(0xFF12041C))
                for (i in 0 until 6) {
                    val o = Offset(w * (0.5f + 0.4f * sin(t * 0.07f * (i + 1) + i)), h * (0.5f + 0.45f * cos(t * 0.05f * (i + 2) + i * 2)))
                    val c = listOf(0xFF4A1070, 0xFF7A1A8C, 0xFFB0306A, 0xFF5A1478, 0xFFE0602A, 0xFF3A0C5A)[i]
                    drawCircle(Brush.radialGradient(listOf(Color(c).copy(alpha = 0.7f), Color.Transparent), o, w * 0.55f), w * 0.55f, o)
                }
                noiseDots(((t * 12).toInt()) % 50, 500, Color(0x10FFFFFF), 1f)
            }
            "tacmap" -> {
                drawRect(Color(0xFF0B110D))
                val line = C.Ember.copy(alpha = 0.10f)
                for (k in 0 until 14) {
                    val p = Path()
                    val cy = h * (k / 13f)
                    p.moveTo(0f, cy)
                    var x = 0f
                    while (x <= w) { p.lineTo(x, cy + 18f * sin(x / w * 6f + k * 0.9f) + 10f * sin(x / w * 13f + k)); x += 8f }
                    drawPath(p, line, style = Stroke(1.2f))
                }
                val g = w / 6f
                var gx = 0f; while (gx < w) { drawLine(Color(0x14FFFFFF), Offset(gx, 0f), Offset(gx, h), 1f); gx += g }
                var gy = 0f; while (gy < h) { drawLine(Color(0x14FFFFFF), Offset(0f, gy), Offset(w, gy), 1f); gy += g }
                val c = Offset(w * 0.5f, h * 0.55f)
                val a = (t * 0.6f) % (2 * PI.toFloat())
                drawArc(Brush.sweepGradient(listOf(Color.Transparent, C.Ember.copy(alpha = 0.18f)), c), a * 180f / PI.toFloat() - 60f, 60f, true, c - Offset(w * 0.6f, w * 0.6f), Size(w * 1.2f, w * 1.2f))
                for (r in 1..3) drawCircle(C.Ember.copy(alpha = 0.10f), w * 0.2f * r, c, style = Stroke(1f))
            }
            "smoke" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF16181A), C.Char)))
                for (i in 0 until 9) {
                    val ph = i * 0.37f
                    val o = Offset(w * ((i * 0.23f + t * 0.012f * (1 + i % 3)) % 1.4f - 0.2f), h * (0.15f + (i % 5) * 0.2f + 0.03f * sin(t * 0.2f + ph)))
                    drawCircle(Brush.radialGradient(listOf(Color(0x30B0B0B0), Color.Transparent), o, w * 0.45f), w * 0.45f, o)
                }
                drawRect(Brush.radialGradient(listOf(C.Ember.copy(alpha = 0.18f), Color.Transparent), Offset(w * 0.5f, h * 1.05f), w))
            }
            else -> drawRect(C.Char)
        }
    }
}

// ================================================================ bubble shapes

fun bubbleShape(mine: Boolean, first: Boolean, last: Boolean): Shape = when (Style.bubble.value) {
    "plate" -> if (mine) CutCornerShape(topStart = 12.dp, topEnd = if (first) 12.dp else 3.dp, bottomEnd = if (last) 3.dp else 3.dp, bottomStart = 12.dp)
        else CutCornerShape(topStart = if (first) 12.dp else 3.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 3.dp)
    "tactical" -> RoundedCornerShape(4.dp)
    "tag" -> RoundedCornerShape(22.dp)
    else -> {
        val big = 18.dp; val small = 5.dp
        if (mine) RoundedCornerShape(topStart = big, topEnd = if (first) big else small, bottomEnd = if (last) big else small, bottomStart = big)
        else RoundedCornerShape(topStart = if (first) big else small, topEnd = big, bottomEnd = big, bottomStart = if (last) big else small)
    }
}

// ================================================================ send effects

/** Plays your equipped send effect every time you send. Draws over the chat, never blocks touches. */
@Composable
fun SendFxOverlay(modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        LogoFx.events.collect { e ->
            if (e == LogoFx.Ev.SENT && Style.sendFx.value != "none") {
                seed++
                p.snapTo(0f)
                kotlinx.coroutines.withContext(FullMotion) { p.animateTo(1f, tween(if (Style.sendFx.value == "blood") 1100 else 650, easing = LinearEasing)) }
            }
        }
    }
    val v = p.value
    if (v >= 1f) return
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val gun = Offset(w - 38.dp.toPx(), h - 46.dp.toPx())
        when (Style.sendFx.value) {
            "muzzle" -> {
                val f = (1f - v / 0.25f).coerceIn(0f, 1f)
                if (f > 0f) {
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = f), C.Flame.copy(alpha = f), Color.Transparent), gun, 70.dp.toPx() * (0.6f + f * 0.4f)), 70.dp.toPx(), gun)
                    for (k in 0 until 5) rotate(-90f + (k - 2) * 22f + seed * 7f % 10f, gun) {
                        drawPath(Path().apply { moveTo(gun.x - 6f, gun.y); lineTo(gun.x, gun.y - 60.dp.toPx() * f); lineTo(gun.x + 6f, gun.y); close() }, C.Flame.copy(alpha = f))
                    }
                }
                for (k in 0 until 2) {
                    val tt = (v - k * 0.08f).coerceIn(0f, 1f)
                    val pos = gun + Offset(-tt * (140 + k * 40).dp.toPx(), -tt * 120.dp.toPx() + tt * tt * 260.dp.toPx())
                    rotate(tt * 900f + k * 40f, pos) {
                        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Color(0xFFB8862A)), pos.x - 9f, pos.x + 9f), pos - Offset(9f, 3.5f), Size(18f, 7f), CornerRadius(3f), alpha = 1f - tt * 0.5f)
                    }
                }
                drawCircle(Color(0x55AAAAAA).copy(alpha = 0.33f * (1f - v)), 30.dp.toPx() * (0.5f + v), gun - Offset(0f, v * 60.dp.toPx()))
            }
            "tracer" -> {
                val head = gun + (Offset(w * 0.1f, h * 0.08f) - gun) * (v / 0.45f).coerceAtMost(1f)
                val tail = gun + (Offset(w * 0.1f, h * 0.08f) - gun) * ((v - 0.15f) / 0.45f).coerceIn(0f, 1f)
                drawLine(C.Flame.copy(alpha = 0.3f * (1f - v)), tail, head, 12f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 1f - v), tail, head, 3f, StrokeCap.Round)
            }
            "frag" -> {
                val r = max(w, h) * 1.1f * v
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - v)), r, gun, style = Stroke(6f + 20f * (1f - v)))
                drawCircle(Brush.radialGradient(listOf(C.Flame.copy(alpha = 0.6f * (1f - v)), Color.Transparent), gun, r * 0.6f + 1f), r * 0.6f + 1f, gun)
                val rnd = Random(seed)
                repeat(24) {
                    val a = -PI.toFloat() * rnd.nextFloat(); val d = r * (0.3f + rnd.nextFloat() * 0.5f)
                    drawCircle(C.Flame.copy(alpha = 1f - v), 3f, gun + Offset(cos(a) * d, sin(a) * d + v * v * 200f))
                }
            }
            "blood" -> {
                val a = (if (v < 0.1f) v / 0.1f else 1f - (v - 0.1f) / 0.9f).coerceIn(0f, 1f)
                bloodSplat(Offset(w * 0.06f, h * 0.2f), w * 0.16f, seed, alpha = a, drip = v * 1.5f)
                bloodSplat(Offset(w * 0.95f, h * 0.55f), w * 0.12f, seed + 3, alpha = a * 0.9f, drip = v)
                drawRect(Brush.radialGradient(listOf(Color.Transparent, BloodDark.copy(alpha = 0.45f * a)), Offset(w / 2, h / 2), max(w, h) * 0.75f))
            }
        }
    }
}

// ================================================================ the Loadout screen

@Composable
fun LoadoutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val me by Repo.me.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Calling card", "Emblem", "Theme", "Background", "Bubbles", "Send FX")
    val card by Style.card; val emblem by Style.emblem; val ecol by Style.emblemColor
    Box(Modifier.fillMaxSize().background(C.Char)) {
        ChatBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp).height(58.dp)
                    .glass(RoundedCornerShape(22.dp), fill = Color(0xCC140A06)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Bone) }
                Text("LOADOUT", style = fireText(24.sp, glow = 0.6f))
            }
            CallingCard(card, me?.name ?: "Operator", "Equipped", emblem, ecol, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, label ->
                    val on = tab == i
                    val shape = RoundedCornerShape(14.dp)
                    Text(
                        label, color = if (on) Color.White else C.Smoke, fontFamily = Display, fontSize = 13.sp,
                        modifier = Modifier.then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x66140A06), rim = 0.5f))
                            .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> LazyVerticalGrid(GridCells.Fixed(1), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CARDS, key = { it.id }) { c ->
                        Picker(c.id == card, c.name, c.blurb, onClick = { Style.set(Style.card, "card", c.id, synced = true) }) {
                            CallingCard(c.id, me?.name ?: "Operator", c.name, emblem, ecol, Modifier.fillMaxWidth())
                        }
                    }
                }
                1 -> Column(Modifier.weight(1f)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EMBLEM_COLORS.forEach { cval ->
                            val c = if (cval == 0) C.Ember else Color(cval)
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(c)
                                    .border(if (cval == ecol) 3.dp else 1.dp, if (cval == ecol) Color.White else Color(0x55FFFFFF), CircleShape)
                                    .clickable { Style.setEmblemColor(cval) },
                                contentAlignment = Alignment.Center,
                            ) { if (cval == 0) Text("A", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(EMBLEMS, key = { it.id }) { e ->
                            Picker(e.id == emblem, e.name, null, onClick = { Style.set(Style.emblem, "emblem", e.id, synced = true) }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { Emblem(e.id, ecol, 64.dp) }
                            }
                        }
                    }
                }
                2 -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Style.themes, key = { it.id }) { th ->
                        Picker(th.id == Style.themeId.value, th.name, null, onClick = { Style.set(Style.themeId, "theme", th.id) }) {
                            Box(Modifier.fillMaxWidth().height(70.dp).background(Brush.verticalGradient(listOf(th.soot, th.char)))) {
                                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(width = 70.dp, height = 26.dp).clip(RoundedCornerShape(13.dp))
                                    .background(Brush.linearGradient(listOf(th.flame, th.ember, th.blood))))
                                Box(Modifier.align(Alignment.TopStart).padding(10.dp).size(width = 56.dp, height = 22.dp).clip(RoundedCornerShape(11.dp)).background(Color(0x22FFFFFF)))
                            }
                        }
                    }
                }
                3 -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BACKGROUNDS, key = { it.id }) { b ->
                        Picker(b.id == Style.background.value, b.name, b.blurb, onClick = { Style.set(Style.background, "bg", b.id) }) {
                            BackgroundPreview(b.id, Modifier.fillMaxWidth().height(120.dp))
                        }
                    }
                }
                4 -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(BUBBLES, key = { it.id }) { b ->
                        Picker(b.id == Style.bubble.value, b.name, b.blurb, onClick = { Style.set(Style.bubble, "bubble", b.id) }) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Drop in 5", color = C.Bone, fontSize = 13.sp, modifier = Modifier.glass(previewShape(b.id, false)).padding(horizontal = 12.dp, vertical = 7.dp))
                                Text("Locked in", color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.End).molten(previewShape(b.id, true)).padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                    }
                }
                else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SEND_FX, key = { it.id }) { f ->
                        Picker(f.id == Style.sendFx.value, f.name, f.blurb, onClick = {
                            Style.set(Style.sendFx, "fx", f.id)
                            LogoFx.emit(LogoFx.Ev.SENT) // preview it
                        }) {
                            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                Text(if (f.id == "none") "—" else "Tap to preview", color = C.Smoke, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        SendFxOverlay(Modifier.fillMaxSize())
    }
}

private fun previewShape(id: String, mine: Boolean): Shape = when (id) {
    "plate" -> if (mine) CutCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 3.dp, bottomStart = 12.dp) else CutCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 3.dp)
    "tactical" -> RoundedCornerShape(4.dp)
    "tag" -> RoundedCornerShape(22.dp)
    else -> RoundedCornerShape(18.dp)
}

@Composable
private fun BackgroundPreview(id: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp))) { ChatBackgroundImpl(id, Modifier.fillMaxSize()) }
}

@Composable
private fun Picker(selected: Boolean, title: String, sub: String?, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (selected) Modifier.border(2.dp, C.Flame, shape) else Modifier.border(1.dp, Color(0x22FFFFFF), shape))
            .background(Color(0x99140A06)).clickable(onClick = onClick),
    ) {
        content()
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.Bone, fontFamily = Display, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrBlank()) Text(sub, color = C.Smoke, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Text("EQUIPPED", color = C.Flame, fontFamily = Display, fontSize = 10.sp)
        }
    }
}
