package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputParsersTest {

    // ── fail2ban jails ──────────────────────────────────────────────────────

    private val fail2banStatusSample = """
        Status:
        |- Number of jail:	2
        `- Jail list:	sshd	nginx-http-auth
    """.trimIndent()

    @Test
    fun `parses jail list`() {
        assertEquals(listOf("sshd", "nginx-http-auth"), OutputParsers.fail2banJails(fail2banStatusSample))
    }

    @Test
    fun `jail list with no jails is empty`() {
        assertEquals(emptyList<String>(), OutputParsers.fail2banJails("Status:\n|- Number of jail: 0\n`- Jail list:"))
    }

    @Test
    fun `garbage jail names are filtered out`() {
        val out = "`- Jail list: sshd;reboot  ok x/y  good-jail_2"
        assertEquals(listOf("ok", "good-jail_2"), OutputParsers.fail2banJails(out))
    }

    // ── fail2ban banned ips ─────────────────────────────────────────────────

    private val jailStatusSample = """
        Status for the jail: sshd
        |- Filter
        |  |- Currently failed:	0
        |  |- Total failed:	42
        `- Actions
           |- Currently banned:	3
           |- Total banned:	57
           |- Banned IP list:	194.26.29.112 45.154.255.89 185.220.101.5
    """.trimIndent()

    @Test
    fun `parses banned ip list`() {
        assertEquals(
            listOf("194.26.29.112", "45.154.255.89", "185.220.101.5"),
            OutputParsers.fail2banBannedIps(jailStatusSample)
        )
    }

    @Test
    fun `empty banned list`() {
        val out = jailStatusSample.replace("Banned IP list:	194.26.29.112 45.154.255.89 185.220.101.5", "Banned IP list:")
        assertEquals(emptyList<String>(), OutputParsers.fail2banBannedIps(out))
    }

    @Test
    fun `invalid entries in banned list are dropped`() {
        val out = "`- Banned IP list: 1.2.3.4 999.1.1.1 not-an-ip 5.6.7.8"
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), OutputParsers.fail2banBannedIps(out))
    }

    // ── systemd units ───────────────────────────────────────────────────────

    private val systemctlSample = """
        dbus.service          loaded active running D-Bus System Message Bus
        ssh.service           loaded active running OpenBSD Secure Shell server
        didban-agent.service  loaded active running Didban Agent
        nginx.service         loaded failed failed A high performance web server
        systemd-resolved.service loaded active running Network Name Resolution
    """.trimIndent()

    @Test
    fun `parses unit rows with description`() {
        val units = OutputParsers.systemdUnits(systemctlSample)
        assertEquals(5, units.size)
        val dbus = units[0]
        assertEquals("dbus.service", dbus.unit)
        assertEquals("active", dbus.active)
        assertEquals("running", dbus.sub)
        assertEquals("D-Bus System Message Bus", dbus.description)
    }

    @Test
    fun `failed units keep their state`() {
        val units = OutputParsers.systemdUnits(systemctlSample)
        val nginx = units.first { it.unit == "nginx.service" }
        assertEquals("failed", nginx.active)
        assertEquals("failed", nginx.sub)
    }

    @Test
    fun `short lines without state are skipped`() {
        val units = OutputParsers.systemdUnits("malformed line\nssh.service loaded active running OpenSSH")
        assertEquals(1, units.size)
        assertEquals("ssh.service", units[0].unit)
    }

    @Test
    fun `at most 40 units are returned`() {
        val lines = (1..60).joinToString("\n") { "unit$it.service loaded active running Desc $it" }
        assertEquals(40, OutputParsers.systemdUnits(lines).size)
    }
}
