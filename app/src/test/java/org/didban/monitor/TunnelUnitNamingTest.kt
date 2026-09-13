package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit-name standardization regression (follow-up of H18/H19).
 *
 * The agent queries and controls `didban-tunnel-<id>` for every tunnel, so
 * every generated install script must create a unit with EXACTLY that name —
 * previously each core used a fixed name (gost.service, backpack-server...),
 * which made post-deploy status wrong and start/stop/delete no-ops.
 */
class TunnelUnitNamingTest {

    private fun cfgFor(core: TunnelCore): TunnelConfig = TunnelConfig(
        id = 42,
        name = "unit-test",
        core = core,
        iranHost = "1.2.3.4",
        iranPort = 443,
        foreignHost = "5.6.7.8",
        foreignPort = 8443,
        token = "tok-unit-test"
    )

    private val allCores = listOf(
        TunnelCore.BACKPACK,
        TunnelCore.PAQET,
        TunnelCore.NARNIA,
        TunnelCore.SPOOF_TUNNEL,
        TunnelCore.BACKHAUL,
        TunnelCore.RATHOLE,
        TunnelCore.GOST,
        TunnelCore.CHISEL,
        TunnelCore.FRP,
        TunnelCore.IPTABLES
    )

    @Test
    fun `every core installs a unit named didban-tunnel-id`() {
        for (core in allCores) {
            val g = TunnelEngine.generateCode(cfgFor(core))
            for (label in listOf("iran", "foreign")) {
                val script = if (label == "iran") g.iranInstallCommand else g.foreignInstallCommand
                val ctx = "[$core/$label]"
                if (!script.contains("systemd")) continue // e.g. "# No setup required"
                if (script.contains("UNIT=didban-tunnel-42")) {
                    // Narnia/IPTables style: unit name in a shell variable.
                    assertTrue("$ctx references the agent unit", script.contains("/etc/systemd/system/\$UNIT.service"))
                    assertTrue("$ctx enables the agent unit", script.contains("enable \$UNIT"))
                    assertTrue("$ctx status of the agent unit", script.contains("status \$UNIT --no-pager"))
                } else {
                    // Inline style: unit name written directly in the script.
                    assertTrue("$ctx writes the agent unit file", script.contains("/etc/systemd/system/didban-tunnel-42.service"))
                    assertTrue("$ctx enables the agent unit", script.contains("enable --now didban-tunnel-42"))
                    assertTrue("$ctx status of the agent unit", script.contains("status didban-tunnel-42 --no-pager"))
                }
                // No fixed-name unit remnants.
                val fixed = Regex("/etc/systemd/system/(?!didban-tunnel-)[a-z0-9-]+\\.service")
                val leftover = fixed.findAll(script).toList()
                assertTrue("$ctx leftover fixed unit: $leftover", leftover.isEmpty())
                val fixedEnable = Regex("enable --now (?!didban-tunnel-)[a-z0-9-]+")
                val leftoverE = fixedEnable.findAll(script).toList()
                assertTrue("$ctx leftover fixed enable: $leftoverE", leftoverE.isEmpty())
            }
        }
    }

    @Test
    fun `every generated install script passes bash -n`() {
        for (core in allCores) {
            val g = TunnelEngine.generateCode(cfgFor(core))
            for ((label, script) in mapOf("iran" to g.iranInstallCommand, "foreign" to g.foreignInstallCommand)) {
                if (!script.trimStart().startsWith("#") && script.contains("\n")) {
                    val dir = Files.createTempDirectory("didban-unit-naming").toFile()
                    try {
                        val f = File(dir, "install.sh")
                        f.writeText(script)
                        val p = ProcessBuilder("bash", "-n", f.absolutePath).redirectErrorStream(true).start()
                        val out = p.inputStream.bufferedReader().readText()
                        assertEquals("bash -n [$core/$label] failed: $out", 0, p.waitFor())
                    } finally {
                        dir.deleteRecursively()
                    }
                }
            }
        }
    }
}
