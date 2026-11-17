@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.daniel.scathechat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.net.Push
import org.json.JSONObject

private fun readText(ctx: android.content.Context, uri: Uri): String? = try {
    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
} catch (_: Exception) {
    null
}

private fun saveCfg(change: (JSONObject) -> Unit) {
    val next = JSONObject(Repo.groupConfig.value?.toString() ?: "{}")
    change(next)
    Repo.postData("cfg", next)
}

@Composable
private fun StepLine(n: Int, text: String, done: Boolean = false) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (done) C.Online else Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "$n", color = if (done) Color.Black else C.Flame, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = C.Bone, fontSize = 14.sp)
    }
}

/** One-time push setup: pick two files from the Firebase console. Shared with the group, encrypted. */
@Composable
fun PushSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val cfg by Repo.groupConfig.collectAsState()
    val token by Push.token.collectAsState()
    val err by Push.lastError.collectAsState()
    val members by Repo.members.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    val hasApp = cfg?.optJSONObject("fcm") != null
    val hasKey = cfg?.optJSONObject("sa") != null
    val pickApp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val text = uri?.let { readText(ctx, it) } ?: return@rememberLauncherForActivityResult
        val parsed = Push.parseGoogleServices(text, ctx.packageName)
        if (parsed == null) note = "That file has no Android app for ${ctx.packageName}. Add one with exactly that package name, then download google-services.json again."
        else { saveCfg { it.put("fcm", parsed) }; note = "App settings saved." }
    }
    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val text = uri?.let { readText(ctx, it) } ?: return@rememberLauncherForActivityResult
        val parsed = Push.parseServiceAccount(text)
        if (parsed == null) note = "That isn't a service account key file. It should be the JSON from Service accounts → Generate new private key."
        else { saveCfg { it.put("sa", parsed) }; note = "Service account key saved." }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Push notifications", style = fireText(24.sp, glow = 0.5f))
            Spacer(Modifier.height(6.dp))
            Text(
                "Lets phones wake each other up through Google, so messages and calls arrive even when Android puts the app to sleep, " +
                    "without the always-on background connection. The push only says \"wake up\": no message content goes to Google. " +
                    "Only one of you sets this up.",
                color = C.Smoke, fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text("In the Firebase console (same project as your database):", color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            StepLine(1, "Project settings → Your apps → Add app → Android. Package name: ${ctx.packageName}. Register, then download google-services.json.", hasApp)
            StepLine(2, "Project settings → Service accounts → Generate new private key. It downloads a .json file.", hasKey)
            StepLine(3, "Pick both files below.", hasApp && hasKey)
            Spacer(Modifier.height(12.dp))
            GlassButton(onClick = { pickApp.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(if (hasApp) "google-services.json ✓ (pick again)" else "Pick google-services.json", 15)
            }
            Spacer(Modifier.height(8.dp))
            GlassButton(onClick = { pickKey.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(if (hasKey) "Service account key ✓ (pick again)" else "Pick service account key", 15)
            }
            note?.let { Text(it, color = C.Flame, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Status", color = C.Bone, fontFamily = Display, fontSize = 15.sp)
            Text(
                when {
                    !hasApp || !hasKey -> "Not set up yet."
                    token == null -> "Registering this phone… (reopen the app if this doesn't change)"
                    else -> "This phone is registered for push."
                },
                color = C.Smoke, fontSize = 13.sp,
            )
            val ready = members.values.filter { it.fcm != null }.map { it.name }
            if (hasApp && hasKey) Text("Phones registered: " + (if (ready.isEmpty()) "none yet" else ready.sorted().joinToString(", ")), color = C.Smoke, fontSize = 13.sp)
            err?.let { Text(it, color = Color(0xFFFFB4A0), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (hasApp && hasKey) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Once everyone shows as registered, you can turn off Instant notifications (the always-on connection) in Settings to save battery.",
                    color = C.Faint, fontSize = 12.sp,
                )
            }
        }
    }
}

/** Group-wide setting: how long photos, videos and files stay on the server. */
@Composable
fun StorageSection() {
    val cfg by Repo.groupConfig.collectAsState()
    val days = cfg?.optInt("retain", 90) ?: 90
    Text("Server storage", color = C.Text, fontSize = 16.sp)
    Text(
        "Photos, videos and files are deleted from the server after this long, to stay inside Firebase's free 1 GB. " +
            "Phones that already downloaded them keep their copies. Messages are never deleted.",
        color = C.Dim, fontSize = 13.sp,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(30 to "30 days", 90 to "90 days", 180 to "180 days", 0 to "Forever").forEach { (d, label) ->
            val on = days == d
            val shape = RoundedCornerShape(14.dp)
            Text(
                label, color = if (on) Color.White else C.Smoke, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x30140A06), rim = 0.5f))
                    .clickable { if (!on) saveCfg { it.put("retain", d) } }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}
