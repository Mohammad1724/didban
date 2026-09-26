package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckHostTargetTest {
    @Test fun `domains and IPv4 addresses are accepted without a server`() {
        assertEquals("example.com", normalizeCheckHostTarget("https://example.com/path", "ping", "443"))
        assertEquals("1.1.1.1", normalizeCheckHostTarget(" 1.1.1.1 ", "dns", "443"))
    }

    @Test fun `TCP appends a separate port and preserves an embedded port`() {
        assertEquals("example.com:443", normalizeCheckHostTarget("example.com", "tcp", "443"))
        assertEquals("example.com:8443", normalizeCheckHostTarget("example.com:8443", "tcp", "443"))
        assertEquals("[2001:db8::1]:443", normalizeCheckHostTarget("2001:db8::1", "tcp", "443"))
    }

    @Test fun `Info accepts domains and raw IPv6 without inventing a port`() {
        assertEquals("example.com", normalizeCheckHostTarget("https://example.com/path", "info", "443"))
        assertEquals("2001:db8::1", normalizeCheckHostTarget("2001:db8::1", "info", "443"))
    }

    @Test fun `filter probes use a deterministic Iranian first cohort and stable fill`() {
        val inventory = mapOf(
            "ir4.node.check-host.net" to "ir",
            "nl2.node.check-host.net" to "nl",
            "ir1.node.check-host.net" to "ir",
            "de1.node.check-host.net" to "de",
            "ir2.node.check-host.net" to "ir",
            "nl1.node.check-host.net" to "nl",
            "ir3.node.check-host.net" to "ir",
            "us1.node.check-host.net" to "us"
        )
        assertEquals(
            listOf(
                "ir1.node.check-host.net",
                "ir2.node.check-host.net",
                "ir3.node.check-host.net",
                "ir4.node.check-host.net",
                "nl1.node.check-host.net",
                "nl2.node.check-host.net"
            ),
            stableCheckHostNodeKeys(inventory, 6)
        )
    }

    @Test fun `empty malformed and invalid TCP targets are rejected`() {
        assertNull(normalizeCheckHostTarget("", "ping", "443"))
        assertEquals("example.com", normalizeCheckHostTarget("example.com/path?x=1", "ping", "443"))
        assertNull(normalizeCheckHostTarget("example.com", "tcp", "0"))
        assertNull(normalizeCheckHostTarget("example.com", "tcp", "70000"))
        assertNull(normalizeCheckHostTarget("user@example.com", "ping", "443"))
    }
}
