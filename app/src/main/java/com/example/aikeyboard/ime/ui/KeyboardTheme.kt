package com.example.aikeyboard.ime.ui

import androidx.compose.foundation.isSystemInDarkTheme
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

/** Typosaurus brand palette (from the launch artwork). */
object Brand {
    val Lime = Color(0xFFC6FF3D)
    val Ink = Color(0xFF050505)
    val DinoGreen = Color(0xFF2F4A22)
    val Paper = Color(0xFFF6F8F2)
}

/** Inter (variable weight axis, Latin subset) — the typeface of the Typosaurus wordmark. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Inter = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val AppTypography: Typography = Typography().let { t ->
    fun TextStyle.inter(weight: FontWeight? = null, tracking: Double? = null) =
        copy(fontFamily = Inter, fontWeight = weight ?: fontWeight, letterSpacing = tracking?.em ?: letterSpacing)
    Typography(
        displayLarge = t.displayLarge.inter(FontWeight.ExtraBold, -0.03), displayMedium = t.displayMedium.inter(FontWeight.ExtraBold, -0.03),
        displaySmall = t.displaySmall.inter(FontWeight.Bold, -0.02), headlineLarge = t.headlineLarge.inter(FontWeight.Bold, -0.02),
        headlineMedium = t.headlineMedium.inter(FontWeight.Bold, -0.02), headlineSmall = t.headlineSmall.inter(FontWeight.Bold, -0.01),
        titleLarge = t.titleLarge.inter(FontWeight.Bold, -0.01), titleMedium = t.titleMedium.inter(FontWeight.SemiBold),
        titleSmall = t.titleSmall.inter(FontWeight.SemiBold), bodyLarge = t.bodyLarge.inter(), bodyMedium = t.bodyMedium.inter(),
        bodySmall = t.bodySmall.inter(), labelLarge = t.labelLarge.inter(FontWeight.SemiBold), labelMedium = t.labelMedium.inter(FontWeight.Medium),
        labelSmall = t.labelSmall.inter(FontWeight.Medium, 0.08),
    )
}

@Composable
fun AppTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        AppSettings.Theme.System -> isSystemInDarkTheme()
        AppSettings.Theme.Light -> false
        AppSettings.Theme.Dark -> true
    }
    val accent = Color(settings.accentColor)
    // Keep the accent readable: bright accents darken on paper, dark accents lighten on ink.
    val primary = when {
        dark && accent.luminance() < 0.25f -> accent.mix(Color.White, 0.35f)
        !dark && accent.luminance() > 0.45f -> accent.mix(Color.Black, 0.45f)
        else -> accent
    }
    val onPrimary = if (primary.luminance() > 0.45f) Brand.Ink else Color.White
    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = Brand.DinoGreen, onPrimaryContainer = Brand.Lime,
        secondary = Brand.DinoGreen, onSecondary = Color.White,
        background = Brand.Ink, onBackground = Color(0xFFF2F5EE),
        surface = Color(0xFF121511), onSurface = Color(0xFFF2F5EE),
        surfaceVariant = Color(0xFF1D231B), onSurfaceVariant = Color(0xFFB9C2B1),
        surfaceContainer = Color(0xFF161A14), surfaceContainerHigh = Color(0xFF1D231B), surfaceContainerHighest = Color(0xFF262D23),
        outline = Color(0xFF3A4436),
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = Brand.Lime, onPrimaryContainer = Brand.Ink,
        secondary = Brand.DinoGreen, onSecondary = Color.White,
        background = Brand.Paper, onBackground = Color(0xFF0E110C),
        surface = Color.White, onSurface = Color(0xFF0E110C),
        surfaceVariant = Color(0xFFE6EBDD), onSurfaceVariant = Color(0xFF4A5443),
        surfaceContainer = Color(0xFFEFF2EA), surfaceContainerHigh = Color(0xFFE9EDE3), surfaceContainerHighest = Color(0xFFE2E7DA),
        outline = Color(0xFFB9C2B1),
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

private fun Color.mix(other: Color, f: Float) =
    Color(red + (other.red - red) * f, green + (other.green - green) * f, blue + (other.blue - blue) * f, alpha)

/** Accent choices offered in Settings > Keyboard; the first is the brand lime. */
val AccentChoices = listOf(0xFFC6FF3D, 0xFF4CAF50, 0xFF3F51B5, 0xFF00BCD4, 0xFFC2185B, 0xFFFF9800)
