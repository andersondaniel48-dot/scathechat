package dev.daniel.scathechat.net

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.IntentCompat
import dev.daniel.scathechat.BuildConfig
import dev.daniel.scathechat.call.CallManager
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.service.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keeps Scathe Chat up to date from this repo's GitHub Releases.
 * Downloads new versions in the background and installs them when the app isn't on screen and
 * no call is running. The first self-update needs one tap; on Android 12+ later ones are silent.
 */
object Updater {
    private const val TAG = "ScatheUpdate"

    /** Download page / APK URL of a newer version, or null. */
    val available = MutableStateFlow<String?>(null)
    /** null, "downloading", "ready", "installing", "failed" */
    val state = MutableStateFlow<String?>(null)

    private var latestCode = 0
    private val lock = Mutex()
    private var loopStarted = false

    private fun apkFile(ctx: Context) = File(ctx.cacheDir, "update-$latestCode.apk")

    fun startAuto(ctx: Context) {
        if (loopStarted) return
        loopStarted = true
        Repo.scope.launch {
            delay(15_000)
            while (true) {
                try {
                    if (check(force = true) && Repo.prefs.autoUpdate) {
                        download(ctx)
                        installWhenIdle(ctx)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "auto update", e)
                }
                delay(3 * 3600_000L)
            }
        }
    }

    /** Safe mode: fetch and install the newest build right away (no waiting for the app to go idle). */
    fun fixNow(ctx: Context) {
        Repo.scope.launch {
            try {
                if (check(force = true)) { download(ctx); install(ctx) }
            } catch (e: Exception) {
                Log.w(TAG, "fixNow", e)
            }
        }
    }

    /** When a ready update exists, install it the next time the app goes to the background. */
    fun onBackground(ctx: Context) {
        if (Repo.prefs.autoUpdate && state.value == "ready") Repo.scope.launch { installWhenIdle(ctx) }
    }

    suspend fun check(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && latestCode > 0) return@withContext available.value != null
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            Repo.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext false
                val o = JSONObject(r.body?.string() ?: return@withContext false)
                val latest = o.optString("tag_name").substringAfterLast('.').toIntOrNull() ?: return@withContext false
                if (latest <= BuildConfig.VERSION_CODE) {
                    available.value = null
                    return@withContext false
                }
                val assets = o.optJSONArray("assets")
                var url: String? = null
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
                }
                if (latest != latestCode) {
                    latestCode = latest
                    if (state.value != "installing") state.value = null
                }
                available.value = url ?: o.optString("html_url")
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun download(ctx: Context): File? = lock.withLock {
        withContext(Dispatchers.IO) {
            val url = available.value ?: return@withContext null
            if (!url.endsWith(".apk")) return@withContext null
            val out = apkFile(ctx)
            if (out.exists() && out.length() > 0 && state.value == "ready") return@withContext out
            ctx.cacheDir.listFiles()?.filter { it.name.startsWith("update-") && it != out }?.forEach { it.delete() }
            state.value = "downloading"
            try {
                val client = Repo.http.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                    val tmp = File(out.path + ".part")
                    r.body!!.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(out)
                }
                state.value = "ready"
                out
            } catch (e: Exception) {
                Log.w(TAG, "download", e)
                state.value = "failed"
                null
            }
        }
    }

    private suspend fun installWhenIdle(ctx: Context) {
        // Never interrupt a call or someone looking at the app: wait for both to be over.
        repeat(360) {
            if (state.value != "ready") return
            if (!Repo.foreground && CallManager.active.value == null) {
                install(ctx)
                return
            }
            delay(60_000)
        }
    }

    /** Hands the downloaded APK to Android's installer. */
    fun install(ctx: Context) {
        val file = apkFile(ctx)
        if (!file.exists()) {
            Repo.scope.launch { download(ctx)?.let { install(ctx) } }
            return
        }
        try {
            state.value = "installing"
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val status = PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags)
                s.commit(status.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "install", e)
            state.value = "failed"
        }
    }
}

/** Gets the installer's answer. If Android wants a confirmation tap, show it (or a notification). */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                Updater.state.value = "ready"
                if (Repo.foreground) {
                    try { context.startActivity(confirm) } catch (_: Exception) { Notifier.updateReady(context, confirm) }
                } else {
                    Notifier.updateReady(context, confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.state.value = null
            else -> {
                Log.w("ScatheUpdate", "install failed: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                Updater.state.value = "failed"
            }
        }
    }
}
