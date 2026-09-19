package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

class ContextualHelpTest {
    @Test fun `every route has specific complete bilingual guidance`() {
        for (language in listOf("fa", "en")) {
            val summaries = mutableSetOf<String>()
            CommandRoute.values().forEach { route ->
                val guide = route.helpContent(language)
                assertTrue("$route summary", guide.summary.length > 50)
                assertTrue("$route steps", guide.steps.size in 3..6)
                assertTrue("$route blank step", guide.steps.all { it.length > 20 })
                assertFalse("$route tip", guide.tip.isNullOrBlank())
                assertFalse("$route warning", guide.warning.isNullOrBlank())
                assertTrue("$route generic summary", summaries.add(guide.summary))
                assertEquals(route.helpContent("fa").steps.size, route.helpContent("en").steps.size)
                assertNotEquals(route.helpContent("fa").summary, route.helpContent("en").summary)
            }
        }
    }

    @Test fun `all menu routes and editor routes are covered`() {
        val menu = CommandWorkspace.values().flatMap(::commandMenuRoutes)
        val routes = menu + listOf(CommandRoute.TUNNELS_EDITOR, CommandRoute.UPTIME_EDITOR,
            CommandRoute.DNS_EDITOR, CommandRoute.NETWORK_TOOLS_EDITOR, CommandRoute.MANAGE_SERVERS)
        routes.forEach { assertTrue(it.helpContent("fa").steps.isNotEmpty()) }
    }

    @Test fun `server panels resolve to the corresponding help without changing navigation`() {
        val root = CommandNavigation.root()
        assertEquals(CommandRoute.FLEET, root.current.helpRoute())
        assertEquals(CommandRoute.SERVER_DOSSIER, root.openServer(7).current.helpRoute())
        assertEquals(CommandRoute.MANAGE_SERVERS, root.editServer(null).current.helpRoute())
        assertEquals(CommandRoute.MANAGE_SERVERS, root.editServer(7).current.helpRoute())
        assertEquals(CommandRoute.RADAR, root.navigate(CommandRoute.RADAR, 7).current.helpRoute())
        assertEquals(CommandRoute.FLEET, root.current.route)
    }

    @Test fun `phone tools do not falsely require an agent and SSH tools distinguish credentials`() {
        listOf(CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR, CommandRoute.CF_SCANNER,
            CommandRoute.REALITY_SNI, CommandRoute.PROXY, CommandRoute.DEVELOPER_LAB,
            CommandRoute.DNS_EDITOR, CommandRoute.NETWORK_TOOLS_EDITOR, CommandRoute.ALERTS).forEach {
            assertNull(it.helpContent("en").prerequisite)
        }
        listOf(CommandRoute.SSH, CommandRoute.SFTP, CommandRoute.BATCH, CommandRoute.SERVICES,
            CommandRoute.SECURITY).forEach { assertTrue(it.helpContent("en").prerequisite!!.contains("SSH password")) }
        listOf(CommandRoute.RADAR, CommandRoute.DOCKER, CommandRoute.BANDWIDTH).forEach {
            assertTrue(it.helpContent("en").prerequisite!!.contains("compatible"))
        }
    }

    @Test fun `uptime explains purpose and acknowledges current runner and measurement limits`() {
        val uptime = CommandRoute.UPTIME.helpContent("en")
        assertTrue(uptime.summary.contains("Does my site or service respond?"))
        assertTrue(uptime.steps.joinToString().contains("no background-engine start control"))
        assertTrue(uptime.tip!!.contains("last 30 checks"))
        assertTrue(uptime.warning!!.contains("not ICMP"))
        assertTrue(uptime.warning!!.contains("background restrictions"))
        assertFalse(uptime.steps.joinToString().contains("Set interval and timeout"))
    }

    @Test fun `scanner instructions match bundled catalogs and disclose candidates`() {
        val cf = CommandRoute.CF_SCANNER.helpContent("en")
        val sni = CommandRoute.REALITY_SNI.helpContent("en")
        assertTrue(cf.steps.joinToString().contains("${CloudflareRanges.V4.size} official"))
        assertTrue(sni.steps.joinToString().contains("${ScannerCatalog.domains(ScannerCatalog.Group.ALL).size} domains"))
        assertTrue(sni.tip!!.contains("${ScannerCatalog.MAX_SNI_TARGETS} targets"))
        assertTrue(cf.warning!!.contains("not guaranteed"))
        assertTrue(sni.warning!!.contains("not guaranteed"))
    }

    @Test fun `guides do not promise unsupported single port proxy or backup workflows`() {
        assertTrue(CommandRoute.SINGLE_PORT.helpContent("en").tip!!.contains("not a single-port connectivity test"))
        assertTrue(CommandRoute.PROXY.helpContent("en").summary.contains("does not connect a phone VPN"))
        assertTrue(CommandRoute.BACKUP.helpContent("en").tip!!.contains("not a file picker"))
        assertTrue(CommandRoute.DEVELOPER_LAB.helpContent("en").warning!!.contains("does not verify its signature"))
    }

    @Test fun `installation commands are static bilingual and limited to server setup`() {
        CommandRoute.values().forEach { route ->
            val commands = route.helpContent("en").commands
            if (route != CommandRoute.MANAGE_SERVERS) assertTrue(commands.isEmpty())
            else {
                assertEquals(2, commands.size)
                assertEquals(commands.map { it.value }, route.helpContent("fa").commands.map { it.value })
                assertTrue(commands.first().value.startsWith("curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/"))
                assertFalse(commands.any { it.value.contains("ufw allow") })
            }
        }
        assertEquals(CommandRoute.FLEET.helpContent("en"), CommandRoute.FLEET.helpContent("unknown"))
    }
}
