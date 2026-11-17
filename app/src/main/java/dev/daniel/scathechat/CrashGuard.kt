package dev.daniel.scathechat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.File

/**
 * Saves any crash to Downloads (scathe-crash-*.txt) so it can be sent to Claude, and counts crashes that happen
 * right after launch. Two in a row puts the app in safe mode: a bare screen that only fetches the latest fix.
 */
object CrashGuard {
    private const val P = "crashguard"
    var safeMode = false
        private set

    fun install(app: Context) {
        val sp = app.getSharedPreferences(P, 0)
        safeMode = sp.getInt("streak", 0) >= 2
        val launchedAt = System.currentTimeMillis()
        sp.edit().putLong("launch_at", launchedAt).commit()
        // survived 30 s: not a launch crash loop
        Handler(Looper.getMainLooper()).postDelayed({ sp.edit().putInt("streak", 0).apply() }, 30_000)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val quick = System.currentTimeMillis() - launchedAt < 30_000
                sp.edit().putInt("streak", if (quick) sp.getInt("streak", 0) + 1 else 1).commit()
                val report = buildString {
                    append("Scathe Chat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                    append("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                    append("Thread: ${t.name}\n\n")
                    append(e.stackTraceToString())
                }
                File(app.filesDir, "last_crash.txt").writeText(report)
                saveToDownloads(app, "scathe-crash-${System.currentTimeMillis() / 1000}.txt", report)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun reset(app: Context) {
        app.getSharedPreferences(P, 0).edit().putInt("streak", 0).commit()
        safeMode = false
    }

    fun lastReport(app: Context): String? = File(app.filesDir, "last_crash.txt").takeIf { it.exists() }?.readText()

    fun saveToDownloads(app: Context, name: String, text: String): Boolean = try {
        if (Build.VERSION.SDK_INT < 29) false else {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            uri != null && app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
        }
    } catch (_: Throwable) {
        false
    }
}
