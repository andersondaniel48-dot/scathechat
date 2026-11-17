package dev.daniel.scathechat.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.daniel.scathechat.data.CalEvent
import dev.daniel.scathechat.data.kindEmoji
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local heads-up notifications 15 minutes before, and at the start of, every locked-in event you're not out of. */
object Reminders {
    private val leads = listOf(15, 0)

    private fun intent(ctx: Context, e: CalEvent, start: Long, lead: Int) =
        Intent(ctx, ReminderReceiver::class.java)
            .setAction("dev.daniel.scathechat.REMINDER")
            .putExtra("eid", e.info.eid)
            .putExtra("title", kindEmoji(e.info.kind) + " " + e.info.title)
            .putExtra("start", start)
            .putExtra("lead", lead)

    private fun code(eid: String, lead: Int) = "$eid|$lead".hashCode()

    fun sync(ctx: Context, events: List<CalEvent>, myId: String?) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for (e in events) {
            val start = e.info.start
            val out = start != null && myId != null && e.votes[myId]?.get(start) == "n"
            val valid = start != null && !e.info.cancelled && !e.info.isPoll && !out
            for (lead in leads) {
                val code = code(e.info.eid, lead)
                val at = (start ?: 0L) - lead * 60_000L
                if (valid && at > now) {
                    val pi = PendingIntent.getBroadcast(ctx, code, intent(ctx, e, start!!, lead),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    try {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    } catch (_: Exception) {
                    }
                } else {
                    PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction("dev.daniel.scathechat.REMINDER"),
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { am.cancel(it); it.cancel() }
                }
            }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val start = intent.getLongExtra("start", 0L)
        val lead = intent.getIntExtra("lead", 0)
        val time = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
        val head = if (lead > 0) "$title in $lead min" else "$title is starting"
        Notifier.reminder(context, ("rem" + intent.getStringExtra("eid")).hashCode(), head, "$time · tap to open Scathe Chat")
    }
}
