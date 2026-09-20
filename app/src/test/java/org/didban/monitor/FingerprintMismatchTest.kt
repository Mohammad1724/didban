package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

/** Pin-rotation UX: deterministic detection, localized explanation, state round-trip. No network. */
class FingerprintMismatchTest {
    private val pinned = "ab".repeat(32)
    private val observed = "cd".repeat(32)

    private fun server(fingerprint: String, useTls: Boolean = true) = ServerConfig(
        id = 1, name = "s", host = "example.com", port = 8686,
        token = "tok", adminToken = "adm", useTls = useTls, fingerprint = fingerprint
    )

    @Test
    fun `pin rejection is detected when observed differs from pinned`() {
        val mismatch = ApiClient().pinRejectionOrNull(server(pinned), observed)
        assertNotNull(mismatch)
        assertEquals(pinned, mismatch!!.expected)
        assertEquals(observed, mismatch.observed)
    }

    @Test
    fun `pin rejection is null when fingerprints match`() {
        assertNull(ApiClient().pinRejectionOrNull(server(pinned), pinned))
    }

    @Test
    fun `pin rejection is null without a pin or without an observation`() {
        val api = ApiClient()
        assertNull(api.pinRejectionOrNull(server(""), observed))
        assertNull(api.pinRejectionOrNull(server(pinned), null))
        assertNull(api.pinRejectionOrNull(server(pinned), ""))
    }

    @Test
    fun `pin rejection is null for plain http`() {
        assertNull(ApiClient().pinRejectionOrNull(server(pinned, useTls = false), observed))
    }

    @Test
    fun `pin rejection normalizes the pin before comparing`() {
        // Uppercase + colon-separated input that denotes the same pin must not alarm.
        val decorated = pinned.uppercase().chunked(2).joinToString(":")
        assertNull(ApiClient().pinRejectionOrNull(server(decorated), pinned))
    }

    @Test
    fun `tool failure explains mismatch in both languages with both prefixes`() {
        val failure = FingerprintMismatchException(pinned, observed)
        val fa = describeAgentToolFailure(failure, CommandCopyFa, server(pinned), false)
        assertTrue("fa must show the expected prefix", fa.contains(pinned.take(12)))
        assertTrue("fa must show the observed prefix", fa.contains(observed.take(12)))
        assertFalse("fa must not leak the raw English alert", fa.contains("Certificate fingerprint mismatch"))
        val en = describeAgentToolFailure(failure, CommandCopyEn, server(pinned), true)
        assertTrue("en must show the expected prefix", en.contains(pinned.take(12)))
        assertTrue("en must show the observed prefix", en.contains(observed.take(12)))
    }

    @Test
    fun `repo state round-trips the mismatch pair`() {
        try {
            Repo.set(7001, error = "error", fingerprintMismatch = FingerprintMismatch("aaa", "bbb"))
            assertEquals(FingerprintMismatch("aaa", "bbb"), Repo.states.value[7001]?.fingerprintMismatch)
            Repo.set(7002, error = "error")
            assertNull(Repo.states.value[7002]?.fingerprintMismatch)
        } finally {
            Repo.remove(7001)
            Repo.remove(7002)
        }
    }
}
