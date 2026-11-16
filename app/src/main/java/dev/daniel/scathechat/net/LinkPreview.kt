package dev.daniel.scathechat.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.LinkInfo
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.media.MediaUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Link previews are built by the SENDER and travel inside the encrypted message,
 * so the others' phones never contact the linked site (same approach as Signal).
 */
object LinkPreview {
    val URL_REGEX = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")
    private const val TRAIL = ".,;:!?)]}'\""
    private const val UA = "WhatsApp/2.24.22.79 A"

    fun trimUrl(s: String): String = s.trimEnd { it in TRAIL }

    fun firstUrl(text: String): String? {
        val raw = URL_REGEX.find(text)?.value ?: return null
        val t = trimUrl(raw)
        val full = if (t.startsWith("http", ignoreCase = true)) t else "https://$t"
        return full.takeIf { it.toHttpUrlOrNull() != null }
    }

    private val cache = ConcurrentHashMap<String, Deferred<LinkInfo?>>()

    private val client: OkHttpClient by lazy {
        Repo.http.newBuilder().callTimeout(9, TimeUnit.SECONDS).followRedirects(true).followSslRedirects(true).build()
    }

    suspend fun fetch(url: String): LinkInfo? = try {
        cache.computeIfAbsent(url) { Repo.scope.async { runCatching { load(url) }.getOrNull() } }.await()
    } catch (e: Exception) {
        null
    }

    private fun load(url: String): LinkInfo? {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,image/*;q=0.9,*/*;q=0.5")
            .header("Accept-Language", "en-US,en;q=0.8")
            .build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body ?: return null
            val ct = r.header("Content-Type")?.lowercase() ?: ""
            val finalUrl = r.request.url
            val site = finalUrl.host.removePrefix("www.")
            if (ct.startsWith("image/")) {
                val bytes = readLimited(body.byteStream(), 8 shl 20, truncate = false) ?: return LinkInfo(url, null, null, site, null)
                return LinkInfo(url, finalUrl.pathSegments.lastOrNull(), null, site, shrink(bytes))
            }
            if (!ct.contains("html")) return LinkInfo(url, finalUrl.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }, null, site, null)
            val bytes = readLimited(body.byteStream(), 800 shl 10, truncate = true) ?: return null
            val html = String(bytes, body.contentType()?.charset() ?: Charsets.UTF_8)
            val meta = parseMeta(html)
            val title = meta["og:title"] ?: meta["twitter:title"]
                ?: Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)
            val desc = meta["og:description"] ?: meta["twitter:description"] ?: meta["description"]
            val siteName = meta["og:site_name"] ?: site
            var img = meta["og:image:secure_url"] ?: meta["og:image"] ?: meta["og:image:url"]
                ?: meta["twitter:image"] ?: meta["twitter:image:src"]
            if (img == null) youtubeId(url)?.let { img = "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
            val imgUrl = img?.let { finalUrl.resolve(clean(it)) }
            val imgB64 = imgUrl?.let { runCatching { fetchImage(it) }.getOrNull() }
            if (title == null && desc == null && imgB64 == null) return null
            return LinkInfo(url, title?.let(::clean)?.take(200), desc?.let(::clean)?.take(280), clean(siteName).take(60), imgB64)
        }
    }

    private fun youtubeId(url: String): String? =
        Regex("""(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([\w-]{11})""").find(url)?.groupValues?.get(1)

    private fun clean(s: String): String =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun parseMeta(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        val attrRe = Regex("""(?is)([a-z_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
        for (tag in Regex("(?is)<meta\\s[^>]*>").findAll(html)) {
            val attrs = HashMap<String, String>()
            for (a in attrRe.findAll(tag.value)) attrs[a.groupValues[1].lowercase()] = a.groupValues[2].ifEmpty { a.groupValues[3] }
            val k = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase() ?: continue
            val v = attrs["content"] ?: continue
            if (v.isNotBlank() && k !in out) out[k] = v
        }
        return out
    }

    private fun fetchImage(url: HttpUrl): String? {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = readLimited(r.body?.byteStream() ?: return null, 8 shl 20, truncate = false) ?: return null
            return shrink(bytes)
        }
    }

    /** Downscale to a small JPEG (~30–90 KB) that rides inside the message. */
    private fun shrink(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 720) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var scaled = MediaUtil.scaleDown(MediaUtil.flatten(bmp), 640)
        var q = 72
        var out = encode(scaled, q)
        while (out.size > 95_000 && q > 35) {
            q -= 12
            if (q < 50) scaled = MediaUtil.scaleDown(scaled, 420)
            out = encode(scaled, q)
        }
        scaled.recycle()
        return Crypto.b64(out)
    }

    private fun encode(b: Bitmap, q: Int): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()

    private fun readLimited(input: InputStream, limit: Int, truncate: Boolean): ByteArray? {
        input.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                if (out.size() + n > limit) {
                    if (!truncate) return null
                    out.write(buf, 0, limit - out.size())
                    break
                }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
