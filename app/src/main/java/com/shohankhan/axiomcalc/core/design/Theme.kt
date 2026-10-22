package com.shohankhan.axiomcalc.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shohankhan.axiomcalc.domain.model.ThemeMode

/**
 * Type scale. All sizes are in `sp` so the OS font scale is honoured, and the
 * calculator display deliberately uses a large base size with `maxLines = 1`
 * plus `autoSize` behaviour implemented by the display component (never by
 * clipping the value silently).
 */
val AxiomTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 60.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

val AxiomShapes = Shapes()

/** Semantic sizes shared by every screen so targets never fall below 48 dp. */
object AxiomDimens {
    val MinTouchTarget: Dp = 48.dp
    val KeyHeight: Dp = 64.dp
    val KeyHeightCompact: Dp = 54.dp
    val KeyGap: Dp = 8.dp
    val ScreenPadding: Dp = 16.dp
    val CardPadding: Dp = 16.dp
    val ListGap: Dp = 8.dp
    val Radius: Dp = 18.dp
    val KeyRadius: Dp = 16.dp
}

/** Extra semantic colours used by keypad roles that Material 3 does not define. */
data class AxiomExtendedColors(
    val digitKey: Color,
    val functionKey: Color,
    val operatorKey: Color,
    val equalsKey: Color,
    val onEqualsKey: Color,
    val onOperatorKey: Color,
    val memoryKey: Color,
    val onMemoryKey: Color,
    val displayText: Color,
    val previewText: Color,
    val errorText: Color,
    val keyBorder: Color,
)

private val darkExtended = AxiomExtendedColors(
    digitKey = Color(0xFF1B222C),
    functionKey = Color(0xFF141A23),
    operatorKey = Color(0xFF3A2E0C),
    equalsKey = AxiomColors.Equals,
    onEqualsKey = Color(0xFF04160A),
    onOperatorKey = AxiomColors.Operator,
    memoryKey = Color(0xFF132A38),
    onMemoryKey = AxiomColors.Accent,
    displayText = AxiomColors.DarkOnSurface,
    previewText = Color(0xFF8C99A6),
    errorText = Color(0xFFFF8A8D),
    keyBorder = Color(0xFF232C38),
)

private val lightExtended = AxiomExtendedColors(
    digitKey = Color(0xFFFFFFFF),
    functionKey = Color(0xFFE9EEF4),
    operatorKey = Color(0xFFFFF3D6),
    equalsKey = Color(0xFF16A34A),
    onEqualsKey = Color(0xFFFFFFFF),
    onOperatorKey = Color(0xFF92400E),
    memoryKey = Color(0xFFDDEEFF),
    onMemoryKey = Color(0xFF075985),
    displayText = AxiomColors.LightOnSurface,
    previewText = Color(0xFF5A6675),
    errorText = Color(0xFFB42318),
    keyBorder = Color(0xFFDDE3EB),
)

val LocalAxiomExtended = staticCompositionLocalOf { darkExtended }

/** Resolved theme mode, used by components that must adapt (e.g. previews). */
val LocalIsDark = staticCompositionLocalOf { true }

/**
 * App theme.
 *
 * Dynamic colour is applied on Android 12+ because the OS palette is the user's
 * own choice, and our brand tokens remain the default below 12 and whenever the
 * system palette is unavailable.
 */
@Composable
fun AxiomTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val useDynamic = dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && dark -> androidx.compose.material3.dynamicDarkColorScheme(
            androidx.compose.ui.platform.LocalContext.current,
        )
        useDynamic && !dark -> androidx.compose.material3.dynamicLightColorScheme(
            androidx.compose.ui.platform.LocalContext.current,
        )
        dark -> AxiomDarkColorScheme
        else -> AxiomLightColorScheme
    }
    val extended = if (dark) darkExtended else lightExtended
    CompositionLocalProvider(LocalAxiomExtended provides extended, LocalIsDark provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AxiomTypography,
            shapes = AxiomShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `Axiom.extended.equalsKey`. */
object Axiom {
    val extended: AxiomExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAxiomExtended.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDark.current
}
