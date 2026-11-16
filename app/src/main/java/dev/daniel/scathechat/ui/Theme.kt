package dev.daniel.scathechat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dev.daniel.scathechat.data.Repo

object C {
    val Bg = Color(0xFF0D0E10)
    val Bar = Color(0xFF15171A)
    val Field = Color(0xFF1E2125)
    val Other = Color(0xFF23262B)
    val Mine = Color(0xFFB8232B)
    val Crimson = Color(0xFFE5383B)
    val Text = Color(0xFFECEDEE)
    val Dim = Color(0xFF9AA0A6)
    val Faint = Color(0xFF5F656B)
    val Online = Color(0xFF3DDC84)

    val members = listOf(
        Color(0xFFE5383B), Color(0xFFF4A261), Color(0xFF2EC4B6), Color(0xFF4EA8DE),
        Color(0xFFB388EB), Color(0xFF80ED99), Color(0xFFFFD166), Color(0xFFFF8FAB),
    )

    fun member(i: Int): Color = members[((i % members.size) + members.size) % members.size]
}

@Composable
fun ScatheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Crimson,
            onPrimary = Color.White,
            primaryContainer = C.Mine,
            onPrimaryContainer = Color.White,
            secondary = C.Crimson,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Bar,
            onSurface = C.Text,
            surfaceVariant = C.Field,
            onSurfaceVariant = C.Dim,
            surfaceContainer = C.Bar,
            surfaceContainerHigh = C.Field,
            surfaceContainerLow = C.Bar,
            outline = C.Faint,
        ),
        content = content,
    )
}

@Composable
fun AppRoot() {
    val group by Repo.group.collectAsState()
    val me by Repo.me.collectAsState()
    if (group == null || me == null) SetupScreen() else ChatScreen()
}
