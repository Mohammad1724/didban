package org.didban.monitor

/**
 * Phase 3 · item 3-A — Aerospatial design tokens (pure JVM, no Compose imports).
 *
 * The v3 system replaces the legacy Obsidian/Platinum identity. Two themes:
 *  - [AeroPlatinumTokens]  — light "paper + navy ink" (DEFAULT)
 *  - [AeroCockpitTokens]   — dark "mission-control night + amber"
 *
 * Hex values are the single source of truth; the Compose side
 * (AeroTheme.kt) renders them into [DidbanPalette] via Color(...).
 * Keep this file free of any Android/Compose dependency so it stays
 * unit-testable on the JVM harness.
 */
data class AeroTokens(
    // Surface hierarchy
    val canvas: Long,
    val surface: Long,
    val surfaceElevated: Long,
    val surfaceLow: Long,
    val surfaceHighlight: Long,
    val hairline: Long,
    val hairlineStrong: Long,
    val hairlineSubtle: Long,

    // Typography
    val textPrimary: Long,
    val textSecondary: Long,
    val textTertiary: Long,

    // Primary interactive hue
    val accent: Long,
    val accentDeep: Long,
    val accentGlow: Long,
    val accentDim: Long,
    val onAccent: Long,

    // Secondary telemetry hue (RAM / memory)
    val violet: Long,
    val violetDeep: Long,
    val violetDim: Long,

    // Semantic signals
    val ok: Long,
    val okDim: Long,
    val warn: Long,
    val warnDim: Long,
    val danger: Long,
    val dangerDim: Long,
    val info: Long,
    val infoDim: Long,

    // Utility
    val neutral: Long,
    val track: Long,
    val glassBorder: Long
) {
    /** All fully-opaque token fields must carry 0xFF alpha (no half-baked surfaces). */
    fun opaqueFields(): Map<String, Long> = mapOf(
        "canvas" to canvas, "surface" to surface, "surfaceElevated" to surfaceElevated,
        "surfaceLow" to surfaceLow, "surfaceHighlight" to surfaceHighlight,
        "hairline" to hairline, "hairlineStrong" to hairlineStrong, "hairlineSubtle" to hairlineSubtle,
        "textPrimary" to textPrimary, "textSecondary" to textSecondary, "textTertiary" to textTertiary,
        "accent" to accent, "accentDeep" to accentDeep, "onAccent" to onAccent,
        "violet" to violet, "violetDeep" to violetDeep,
        "ok" to ok, "warn" to warn, "danger" to danger, "info" to info,
        "neutral" to neutral, "track" to track
    )
}

/** Light platinum (DEFAULT theme of v3): cold paper, navy ink. */
val AeroPlatinumTokens = AeroTokens(
    canvas = 0xFFF4F6FA,
    surface = 0xFFFFFFFF,
    surfaceElevated = 0xFFFBFCFE,
    surfaceLow = 0xFFEEF2F8,
    surfaceHighlight = 0xFFE3E8F0,
    hairline = 0xFFE3E8F0,
    hairlineStrong = 0xFFCBD5E1,
    hairlineSubtle = 0xFFF1F5F9,

    textPrimary = 0xFF0E1B2C,
    textSecondary = 0xFF46566E,
    textTertiary = 0xFF8A97AB,

    accent = 0xFF1E40AF,
    accentDeep = 0xFF1E3A8A,
    accentGlow = 0x2E1E40AF,
    accentDim = 0x1A1E40AF,
    onAccent = 0xFFFFFFFF,

    violet = 0xFF7C3AED,
    violetDeep = 0xFF5B21B6,
    violetDim = 0x147C3AED,

    ok = 0xFF0E9F6E,
    okDim = 0x180E9F6E,
    warn = 0xFFB45309,
    warnDim = 0x18B45309,
    danger = 0xFFDC2626,
    dangerDim = 0x20DC2626,
    info = 0xFF2563EB,
    infoDim = 0x182563EB,

    neutral = 0xFF64748B,
    track = 0xFFE3E8F0,
    glassBorder = 0x140E1B2C
)

/** Dark cockpit (optional theme): night command room, amber instruments. */
val AeroCockpitTokens = AeroTokens(
    canvas = 0xFF070B14,
    surface = 0xFF0D1421,
    surfaceElevated = 0xFF121B2C,
    surfaceLow = 0xFF090E18,
    surfaceHighlight = 0xFF1C2A44,
    hairline = 0xFF1B2536,
    hairlineStrong = 0xFF27364E,
    hairlineSubtle = 0xFF101826,

    textPrimary = 0xFFEEF2F8,
    textSecondary = 0xFF8FA0B8,
    textTertiary = 0xFF55647C,

    accent = 0xFFFFB454,
    accentDeep = 0xFFC77E1B,
    accentGlow = 0x33FFB454,
    accentDim = 0x1AFFB454,
    onAccent = 0xFF1A1206,

    violet = 0xFFA78BFA,
    violetDeep = 0xFF7C3AED,
    violetDim = 0x1AA78BFA,

    ok = 0xFF2DD4A7,
    okDim = 0x182DD4A7,
    warn = 0xFFF5B942,
    warnDim = 0x18F5B942,
    danger = 0xFFFF5C5C,
    dangerDim = 0x20FF5C5C,
    info = 0xFF5CA8FF,
    infoDim = 0x185CA8FF,

    neutral = 0xFF64748B,
    track = 0xFF121B2C,
    glassBorder = 0x22FFFFFF
)

/** Theme selection. ids match the persisted Prefs `theme_mode` string values. */
enum class AeroThemeMode(val id: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto");

    companion object {
        fun fromId(id: String): AeroThemeMode =
            values().firstOrNull { it.id == id } ?: LIGHT
    }
}

data class ResolvedAeroTheme(val isDark: Boolean, val tokens: AeroTokens)

/**
 * Pure theme resolution — no Android dependencies.
 * "auto" follows the system; unknown modes (defensive) fall back to LIGHT,
 * which is the product default for v3.
 */
fun resolveAeroTheme(mode: AeroThemeMode, systemDark: Boolean): ResolvedAeroTheme = when (mode) {
    AeroThemeMode.LIGHT -> ResolvedAeroTheme(isDark = false, tokens = AeroPlatinumTokens)
    AeroThemeMode.DARK -> ResolvedAeroTheme(isDark = true, tokens = AeroCockpitTokens)
    AeroThemeMode.AUTO -> if (systemDark) {
        ResolvedAeroTheme(isDark = true, tokens = AeroCockpitTokens)
    } else {
        ResolvedAeroTheme(isDark = false, tokens = AeroPlatinumTokens)
    }
}
