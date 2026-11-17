package dev.daniel.scathechat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

/**
 * Collects what the interactive DMZ map sites load (their map data, scripts and the addresses of their map
 * pieces) into one zip saved to Downloads, which the user can hand to Claude, so we can build our own map from the same official data.
 * Nothing is uploaded anywhere by this: it opens the share sheet and the user decides.
 */
private val CAPTURE_PAGES = listOf(
    "official" to "https://www.callofduty.com/guides/modernwarfare4/dmz-maps/hajin",
    "codcentral" to "https://codcentral.net/blog/mw4-dmz-map/",
    "radar" to "https://dmzradar.com/map.html",
    "dmzmap" to "https://dmzmap.com/",
    "dmzmap_x01" to "https://dmzmap.com/x01/map",
)

private const val COLLECT_JS = """
(function(){
  try { performance.setResourceTimingBufferSize(20000); } catch(e) {}
  var r = performance.getEntriesByType('resource').map(function(e){ return {u:e.name, t:e.initiatorType, s:(e.encodedBodySize||e.transferSize||0)}; });
  var g = {};
  try {
    Object.keys(window).forEach(function(k){
      try {
        var v = window[k];
        if (v && typeof v === 'object' && !(v instanceof Node) && k.length < 60) {
          var s = JSON.stringify(v);
          if (s && s.length > 200 && s.length < 4000000 && /(lat|lng|x"|y"|coord|marker|poi|key)/i.test(s.slice(0, 20000))) g[k] = s;
        }
      } catch(e) {}
    });
  } catch(e) {}
  return JSON.stringify({url: location.href, w: innerWidth, h: innerHeight, res: r, globals: g, html: document.documentElement.outerHTML.slice(0, 4000000)});
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DmzCaptureScreen(onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Starting…") }
    var step by remember { mutableStateOf(0) }
    val web = remember {
        WebView(ctx).apply {
            setBackgroundColor(0xFF0B0705.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    view.evaluateJavascript("try{performance.setResourceTimingBufferSize(20000)}catch(e){}", null)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val dir = File(ctx.cacheDir, "dmz_capture").apply { deleteRecursively(); mkdirs() }
        var budget = 60L * 1024 * 1024
        CAPTURE_PAGES.forEachIndexed { i, (name, url) ->
            step = i
            status = "Reading map ${i + 1} of ${CAPTURE_PAGES.size}: ${url.substringAfter("//").substringBefore("/")}"
            web.loadUrl(url)
            delay(14_000) // let the map and its data load
            // nudge the map so it loads its detailed pieces too
            web.evaluateJavascript(
                "(function(){var c=document.querySelector('canvas, .leaflet-container, .maplibregl-canvas, [class*=map]');" +
                    "if(c){var r=c.getBoundingClientRect();['wheel'].forEach(function(t){for(var k=0;k<6;k++)c.dispatchEvent(new WheelEvent(t,{deltaY:-240,clientX:r.left+r.width/2,clientY:r.top+r.height/2,bubbles:true}));});}})()",
                null,
            )
            delay(5_000)
            val raw = suspendCancellableCoroutine { cont -> web.evaluateJavascript(COLLECT_JS) { cont.resume(it) } }
            val text = runCatching { JSONTokener(raw).nextValue() as String }.getOrNull() ?: return@forEachIndexed
            val pageDir = File(dir, name).apply { mkdirs() }
            File(pageDir, "page.json").writeText(text)
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return@forEachIndexed
            val res = o.optJSONArray("res") ?: JSONArray()
            status = "Saving map ${i + 1} data…"
            withContext(Dispatchers.IO) {
                val seen = HashSet<String>()
                var n = 0
                for (k in 0 until res.length()) {
                    val e = res.optJSONObject(k) ?: continue
                    val u = e.optString("u")
                    val t = e.optString("t")
                    if (!u.startsWith("http") || !seen.add(u)) continue
                    val path = u.substringBefore('?').lowercase()
                    val isImage = Regex("\\.(png|jpe?g|webp|avif|gif|svg|ico)$").containsMatchIn(path) || t == "img" || t == "css" && path.endsWith(".png")
                    val wanted = !isImage && (t == "fetch" || t == "xmlhttprequest" || t == "script" || t == "other" ||
                        Regex("\\.(json|geojson|js|mjs|csv|txt|xml)$").containsMatchIn(path))
                    if (!wanted || budget <= 0) continue
                    val bytes = runCatching {
                        Repo.http.newCall(okhttp3.Request.Builder().url(u).header("User-Agent", web.settings.userAgentString).build()).execute().use { r ->
                            if (!r.isSuccessful) null else r.body?.bytes()
                        }
                    }.getOrNull() ?: continue
                    if (bytes.size > 8 * 1024 * 1024) continue
                    budget -= bytes.size
                    val safe = path.substringAfterLast('/').replace(Regex("[^a-z0-9._-]"), "_").take(60).ifEmpty { "data" }
                    File(pageDir, "${n++}_$safe").writeBytes(bytes)
                    File(pageDir, "urls.txt").appendText("${n - 1}_$safe\t$u\n")
                }
            }
        }
        status = "Packing…"
        val zip = File(ctx.cacheDir, "dmz-map-data.zip")
        withContext(Dispatchers.IO) {
            ZipOutputStream(zip.outputStream().buffered()).use { z ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        val name = "scathe-dmz-map-data-${System.currentTimeMillis() / 1000}.zip"
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                if (android.os.Build.VERSION.SDK_INT < 29) return@runCatching false
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val r = ctx.contentResolver
                val uri = r.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
                r.openOutputStream(uri)?.use { out -> zip.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
        }
        if (saved) {
            status = "Saved to your Downloads folder as $name (${zip.length() / 1024} KB). Attach it in the Claude chat."
        } else {
            status = "Couldn't save to Downloads on this phone, so pick where to send it."
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", zip)
            ctx.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "Send map data",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0705))) {
        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().padding(top = 110.dp))
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp).glass(RoundedCornerShape(18.dp), fill = Color(0xF2140A06)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Collecting map data", color = C.Bone, fontFamily = Display, fontSize = 17.sp)
            Text(status, color = C.Smoke, fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { (step + 1f) / CAPTURE_PAGES.size }, color = C.Ember, trackColor = Color(0x22FFFFFF),
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
            GlassButton(onClick = onDone) { ButtonLabel("Close", 15) }
        }
    }
}
