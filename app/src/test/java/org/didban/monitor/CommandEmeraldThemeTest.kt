package org.didban.monitor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قرارداد هویت رنگی «زمرد» (۲۰۲۶-۰۹-۲۱).
 *
 * کاربر خواست سبز زمردی جای آبی را بگیرد. این تست‌ها همان تصمیم را قفل
 * می‌کنند: accent باید سبز باشد (نه آبی)، طلایی باید کنتراست AA داشته باشد،
 * و گرادیان هیرو باید از زمرد به زمرد عمیق برود — نه به آبی.
 */
class CommandEmeraldThemeTest {

    private fun ratio(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    private fun hue(color: Color): Float {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 0f
        val h = when (max) {
            r -> ((g - b) / d) % 6f
            g -> ((b - r) / d) + 2f
            else -> ((r - g) / d) + 4f
        }
        return ((h * 60f) + 360f) % 360f
    }

    // ── سبز جای آبی ─────────────────────────────────────────────────────────

    @Test
    fun `both palettes use a green accent instead of the legacy blue`() {
        val legacyBlueLight = Color(0xFF2364A6)
        val legacyBlueDark = Color(0xFFBFE9F4)
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val h = hue(p.accent)
            assertTrue("accent hue $h must sit in the green band", h in 130f..185f)
            // green channel dominates red by a wide margin (never a blue/cyan identity)
            assertTrue("accent must be green-dominant", p.accent.green > p.accent.red)
            assertNotEquals(legacyBlueLight, p.accent)
            assertNotEquals(legacyBlueDark, p.accent)
        }
    }

    @Test
    fun `the atmosphere tints are emerald and gold, not blue or violet`() {
        val tints = listOf(
            CommandEmeraldAurora.darkAccent, CommandEmeraldAurora.darkGold, CommandEmeraldAurora.darkJade,
            CommandEmeraldAurora.lightAccent, CommandEmeraldAurora.lightGold, CommandEmeraldAurora.lightJade
        )
        tints.forEach { c ->
            val h = hue(c)
            assertTrue("orb $c hue $h must be green→gold, never blue", h <= 200f)
        }
    }

    // ── طلایی ───────────────────────────────────────────────────────────────

    @Test
    fun `gold keeps AA contrast against onGold in both themes`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            assertTrue("onGold/gold ${ratio(p.onGold, p.gold)}", ratio(p.onGold, p.gold) >= 4.5f)
        }
    }

    @Test
    fun `gold reads as a warm hue and never as the interactive green`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val h = hue(p.gold)
            assertTrue("gold hue $h", h in 30f..60f)
            assertNotEquals(p.accent, p.gold)
        }
    }

    // ── گرادیان هیرو ────────────────────────────────────────────────────────

    @Test
    fun `hero gradient runs from emerald to deep emerald in both themes`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(p.heroTop, p.heroBottom).forEach { c ->
                val h = hue(c)
                assertTrue("hero $c hue $h", h in 130f..185f)
                assertTrue("hero stops must be green-dominant", c.green > c.red)
            }
            // the bottom stop is the deeper end of the gradient
            assertTrue(p.heroBottom.luminance() < p.heroTop.luminance())
        }
    }

    // ── دادهٔ تله‌متری (رنگ دوم) ─────────────────────────────────────────────

    @Test
    fun `telemetry hue is warm gold and distinct from the brand green`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val h = hue(p.violet)
            assertTrue("telemetry hue $h", h in 30f..60f)
            assertNotEquals(p.accent, p.violet)
        }
    }

    // ── کنتراست کامل پالت (سخت‌گیرانه‌تر از دو تست موجود) ──────────────────

    @Test
    fun `every semantic pair keeps AA contrast`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val pairs = listOf(
                "textPrimary/surface" to (p.textPrimary to p.surface),
                "textSecondary/surface" to (p.textSecondary to p.surface),
                "textTertiary/canvas" to (p.textTertiary to p.canvas),
                "onAccent/accent" to (p.onAccent to p.accent),
                "onGold/gold" to (p.onGold to p.gold),
                "success/successSurface" to (p.success to p.successSurface),
                "warning/warningSurface" to (p.warning to p.warningSurface),
                "danger/dangerSurface" to (p.danger to p.dangerSurface),
                "info/infoSurface" to (p.info to p.infoSurface)
            )
            pairs.forEach { (label, pair) ->
                assertTrue("$label = ${ratio(pair.first, pair.second)}", ratio(pair.first, pair.second) >= 4.5f)
            }
        }
    }

    @Test
    fun `accent stays legible on the raised surface`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            assertTrue(ratio(p.accent, p.surfaceRaised) >= 3f)
        }
    }

    // ── انیمیشن محیطی (نباید بی‌قاعده یا نامرئی باشد) ───────────────────────

    @Test
    fun `aurora drift stays bounded and closes its loop`() {
        var sawMovement = false
        var previous = CommandAurora.driftOffset(0f, 1f)
        for (step in 0..200) {
            val phase = step / 200f
            val v = CommandAurora.driftOffset(phase, 1f)
            assertTrue("drift $v left the ±1 unit box", v in -1f..1f)
            if (kotlin.math.abs(v - previous) > 1e-4f) sawMovement = true
            previous = v
        }
        assertTrue("drift must actually move", sawMovement)
        // the loop closes: end of the cycle equals the start
        assertEquals(CommandAurora.driftOffset(0f, 1f), CommandAurora.driftOffset(1f, 1f), 1e-4f)
        assertEquals(CommandAurora.driftOffset(0f, 2f), CommandAurora.driftOffset(1f, 2f), 1e-4f)
    }

    @Test
    fun `aurora drift is a slow ambient budget, not a distracting loop`() {
        assertTrue("period should be slow (was ${CommandAurora.periodMs} ms)", CommandAurora.periodMs >= 20_000)
        assertTrue("travel should be a few dp (was ${CommandAurora.driftDp})", CommandAurora.driftDp in 2f..16f)
    }

    @Test
    fun `aurora harmonic axes never move in lockstep`() {
        // both axes must be out of phase somewhere, otherwise the canvas would
        // slide diagonally as one rigid sheet
        val diverges = (0..40).any { step ->
            val phase = step / 40f
            val dx = CommandAurora.driftOffset(phase, 1f)
            val dy = CommandAurora.driftOffset(phase, 2f)
            kotlin.math.abs(dx - dy) > .25f
        }
        assertTrue(diverges)
    }
}
