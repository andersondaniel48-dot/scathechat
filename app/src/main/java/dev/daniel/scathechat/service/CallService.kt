package dev.daniel.scathechat.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dev.daniel.scathechat.call.CallManager

/** Keeps the microphone (and camera on video calls) running when the screen is off or the app is in the background. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val video = intent?.getBooleanExtra("video", false) ?: false
        val n = Notifier.ongoingCall(this, video)
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                if (video) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                startForeground(Notifier.CALL_ID, n, type)
            } else {
                startForeground(Notifier.CALL_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        fun start(ctx: Context, video: Boolean) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, CallService::class.java).putExtra("video", video))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CallService::class.java))
        }
    }
}

/** Decline / hang up buttons on call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "hangup") CallManager.hangUp() else CallManager.decline()
    }
}
