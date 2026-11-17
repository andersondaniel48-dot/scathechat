package dev.daniel.scathechat.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.io.IOException

class HttpError(val code: Int, body: String) : IOException("HTTP $code ${body.take(160)}")

/**
 * Minimal Firebase Realtime Database client over its REST API.
 * Live updates use the REST streaming endpoint (Server-Sent Events), so the only
 * setup needed is the database URL — no Firebase SDK or google-services.json.
 */
class Rtdb(base: String, private val http: OkHttpClient, private val streamHttp: OkHttpClient) {
    private val base = base.trimEnd('/')
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String, query: Map<String, String> = emptyMap()): HttpUrl {
        val b = "$base/${path.trim('/')}.json".toHttpUrl().newBuilder()
        for ((k, v) in query) b.addQueryParameter(k, v)
        return b.build()
    }

    /** Returns (status code, body) without throwing on HTTP errors. */
    fun raw(path: String, query: Map<String, String> = emptyMap()): Pair<Int, String> {
        http.newCall(Request.Builder().url(url(path, query)).get().build()).execute().use { r ->
            return r.code to (r.body?.string() ?: "")
        }
    }

    fun get(path: String): String = exec(Request.Builder().url(url(path)).get().build())

    fun put(path: String, body: String): String = exec(Request.Builder().url(url(path)).put(body.toRequestBody(json)).build())

    fun patch(path: String, body: String): String = exec(Request.Builder().url(url(path)).patch(body.toRequestBody(json)).build())

    /** Children of [path] from [startKey] on (inclusive), ordered by key. */
    fun since(path: String, startKey: String?): String {
        val q = if (startKey != null) mapOf("orderBy" to "\"\$key\"", "startAt" to "\"$startKey\"") else emptyMap()
        return exec(Request.Builder().url(url(path, q)).get().build())
    }

    /** Appends a child with a server-generated, time-ordered key and returns that key. */
    fun post(path: String, body: String): String =
        JSONObject(exec(Request.Builder().url(url(path)).post(body.toRequestBody(json)).build())).getString("name")

    private fun exec(req: Request): String {
        http.newCall(req).execute().use { r ->
            val body = r.body?.string() ?: ""
            if (!r.isSuccessful) throw HttpError(r.code, body)
            return body
        }
    }

    fun stream(path: String, startAtKey: String?, listener: EventSourceListener): EventSource {
        val q = if (startAtKey != null) mapOf("orderBy" to "\"\$key\"", "startAt" to "\"$startAtKey\"") else emptyMap()
        val req = Request.Builder().url(url(path, q)).header("Accept", "text/event-stream").build()
        return EventSources.createFactory(streamHttp).newEventSource(req, listener)
    }
}
