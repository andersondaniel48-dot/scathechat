package dev.daniel.scathechat

import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import android.os.Build
import dev.daniel.scathechat.call.CallManager
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.service.Notifier
import dev.daniel.scathechat.service.ChatService
import dev.daniel.scathechat.ui.AppRoot
import dev.daniel.scathechat.ui.ScatheTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        if (CrashGuard.safeMode) { setContent { SafeMode() }; return }
        setContent { ScatheTheme { AppRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Repo.foreground = true
        Repo.onForeground()
        ChatService.start(this)
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= 27 && CallManager.incoming.value == null && CallManager.active.value == null) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }
        Repo.foreground = false
        dev.daniel.scathechat.net.Updater.onBackground(this)
        super.onStop()
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        when (i.action) {
            Notifier.ACTION_ANSWER -> CallManager.answerRequested.value = true
            Intent.ACTION_VIEW -> i.data?.toString()?.let { Repo.pendingInvite.value = it }
            Intent.ACTION_SEND -> {
                val text = i.getStringExtra(Intent.EXTRA_TEXT)
                val stream = IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (stream != null) Repo.pendingShare.value = listOf(stream)
                if (!text.isNullOrBlank()) Repo.draft.value = text
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
                if (!list.isNullOrEmpty()) Repo.pendingShare.value = list.toList()
            }
        }
    }
}


/** Bare recovery screen (no theme, no chat) shown after the app crashed twice on launch. */
@androidx.compose.runtime.Composable
private fun SafeMode() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state by dev.daniel.scathechat.net.Updater.state.collectAsState()
    val avail by dev.daniel.scathechat.net.Updater.available.collectAsState()
    androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        androidx.compose.material3.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                androidx.compose.material3.Text("Scathe Chat hit a problem", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
                androidx.compose.material3.Text(
                    when {
                        state == "downloading" -> "Downloading the fix…"
                        state == "ready" || state == "installing" -> "Installing the fix…"
                        avail != null -> "A fix is available."
                        else -> "Checking for a fix. A crash report was saved to your Downloads folder; send it to Claude."
                    },
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(20.dp))
                androidx.compose.material3.Button(onClick = { dev.daniel.scathechat.net.Updater.fixNow(ctx) }) { androidx.compose.material3.Text("Check for the fix") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    CrashGuard.reset(ctx)
                    (ctx as? android.app.Activity)?.let { a -> a.finish(); a.startActivity(android.content.Intent(a, MainActivity::class.java)) }
                }) { androidx.compose.material3.Text("Try opening normally") }
            }
        }
    }
}
