@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.daniel.scathechat.ui

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.scathechat.data.CalEvent
import dev.daniel.scathechat.data.Crypto
import dev.daniel.scathechat.data.EventInfo
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.Msg
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.data.kindEmoji
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

// ---------------------------------------------------------------- time helpers

private val dayTime = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
private val zoneFmt = DateTimeFormatter.ofPattern("zzz")
private val shortTime = DateTimeFormatter.ofPattern("h:mm a zzz")
private val dayShortTime = DateTimeFormatter.ofPattern("EEE h:mm a zzz")

private fun zoneOf(id: String?): ZoneId = try {
    if (id == null) ZoneId.systemDefault() else ZoneId.of(id)
} catch (_: Exception) {
    ZoneId.systemDefault()
}

fun localDateTime(ts: Long): String {
    val z = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    return z.format(dayTime) + " " + z.format(zoneFmt)
}

/** "David 8:00 PM CST · Sam Sat 1:00 AM GMT" for brothers in a different time zone than me. */
fun othersTimes(ts: Long, members: Map<String, Member>, myId: String): String? {
    val mine = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
    val parts = members.values.filter { it.id != myId && it.tz != null }
        .sortedBy { it.name.lowercase() }
        .mapNotNull { m ->
            val z = zoneOf(m.tz)
            if (z.rules.getOffset(mine.toInstant()) == mine.offset) return@mapNotNull null
            val theirs = mine.withZoneSameInstant(z)
            m.name + " " + theirs.format(if (theirs.toLocalDate() != mine.toLocalDate()) dayShortTime else shortTime)
        }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

private fun relative(start: Long, durMin: Int, now: Long): String {
    val end = start + durMin * 60_000L
    if (now in start until end) return "LIVE NOW"
    if (now >= end) return "Ended"
    val mins = (start - now) / 60_000
    return when {
        mins < 1 -> "starting now"
        mins < 60 -> "in ${mins}m"
        mins < 24 * 60 -> "in ${mins / 60}h ${mins % 60}m"
        else -> "in ${mins / (24 * 60)}d ${(mins % (24 * 60)) / 60}h"
    }
}

private fun kindName(kind: String) = when (kind) {
    "filth" -> "Filthification"
    "cod" -> "CoD session"
    else -> "Event"
}

private fun voteColor(v: String?): Color = when (v) {
    "y" -> C.Online
    "m" -> Color(0xFFFFC857)
    "n" -> C.Crimson
    else -> C.Faint
}

private fun score(e: CalEvent, slot: Long): Int = e.votes.values.sumOf { v ->
    when (v[slot]) {
        "y" -> 2
        "m" -> 1
        else -> 0
    }.toInt()
}

private fun addToPhoneCalendar(ctx: android.content.Context, e: EventInfo, start: Long) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + e.dur * 60_000L)
                .putExtra(CalendarContract.Events.TITLE, kindEmoji(e.kind) + " " + e.title)
                .putExtra(CalendarContract.Events.DESCRIPTION, e.note ?: "Scheduled in Scathe Chat")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(ctx, "No calendar app found", Toast.LENGTH_SHORT).show()
    }
}

// ---------------------------------------------------------------- screen

@Composable
fun CalendarScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val state by Repo.chat.collectAsState()
    val members by Repo.members.collectAsState()
    val me by Repo.me.collectAsState()
    val myId = me?.id ?: return
    var create by remember { mutableStateOf<String?>(null) }
    val now by produceState(Repo.now()) {
        while (true) {
            delay(20_000); value = Repo.now()
        }
    }
    val polls = state.events.filter { !it.info.cancelled && it.info.isPoll && (it.info.slots.maxOrNull() ?: 0L) > now - 6 * 3600_000L }
    val upcoming = state.events.filter {
        val s = it.info.start
        !it.info.cancelled && s != null && s + it.info.dur * 60_000L > now
    }
    val past = state.events.filter { it !in polls && it !in upcoming }
        .sortedByDescending { it.info.start ?: it.created }.take(15)

    Box(Modifier.fillMaxSize()) {
        EmberBackground(Modifier.fillMaxSize(), intensity = 0.9f)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 6.dp)
                    .height(62.dp).glass(RoundedCornerShape(24.dp), fill = Color(0x40140A06)).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Bone) }
                Column(Modifier.weight(1f)) {
                    Text("Scathe Calendar", style = fireText(19.sp, glow = 0.5f))
                    Text("Times in your zone (${ZonedDateTime.now().format(zoneFmt)})", color = C.Smoke, fontSize = 12.sp)
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.events.isEmpty()) {
                    item { CalendarEmpty(onPick = { create = it }) }
                }
                if (polls.isNotEmpty()) {
                    item { SectionLabel("PICKING A TIME") }
                    items(polls, key = { "p" + it.info.eid }) { PollCard(it, members, myId, now) }
                }
                if (upcoming.isNotEmpty()) {
                    item { SectionLabel("COMING UP") }
                    items(upcoming, key = { "u" + it.info.eid }) { ScheduledCard(it, members, myId, now) }
                }
                if (past.isNotEmpty()) {
                    item { SectionLabel("PAST") }
                    items(past, key = { "x" + it.info.eid }) { PastRow(it) }
                }
            }
        }
        if (state.events.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassButton(onClick = { create = "filth" }, shape = CircleShape, padding = PaddingValues(14.dp)) {
                    Text("💀", fontSize = 22.sp)
                }
                FireButton(onClick = { create = "cod" }, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    ButtonLabel("CoD session")
                }
            }
        }
    }
    create?.let { kind -> CreateEventSheet(kind, members, myId) { create = null } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = C.Flame.copy(alpha = 0.85f), fontFamily = Display, fontSize = 13.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 6.dp, top = 8.dp))
}

@Composable
private fun CalendarEmpty(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📅", fontSize = 54.sp)
        Text("Nothing on the calendar", style = fireText(24.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            "Offer a few times and let everyone vote, or just set one. Each brother sees it in his own time zone.",
            color = C.Dim, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        FireButton(onClick = { onPick("cod") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { ButtonLabel("🎮  Pick a time for CoD") }
        Spacer(Modifier.height(10.dp))
        GlassButton(onClick = { onPick("filth") }, modifier = Modifier.fillMaxWidth().height(56.dp)) { ButtonLabel("💀  Schedule a filthification") }
    }
}

@Composable
private fun CardShell(e: CalEvent, members: Map<String, Member>, highlight: Boolean = false, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = Color(0x38140A06), rim = if (highlight) 1.6f else 1f)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).glass(RoundedCornerShape(14.dp), fill = Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                Text(kindEmoji(e.info.kind), fontSize = 22.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(e.info.title, color = C.Bone, fontFamily = Display, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    kindName(e.info.kind) + " · by " + (members[e.creator]?.name ?: "someone") + " · " + (if (e.info.dur % 60 == 0) "${e.info.dur / 60}h" else "${e.info.dur}m"),
                    color = C.Dim, fontSize = 12.sp,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = C.Dim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    e.info.start?.let { s ->
                        DropdownMenuItem(text = { Text("Add to my phone's calendar") }, onClick = { menu = false; addToPhoneCalendar(ctx, e.info, s) })
                    }
                    if (e.info.final != null && e.info.slots.size > 1) {
                        DropdownMenuItem(text = { Text("Reopen the time vote") }, onClick = {
                            menu = false
                            Repo.saveEvent(e.info.copy(final = null, act = "edit"))
                        })
                    }
                    DropdownMenuItem(text = { Text("Cancel event", color = C.Crimson) }, onClick = { menu = false; confirmCancel = true })
                }
            }
        }
        e.info.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = C.Text.copy(alpha = 0.85f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel ${e.info.title}?") },
            text = { Text("Everyone will see it as cancelled.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; Repo.saveEvent(e.info.copy(cancelled = true, act = "cancel")) }) { Text("Cancel event", color = C.Crimson) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it", color = C.Dim) } },
        )
    }
}

@Composable
private fun VoteButtons(current: String?, onVote: (String) -> Unit, labels: Triple<String, String, String> = Triple("✓", "?", "✗")) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("y" to labels.first, "m" to labels.second, "n" to labels.third).forEach { (v, label) ->
            val on = current == v
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (on) Modifier.background(voteColor(v)) else Modifier.glass(RoundedCornerShape(10.dp), fill = Color(0x1FFFFFFF), rim = 0.5f))
                    .clickable { onVote(if (on) "" else v) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) Color.Black else C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun VoterDots(e: CalEvent, slot: Long, members: Map<String, Member>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        members.values.sortedBy { it.name.lowercase() }.forEach { m ->
            val v = e.votes[m.id]?.get(slot)
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(C.member(m.color).copy(alpha = if (v == null) 0.35f else 1f))
                    .border(2.dp, voteColor(v), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(m.name.take(1).uppercase(), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PollCard(e: CalEvent, members: Map<String, Member>, myId: String, now: Long) {
    val mine = e.votes[myId] ?: emptyMap()
    val best = e.info.slots.maxByOrNull { score(e, it) }?.takeIf { score(e, it) > 0 }
    var lockSlot by remember { mutableStateOf<Long?>(null) }
    val waiting = members.values.filter { m -> e.info.slots.none { e.votes[m.id]?.get(it) != null } }
    CardShell(e, members) {
        Text(
            if (waiting.isEmpty()) "Everyone has voted. Lock in the best time." else "Waiting on: " + waiting.joinToString(", ") { if (it.id == myId) "you" else it.name },
            color = if (waiting.isEmpty()) C.Online else C.Dim, fontSize = 13.sp,
        )
        e.info.slots.forEach { slot ->
            val isBest = slot == best
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isBest) C.Ember.copy(alpha = 0.16f) else Color(0x40000000))
                    .border(1.dp, if (isBest) C.Ember else Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(localDateTime(slot), color = if (slot < now) C.Dim else C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (isBest) Pill("🔥 best")
                }
                othersTimes(slot, members, myId)?.let { Text(it, color = C.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VoterDots(e, slot, members)
                    Spacer(Modifier.weight(1f))
                    VoteButtons(mine[slot], onVote = { Repo.vote(e.info.eid, slot, it) })
                }
                TextButton(onClick = { lockSlot = slot }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("Lock in this time", color = C.Crimson, fontSize = 13.sp)
                }
            }
        }
    }
    lockSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { lockSlot = null },
            title = { Text("Lock it in?") },
            text = { Text("${e.info.title} on ${localDateTime(slot)}. Everyone gets reminded 15 minutes before.") },
            confirmButton = { TextButton(onClick = { lockSlot = null; Repo.saveEvent(e.info.copy(final = slot, act = "lock")) }) { Text("Lock it in") } },
            dismissButton = { TextButton(onClick = { lockSlot = null }) { Text("Not yet", color = C.Dim) } },
        )
    }
}

@Composable
private fun ScheduledCard(e: CalEvent, members: Map<String, Member>, myId: String, now: Long) {
    val start = e.info.start ?: return
    val rel = relative(start, e.info.dur, now)
    val live = rel == "LIVE NOW"
    CardShell(e, members, highlight = live) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(localDateTime(start), style = fireText(19.sp, glow = 0.4f), modifier = Modifier.weight(1f))
            GlassPill(rel, hot = live)
        }
        othersTimes(start, members, myId)?.let { Text(it, color = C.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("You:", color = C.Dim, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            VoteButtons(e.votes[myId]?.get(start), onVote = { Repo.vote(e.info.eid, start, it) }, labels = Triple("I'm in", "Maybe", "Out"))
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            members.values.sortedBy { it.name.lowercase() }.forEach { m ->
                val v = e.votes[m.id]?.get(start)
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x40000000)).border(1.dp, voteColor(v), RoundedCornerShape(12.dp))
                        .padding(start = 3.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberDot(m, 20)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        (if (m.id == myId) "You" else m.name) + " " + when (v) { "y" -> "in"; "m" -> "maybe"; "n" -> "out"; else -> "?" },
                        color = C.Text, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PastRow(e: CalEvent) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp), fill = Color(0x26000000), rim = 0.5f).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(kindEmoji(e.info.kind), fontSize = 18.sp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(e.info.title, color = C.Dim, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    e.info.cancelled -> "Cancelled"
                    e.info.start != null -> localDateTime(e.info.start!!)
                    else -> "Vote expired"
                },
                color = C.Faint, fontSize = 12.sp,
            )
        }
    }
}

// ---------------------------------------------------------------- create

private fun at(date: LocalDate, h: Int, m: Int): Long =
    LocalDateTime.of(date, LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun quickTimes(): List<Pair<String, Long>> {
    val today = LocalDate.now()
    val out = ArrayList<Pair<String, Long>>()
    val nowMs = System.currentTimeMillis()
    if (at(today, 21, 0) > nowMs + 15 * 60_000) out.add("Tonight 9 PM" to at(today, 21, 0))
    out.add("Tomorrow 9 PM" to at(today.plusDays(1), 21, 0))
    val fri = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
    val sat = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    val sun = today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
    out.add("Fri 9 PM" to at(fri, 21, 0))
    out.add("Sat 2 PM" to at(sat, 14, 0))
    out.add("Sat 9 PM" to at(sat, 21, 0))
    out.add("Sun 8 PM" to at(sun, 20, 0))
    return out.distinctBy { it.second }
}

@Composable
private fun CreateEventSheet(initialKind: String, members: Map<String, Member>, myId: String, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(initialKind) }
    var title by remember { mutableStateOf(kindName(initialKind)) }
    var titleEdited by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var dur by remember { mutableIntStateOf(120) }
    val slots = remember { mutableStateListOf<Long>() }
    var pickDate by remember { mutableStateOf(false) }
    var pickTimeFor by remember { mutableStateOf<LocalDate?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 20.dp)
        ) {
            Text("New event", style = fireText(22.sp, glow = 0.5f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("cod", "filth", "other").forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = {
                            kind = k
                            if (!titleEdited) title = kindName(k)
                        },
                        label = { Text(kindEmoji(k) + " " + kindName(k)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mine, selectedLabelColor = Color.White),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                title, { title = it; titleEdited = true }, label = { Text("Name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                note, { note = it }, label = { Text("Details (optional)") }, placeholder = { Text("Mode, map, who's hosting…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth(), maxLines = 3,
            )
            Spacer(Modifier.height(14.dp))
            Text("How long", color = C.Dim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 120, 180, 240).forEach { d ->
                    FilterChip(
                        selected = dur == d, onClick = { dur = d }, label = { Text("${d / 60}h") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mine, selectedLabelColor = Color.White),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Times (in your zone, ${ZonedDateTime.now().format(zoneFmt)}). Pick one to schedule it, or a few and let everyone vote.",
                color = C.Dim, fontSize = 13.sp,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickTimes().forEach { (label, t) ->
                    FilterChip(
                        selected = t in slots,
                        onClick = { if (t in slots) slots.remove(t) else slots.add(t) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Mine, selectedLabelColor = Color.White),
                    )
                }
                FilterChip(selected = false, onClick = { pickDate = true }, label = { Text("+ Pick date & time") })
            }
            slots.sorted().forEach { s ->
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Field).padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(localDateTime(s), color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        othersTimes(s, members, myId)?.let { Text(it, color = C.Dim, fontSize = 12.sp) }
                    }
                    IconButton(onClick = { slots.remove(s) }) { Icon(Icons.Filled.Close, "Remove", tint = C.Dim) }
                }
            }
            Spacer(Modifier.height(18.dp))
            FireButton(
                onClick = {
                    Repo.saveEvent(
                        EventInfo(
                            eid = Crypto.hex(Crypto.random(8)), kind = kind, title = title.trim().ifBlank { kindName(kind) },
                            note = note.trim().ifBlank { null }, slots = slots.sorted(), dur = dur, final = null,
                            cancelled = false, tz = null, act = "new",
                        )
                    )
                    onDismiss()
                },
                enabled = slots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                ButtonLabel(
                    when (slots.size) {
                        0 -> "Pick at least one time"
                        1 -> "Schedule it"
                        else -> "Send time vote (${slots.size} options)"
                    },
                )
            }
        }
    }

    if (pickDate) {
        val today = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ds = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today - 24 * 3600_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = ds.selectedDateMillis
                    pickDate = false
                    if (ms != null) pickTimeFor = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = C.Dim) } },
        ) { DatePicker(state = ds) }
    }
    pickTimeFor?.let { date ->
        val ts = rememberTimePickerState(initialHour = 21, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTimeFor = null },
            title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))) },
            text = { Box(Modifier.widthIn(max = 360.dp)) { TimePicker(state = ts) } },
            confirmButton = {
                TextButton(onClick = {
                    val t = at(date, ts.hour, ts.minute)
                    if (t !in slots) slots.add(t)
                    pickTimeFor = null
                }) { Text("Add time") }
            },
            dismissButton = { TextButton(onClick = { pickTimeFor = null }) { Text("Cancel", color = C.Dim) } },
        )
    }
}

// ---------------------------------------------------------------- in-chat card

@Composable
fun EventChatCard(m: Msg, members: Map<String, Member>, myId: String, onOpen: () -> Unit) {
    val e = m.event ?: return
    val who = if (m.from == myId) "You" else members[m.from]?.name ?: "Someone"
    val line = when (e.act) {
        "lock" -> "$who locked in ${e.title}"
        "cancel" -> "$who cancelled ${e.title}"
        "edit" -> "$who reopened the vote for ${e.title}"
        else -> if (e.isPoll) "$who wants to set a time for ${e.title}" else "$who scheduled ${e.title}"
    }
    val sub = when {
        e.act == "cancel" -> null
        e.start != null -> localDateTime(e.start!!)
        else -> "${e.slots.size} options · tap to vote"
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 320.dp)
                .glass(RoundedCornerShape(18.dp), fill = Color(0x33140A06))
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(kindEmoji(e.kind), fontSize = 26.sp)
            Column(Modifier.padding(start = 12.dp)) {
                Text(line, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                sub?.let { Text(it, color = C.Dim, fontSize = 12.sp) }
                Text("Open calendar ›", color = C.Crimson, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
