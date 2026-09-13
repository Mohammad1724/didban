package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EncryptedVaultTest {

    /** Builds a legacy v1 payload exactly as the pre-M17 code produced it:
     *  [salt:16][iv:12][ct+tag] with PBKDF2-HMAC-SHA256 @ 100k. */
    private fun legacyEncrypt(plaintext: String, password: String, iterations: Int = 100_000): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val keySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        keySpec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(16 + 12 + ct.size)
        salt.copyInto(combined, 0)
        iv.copyInto(combined, 16)
        ct.copyInto(combined, 28)
        return Base64.getEncoder().encodeToString(combined)
    }

    @Test
    fun `round trip with the current 600k format`() {
        val secret = "ssh root@10.0.0.5 — password: S3cr3t! — 📱 didban"
        val enc = EncryptedVault.encrypt(secret, "master-123")
        assertEquals(secret, EncryptedVault.decrypt(enc, "master-123"))
    }

    @Test
    fun `new payload starts with the versioned magic`() {
        val bytes = Base64.getDecoder().decode(EncryptedVault.encrypt("x", "p"))
        // "DID2"
        assertEquals(0x44.toByte(), bytes[0])
        assertEquals(0x49.toByte(), bytes[1])
        assertEquals(0x44.toByte(), bytes[2])
        assertEquals(0x32.toByte(), bytes[3])
        assertEquals(1.toByte(), bytes[4]) // version 1
        // iterations = 600000 = 0x000927C0
        val iters = ((bytes[5].toInt() and 0xFF) shl 24) or
            ((bytes[6].toInt() and 0xFF) shl 16) or
            ((bytes[7].toInt() and 0xFF) shl 8) or
            (bytes[8].toInt() and 0xFF)
        assertEquals(600_000, iters)
    }

    @Test
    fun `legacy 100k payloads stay decryptable - backward compat`() {
        val secret = "legacy note content"
        val legacy = legacyEncrypt(secret, "old-master")
        assertEquals(secret, EncryptedVault.decrypt(legacy, "old-master"))
    }

    @Test
    fun `wrong password fails authentication`() {
        val enc = EncryptedVault.encrypt("secret", "right")
        try {
            EncryptedVault.decrypt(enc, "wrong")
            throw AssertionError("wrong password must not decrypt")
        } catch (e: Exception) {
            assertTrue(e is javax.crypto.AEADBadTagException || e is IllegalArgumentException)
        }
    }

    @Test
    fun `tampered ciphertext fails`() {
        val enc = EncryptedVault.encrypt("secret", "p")
        val bytes = Base64.getDecoder().decode(enc)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        try {
            EncryptedVault.decrypt(Base64.getEncoder().encodeToString(bytes), "p")
            throw AssertionError("tampered payload must not decrypt")
        } catch (e: Exception) {
            assertTrue(e is javax.crypto.AEADBadTagException || e is IllegalArgumentException)
        }
    }

    @Test
    fun `truncated payload is rejected cleanly`() {
        val enc = Base64.getEncoder().encodeToString(ByteArray(10))
        try {
            EncryptedVault.decrypt(enc, "p")
            throw AssertionError("truncated payload must be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `garbage base64 is rejected cleanly`() {
        try {
            EncryptedVault.decrypt("not!base64!!!", "p")
            throw AssertionError("garbage input must be rejected")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
