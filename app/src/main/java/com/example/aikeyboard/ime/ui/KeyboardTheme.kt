package com.example.aikeyboard.ime.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.aikeyboard.core.action.AppSettings

@Composable
fun AppTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        AppSettings.Theme.System -> isSystemInDarkTheme()
        AppSettings.Theme.Light -> false
        AppSettings.Theme.Dark -> true
    }
    val accent = Color(settings.accentColor)
    val scheme = if (dark) darkColorScheme(
        primary = accent.lighten(0.35f),
        background = Color(0xFF121316), surface = Color(0xFF1B1C20),
        surfaceVariant = Color(0xFF2A2C32), onSurface = Color(0xFFE6E6EA),
    ) else lightColorScheme(
        primary = accent,
        background = Color(0xFFF4F4F7), surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE2E3EA), onSurface = Color(0xFF1B1B1F),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)

/** Accent choices offered in Settings > Keyboard. */
val AccentChoices = listOf(0xFF3F51B5, 0xFF00796B, 0xFFC2185B, 0xFFE65100, 0xFF5E35B1, 0xFF2E7D32)
