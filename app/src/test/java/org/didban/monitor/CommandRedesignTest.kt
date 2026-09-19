package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

class CommandRedesignTest {
    @Test fun `every legacy route has one primary section and existing keys stay intact`() {
        assertEquals(4, CommandPrimary.values().size)
        CommandRoute.values().forEach { route ->
            assertTrue(route.primary() in CommandPrimary.values())
            assertEquals(route, CommandRoute.values().single { it.key == route.key })
        }
        CommandPrimary.values().forEach { assertEquals(it, it.root.primary()) }
    }
    @Test fun `server tools and independent tools have separate reachable homes`() {
        assertEquals(8, serverToolRoutes.distinct().size)
        assertTrue(serverToolRoutes.all { it.primary() == CommandPrimary.SERVERS })
        assertTrue(independentToolRoutes.all { it.primary() == CommandPrimary.TOOLS })
        assertTrue(settingsToolRoutes.all { it.primary() == CommandPrimary.SETTINGS })
        assertTrue(serverToolRoutes.intersect(independentToolRoutes.toSet()).isEmpty())
        assertTrue(serverToolRoutes.containsAll(listOf(CommandRoute.SSH, CommandRoute.SFTP, CommandRoute.BANDWIDTH, CommandRoute.SECURITY)))
        assertEquals(CommandPrimary.MONITORING, CommandRoute.RADAR.primary())
    }
    @Test fun `unknown filter never treats missing measurements as offline`() {
        val fleet = (1L..3L).map { ServerConfig(it, "Node $it", "node$it.example") }
        val states = mapOf(1L to Repo.State(error = "unreachable"), 2L to Repo.State(updated = 5L))
        assertEquals(listOf(2L, 3L), visibleFleet(fleet, states, "", FleetFilter.UNKNOWN, 10L).map { it.id })
        assertEquals(listOf(1L), visibleFleet(fleet, states, "", FleetFilter.OFFLINE, 10L).map { it.id })
    }
    @Test fun `both palettes keep normal text and semantic states above AA contrast`() {
        fun ratio(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
        }
        listOf(CommandLightPalette, CommandDarkPalette).forEach { p ->
            listOf(p.textPrimary to p.surface, p.textSecondary to p.surface,
                p.textTertiary to p.canvas, p.onAccent to p.accent,
                p.success to p.successSurface, p.warning to p.warningSurface,
                p.danger to p.dangerSurface).forEach { (text, bg) ->
                assertTrue("Contrast ${ratio(text, bg)}", ratio(text, bg) >= 4.5f)
            }
        }
    }
}
