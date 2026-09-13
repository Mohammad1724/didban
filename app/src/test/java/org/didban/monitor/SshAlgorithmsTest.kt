package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [SshAlgorithms] (H10) — the shared modern SSH whitelist
 * (JVM).
 */
class SshAlgorithmsTest {

    @Test
    fun noWhitelistContainsAForgottenWeakAlgorithm() {
        // the guard itself: any future edit that reintroduces a weak name
        // fails here.
        SshAlgorithms.assertNoWeakAlgorithms()
    }

    @Test
    fun everyForbiddenAlgorithmIsAbsentFromAllWhitelists() {
        val offered = (SshAlgorithms.KEX + SshAlgorithms.HOST_KEY + SshAlgorithms.CIPHER).toSet()
        val leaks = offered.intersect(SshAlgorithms.FORBIDDEN)
        assertTrue("weak algorithms leaked into the whitelist: $leaks", leaks.isEmpty())
    }

    @Test
    fun kexPrefersModernGroupsAndContainsNoSha1Groups() {
        assertEquals("curve25519-sha256", SshAlgorithms.KEX.first())
        assertTrue(SshAlgorithms.KEX.contains("ecdh-sha2-nistp256"))
        assertTrue(SshAlgorithms.KEX.contains("diffie-hellman-group14-sha256"))
        // group1 is 1024-bit — forbidden even with sha256
        assertFalse(SshAlgorithms.KEX.any { it.startsWith("diffie-hellman-group1-") })
        assertFalse(SshAlgorithms.KEX.any { it.endsWith("-sha1") })
    }

    @Test
    fun hostKeysPreferEd25519AndExcludeSha1SignatureAlgos() {
        assertEquals("ssh-ed25519", SshAlgorithms.HOST_KEY.first())
        assertTrue(SshAlgorithms.HOST_KEY.contains("rsa-sha2-256"))
        assertFalse(SshAlgorithms.HOST_KEY.contains("ssh-dss"))
        assertFalse(SshAlgorithms.HOST_KEY.contains("ssh-rsa"))
    }

    @Test
    fun ciphersPreferChacha20AndContainNoCbcOrStreamCiphers() {
        assertEquals("chacha20-poly1305@openssh.com", SshAlgorithms.CIPHER.first())
        assertTrue(SshAlgorithms.CIPHER.contains("aes256-gcm@openssh.com"))
        assertFalse(SshAlgorithms.CIPHER.any { it.endsWith("-cbc") })
        assertFalse(SshAlgorithms.CIPHER.any { it.contains("arcfour") })
        assertFalse(SshAlgorithms.CIPHER.contains("3des-cbc"))
    }

    @Test
    fun whitelistsHaveNoDuplicatesAndNoBlankEntries() {
        for (list in listOf(SshAlgorithms.KEX, SshAlgorithms.HOST_KEY, SshAlgorithms.CIPHER)) {
            assertTrue(list.isNotEmpty())
            assertEquals(list.size, list.toSet().size)
            assertTrue(list.all { it.isNotBlank() })
        }
    }

    @Test
    fun configStringsJoinWithCommas() {
        assertEquals(SshAlgorithms.KEX.joinToString(","), SshAlgorithms.kexConfig())
        assertEquals(SshAlgorithms.HOST_KEY.joinToString(","), SshAlgorithms.hostKeyConfig())
        assertEquals(SshAlgorithms.CIPHER.joinToString(","), SshAlgorithms.cipherConfig())
        // JSch expects comma-separated, unspaced algorithm lists
        assertFalse(SshAlgorithms.kexConfig().contains(" "))
        assertFalse(SshAlgorithms.hostKeyConfig().contains(" "))
        assertFalse(SshAlgorithms.cipherConfig().contains(" "))
    }

    @Test
    fun assertNoWeakAlgorithmsRejectsAWeakName() {
        // The guard must actually detect weak names (mutation check).
        val weakKex = SshAlgorithms.KEX + "diffie-hellman-group1-sha1"
        try {
            require(weakKex.none { it in SshAlgorithms.FORBIDDEN }) {
                "weak SSH algorithm in whitelist"
            }
            fail("expected require to fail for a weak kex algorithm")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("weak"))
        }
    }
}
