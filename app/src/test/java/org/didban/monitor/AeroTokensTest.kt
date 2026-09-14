package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 · item 3-A — JVM tests for the Aerospatial token layer
 * (pure logic in AeroTokens.kt; the Compose render layer is code-reviewed).
 */
class AeroTokensTest {

    // ── Resolution table ────────────────────────────────────────────────────

    @Test
    fun `light mode always resolves to platinum light`() {
        val r = resolveAeroTheme(AeroThemeMode.LIGHT, systemDark = true)
        assertEquals(false, r.isDark)
        assertEquals(AeroPlatinumTokens, r.tokens)
    }

    @Test
    fun `dark mode always resolves to cockpit dark`() {
        val r = resolveAeroTheme(AeroThemeMode.DARK, systemDark = false)
        assertEquals(true, r.isDark)
        assertEquals(AeroCockpitTokens, r.tokens)
    }

    @Test
    fun `auto follows system light`() {
        val r = resolveAeroTheme(AeroThemeMode.AUTO, systemDark = false)
        assertEquals(false, r.isDark)
        assertEquals(AeroPlatinumTokens, r.tokens)
    }

    @Test
    fun `auto follows system dark`() {
        val r = resolveAeroTheme(AeroThemeMode.AUTO, systemDark = true)
        assertEquals(true, r.isDark)
        assertEquals(AeroCockpitTokens, r.tokens)
    }

    // ── Persisted id mapping (Prefs theme_mode strings) ─────────────────────

    @Test
    fun `fromId maps persisted values`() {
        assertEquals(AeroThemeMode.LIGHT, AeroThemeMode.fromId("light"))
        assertEquals(AeroThemeMode.DARK, AeroThemeMode.fromId("dark"))
        assertEquals(AeroThemeMode.AUTO, AeroThemeMode.fromId("auto"))
    }

    @Test
    fun `fromId falls back to LIGHT for unknown ids`() {
        assertEquals(AeroThemeMode.LIGHT, AeroThemeMode.fromId(""))
        assertEquals(AeroThemeMode.LIGHT, AeroThemeMode.fromId("obsidian"))
        assertEquals(AeroThemeMode.LIGHT, AeroThemeMode.fromId("DARK"))
    }

    // ── Exact token values (light / platinum — default) ─────────────────────

    @Test
    fun `platinum tokens have exact v3 values`() {
        val t = AeroPlatinumTokens
        assertEquals(0xFFF4F6FA, t.canvas)
        assertEquals(0xFFFFFFFF, t.surface)
        assertEquals(0xFFFBFCFE, t.surfaceElevated)
        assertEquals(0xFFEEF2F8, t.surfaceLow)
        assertEquals(0xFFE3E8F0, t.surfaceHighlight)
        assertEquals(0xFFE3E8F0, t.hairline)
        assertEquals(0xFFCBD5E1, t.hairlineStrong)
        assertEquals(0xFFF1F5F9, t.hairlineSubtle)
        assertEquals(0xFF0E1B2C, t.textPrimary)
        assertEquals(0xFF46566E, t.textSecondary)
        assertEquals(0xFF8A97AB, t.textTertiary)
        assertEquals(0xFF1E40AF, t.accent)
        assertEquals(0xFF1E3A8A, t.accentDeep)
        assertEquals(0xFF7C3AED, t.violet)
        assertEquals(0xFF0E9F6E, t.ok)
        assertEquals(0xFFB45309, t.warn)
        assertEquals(0xFFDC2626, t.danger)
        assertEquals(0xFF2563EB, t.info)
        assertEquals(0xFFE3E8F0, t.track)
    }

    // ── Exact token values (dark / cockpit) ─────────────────────────────────

    @Test
    fun `cockpit tokens have exact v3 values`() {
        val t = AeroCockpitTokens
        assertEquals(0xFF070B14, t.canvas)
        assertEquals(0xFF0D1421, t.surface)
        assertEquals(0xFF121B2C, t.surfaceElevated)
        assertEquals(0xFF090E18, t.surfaceLow)
        assertEquals(0xFF1C2A44, t.surfaceHighlight)
        assertEquals(0xFF1B2536, t.hairline)
        assertEquals(0xFF27364E, t.hairlineStrong)
        assertEquals(0xFF101826, t.hairlineSubtle)
        assertEquals(0xFFEEF2F8, t.textPrimary)
        assertEquals(0xFF8FA0B8, t.textSecondary)
        assertEquals(0xFF55647C, t.textTertiary)
        assertEquals(0xFFFFB454, t.accent)
        assertEquals(0xFFC77E1B, t.accentDeep)
        assertEquals(0xFFA78BFA, t.violet)
        assertEquals(0xFF2DD4A7, t.ok)
        assertEquals(0xFFF5B942, t.warn)
        assertEquals(0xFFFF5C5C, t.danger)
        assertEquals(0xFF5CA8FF, t.info)
        assertEquals(0xFF121B2C, t.track)
    }

    // ── "Completely different" guards ───────────────────────────────────────

    @Test
    fun `v3 accents differ from both legacy themes`() {
        val legacyObsidianAccent = 0xFF00E5FF
        val legacyPlatinumAccent = 0xFF0284C7
        assertNotEquals(legacyObsidianAccent, AeroPlatinumTokens.accent)
        assertNotEquals(legacyPlatinumAccent, AeroPlatinumTokens.accent)
        assertNotEquals(legacyObsidianAccent, AeroCockpitTokens.accent)
        assertNotEquals(legacyPlatinumAccent, AeroCockpitTokens.accent)
    }

    @Test
    fun `v3 canvases differ from legacy canvases`() {
        val legacyObsidianCanvas = 0xFF07090E
        val legacyPlatinumCanvas = 0xFFF8FAFC
        assertNotEquals(legacyObsidianCanvas, AeroCockpitTokens.canvas)
        assertNotEquals(legacyPlatinumCanvas, AeroPlatinumTokens.canvas)
    }

    @Test
    fun `the two v3 themes are distinct identities`() {
        assertNotEquals(AeroPlatinumTokens.accent, AeroCockpitTokens.accent)
        assertNotEquals(AeroPlatinumTokens.canvas, AeroCockpitTokens.canvas)
        assertNotEquals(AeroPlatinumTokens.textPrimary, AeroCockpitTokens.textPrimary)
        assertNotEquals(AeroPlatinumTokens.ok, AeroCockpitTokens.ok)
        assertNotEquals(AeroPlatinumTokens.danger, AeroCockpitTokens.danger)
    }

    // ── Token hygiene: opacity invariants ───────────────────────────────────

    @Test
    fun `all opaque token fields carry full alpha`() {
        for (name in listOf("platinum" to AeroPlatinumTokens, "cockpit" to AeroCockpitTokens)) {
            for ((field, value) in name.second.opaqueFields()) {
                assertEquals(
                    "${name.first}.$field must be fully opaque (0xFF alpha), got ${value.toString(16)}",
                    0xFF000000L, value and 0xFF000000L
                )
            }
        }
    }

    @Test
    fun `glow and dim tints carry partial alpha`() {
        for (t in listOf(AeroPlatinumTokens, AeroCockpitTokens)) {
            assertTrue("glow must be translucent", (t.accentGlow and 0xFF000000L) < 0xFF000000L)
            assertTrue("dim must be translucent", (t.accentDim and 0xFF000000L) < 0xFF000000L)
        }
    }
}
