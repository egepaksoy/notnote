package com.notnote.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Bg = Color(0xFF161917)
    val Raised = Color(0xFF1F2420)
    val InputBg = Color(0xFF1E231F)
    val InputBorder = Color(0xFF2B312C)
    val SendBg = Color(0xFF2C322D)
    val Text = Color(0xFFEDEAE2)
    val Muted = Color(0xFF8C918C)
    val Divider = Color(0xFF262B27)
    val Ok = Color(0xFF7FB38A)
    val Fail = Color(0xFFD9826F)
    val Wait = Color(0xFFD8B56A)
    val Accent = Color(0xFF9CC5A6)
}

private val scheme = darkColorScheme(
    primary = C.Accent,
    onPrimary = C.Bg,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Raised,
    onSurface = C.Text,
    surfaceVariant = C.InputBg,
    onSurfaceVariant = C.Muted,
    surfaceContainer = C.Raised,
    surfaceContainerHigh = C.Raised,
    surfaceContainerHighest = C.SendBg,
    surfaceContainerLow = C.Bg,
    outline = C.InputBorder,
    outlineVariant = C.Divider,
    error = C.Fail,
    inverseSurface = C.Text,
    inverseOnSurface = C.Bg,
    inversePrimary = C.Bg,
)

@Composable
fun NotnoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
