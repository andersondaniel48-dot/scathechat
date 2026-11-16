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

data class Member(val id: String, val name: String, val color: Int, val read: String?, val active: Long)

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
    val status: Int = SENT,
) {
    fun toPayload(): JSONObject = JSONObject().apply {
        put("v", 1); put("id", id); put("f", from); put("y", type); put("ts", ts)
        text?.let { put("x", it) }
        replyTo?.let { put("r", it) }
        target?.let { put("tg", it) }
        emoji?.let { put("e", it) }
        link?.let { put("l", it.toJson()) }
        media?.let { put("m", it.toJson()) }
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
        )

        fun fromStore(o: JSONObject): Msg = fromPayload(o, o.str("k"), o.optLong("t")).copy(status = o.optInt("st", SENT))
    }
}

data class ChatState(
    val messages: List<Msg> = emptyList(),
    val reactions: Map<String, Map<String, String>> = emptyMap(), // target id -> member id -> emoji
    val byId: Map<String, Msg> = emptyMap(),
)
