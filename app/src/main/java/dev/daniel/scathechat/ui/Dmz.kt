@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.daniel.scathechat.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.Dmz
import dev.daniel.scathechat.data.DmzPin
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val CELL = 100f

// ---------------------------------------------------------------- colours

private fun rarityColor(r: String?) = when (r) {
    "Uncommon" -> Color(0xFF8CFF6A)
    "Rare" -> Color(0xFF6EC8FF)
    "Epic" -> Color(0xFFB48CFF)
    "Legendary" -> Color(0xFFFFB23F)
    "Ultra" -> Color(0xFFFF5A5A)
    else -> Color(0xFFF2E6D8)
}

private fun kindColor(s: Dmz.Spot) = when (s.kind) {
    Dmz.Kind.KEY -> rarityColor(s.rarity)
    Dmz.Kind.POLICE -> Color(0xFF6EC8FF)
    Dmz.Kind.FIRE -> Color(0xFFFF6A3D)
    Dmz.Kind.BANK -> Color(0xFF3DE0C8)
    Dmz.Kind.MARKET -> Color(0xFFFF7AA8)
    Dmz.Kind.EXFIL -> Color(0xFF8CFF6A)
    Dmz.Kind.BOSS -> Color(0xFFFF3B3B)
    Dmz.Kind.ENTRY -> Color(0xFFFFB23F)
    Dmz.Kind.CLINIC -> Color(0xFF8CFF6A)
    Dmz.Kind.GAS -> Color(0xFFFFD27A)
    Dmz.Kind.CACHE -> Color(0xFFD9CBBE)
    Dmz.Kind.TOWER -> Color(0xFFB48CFF)
    Dmz.Kind.COMPOUND -> Color(0xFFFF7AA8)
    Dmz.Kind.STRONGHOLD -> Color(0xFFFF5A1F)
    else -> Color(0xFFF2E6D8)
}

private fun glyph(k: Dmz.Kind) = when (k) {
    Dmz.Kind.KEY -> "K"; Dmz.Kind.POLICE -> "P"; Dmz.Kind.FIRE -> "F"; Dmz.Kind.BANK -> "$"
    Dmz.Kind.MARKET -> "B"; Dmz.Kind.EXFIL -> "E"; Dmz.Kind.BOSS -> "!"; Dmz.Kind.STATION -> "S"
    Dmz.Kind.ENTRY -> "X"; Dmz.Kind.CLINIC -> "+"; Dmz.Kind.GAS -> "G"; Dmz.Kind.CACHE -> "A"
    Dmz.Kind.TOWER -> "T"; Dmz.Kind.COMPOUND -> "C"; Dmz.Kind.STRONGHOLD -> "■"; else -> "•"
}

private fun threatColor(t: Dmz.Threat) = when (t) {
    Dmz.Threat.LOW -> Color(0xFF8FBF6A)
    Dmz.Threat.MEDIUM -> Color(0xFFFFB23F)
    Dmz.Threat.HIGH -> Color(0xFFFF6A2A)
    Dmz.Threat.CRITICAL -> Color(0xFFE5383B)
    Dmz.Threat.EXTREME -> Color(0xFFC04CFF)
    Dmz.Threat.NONE -> Color(0xFFAE9D90)
}

private val PIN_KINDS = listOf("key" to "Key room", "loot" to "Loot", "danger" to "Danger", "exfil" to "Exfil", "note" to "Note")
private fun pinColor(kind: String) = when (kind) {
    "key" -> Color(0xFFFFB23F); "loot" -> Color(0xFF3DE0C8); "danger" -> Color(0xFFFF3B3B); "exfil" -> Color(0xFF8CFF6A)
    else -> Color(0xFFF2E6D8)
}

// ---------------------------------------------------------------- layout of markers

private enum class Layer(val label: String) {
    KEYS("Key rooms"), PLACES("Places"), SUPPLY("Caches & gas"), EXITS("Exfils & bosses"), PINS("Group pins")
}

private fun layerOf(k: Dmz.Kind) = when (k) {
    Dmz.Kind.KEY -> Layer.KEYS
    Dmz.Kind.EXFIL, Dmz.Kind.BOSS -> Layer.EXITS
    Dmz.Kind.CACHE, Dmz.Kind.GAS -> Layer.SUPPLY
    else -> Layer.PLACES
}

/** Official-map pixels to our world units. */
private fun officialAt(px: Float, py: Float) = Offset(dev.daniel.scathechat.data.DmzOfficial.gx(px) * CELL, dev.daniel.scathechat.data.DmzOfficial.gy(py) * CELL)

// ---------------------------------------------------------------- official map tiles

/** Activision's map tiles, fetched by this phone, kept on disk and in memory. */
private object Tiles {
    private val mem = android.util.LruCache<String, ImageBitmap>(140)
    private val pending = java.util.Collections.synchronizedSet(HashSet<String>())
    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun get(ctx: Context, z: Int, x: Int, y: Int): ImageBitmap? {
        val key = "$z/$x/$y"
        mem.get(key)?.let { return it }
        if (!pending.add(key)) return null
        Repo.scope.launch(Dispatchers.IO) {
            try {
                val f = File(ctx.filesDir, "dmz_tiles/hajin_v1/${z}_${x}_${y}.png")
                if (!f.exists()) {
                    f.parentFile?.mkdirs()
                    val url = dev.daniel.scathechat.data.DmzOfficial.TILES.replace("{z}", "$z").replace("{x}", "$x").replace("{y}", "$y")
                    val req = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
                        .header("Referer", "https://www.callofduty.com/").build()
                    Repo.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes()?.let { f.writeBytes(it) } }
                }
                if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.let { mem.put(key, it) }
            } catch (_: Exception) {
            } finally {
                pending.remove(key)
                version.intValue++
            }
        }
        return null
    }
}

private fun DrawScope.drawTiles(ctx: Context, cam: Cam) {
    val O = dev.daniel.scathechat.data.DmzOfficial
    val sppx = cam.scale * CELL / O.CELL // screen px per map px
    val need = 1f / sppx // map px per screen px
    val zDetail = (O.MAX_Z - kotlin.math.floor(kotlin.math.log2(max(need * 1.4f, 1f)).toDouble()).toInt()).coerceIn(2, O.MAX_Z)
    fun layer(z: Int) {
        val n = 1 shl z
        val ts = O.SIZE / n
        val w0 = cam.toWorld(Offset.Zero); val w1 = cam.toWorld(Offset(size.width, size.height))
        val mx0 = O.X0 + w0.x / CELL * O.CELL; val my0 = O.Y0 + w0.y / CELL * O.CELL
        val mx1 = O.X0 + w1.x / CELL * O.CELL; val my1 = O.Y0 + w1.y / CELL * O.CELL
        val x0 = (mx0 / ts).toInt().coerceIn(0, n - 1); val x1 = (mx1 / ts).toInt().coerceIn(0, n - 1)
        val y0 = (my0 / ts).toInt().coerceIn(0, n - 1); val y1 = (my1 / ts).toInt().coerceIn(0, n - 1)
        for (ty in y0..y1) for (tx in x0..x1) {
            val img = Tiles.get(ctx, z, tx, ty) ?: continue
            val a = cam.toScreen(officialAt(tx * ts, ty * ts))
            val b = cam.toScreen(officialAt((tx + 1) * ts, (ty + 1) * ts))
            drawImage(
                img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(a.x.toInt(), a.y.toInt()),
                dstSize = IntSize((b.x - a.x).toInt() + 1, (b.y - a.y).toInt() + 1),
            )
        }
    }
    layer(2) // quick, low-detail underlay
    if (zDetail > 2) layer(zDetail)
}

private data class Placed(val spot: Dmz.Spot, val at: Offset)

/** Spots sharing a grid square fan out in a ring so each can be tapped. Areas sit in the middle of their square. */
private fun place(map: Dmz.MapDef, all: List<Dmz.Spot>): List<Placed> {
    val out = ArrayList<Placed>()
    // exact positions: several rooms in one building fan out in a small ring around it
    all.filter { it.px != null && it.py != null }.groupBy { (it.px!! / 6).toInt() to (it.py!! / 6).toInt() }.values.forEach { list ->
        val at = officialAt(list[0].px!!, list[0].py!!)
        if (list.size == 1) out.add(Placed(list[0], at))
        else {
            val rad = (0.05f + 0.012f * list.size).coerceAtMost(0.13f) * CELL
            list.forEachIndexed { i, sp ->
                val a = (i * 2 * PI / list.size - PI / 2).toFloat()
                out.add(Placed(sp, at + Offset(cos(a) * rad, sin(a) * rad)))
            }
        }
    }
    val spots = all.filter { it.px == null || it.py == null }
    val zones = Dmz.zones.filter { it.map == map.id }
    // Group by square, and within a square by the location they belong to, so a prison's rooms sit by the prison.
    spots.filter { it.kind != Dmz.Kind.AREA }.groupBy { it.grid }.forEach { (grid, inCell) ->
        val (c, r) = Dmz.cell(map, grid) ?: return@forEach
        val cellCenter = Offset((c + 0.5f) * CELL, (r + 0.5f) * CELL)
        val byZone = inCell.groupBy { s -> zones.firstOrNull { z -> s.place.contains(z.name, ignoreCase = true) } }
        byZone.forEach { (zone, list) ->
            val anchor = if (zone == null || byZone.size > 1 && zone == null) cellCenter else Offset(
                (zone.cx * CELL).coerceIn((c + 0.22f) * CELL, (c + 0.78f) * CELL),
                (zone.cy * CELL).coerceIn((r + 0.22f) * CELL, (r + 0.78f) * CELL),
            )
            if (list.size == 1) out.add(Placed(list[0], anchor))
            else {
                val rad = (if (list.size > 6) 0.24f else if (list.size > 3) 0.18f else 0.13f) * CELL
                list.forEachIndexed { i, sp ->
                    val a = (i * 2 * PI / list.size - PI / 2).toFloat()
                    out.add(Placed(sp, anchor + Offset(cos(a) * rad, sin(a) * rad)))
                }
            }
        }
    }
    return out
}

private fun areaCenter(map: Dmz.MapDef, s: Dmz.Spot): Offset? =
    Dmz.cell(map, s.grid)?.let { (c, r) -> Offset((c + 0.5f) * CELL, (r + 0.5f) * CELL) }

// ---------------------------------------------------------------- camera

private class Cam {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var ready = false
    var job: Job? = null
    fun toScreen(w: Offset) = w * scale + offset
    fun toWorld(s: Offset) = (s - offset) / scale
}

// ---------------------------------------------------------------- background image (each phone's own tac-map screenshot)

private fun imgFile(ctx: Context, map: String) = File(ctx.filesDir, "dmz_$map.img")
private fun calOf(ctx: Context, map: String): FloatArray? =
    ctx.getSharedPreferences("dmz", 0).getString("cal_$map", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray()
private fun saveCal(ctx: Context, map: String, cal: FloatArray?) =
    ctx.getSharedPreferences("dmz", 0).edit().putString("cal_$map", cal?.joinToString(",")).apply()

private fun srcOf(ctx: Context, map: String): String? = ctx.getSharedPreferences("dmz", 0).getString("src_$map", null)
private fun saveSrc(ctx: Context, map: String, src: String?) = ctx.getSharedPreferences("dmz", 0).edit().putString("src_$map", src).apply()

/** Downloads a map image straight onto this phone. Returns null on success, else what went wrong. */
private fun download(ctx: Context, map: String, url: String): String? =
    downloadTo(url, imgFile(ctx, map)).also { if (it == null) saveSrc(ctx, map, url) }

private fun downloadTo(url: String, dest: File): String? = try {
    val req = okhttp3.Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
        .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
        .build()
    Repo.http.newCall(req).execute().use { r ->
        val body = r.body
        when {
            !r.isSuccessful -> "The site answered ${r.code}."
            body == null -> "Empty download."
            else -> {
                val bytes = body.bytes()
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                if (o.outWidth <= 0) "That link isn't an image."
                else { dest.writeBytes(bytes); null }
            }
        }
    }
} catch (e: Exception) {
    e.message ?: "Download failed."
}

private sealed interface Setup {
    data class Done(val url: String, val cal: FloatArray) : Setup
    data object Manual : Setup
    data class Failed(val why: String) : Setup
}

/**
 * Gets the map and lines it up with no hands: download the gridded map, find its grid, then (for Hajin)
 * download the sharper official map and carry the grid over to it.
 */
private fun autoSetup(ctx: Context, map: Dmz.MapDef): Setup {
    if (map.fullExtent) {
        val full = floatArrayOf(0f, 0f, 1f, 1f)
        val files = map.sources.mapIndexed { i, u -> u to File(ctx.cacheDir, "dmz_src${i}_${map.id}") }
        val got = files.filter { (u, f) -> downloadTo(u, f) == null }
        if (got.isEmpty()) return Setup.Failed("no map source answered")
        var pick = got.first()
        var cal = full
        if (got.size > 1) {
            // the sharpest copy, checked against the next one; if their framing differs, carry the grid over.
            // If the match can't be confirmed, use the copy whose framing is known.
            val a = MapAlign.decode(got[0].second, 2400)
            val b = MapAlign.decode(got[1].second, 2400)
            val c = if (a != null && b != null) MapAlign.register(a, b, full, map.cols, map.rows) else null
            if (c != null) cal = c else pick = got[1]
        }
        pick.second.copyTo(imgFile(ctx, map.id), overwrite = true)
        saveSrc(ctx, map.id, pick.first); saveCal(ctx, map.id, cal)
        return Setup.Done(pick.first, cal)
    }
    val tmpGrid = File(ctx.cacheDir, "dmz_grid_${map.id}")
    var gridUrl: String? = null
    var lastErr = "no map source"
    for (u in map.sources) {
        val e = downloadTo(u, tmpGrid)
        if (e == null) { gridUrl = u; break } else lastErr = e
    }
    gridUrl ?: return Setup.Failed(lastErr)
    val g = MapAlign.decode(tmpGrid, 2400) ?: return Setup.Failed("couldn't read the image")
    val cal = MapAlign.detectGrid(g, map.cols, map.rows)
    if (cal == null) {
        tmpGrid.copyTo(imgFile(ctx, map.id), overwrite = true); saveSrc(ctx, map.id, gridUrl); saveCal(ctx, map.id, null)
        return Setup.Manual
    }
    map.hires?.let { hu ->
        val tmpHi = File(ctx.cacheDir, "dmz_hi_${map.id}")
        if (downloadTo(hu, tmpHi) == null) {
            val h = MapAlign.decode(tmpHi, 2400)
            val hc = h?.let { MapAlign.register(it, g, cal, map.cols, map.rows) }
            if (hc != null) {
                tmpHi.copyTo(imgFile(ctx, map.id), overwrite = true); saveSrc(ctx, map.id, hu); saveCal(ctx, map.id, hc)
                return Setup.Done(hu, hc)
            }
        }
    }
    tmpGrid.copyTo(imgFile(ctx, map.id), overwrite = true); saveSrc(ctx, map.id, gridUrl); saveCal(ctx, map.id, cal)
    return Setup.Done(gridUrl, cal)
}

/** The group's shared backgrounds: one person lines up the grid, everyone gets it. */
private const val ALIGN_V = 3 // bump to make every phone redo the automatic line-up
private fun sharedMap(map: String): JSONObject? =
    Repo.groupConfig.value?.optJSONObject("maps")?.optJSONObject(map)?.takeIf { it.optInt("v") >= ALIGN_V }
private fun shareMap(map: String, url: String, cal: FloatArray) {
    val next = JSONObject(Repo.groupConfig.value?.toString() ?: "{}")
    val maps = next.optJSONObject("maps") ?: JSONObject()
    maps.put(map, JSONObject().put("url", url).put("cal", cal.joinToString(",")).put("v", ALIGN_V))
    next.put("maps", maps)
    Repo.postData("cfg", next)
}

private fun loadBitmap(f: File): ImageBitmap? {
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var sample = 1
    while (max(o.outWidth, o.outHeight) / sample > 4096) sample *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

// ---------------------------------------------------------------- selection

private sealed interface Sel {
    data class SpotSel(val spot: Dmz.Spot) : Sel
    data class ZoneSel(val zone: Dmz.Zone) : Sel
    data class PinSel(val pin: DmzPin) : Sel
}

/**
 * DMZ mode: Hajin and both X01 floors as pan-and-zoom tac maps, every known key room and location searchable,
 * plus pins the group drops for each other (synced, encrypted).
 */
@Composable
fun DmzScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val pins by Repo.pins.collectAsState()
    val members by Repo.members.collectAsState()
    val me by Repo.me.collectAsState()

    var mapId by rememberSaveable { mutableStateOf("hajin") }
    val map = Dmz.map(mapId)
    var query by rememberSaveable { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Sel?>(null) }
    var layers by remember { mutableStateOf(Layer.entries.toSet() - Layer.SUPPLY) }
    var newPinAt by remember { mutableStateOf<Offset?>(null) }
    var imageSheet by remember { mutableStateOf(false) }
    var aligning by remember { mutableStateOf(false) }
    var imgVersion by remember { mutableIntStateOf(0) }
    var pendingFocus by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var downloading by remember { mutableStateOf(false) }
    var dlError by remember { mutableStateOf<String?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf<String?>(null) }
    val groupCfg by Repo.groupConfig.collectAsState()

    val cam = remember { Cam() }
    val anim = remember { Animatable(0f) }
    val topPad = with(density) { 190.dp.toPx() }
    val bottomPad = with(density) { 40.dp.toPx() }

    val bitmap by produceState<ImageBitmap?>(null, mapId, imgVersion) {
        value = withContext(Dispatchers.IO) { loadBitmap(imgFile(ctx, mapId)) }
    }
    val cal = remember(mapId, imgVersion) { calOf(ctx, mapId) }

    /** After a new image: find its grid automatically; only ask for a hand line-up if that fails. */
    fun autoAlignOrAsk(url: String?) {
        val target = mapId
        val m = map
        scope.launch {
            working = "Lining up the grid…"
            val cal = withContext(Dispatchers.Default) {
                MapAlign.decode(imgFile(ctx, target), 2400)?.let { MapAlign.detectGrid(it, m.cols, m.rows) }
            }
            working = null
            saveCal(ctx, target, cal)
            imgVersion++
            if (cal != null) { if (url != null) shareMap(target, url, cal) }
            else { dlError = "Couldn't find the grid on that image automatically, so line it up by hand once."; aligning = true }
        }
    }

    fun fetch(url: String, then: (Boolean) -> Unit = {}) {
        val target = mapId
        downloading = true
        dlError = null
        scope.launch {
            val err = withContext(Dispatchers.IO) { download(ctx, target, url) }
            downloading = false
            if (err == null) imgVersion++ else dlError = err
            then(err == null)
        }
    }

    fun runSetup(m: Dmz.MapDef) {
        if (working != null) return
        working = "Getting the ${m.short} map and lining up the grid…"
        dlError = null
        scope.launch {
            val r = withContext(Dispatchers.Default) { autoSetup(ctx, m) }
            working = null
            when (r) {
                is Setup.Done -> { shareMap(m.id, r.url, r.cal); imgVersion++ }
                Setup.Manual -> { imgVersion++; if (mapId == m.id) { dlError = "Couldn't find the grid on that image automatically, so line it up by hand once."; aligning = true } }
                is Setup.Failed -> dlError = "Couldn't download the ${m.short} map (${r.why}). Screenshot your tac map in game, or paste a link to a map image."
            }
        }
    }
    // first time on a map with no background and nothing shared yet: just do it
    LaunchedEffect(mapId) {
        val prefs = ctx.getSharedPreferences("dmz", 0)
        if (map.sources.isNotEmpty() && sharedMap(mapId) == null && srcOf(ctx, mapId) != "local" && !prefs.getBoolean("auto${ALIGN_V}_$mapId", false)) {
            prefs.edit().putBoolean("auto${ALIGN_V}_$mapId", true).apply()
            runSetup(map)
        }
    }

    // someone lined up a map for the group: download it and use their alignment
    LaunchedEffect(groupCfg, mapId) {
        val shared = sharedMap(mapId) ?: return@LaunchedEffect
        val url = shared.optString("url").takeIf { it.startsWith("http") } ?: return@LaunchedEffect
        val sharedCal = shared.optString("cal").split(",").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.toFloatArray()
        val mine = srcOf(ctx, mapId)
        if (mine == "local") return@LaunchedEffect // this phone uses its own screenshot
        if (mine != url || !imgFile(ctx, mapId).exists()) {
            val err = withContext(Dispatchers.IO) { download(ctx, mapId, url) }
            if (err != null) return@LaunchedEffect
        }
        if (sharedCal != null && !sharedCal.contentEquals(calOf(ctx, mapId))) saveCal(ctx, mapId, sharedCal)
        imgVersion++
    }

    val mapSpots = remember(mapId) { Dmz.spots.filter { it.map == mapId } }
    val placed = remember(mapId) { place(map, mapSpots) }
    val areas = remember(mapId) { mapSpots.filter { it.kind == Dmz.Kind.AREA } }
    val zones = remember(mapId) { Dmz.zones.filter { it.map == mapId } }
    val mapPins = remember(pins, mapId) { pins.filter { it.map == mapId } }
    val results = remember(query) { Dmz.search(query) }

    val W = map.cols * CELL
    val H = map.rows * CELL
    fun minScale() = if (canvasSize.width == 0) 0.1f else min(canvasSize.width / (W * 1.1f), (canvasSize.height - topPad - bottomPad) / (H * 1.1f)) * 0.8f
    val maxScale = with(density) { 6.dp.toPx() } // a grid square up to 600dp across

    fun fit() {
        if (canvasSize.width == 0) return
        cam.job?.cancel()
        val s = min(canvasSize.width / (W * 1.06f), (canvasSize.height - topPad - bottomPad) / (H * 1.06f))
        cam.scale = s
        cam.offset = Offset((canvasSize.width - W * s) / 2f, topPad + (canvasSize.height - topPad - bottomPad - H * s) / 2f)
        cam.ready = true
    }

    fun flyTo(world: Offset, targetScale: Float) {
        if (canvasSize.width == 0) return
        cam.job?.cancel()
        val view = Offset(canvasSize.width / 2f, topPad + (canvasSize.height - topPad) / 2f - with(density) { 60.dp.toPx() })
        val s0 = cam.scale
        val c0 = cam.toWorld(view)
        val s1 = targetScale.coerceIn(minScale(), maxScale)
        cam.job = scope.launch {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) {
                val t = value
                // zoom out a little mid-flight when travelling far, like a camera pulling up
                val far = ((c0 - world).getDistance() / (W * 0.5f)).coerceIn(0f, 1f)
                val dip = 1f - 0.45f * far * sin(PI.toFloat() * t)
                val s = exp(ln(s0) + (ln(s1) - ln(s0)) * t) * dip
                val c = c0 + (world - c0) * t
                cam.scale = s
                cam.offset = view - c * s
            }
        }
    }

    fun focusOn(world: Offset) = flyTo(world, max(cam.scale, canvasSize.width * 0.6f / CELL))

    LaunchedEffect(mapId, canvasSize) {
        if (canvasSize.width == 0) return@LaunchedEffect
        fit()
        pendingFocus?.let { focusOn(it); pendingFocus = null }
    }

    fun select(hit: Dmz.Hit) {
        focus.clearFocus()
        searching = false
        val target: Offset?
        val m = Dmz.map(hit.map)
        if (hit.spot != null) {
            sel = Sel.SpotSel(hit.spot)
            target = if (hit.spot.kind == Dmz.Kind.AREA) areaCenter(m, hit.spot)
            else place(m, Dmz.spots.filter { it.map == hit.map }).firstOrNull { it.spot.id == hit.spot.id }?.at
        } else {
            val z = hit.zone!!
            sel = Sel.ZoneSel(z)
            target = Offset(z.cx * CELL, z.cy * CELL)
        }
        if (target == null) return
        if (hit.map != mapId) { pendingFocus = target; mapId = hit.map } else focusOn(target)
    }

    // ------------------------------------------------------------ taps on the map
    val latest by rememberUpdatedState(Triple(placed, mapPins, zones))
    val latestAreas by rememberUpdatedState(areas)
    val latestLayers by rememberUpdatedState(layers)
    val hitRadius = with(density) { 26.dp.toPx() }
    fun pick(p: Offset): Sel? {
        val (pl, pn, zs) = latest
        var best: Sel? = null
        var bestD = hitRadius
        if (Layer.PINS in latestLayers) for (pin in pn) {
            val d = (cam.toScreen(Offset(pin.x * CELL, pin.y * CELL)) - p).getDistance()
            if (d < bestD) { bestD = d; best = Sel.PinSel(pin) }
        }
        for (x in pl) {
            if (layerOf(x.spot.kind) !in latestLayers) continue
            val d = (cam.toScreen(x.at) - p).getDistance()
            if (d < bestD) { bestD = d; best = Sel.SpotSel(x.spot) }
        }
        if (best != null) return best
        val w = cam.toWorld(p)
        for (a in latestAreas) {
            val c = areaCenter(map, a) ?: continue
            if ((c - w).getDistance() < 0.42f * CELL) return Sel.SpotSel(a)
        }
        return zs.filter { (Offset(it.cx * CELL, it.cy * CELL) - w).getDistance() < it.r * CELL }
            .minByOrNull { it.r }?.let { Sel.ZoneSel(it) }
    }

    val pulse = rememberInfiniteTransition(label = "sel").animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "p")
    val measurer = rememberTextMeasurer(cacheSize = 256)

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0705))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(mapId) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        cam.job?.cancel()
                        val ns = (cam.scale * zoom).coerceIn(minScale(), maxScale)
                        val k = ns / cam.scale
                        cam.offset = (cam.offset - centroid) * k + centroid + pan
                        cam.scale = ns
                    }
                }
                .pointerInput(mapId) {
                    detectTapGestures(
                        onTap = { p -> focus.clearFocus(); searching = false; sel = pick(p) },
                        onDoubleTap = { p -> flyTo(cam.toWorld(p), cam.scale * 2.2f) },
                        onLongPress = { p ->
                            val w = cam.toWorld(p)
                            if (w.x in 0f..W && w.y in 0f..H) newPinAt = Offset(w.x / CELL, w.y / CELL)
                        },
                    )
                },
        ) {
            if (!cam.ready) return@Canvas
            Tiles.version.intValue // redraw as tiles arrive
            drawMap(
                map = map, cam = cam, bitmap = bitmap, cal = cal, zones = zones, areas = areas, placed = placed,
                pins = mapPins, layers = layers, sel = sel, pulse = pulse.value, measurer = measurer,
                memberColor = { id -> C.member(members[id]?.color ?: 0) }, ctx = ctx,
            )
        }

        // ------------------------------------------------------------ header, search, layers
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().height(58.dp).glass(RoundedCornerShape(22.dp), fill = Color(0xCC140A06)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to chat", tint = C.Bone) }
                Text("DMZ", style = fireText(22.sp, glow = 0.6f))
                Spacer(Modifier.width(10.dp))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dmz.maps.forEach { m ->
                        val on = m.id == mapId
                        val shape = RoundedCornerShape(14.dp)
                        Text(
                            m.short, color = if (on) Color.White else C.Smoke, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x30140A06), rim = 0.5f))
                                .clickable { if (!on) { sel = null; mapId = m.id } }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
                if (!map.tiled) IconButton(onClick = { imageSheet = true }) { Icon(Icons.Filled.Image, "Map background", tint = C.Bone) }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(18.dp), fill = Color(0xCC140A06)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = C.Smoke, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search key rooms, places, X01…", color = C.Faint, fontSize = 15.sp)
                    BasicTextField(
                        value = query, onValueChange = { query = it; searching = true },
                        singleLine = true,
                        textStyle = TextStyle(color = C.Bone, fontSize = 15.sp),
                        cursorBrush = SolidColor(C.Ember),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { select(it) } }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) searching = true },
                    )
                }
                if (query.isNotEmpty()) IconButton(onClick = { query = ""; searching = false; focus.clearFocus() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, "Clear search", tint = C.Smoke)
                }
            }
            if (searching && query.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).glass(RoundedCornerShape(18.dp), fill = Color(0xEE140A06))) {
                    if (results.isEmpty()) Text("Nothing matches \"$query\".", color = C.Smoke, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    else LazyColumn(Modifier.padding(vertical = 6.dp)) {
                        items(results) { hit -> ResultRow(hit) { select(hit) } }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Layer.entries.forEach { l ->
                        val on = l in layers
                        val shape = RoundedCornerShape(12.dp)
                        Text(
                            l.label, color = if (on) C.Bone else C.Faint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .glass(shape, fill = if (on) Color(0x66FF5A1F) else Color(0x99140A06), rim = 0.5f)
                                .clickable { layers = if (on) layers - l else layers + l }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------ bottom: details or hint
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(
                    onClick = { fit() },
                    modifier = Modifier.size(46.dp).glass(CircleShape, fill = Color(0xCC140A06)),
                ) { Icon(Icons.Filled.ZoomOutMap, "Show the whole map", tint = C.Bone) }
            }
            Spacer(Modifier.height(8.dp))
            when (val s = sel) {
                null -> when {
                    working != null -> Row(
                        Modifier.glass(RoundedCornerShape(14.dp), fill = Color(0xCC140A06)).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = C.Flame, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(working ?: "", color = C.Bone, fontSize = 13.sp)
                    }
                    dlError != null && !imageSheet -> Text(
                        dlError ?: "", color = Color(0xFFFFB4A0), fontSize = 13.sp,
                        modifier = Modifier.glass(RoundedCornerShape(14.dp), fill = Color(0xEE140A06)).clickable { dlError = null }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                    else -> Text(
                        "Pinch to zoom · double-tap to zoom in · long-press to drop a pin for the group",
                        color = C.Smoke, fontSize = 12.sp,
                        modifier = Modifier.glass(RoundedCornerShape(14.dp), fill = Color(0xCC140A06)).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                else -> DetailCard(
                    s, map, members.mapValues { it.value.name }, me?.id,
                    onClose = { sel = null },
                    onDeletePin = { pin ->
                        Repo.postData("pin", JSONObject().put("pid", pin.pid).put("del", true))
                        sel = null
                    },
                )
            }
        }
    }

    newPinAt?.let { at ->
        NewPinDialog(
            grid = Dmz.gridLabel(map, at.x.toInt().coerceIn(0, map.cols - 1), at.y.toInt().coerceIn(0, map.rows - 1)),
            onDismiss = { newPinAt = null },
            onSave = { label, kind ->
                Repo.postData(
                    "pin",
                    JSONObject().put("pid", Crypto.hex(Crypto.random(8))).put("map", mapId)
                        .put("x", at.x.toDouble()).put("y", at.y.toDouble()).put("label", label).put("kind", kind),
                )
                layers = layers + Layer.PINS
                newPinAt = null
            },
        )
    }

    if (imageSheet) MapImageSheet(
        map = map, has = bitmap != null, downloading = downloading || working != null, error = dlError,
        shared = sharedMap(mapId) != null,
        onDismiss = { imageSheet = false },
        onDownload = { imageSheet = false; runSetup(map) },
        onLink = { linkDialog = true },
        onPicked = { uri ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { ctx.contentResolver.openInputStream(uri)?.use { i -> imgFile(ctx, mapId).outputStream().use { i.copyTo(it) } } != null }.getOrDefault(false)
                }
                if (ok) { saveSrc(ctx, mapId, "local"); imgVersion++; imageSheet = false; autoAlignOrAsk(null) }
            }
        },
        onAlign = { imageSheet = false; aligning = true },
        onRemove = { imgFile(ctx, mapId).delete(); saveCal(ctx, mapId, null); saveSrc(ctx, mapId, "none"); imgVersion++; imageSheet = false },
    )

    if (linkDialog) LinkDialog(
        onDismiss = { linkDialog = false },
        onGo = { url ->
            linkDialog = false
            fetch(url) { ok -> if (ok) { imageSheet = false; autoAlignOrAsk(url) } }
        },
    )

    if (aligning) bitmap?.let { bmp ->
        AlignScreen(
            bmp, map, cal ?: floatArrayOf(0f, 0f, 1f, 1f),
            onCancel = { aligning = false },
            onSave = {
                saveCal(ctx, mapId, it); imgVersion++; aligning = false
                val src = srcOf(ctx, mapId)
                if (src != null && src.startsWith("http")) shareMap(mapId, src, it)
            },
        )
    }
}

// ---------------------------------------------------------------- drawing

private fun DrawScope.drawMap(
    map: Dmz.MapDef, cam: Cam, bitmap: ImageBitmap?, cal: FloatArray?, zones: List<Dmz.Zone>, areas: List<Dmz.Spot>,
    placed: List<Placed>, pins: List<DmzPin>, layers: Set<Layer>, sel: Sel?, pulse: Float,
    measurer: androidx.compose.ui.text.TextMeasurer, memberColor: (String) -> Color, ctx: Context,
) {
    val W = map.cols * CELL
    val H = map.rows * CELL
    val tl = cam.toScreen(Offset.Zero)
    val br = cam.toScreen(Offset(W, H))
    val cellPx = CELL * cam.scale
    val dp = density

    // base
    drawRect(Color(0xFF150C08), topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y))
    if (bitmap != null) {
        val c = cal ?: floatArrayOf(0f, 0f, 1f, 1f)
        val x0 = (0f - c[0]) / (c[2] - c[0]) * W
        val x1 = (1f - c[0]) / (c[2] - c[0]) * W
        val y0 = (0f - c[1]) / (c[3] - c[1]) * H
        val y1 = (1f - c[1]) / (c[3] - c[1]) * H
        val a = cam.toScreen(Offset(x0, y0))
        val b = cam.toScreen(Offset(x1, y1))
        drawImage(bitmap, dstOffset = IntOffset(a.x.toInt(), a.y.toInt()), dstSize = IntSize((b.x - a.x).toInt(), (b.y - a.y).toInt()), alpha = 0.92f)
    } else if (map.tiled) {
        drawTiles(ctx, cam)
    } else {
        val big = TextStyle(color = Color(0x1FF5EADF), fontSize = (cellPx / dp * 0.34f).coerceIn(10f, 60f).sp, fontFamily = Display)
        drawLabel(measurer, if (map.id == "x01_lower") "LOWER FLOOR" else "UPPER FLOOR", cam.toScreen(Offset(5f * CELL, 8.5f * CELL)), big)
    }

    // grid
    val gridColor = if (map.tiled) Color(0x40FFFFFF) else Color(0x33F5EADF)
    for (c in 0..map.cols) {
        val x = cam.toScreen(Offset(c * CELL, 0f)).x
        drawLine(gridColor, Offset(x, tl.y), Offset(x, br.y), strokeWidth = 1f)
    }
    for (r in 0..map.rows) {
        val y = cam.toScreen(Offset(0f, r * CELL)).y
        drawLine(gridColor, Offset(tl.x, y), Offset(br.x, y), strokeWidth = 1f)
    }
    drawRect(Color(0x66FF5A1F), topLeft = tl, size = Size(br.x - tl.x, br.y - tl.y), style = Stroke(1.5f))
    val axis = TextStyle(color = Color(0xCCFFB23F), fontSize = 12.sp, fontFamily = Display)
    val labelY = max(tl.y, 6 * dp) - 0f
    for (c in 0 until map.cols) {
        val x = cam.toScreen(Offset((c + 0.5f) * CELL, 0f)).x
        drawLabel(measurer, "${'A' + c}", Offset(x, (tl.y - 10 * dp).coerceAtLeast(labelY)), axis)
    }
    for (r in 0 until map.rows) {
        val y = cam.toScreen(Offset(0f, (r + 0.5f) * CELL)).y
        drawLabel(measurer, "${r + map.rowStart}", Offset((tl.x - 10 * dp).coerceAtLeast(10 * dp), y), axis)
    }
    val taken = ArrayList<androidx.compose.ui.geometry.Rect>()

    // zones (named locations), coloured by threat
    val zoneText = TextStyle(color = C.Bone, fontSize = 13.sp, fontFamily = Display)
    val selZone = (sel as? Sel.ZoneSel)?.zone
    val minorText = TextStyle(color = Color(0xFFE8DDD2), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    // official-style labels: big names always, smaller place names once zoomed in
    for (z in zones.filter { it.label }.sortedByDescending { it.major }) {
        if (!z.major && cellPx < 170 * dp) continue
        val c = cam.toScreen(Offset(z.cx * CELL, z.cy * CELL))
        if (z == selZone) drawCircle(C.Ember, 18 * dp, c, style = Stroke(3 * dp))
        val drawn = drawLabel(measurer, z.name, c, if (z.major) zoneText else minorText, halo = true, taken = taken, maxWidth = (160 * dp).toInt())
        if (drawn && z.major && z.threat != Dmz.Threat.NONE) {
            val lay = measurer.measure(z.name, zoneText, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (160 * dp).toInt()), maxLines = 2)
            drawCircle(threatColor(z.threat), 4.5f * dp, c - Offset(lay.size.width / 2f + 10 * dp, 0f))
        }
    }
    for (z in zones.filter { !it.label }) {
        val c = cam.toScreen(Offset(z.cx * CELL, z.cy * CELL))
        val r = z.r * cellPx
        val col = threatColor(z.threat)
        drawCircle(col.copy(alpha = if (z == selZone) 0.3f else 0.14f), r, c)
        drawCircle(col.copy(alpha = 0.75f), r, c, style = Stroke(if (z == selZone) 3f * dp else 1.4f * dp, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
        drawLabel(measurer, z.name, c - Offset(0f, if (r > 40 * dp) r * 0.15f else 0f), zoneText, halo = true, taken = taken, maxWidth = (170 * dp).toInt())
    }
    // X01 areas
    val selSpot = (sel as? Sel.SpotSel)?.spot
    for (a in areas) {
        val c = cam.toScreen(areaCenter(map, a) ?: continue)
        val r = 0.42f * cellPx
        drawRoundRect(Color(0x22F5EADF), topLeft = c - Offset(r, r * 0.55f), size = Size(2 * r, 1.1f * r), cornerRadius = CornerRadius(12 * dp))
        drawRoundRect(
            (if (a == selSpot) C.Ember else Color(0x88F5EADF)), topLeft = c - Offset(r, r * 0.55f), size = Size(2 * r, 1.1f * r),
            cornerRadius = CornerRadius(12 * dp), style = Stroke(if (a == selSpot) 3f * dp else 1.2f * dp),
        )
        drawLabel(measurer, a.name, c, zoneText, halo = true, taken = taken, maxWidth = (170 * dp).toInt())
    }

    // markers
    val mr = (cellPx * 0.075f).coerceIn(5f * dp, 12f * dp)
    val showNames = cellPx > 340 * dp
    val glyphStyle = TextStyle(color = Color(0xFF140A06), fontSize = (mr / dp * 1.05f).sp, fontWeight = FontWeight.Bold)
    val nameStyle = TextStyle(color = C.Bone, fontSize = 11.sp)
    var selAt: Offset? = null
    for (p in placed) {
        if (layerOf(p.spot.kind) !in layers) continue
        val c = cam.toScreen(p.at)
        if (c.x < -40 || c.y < -40 || c.x > size.width + 40 || c.y > size.height + 40) continue
        val col = kindColor(p.spot)
        if (p.spot.kind == Dmz.Kind.KEY) {
            // key rooms: diamond
            val path = Path().apply { moveTo(c.x, c.y - mr * 1.2f); lineTo(c.x + mr * 1.2f, c.y); lineTo(c.x, c.y + mr * 1.2f); lineTo(c.x - mr * 1.2f, c.y); close() }
            drawPath(path, Color(0xCC000000), style = Stroke(3 * dp))
            drawPath(path, col)
        } else {
            drawCircle(Color(0xCC000000), mr + 1.5f * dp, c)
            drawCircle(col, mr, c)
        }
        if (mr > 7 * dp) drawLabel(measurer, glyph(p.spot.kind), c, glyphStyle)
        if (showNames || p.spot == selSpot) drawLabel(
            measurer, p.spot.name, c + Offset(0f, mr + 9 * dp), nameStyle, halo = true,
            maxWidth = (cellPx * 0.36f).toInt().coerceAtMost((150 * dp).toInt()), taken = taken, lines = 1,
        )
        if (p.spot == selSpot) selAt = c
    }

    // group pins
    if (Layer.PINS in layers) for (pin in pins) {
        val c = cam.toScreen(Offset(pin.x * CELL, pin.y * CELL))
        val col = pinColor(pin.kind)
        val h = 26 * dp
        val path = Path().apply {
            moveTo(c.x, c.y)
            cubicTo(c.x - h * 0.15f, c.y - h * 0.35f, c.x - h * 0.42f, c.y - h * 0.55f, c.x - h * 0.42f, c.y - h * 0.8f)
            cubicTo(c.x - h * 0.42f, c.y - h * 1.12f, c.x + h * 0.42f, c.y - h * 1.12f, c.x + h * 0.42f, c.y - h * 0.8f)
            cubicTo(c.x + h * 0.42f, c.y - h * 0.55f, c.x + h * 0.15f, c.y - h * 0.35f, c.x, c.y)
            close()
        }
        drawPath(path, Color(0xCC000000), style = Stroke(3 * dp))
        drawPath(path, col)
        drawCircle(memberColor(pin.by), h * 0.17f, c - Offset(0f, h * 0.8f))
        drawLabel(measurer, pin.label, c + Offset(0f, 10 * dp), nameStyle, halo = true, maxWidth = (150 * dp).toInt(), taken = taken, lines = 1)
        if ((sel as? Sel.PinSel)?.pin?.pid == pin.pid) selAt = c - Offset(0f, h * 0.6f)
    }

    // selection pulse
    selAt?.let { c ->
        val rr = mr * (1.6f + 2.2f * pulse)
        drawCircle(C.Ember.copy(alpha = 1f - pulse), rr, c, style = Stroke(3 * dp))
        drawCircle(C.Flame, mr * 1.7f, c, style = Stroke(2 * dp))
    }
}

/** Draws a centred label. With [taken], skips it (returns false) if it would overlap a label already drawn. */
private fun DrawScope.drawLabel(
    m: androidx.compose.ui.text.TextMeasurer, text: String, center: Offset, style: TextStyle,
    halo: Boolean = false, maxWidth: Int = Int.MAX_VALUE, taken: MutableList<androidx.compose.ui.geometry.Rect>? = null, lines: Int = 2,
): Boolean {
    val lay = m.measure(
        text, style,
        constraints = if (maxWidth == Int.MAX_VALUE) androidx.compose.ui.unit.Constraints() else androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
        overflow = TextOverflow.Ellipsis, maxLines = lines,
    )
    val tl = center - Offset(lay.size.width / 2f, lay.size.height / 2f)
    if (tl.x > size.width || tl.y > size.height || tl.x + lay.size.width < 0 || tl.y + lay.size.height < 0) return false
    if (taken != null) {
        val pad = 4 * density
        val r = androidx.compose.ui.geometry.Rect(tl.x - pad, tl.y - pad, tl.x + lay.size.width + pad, tl.y + lay.size.height + pad)
        if (taken.any { it.overlaps(r) }) return false
        taken.add(r)
    }
    if (halo) drawRoundRect(
        Color(0xB30B0705), topLeft = tl - Offset(4 * density, 1 * density),
        size = Size(lay.size.width + 8 * density, lay.size.height + 2 * density), cornerRadius = CornerRadius(6 * density),
    )
    drawText(lay, topLeft = tl)
    return true
}

// ---------------------------------------------------------------- pieces

@Composable
private fun ResultRow(hit: Dmz.Hit, onClick: () -> Unit) {
    val (col, glyphText, sub) = when {
        hit.spot != null -> Triple(
            kindColor(hit.spot), glyph(hit.spot.kind),
            listOfNotNull(hit.spot.place, hit.spot.grid, Dmz.map(hit.spot.map).short, hit.spot.rarity).joinToString(" · "),
        )
        else -> Triple(threatColor(hit.zone!!.threat), "◎", listOfNotNull(Dmz.map(hit.zone.map).short, hit.zone.threat.label.ifEmpty { null }).joinToString(" · "))
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
            Text(glyphText, color = Color(0xFF140A06), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.name, color = C.Bone, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = C.Smoke, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailCard(
    sel: Sel, map: Dmz.MapDef, names: Map<String, String>, myId: String?,
    onClose: () -> Unit, onDeletePin: (DmzPin) -> Unit,
) {
    val title: String
    val chips: List<Pair<String, Color>>
    val body: String
    var share: String? = null
    var pin: DmzPin? = null
    when (sel) {
        is Sel.SpotSel -> {
            val s = sel.spot
            title = s.name
            chips = listOfNotNull(
                s.kind.label to kindColor(s),
                s.rarity?.let { it to rarityColor(it) },
                s.grid to C.Flame,
                Dmz.map(s.map).short to C.Smoke,
            )
            body = listOf(s.place, s.detail).filter { it.isNotBlank() }.joinToString("\n")
            share = "📍 ${s.name}: ${s.grid}, ${s.place} (${Dmz.map(s.map).short})"
        }
        is Sel.ZoneSel -> {
            val z = sel.zone
            title = z.name
            chips = listOfNotNull(z.threat.label.takeIf { it.isNotEmpty() }?.let { "$it threat" to threatColor(z.threat) }, Dmz.map(z.map).short to C.Smoke)
            body = z.note
            share = "📍 ${z.name} (${Dmz.map(z.map).short})"
        }
        is Sel.PinSel -> {
            val p = sel.pin
            pin = p
            title = p.label.ifBlank { "Pin" }
            val grid = Dmz.gridLabel(map, p.x.toInt().coerceIn(0, map.cols - 1), p.y.toInt().coerceIn(0, map.rows - 1))
            chips = listOf((PIN_KINDS.firstOrNull { it.first == p.kind }?.second ?: "Note") to pinColor(p.kind), grid to C.Flame)
            body = "Dropped by ${if (p.by == myId) "you" else names[p.by] ?: "someone"}"
            share = "📍 ${p.label} at $grid (${map.short})"
        }
    }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), fill = Color(0xEE140A06)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(title, color = C.Bone, fontFamily = Display, fontSize = 19.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, "Close", tint = C.Smoke) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { (t, c) ->
                Text(
                    t, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
        if (body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(body, color = C.Smoke, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            share?.let { text ->
                GlassButton(onClick = { Repo.sendText(text, null, null, allowPreview = false) }) {
                    Icon(Icons.Filled.Send, null, tint = C.Bone, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    ButtonLabel("Send to chat", 14)
                }
            }
            if (pin != null && pin.by == myId) GlassButton(onClick = { onDeletePin(pin) }) {
                Icon(Icons.Filled.Delete, null, tint = C.Bone, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                ButtonLabel("Remove pin", 14)
            }
        }
    }
}

@Composable
private fun NewPinDialog(grid: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("key") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF221610),
        title = { Text("Drop a pin at $grid", color = C.Bone, fontFamily = Display) },
        text = {
            Column {
                Text("Everyone in the group sees it on their map.", color = C.Smoke, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(60) }, singleLine = true,
                    placeholder = { Text("What's here?", color = C.Faint) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.Bone, unfocusedTextColor = C.Bone, focusedBorderColor = C.Ember, cursorColor = C.Ember),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PIN_KINDS.forEach { (id, name) ->
                        val on = kind == id
                        Text(
                            name, color = if (on) Color(0xFF140A06) else C.Bone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) pinColor(id) else Color(0x22FFFFFF))
                                .clickable { kind = id }.padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim().ifEmpty { PIN_KINDS.first { it.first == kind }.second }, kind) }) { Text("Drop pin", color = C.Flame) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Smoke) } },
    )
}

@Composable
private fun MapImageSheet(
    map: Dmz.MapDef, has: Boolean, downloading: Boolean, error: String?, shared: Boolean,
    onDismiss: () -> Unit, onDownload: () -> Unit, onLink: () -> Unit, onPicked: (Uri) -> Unit, onAlign: () -> Unit, onRemove: () -> Unit,
) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onPicked) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("${map.title} background", style = fireText(22.sp, glow = 0.5f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Put the real tac map under the markers. Download it, then drag two corners so its grid lines up with " +
                    "${'A'}${map.rowStart} to ${'A' + map.cols - 1}${map.rowStart + map.rows - 1}. " +
                    "Once one of you lines it up, everyone's phone downloads it with the same alignment.",
                color = C.Smoke, fontSize = 13.sp,
            )
            if (shared) Text("Already lined up for the group.", color = C.Online, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            if (map.sources.isNotEmpty()) {
                FireButton(onClick = { if (!downloading) onDownload() }, modifier = Modifier.fillMaxWidth()) {
                    ButtonLabel(if (downloading) "Downloading…" else if (has) "Download the map again" else "Download the ${map.short} map", 15)
                }
                Spacer(Modifier.height(8.dp))
            }
            error?.let { Text(it, color = Color(0xFFFFB4A0), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            GlassButton(onClick = onLink, modifier = Modifier.fillMaxWidth()) { ButtonLabel("Paste an image link", 15) }
            Spacer(Modifier.height(8.dp))
            GlassButton(onClick = { pick.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { ButtonLabel("Use a screenshot", 15) }
            if (has) {
                Spacer(Modifier.height(8.dp))
                GlassButton(onClick = onAlign, modifier = Modifier.fillMaxWidth()) { ButtonLabel("Line up the grid", 15) }
                Spacer(Modifier.height(8.dp))
                GlassButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { ButtonLabel("Remove image", 15) }
            }
        }
    }
}

/** Drag the two corner handles onto the image's grid: top-left of the first square, bottom-right of the last. */
@Composable
private fun AlignScreen(bmp: ImageBitmap, map: Dmz.MapDef, initial: FloatArray, onCancel: () -> Unit, onSave: (FloatArray) -> Unit) {
    BackHandler(onBack = onCancel)
    var cal by remember { mutableStateOf(initial.copyOf()) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) {
                var which = -1
                detectDragGestures(
                    onDragStart = { p ->
                        val (o, s) = imageRect(bmp, box)
                        val a = o + Offset(cal[0] * bmp.width * s, cal[1] * bmp.height * s)
                        val b = o + Offset(cal[2] * bmp.width * s, cal[3] * bmp.height * s)
                        which = if ((p - a).getDistance() < (p - b).getDistance()) 0 else 1
                    },
                    onDrag = { change, d ->
                        change.consume()
                        val (_, s) = imageRect(bmp, box)
                        val n = cal.copyOf()
                        val i = which * 2
                        n[i] = (n[i] + d.x / (bmp.width * s)).coerceIn(-0.2f, 1.2f)
                        n[i + 1] = (n[i + 1] + d.y / (bmp.height * s)).coerceIn(-0.2f, 1.2f)
                        cal = n
                    },
                )
            },
        ) {
            if (box.width == 0) return@Canvas
            val (o, s) = imageRect(bmp, box)
            drawImage(bmp, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize((bmp.width * s).toInt(), (bmp.height * s).toInt()))
            val a = o + Offset(cal[0] * bmp.width * s, cal[1] * bmp.height * s)
            val b = o + Offset(cal[2] * bmp.width * s, cal[3] * bmp.height * s)
            val gw = (b.x - a.x) / map.cols
            val gh = (b.y - a.y) / map.rows
            for (c in 0..map.cols) drawLine(Color(0xCCFF5A1F), Offset(a.x + c * gw, a.y), Offset(a.x + c * gw, b.y), strokeWidth = 2f)
            for (r in 0..map.rows) drawLine(Color(0xCCFF5A1F), Offset(a.x, a.y + r * gh), Offset(b.x, a.y + r * gh), strokeWidth = 2f)
            val st = TextStyle(color = C.Flame, fontSize = 12.sp, fontFamily = Display)
            for (c in 0 until map.cols) drawText(measurer, "${'A' + c}", Offset(a.x + c * gw + 4, a.y + 2), st)
            for (r in 0 until map.rows) drawText(measurer, "${r + map.rowStart}", Offset(a.x + 4, a.y + r * gh + 16 * density.density), st)
            for (p in listOf(a, b)) {
                drawCircle(Color(0x66FF5A1F), 26 * density.density, p)
                drawCircle(C.Flame, 26 * density.density, p, style = Stroke(3f))
                drawLine(C.Bone, p - Offset(14f * density.density, 0f), p + Offset(14f * density.density, 0f), strokeWidth = 2f)
                drawLine(C.Bone, p - Offset(0f, 14f * density.density), p + Offset(0f, 14f * density.density), strokeWidth = 2f)
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).glass(RoundedCornerShape(18.dp), fill = Color(0xDD140A06)).padding(14.dp)) {
            Text("Line up the grid", color = C.Bone, fontFamily = Display, fontSize = 18.sp)
            Text(
                "Drag the two circles onto the top-left corner of ${'A'}${map.rowStart} and the bottom-right corner of ${'A' + map.cols - 1}${map.rowStart + map.rows - 1} on the image.",
                color = C.Smoke, fontSize = 13.sp,
            )
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassButton(onClick = onCancel) { ButtonLabel("Cancel", 15) }
            FireButton(onClick = { onSave(cal) }) { ButtonLabel("Save", 15) }
        }
    }
}

private fun imageRect(bmp: ImageBitmap, box: IntSize): Pair<Offset, Float> {
    val s = min(box.width / bmp.width.toFloat(), box.height / bmp.height.toFloat())
    return Offset((box.width - bmp.width * s) / 2f, (box.height - bmp.height * s) / 2f) to s
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF221610),
        title = { Text("Image link", color = C.Bone, fontFamily = Display) },
        text = {
            Column {
                Text("Long-press a map image in your browser, copy its link, and paste it here.", color = C.Smoke, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it.trim() }, singleLine = true,
                    placeholder = { Text("https://…", color = C.Faint) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = C.Bone, unfocusedTextColor = C.Bone, focusedBorderColor = C.Ember, cursorColor = C.Ember),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (url.startsWith("http")) onGo(url) }) { Text("Download", color = C.Flame) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Smoke) } },
    )
}


