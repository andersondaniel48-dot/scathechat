package dev.daniel.scathechat.data

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Game modes the squad tracks. [codes] are the Activision API names to try, in order. */
enum class CodMode(val key: String, val label: String, val codes: List<String>) {
    WZ("wz", "Warzone", listOf("wz", "wz2", "br")),
    MP("mp", "Multiplayer", listOf("mp")),
    DMZ("dmz", "DMZ", listOf("dmz"));

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}

val PLATFORMS = listOf(
    "battle" to "Battle.net",
    "psn" to "PlayStation",
    "xbl" to "Xbox",
    "steam" to "Steam",
    "uno" to "Activision ID",
)

fun platformName(p: String?) = PLATFORMS.firstOrNull { it.first == p }?.second ?: (p ?: "")

/** One player's result in one match, posted (encrypted) to the group as a "cod" message. */
data class CodMatch(
    val matchId: String,
    val member: String,
    val mode: String,
    val title: String,
    val map: String?,
    val gameType: String?,
    val start: Long,
    val dur: Long,
    val result: String?,
    val place: Int?,
    val teams: Int?,
    val stats: Map<String, Double>,
) {
    fun n(vararg keys: String): Double? = keys.firstNotNullOfOrNull { stats[it] }
    val kills get() = n("kills")?.toInt() ?: 0
    val deaths get() = n("deaths")?.toInt() ?: 0
    val assists get() = n("assists")?.toInt()
    val damage get() = n("damageDone", "damage")?.toInt()
    val kd: Double get() = n("kdRatio") ?: if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths

    fun toJson(): JSONObject = JSONObject()
        .put("id", matchId).put("mb", member).put("md", mode).put("tl", title).put("st", start).put("du", dur)
        .put("s", JSONObject().apply { stats.forEach { (k, v) -> put(k, v) } })
        .apply {
            map?.let { put("mp", it) }; gameType?.let { put("gt", it) }; result?.let { put("r", it) }
            place?.let { put("pl", it) }; teams?.let { put("tm", it) }
        }

    companion object {
        fun from(o: JSONObject): CodMatch? {
            val id = o.str("id") ?: return null
            return CodMatch(
                id, o.optString("mb"), o.optString("md", "mp"), o.optString("tl"), o.str("mp"), o.str("gt"),
                o.optLong("st"), o.optLong("du"), o.str("r"),
                if (o.has("pl")) o.optInt("pl") else null, if (o.has("tm")) o.optInt("tm") else null,
                numbers(o.optJSONObject("s")),
            )
        }

        /** Same match for the same brother always gets the same message id, so re-posts don't duplicate. */
        fun msgId(member: String, matchId: String): String {
            val d = MessageDigest.getInstance("SHA-256").digest("cod|$member|$matchId".toByteArray())
            return "c" + Crypto.hex(d).take(23)
        }
    }
}

/** One brother's career (lifetime) and weekly numbers for one mode. */
data class CodSnap(
    val member: String,
    val mode: String,
    val title: String,
    val tag: String,
    val at: Long,
    val lifetime: Map<String, Double>,
    val weekly: Map<String, Double>,
) {
    fun toJson(): JSONObject = JSONObject().put("mb", member).put("md", mode).put("tl", title).put("tg", tag).put("at", at)
        .put("lt", JSONObject().apply { lifetime.forEach { (k, v) -> put(k, v) } })
        .put("wk", JSONObject().apply { weekly.forEach { (k, v) -> put(k, v) } })

    companion object {
        fun from(o: JSONObject): CodSnap? {
            val mb = o.str("mb") ?: return null
            return CodSnap(mb, o.optString("md", "mp"), o.optString("tl"), o.optString("tg"), o.optLong("at"),
                numbers(o.optJSONObject("lt")), numbers(o.optJSONObject("wk")))
        }
    }
}

fun numbers(o: JSONObject?): Map<String, Double> {
    o ?: return emptyMap()
    val out = LinkedHashMap<String, Double>()
    o.keys().forEach { k ->
        val v = o.opt(k)
        if (v is Number) out[k] = v.toDouble()
    }
    return out
}

/** Turns Activision's internal names into words: "mp_crash2" -> "Crash", "br_brquads" -> "BR Quads". */
fun prettyCode(raw: String?): String? {
    raw ?: return null
    var s = raw.lowercase(Locale.US)
    for (p in listOf("mp_", "wz_", "br_", "dmz_", "gw_", "jup_", "iw9_")) if (s.startsWith(p)) s = s.removePrefix(p)
    s = s.replace(Regex("\\d+$"), "")
    s = s.replace("brquads", "br quads").replace("brtrios", "br trios").replace("brduos", "br duos").replace("brsolo", "br solo")
        .replace("rebirth", "rebirth ").replace("resurgence", "resurgence ")
    return s.split('_', ' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
        if (w.length <= 3 && w in setOf("br", "tdm", "ffa", "dmz", "kc", "dom", "hp", "snd", "cdl")) w.uppercase(Locale.US)
        else w.replaceFirstChar { it.titlecase(Locale.US) }
    }.trim().ifBlank { raw }
}

/** "scorePerMinute" -> "Score per minute". */
fun prettyKey(k: String): String {
    val words = k.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').lowercase(Locale.US)
    return words.replaceFirstChar { it.titlecase(Locale.US) }
        .replace("Kd ratio", "K/D").replace("Wl ratio", "W/L").replace("Ekiad ratio", "EKIA/D")
}

// ---------------------------------------------------------------- parsing Activision's responses

object CodParse {
    private fun props(o: JSONObject?, vararg path: String): JSONObject? {
        var cur = o
        for (p in path) cur = cur?.optJSONObject(p)
        return cur
    }

    /** Lifetime + weekly numbers out of a profile response. Warzone keeps its numbers under mode.br. */
    fun snapshot(data: JSONObject, member: String, mode: CodMode, title: String, tag: String, at: Long): CodSnap {
        val lifeAll = numbers(props(data, "lifetime", "all", "properties"))
        val lifeBr = numbers(props(data, "lifetime", "mode", "br", "properties"))
        val weekAll = numbers(props(data, "weekly", "all", "properties"))
        val weekBr = numbers(props(data, "weekly", "mode", "br", "properties"))
        val lifetime = if (mode == CodMode.WZ && lifeBr.isNotEmpty()) lifeBr else lifeAll
        val weekly = if (mode == CodMode.WZ && weekBr.isNotEmpty()) weekBr else weekAll
        val level = data.optDouble("level", Double.NaN)
        val withLevel = if (!level.isNaN()) lifetime + ("level" to level) else lifetime
        return CodSnap(member, mode.key, title, tag, at, withLevel, weekly)
    }

    fun matches(data: JSONObject, member: String, mode: CodMode, title: String): List<CodMatch> {
        val arr = data.optJSONArray("matches") ?: return emptyList()
        val out = ArrayList<CodMatch>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.opt("matchID")?.toString() ?: m.opt("matchId")?.toString() ?: continue
            val ps = m.optJSONObject("playerStats")
            val stats = numbers(ps)
            val start = m.optLong("utcStartSeconds") * 1000
            val end = m.optLong("utcEndSeconds") * 1000
            val dur = m.optLong("duration").takeIf { it > 0 } ?: (end - start).coerceAtLeast(0)
            val place = stats["teamPlacement"]?.toInt()?.takeIf { it > 0 }
            val result = m.opt("result")?.toString()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() && it != "null" }
                ?: if (place == 1) "win" else null
            out.add(
                CodMatch(
                    id, member, mode.key, title, m.optString("map").ifBlank { null }, m.optString("mode").ifBlank { null },
                    start, dur, result, place, m.optInt("teamCount").takeIf { it > 0 }, stats,
                )
            )
        }
        return out
    }
}

/** A row in the stats feed: one match, with every brother who played in it. */
data class CodFeedItem(val matchId: String, val mode: String, val start: Long, val players: List<CodMatch>) {
    val lead get() = players.first()
}

data class CodState(
    val feed: List<CodFeedItem> = emptyList(),
    val snaps: Map<String, Map<String, CodSnap>> = emptyMap(), // member -> mode -> newest snapshot
    val lastSync: Long = 0L,
)
