package dev.daniel.scathechat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.service.ChatService
import dev.daniel.scathechat.ui.AppRoot
import dev.daniel.scathechat.ui.ScatheTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
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
        Repo.foreground = false
        super.onStop()
    }

    private fun handleIntent(i: Intent?) {
        i ?: return
        when (i.action) {
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
