package dev.daniel.scathechat.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * "Location drop": the sender's phone finds its position, draws a dark tactical map snapshot from
 * OpenStreetMap tiles, and sends picture + coordinates inside an encrypted message. The other
 * phones never contact a map service until someone taps "open in Maps".
 */
object LocationDrop {
    private const val ZOOM = 16
    private const val W = 640
    private const val H = 360

    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (providers.isEmpty()) return null
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val provider = providers.first()
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, Executors.newSingleThreadExecutor()) { loc -> if (cont.isActive) cont.resume(loc) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }
                        @Deprecated("old API") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                }
            }
        }
        if (fresh != null) return fresh
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    fun placeName(ctx: Context, lat: Double, lng: Double): String? = try {
        val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
        if (a == null) null else listOfNotNull(
            a.featureName?.takeIf { f -> f.any { it.isLetter() } && f != a.thoroughfare },
            a.thoroughfare, a.locality ?: a.subAdminArea,
        ).distinct().take(2).joinToString(", ").ifBlank { null }
    } catch (_: Exception) {
        null
    }

    private fun tilePx(lat: Double, lng: Double): Pair<Double, Double> {
        val n = (1 shl ZOOM).toDouble()
        val x = (lng + 180.0) / 360.0 * n * 256
        val r = lat * PI / 180
        val y = (1.0 - ln(tan(r) + 1 / cos(r)) / PI) / 2.0 * n * 256
        return x to y
    }

    /** Dark, ember-tinted map with a crosshair marker. Falls back to a bare grid if tiles can't load. */
    suspend fun snapshot(lat: Double, lng: Double): Bitmap = withContext(Dispatchers.IO) {
        val (px, py) = tilePx(lat, lng)
        val left = px - W / 2
        val top = py - H / 2
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF140B08.toInt())
        val tx0 = floor(left / 256).toInt()
        val ty0 = floor(top / 256).toInt()
        val tx1 = floor((left + W) / 256).toInt()
        val ty1 = floor((top + H) / 256).toInt()
        val dark = Paint().apply {
            val m = ColorMatrix().apply { setSaturation(0f) }
            m.postConcat(ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
            m.postConcat(ColorMatrix(floatArrayOf(0.95f, 0f, 0f, 0f, 6f, 0f, 0.55f, 0f, 0f, 2f, 0f, 0f, 0.42f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
            colorFilter = ColorMatrixColorFilter(m)
            isFilterBitmap = true
        }
        coroutineScope {
            val jobs = ArrayList<kotlinx.coroutines.Deferred<Unit>>()
            for (tx in tx0..tx1) for (ty in ty0..ty1) {
                jobs.add(async {
                    val tile = fetchTile(tx, ty) ?: return@async
                    synchronized(canvas) {
                        canvas.drawBitmap(tile, (tx * 256 - left).toFloat(), (ty * 256 - top).toFloat(), dark)
                    }
                    tile.recycle()
                })
            }
            jobs.awaitAll()
        }
        val grid = Paint().apply { color = 0x26FF5A1F; strokeWidth = 1f }
        var g = 0f
        while (g < W) { canvas.drawLine(g, 0f, g, H.toFloat(), grid); g += 40f }
        g = 0f
        while (g < H) { canvas.drawLine(0f, g, W.toFloat(), g, grid); g += 40f }
        val cx = W / 2f
        val cy = H / 2f
        val ember = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF5A1F.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FF5A1F }
        canvas.drawCircle(cx, cy, 46f, glow)
        canvas.drawCircle(cx, cy, 30f, ember)
        canvas.drawCircle(cx, cy, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB23F.toInt() })
        canvas.drawLine(cx - 60, cy, cx - 36, cy, ember); canvas.drawLine(cx + 36, cy, cx + 60, cy, ember)
        canvas.drawLine(cx, cy - 60, cx, cy - 36, ember); canvas.drawLine(cx, cy + 36, cx, cy + 60, ember)
        out
    }

    private fun fetchTile(x: Int, y: Int): Bitmap? = try {
        val n = 1 shl ZOOM
        val xx = ((x % n) + n) % n
        val req = Request.Builder().url("https://tile.openstreetmap.org/$ZOOM/$xx/$y.png")
            .header("User-Agent", "ScatheChat/1.0 (private group chat app)").build()
        Repo.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
    } catch (_: Exception) {
        null
    }

    /** Finds, renders and posts a location drop. Returns an error message, or null when sent. */
    suspend fun drop(ctx: Context, note: String?): String? {
        val loc = current(ctx) ?: return "Couldn't get your location. Is location turned on?"
        val place = withContext(Dispatchers.IO) { placeName(ctx, loc.latitude, loc.longitude) }
        val bmp = snapshot(loc.latitude, loc.longitude)
        val jpg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 72, it) }.toByteArray()
        bmp.recycle()
        val body = JSONObject()
            .put("lat", loc.latitude).put("lng", loc.longitude)
            .put("acc", if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0)
            .put("img", Crypto.b64(jpg))
            .apply {
                place?.let { put("place", it) }
                note?.takeIf { it.isNotBlank() }?.let { put("note", it.trim()) }
            }
        Repo.postData("loc", body)
        return null
    }
}
