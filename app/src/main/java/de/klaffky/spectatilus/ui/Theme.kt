package de.klaffky.spectatilus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF07090E)
    val Surface = Color(0xFF12151D)
    val SurfaceHigh = Color(0xFF1B1F2A)
    val Accent = Color(0xFF8B6CFF)
    val AccentSoft = Color(0xFFB9A8FF)
    val Text = Color(0xFFF4F5F8)
    val TextDim = Color(0xFFA3A8B8)
    val TextFaint = Color(0xFF6C7183)
    val Success = Color(0xFF3DDC97)
    val Warn = Color(0xFFFFB547)
    val Error = Color(0xFFFF6B6B)
}

@Composable
fun SpectatilusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            background = C.Bg,
            surface = C.Surface,
            onBackground = C.Text,
            onSurface = C.Text,
        ),
        content = content,
    )
}
