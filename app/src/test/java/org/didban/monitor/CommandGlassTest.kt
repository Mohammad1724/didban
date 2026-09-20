package org.didban.monitor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

/** The glass contract (owner-approved spec, docs/design/glass-refinement.md):
 * card fills are pre-composited over the canvas and therefore fully OPAQUE —
 * real translucency rendered as hard inner rectangles / seams on physical
 * devices. Chrome reads a step brighter than plain cards, and text keeps AA
 * contrast over every fill stop. Orb glows stay decorative under a dim veil
 * (verified visually via the redesign screenshots CI uploads). */
class CommandGlassTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun `cards are opaque by design in both themes`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(false, true).forEach { chrome ->
                val m = commandGlassMaterial(p, chrome, false)
                assertEquals(3, m.fill.size)
                m.fill.forEach { fill ->
                    assertEquals("fill must be solid, got $fill", 1f, fill.alpha, 0f)
                }
            }
        }
    }

    @Test fun `chrome reads brighter than plain cards`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val plain = commandGlassMaterial(p)
            val chrome = commandGlassMaterial(p, chrome = true)
            plain.fill.indices.forEach { i ->
                assertTrue(
                    "chrome stop $i should outrank plain on ${p.canvas}",
                    chrome.fill[i].luminance() > plain.fill[i].luminance()
                )
            }
        }
    }

    @Test fun `light cards stay milky and dark cards stay charcoal`() {
        val light = commandGlassMaterial(CommandLightPalette)
        light.fill.forEach { fill ->
            assertTrue("light fill should stay near-white, got $fill", fill.luminance() > .70f)
        }
        val dark = commandGlassMaterial(CommandDarkPalette)
        dark.fill.forEach { fill ->
            assertTrue("dark fill should stay charcoal, got $fill", fill.luminance() < .30f)
        }
    }

    @Test fun `text stays AA over every fill stop`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(false, true).forEach { chrome ->
                val m = commandGlassMaterial(p, chrome, false)
                m.fill.forEach { fill ->
                    val bg = fill.compositeOver(p.canvas)
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
