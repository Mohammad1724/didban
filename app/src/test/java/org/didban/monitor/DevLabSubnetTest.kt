package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Pure-JVM tests for the flexible subnet input: IP/prefix, bare IP and domains. */
class DevLabSubnetTest {

    private fun codeOf(block: () -> Unit): String {
        return try {
            block()
            fail("expected SubnetInputException")
            ""
        } catch (e: DevLabTools.SubnetInputException) {
            e.code
        }
    }

    @Test fun `parses ip with prefix`() {
        val request = DevLabTools.parseSubnetRequest("192.168.1.10/24")
        assertEquals("192.168.1.10", request.host)
        assertEquals(24, request.prefix)
        assertFalse(request.prefixAssumed)
    }

    @Test fun `bare ip assumes slash 24`() {
        val request = DevLabTools.parseSubnetRequest("10.0.0.5")
        assertEquals("10.0.0.5", request.host)
        assertEquals(24, request.prefix)
        assertTrue(request.prefixAssumed)
    }

    @Test fun `domain keeps host and may carry a prefix`() {
        val bare = DevLabTools.parseSubnetRequest("example.com")
        assertEquals("example.com", bare.host)
        assertEquals(24, bare.prefix)
        assertTrue(bare.prefixAssumed)
        val withPrefix = DevLabTools.parseSubnetRequest("example.com/16")
        assertEquals("example.com", withPrefix.host)
        assertEquals(16, withPrefix.prefix)
        assertFalse(withPrefix.prefixAssumed)
    }

    @Test fun `tolerates pasted urls ports and trailing slashes`() {
        assertEquals("example.com", DevLabTools.parseSubnetRequest("https://example.com/path").host)
        assertEquals("example.com", DevLabTools.parseSubnetRequest("example.com:443").host)
        assertEquals("192.168.1.1", DevLabTools.parseSubnetRequest("192.168.1.1/").host)
        assertEquals(24, DevLabTools.parseSubnetRequest("192.168.1.1/").prefix)
    }

    @Test fun `rejects garbage prefixes and ipv6 with codes`() {
        assertEquals("format", codeOf { DevLabTools.parseSubnetRequest("") })
        assertEquals("format", codeOf { DevLabTools.parseSubnetRequest("not a host!!") })
        assertEquals("format", codeOf { DevLabTools.parseSubnetRequest("192.168.1.1/24/extra") })
        assertEquals("prefix", codeOf { DevLabTools.parseSubnetRequest("192.168.1.1/99") })
        assertEquals("prefix", codeOf { DevLabTools.parseSubnetRequest("192.168.1.1/abc") })
        assertEquals("ipv6", codeOf { DevLabTools.parseSubnetRequest("2001:db8::1") })
    }

    @Test fun `calculates classic slash 24 ranges`() {
        val result = DevLabTools.calculateSubnet("192.168.1.10/24")
        assertEquals("192.168.1.0", result.network)
        assertEquals("192.168.1.255", result.broadcast)
        assertEquals("192.168.1.1", result.firstHost)
        assertEquals("192.168.1.254", result.lastHost)
        assertEquals("255.255.255.0", result.netmask)
        assertEquals("0.0.0.255", result.wildcard)
        assertEquals(254L, result.usableHosts)
        assertEquals(256L, result.totalHosts)
    }

    @Test fun `handles slash 31 and slash 32 edges`() {
        assertEquals(2L, DevLabTools.calculateSubnet("10.0.0.0/31").usableHosts)
        val single = DevLabTools.calculateSubnet("10.0.0.7/32")
        assertEquals("10.0.0.7", single.network)
        assertEquals(1L, single.usableHosts)
    }

    @Test fun `flags bad octets as ip errors`() {
        assertEquals("ip", codeOf { DevLabTools.calculateSubnet("999.1.1.1/24") })
        assertEquals("ip", codeOf { DevLabTools.calculateSubnet("a.b.c.d/24") })
    }

    @Test fun `passes ipv4 literals through without dns`() {
        assertEquals("1.2.3.4", DevLabTools.resolveIpv4("1.2.3.4"))
        assertTrue(DevLabTools.looksLikeIpv4("1.2.3.4"))
        assertFalse(DevLabTools.looksLikeIpv4("example.com"))
    }

    @Test fun `resolves localhost and fails reserved names`() {
        assertEquals("127.0.0.1", DevLabTools.resolveIpv4("localhost"))
        assertEquals("dns", codeOf { DevLabTools.resolveIpv4("invalid.invalid") })
    }
}
