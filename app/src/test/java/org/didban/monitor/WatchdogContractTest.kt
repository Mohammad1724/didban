package org.didban.monitor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 · 4-A — watchdog contract tests (JVM).
 *
 * Covers the three pure pieces of the watchdog feature:
 *  1. parseWatchdog — the /api/tunnel/watchdog payload contract,
 *  2. worstWatchdogState — cross-role severity aggregation,
 *  3. the per-core listen-port contract every generator declares
 *     (the port the agent's watchdog will actually probe).
 */
class WatchdogContractTest {

    // ── 1. payload parsing ──────────────────────────────────────────────────

    @Test
    fun parseWatchdogFull() {
        val o = JSONObject(
            """{"enabled":true,"interval_ms":30000,"tunnels":[
                {"id":"42","name":"t","core":"BACKPACK","role":"iran","state":"degraded",
                 "active":true,"port":8443,"port_ok":false,"n_restarts":2,"uptime_sec":120,
                 "changed_at":"2026-09-14T12:00:00Z","detail":"port closed"}
            ],"transitions":[]}"""
        )
        val wd = TunnelEngine.parseWatchdog(o)
        assertTrue(wd.enabled)
        assertEquals(30000L, wd.intervalMs)
        assertEquals(1, wd.tunnels.size)
        val s = wd.tunnels[0]
        assertEquals("42", s.id)
        assertEquals("t", s.name)
        assertEquals("BACKPACK", s.core)
        assertEquals("iran", s.role)
        assertEquals("degraded", s.state)
        assertTrue(s.active)
        assertEquals(8443, s.port)
        assertFalse(s.portOk)
        assertEquals(2, s.nRestarts)
        assertEquals(120, s.uptimeSec)
        assertEquals(
            java.time.Instant.parse("2026-09-14T12:00:00Z").toEpochMilli(),
            s.changedAtMs
        )
        assertEquals("port closed", s.detail)
    }

    @Test
    fun parseWatchdogDisabledShape() {
        val wd = TunnelEngine.parseWatchdog(JSONObject("""{"enabled":false}"""))
        assertFalse(wd.enabled)
        assertTrue(wd.tunnels.isEmpty())
        assertEquals(0L, wd.intervalMs)
    }

    @Test
    fun parseWatchdogBlankOrZeroChangedAt() {
        val blank = TunnelEngine.parseWatchdog(
            JSONObject("""{"enabled":true,"tunnels":[{"id":"1","state":"unknown"}]}""")
        ).tunnels[0].changedAtMs
        assertEquals(0L, blank)

        val zero = TunnelEngine.parseWatchdog(
            JSONObject("""{"enabled":true,"tunnels":[{"id":"1","state":"unknown","changed_at":"0001-01-01T00:00:00Z"}]}""")
        ).tunnels[0].changedAtMs
        assertEquals(0L, zero)
    }

    // ── 2. cross-role severity aggregation ─────────────────────────────────

    @Test
    fun worstStateUpOnly() {
        assertEquals("up", TunnelEngine.worstWatchdogState(listOf("up", "up")))
    }

    @Test
    fun worstStateDownBeatsUp() {
        assertEquals("down", TunnelEngine.worstWatchdogState(listOf("up", "down")))
    }

    @Test
    fun worstStateDownNotHiddenByUnknown() {
        // A real "down" must never be masked by an unobserved side.
        assertEquals("down", TunnelEngine.worstWatchdogState(listOf("unknown", "down")))
        assertEquals("down", TunnelEngine.worstWatchdogState(listOf("down", "unknown")))
    }

    @Test
    fun worstStateUnknownHidesUp() {
        // Honest: we cannot vouch for a side we have not observed yet.
        assertEquals("unknown", TunnelEngine.worstWatchdogState(listOf("up", "unknown")))
    }

    @Test
    fun worstStateCrashLoopBeatsDown() {
        assertEquals("crash_loop", TunnelEngine.worstWatchdogState(listOf("down", "crash_loop")))
    }

    @Test
    fun worstStateEmpty() {
        assertEquals("unknown", TunnelEngine.worstWatchdogState(emptyList()))
    }

    // ── 3. per-core listen-port contract ────────────────────────────────────

    private fun baseCfg(core: TunnelCore): TunnelConfig =
        // hosts set: H19 requires a concrete foreign host for IPTables (DNAT).
        TunnelConfig(id = 7, name = "port-contract", core = core,
            iranHost = "203.0.113.10", foreignHost = "198.51.100.20")

    @Test
    fun listenPortsPerCore() {
        // (core, expected iran-port, expected foreign-port). 3080 = the
        // TunnelConfig default corePort; 443 = the default first forward
        // port (spoof-tunnel's client role listens on it).
        val expected: Map<TunnelCore, Pair<Int, Int>> = mapOf(
            TunnelCore.BACKPACK to (3080 to 0), // server binds corePort
            TunnelCore.PAQET to (0 to 3080), // kharej server listens
            TunnelCore.NARNIA to (0 to 0), // docker host-net: no fixed port
            TunnelCore.SPOOF_TUNNEL to (443 to 3080),
            TunnelCore.BACKHAUL to (3080 to 0),
            TunnelCore.RATHOLE to (3080 to 0),
            TunnelCore.GOST to (3080 to 0),
            TunnelCore.CHISEL to (0 to 3080), // kharej chisel server listens
            TunnelCore.FRP to (0 to 3080), // kharej frps binds
            TunnelCore.IPTABLES to (0 to 0) // no process at all
        )
        for ((core, ports) in expected) {
            val code = TunnelEngine.generateCode(baseCfg(core))
            assertEquals("core $core: iran listen port", ports.first, code.listenPortIran)
            assertEquals("core $core: foreign listen port", ports.second, code.listenPortForeign)
        }
    }
}
