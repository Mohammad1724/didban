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

    @Test fun `empty malformed and invalid TCP targets are rejected`() {
        assertNull(normalizeCheckHostTarget("", "ping", "443"))
        assertEquals("example.com", normalizeCheckHostTarget("example.com/path?x=1", "ping", "443"))
        assertNull(normalizeCheckHostTarget("example.com", "tcp", "0"))
        assertNull(normalizeCheckHostTarget("example.com", "tcp", "70000"))
        assertNull(normalizeCheckHostTarget("user@example.com", "ping", "443"))
    }
}
