package dev.daniel.scathechat.media

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.MediaInfo
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object MediaUtil {
    const val MAX_BYTES = 100L * 1024 * 1024
    private const val MAX_DIM = 2048

    class TooBig : IOException("File is larger than 100 MB")

    class Prepared(
        val file: File, val kind: String, val mime: String, val name: String?,
        val w: Int, val h: Int, val dur: Long, val thumb: String?,
    )

    fun mediaDir(ctx: Context): File = File(ctx.filesDir, "media").also { it.mkdirs() }

    fun ext(mime: String): String {
        val e = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        return if (e.isNullOrEmpty()) "" else ".$e"
    }

    fun fileFor(ctx: Context, blob: String, mime: String): File = File(mediaDir(ctx), blob + ext(mime))

    fun prepare(ctx: Context, uri: Uri): Prepared {
        val cr = ctx.contentResolver
        var name: String? = null
        var size = -1L
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
        }
        if (size > MAX_BYTES) throw TooBig()
        val mime = cr.getType(uri)
            ?: name?.substringAfterLast('.', "")?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase()) }
            ?: "application/octet-stream"
        val tmp = File(ctx.cacheDir, "prep-${System.nanoTime()}")
        return when {
            mime == "image/gif" -> {
                copy(cr, uri, tmp)
                val info = gifInfo(tmp)
                Prepared(tmp, "gif", mime, name, info.first, info.second, 0, info.third)
            }
            mime.startsWith("image/") -> try {
                val r = compressImage(cr, uri, tmp)
                Prepared(tmp, "image", "image/jpeg", name?.substringBeforeLast('.')?.plus(".jpg"), r.first, r.second, 0, r.third)
            } catch (e: Exception) {
                copy(cr, uri, tmp)
                Prepared(tmp, "file", mime, name, 0, 0, 0, null)
            }
            mime.startsWith("video/") -> {
                copy(cr, uri, tmp)
                val v = videoInfo(tmp)
                Prepared(tmp, "video", mime, name, v.w, v.h, v.dur, v.thumb)
            }
            else -> {
                copy(cr, uri, tmp)
                Prepared(tmp, "file", mime, name, 0, 0, 0, null)
            }
        }
    }

    private fun copy(cr: ContentResolver, uri: Uri, out: File) {
        val input = cr.openInputStream(uri) ?: throw IOException("Can't open file")
        input.use { inp ->
            FileOutputStream(out).use { o ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        o.close(); out.delete(); throw TooBig()
                    }
                    o.write(buf, 0, n)
                }
            }
        }
    }

    /** Re-encodes photos: max 2048px, JPEG q82, and strips EXIF (including GPS location). */
    private fun compressImage(cr: ContentResolver, uri: Uri, out: File): Triple<Int, Int, String?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) throw IOException("Unsupported image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val decoded = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IOException("Unsupported image")
        val rot = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }
        var bmp = scaleDown(flatten(decoded), MAX_DIM)
        if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        val res = Triple(bmp.width, bmp.height, thumb(bmp, 48, 55))
        bmp.recycle()
        return res
    }

    private fun gifInfo(f: File): Triple<Int, Int, String?> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
        val first = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
        val th = first?.let { b -> thumb(b, 48, 55).also { b.recycle() } }
        return Triple(bounds.outWidth.coerceAtLeast(0), bounds.outHeight.coerceAtLeast(0), th)
    }

    private class Vid(val w: Int, val h: Int, val dur: Long, val thumb: String?)

    private fun videoInfo(f: File): Vid {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            val th = frame?.let { b -> thumb(b, 360, 60).also { b.recycle() } }
            Vid(w, h, dur, th)
        } catch (_: Exception) {
            Vid(0, 0, 0, null)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    /** Puts transparent images on white so they don't turn black as JPEG. */
    fun flatten(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
        b.recycle()
        return out
    }

    fun scaleDown(b: Bitmap, maxDim: Int): Bitmap {
        val m = max(b.width, b.height)
        if (m <= maxDim) return b
        val s = maxDim.toFloat() / m
        val out = Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
        if (out !== b) b.recycle()
        return out
    }

    private fun thumb(b: Bitmap, maxDim: Int, q: Int): String {
        val m = max(b.width, b.height).coerceAtLeast(1)
        val s = (maxDim.toFloat() / m).coerceAtMost(1f)
        val t = Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
        val bos = ByteArrayOutputStream()
        t.compress(Bitmap.CompressFormat.JPEG, q, bos)
        if (t !== b) t.recycle()
        return Crypto.b64(bos.toByteArray())
    }

    private val thumbCache = LruCache<String, Bitmap>(120)

    fun decodeThumb(b64: String?): Bitmap? {
        b64 ?: return null
        thumbCache.get(b64)?.let { return it }
        return try {
            val bytes = Crypto.unb64(b64)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { thumbCache.put(b64, it) }
        } catch (_: Exception) {
            null
        }
    }

    fun humanSize(n: Long): String = when {
        n >= 1 shl 20 -> String.format("%.1f MB", n / 1048576.0)
        n >= 1 shl 10 -> String.format("%.0f KB", n / 1024.0)
        else -> "$n B"
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    // ---- Sharing / saving / opening ----

    private fun namedCopy(ctx: Context, file: File, media: MediaInfo): File {
        val dir = File(ctx.cacheDir, "share").also { it.mkdirs() }
        val name = (media.name ?: ("scathe-" + media.blob.take(8) + ext(media.mime))).replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val out = File(dir, name)
        file.copyTo(out, overwrite = true)
        return out
    }

    private fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    fun share(ctx: Context, file: File, media: MediaInfo) {
        try {
            val uri = uriFor(ctx, namedCopy(ctx, file, media))
            val send = Intent(Intent.ACTION_SEND).setType(media.mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun open(ctx: Context, file: File, media: MediaInfo) {
        try {
            val uri = uriFor(ctx, namedCopy(ctx, file, media))
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, media.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            share(ctx, file, media)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveToGallery(ctx: Context, file: File, media: MediaInfo): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            share(ctx, file, media)
            return false
        }
        return try {
            val isVideo = media.mime.startsWith("video/")
            val isImage = media.mime.startsWith("image/")
            val collection = when {
                isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val dir = when {
                isVideo -> Environment.DIRECTORY_MOVIES
                isImage -> Environment.DIRECTORY_PICTURES
                else -> Environment.DIRECTORY_DOWNLOADS
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, media.name ?: ("scathe-" + media.blob.take(8) + ext(media.mime)))
                put(MediaStore.MediaColumns.MIME_TYPE, media.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/Scathe Chat")
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            Toast.makeText(ctx, "Saved to $dir/Scathe Chat", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't save: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
