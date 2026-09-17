package org.didban.monitor

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M17: discovered tunnels must never carry a fabricated credential.
 *
 * The old discovery path stamped every auto-detected tunnel with the literal
 * "auto-detected", which [TunnelSecrets.ensureToken] treated as a real token —
 * so a deploy silently used a secret the live process does not know. Now:
 * discovery never imports credentials from process or container metadata;
 * the token stays blank and the deploy gate blocks
 * generation/deploy until the user enters the actual token.
 */
class TunnelDiscoveryTest {

    // ── "auto-detected" placeholder migration ────────────────────────────────

    @Test
    fun `legacy auto-detected placeholder is healed to blank + discovered`() {
        val o = JSONObject()
        o.put("id", 7)
        o.put("core", "BACKPACK")
        o.put("token", "auto-detected")
        val c = TunnelConfig.fromJson(o)
        assertEquals("", c.token)
        assertTrue(c.discovered)
    }

    @Test
    fun `discovered flag round-trips through json`() {
        val c = TunnelConfig(id = 9, name = "x", core = TunnelCore.CHISEL, token = "", discovered = true)
        val c2 = TunnelConfig.fromJson(c.toJson())
        assertTrue(c2.discovered)
        assertEquals("", c2.token)
    }

    @Test
    fun `manual tunnels are not marked discovered`() {
        val c = TunnelConfig(id = 9, name = "x", core = TunnelCore.CHISEL, token = "abc123")
        val c2 = TunnelConfig.fromJson(c.toJson())
        assertFalse(c2.discovered)
        assertEquals("abc123", c2.token)
    }

    // ── deploy gate ──────────────────────────────────────────────────────────

    private fun cfg(core: TunnelCore, token: String, discovered: Boolean): TunnelConfig = TunnelConfig(
        id = 42,
        name = "gate-test",
        core = core,
        iranHost = "1.2.3.4",
        iranPort = 443,
        foreignHost = "5.6.7.8",
        foreignPort = 8443,
        token = token,
        discovered = discovered
    )

    @Test
    fun `discovered token-owning tunnel with blank token is blocked`() {
        val tokenized = listOf(
            TunnelCore.BACKPACK,
            TunnelCore.PAQET,
            TunnelCore.SPOOF_TUNNEL,
            TunnelCore.BACKHAUL,
            TunnelCore.RATHOLE,
            TunnelCore.CHISEL,
            TunnelCore.FRP,
            TunnelCore.NARNIA
        )
        for (core in tokenized) {
            val c = cfg(core, "", discovered = true)
            val errors = TunnelEngine.validateForDeploy(c)
            assertTrue("[$core] must be blocked", errors.any { it.contains("توکن") })
            assertThrows("[$core] generateCode must throw", IllegalArgumentException::class.java) {
                TunnelEngine.generateCode(c)
            }
        }
    }

    @Test
    fun `discovered tokenless cores are not blocked by the gate`() {
        for (core in listOf(TunnelCore.GOST, TunnelCore.IPTABLES)) {
            val errors = TunnelEngine.validateForDeploy(cfg(core, "", discovered = true))
            assertFalse("[$core] must pass the token gate", errors.any { it.contains("توکن") })
        }
    }

    @Test
    fun `discovered tunnel with a real token deploys with it`() {
        val c = cfg(TunnelCore.CHISEL, "RealSecret123", discovered = true)
        assertTrue(TunnelEngine.validateForDeploy(c).isEmpty())
        val g = TunnelEngine.generateCode(c)
        assertTrue("generated config must use the real token", g.foreignConfig.contains("RealSecret123"))
        assertTrue(g.iranConfig.contains("RealSecret123"))
    }

    @Test
    fun `manual tunnel with blank token still gets a minted token`() {
        val c = cfg(TunnelCore.BACKPACK, "", discovered = false)
        assertTrue(TunnelEngine.validateForDeploy(c).isEmpty())
        TunnelEngine.generateCode(c)
        assertTrue("token must be minted into the model", c.token.length >= 24)
    }

    @Test
    fun `model ignores legacy container environment credentials`() {
        val container = JSONObject()
            .put("id", "abc123def456789")
            .put("name", "didban-tunnel-42")
            .put("image", "stormotron/narnia:0.0.3")
            .put("state", "running")
            .put("status", "Up 2 hours")
            .put("created", 1700000000L)
            // Older agents may still return this field. The current model has
            // no destination for it and must parse topology without secrets.
            .put("env", JSONObject().put("PASSWORD", "must-not-enter-model"))
        val payload = JSONObject()
            .put("installed", true)
            .put("containers", org.json.JSONArray().put(container))

        val parsed = JsonParse.docker(payload)
        assertEquals(1, parsed.containers.size)
        assertEquals("didban-tunnel-42", parsed.containers[0].name)
        assertEquals("stormotron/narnia:0.0.3", parsed.containers[0].image)
    }

}
