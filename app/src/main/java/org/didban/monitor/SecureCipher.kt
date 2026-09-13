package org.didban.monitor

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM core for [SecureStorage]. Pure JVM (key material is injected),
 * so it is directly unit-testable without Android. Output is
 * base64(iv || ciphertext || tag); a random 12-byte IV is used per call.
 */
object SecureCipher {

    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    /** AES-256-GCM encrypt; output is base64(iv || ciphertext || tag). */
    fun encrypt(key: SecretKey, plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    /** Inverse of [encrypt]; throws on tampering or wrong key. */
    fun decrypt(key: SecretKey, payload: String): String {
        val raw = Base64.getDecoder().decode(payload)
        require(raw.size >= IV_LENGTH + TAG_LENGTH_BIT / 8) { "payload too short" }
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val ct = raw.copyOfRange(IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
