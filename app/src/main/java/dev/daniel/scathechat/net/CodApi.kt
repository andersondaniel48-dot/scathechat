package dev.daniel.scathechat.net

import dev.daniel.scathechat.data.CodMatch
import dev.daniel.scathechat.data.CodMode
import dev.daniel.scathechat.data.CodParse
import dev.daniel.scathechat.data.CodSnap
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the unofficial API behind callofduty.com. There is no public Activision API; this uses
 * the signed-in browser session (cookies) from the in-app Activision login. It can break whenever
 * Activision changes things, so every call reports exactly what came back.
 */
object CodApi {
    private const val BASE = "https://my.callofduty.com/api/papi-client"
    const val DEFAULT_TITLE = "mw2"

    class Res(val ok: Boolean, val code: Int, val data: JSONObject?, val message: String) {
        val notPermitted get() = message.contains("not permitted", ignoreCase = true) || message.contains("private", ignoreCase = true)
        val notAuthenticated get() = code == 401 || message.contains("not authenticated", ignoreCase = true)
    }

    private val client by lazy { Repo.http.newBuilder().callTimeout(15, TimeUnit.SECONDS).followRedirects(false).build() }

    private fun url(vararg segments: String): HttpUrl {
        val b = BASE.toHttpUrl().newBuilder()
        for (s in segments) b.addPathSegment(s) // encodes "#" in tags
        return b.build()
    }

    fun get(url: HttpUrl): Res {
        val cookie = Repo.prefs.codCookie ?: return Res(false, 0, null, "Not signed in to Activision")
        val req = Request.Builder().url(url)
            .header("Cookie", cookie)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", Repo.prefs.codUserAgent ?: "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            .header("Referer", "https://www.callofduty.com/")
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.isRedirect) return Res(false, r.code, null, "Activision sign-in expired (redirected to login)")
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                    ?: return Res(false, r.code, null, "Unexpected response (HTTP ${r.code})")
                val status = json.optString("status")
                val data = json.optJSONObject("data")
                if (r.isSuccessful && status == "success" && data != null) {
                    // Activision sometimes reports errors inside a "success" envelope.
                    val inner = data.optString("message")
                    if (data.optString("type").contains("exception", true) && inner.isNotBlank()) Res(false, r.code, data, inner)
                    else Res(true, r.code, data, "OK")
                } else {
                    Res(false, r.code, data, data?.optString("message")?.ifBlank { null } ?: json.optString("message").ifBlank { "HTTP ${r.code}" })
                }
            }
        } catch (e: Exception) {
            Res(false, 0, null, "Network error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun identities() = get(url("crm", "cod", "v2", "identities"))
    fun profile(title: String, platform: String, tag: String, mode: String) =
        get(url("stats", "cod", "v1", "title", title, "platform", platform, "gamer", tag, "profile", "type", mode))

    fun matches(title: String, platform: String, tag: String, mode: String) =
        get(url("crm", "cod", "v2", "title", title, "platform", platform, "gamer", tag, "matches", mode, "start", "0", "end", "0", "details"))

    /** Newest game, platform and tag on the signed-in account, if Activision tells us. */
    fun guessIdentity(): Triple<String, String, String>? {
        val r = identities()
        if (!r.ok) return null
        val arr = r.data?.optJSONArray("titleIdentities") ?: return null
        var best: JSONObject? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (best == null || o.optLong("activeDate") > best.optLong("activeDate")) best = o
        }
        val b = best ?: return null
        val title = b.optString("title").ifBlank { return null }
        return Triple(title, b.optString("platform"), b.optString("username"))
    }
}

/** Keeps the squad's stats fresh: polls every few minutes while the app is open, less often in the background. */
object CodSync {
    val report = MutableStateFlow<List<String>>(emptyList())
    val busy = MutableStateFlow(false)
    val signedIn = MutableStateFlow(false)
    private val lock = Mutex()
    private var loop: Job? = null
    private val lastProfile = HashMap<String, Long>()

    fun init() {
        signedIn.value = Repo.prefs.codCookie != null
    }

    fun start() {
        if (loop?.isActive == true) return
        loop = Repo.scope.launch {
            delay(5_000)
            while (isActive) {
                if (signedIn.value) runCatching { sync(manual = false) }
                delay(if (Repo.foreground) 4 * 60_000L else 20 * 60_000L)
            }
        }
    }

    fun syncNow() {
        Repo.scope.launch { sync(manual = true) }
    }

    fun signOut() {
        Repo.prefs.codCookie = null
        signedIn.value = false
        report.value = emptyList()
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
        } catch (_: Exception) {
        }
    }

    private fun codeFor(mode: CodMode): List<String> {
        val cached = Repo.prefs.modeCode(mode.key)
        return if (cached != null) listOf(cached) + (mode.codes - cached) else mode.codes
    }

    suspend fun sync(manual: Boolean) {
        if (!signedIn.value) return
        if (!lock.tryLock()) return
        busy.value = true
        val lines = ArrayList<String>()
        try {
            val myId = Repo.me.value?.id
            val now = System.currentTimeMillis()
            val tagged = Repo.members.value.values.filter { !it.tag.isNullOrBlank() && !it.platform.isNullOrBlank() }
                .sortedBy { it.id != myId }
            if (tagged.isEmpty()) lines.add("Nobody has set a gamertag yet. Add yours in Settings → Call of Duty.")
            for (m in tagged) {
                val freshest = Repo.cod.value.snaps[m.id]?.values?.maxOfOrNull { it.at } ?: 0L
                if (!manual && m.id != myId && now - freshest < 10 * 60_000L) {
                    lines.add("${m.name}: fresh (synced by another phone)")
                    continue
                }
                syncMember(m, manual, lines)
            }
            Repo.prefs.codLastSync = now
        } finally {
            report.value = lines
            busy.value = false
            lock.unlock()
        }
    }

    private suspend fun syncMember(m: Member, manual: Boolean, lines: MutableList<String>) {
        val tag = m.tag ?: return
        val platform = m.platform ?: return
        val title = m.title?.ifBlank { null } ?: Repo.prefs.codTitle ?: CodApi.DEFAULT_TITLE
        val known = Repo.chat.value.byIdAll
        for (mode in CodMode.entries) {
            var worked: String? = null
            var last: CodApi.Res? = null
            for (code in codeFor(mode)) {
                val r = CodApi.matches(title, platform, tag, code)
                delay(700)
                last = r
                if (r.ok) {
                    worked = code
                    Repo.prefs.setModeCode(mode.key, code)
                    val matches = CodParse.matches(r.data!!, m.id, mode, title)
                    var posted = 0
                    for (x in matches.sortedBy { it.start }) {
                        val id = CodMatch.msgId(m.id, x.matchId)
                        if (id in known) continue
                        Repo.postCod("cod", id, x.toJson())
                        posted++
                    }
                    lines.add("${m.name} · ${mode.label}: ${matches.size} recent matches" + if (posted > 0) ", $posted new" else "")
                    break
                }
                if (r.notPermitted || r.notAuthenticated) break
            }
            if (worked == null) {
                val r = last
                lines.add("${m.name} · ${mode.label}: " + when {
                    r == null -> "skipped"
                    r.notAuthenticated -> "Activision sign-in expired. Sign in again."
                    r.notPermitted -> "not permitted (profile is private, or this game/mode isn't available)"
                    else -> r.message
                })
                if (r?.notAuthenticated == true) {
                    CodSync.signedIn.value = false
                    return
                }
                continue
            }
            val key = m.id + "|" + mode.key
            if (manual || System.currentTimeMillis() - (lastProfile[key] ?: 0L) > 30 * 60_000L) {
                val p = CodApi.profile(title, platform, tag, worked)
                delay(700)
                if (p.ok) {
                    lastProfile[key] = System.currentTimeMillis()
                    val snap = CodParse.snapshot(p.data!!, m.id, mode, title, tag, System.currentTimeMillis())
                    val prev = Repo.cod.value.snaps[m.id]?.get(mode.key)
                    if (prev == null || prev.lifetime != snap.lifetime || prev.weekly != snap.weekly || manual) {
                        Repo.postCod("codstats", null, snap.toJson())
                    }
                } else {
                    lines.add("${m.name} · ${mode.label} career stats: ${p.message}")
                }
            }
        }
    }

    /** Runs one request per step and reports Activision's raw answer, for the connection test. */
    suspend fun diagnose(tag: String, platform: String, title: String): List<String> {
        val out = ArrayList<String>()
        val id = CodApi.identities()
        out.add("Account lookup: " + if (id.ok) "OK" else "${id.code} ${id.message}")
        for (mode in CodMode.entries) {
            for (code in mode.codes) {
                val r = CodApi.matches(title, platform, tag, code)
                out.add("${mode.label} matches ($title/$code): " + if (r.ok) "OK, ${r.data?.optJSONArray("matches")?.length() ?: 0} matches" else "${r.code} ${r.message}")
                delay(600)
                if (r.ok || r.notAuthenticated) break
            }
        }
        return out
    }
}
