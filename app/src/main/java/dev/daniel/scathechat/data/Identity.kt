package dev.daniel.scathechat.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap

/**
 * Each phone has its own signing key, kept in the Android Keystore (it can't be copied off the phone).
 * Every message is signed; the public key travels in the member's encrypted profile and is pinned the
 * first time it's seen. Someone who only has the invite code can read the chat, but can't post as David.
 */
object Identity {
    private const val ALIAS = "scathe_sign_v1"
    const val UNKNOWN = 0
    const val VERIFIED = 1
    const val WARNING = 2

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        val ks = keyStore()
        if (ks.containsAlias(ALIAS)) return
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        gen.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        gen.generateKeyPair()
    }

    fun publicKey(): String? = try {
        ensureKey()
        Crypto.b64(keyStore().getCertificate(ALIAS).publicKey.encoded)
    } catch (e: Exception) {
        Log.w("ScatheId", "public key", e)
        null
    }

    fun sign(data: ByteArray): String? = try {
        ensureKey()
        val key = keyStore().getKey(ALIAS, null) as PrivateKey
        val s = Signature.getInstance("SHA256withECDSA")
        s.initSign(key)
        s.update(data)
        Crypto.b64(s.sign())
    } catch (e: Exception) {
        Log.w("ScatheId", "sign", e)
        null
    }

    private val keyCache = ConcurrentHashMap<String, java.security.PublicKey>()

    fun verify(pub: String, data: ByteArray, sig: String): Boolean = try {
        val key = keyCache.getOrPut(pub) { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Crypto.unb64(pub))) }
        val s = Signature.getInstance("SHA256withECDSA")
        s.initVerify(key)
        s.update(data)
        s.verify(Crypto.unb64(sig))
    } catch (e: Exception) {
        false
    }

    /** The exact bytes that get signed: built field by field so both sides compute the same thing. */
    fun canon(m: Msg): ByteArray {
        val sep = "\u001f"
        val e = m.event
        val parts = listOf(
            "v1", m.id, m.from, m.type, (if (m.cts > 0) m.cts else m.ts).toString(),
            m.text ?: "", m.replyTo ?: "", m.target ?: "", m.emoji ?: "", m.data ?: "",
            m.media?.let { "${it.blob},${it.key},${it.chunks},${it.size},${it.mime}" } ?: "",
            m.link?.url ?: "",
            e?.let { "${it.eid},${it.kind},${it.title},${it.note ?: ""},${it.slots.joinToString(";")},${it.dur},${it.final ?: ""},${it.cancelled},${it.act}" } ?: "",
            m.votes?.entries?.sortedBy { it.key }?.joinToString(";") { "${it.key}=${it.value}" } ?: "",
        )
        return parts.joinToString(sep).toByteArray(Charsets.UTF_8)
    }

    // ---- pinned keys (trust on first use), stored on this phone

    private var pins: JSONObject? = null

    private fun pinsObj(): JSONObject {
        pins?.let { return it }
        val o = runCatching { JSONObject(Repo.prefs.pins ?: "{}") }.getOrElse { JSONObject() }
        pins = o
        return o
    }

    /** Returns true when this member's published key differs from the one pinned earlier. */
    @Synchronized
    fun pin(member: String, pub: String, now: Long): Boolean {
        val o = pinsObj()
        val existing = o.optJSONObject(member)
        if (existing == null) {
            o.put(member, JSONObject().put("k", pub).put("at", now))
            Repo.prefs.pins = o.toString()
            return false
        }
        return existing.optString("k") != pub
    }

    @Synchronized
    fun pinned(member: String): Pair<String, Long>? =
        pinsObj().optJSONObject(member)?.let { it.optString("k") to it.optLong("at") }

    @Synchronized
    fun clearPins() {
        pins = JSONObject()
        Repo.prefs.pins = null
    }

    private val results = ConcurrentHashMap<String, Int>()

    fun status(m: Msg): Int {
        val pin = pinned(m.from) ?: return UNKNOWN
        val cacheKey = m.id + "|" + (m.sig ?: "-")
        results[cacheKey]?.let { return it }
        val r = when {
            m.sig == null -> if (m.key != null && m.ts > pin.second + 60_000) WARNING else UNKNOWN
            verify(pin.first, canon(m), m.sig) -> VERIFIED
            else -> WARNING
        }
        results[cacheKey] = r
        return r
    }
}
