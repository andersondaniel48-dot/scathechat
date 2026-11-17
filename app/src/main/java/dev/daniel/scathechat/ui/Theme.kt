package dev.daniel.scathechat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.daniel.scathechat.R
import dev.daniel.scathechat.data.Repo

/**
 * Scathe palette. One light source: the fire at the bottom of the screen.
 * Glass picks up white specular light on its top edge and ember light on its bottom edge.
 */
object C {
    val Char = Color(0xFF0E0806) // burnt base
    val Soot = Color(0xFF1A0E0A) // lifted base, top of the gradient
    val Ember = Color(0xFFFF5A1F) // primary fire
    val Flame = Color(0xFFFFB23F) // hot highlight
    val Blood = Color(0xFFA8101C) // deep red, bottom of molten glass
    val Bone = Color(0xFFF5EADF) // text
    val Smoke = Color(0xFFAE9D90) // secondary text

    // Roles used across the app
    val Bg = Char
    val Bar = Color(0xF2160D0A) // sheet glass
    val Field = Color(0x1AFFFFFF) // glass input / chip fill
    val Other = Color(0x17FFFFFF) // others' bubbles
    val Mine = Ember
    val Crimson = Ember // primary accent
    val Text = Bone
    val Dim = Smoke
    val Faint = Color(0xFF6E6159)
    val Online = Color(0xFF7CFF6B)

    // Brother colors: warm and cold so they read apart against fire.
    val members = listOf(
        Color(0xFFFF6A3D), Color(0xFFFFB23F), Color(0xFF6EC8FF), Color(0xFF8CFF6A),
        Color(0xFFB48CFF), Color(0xFF3DE0C8), Color(0xFFFF7AA8), Color(0xFFF2E6D8),
    )

    fun member(i: Int): Color = members[((i % members.size) + members.size) % members.size]
}

/** Stencil display face: titles, section heads, big times. */
val Display = FontFamily(Font(R.font.black_ops_one))

/** Tactical sans for everything else, message text included. */
val Body = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

private fun typography(): Typography {
    val t = Typography()
    fun TextStyle.b() = copy(fontFamily = Body)
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Display),
        displayMedium = t.displayMedium.copy(fontFamily = Display),
        displaySmall = t.displaySmall.copy(fontFamily = Display),
        headlineLarge = t.headlineLarge.copy(fontFamily = Display),
        headlineMedium = t.headlineMedium.copy(fontFamily = Display),
        headlineSmall = t.headlineSmall.copy(fontFamily = Display),
        titleLarge = t.titleLarge.b(),
        titleMedium = t.titleMedium.b(),
        titleSmall = t.titleSmall.b(),
        bodyLarge = t.bodyLarge.b(),
        bodyMedium = t.bodyMedium.b(),
        bodySmall = t.bodySmall.b(),
        labelLarge = t.labelLarge.b().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.b(),
        labelSmall = t.labelSmall.b(),
    )
}

@Composable
fun ScatheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Ember,
            onPrimary = Color.White,
            primaryContainer = C.Blood,
            onPrimaryContainer = Color.White,
            secondary = C.Flame,
            background = C.Char,
            onBackground = C.Bone,
            surface = Color(0xFF1A110D),
            onSurface = C.Bone,
            surfaceVariant = Color(0xFF2A1C16),
            onSurfaceVariant = C.Smoke,
            surfaceContainer = Color(0xFF1C120E),
            surfaceContainerHigh = Color(0xFF241812),
            surfaceContainerHighest = Color(0xFF2C1E17),
            surfaceContainerLow = Color(0xFF170F0B),
            outline = Color(0x55FFFFFF),
            outlineVariant = Color(0x22FFFFFF),
            error = Color(0xFFFF6B6B),
        ),
        typography = typography(),
        content = content,
    )
}

@Composable
fun AppRoot() {
    val group by Repo.group.collectAsState()
    val me by Repo.me.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (group == null || me == null) SetupScreen() else ChatScreen()
    }
}
