package dev.daniel.scathechat.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotEmpty() } else null

fun normalizeDbUrl(input: String): String? {
    var t = input.trim().trimEnd('/')
    if (t.isEmpty()) return null
    if (!t.startsWith("http://") && !t.startsWith("https://")) t = "https://$t"
    val u = t.toHttpUrlOrNull() ?: return null
    if (u.scheme != "https" || !u.host.contains('.')) return null
    // Pasted the Firebase console page instead? Pull the database name out of it:
    // console.firebase.google.com/project/<p>/database/<instance>/data
    if (u.host == "console.firebase.google.com") {
        val segs = u.pathSegments
        val i = segs.indexOf("database")
        val inst = segs.getOrNull(i + 1)?.takeIf { i >= 0 && it.isNotBlank() && it != "data" } ?: return null
        return if (inst.contains('.')) "https://$inst" else "https://$inst.firebaseio.com"
    }
    return "https://${u.host}"
}

class Group(val dbUrl: String, val secret: ByteArray, val name: String) {
    val roomId: String = Crypto.hex(Crypto.hkdf(secret, SALT, "room-id"))
    val key: ByteArray = Crypto.hkdf(secret, SALT, "message-key")
    val aad: ByteArray = roomId.toByteArray()
    fun profileAad(memberId: String): ByteArray = "$roomId/p/$memberId".toByteArray()

    fun inviteCode(): String = PREFIX + Crypto.b64url(
        JSONObject().put("d", dbUrl).put("k", Crypto.b64url(secret)).put("g", name).toString().toByteArray()
    )

    fun inviteLink(): String = "scathe://join?c=" + inviteCode()

    companion object {
        private val SALT = "scathe-chat-v1".toByteArray()
        private const val PREFIX = "SCATHE1."

        fun parseInvite(text: String): Group? {
            val i = text.indexOf(PREFIX)
            if (i < 0) return null
            val code = text.substring(i + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
            return try {
                val o = JSONObject(String(Crypto.unb64url(code), Charsets.UTF_8))
                val url = normalizeDbUrl(o.getString("d")) ?: return null
                val sec = Crypto.unb64url(o.getString("k"))
                if (sec.size != 32) return null
                Group(url, sec, o.optString("g", "Brothers"))
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class Me(val id: String, val name: String, val color: Int)

data class Member(
    val id: String,
    val name: String,
    val color: Int,
    val read: String?,
    val active: Long,
    val tz: String? = null,
    val tag: String? = null, // Call of Duty gamertag
    val platform: String? = null,
    val title: String? = null,
    val typing: Long = 0L, // server time of the last keystroke
    val pub: String? = null, // public signing key
    val keyChanged: Boolean = false,
    val fcm: String? = null, // push token
)

enum class Conn { CONNECTING, ONLINE, OFFLINE, ERROR }

data class LinkInfo(val url: String, val title: String?, val desc: String?, val site: String?, val image: String?) {
    fun toJson(): JSONObject = JSONObject().put("u", url).apply {
        title?.let { put("t", it) }; desc?.let { put("d", it) }; site?.let { put("s", it) }; image?.let { put("i", it) }
    }

    companion object {
        fun from(o: JSONObject?): LinkInfo? {
            o ?: return null
            val u = o.str("u") ?: return null
            return LinkInfo(u, o.str("t"), o.str("d"), o.str("s"), o.str("i"))
        }
    }
}

/**
 * A calendar event. Every change is a new encrypted "event" message with the same [eid];
 * the newest one wins. One slot = scheduled; several slots = a time poll until [final] is set.
 */
data class EventInfo(
    val eid: String,
    val kind: String, // filth, cod, other
    val title: String,
    val note: String?,
    val slots: List<Long>, // start times, epoch millis (UTC)
    val dur: Int, // minutes
    val final: Long?, // locked-in start time
    val cancelled: Boolean,
    val tz: String?, // creator's zone, for reference
    val act: String, // new, lock, edit, cancel
) {
    val start: Long? get() = final ?: slots.singleOrNull()
    val isPoll: Boolean get() = final == null && slots.size > 1

    fun toJson(): JSONObject = JSONObject().put("e", eid).put("k", kind).put("t", title).put("d", dur)
        .put("s", org.json.JSONArray(slots)).put("x", cancelled).put("a", act).apply {
            note?.let { put("n", it) }; final?.let { put("f", it) }; tz?.let { put("z", it) }
        }

    companion object {
        fun from(o: JSONObject?): EventInfo? {
            o ?: return null
            val eid = o.str("e") ?: return null
            val arr = o.optJSONArray("s")
            val slots = ArrayList<Long>()
            if (arr != null) for (i in 0 until arr.length()) slots.add(arr.optLong(i))
            return EventInfo(
                eid, o.optString("k", "other"), o.optString("t", "Event"), o.str("n"), slots.distinct().sorted(),
                o.optInt("d", 120), if (o.has("f") && !o.isNull("f")) o.optLong("f") else null,
                o.optBoolean("x", false), o.str("z"), o.optString("a", "new"),
            )
        }
    }
}

data class MediaInfo(
    val blob: String,
    val key: String,
    val chunks: Int,
    val size: Long,
    val mime: String,
    val name: String?,
    val kind: String, // image, gif, video, file
    val w: Int,
    val h: Int,
    val dur: Long,
    val thumb: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("b", blob).put("k", key).put("n", chunks).put("z", size).put("mt", mime).put("kd", kind)
        .put("w", w).put("h", h).put("du", dur).apply {
            name?.let { put("nm", it) }; thumb?.let { put("th", it) }
        }

    companion object {
        fun from(o: JSONObject?): MediaInfo? {
            o ?: return null
            val blob = o.str("b") ?: return null
            if (!blob.matches(Regex("[0-9a-f]{32}"))) return null
            return MediaInfo(
                blob, o.optString("k"), o.optInt("n", 1), o.optLong("z"), o.optString("mt", "application/octet-stream"),
                o.str("nm"), o.optString("kd", "file"), o.optInt("w"), o.optInt("h"), o.optLong("du"), o.str("th"),
            )
        }
    }
}

data class Msg(
    val id: String,
    val key: String?, // server key; null until the server has it
    val from: String,
    val ts: Long,
    val type: String, // text, media, react
    val text: String? = null,
    val link: LinkInfo? = null,
    val media: MediaInfo? = null,
    val replyTo: String? = null,
    val target: String? = null,
    val emoji: String? = null,
    val event: EventInfo? = null,
    val votes: Map<Long, String>? = null, // for type "vote": slot start -> y / m / n
    val data: String? = null, // JSON body for calls, locations, killcams, group settings
    val cts: Long = 0L, // time the sender created it (signed)
    val sig: String? = null, // sender's signature over Identity.canon
    val status: Int = SENT,
) {
    fun toPayload(): JSONObject = JSONObject().apply {
        put("v", 1); put("id", id); put("f", from); put("y", type); put("ts", if (cts > 0) cts else ts)
        sig?.let { put("sg", it) }
        text?.let { put("x", it) }
        replyTo?.let { put("r", it) }
        target?.let { put("tg", it) }
        emoji?.let { put("e", it) }
        link?.let { put("l", it.toJson()) }
        media?.let { put("m", it.toJson()) }
        event?.let { put("ev", it.toJson()) }
        data?.let { put("cd", it) }
        votes?.let { v -> put("vt", JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), x) } }) }
    }

    fun toStore(): JSONObject = toPayload().apply {
        put("k", key ?: JSONObject.NULL); put("t", ts); put("st", status)
    }

    fun preview(): String = when {
        type == "media" && media?.kind == "image" -> "📷 Photo" + (text?.let { " · $it" } ?: "")
        type == "media" && media?.kind == "gif" -> "GIF"
        type == "media" && media?.kind == "video" -> "🎬 Video"
        type == "media" -> "📎 " + (media?.name ?: "File")
        type == "react" -> "Reacted ${emoji ?: ""}"
        type == "event" && event != null -> eventPreview(event)
        type == "vote" -> "Voted on a time"
        type == "call" -> callPreview(data)
        type == "loc" -> "📍 Location" + (data?.let { runCatching { JSONObject(it).optString("place") }.getOrNull() }?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        type == "killcam" -> "📹 Killcam" + (data?.let { runCatching { JSONObject(it).optString("c") }.getOrNull() }?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
        type == "cfg" -> "Changed group settings"
        else -> text ?: link?.url ?: ""
    }

    companion object {
        const val PENDING = 0
        const val SENT = 1
        const val FAILED = 2

        fun fromPayload(o: JSONObject, key: String?, ts: Long): Msg = Msg(
            id = o.getString("id"),
            key = key,
            from = o.getString("f"),
            ts = if (ts > 0) ts else o.optLong("ts"),
            type = o.optString("y", "text"),
            text = o.str("x"),
            link = LinkInfo.from(o.optJSONObject("l")),
            media = MediaInfo.from(o.optJSONObject("m")),
            replyTo = o.str("r"),
            target = o.str("tg"),
            emoji = if (o.has("e")) o.optString("e") else null,
            event = EventInfo.from(o.optJSONObject("ev")),
            data = o.str("cd"),
            cts = o.optLong("ts"),
            sig = o.str("sg"),
            votes = o.optJSONObject("vt")?.let { v ->
                val out = HashMap<Long, String>()
                v.keys().forEach { k -> k.toLongOrNull()?.let { out[it] = v.optString(k) } }
                out
            },
        )

        fun fromStore(o: JSONObject): Msg = fromPayload(o, o.str("k"), o.optLong("t")).copy(status = o.optInt("st", SENT))
    }
}

fun callPreview(data: String?): String {
    val o = data?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return "Call"
    val kind = if (o.optBoolean("v")) "video call" else "voice call"
    return if (o.optString("a") == "end") "Call ended" else "Started a $kind"
}

fun kindEmoji(kind: String): String = when (kind) {
    "filth" -> "💀"
    "cod" -> "🎮"
    else -> "📅"
}

fun eventPreview(e: EventInfo): String {
    val head = kindEmoji(e.kind) + " " + e.title
    return when (e.act) {
        "lock" -> "$head · time locked in"
        "cancel" -> "$head · cancelled"
        "edit" -> "$head · updated"
        else -> if (e.isPoll) "$head · vote on a time" else "$head · scheduled"
    }
}

/** One event as seen in the calendar: its newest version plus everyone's votes. */
data class CalEvent(
    val info: EventInfo,
    val creator: String,
    val created: Long,
    val votes: Map<String, Map<Long, String>>, // member -> slot -> y/m/n
)

data class ChatState(
    val events: List<CalEvent> = emptyList(),
    val messages: List<Msg> = emptyList(),
    val reactions: Map<String, Map<String, String>> = emptyMap(), // target id -> member id -> emoji
    val byId: Map<String, Msg> = emptyMap(),
    val byIdAll: Set<String> = emptySet(),
)
