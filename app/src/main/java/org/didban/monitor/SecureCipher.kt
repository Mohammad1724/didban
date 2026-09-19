package org.didban.monitor

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/**
 * AES-256-GCM core for [SecureStorage]. Pure JVM (key material is injected),
 * so it is directly unit-testable without Android. Output is
 * base64(iv || ciphertext || tag); a random 12-byte IV is used per call.
 */
object SecureCipher {

    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    /** AES-256-GCM encrypt; output is base64(iv || ciphertext || tag). */
    fun encrypt(key: SecretKey, plaintext: String, associatedData: ByteArray = byteArrayOf()): String {
        val generatedIv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = try {
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, generatedIv))
            generatedIv
        } catch (vendorFailure: Exception) {
            // Some vendor AndroidKeyStore providers crash while importing a
            // caller-supplied GCMParameterSpec. Let the provider generate its
            // own random IV instead; Android forbids caller IV reuse anyway.
            val fallback = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                fallback.init(Cipher.ENCRYPT_MODE, key)
            } catch (fallbackFailure: Exception) {
                fallbackFailure.addSuppressed(vendorFailure)
                throw fallbackFailure
            }
            require(fallback.iv?.size == IV_LENGTH) { "provider returned an invalid GCM IV" }
            if (associatedData.isNotEmpty()) fallback.updateAAD(associatedData)
            val ct = fallback.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(fallback.iv + ct)
        }
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    /** Inverse of [encrypt]; throws on tampering or wrong key. */
    fun decrypt(key: SecretKey, payload: String, associatedData: ByteArray = byteArrayOf()): String {
        val raw = Base64.getDecoder().decode(payload)
        require(raw.size >= IV_LENGTH + TAG_LENGTH_BIT / 8) { "payload too short" }
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val ct = raw.copyOfRange(IV_LENGTH, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        } catch (vendorFailure: Exception) {
            val fallback = Cipher.getInstance("AES/GCM/NoPadding")
            try {
                fallback.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            } catch (fallbackFailure: Exception) {
                fallbackFailure.addSuppressed(vendorFailure)
                throw fallbackFailure
            }
            if (associatedData.isNotEmpty()) fallback.updateAAD(associatedData)
            return String(fallback.doFinal(ct), Charsets.UTF_8)
        }
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
