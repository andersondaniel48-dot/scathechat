package dev.daniel.scathechat.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dev.daniel.scathechat.media.MediaUtil
import dev.daniel.scathechat.net.LinkPreview
import dev.daniel.scathechat.net.Rtdb
import dev.daniel.scathechat.service.ChatService
import dev.daniel.scathechat.service.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * The sync engine. Server layout (everything except keys is ciphertext):
 *
 *   rooms/<roomId>/m/<pushKey>        {c: encrypted message, t: server time}
 *   rooms/<roomId>/s/<memberId>       {p: encrypted profile, r: last read key, a: last active}
 *   rooms/<roomId>/b/<blobId>/<i>     encrypted 512 KB chunk of a photo/video/file
 */
object Repo {
    private const val TAG = "ScatheRepo"
    const val CHUNK = 512 * 1024

    lateinit var app: Context
        private set
    lateinit var prefs: Prefs
        private set
    private lateinit var db: Db

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Firebase sends a keep-alive every ~30 s, so 75 s of silence means the stream is dead.
    private val streamHttp: OkHttpClient by lazy { http.newBuilder().readTimeout(75, TimeUnit.SECONDS).build() }

    val group = MutableStateFlow<Group?>(null)
    val me = MutableStateFlow<Me?>(null)
    val chat = MutableStateFlow(ChatState())
    val members = MutableStateFlow<Map<String, Member>>(emptyMap())
    val conn = MutableStateFlow(Conn.CONNECTING)
    val connError = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val ready = MutableStateFlow<Set<String>>(emptySet())
    val draft = MutableStateFlow<String?>(null)
    val pendingInvite = MutableStateFlow<String?>(null)
    val pendingShare = MutableStateFlow<List<Uri>>(emptyList())
    val showInvite = MutableStateFlow(false)

    val fg = MutableStateFlow(false)
    @Volatile private var useQuery = true
    @Volatile var foreground = false
        set(v) { field = v; fg.value = v }
    @Volatile var viewing = false
    @Volatile private var serverOffset = 0L
    fun now(): Long = System.currentTimeMillis() + serverOffset

    private val lock = Any()
    private val all = HashMap<String, Msg>()
    private var stateTree = JSONObject()
    @Volatile private var lastKey: String? = null
    @Volatile private var started = false
    @Volatile private var gen = 0
    @Volatile private var suppressNotify = false
    private var msgStream: EventSource? = null
    private var stateStream: EventSource? = null
    private var reconnectJob: Job? = null
    @Volatile private var backoff = 1000L
    private var lastNet: Network? = null
    private var netRegistered = false
    private val inFlight = Collections.synchronizedSet(HashSet<String>())
    private val uploaded = Collections.synchronizedSet(HashSet<String>())
    private val downloading = ConcurrentHashMap<String, Deferred<File?>>()
    private val netSem = Semaphore(6)
    @Volatile private var lastReadSent: String? = null
    private var loaded: Job? = null
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        app = ctx.applicationContext
        prefs = Prefs(app)
        db = Db(app)
        group.value = prefs.loadGroup()
        me.value = prefs.loadMe()
        if (group.value == null) me.value = null
        lastKey = prefs.lastKey
        lastReadSent = prefs.lastRead
        loaded = scope.launch {
            val msgs = db.all()
            synchronized(lock) { for (m in msgs) all[m.id] = m }
            val files = MediaUtil.mediaDir(app).listFiles()
                ?.filter { !it.name.endsWith(".part") }
                ?.map { it.name.substringBefore('.') }?.toSet() ?: emptySet()
            ready.value = files
            publish()
        }
        if (group.value != null) start()
    }

    private fun rtdb(g: Group) = Rtdb(g.dbUrl, http, streamHttp)

    fun toast(msg: String) = main.post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        if (group.value == null || started) return
        started = true
        scope.launch {
            loaded?.join()
            registerNetwork()
            connect()
            while (started) {
                if (foreground) ping()
                delay(45_000)
            }
        }
    }

    fun onForeground() {
        start()
        if (!started) return
        ping()
        if (conn.value != Conn.ONLINE) reconnectNow()
    }

    private fun registerNetwork() {
        if (netRegistered) return
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val changed = lastNet != null && lastNet != network
                    lastNet = network
                    if (started && (changed || conn.value == Conn.OFFLINE || conn.value == Conn.ERROR)) reconnectNow()
                }

                override fun onLost(network: Network) {
                    if (network == lastNet && started) conn.value = Conn.OFFLINE
                }
            })
            netRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "network callback", e)
        }
    }

    private fun reconnectNow() {
        reconnectJob?.cancel()
        backoff = 1000
        scope.launch { connect() }
    }

    private fun connect() {
        val g = group.value ?: return
        val myGen = synchronized(lock) {
            gen += 1
            msgStream?.cancel()
            stateStream?.cancel()
            gen
        }
        if (conn.value != Conn.ERROR) conn.value = Conn.CONNECTING
        suppressNotify = lastKey == null
        val rt = rtdb(g)
        val ms = rt.stream("rooms/${g.roomId}/m", if (useQuery) lastKey else null, listener(myGen, isMain = true) { t, p, d -> onMsgEvent(t, p, d) })
        val ss = rt.stream("rooms/${g.roomId}/s", null, listener(myGen, isMain = false) { t, p, d -> onStateEvent(t, p, d) })
        synchronized(lock) {
            if (myGen == gen) {
                msgStream = ms; stateStream = ss
            } else {
                ms.cancel(); ss.cancel()
            }
        }
    }

    private fun listener(myGen: Int, isMain: Boolean, onData: (String, String, Any?) -> Unit) = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            if (myGen != gen || !isMain) return
            backoff = 1000
            conn.value = Conn.ONLINE
            connError.value = null
            retryPending()
            pushProfile()
            ping()
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            if (myGen != gen) return
            when (type) {
                "put", "patch" -> try {
                    val o = JSONObject(data)
                    onData(type, o.optString("path", "/"), if (o.isNull("data")) null else o.opt("data"))
                } catch (e: Exception) {
                    Log.w(TAG, "bad event", e)
                }
                "cancel" -> {
                    connError.value = "The database refused access. Check the security rules."
                    conn.value = Conn.ERROR
                }
                "auth_revoked" -> scheduleReconnect(myGen)
            }
        }

        override fun onClosed(eventSource: EventSource) {
            if (myGen != gen) return
            if (conn.value == Conn.ONLINE) conn.value = Conn.OFFLINE
            scheduleReconnect(myGen)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (myGen != gen) return
            val code = response?.code
            if (isMain && code == 400 && useQuery) {
                // Server didn't accept the "only new messages" query: fall back to a full stream.
                useQuery = false
                scope.launch { connect() }
                return
            }
            if (code == 401 || code == 403) {
                connError.value = "The database refused access. Check the security rules."
                conn.value = Conn.ERROR
            } else if (code == 404) {
                connError.value = "Database not found. Was it deleted?"
                conn.value = Conn.ERROR
            } else {
                conn.value = Conn.OFFLINE
            }
            scheduleReconnect(myGen)
        }
    }

    private fun scheduleReconnect(myGen: Int) {
        synchronized(lock) {
            if (myGen != gen || !started) return
            if (reconnectJob?.isActive == true) return
            val wait = backoff
            backoff = min(backoff * 2, 15_000)
            reconnectJob = scope.launch {
                delay(wait)
                if (myGen == gen) connect()
            }
        }
    }

    // ---------------------------------------------------------------- incoming

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun onMsgEvent(type: String, path: String, data: Any?) {
        val segs = segments(path)
        val entries = ArrayList<Pair<String, JSONObject>>()
        if (segs.isEmpty()) {
            if (data is JSONObject) data.keys().forEach { k -> data.optJSONObject(k)?.let { entries.add(k to it) } }
        } else if (segs.size == 1 && data is JSONObject) {
            entries.add(segs[0] to data)
        }
        if (entries.isNotEmpty()) ingest(entries)
        if (segs.isEmpty() && type == "put") suppressNotify = false
    }

    private fun ingest(entries: List<Pair<String, JSONObject>>) {
        val g = group.value ?: return
        val myId = me.value?.id
        val fresh = ArrayList<Msg>()
        val changed = ArrayList<Msg>()
        synchronized(lock) {
            for ((k, v) in entries) {
                val c = v.optString("c")
                if (c.isEmpty()) continue
                val t = v.optLong("t", 0L)
                val m = try {
                    Msg.fromPayload(JSONObject(String(Crypto.open(g.key, Crypto.unb64(c), g.aad), Charsets.UTF_8)), k, t)
                } catch (e: Exception) {
                    continue
                }
                val ex = all[m.id]
                if (ex != null && ex.key == k && ex.ts == m.ts && ex.status == Msg.SENT) continue
                all[m.id] = m
                changed.add(m)
                if (ex == null && m.from != myId) fresh.add(m)
                val lk = lastKey
                if (lk == null || k > lk) lastKey = k
            }
        }
        if (changed.isEmpty()) return
        db.upsertAll(changed)
        prefs.lastKey = lastKey
        publish()
        onFresh(fresh)
    }

    private fun onFresh(fresh: List<Msg>) {
        if (fresh.isEmpty()) return
        val recent = now() - 3L * 24 * 3600 * 1000
        for (m in fresh) {
            val media = m.media ?: continue
            if (m.ts < recent) continue
            if (media.kind == "image" || media.kind == "gif" || (media.kind == "video" && media.size < 25L * 1024 * 1024)) requestMedia(m)
        }
        if (suppressNotify || (foreground && viewing)) return
        val myId = me.value?.id
        val names = members.value
        val g = group.value ?: return
        for (m in fresh) {
            if (m.ts < now() - 10 * 60_000) continue
            if (m.type == "react") {
                val target = m.target?.let { chat.value.byId[it] } ?: continue
                if (target.from != myId || m.emoji.isNullOrEmpty()) continue
            }
            Notifier.notify(app, g.name, names[m.from]?.name ?: "Someone", m, chat.value.byId)
        }
    }

    private fun onStateEvent(type: String, path: String, data: Any?) {
        val segs = segments(path)
        synchronized(lock) { stateTree = applyTree(stateTree, segs, data, type == "patch") }
        rebuildMembers()
    }

    private fun mergeInto(target: JSONObject, data: JSONObject) {
        data.keys().forEach { k ->
            val v = data.opt(k)
            if (v == null || v == JSONObject.NULL) target.remove(k) else target.put(k, v)
        }
    }

    private fun applyTree(root: JSONObject, segs: List<String>, data: Any?, patch: Boolean): JSONObject {
        if (segs.isEmpty()) {
            if (!patch) return (data as? JSONObject) ?: JSONObject()
            if (data is JSONObject) mergeInto(root, data)
            return root
        }
        var node = root
        for (s in segs.dropLast(1)) {
            val next = node.optJSONObject(s) ?: JSONObject().also { node.put(s, it) }
            node = next
        }
        val last = segs.last()
        if (patch) {
            val target = node.optJSONObject(last) ?: JSONObject().also { node.put(last, it) }
            if (data is JSONObject) mergeInto(target, data)
        } else {
            if (data == null || data == JSONObject.NULL) node.remove(last) else node.put(last, data)
        }
        return root
    }

    private fun rebuildMembers() {
        val g = group.value ?: return
        val tree = synchronized(lock) { JSONObject(stateTree.toString()) }
        val out = HashMap<String, Member>()
        tree.keys().forEach { id ->
            val o = tree.optJSONObject(id) ?: return@forEach
            val p = o.optString("p")
            if (p.isEmpty()) return@forEach
            val prof = try {
                JSONObject(String(Crypto.open(g.key, Crypto.unb64(p), g.profileAad(id)), Charsets.UTF_8))
            } catch (e: Exception) {
                return@forEach
            }
            out[id] = Member(id, prof.optString("n", "?"), prof.optInt("c", 0), o.str("r"), o.optLong("a", 0L))
        }
        me.value?.let { m -> if (m.id !in out) out[m.id] = Member(m.id, m.name, m.color, lastReadSent, now()) }
        members.value = out
    }

    private fun publish() {
        val snapshot: List<Msg> = synchronized(lock) { ArrayList(all.values) }
        val reacts = HashMap<String, HashMap<String, Pair<Long, String>>>()
        val list = ArrayList<Msg>(snapshot.size)
        for (m in snapshot) {
            if (m.type == "react") {
                val t = m.target ?: continue
                val per = reacts.getOrPut(t) { HashMap() }
                val prev = per[m.from]
                if (prev == null || prev.first <= m.ts) per[m.from] = m.ts to (m.emoji ?: "")
            } else {
                list.add(m)
            }
        }
        list.sortWith(compareBy<Msg>({ it.ts }, { it.key ?: "~" }))
        val rmap = HashMap<String, Map<String, String>>()
        for ((t, per) in reacts) {
            val m = per.filterValues { it.second.isNotEmpty() }.mapValues { it.value.second }
            if (m.isNotEmpty()) rmap[t] = m
        }
        chat.value = ChatState(list, rmap, list.associateBy { it.id })
    }

    // ---------------------------------------------------------------- outgoing

    private fun newMsg(type: String): Msg =
        Msg(id = Crypto.hex(Crypto.random(12)), key = null, from = me.value!!.id, ts = now(), type = type, status = Msg.PENDING)

    private fun put(m: Msg) {
        synchronized(lock) { all[m.id] = m }
        publish()
        scope.launch { db.upsertAll(listOf(m)) }
    }

    private fun current(m: Msg): Msg = synchronized(lock) { all[m.id] } ?: m

    fun sendText(text: String, link: LinkInfo?, replyTo: String?, allowPreview: Boolean = true) {
        if (me.value == null) return
        val first = newMsg("text").copy(text = text, link = link, replyTo = replyTo)
        put(first)
        scope.launch {
            var m = first
            if (link == null && allowPreview) {
                val url = LinkPreview.firstUrl(text)
                if (url != null) {
                    val li = withTimeoutOrNull(4_000) { LinkPreview.fetch(url) }
                    if (li != null) {
                        m = current(m).copy(link = li)
                        put(m)
                    }
                }
            }
            deliver(m)
        }
    }

    fun react(target: Msg, emoji: String) {
        val myId = me.value?.id ?: return
        val mine = chat.value.reactions[target.id]?.get(myId)
        val m = newMsg("react").copy(target = target.id, emoji = if (mine == emoji) "" else emoji)
        put(m)
        scope.launch { deliver(m) }
    }

    fun sendMedia(uri: Uri, replyTo: String?) {
        if (me.value == null || group.value == null) return
        scope.launch {
            try {
                val prep = MediaUtil.prepare(app, uri)
                val blob = Crypto.hex(Crypto.random(16))
                val out = MediaUtil.fileFor(app, blob, prep.mime)
                if (!prep.file.renameTo(out)) {
                    prep.file.copyTo(out, overwrite = true)
                    prep.file.delete()
                }
                val size = out.length()
                val chunks = max(1, ((size + CHUNK - 1) / CHUNK).toInt())
                val media = MediaInfo(blob, Crypto.b64(Crypto.random(32)), chunks, size, prep.mime, prep.name,
                    prep.kind, prep.w, prep.h, prep.dur, prep.thumb)
                markReady(blob)
                val m = newMsg("media").copy(media = media, replyTo = replyTo)
                put(m)
                deliver(m)
            } catch (e: MediaUtil.TooBig) {
                toast("That file is over 100 MB.")
            } catch (e: Exception) {
                Log.w(TAG, "send media", e)
                toast("Couldn't send that: ${e.message}")
            }
        }
    }

    fun retry(m: Msg) {
        if (m.key != null) return
        put(current(m).copy(status = Msg.PENDING))
        scope.launch { deliver(current(m)) }
    }

    private fun retryPending() {
        val myId = me.value?.id ?: return
        val pending = synchronized(lock) { all.values.filter { it.from == myId && it.key == null }.sortedBy { it.ts } }
        if (pending.isEmpty()) return
        scope.launch { for (m in pending) deliver(current(m)) }
    }

    private suspend fun deliver(m: Msg) {
        if (!inFlight.add(m.id)) return
        try {
            val g = group.value ?: return
            val media = m.media
            if (media != null && media.blob !in uploaded) {
                upload(g, media)
                uploaded.add(media.blob)
            }
            val payload = m.toPayload().toString().toByteArray(Charsets.UTF_8)
            val box = Crypto.seal(g.key, payload, g.aad)
            val body = JSONObject().put("c", Crypto.b64(box)).put("t", JSONObject().put(".sv", "timestamp")).toString()
            val key = rtdb(g).post("rooms/${g.roomId}/m", body)
            val cur = current(m)
            if (cur.key == null) put(cur.copy(key = key, status = Msg.SENT))
        } catch (e: Exception) {
            Log.w(TAG, "deliver", e)
            val cur = current(m)
            if (cur.key == null) put(cur.copy(status = Msg.FAILED))
        } finally {
            inFlight.remove(m.id)
        }
    }

    private suspend fun upload(g: Group, media: MediaInfo) {
        val file = mediaFile(media)
        if (!file.exists()) throw IOException("File is gone")
        val key = Crypto.unb64(media.key)
        val rt = rtdb(g)
        val done = AtomicInteger(0)
        setProgress(media.blob, 0f)
        try {
            coroutineScope {
                (0 until media.chunks).map { i ->
                    async {
                        netSem.withPermit {
                            val enc = Crypto.sealChunk(key, i, readChunk(file, i))
                            rt.put("rooms/${g.roomId}/b/${media.blob}/$i", JSONObject.quote(Crypto.b64(enc)))
                            setProgress(media.blob, done.incrementAndGet().toFloat() / media.chunks)
                        }
                    }
                }.awaitAll()
            }
        } finally {
            clearProgress(media.blob)
        }
    }

    private fun readChunk(file: File, i: Int): ByteArray = RandomAccessFile(file, "r").use { raf ->
        val off = i.toLong() * CHUNK
        val len = min(CHUNK.toLong(), raf.length() - off).coerceAtLeast(0).toInt()
        val b = ByteArray(len)
        raf.seek(off)
        raf.readFully(b)
        b
    }

    // ---------------------------------------------------------------- media download

    fun mediaFile(media: MediaInfo): File = MediaUtil.fileFor(app, media.blob, media.mime)

    private fun markReady(blob: String) = ready.update { it + blob }
    private fun setProgress(blob: String, p: Float) = progress.update { it + (blob to p) }
    private fun clearProgress(blob: String) = progress.update { it - blob }

    fun requestMedia(m: Msg) {
        val media = m.media ?: return
        if (media.blob in ready.value) return
        scope.launch { ensureMedia(media) }
    }

    suspend fun ensureMedia(media: MediaInfo): File? {
        val f = mediaFile(media)
        if (media.blob in ready.value && f.exists()) return f
        val d = downloading.computeIfAbsent(media.blob) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    download(media)
                } finally {
                    downloading.remove(media.blob)
                }
            }
        }
        return try {
            d.await()
        } catch (e: Exception) {
            Log.w(TAG, "download", e)
            null
        }
    }

    private suspend fun download(media: MediaInfo): File? {
        val g = group.value ?: return null
        val rt = rtdb(g)
        val key = Crypto.unb64(media.key)
        val out = mediaFile(media)
        val tmp = File(out.path + ".part")
        val done = AtomicInteger(0)
        setProgress(media.blob, 0f)
        try {
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.setLength(media.size)
                coroutineScope {
                    (0 until media.chunks).map { i ->
                        async {
                            netSem.withPermit {
                                val body = rt.get("rooms/${g.roomId}/b/${media.blob}/$i")
                                val s = JSONTokener(body).nextValue() as? String ?: throw IOException("Missing part $i")
                                val plain = Crypto.openChunk(key, i, Crypto.unb64(s))
                                synchronized(raf) {
                                    raf.seek(i.toLong() * CHUNK)
                                    raf.write(plain)
                                }
                                setProgress(media.blob, done.incrementAndGet().toFloat() / media.chunks)
                            }
                        }
                    }.awaitAll()
                }
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                tmp.delete()
            }
            markReady(media.blob)
            return out
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            clearProgress(media.blob)
        }
    }

    // ---------------------------------------------------------------- presence / profile

    fun pushProfile() {
        val g = group.value ?: return
        val m = me.value ?: return
        scope.launch {
            try {
                val prof = JSONObject().put("n", m.name).put("c", m.color).toString().toByteArray(Charsets.UTF_8)
                val box = Crypto.seal(g.key, prof, g.profileAad(m.id))
                rtdb(g).put("rooms/${g.roomId}/s/${m.id}/p", JSONObject.quote(Crypto.b64(box)))
            } catch (e: Exception) {
                Log.w(TAG, "profile", e)
            }
        }
    }

    fun ping() {
        val g = group.value ?: return
        val m = me.value ?: return
        scope.launch {
            try {
                val t0 = System.currentTimeMillis()
                val res = rtdb(g).put("rooms/${g.roomId}/s/${m.id}/a", "{\".sv\":\"timestamp\"}")
                val t1 = System.currentTimeMillis()
                res.trim().toLongOrNull()?.let { serverOffset = it - (t0 + t1) / 2 }
            } catch (_: Exception) {
            }
        }
    }

    fun markRead() {
        val g = group.value ?: return
        val m = me.value ?: return
        Notifier.clear(app)
        val latest = chat.value.messages.lastOrNull { it.key != null }?.key ?: return
        if (latest == lastReadSent) return
        lastReadSent = latest
        prefs.lastRead = latest
        scope.launch {
            try {
                rtdb(g).put("rooms/${g.roomId}/s/${m.id}/r", JSONObject.quote(latest))
            } catch (_: Exception) {
                lastReadSent = null
            }
        }
    }

    fun updateMe(name: String, color: Int) {
        val m = me.value ?: return
        val n = m.copy(name = name.ifBlank { m.name }, color = color)
        me.value = n
        prefs.saveMe(n)
        pushProfile()
        rebuildMembers()
    }

    // ---------------------------------------------------------------- setup

    /** Returns an error message, or null if the database is reachable and locked down. */
    suspend fun checkDb(g: Group, strict: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val rt = rtdb(g)
            val (rootCode, _) = rt.raw("", mapOf("shallow" to "true"))
            if (rootCode == 200 && strict) {
                return@withContext "Your database is open to anyone (Firebase \"test mode\"). Paste the Scathe Chat rules " +
                    "in Firebase → Realtime Database → Rules, tap Publish, then try again."
            }
            val (code, body) = rt.raw("rooms/${g.roomId}/s", mapOf("shallow" to "true"))
            when (code) {
                200 -> null
                401, 403 -> "The database refused access. Paste the Scathe Chat rules in Firebase → Realtime Database → Rules and tap Publish."
                404 -> "No database at that address. Copy the URL shown at the top of Firebase → Realtime Database → Data."
                423 -> "That database is disabled in Firebase."
                else -> "Database error $code: ${body.take(120)}"
            }
        } catch (e: Exception) {
            "Couldn't reach the database: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun createGroup(dbUrl: String, groupName: String, name: String, color: Int): String? {
        val url = normalizeDbUrl(dbUrl) ?: return "That doesn't look like a database URL."
        val g = Group(url, Crypto.random(32), groupName.trim().ifBlank { "Brothers" })
        checkDb(g, strict = true)?.let { return it }
        join(g, name, color)
        showInvite.value = true
        return null
    }

    suspend fun joinWithInvite(code: String, name: String, color: Int): String? {
        val g = Group.parseInvite(code) ?: return "That invite code isn't valid. Ask for a fresh one."
        checkDb(g, strict = false)?.let { return it }
        join(g, name, color)
        return null
    }

    private fun join(g: Group, name: String, color: Int) {
        val m = Me(Crypto.hex(Crypto.random(16)), name.trim().ifBlank { "Me" }, color)
        prefs.saveGroup(g)
        prefs.saveMe(m)
        prefs.lastKey = null
        lastKey = null
        group.value = g
        me.value = m
        start()
        pushProfile()
        ChatService.start(app)
    }

    fun leave() {
        started = false
        synchronized(lock) {
            gen += 1
            msgStream?.cancel(); stateStream?.cancel()
            msgStream = null; stateStream = null
            reconnectJob?.cancel()
            all.clear()
            stateTree = JSONObject()
        }
        ChatService.stop(app)
        db.clear()
        prefs.clear()
        MediaUtil.mediaDir(app).deleteRecursively()
        lastKey = null
        lastReadSent = null
        uploaded.clear()
        members.value = emptyMap()
        chat.value = ChatState()
        ready.value = emptySet()
        conn.value = Conn.CONNECTING
        group.value = null
        me.value = null
        Notifier.clear(app)
    }
}
