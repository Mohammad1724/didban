package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

class CommandNavigationTest {
    @Test fun `route keys are unique and round trip`() {
        val routes = CommandRoute.values()
        assertEquals(routes.size, routes.map { it.key }.distinct().size)
        routes.forEach { assertEquals(it, CommandRoute.fromKey(it.key)) }
        assertEquals(CommandRoute.OVERVIEW, CommandRoute.fromKey("unknown"))
    }

    @Test fun `back returns to actual origin instead of workspace home`() {
        val fromOverview = CommandNavigation.root().navigate(CommandRoute.SERVER_DOSSIER, 7)
        assertEquals(CommandRoute.OVERVIEW, fromOverview.back().current.route)
        val fromIncidents = CommandNavigation.root().navigate(CommandRoute.INCIDENTS)
            .navigate(CommandRoute.SERVER_DOSSIER, 7)
        assertEquals(CommandRoute.INCIDENTS, fromIncidents.back().current.route)
    }

    @Test fun `cross workspace trip restores server and each visited page`() {
        var nav = CommandNavigation.root().navigate(CommandRoute.FLEET)
            .navigate(CommandRoute.SERVER_DOSSIER, 9).navigate(CommandRoute.DOCKER)
        nav = nav.back()
        assertEquals(CommandDestination(CommandRoute.SERVER_DOSSIER, 9), nav.current)
        assertEquals(CommandDestination(CommandRoute.FLEET), nav.back().current)
    }

    @Test fun `changing servers preserves original scope on back`() {
        val nav = CommandNavigation.root().navigate(CommandRoute.SERVER_DOSSIER, 1)
            .navigate(CommandRoute.SERVER_DOSSIER, 2)
        assertEquals(1L, nav.back().current.serverId)
    }

    @Test fun `each editor returns to the page that opened it`() {
        listOf(
            CommandRoute.UPTIME to CommandRoute.UPTIME_EDITOR,
            CommandRoute.DNS to CommandRoute.DNS_EDITOR,
            CommandRoute.NETWORK_TOOLS to CommandRoute.NETWORK_TOOLS_EDITOR,
            CommandRoute.TUNNELS to CommandRoute.TUNNELS_EDITOR
        ).forEach { (parent, editor) ->
            val nav = CommandNavigation.root().navigate(parent).navigate(editor)
            assertEquals(parent, nav.back().current.route)
        }
    }

    @Test fun `repeated destination or done navigation never creates a back loop`() {
        val start = CommandNavigation.root().navigate(CommandRoute.TUNNELS)
        assertEquals(start, start.navigate(CommandRoute.TUNNELS))
        assertEquals(start, start.navigate(CommandRoute.TUNNELS_EDITOR).navigate(CommandRoute.TUNNELS))
        assertEquals(CommandNavigation.root(), start.navigate(CommandRoute.OVERVIEW))
    }

    @Test fun `menu and help close without popping the current page`() {
        val nav = CommandNavigation.root().navigate(CommandRoute.SETTINGS)
        assertEquals(CommandBackAction.CLOSE_HELP, nav.backAction(true, true))
        assertEquals(CommandBackAction.CLOSE_NAVIGATION, nav.backAction(true, false))
        assertEquals(CommandBackAction.POP, nav.backAction(false, false))
        assertEquals(CommandBackAction.EXIT, nav.back().backAction(false, false))
    }

    @Test fun `root back is stable and only root can exit`() {
        val root = CommandNavigation.root()
        assertEquals(root, root.back())
        CommandRoute.values().filter { it != CommandRoute.OVERVIEW }.forEach {
            assertEquals(CommandBackAction.POP, root.navigate(it).backAction(false, false))
        }
    }

    @Test fun `history and server scope survive saved state round trip`() {
        val nav = CommandNavigation.root().navigate(CommandRoute.FLEET)
            .navigate(CommandRoute.SERVER_DOSSIER, Long.MAX_VALUE).navigate(CommandRoute.PROCESSES)
        assertEquals(nav, CommandNavigation.restore(nav.save()))
        assertEquals(CommandNavigation.root(), CommandNavigation.restore(listOf("bad", "fleet|wrong", "unknown|5")))
    }

    @Test fun `deep link can return safely to overview`() {
        val nav = CommandNavigation.root().navigate(CommandRoute.SERVER_DOSSIER, 42)
        assertEquals(CommandNavigation.root(), nav.back())
    }

    @Test fun `history is bounded but never loses the overview root`() {
        var nav = CommandNavigation.root()
        repeat(100) { nav = nav.navigate(CommandRoute.SERVER_DOSSIER, it.toLong()) }
        assertEquals(64, nav.entries.size)
        repeat(63) { nav = nav.back() }
        assertEquals(CommandNavigation.root(), nav)
    }

    @Test fun `unrelated tools do not pretend to refresh server metrics`() {
        assertTrue(CommandRoute.OVERVIEW.hasMetricsRefresh())
        assertTrue(CommandRoute.SERVER_DOSSIER.hasMetricsRefresh())
        assertFalse(CommandRoute.DOCKER.hasMetricsRefresh())
        assertFalse(CommandRoute.DNS_EDITOR.hasMetricsRefresh())
        assertFalse(CommandRoute.SSH.hasMetricsRefresh())
    }
}
