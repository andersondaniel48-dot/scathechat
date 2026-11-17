@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.daniel.scathechat.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.daniel.scathechat.data.CodFeedItem
import dev.daniel.scathechat.data.CodMatch
import dev.daniel.scathechat.data.CodMode
import dev.daniel.scathechat.data.CodSnap
import dev.daniel.scathechat.data.Member
import dev.daniel.scathechat.data.PLATFORMS
import dev.daniel.scathechat.data.Repo
import dev.daniel.scathechat.data.platformName
import dev.daniel.scathechat.data.prettyCode
import dev.daniel.scathechat.data.prettyKey
import dev.daniel.scathechat.net.CodApi
import dev.daniel.scathechat.net.CodSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------- helpers

private fun ago(ts: Long, now: Long): String {
    val m = ((now - ts) / 60_000).coerceAtLeast(0)
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / (24 * 60)}d ago"
    }
}

private fun fmt(v: Double?, decimals: Int = 0): String {
    v ?: return "–"
    return if (decimals == 0) String.format(Locale.US, "%,d", v.toLong()) else String.format(Locale.US, "%.${decimals}f", v)
}

private fun modeShort(mode: String) = when (mode) {
    "wz" -> "WZ"
    "dmz" -> "DMZ"
    else -> "MP"
}

private fun kd(s: Map<String, Double>): Double? =
    s["kdRatio"] ?: s["kills"]?.let { k -> s["deaths"]?.let { d -> if (d == 0.0) k else k / d } }

private fun winRate(s: Map<String, Double>): Double? {
    val w = s["wins"] ?: return null
    val g = s["gamesPlayed"] ?: s["matchesPlayed"] ?: return null
    return if (g > 0) w / g * 100 else null
}

private fun hours(s: Map<String, Double>): Double? = (s["timePlayedTotal"] ?: s["timePlayed"])?.let { it / 3600.0 }

// ---------------------------------------------------------------- page

@Composable
fun StatsPage(topPad: Dp) {
    val cod by Repo.cod.collectAsState()
    val members by Repo.members.collectAsState()
    val me by Repo.me.collectAsState()
    val signedIn by CodSync.signedIn.collectAsState()
    val busy by CodSync.busy.collectAsState()
    val report by CodSync.report.collectAsState()
    var mode by remember { mutableStateOf<String?>(null) }
    var squad by remember { mutableStateOf(false) }
    var memberSheet by remember { mutableStateOf<Member?>(null) }
    var setup by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val myTag = me?.id?.let { members[it] }?.tag
    val feed = cod.feed.filter { mode == null || it.mode == mode }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad + 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (!signedIn && cod.feed.isEmpty()) {
                SetupCard(hasTag = myTag != null, onSetup = { setup = true })
            } else {
                LiveStrip(busy, cod.lastSync, now, signedIn, report.firstOrNull { "not permitted" in it || "expired" in it || "error" in it.lowercase() }, onSync = { CodSync.syncNow() }, onSetup = { setup = true })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Segment("Feed", !squad) { squad = false }
                Segment("Squad", squad) { squad = true }
                Spacer(Modifier.weight(1f))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModeChip("All", mode == null) { mode = null }
                CodMode.entries.forEach { m -> ModeChip(m.label, mode == m.key) { mode = m.key } }
            }
        }
        if (!squad) {
            if (feed.isEmpty()) {
                item {
                    Text(
                        if (signedIn) "No matches yet. Play a game, then pull to refresh with the sync button. New matches show up here within a few minutes."
                        else "Match summaries show up here once someone in the group signs in to Activision.",
                        color = C.Smoke, fontSize = 14.sp, modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(feed.take(150), key = { it.matchId }) { item -> MatchCard(item, members, now) }
        } else {
            val modes = if (mode == null) CodMode.entries else CodMode.entries.filter { it.key == mode }
            modes.forEach { m ->
                item(key = "sq-" + m.key) { SquadBoard(m, members, cod.snaps, cod.feed) { memberSheet = it } }
            }
        }
    }
    memberSheet?.let { m -> MemberStatsSheet(m, cod.snaps[m.id] ?: emptyMap(), cod.feed, now) { memberSheet = null } }
    if (setup) CodSetupSheet { setup = false }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        label, color = if (on) Color.White else C.Smoke, fontFamily = Display, fontSize = 14.sp,
        modifier = Modifier
            .then(if (on) Modifier.molten(shape) else Modifier.glass(shape, fill = Color(0x30140A06), rim = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = on, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0x22000000), selectedContainerColor = C.Blood, selectedLabelColor = Color.White, labelColor = C.Bone),
    )
}

@Composable
private fun SetupCard(hasTag: Boolean, onSetup: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), fill = Color(0x38140A06)).padding(18.dp)) {
        Text("Bring in your CoD stats", style = fireText(24.sp, glow = 0.6f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Each of you adds your gamertag. One of you signs in to Activision, and that phone pulls everyone's matches and career stats " +
                "for Warzone, Multiplayer and DMZ into this feed. Your Activision login stays on your phone.",
            color = C.Bone.copy(alpha = 0.85f), fontSize = 14.sp,
        )
        Spacer(Modifier.height(14.dp))
        FireButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { ButtonLabel(if (hasTag) "Sign in with Activision" else "Set up my gamertag") }
    }
}

@Composable
private fun LiveStrip(busy: Boolean, last: Long, now: Long, signedIn: Boolean, problem: String?, onSync: () -> Unit, onSetup: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "r")
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), fill = Color(0x30140A06)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (signedIn) C.Online else C.Faint))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (signedIn) "Live stats" else "Not signed in on this phone", color = C.Bone, fontFamily = Display, fontSize = 15.sp)
                Text(
                    when {
                        busy -> "Pulling from Activision…"
                        last > 0 -> "Updated ${ago(last, now)} · checks every few minutes"
                        signedIn -> "Waiting for the first sync"
                        else -> "Stats posted by your brothers still show here"
                    },
                    color = C.Smoke, fontSize = 12.sp,
                )
            }
            if (signedIn) {
                IconButton(onClick = onSync, enabled = !busy) {
                    Icon(Icons.Filled.Refresh, "Sync now", tint = C.Flame, modifier = if (busy) Modifier.rotate(spin.value) else Modifier)
                }
            } else {
                TextButton(onClick = onSetup) { Text("Set up", color = C.Flame) }
            }
        }
        if (problem != null) Text(problem, color = Color(0xFFFFB4A0), fontSize = 12.sp, modifier = Modifier.padding(start = 17.dp, bottom = 4.dp, end = 8.dp))
    }
}

// ---------------------------------------------------------------- feed card

@Composable
private fun MatchCard(item: CodFeedItem, members: Map<String, Member>, now: Long) {
    val lead = item.lead
    val squadGame = item.players.size > 1
    val won = item.players.any { it.result == "win" || it.place == 1 }
    Column(
        Modifier.fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = if (won) C.Ember.copy(alpha = 0.10f) else Color(0x33140A06), rim = if (won) 1.5f else 1f)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassPill(modeShort(item.mode), hot = item.mode == "wz")
            Spacer(Modifier.width(8.dp))
            Text(prettyCode(lead.map) ?: prettyCode(lead.gameType) ?: "Match", color = C.Bone, fontFamily = Display, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(ago(item.start, now), color = C.Smoke, fontSize = 12.sp)
        }
        val sub = listOfNotNull(prettyCode(lead.gameType)?.takeIf { lead.map != null }, if (squadGame) "Squad game" else null).joinToString("  ·  ")
        if (sub.isNotBlank()) Text(sub, color = C.Smoke, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val place = item.players.mapNotNull { it.place }.minOrNull()
            when {
                place != null -> {
                    Text("#$place", style = fireText(40.sp))
                    lead.teams?.let { Text("  of $it", color = C.Smoke, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                }
                lead.result != null -> Text(
                    when (lead.result) { "win" -> "Victory"; "loss" -> "Defeat"; "draw" -> "Draw"; else -> lead.result.replaceFirstChar { it.titlecase() } },
                    style = if (lead.result == "win") fireText(32.sp) else TextStyle(fontFamily = Display, fontSize = 32.sp, color = C.Smoke),
                )
                else -> {}
            }
            Spacer(Modifier.weight(1f))
            val totalK = item.players.sumOf { it.kills }
            Column(horizontalAlignment = Alignment.End) {
                Text("$totalK", color = C.Bone, fontFamily = Display, fontSize = 24.sp)
                Text(if (squadGame) "squad kills" else "kills", color = C.Smoke, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        item.players.forEach { p -> PlayerLine(p, members[p.member]) }
    }
}

@Composable
private fun PlayerLine(p: CodMatch, m: Member?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (m != null) MemberDot(m, 26) else Box(Modifier.size(26.dp).clip(CircleShape).background(C.Faint))
        Spacer(Modifier.width(8.dp))
        Text(m?.name ?: "?", color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        Stat("${p.kills}/${p.deaths}" + (p.assists?.let { "/$it" } ?: ""), "K/D/A")
        p.damage?.let { Stat(fmt(it.toDouble()), "dmg") }
        Stat(fmt(p.kd, 2), "K/D")
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
        Text(value, color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = C.Faint, fontSize = 10.sp)
    }
}

// ---------------------------------------------------------------- squad board

private class Board(val label: String, val decimals: Int, val value: (Map<String, Double>) -> Double?)

private val boards = listOf(
    Board("K/D", 2) { kd(it) },
    Board("Kills", 0) { it["kills"] },
    Board("Wins", 0) { it["wins"] },
    Board("Win rate %", 1) { winRate(it) },
    Board("Score per minute", 0) { it["scorePerMinute"] },
    Board("Hours played", 0) { hours(it) },
)

@Composable
private fun SquadBoard(mode: CodMode, members: Map<String, Member>, snaps: Map<String, Map<String, CodSnap>>, feed: List<CodFeedItem>, onMember: (Member) -> Unit) {
    val rows = members.values.mapNotNull { m -> snaps[m.id]?.get(mode.key)?.let { m to it } }
    val recent = feed.filter { it.mode == mode.key }.take(60).flatMap { it.players }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), fill = Color(0x38140A06)).padding(16.dp)) {
        Text(mode.label, style = fireText(24.sp, glow = 0.5f))
        if (rows.isEmpty() && recent.isEmpty()) {
            Text("No ${mode.label} stats yet.", color = C.Smoke, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            return@Column
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Recent games", color = C.Flame, fontFamily = Display, fontSize = 13.sp)
            val byMember = recent.groupBy { it.member }
            byMember.entries.sortedByDescending { e -> e.value.map { it.kills }.average() }.forEach { (id, games) ->
                val m = members[id] ?: return@forEach
                Row(Modifier.fillMaxWidth().clickable { onMember(m) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MemberDot(m, 26)
                    Spacer(Modifier.width(8.dp))
                    Text(m.name, color = C.Bone, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Stat("${games.size}", "games")
                    Stat(fmt(games.map { it.kills }.average(), 1), "avg kills")
                    games.mapNotNull { it.damage }.takeIf { it.isNotEmpty() }?.let { Stat(fmt(it.average()), "avg dmg") }
                    Stat("${games.maxOf { it.kills }}", "best")
                }
            }
        }
        if (rows.isNotEmpty()) {
            boards.forEach { b ->
                val vals = rows.mapNotNull { (m, s) -> b.value(s.lifetime)?.let { m to it } }.sortedByDescending { it.second }
                if (vals.isEmpty()) return@forEach
                val max = vals.first().second.takeIf { it > 0 } ?: 1.0
                Spacer(Modifier.height(12.dp))
                Text(b.label, color = C.Flame, fontFamily = Display, fontSize = 13.sp)
                vals.forEachIndexed { i, (m, v) ->
                    Row(Modifier.fillMaxWidth().clickable { onMember(m) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, color = C.Bone, fontSize = 13.sp, modifier = Modifier.width(78.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth((v / max).toFloat().coerceIn(0.03f, 1f))
                                    .then(if (i == 0) Modifier.molten(RoundedCornerShape(8.dp)) else Modifier.background(C.member(m.color).copy(alpha = 0.7f), RoundedCornerShape(8.dp)))
                            )
                        }
                        Text(fmt(v, b.decimals), color = if (i == 0) C.Flame else C.Bone, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp).padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- member page

@Composable
private fun MemberStatsSheet(m: Member, snaps: Map<String, CodSnap>, feed: List<CodFeedItem>, now: Long, onDismiss: () -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberDot(m, 48)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(m.name, style = fireText(26.sp, glow = 0.5f))
                    Text(listOfNotNull(m.tag, platformName(m.platform).ifBlank { null }).joinToString(" · "), color = C.Smoke, fontSize = 13.sp)
                }
            }
            CodMode.entries.forEach { mode ->
                val s = snaps[mode.key] ?: return@forEach
                Spacer(Modifier.height(16.dp))
                Text(mode.label, color = C.Flame, fontFamily = Display, fontSize = 16.sp)
                Text("Updated ${ago(s.at, now)}", color = C.Faint, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("K/D", fmt(kd(s.lifetime), 2))
                    BigStat("Kills", fmt(s.lifetime["kills"]))
                    s.lifetime["wins"]?.let { BigStat("Wins", fmt(it)) }
                    winRate(s.lifetime)?.let { BigStat("Win rate", fmt(it, 1) + "%") }
                    (s.lifetime["gamesPlayed"] ?: s.lifetime["matchesPlayed"])?.let { BigStat("Matches", fmt(it)) }
                    hours(s.lifetime)?.let { BigStat("Hours", fmt(it)) }
                    s.lifetime["topFive"]?.let { BigStat("Top 5", fmt(it)) }
                    s.lifetime["level"]?.let { BigStat("Level", fmt(it)) }
                }
                if (s.weekly.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This week: " + listOfNotNull(
                            s.weekly["kills"]?.let { "${fmt(it)} kills" },
                            kd(s.weekly)?.let { "${fmt(it, 2)} K/D" },
                            s.weekly["wins"]?.let { "${fmt(it)} wins" },
                            (s.weekly["matchesPlayed"] ?: s.weekly["gamesPlayed"])?.let { "${fmt(it)} games" },
                        ).joinToString(" · "),
                        color = C.Bone, fontSize = 13.sp,
                    )
                }
                if (showAll) {
                    Spacer(Modifier.height(6.dp))
                    s.lifetime.entries.sortedBy { it.key }.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(prettyKey(k), color = C.Smoke, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(fmt(v, if (v % 1.0 == 0.0) 0 else 2), color = C.Bone, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (snaps.isNotEmpty()) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide full stat list" else "Show every stat", color = C.Flame) }
            val mine = feed.mapNotNull { f -> f.players.firstOrNull { it.member == m.id } }.take(15)
            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Last matches", color = C.Flame, fontFamily = Display, fontSize = 16.sp)
                mine.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassPill(modeShort(p.mode))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prettyCode(p.map) ?: prettyCode(p.gameType) ?: "Match", color = C.Bone, fontSize = 13.sp, maxLines = 1)
                            Text(ago(p.start, now) + (p.place?.let { " · #$it" } ?: p.result?.let { " · $it" } ?: ""), color = C.Smoke, fontSize = 11.sp)
                        }
                        Stat("${p.kills}/${p.deaths}", "K/D")
                        p.damage?.let { Stat(fmt(it.toDouble()), "dmg") }
                    }
                }
            }
            if (snaps.isEmpty() && mine.isEmpty()) Text("No stats yet for ${m.name}.", color = C.Smoke, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun BigStat(label: String, value: String) {
    Column(Modifier.glass(RoundedCornerShape(14.dp), fill = Color(0x22FFFFFF), rim = 0.6f).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = C.Bone, fontFamily = Display, fontSize = 18.sp)
        Text(label, color = C.Smoke, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------- setup: tag + Activision sign-in

@Composable
fun CodSetupSheet(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val signedIn by CodSync.signedIn.collectAsState()
    val report by CodSync.report.collectAsState()
    var tag by remember { mutableStateOf(Repo.prefs.codTag ?: "") }
    var platform by remember { mutableStateOf(Repo.prefs.codPlatform ?: "battle") }
    var title by remember { mutableStateOf(Repo.prefs.codTitle ?: "") }
    var login by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>?>(null) }
    var saved by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.Bar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Call of Duty", style = fireText(24.sp, glow = 0.5f))
            Text("Your gamertag is shared with the group so any signed-in phone can pull your stats.", color = C.Smoke, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Platform", color = C.Smoke, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PLATFORMS.forEach { (code, name) ->
                    FilterChip(
                        selected = platform == code, onClick = { platform = code; saved = false }, label = { Text(name) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.Blood, selectedLabelColor = Color.White),
                    )
                }
            }
            OutlinedTextField(
                tag, { tag = it; saved = false }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Gamertag") },
                placeholder = { Text(if (platform == "battle" || platform == "uno") "Name#1234567" else "Your gamertag") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                title, { title = it.trim().lowercase(Locale.US); saved = false }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text("Game code (optional)") },
                placeholder = { Text("Filled in after you sign in") },
                supportingText = { Text("Activision's code for the current game. Leave blank to use what your account reports.") },
            )
            Spacer(Modifier.height(8.dp))
            FireButton(onClick = {
                Repo.saveCodSettings(tag, platform, title)
                saved = true
                CodSync.syncNow()
            }, enabled = tag.isNotBlank(), modifier = Modifier.fillMaxWidth()) { ButtonLabel(if (saved) "Saved" else "Save gamertag") }

            Spacer(Modifier.height(18.dp)); HorizontalDivider(color = Color(0x22FFFFFF)); Spacer(Modifier.height(14.dp))
            Text("Activision sign-in", color = C.Bone, fontFamily = Display, fontSize = 16.sp)
            Text(
                "Only one of you needs this. The login stays on this phone and is used to read stats from callofduty.com. " +
                    "Activision has no official stats API, so this can stop working if they change their site, and it's against their terms of use.",
                color = C.Smoke, fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            if (signedIn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(C.Online))
                    Spacer(Modifier.width(8.dp))
                    Text("Signed in on this phone", color = C.Bone, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { CodSync.signOut() }) { Text("Sign out", color = C.Smoke) }
                }
                GlassButton(onClick = {
                    testing = true
                    scope.launch {
                        val t = tag.ifBlank { Repo.prefs.codTag ?: "" }
                        results = if (t.isBlank()) listOf("Add a gamertag first.")
                        else withContext(Dispatchers.IO) { CodSync.diagnose(t, platform, title.ifBlank { Repo.prefs.codTitle ?: CodApi.DEFAULT_TITLE }) }
                        testing = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = C.Flame) else ButtonLabel("Test connection")
                }
            } else {
                FireButton(onClick = { login = true }, modifier = Modifier.fillMaxWidth()) { ButtonLabel("Sign in with Activision") }
                Text("Use your Activision email and password. Sign-in through Google, PlayStation or Xbox may not work inside the app.", color = C.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (report.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Last sync", color = C.Flame, fontFamily = Display, fontSize = 13.sp)
                report.forEach { Text(it, color = C.Smoke, fontSize = 12.sp) }
            }
        }
    }
    if (login) ActivisionLogin(onDone = { ok ->
        login = false
        if (ok) scope.launch {
            val guess = withContext(Dispatchers.IO) { CodApi.guessIdentity() }
            if (guess != null) {
                if (title.isBlank()) title = guess.first
                if (tag.isBlank() && guess.third.isNotBlank()) { tag = guess.third; platform = guess.second.ifBlank { platform } }
                Repo.saveCodSettings(tag, platform, title)
            }
            CodSync.syncNow()
        }
    })
    results?.let { lines ->
        AlertDialog(
            onDismissRequest = { results = null },
            title = { Text("Connection test") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { lines.forEach { Text(it, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) } } },
            confirmButton = { TextButton(onClick = { results = null }) { Text("Done") } },
            containerColor = Color(0xFF1C120E),
        )
    }
}

/** Activision's own login page in a WebView. When the session cookie appears, it is stored (encrypted) on this phone. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ActivisionLogin(onDone: (Boolean) -> Unit) {
    Dialog(onDismissRequest = { onDone(false) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(C.Char)) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDone(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = C.Bone) }
                Text("Sign in to Activision", color = C.Bone, fontFamily = Display, fontSize = 17.sp)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    WebView(ctx).apply {
                        cm.setAcceptThirdPartyCookies(this, true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        var finished = false
                        fun check() {
                            if (finished) return
                            val cookies = listOf("https://my.callofduty.com", "https://www.callofduty.com", "https://profile.callofduty.com")
                                .mapNotNull { cm.getCookie(it) }.joinToString("; ")
                            if ("ACT_SSO_COOKIE" in cookies) {
                                val merged = cookies.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.substringBefore('=') }.joinToString("; ")
                                finished = true
                                cm.flush()
                                Repo.prefs.codCookie = merged
                                Repo.prefs.codUserAgent = settings.userAgentString
                                CodSync.signedIn.value = true
                                onDone(true)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = check()
                            override fun onPageFinished(view: WebView?, url: String?) = check()
                        }
                        loadUrl("https://profile.callofduty.com/cod/login")
                    }
                },
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
