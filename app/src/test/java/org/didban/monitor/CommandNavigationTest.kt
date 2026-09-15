package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Back-navigation contract for the Command shell.
 *
 * These tests exist because of a defect no build or lint check could see: the
 * inline `when` that handled Back consumed the press and navigated a workspace
 * home route to its own default, i.e. to itself. The UI looked fine, the app
 * compiled, and Back simply did nothing on five of the six workspaces.
 */
class CommandNavigationTest {

    private val routes = CommandRoute.values().toList()

    @Test
    fun `every route is reachable from its key and unknown keys fall back to overview`() {
        routes.forEach { route ->
            assertEquals(route, CommandRoute.fromKey(route.key))
        }
        assertEquals(CommandRoute.OVERVIEW, CommandRoute.fromKey("does-not-exist"))
        // Keys are what gets persisted into saved instance state, so a
        // duplicate would silently collapse two routes into one on restore.
        assertEquals(routes.size, routes.map { it.key }.distinct().size)
    }

    @Test
    fun `an open navigation drawer absorbs the back press on every route`() {
        routes.forEach { route ->
            assertEquals(
                "drawer open on $route",
                CommandBackAction.CloseNavigation,
                commandBackAction(route, navigationOpen = true)
            )
        }
    }

    @Test
    fun `no back press is ever swallowed`() {
        // The regression: navigating to a route's own workspace default is a
        // no-op, so the press was consumed and nothing happened.
        routes.forEach { route ->
            val action = commandBackAction(route, navigationOpen = false)
            if (action is CommandBackAction.Navigate) {
                assertNotEquals("back on $route navigates to itself", route, action.to)
            }
        }
    }

    @Test
    fun `only the overview arms the exit guard`() {
        routes.forEach { route ->
            val expected = route == CommandRoute.OVERVIEW
            assertEquals(
                "exit guard on $route",
                expected,
                commandBackAction(route, navigationOpen = false) == CommandBackAction.ExitGuard
            )
        }
    }

    @Test
    fun `every non-overview route steps up instead of exiting`() {
        routes.filter { it != CommandRoute.OVERVIEW }.forEach { route ->
            val action = commandBackAction(route, navigationOpen = false)
            assertTrue("$route should navigate up, got $action", action is CommandBackAction.Navigate)
        }
    }

    @Test
    fun `a workspace home steps back to the overview`() {
        CommandWorkspace.values().forEach { workspace ->
            val home = workspaceDefault(workspace)
            if (home != CommandRoute.OVERVIEW) {
                assertEquals(
                    "back from $home",
                    CommandBackAction.Navigate(CommandRoute.OVERVIEW),
                    commandBackAction(home, navigationOpen = false)
                )
            }
        }
    }

    @Test
    fun `a sub-route steps back to its own workspace home first`() {
        routes.forEach { route ->
            val home = workspaceDefault(route.workspace)
            if (route != home && route != CommandRoute.SERVER_DOSSIER) {
                assertEquals(
                    "back from $route",
                    CommandBackAction.Navigate(home),
                    commandBackAction(route, navigationOpen = false)
                )
            }
        }
    }

    @Test
    fun `the server dossier steps back to the fleet even though the fleet is its home`() {
        assertEquals(
            CommandBackAction.Navigate(CommandRoute.FLEET),
            commandBackAction(CommandRoute.SERVER_DOSSIER, navigationOpen = false)
        )
    }

    @Test
    fun `back from any route reaches the overview within two presses`() {
        // Guards against a route chain that could trap the user: home -> default
        // -> overview, so two presses is always enough.
        routes.forEach { start ->
            var route = start
            var presses = 0
            while (route != CommandRoute.OVERVIEW && presses < 3) {
                val action = commandBackAction(route, navigationOpen = false)
                assertTrue("dead end at $route", action is CommandBackAction.Navigate)
                route = (action as CommandBackAction.Navigate).to
                presses++
            }
            assertEquals("$start needed $presses presses", CommandRoute.OVERVIEW, route)
            assertTrue("$start took $presses presses", presses <= 2)
        }
    }
}
