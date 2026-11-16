package dev.daniel.scathechat.net

import dev.daniel.scathechat.BuildConfig
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Checks this app's GitHub Releases for a newer APK. */
object Updater {
    /** Download URL of a newer APK, or null. */
    val available = MutableStateFlow<String?>(null)
    private var checked = false

    suspend fun check(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (checked && !force) return@withContext available.value != null
        checked = true
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
                available.value = url ?: o.optString("html_url")
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
