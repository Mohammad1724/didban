package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.fail
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class SecureCipherTest {

    private fun randomKey(): SecretKeySpec {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return SecretKeySpec(bytes, "AES")
    }

    @Test
    fun `round trip returns the original plaintext`() {
        val key = randomKey()
        val plain = "agent-token-1234567890-abcdef"
        val enc = SecureCipher.encrypt(key, plain)
        assertEquals(plain, SecureCipher.decrypt(key, enc))
    }

    @Test
    fun `round trip works for unicode and long payloads`() {
        val key = randomKey()
        val plain = "توکن فارسی ✓ 中文 " + "x".repeat(100_000)
        val enc = SecureCipher.encrypt(key, plain)
        assertEquals(plain, SecureCipher.decrypt(key, enc))
    }

    @Test
    fun `round trip works for empty string`() {
        val key = randomKey()
        val enc = SecureCipher.encrypt(key, "")
        assertEquals("", SecureCipher.decrypt(key, enc))
    }

    @Test
    fun `two encryptions of the same plaintext differ (fresh IV)`() {
        val key = randomKey()
        val enc1 = SecureCipher.encrypt(key, "same-value")
        val enc2 = SecureCipher.encrypt(key, "same-value")
        assertNotEquals(enc1, enc2)
        assertEquals("same-value", SecureCipher.decrypt(key, enc1))
        assertEquals("same-value", SecureCipher.decrypt(key, enc2))
    }

    @Test
    fun `tampering with the ciphertext is detected`() {
        val key = randomKey()
        val enc = SecureCipher.encrypt(key, "secret-data")
        val raw = java.util.Base64.getDecoder().decode(enc).toMutableList()
        val last = raw[raw.size - 1].toInt()
        raw[raw.size - 1] = (last xor 1).toByte() // flip a tag byte
        val tampered = java.util.Base64.getEncoder().encodeToString(raw.toByteArray())
        try {
            SecureCipher.decrypt(key, tampered)
            fail("tampered payload must not decrypt")
        } catch (expected: Exception) {
            // AEADBadTagException (or wrapped) - expected
        }
    }

    @Test
    fun `truncated payload is rejected`() {
        val key = randomKey()
        val enc = SecureCipher.encrypt(key, "data")
        val short = java.util.Base64.getEncoder().encodeToString(
            java.util.Base64.getDecoder().decode(enc).copyOfRange(0, 10)
        )
        try {
            SecureCipher.decrypt(key, short)
            fail("truncated payload must be rejected")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun `wrong key fails to decrypt`() {
        val plain = "agent-token"
        val enc = SecureCipher.encrypt(randomKey(), plain)
        try {
            SecureCipher.decrypt(randomKey(), enc)
            fail("wrong key must not decrypt")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun `associated data prevents ciphertext substitution`() {
        val key = randomKey()
        val tokenSlot = "didban\u0000tg_bot_token".toByteArray()
        val webhookSlot = "didban\u0000discord_webhook".toByteArray()
        val enc = SecureCipher.encrypt(key, "secret", tokenSlot)
        assertEquals("secret", SecureCipher.decrypt(key, enc, tokenSlot))
        try {
            SecureCipher.decrypt(key, enc, webhookSlot)
            fail("ciphertext moved to another preference key must not decrypt")
        } catch (expected: Exception) {
        }
    }

    @Test
    fun `ciphertext is base64 and much longer than the plaintext`() {
        val key = randomKey()
        val enc = SecureCipher.encrypt(key, "short")
        // 12 (iv) + tag(16) + ct >= base64 of >= 28 bytes
        assertTrue(java.util.Base64.getDecoder().decode(enc).size >= 28)
    }
}
