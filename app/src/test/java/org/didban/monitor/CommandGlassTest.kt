package org.didban.monitor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CommandGlassTest {
    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)

    @Test fun `data cards remain opaque in both themes regardless of elevation`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(false, true).forEach { raised ->
                val m = commandGlassMaterial(p, raised = raised)
                assertEquals(1f, m.top.alpha, .001f)
                assertEquals(1f, m.bottom.alpha, .001f)
            }
        }
    }
    @Test fun `frosted chrome transmits only a small amount of background`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            val m = commandGlassMaterial(p, chrome = true)
            assertTrue(m.top.alpha in .95f.. .98f)
            assertTrue(m.bottom.alpha in .95f.. .98f)
        }
    }
    @Test fun `text stays AA on every layer endpoint and extreme chrome underlays`() {
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(false, true).forEach { chrome ->
                listOf(false, true).forEach { raised ->
                    val m = commandGlassMaterial(p, chrome, raised)
                    listOf(m.top, m.bottom).forEach { fill ->
                        listOf(Color.White, Color.Black, p.canvas).forEach { underneath ->
                            listOf(p.textPrimary, p.textSecondary, p.textTertiary, p.accent).forEach { text ->
                                assertTrue("Text contrast ${contrast(text, fill.compositeOver(underneath))}",
                                    contrast(text, fill.compositeOver(underneath)) >= 4.5f)
                            }
                        }
                    }
                }
            }
        }
    }
}
