package dev.daniel.scathechat

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.daniel.scathechat.call.CallManager
import dev.daniel.scathechat.service.Notifier
import dev.daniel.scathechat.ui.C
import dev.daniel.scathechat.ui.CallOverlay
import dev.daniel.scathechat.ui.ScatheTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The ringing and in-call screen when the phone is locked or the app is closed.
 * It's its own screen (like the phone app's) so it works over the lock screen without exposing the chat.
 */
class CallActivity : ComponentActivity() {
    private var pending: (() -> Unit)? = null
    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val action = pending
        pending = null
        if (res.values.all { it } && action != null) action()
    }

    private fun withCallPermissions(video: Boolean, action: () -> Unit) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) action() else {
            pending = action
            perms.launch(needed.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            ScatheTheme {
                Box(Modifier.fillMaxSize().background(C.Char)) {
                    CallOverlay(acceptWithPermissions = { video, action -> withCallPermissions(video, action) })
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(CallManager.active, CallManager.incoming, CallManager.minimized) { a, i, m -> Triple(a, i, m) }
                    .collect { (a, i, m) ->
                        when {
                            a == null && i == null -> finishAndRemoveTask()
                            a != null && m -> openChat()
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Notifier.ACTION_ANSWER) CallManager.answerRequested.value = true
    }

    private var leaving = false

    /** Minimizing goes to the chat, which needs the phone unlocked first. */
    private fun openChat() {
        if (leaving) return
        leaving = true
        val go = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finishAndRemoveTask()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = go()
                override fun onDismissCancelled() { leaving = false; CallManager.minimized.value = false }
                override fun onDismissError() { leaving = false; CallManager.minimized.value = false }
            })
        } else go()
    }
}
