package dev.daniel.scathechat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.daniel.scathechat.R
import dev.daniel.scathechat.data.Repo
import kotlinx.coroutines.launch

val FIREBASE_RULES = """{
  "rules": {
    "rooms": {
      "${'$'}room": {
        "m": {
          ".read": "${'$'}room.length == 64",
          "${'$'}k": {
            ".write": "${'$'}room.length == 64 && !data.exists()",
            ".validate": "newData.hasChildren(['c', 't']) && newData.child('c').isString() && newData.child('c').val().length < 400000"
          }
        },
        "s": {
          ".read": "${'$'}room.length == 64",
          "${'$'}member": {
            ".write": "${'$'}room.length == 64 && ${'$'}member.length == 32"
          }
        },
        "b": {
          "${'$'}blob": {
            ".read": "${'$'}room.length == 64 && ${'$'}blob.length == 32",
            "${'$'}i": {
              ".write": "${'$'}room.length == 64 && ${'$'}blob.length == 32",
              ".validate": "newData.isString() && newData.val().length < 1000000"
            }
          }
        }
      }
    }
  }
}"""

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
fun SetupScreen() {
    var mode by rememberSaveable { mutableStateOf("welcome") }
    val invite by Repo.pendingInvite.collectAsState()
    LaunchedEffect(invite) { if (invite != null) mode = "join" }
    BackHandler(enabled = mode != "welcome") { mode = "welcome" }

    Box(Modifier.fillMaxSize()) {
        EmberBackground(Modifier.fillMaxSize(), intensity = if (mode == "welcome") 1.25f else 0.8f)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            SetupContent(mode, setMode = { mode = it })
        }
    }
}

@Composable
private fun SetupContent(mode: String, setMode: (String) -> Unit) {
    run {
        when (mode) {
            "create" -> CreateForm { setMode("welcome") }
            "join" -> JoinForm { setMode("welcome") }
            else -> Welcome(onJoin = { setMode("join") }, onCreate = { setMode("create") })
        }
    }
}

@Composable
private fun Logo(size: Int = 132) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(size.dp))
}

@Composable
private fun Welcome(onJoin: () -> Unit, onCreate: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo(112)
        Text("SCATHE", style = fireText(64.sp), letterSpacing = 2.sp)
        Text("chat", color = C.Bone.copy(alpha = 0.75f), fontFamily = Display, fontSize = 20.sp, letterSpacing = 14.sp)
        Spacer(Modifier.height(14.dp))
        Text("Private. Encrypted. Brothers only.", color = C.Smoke, fontSize = 15.sp)
        Spacer(Modifier.height(56.dp))
        FireButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().height(56.dp)) { ButtonLabel("Join with an invite") }
        Spacer(Modifier.height(12.dp))
        GlassButton(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp)) { ButtonLabel("Create the group") }
        Spacer(Modifier.height(18.dp))
        Text(
            "Only one of you creates the group. Everyone else joins by scanning the invite QR.",
            color = C.Faint, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        C.members.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(c)
                    .then(if (i == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) {
                if (i == selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FormScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back", color = C.Smoke) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(60)
            Text(title, style = fireText(26.sp, glow = 0.6f))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), fill = Color(0x2E120906)).padding(18.dp)) {
            content()
        }
    }
}

@Composable
private fun ErrorText(err: String?) {
    if (err != null) {
        Spacer(Modifier.height(10.dp))
        Text(err, color = C.Crimson, fontSize = 14.sp)
    }
}

@Composable
private fun CreateForm(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var groupName by rememberSaveable { mutableStateOf("Brothers") }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    FormScaffold("Create the group", onBack) {
        Text(
            "Scathe Chat stores only encrypted data, in a free Firebase database that you own. One-time setup (about 3 minutes):",
            color = C.Dim, fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        Steps(
            "Open console.firebase.google.com and sign in with any Google account.",
            "Create a project (any name, Analytics off).",
            "Build → Realtime Database → Create database → pick a location → Start in locked mode.",
            "Open the Rules tab, replace everything with the Scathe Chat rules (copy button below) and tap Publish.",
            "On the Data tab, copy the database URL (https://…firebaseio.com or …firebasedatabase.app) and paste it here.",
        )
        Spacer(Modifier.height(8.dp))
        GlassButton(onClick = { copyText(ctx, "Scathe Chat rules", FIREBASE_RULES) }) {
            Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            ButtonLabel("Copy the rules", 15)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            url, { url = it; err = null }, label = { Text("Database URL") }, singleLine = true,
            placeholder = { Text("https://your-project-default-rtdb.firebaseio.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            groupName, { groupName = it }, label = { Text("Group name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            name, { name = it }, label = { Text("Your name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text("Your color", color = C.Dim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        ColorPicker(color) { color = it }
        ErrorText(err)
        Spacer(Modifier.height(20.dp))
        FireButton(
            onClick = {
                busy = true; err = null
                scope.launch {
                    err = Repo.createGroup(url, groupName, name, color)
                    busy = false
                }
            },
            enabled = !busy && url.isNotBlank() && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            else ButtonLabel("Create group")
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun Steps(vararg steps: String) {
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Box(
                Modifier.size(24.dp).molten(CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", color = Color.White, fontFamily = Display, fontSize = 12.sp) }
            Spacer(Modifier.size(10.dp))
            Text(s, color = C.Text, fontSize = 14.sp)
        }
    }
}

@Composable
private fun JoinForm(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val invite by Repo.pendingInvite.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(invite) {
        invite?.let { code = it; Repo.pendingInvite.value = null }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        r.contents?.let { code = it; err = null }
    }

    FormScaffold("Join the group", onBack) {
        Text("Ask whoever created the group to open Scathe Chat → Invite, then scan their QR code.", color = C.Dim, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        FireButton(
            onClick = {
                scanner.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the Scathe Chat invite")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Filled.QrCodeScanner, null)
            Spacer(Modifier.size(10.dp))
            ButtonLabel(if (code.isBlank()) "Scan invite QR" else "Scan again")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            code, { code = it.trim(); err = null }, label = { Text("…or paste the invite code") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (code.isNotBlank()) Icon(Icons.Filled.Check, null, tint = C.Online) },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            name, { name = it }, label = { Text("Your name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Text("Your color", color = C.Dim, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        ColorPicker(color) { color = it }
        ErrorText(err)
        Spacer(Modifier.height(20.dp))
        FireButton(
            onClick = {
                busy = true; err = null
                scope.launch {
                    err = Repo.joinWithInvite(code, name, color)
                    busy = false
                }
            },
            enabled = !busy && code.isNotBlank() && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            else ButtonLabel("Join")
        }
        Spacer(Modifier.height(30.dp))
    }
}
