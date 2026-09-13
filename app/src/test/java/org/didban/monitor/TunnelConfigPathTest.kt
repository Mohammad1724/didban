package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-tunnel config paths (Item 26).
 *
 * Before: every core wrote its config to a FIXED path (/etc/backpack/server.toml,
 * /etc/frp/frpc.toml, ...). Two tunnels of the SAME core on one host therefore
 * overwrote each other's config, while the deployed unit kept reading the file
 * the OTHER tunnel last wrote.
 *
 * Now: the runtime config lives in a per-tunnel directory inside the agent's
 * config sandbox (/etc/didban/tunnels/<id>/), the install script and the unit
 * both use that path, and the agent's delete removes the real file.
 */
class TunnelConfigPathTest {

    private fun cfgFor(core: TunnelCore, id: Long = 42): TunnelConfig = TunnelConfig(
        id = id,
        name = "path-test",
        core = core,
        iranHost = "1.2.3.4",
        iranPort = 443,
        foreignHost = "5.6.7.8",
        foreignPort = 8443,
        token = "tok-path-test"
    )

    private val fileCores = listOf(
        TunnelCore.BACKPACK,
        TunnelCore.PAQET,
        TunnelCore.SPOOF_TUNNEL,
        TunnelCore.BACKHAUL,
        TunnelCore.RATHOLE,
        TunnelCore.FRP
    )

    private val oldFixedDirs = mapOf(
        TunnelCore.BACKPACK to "/etc/backpack/",
        TunnelCore.PAQET to "/etc/paqet/",
        TunnelCore.SPOOF_TUNNEL to "/etc/spoof-tunnel/",
        TunnelCore.BACKHAUL to "/etc/backhaul/",
        TunnelCore.RATHOLE to "/etc/rathole/",
        TunnelCore.FRP to "/etc/frp/"
    )

    private fun occurrences(haystack: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(haystack).count()

    @Test
    fun `config-file cores write and start from the per-tunnel path`() {
        for (core in fileCores) {
            val g = TunnelEngine.generateCode(cfgFor(core, 42))
            val dir = "/etc/didban/tunnels/42"
            val ctx = "[$core]"
            assertTrue("$ctx declares a per-tunnel iran path", g.iranConfigPath.startsWith(dir + "/"))
            assertTrue("$ctx declares a per-tunnel foreign path", g.foreignConfigPath.startsWith(dir + "/"))

            for ((label, script, path) in listOf(
                Triple("iran", g.iranInstallCommand, g.iranConfigPath),
                Triple("foreign", g.foreignInstallCommand, g.foreignConfigPath)
            )) {
                val c = "$ctx/$label"
                assertTrue("$c creates the per-tunnel dir", script.contains("mkdir -p $dir"))
                // Written by the script AND referenced by the unit's ExecStart.
                assertTrue("$c references the runtime path (write + ExecStart)", occurrences(script, path) >= 2)
                val oldDir = oldFixedDirs[core]!!
                assertFalse("$c still uses the old fixed dir: $oldDir", script.contains(oldDir))
            }
        }
    }

    @Test
    fun `same-core tunnels on one host never share a config path`() {
        for (core in fileCores) {
            val g1 = TunnelEngine.generateCode(cfgFor(core, 11))
            val g2 = TunnelEngine.generateCode(cfgFor(core, 12))
            assertEquals("[$core] iran path is per-tunnel", "/etc/didban/tunnels/11", g1.iranConfigPath.substringBeforeLast('/'))
            assertEquals("[$core] foreign path is per-tunnel", "/etc/didban/tunnels/12", g2.foreignConfigPath.substringBeforeLast('/'))
            for (g in listOf(g1, g2)) {
                for (script in listOf(g.iranInstallCommand, g.foreignInstallCommand)) {
                    // Tunnel 2's scripts must not touch tunnel 1's files and vice versa.
                    assertFalse("[$core] cross-tunnel dir leak", script.contains("/etc/didban/tunnels/1${if (g === g1) 2 else 1}"))
                }
            }
        }
    }

    @Test
    fun `cores without a runtime config file declare none`() {
        for (core in listOf(TunnelCore.GOST, TunnelCore.CHISEL, TunnelCore.NARNIA, TunnelCore.IPTABLES)) {
            val g = TunnelEngine.generateCode(cfgFor(core))
            assertTrue("[$core] no iran config file", g.iranConfigPath.isEmpty())
            assertTrue("[$core] no foreign config file", g.foreignConfigPath.isEmpty())
        }
    }
}
