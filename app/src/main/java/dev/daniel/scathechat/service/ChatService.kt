package dev.daniel.scathechat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import dev.daniel.scathechat.MainActivity
import dev.daniel.scathechat.R
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo

/** Keeps one quiet connection open so messages arrive instantly, even with the app closed. */
class ChatService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = Notifier.serviceNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Notifier.SERVICE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(Notifier.SERVICE_ID, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return
        }
        Repo.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    companion object {
        fun start(ctx: Context) {
            if (!Repo.prefs.background || Repo.group.value == null) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ChatService::class.java))
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, ChatService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ChatService.start(context)
    }
}

object Notifier {
    private const val CH_MSG = "messages"
    private const val CH_SVC = "connection"
    private const val MSG_ID = 1
    const val SERVICE_ID = 2

    private val recent = ArrayList<Pair<String, String>>()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH_MSG, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages from the group"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_SVC, "Background connection", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Shown while Scathe Chat stays connected for instant messages. You can hide this one."
            setShowBadge(false)
        })
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun serviceNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, CH_SVC)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Scathe Chat is connected")
            .setContentText("Messages will arrive instantly")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(ctx))
            .build()

    fun notify(ctx: Context, groupName: String, sender: String, m: Msg, byId: Map<String, Msg>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = if (m.type == "react") {
            val t = m.target?.let { byId[it] }
            "reacted ${m.emoji} to “${t?.preview()?.take(40) ?: "your message"}”"
        } else {
            m.preview()
        }
        val style = synchronized(recent) {
            recent.add(sender to text)
            while (recent.size > 7) recent.removeAt(0)
            val st = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
                .setConversationTitle(groupName)
                .setGroupConversation(true)
            val base = System.currentTimeMillis() - recent.size
            recent.forEachIndexed { i, (who, body) ->
                st.addMessage(body, base + i, Person.Builder().setName(who).build())
            }
            st
        }
        val n = NotificationCompat.Builder(ctx, CH_MSG)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFE5383B.toInt())
            .setStyle(style)
            .setContentTitle(groupName)
            .setContentText("$sender: $text")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(MSG_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun clear(ctx: Context) {
        synchronized(recent) { recent.clear() }
        NotificationManagerCompat.from(ctx).cancel(MSG_ID)
    }
}
