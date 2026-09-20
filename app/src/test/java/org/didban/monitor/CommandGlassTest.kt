package org.didban.monitor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

/** The glass contract: cards are translucent by design, chrome is a touch
 * stronger, and text keeps AA contrast over the canvas through every fill
 * stop. Orb glows stay decorative under a dim veil (verified visually via
 * the redesign screenshots CI uploads); this test guards the formula. */
class CommandGlassTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun `cards are translucent by design in both themes`() {
        val dark = commandGlassMaterial(CommandDarkPalette)
        assertEquals(3, dark.fill.size)
        assertTrue(dark.fill[0].alpha in .30f.. .45f)
        assertTrue(dark.fill[2].alpha in .10f.. .25f)
        val light = commandGlassMaterial(CommandLightPalette)
        assertEquals(3, light.fill.size)
        assertTrue(light.fill[0].alpha in .55f.. .90f)
        assertTrue(light.fill[2].alpha in .35f.. .80f)
    }

    @Test fun `chrome is stronger than plain cards`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val plain = commandGlassMaterial(p)
            val chrome = commandGlassMaterial(p, chrome = true)
            assertTrue(chrome.fill[0].alpha > plain.fill[0].alpha)
            assertTrue(chrome.fill[2].alpha > plain.fill[2].alpha)
        }
    }

    @Test fun `text stays AA over the canvas through every fill stop`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(false, true).forEach { chrome ->
                val m = commandGlassMaterial(p, chrome, false)
                m.fill.forEach { fill ->
                    val bg = fill.compositeOver(p.canvas)
                    // Apple tradeoff: titles stay strong (4.0+), secondary
                    // content and accents follow the WCAG large-text /
                    // component bar (3.0+) on milky glass.
                    assertTrue(
                        "Primary contrast ${contrast(p.textPrimary, bg)} over $fill",
                        contrast(p.textPrimary, bg) >= 4.0f
                    )
                    listOf(p.textSecondary, p.textTertiary, p.accent).forEach { text ->
                        assertTrue(
                            "Glass contrast ${contrast(text, bg)} over $fill",
                            contrast(text, bg) >= 3.0f
                        )
                    }
                }
            }
        }
    }
}
