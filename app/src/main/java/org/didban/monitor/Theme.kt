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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═════════════════════════════════════════════════════════════════════════════
//  DIDBAN · OBSIDIAN ZENITH DESIGN SYSTEM
//  A hyper-refined, minimalist, telemetry-grade visual language for server
//  observability and DevOps command centers.
//  Ultra-clean surfaces, micro-hairlines, luminous accents, and precision type.
// ═════════════════════════════════════════════════════════════════════════════

data class DidbanPalette(
    // Surface Hierarchy
    val canvas: Color,              // Root app background (Obsidian deep space)
    val surface: Color,             // Primary cards & content surfaces
    val surfaceElevated: Color,     // Floating dialogs, popups, elevated panels
    val surfaceLow: Color,          // Sunken wells, inputs, terminal boxes, inner tiles
    val surfaceHighlight: Color,    // Active states, hover fills, selected chip backgrounds
    val hairline: Color,            // 1dp subtle structural border
    val hairlineStrong: Color,      // Emphasized border for focus & active items
    val hairlineSubtle: Color,      // Micro-divider between rows

    // Typography
    val textPrimary: Color,         // Crisp headers, primary values, active titles
    val textSecondary: Color,       // Body copy, labels, secondary metadata
    val textTertiary: Color,        // Micro-captions, unit labels, inactive hints

    // Signature Interactive Hue (Luminous Cyan)
    val accent: Color,              // Primary electric cyan
    val accentDeep: Color,          // Deep cyan for gradients
    val accentGlow: Color,          // Atmospheric glow wash (≈18%)
    val accentDim: Color,           // Subtle accent tint (≈10%)
    val onAccent: Color,            // High-contrast text on accent fill

    // Secondary Telemetry Hue (Cyber Violet - RAM & Memory)
    val violet: Color,
    val violetDeep: Color,
    val violetDim: Color,

    // Telemetry & Semantic Signals
    val ok: Color,                  // Healthy / Running (Pure Emerald)
    val okDim: Color,
    val warn: Color,                // Degraded / Spike / Caution (Amber Gold)
    val warnDim: Color,
    val danger: Color,              // Down / Critical / Destructive (Rose Crimson)
    val dangerDim: Color,
    val info: Color,                // Network / Informational (Electric Sapphire)
    val infoDim: Color,

    // Utility
    val neutral: Color,             // Muted icon/meta tint
    val track: Color,               // Progress, ring gauge, and meter tracks
    val glassBorder: Color          // Top-edge light refraction border
)

/** Midnight Obsidian — Default flagship theme. Deep, focused, noise-free. */
val ObsidianPalette = DidbanPalette(
    canvas = Color(0xFF07090E),
    surface = Color(0xFF0F131D),
    surfaceElevated = Color(0xFF151C28),
    surfaceLow = Color(0xFF0A0D14),
    surfaceHighlight = Color(0xFF1C2536),
    hairline = Color(0xFF171F2C),
    hairlineStrong = Color(0xFF26334A),
    hairlineSubtle = Color(0xFF111722),

    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF54627A),

    accent = Color(0xFF00E5FF),
    accentDeep = Color(0xFF0284C7),
    accentGlow = Color(0x3300E5FF),
    accentDim = Color(0x1800E5FF),
    onAccent = Color(0xFF021B24),

    violet = Color(0xFFA855F7),
    violetDeep = Color(0xFF7E22CE),
    violetDim = Color(0x1AA855F7),

    ok = Color(0xFF10B981),
    okDim = Color(0x1810B981),
    warn = Color(0xFFF59E0B),
    warnDim = Color(0x18F59E0B),
    danger = Color(0xFFEF4444),
    dangerDim = Color(0x20EF4444),
    info = Color(0xFF3B82F6),
    infoDim = Color(0x183B82F6),

    neutral = Color(0xFF64748B),
    track = Color(0xFF151C28),
    glassBorder = Color(0x33FFFFFF)
)

/** Platinum Daylight — Clean, high-contrast, airy daylight theme. */
val PlatinumPalette = DidbanPalette(
    canvas = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9),
    surfaceLow = Color(0xFFF1F5F9),
    surfaceHighlight = Color(0xFFE2E8F0),
    hairline = Color(0xFFE2E8F0),
    hairlineStrong = Color(0xFFCBD5E1),
    hairlineSubtle = Color(0xFFF1F5F9),

    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),

    accent = Color(0xFF0284C7),
    accentDeep = Color(0xFF0369A1),
    accentGlow = Color(0x280284C7),
    accentDim = Color(0x140284C7),
    onAccent = Color(0xFFFFFFFF),

    violet = Color(0xFF7C3AED),
    violetDeep = Color(0xFF5B21B6),
    violetDim = Color(0x147C3AED),

    ok = Color(0xFF059669),
    okDim = Color(0x14059669),
    warn = Color(0xFFD97706),
    warnDim = Color(0x14D97706),
    danger = Color(0xFFDC2626),
    dangerDim = Color(0x14DC2626),
    info = Color(0xFF2563EB),
    infoDim = Color(0x142563EB),

    neutral = Color(0xFF64748B),
    track = Color(0xFFE2E8F0),
    glassBorder = Color(0x33000000)
)

// Legacy aliases for backward compatibility
val NightwatchPalette = ObsidianPalette
val DaylightPalette = PlatinumPalette

val LocalDidbanPalette = staticCompositionLocalOf { ObsidianPalette }

/**
 * Direct access to design tokens anywhere in Compose UI:
 * `Ds.accent`, `Ds.surface`, `Ds.hairline`, etc.
 */
object Ds {
    val canvas: Color @Composable get() = LocalDidbanPalette.current.canvas
    val surface: Color @Composable get() = LocalDidbanPalette.current.surface
    val surfaceElevated: Color @Composable get() = LocalDidbanPalette.current.surfaceElevated
    val surfaceLow: Color @Composable get() = LocalDidbanPalette.current.surfaceLow
    val surfaceHighlight: Color @Composable get() = LocalDidbanPalette.current.surfaceHighlight
    val hairline: Color @Composable get() = LocalDidbanPalette.current.hairline
    val hairlineStrong: Color @Composable get() = LocalDidbanPalette.current.hairlineStrong
    val hairlineSubtle: Color @Composable get() = LocalDidbanPalette.current.hairlineSubtle

    val textPrimary: Color @Composable get() = LocalDidbanPalette.current.textPrimary
    val textSecondary: Color @Composable get() = LocalDidbanPalette.current.textSecondary
    val textTertiary: Color @Composable get() = LocalDidbanPalette.current.textTertiary

    val accent: Color @Composable get() = LocalDidbanPalette.current.accent
    val accentDeep: Color @Composable get() = LocalDidbanPalette.current.accentDeep
    val accentGlow: Color @Composable get() = LocalDidbanPalette.current.accentGlow
    val accentDim: Color @Composable get() = LocalDidbanPalette.current.accentDim
    val onAccent: Color @Composable get() = LocalDidbanPalette.current.onAccent

    val violet: Color @Composable get() = LocalDidbanPalette.current.violet
    val violetDeep: Color @Composable get() = LocalDidbanPalette.current.violetDeep
    val violetDim: Color @Composable get() = LocalDidbanPalette.current.violetDim

    val ok: Color @Composable get() = LocalDidbanPalette.current.ok
    val okDim: Color @Composable get() = LocalDidbanPalette.current.okDim
    val warn: Color @Composable get() = LocalDidbanPalette.current.warn
    val warnDim: Color @Composable get() = LocalDidbanPalette.current.warnDim
    val danger: Color @Composable get() = LocalDidbanPalette.current.danger
    val dangerDim: Color @Composable get() = LocalDidbanPalette.current.dangerDim
    val info: Color @Composable get() = LocalDidbanPalette.current.info
    val infoDim: Color @Composable get() = LocalDidbanPalette.current.infoDim

    val neutral: Color @Composable get() = LocalDidbanPalette.current.neutral
    val track: Color @Composable get() = LocalDidbanPalette.current.track
    val glassBorder: Color @Composable get() = LocalDidbanPalette.current.glassBorder

    val palette: DidbanPalette @Composable get() = LocalDidbanPalette.current
}

// ── Typography Definition ───────────────────────────────────────────────────

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

val Vazirmatn = FontFamily(
    Font(R.font.vazir_400, FontWeight.Normal),
    Font(R.font.vazir_500, FontWeight.Medium),
    Font(R.font.vazir_600, FontWeight.SemiBold),
    Font(R.font.vazir_700, FontWeight.Bold)
)

/** Automatic typeface selection based on layout direction (Persian = Vazirmatn, English = Inter). */
val AppFontFamily: FontFamily
    @Composable get() = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Vazirmatn else Inter

/** Tabular numeric setting for zero-jitter realtime gauges and charts. */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

@Composable
private fun didbanTypography(): Typography {
    val base = TextStyle(fontFamily = AppFontFamily)
    return Typography(
        displayLarge = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        displaySmall = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineLarge = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        headlineMedium = base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.copy(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
        bodyMedium = base.copy(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
        bodySmall = base.copy(fontSize = 11.5.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.Normal),
        labelLarge = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        labelMedium = base.copy(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = base.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
    )
}

private fun schemeFor(p: DidbanPalette, dark: Boolean): ColorScheme {
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
        background = p.canvas,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceContainerLowest = p.surfaceLow,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceElevated,
        surfaceContainerHighest = p.surfaceElevated,
        surfaceVariant = p.surfaceElevated,
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
        background = p.canvas,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = p.surfaceLow,
        surfaceContainer = p.surfaceLow,
        surfaceContainerHigh = p.surfaceElevated,
        surfaceContainerHighest = p.surfaceElevated,
        surfaceVariant = p.surfaceElevated,
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
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun DidbanTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (dark) ObsidianPalette else PlatinumPalette
    CompositionLocalProvider(LocalDidbanPalette provides palette) {
        MaterialTheme(
            colorScheme = schemeFor(palette, dark),
            typography = didbanTypography(),
            shapes = DidbanShapes,
            content = content
        )
    }
}
