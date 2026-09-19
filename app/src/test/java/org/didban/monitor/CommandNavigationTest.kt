package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

class CommandNavigationTest {
    @Test fun `route keys remain readable for saved states and old callers`() {
        val routes = CommandRoute.values()
        assertEquals(routes.size, routes.map { it.key }.distinct().size)
        routes.forEach { assertEquals(it, CommandRoute.fromKey(it.key)) }
    }

    @Test fun `five old pages expose just one menu destination`() {
        val menu = CommandWorkspace.values().flatMap(::commandMenuRoutes)
        val old = setOf(CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.FLEET,
            CommandRoute.SERVER_DOSSIER, CommandRoute.MANAGE_SERVERS)
        assertEquals(listOf(CommandRoute.FLEET), menu.filter { it in old })
        assertTrue(commandMenuRoutes(CommandWorkspace.OBSERVE).isEmpty())
        assertTrue(CommandRoute.SSH in menu)
        assertTrue(CommandRoute.DOCKER in menu)
    }

    @Test fun `server workspace is the only root and only list root exits`() {
        val root = CommandNavigation.root()
        assertEquals(CommandRoute.FLEET, root.current.route)
        assertEquals(ServerPane.LIST, root.current.serverPane)
        assertEquals(root, root.back())
        assertEquals(CommandBackAction.EXIT, root.backAction(false, false))
        assertEquals(CommandBackAction.CLOSE_PANE, root.openServer(7).backAction(false, false))
    }

    @Test fun `overview and incidents links normalize to same workspace`() {
        val root = CommandNavigation.root()
        assertEquals(root, root.navigate(CommandRoute.OVERVIEW))
        assertEquals(root, root.navigate(CommandRoute.INCIDENTS))
        assertEquals(root.openServer(7), root.navigate(CommandRoute.SERVER_DOSSIER, 7))
        assertEquals(ServerPane.ADD, root.navigate(CommandRoute.MANAGE_SERVERS, null).current.serverPane)
    }

    @Test fun `tool trip returns to same server inspector then list`() {
        val details = CommandNavigation.root().openServer(9)
        val tool = details.navigate(CommandRoute.DOCKER)
        assertEquals(9L, tool.current.serverId)
        assertEquals(details, tool.back())
        assertEquals(CommandNavigation.root(), tool.back().closePane())
    }

    @Test fun `selecting another card replaces inspector rather than stacking pages`() {
        val nav = CommandNavigation.root().openServer(1).openServer(2)
        assertEquals(2, nav.entries.size)
        assertEquals(2L, nav.current.serverId)
        assertEquals(CommandNavigation.root(), nav.closePane())
    }

    @Test fun `edit cancel restores inspector and add cancel restores list`() {
        val root = CommandNavigation.root()
        val details = root.openServer(9)
        assertEquals(details, details.editServer(9).closePane())
        assertEquals(root, root.editServer(null).closePane())
        assertEquals(details, details.editServer(null).closePane())
    }

    @Test fun `save opens saved server with no duplicate editor or panel`() {
        val nav = CommandNavigation.root().openServer(9).editServer(null).finishEditing(12)
        assertEquals(2, nav.entries.size)
        assertEquals(CommandDestination(CommandRoute.FLEET, 12, ServerPane.DETAILS), nav.current)
        assertEquals(CommandNavigation.root(), nav.closePane())
    }

    @Test fun `all other editors still return to their real origin`() {
        listOf(CommandRoute.UPTIME to CommandRoute.UPTIME_EDITOR, CommandRoute.DNS to CommandRoute.DNS_EDITOR,
            CommandRoute.NETWORK_TOOLS to CommandRoute.NETWORK_TOOLS_EDITOR, CommandRoute.TUNNELS to CommandRoute.TUNNELS_EDITOR
        ).forEach { (parent, editor) ->
            assertEquals(parent, CommandNavigation.root().navigate(parent).navigate(editor).back().current.route)
        }
    }

    @Test fun `menu and help close before server panel`() {
        val nav = CommandNavigation.root().openServer(7)
        assertEquals(CommandBackAction.CLOSE_HELP, nav.backAction(true, true))
        assertEquals(CommandBackAction.CLOSE_NAVIGATION, nav.backAction(true, false))
        assertEquals(CommandBackAction.CLOSE_PANE, nav.backAction(false, false))
    }

    @Test fun `v1 saved states migrate without phantom overview and fleet steps`() {
        val restored = CommandNavigation.restore(listOf("overview|", "fleet|", "server-dossier|9", "docker|9"))
        assertEquals(3, restored.entries.size)
        assertEquals(CommandRoute.DOCKER, restored.current.route)
        assertEquals(ServerPane.DETAILS, restored.back().current.serverPane)
        assertEquals(CommandNavigation.root(), restored.back().closePane())
    }

    @Test fun `panel state round trips and malformed state falls back safely`() {
        val nav = CommandNavigation.root().openServer(Long.MAX_VALUE).editServer(Long.MAX_VALUE)
        assertEquals(nav, CommandNavigation.restore(nav.save()))
        assertEquals(CommandNavigation.root(), CommandNavigation.restore(listOf("bad", "fleet|wrong", "unknown|5")))
        assertEquals(CommandNavigation.root(), CommandNavigation.restore(listOf("fleet||EDIT")))
    }

    @Test fun `deleting selected server removes stale scopes and panels`() {
        val nav = CommandNavigation.root().openServer(7).navigate(CommandRoute.SSH)
        assertEquals(CommandNavigation.root(), nav.removeServer(7))
        assertEquals(nav, nav.removeServer(99))
    }

    @Test fun `repeated taps do not grow history`() {
        val nav = CommandNavigation.root().openServer(7)
        assertEquals(nav, nav.openServer(7))
        assertEquals(nav.editServer(7), nav.editServer(7).editServer(7))
        assertEquals(CommandNavigation.root(), nav.navigate(CommandRoute.FLEET))
    }

    @Test fun `bounded history always preserves server-list root`() {
        var nav = CommandNavigation.root()
        repeat(100) { nav = nav.navigate(CommandRoute.DOCKER, it.toLong()) }
        assertEquals(64, nav.entries.size)
        repeat(63) { nav = nav.back() }
        assertEquals(CommandNavigation.root(), nav)
    }
}
