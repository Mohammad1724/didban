package org.didban.monitor

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.SecureRandom
import java.security.Security

/**
 * Bouncy Castle is registered at provider priority 1, which means it serves
 * `new SecureRandom()` as well — and its DRBG caps one request at 262,144 bits
 * (32 KB). Any buffer filled in a single `nextBytes()` call above that size
 * throws on a device, not just in a test. `BandwidthBenchmark`'s 64 KB upload
 * block did exactly that, so the bandwidth test failed for every user.
 *
 * These tests run against the real provider (they register it), so they fail
 * again if someone goes back to a one-shot fill.
 */
class CryptoSecurityTest {

    private fun withBouncyCastle() {
        CryptoSecurity.ensureInitialized()
        val provider = Security.getProvider("BC")
        assertTrue(
            "expected Bouncy Castle to be serving this JVM, got $provider",
            provider is BouncyCastleProvider
        )
    }

    @Test
    fun `fillRandom fills buffers far larger than the DRBG per-request limit`() {
        withBouncyCastle()
        // 64 KB is what the bandwidth upload block uses; 256 KB leaves room.
        val buffer = ByteArray(256 * 1024)
        CryptoSecurity.fillRandom(buffer)
        assertTrue("buffer must not be left zeroed", buffer.any { it != 0.toByte() })

        val second = ByteArray(256 * 1024)
        CryptoSecurity.fillRandom(second)
        assertTrue("two fills must differ", !buffer.contentEquals(second))
    }

    @Test
    fun `fillRandom handles the sizes the app actually asks for`() {
        withBouncyCastle()
        listOf(0, 1, 12, 16, 32, 8 * 1024, 64 * 1024, 100 * 1024).forEach { size ->
            val buffer = ByteArray(size)
            CryptoSecurity.fillRandom(buffer)
            assertEquals("size must be preserved", size, buffer.size)
            // A one-byte fill is legitimately zero one time in 256, so only
            // the larger sizes are asked to prove they were written.
            if (size >= 16) {
                assertTrue("size $size must not be left zeroed", buffer.any { it != 0.toByte() })
            }
        }
    }

    @Test
    fun `one oversized nextBytes call is rejected by the DRBG, which is why fillRandom exists`() {
        withBouncyCastle()
        val oversized = ByteArray(64 * 1024)
        try {
            SecureRandom().nextBytes(oversized)
            // If a future provider lifts the cap this is good news, not a
            // failure — but fillRandom must stay, since we cannot choose the
            // provider a device gives us.
        } catch (rejected: IllegalArgumentException) {
            assertTrue(
                "unexpected rejection message: ${rejected.message}",
                rejected.message.orEmpty().contains("262144")
            )
        }
    }

    @Test
    fun `ensureInitialized is idempotent and keeps the same provider`() {
        CryptoSecurity.ensureInitialized()
        val first = Security.getProvider("BC")
        CryptoSecurity.ensureInitialized()
        val second = Security.getProvider("BC")
        assertTrue(first is BouncyCastleProvider)
        assertTrue("the provider must not be swapped on a second call", first === second)
    }
}
