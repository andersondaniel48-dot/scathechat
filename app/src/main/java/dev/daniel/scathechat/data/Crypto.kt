package dev.daniel.scathechat.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * All encryption happens on the phone. The server only ever sees ciphertext.
 *
 * - The group secret (32 random bytes) lives in the invite code and never touches the server.
 * - HKDF-SHA256 turns it into a room id (where the data lives) and a message key.
 * - Messages and profiles: AES-256-GCM with a random 96-bit nonce.
 * - Each photo/video/file gets its own random AES-256 key, carried inside the encrypted message.
 */
object Crypto {
    private val rng = SecureRandom()

    fun random(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }

    fun hex(b: ByteArray): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(b.size * 2)
        for (x in b) {
            val v = x.toInt() and 0xff
            sb.append(chars[v ushr 4]).append(chars[v and 0x0f])
        }
        return sb.toString()
    }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    fun b64url(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun unb64url(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** RFC 5869 HKDF-SHA256. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int = 32): ByteArray {
        val prk = hmac(salt, ikm)
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < len) {
            t = hmac(prk, t + info.toByteArray(Charsets.UTF_8) + byteArrayOf(i.toByte()))
            out.write(t)
            i++
        }
        return out.toByteArray().copyOf(len)
    }

    /** AES-256-GCM. Output = nonce(12) || ciphertext+tag. */
    fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        val nonce = random(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) c.updateAAD(aad)
        return nonce + c.doFinal(plain)
    }

    fun open(key: ByteArray, box: ByteArray, aad: ByteArray?): ByteArray {
        require(box.size >= 28) { "short box" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, box, 0, 12))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(box, 12, box.size - 12)
    }

    // Media chunks: the key is unique per file, so the chunk number is a safe nonce.
    private fun chunkNonce(index: Int): ByteArray = ByteBuffer.allocate(12).putInt(8, index).array()

    fun sealChunk(key: ByteArray, index: Int, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, chunkNonce(index)))
        return c.doFinal(plain)
    }

    fun openChunk(key: ByteArray, index: Int, box: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, chunkNonce(index)))
        return c.doFinal(box)
    }
}

/** Wraps the group secret with a hardware-backed Android Keystore key before it is saved to disk. */
object Vault {
    private const val ALIAS = "scathe_vault_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun wrap(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Crypto.b64(c.iv + c.doFinal(plain))
    }

    fun unwrap(s: String): ByteArray {
        val b = Crypto.unb64(s)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
        return c.doFinal(b, 12, b.size - 12)
    }
}
