package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSecretsTest {

    // ── generateRandomToken ─────────────────────────────────────────────────

    @Test
    fun `token has requested length and safe charset`() {
        val t = TunnelSecrets.generateRandomToken(24)
        assertEquals(24, t.length)
        assertTrue(t.all { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" })
    }

    @Test
    fun `tokens are unique across draws`() {
        val set = (1..200).associate { TunnelSecrets.generateRandomToken(24) to it }.keys.toSet()
        assertEquals(200, set.size)
    }

    // ── deriveKey ───────────────────────────────────────────────────────────

    @Test
    fun `deriveKey is deterministic`() {
        val a = TunnelSecrets.deriveKey("seed-token", "server")
        val b = TunnelSecrets.deriveKey("seed-token", "server")
        assertEquals(a, b)
    }

    @Test
    fun `deriveKey length and format`() {
        val k = TunnelSecrets.deriveKey("seed-token", "server")
        assertEquals(32, k.length)
        assertTrue(k.all { it in "0123456789abcdef" })
    }

    @Test
    fun `different domains or seeds give different keys`() {
        assertNotEquals(TunnelSecrets.deriveKey("s", "server"), TunnelSecrets.deriveKey("s", "client"))
        assertNotEquals(TunnelSecrets.deriveKey("s1", "server"), TunnelSecrets.deriveKey("s2", "server"))
    }

    // ── ensureToken (the H3 core) ───────────────────────────────────────────

    @Test
    fun `blank token is generated and reported once`() {
        var reported: String? = null
        val t1 = TunnelSecrets.ensureToken("") { reported = it }
        assertEquals(reported, t1)
        assertEquals(24, t1.length)
    }

    @Test
    fun `stored token is never regenerated`() {
        var calls = 0
        val stored = "user-chosen-secret"
        val r1 = TunnelSecrets.ensureToken(stored) { calls++ }
        val r2 = TunnelSecrets.ensureToken(stored) { calls++ }
        assertEquals(stored, r1)
        assertEquals(stored, r2)
        assertEquals(0, calls) // callback must not fire
    }

    // ── H3 scenario: redeploy reproduces the SAME pair ──────────────────────

    @Test
    fun `spoof pair is stable across code regeneration`() {
        // First apply: token blank -> generated & persisted
        var stored = ""
        val seed = TunnelSecrets.ensureToken(stored) { stored = it }
        val srv1 = TunnelSecrets.deriveKey(seed, "server")
        val cli1 = TunnelSecrets.deriveKey(seed, "client")

        // Redeploy (new generateCode call, same persisted token):
        val seed2 = TunnelSecrets.ensureToken(stored) { fail("token must not be regenerated") }
        val srv2 = TunnelSecrets.deriveKey(seed2, "server")
        val cli2 = TunnelSecrets.deriveKey(seed2, "client")

        assertEquals(seed, seed2)
        assertEquals(srv1, srv2)
        assertEquals(cli1, cli2)
        assertNotEquals(srv1, cli1)
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)
}
