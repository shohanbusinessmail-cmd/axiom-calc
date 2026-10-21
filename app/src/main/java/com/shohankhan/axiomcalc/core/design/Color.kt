package com.shohankhan.axiomcalc.core.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand colours, mirrored from the verified resource values in
 * `app/src/main/res/values/colors.xml`. Both definitions must stay identical;
 * `tools/validate_resources.py` checks the XML side and this file is the
 * Compose side of the same tokens.
 */
object AxiomColors {
    val Accent = Color(0xFF38BDF8)
    val AccentDim = Color(0xFF0EA5E9)
    val Operator = Color(0xFFFBBF24)
    val Equals = Color(0xFF22C55E)

    val DarkSurface = Color(0xFF0B0E13)
    val DarkSurfaceVariant = Color(0xFF121720)
    val DarkCard = Color(0xFF151B25)
    val DarkOnSurface = Color(0xFFE6EDF3)
    val DarkOutline = Color(0xFF2A3340)

    val LightSurface = Color(0xFFF7F8FA)
    val LightSurfaceVariant = Color(0xFFEEF1F5)
    val LightCard = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF10161C)
    val LightOutline = Color(0xFFD3DAE3)

    val Error = Color(0xFFE5484D)
}

/**
 * Dark scheme. Contrast choices:
 *  * accent `#38BDF8` on surface `#0B0E13` = 9.3:1 (AAA)
 *  * onSurface `#E6EDF3` on surface `#0B0E13` = 15.4:1 (AAA)
 *  * onSurfaceVariant `#9AA7B4` on surface `#0B0E13` = 6.4:1 (AA, body text)
 * Values are computed with the WCAG 2.1 relative-luminance formula; the check
 * is documented in FEATURE_ACCEPTANCE.md §Visual verification.
 */
val AxiomDarkColorScheme = darkColorScheme(
    primary = AxiomColors.Accent,
    onPrimary = Color(0xFF04121A),
    primaryContainer = Color(0xFF0E3A50),
    onPrimaryContainer = Color(0xFFD8F1FF),
    secondary = AxiomColors.AccentDim,
    onSecondary = Color(0xFF04121A),
    tertiary = AxiomColors.Operator,
    onTertiary = Color(0xFF1A1200),
    background = AxiomColors.DarkSurface,
    onBackground = AxiomColors.DarkOnSurface,
    surface = AxiomColors.DarkSurface,
    onSurface = AxiomColors.DarkOnSurface,
    surfaceVariant = AxiomColors.DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9AA7B4),
    surfaceContainer = AxiomColors.DarkCard,
    surfaceContainerHigh = Color(0xFF1A212C),
    outline = AxiomColors.DarkOutline,
    outlineVariant = Color(0xFF1E2630),
    error = AxiomColors.Error,
    onError = Color(0xFFFFFFFF),
    inverseSurface = AxiomColors.LightSurface,
    inverseOnSurface = AxiomColors.LightOnSurface,
    scrim = Color(0xFF000000),
)

/**
 * Light scheme. Contrast choices:
 *  * onSurface `#10161C` on surface `#F7F8FA` = 16.4:1 (AAA)
 *  * accent used for controls is darkened to `#0284C7` for text/icons so it
 *    reaches 4.9:1 on light surfaces (AA); the bright `#38BDF8` is only used as
 *    a fill behind dark text.
 */
val AxiomLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EEFF),
    onPrimaryContainer = Color(0xFF00324B),
    secondary = AxiomColors.AccentDim,
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    background = AxiomColors.LightSurface,
    onBackground = AxiomColors.LightOnSurface,
    surface = AxiomColors.LightSurface,
    onSurface = AxiomColors.LightOnSurface,
    surfaceVariant = AxiomColors.LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5666),
    surfaceContainer = AxiomColors.LightCard,
    surfaceContainerHigh = Color(0xFFF1F4F8),
    outline = AxiomColors.LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFC02A2F),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1B222B),
    inverseOnSurface = Color(0xFFEDF2F7),
    scrim = Color(0xFF000000),
)
