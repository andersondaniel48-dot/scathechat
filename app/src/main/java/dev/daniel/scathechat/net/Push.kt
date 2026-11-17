package dev.daniel.scathechat.net

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Push notifications through Firebase Cloud Messaging, with no server of our own:
 * the sending phone asks Google to wake the other phones using the project's service-account key
 * (shared with the group, encrypted, in the group settings). The push carries no message content;
 * it only wakes the app, which then fetches and decrypts the new message itself.
 */
object Push {
    private const val TAG = "ScathePush"
    val token = MutableStateFlow<String?>(null)
    val lastError = MutableStateFlow<String?>(null)

    private fun cfg() = Repo.groupConfig.value?.optJSONObject("fcm")
    private fun sa() = Repo.groupConfig.value?.optJSONObject("sa")
    fun configured() = cfg() != null && sa() != null

    fun ensureInit(ctx: Context) {
        val c = cfg() ?: return
        try {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(
                    ctx,
                    FirebaseOptions.Builder()
                        .setApplicationId(c.optString("app"))
                        .setApiKey(c.optString("key"))
                        .setProjectId(c.optString("project"))
                        .setGcmSenderId(c.optString("sender"))
                        .build(),
                )
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { saveToken(it) }
                .addOnFailureListener { lastError.value = "Couldn't register this phone: ${it.message}" }
        } catch (e: Exception) {
            Log.w(TAG, "init", e)
            lastError.value = "Push setup error: ${e.message}"
        }
    }

    fun saveToken(t: String?) {
        t ?: return
        token.value = t
        if (Repo.prefs.fcmToken != t) {
            Repo.prefs.fcmToken = t
            Repo.pushProfile()
        }
    }

    // ---------------------------------------------------------------- setup files

    /** Pulls the Android app's settings out of google-services.json. */
    fun parseGoogleServices(text: String, pkg: String): JSONObject? = try {
        val o = JSONObject(text)
        val info = o.getJSONObject("project_info")
        val clients = o.getJSONArray("client")
        var found: JSONObject? = null
        for (i in 0 until clients.length()) {
            val c = clients.getJSONObject(i)
            if (c.optJSONObject("client_info")?.optJSONObject("android_client_info")?.optString("package_name") == pkg) found = c
        }
        found?.let { c ->
            JSONObject()
                .put("app", c.getJSONObject("client_info").getString("mobilesdk_app_id"))
                .put("key", c.getJSONArray("api_key").getJSONObject(0).getString("current_key"))
                .put("project", info.getString("project_id"))
                .put("sender", info.getString("project_number"))
        }
    } catch (e: Exception) {
        null
    }

    fun parseServiceAccount(text: String): JSONObject? = try {
        val o = JSONObject(text)
        if (o.optString("type") != "service_account") null
        else JSONObject().put("email", o.getString("client_email")).put("pk", o.getString("private_key")).put("project", o.getString("project_id"))
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- sending

    private var access: String? = null
    private var accessUntil = 0L

    private fun b64url(b: ByteArray) = Crypto.b64url(b)

    private fun accessToken(sa: JSONObject): String {
        val now = System.currentTimeMillis()
        access?.let { if (now < accessUntil - 60_000) return it }
        val iat = now / 1000
        val header = b64url(JSONObject().put("alg", "RS256").put("typ", "JWT").toString().toByteArray())
        val claims = b64url(
            JSONObject()
                .put("iss", sa.getString("email"))
                .put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                .put("aud", "https://oauth2.googleapis.com/token")
                .put("iat", iat).put("exp", iat + 3600)
                .toString().toByteArray()
        )
        val pem = sa.getString("pk").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace("\\s".toRegex(), "")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(android.util.Base64.decode(pem, android.util.Base64.DEFAULT)))
        val sig = Signature.getInstance("SHA256withRSA").apply { initSign(key); update("$header.$claims".toByteArray()) }.sign()
        val jwt = "$header.$claims.${b64url(sig)}"
        val req = Request.Builder().url("https://oauth2.googleapis.com/token")
            .post(FormBody.Builder().add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer").add("assertion", jwt).build())
            .build()
        Repo.http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IllegalStateException("Google sign-in for push failed (${r.code}): ${body.take(120)}")
            val o = JSONObject(body)
            access = o.getString("access_token")
            accessUntil = now + o.optLong("expires_in", 3600) * 1000
            return access!!
        }
    }

    /** Wakes everyone else's phone. [urgent] (calls) gets a short time-to-live. */
    fun notifyOthers(urgent: Boolean) {
        val sa = sa() ?: return
        val myId = Repo.me.value?.id
        val tokens = Repo.members.value.values.filter { it.id != myId }.mapNotNull { it.fcm }
        if (tokens.isEmpty()) return
        Repo.scope.launch {
            try {
                val at = accessToken(sa)
                val url = "https://fcm.googleapis.com/v1/projects/${sa.getString("project")}/messages:send"
                for (t in tokens) {
                    val msg = JSONObject().put(
                        "message",
                        JSONObject()
                            .put("token", t)
                            .put("data", JSONObject().put("w", if (urgent) "call" else "msg"))
                            .put("android", JSONObject().put("priority", "HIGH").put("ttl", if (urgent) "45s" else "3600s")),
                    )
                    val req = Request.Builder().url(url).header("Authorization", "Bearer $at")
                        .post(msg.toString().toRequestBody("application/json".toMediaType())).build()
                    Repo.http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) lastError.value = "Push send failed (${r.code}): ${r.body?.string()?.take(140)}"
                        else lastError.value = null
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "send", e)
                lastError.value = e.message
            }
        }
    }
}

/** Receives the wake-up push: fetch and show new messages, ring for calls. */
class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Repo.syncNow()
    }

    override fun onNewToken(token: String) {
        Push.saveToken(token)
    }
}
