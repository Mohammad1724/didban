package org.didban.monitor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═════════════════════════════════════════════════════════════════════════════
//  DIDBAN · NIGHTWATCH DESIGN LANGUAGE
//  A calm, precise "observatory" aesthetic: deep space surfaces, hairline
//  structure, one luminous accent, telemetry-grade typography (Inter + mono
//  tabular numerals). Dark-first, daylight-ready.
// ═════════════════════════════════════════════════════════════════════════════

data class DidbanPalette(
    // Surfaces
    val canvas: Color,          // app background
    val surface: Color,         // cards & panels
    val surfaceLow: Color,      // wells, inputs, terminal, inner tiles
    val surfaceHigh: Color,     // elevated chips / pressed states
    val hairline: Color,        // 1dp structure borders
    val hairlineStrong: Color,  // emphasized borders
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    // Accent (the single interactive hue — luminous cyan)
    val accent: Color,
    val accentDeep: Color,      // gradient partner for accent fills
    val accentDim: Color,       // translucent accent wash (≈10%)
    val onAccent: Color,        // content drawn on accent fills
    // Semantic signals
    val ok: Color, val okDim: Color,
    val warn: Color, val warnDim: Color,
    val danger: Color, val dangerDim: Color,
    val info: Color, val infoDim: Color,
    val violet: Color, val violetDim: Color,   // secondary metric hue (RAM)
    val neutral: Color,                          // muted icons / meta
    val track: Color                             // progress & gauge tracks
)

/** Midnight observatory — the default experience. */
val NightwatchPalette = DidbanPalette(
    canvas = Color(0xFF0A0D13),
    surface = Color(0xFF12161F),
    surfaceLow = Color(0xFF0D1017),
    surfaceHigh = Color(0xFF1A2030),
    hairline = Color(0xFF1E2536),
    hairlineStrong = Color(0xFF2A3448),
    textPrimary = Color(0xFFE9EDF5),
    textSecondary = Color(0xFF94A0B4),
    textTertiary = Color(0xFF5C6678),
    accent = Color(0xFF22D3EE),
    accentDeep = Color(0xFF0891B2),
    accentDim = Color(0x1A22D3EE),
    onAccent = Color(0xFF052730),
    ok = Color(0xFF34D399), okDim = Color(0x1A34D399),
    warn = Color(0xFFFBBF24), warnDim = Color(0x1AFBBF24),
    danger = Color(0xFFFB7185), dangerDim = Color(0x1FFB7185),
    info = Color(0xFF7DA9FF), infoDim = Color(0x1A7DA9FF),
    violet = Color(0xFFA78BFA), violetDim = Color(0x1AA78BFA),
    neutral = Color(0xFF74809A),
    track = Color(0xFF202839)
)

/** Clean paper daylight — same skeleton, airier tones. */
val DaylightPalette = DidbanPalette(
    canvas = Color(0xFFF4F6FB),
    surface = Color(0xFFFFFFFF),
    surfaceLow = Color(0xFFEEF2F8),
    surfaceHigh = Color(0xFFE7ECF4),
    hairline = Color(0xFFE3E9F2),
    hairlineStrong = Color(0xFFC9D3E2),
    textPrimary = Color(0xFF141B27),
    textSecondary = Color(0xFF5A667A),
    textTertiary = Color(0xFF8B96A9),
    accent = Color(0xFF0891B2),
    accentDeep = Color(0xFF0E7490),
    accentDim = Color(0x1A0891B2),
    onAccent = Color(0xFFFFFFFF),
    ok = Color(0xFF059669), okDim = Color(0x1A059669),
    warn = Color(0xFFD97706), warnDim = Color(0x1AD97706),
    danger = Color(0xFFE11D48), dangerDim = Color(0x1AE11D48),
    info = Color(0xFF2563EB), infoDim = Color(0x1A2563EB),
    violet = Color(0xFF6D28D9), violetDim = Color(0x1A6D28D9),
    neutral = Color(0xFF64748B),
    track = Color(0xFFE2E8F1)
)

val LocalDidbanPalette = staticCompositionLocalOf { NightwatchPalette }

/**
 * Design tokens, readable anywhere in composition:
 *   Ds.accent, Ds.surface, Ds.textSecondary, …
 */
object Ds {
    val canvas: Color @Composable get() = LocalDidbanPalette.current.canvas
    val surface: Color @Composable get() = LocalDidbanPalette.current.surface
    val surfaceLow: Color @Composable get() = LocalDidbanPalette.current.surfaceLow
    val surfaceHigh: Color @Composable get() = LocalDidbanPalette.current.surfaceHigh
    val hairline: Color @Composable get() = LocalDidbanPalette.current.hairline
    val hairlineStrong: Color @Composable get() = LocalDidbanPalette.current.hairlineStrong
    val textPrimary: Color @Composable get() = LocalDidbanPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalDidbanPalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalDidbanPalette.current.textTertiary
    val accent: Color @Composable get() = LocalDidbanPalette.current.accent
    val accentDeep: Color @Composable get() = LocalDidbanPalette.current.accentDeep
    val accentDim: Color @Composable get() = LocalDidbanPalette.current.accentDim
    val onAccent: Color @Composable get() = LocalDidbanPalette.current.onAccent
    val ok: Color @Composable get() = LocalDidbanPalette.current.ok
    val okDim: Color @Composable get() = LocalDidbanPalette.current.okDim
    val warn: Color @Composable get() = LocalDidbanPalette.current.warn
    val warnDim: Color @Composable get() = LocalDidbanPalette.current.warnDim
    val danger: Color @Composable get() = LocalDidbanPalette.current.danger
    val dangerDim: Color @Composable get() = LocalDidbanPalette.current.dangerDim
    val info: Color @Composable get() = LocalDidbanPalette.current.info
    val infoDim: Color @Composable get() = LocalDidbanPalette.current.infoDim
    val violet: Color @Composable get() = LocalDidbanPalette.current.violet
    val violetDim: Color @Composable get() = LocalDidbanPalette.current.violetDim
    val neutral: Color @Composable get() = LocalDidbanPalette.current.neutral
    val track: Color @Composable get() = LocalDidbanPalette.current.track

    val palette: DidbanPalette @Composable get() = LocalDidbanPalette.current
}

// ── Typography: Inter for UI, JetBrains Mono for telemetry numerals ──────────

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold)
)

val Telemetry = FontFamily(
    Font(R.font.jbmono_400, FontWeight.Normal),
    Font(R.font.jbmono_500, FontWeight.Medium),
    Font(R.font.jbmono_600, FontWeight.SemiBold)
)

/** Numbers that never jitter as values refresh. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

private fun didbanTypography(): Typography {
    val base = TextStyle(fontFamily = Inter)
    return Typography(
        displaySmall = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineMedium = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = base.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
        bodyMedium = base.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
        bodySmall = base.copy(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
        labelLarge = base.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    )
}

private fun schemeFor(p: DidbanPalette, dark: Boolean): ColorScheme {
    val bg = if (dark) Color(0xFF0A0D13) else Color(0xFFF4F6FB)
    val onBg = if (dark) Color(0xFFE9EDF5) else Color(0xFF141B27)
    return if (dark) darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentDim,
        onPrimaryContainer = p.accent,
        secondary = p.violet,
        onSecondary = Color.White,
        secondaryContainer = p.violetDim,
        onSecondaryContainer = p.violet,
        tertiary = p.info,
        onTertiary = Color.White,
        background = bg,
        onBackground = onBg,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceContainerLowest = p.surfaceLow,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textSecondary,
        outline = p.hairlineStrong,
        outlineVariant = p.hairline,
        error = p.danger,
        onError = Color.White,
        errorContainer = p.dangerDim,
        onErrorContainer = p.danger
    ) else lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = p.accentDim,
        onPrimaryContainer = p.accent,
        secondary = p.violet,
        onSecondary = Color.White,
        secondaryContainer = p.violetDim,
        onSecondaryContainer = p.violet,
        tertiary = p.info,
        onTertiary = Color.White,
        background = bg,
        onBackground = onBg,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainer = p.surfaceLow,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textSecondary,
        outline = p.hairlineStrong,
        outlineVariant = p.hairline,
        error = p.danger,
        onError = Color.White,
        errorContainer = p.dangerDim,
        onErrorContainer = p.danger
    )
}

private val DidbanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun DidbanTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (dark) NightwatchPalette else DaylightPalette
    CompositionLocalProvider(LocalDidbanPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette, dark),
            typography = didbanTypography(),
            shapes = DidbanShapes,
            content = content
        )
    }
}
