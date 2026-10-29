package com.example.aikeyboard.ime.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.example.aikeyboard.R
import com.example.aikeyboard.core.action.AppSettings

/**
 * Typosaurus palette, shared by the app and the keyboard.
 * Light, "minimalist": off-white base, white keys with a soft shadow, slate-blue action, deep-charcoal text.
 * Dark, "stealth": charcoal base, dark-slate keys, a vibrant accent (neon teal by default), off-white text.
 */
object Palette {
    val LightBase = Color(0xFFF5F7FA)
    val LightKey = Color(0xFFFFFFFF)
    val SlateBlue = Color(0xFF4A90E2)
    val Charcoal = Color(0xFF2C3E50)

    val DarkBase = Color(0xFF121212)
    val DarkSurface = Color(0xFF1E1E1E)
    val DarkKey = Color(0xFF2A2E33)
    val OffWhite = Color(0xFFE0E0E0)
}

/**
 * Inter (variable: weight + optical size, Latin subset). The wordmark on the launch artwork is the
 * Display cut (optical size 32): tighter and narrower than the text cut used for body copy and keys.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(opticalSize: Float) = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.inter, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)),
        )
    },
)

private val InterText = inter(14f)
private val InterDisplay = inter(32f)

private val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.text(weight: FontWeight? = null, tracking: Double? = null) =
        copy(fontFamily = InterText, fontWeight = weight ?: fontWeight, letterSpacing = tracking?.em ?: letterSpacing)
    // Headings use the wordmark's cut, weight and tight tracking.
    fun TextStyle.display(weight: FontWeight, tracking: Double) =
        copy(fontFamily = InterDisplay, fontWeight = weight, letterSpacing = tracking.em)
    Typography(
        displayLarge = t.displayLarge.display(FontWeight.ExtraBold, -0.035), displayMedium = t.displayMedium.display(FontWeight.ExtraBold, -0.03),
        displaySmall = t.displaySmall.display(FontWeight.Bold, -0.03), headlineLarge = t.headlineLarge.display(FontWeight.Bold, -0.03),
        headlineMedium = t.headlineMedium.display(FontWeight.Bold, -0.025), headlineSmall = t.headlineSmall.display(FontWeight.Bold, -0.02),
        titleLarge = t.titleLarge.display(FontWeight.Bold, -0.02), titleMedium = t.titleMedium.display(FontWeight.SemiBold, -0.01),
        titleSmall = t.titleSmall.display(FontWeight.SemiBold, -0.005), bodyLarge = t.bodyLarge.text(), bodyMedium = t.bodyMedium.text(),
        bodySmall = t.bodySmall.text(), labelLarge = t.labelLarge.text(FontWeight.SemiBold), labelMedium = t.labelMedium.text(FontWeight.Medium),
        labelSmall = t.labelSmall.text(FontWeight.Medium, 0.08),
    )
}

private fun AppSettings.Theme.isDark(system: Boolean) = when (this) {
    AppSettings.Theme.System -> system
    AppSettings.Theme.Light -> false
    AppSettings.Theme.Dark -> true
}

/** Light mode always uses slate blue; [darkAccent] is the user's dark-mode accent. */
private fun typosaurusScheme(dark: Boolean, darkAccent: Color, keyboard: Boolean): ColorScheme =
    if (dark) {
        val onAccent = if (darkAccent.luminance() > 0.45f) Palette.DarkBase else Color.White
        darkColorScheme(
            primary = darkAccent, onPrimary = onAccent,
            primaryContainer = darkAccent.mix(Color.Black, 0.72f), onPrimaryContainer = darkAccent,
            secondary = darkAccent.mix(Color.Black, 0.3f), onSecondary = onAccent,
            background = Palette.DarkBase, onBackground = Palette.OffWhite,
            // The keyboard draws keys in `surface`; the app draws sheets and lists in it.
            surface = if (keyboard) Palette.DarkKey else Palette.DarkSurface, onSurface = Palette.OffWhite,
            surfaceVariant = if (keyboard) Palette.DarkSurface else Palette.DarkKey, onSurfaceVariant = Color(0xFFA8AFB8),
            surfaceContainer = Palette.DarkSurface, surfaceContainerHigh = Color(0xFF24282C), surfaceContainerHighest = Palette.DarkKey,
            outline = Color(0xFF3A3F45), error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = Palette.SlateBlue, onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFB), onPrimaryContainer = Color(0xFF1C3F66),
            secondary = Palette.Charcoal, onSecondary = Color.White,
            background = Palette.LightBase, onBackground = Palette.Charcoal,
            surface = Palette.LightKey, onSurface = Palette.Charcoal,
            surfaceVariant = Color(0xFFE9EEF4), onSurfaceVariant = Color(0xFF5D6D7E),
            surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
            outline = Color(0xFFC9D2DC),
        )
    }

@Composable
fun AppTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = settings.theme.isDark(isSystemInDarkTheme())
    MaterialTheme(typosaurusScheme(dark, Color(settings.accentColor), keyboard = false), typography = AppTypography, content = content)
}

/** The keyboard follows the phone's light/dark mode unless set otherwise in Settings › Keyboard. */
@Composable
fun KeyboardTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = settings.keyboardTheme.isDark(isSystemInDarkTheme())
    MaterialTheme(typosaurusScheme(dark, Color(settings.keyboardAccent), keyboard = true), typography = AppTypography, content = content)
}

/** Dark-mode accent choices (Settings › Keyboard); the first is the default neon teal. */
val KeyboardAccentChoices = listOf(AppSettings.NEON_TEAL, 0xFFBB86FC, 0xFF4A90E2, 0xFF38BDF8, 0xFFFF8A65, 0xFFF472B6)

private fun Color.mix(other: Color, f: Float) =
    Color(red + (other.red - red) * f, green + (other.green - green) * f, blue + (other.blue - blue) * f, alpha)
