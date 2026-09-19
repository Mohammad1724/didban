package org.didban.monitor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Device-bound secret storage.
 *
 * App-level credentials (agent tokens, Cloudflare/Telegram/Discord secrets)
 * used to sit in plain SharedPreferences while allowBackup=true, so a device
 * backup (cloud or adb) leaked them. They are now AES-256-GCM encrypted with
 * a key that lives in the Android Keystore (non-exportable, wiped together
 * with app data), and the ciphertext lives in the same prefs file.
 *
 * Split for testability:
 *  - [SecureCipher] is pure JVM (key material injected) -> unit-tested.
 *  - [SecureStorage] is the Android glue (Keystore + prefs + migration).
 *
 * What IS a secret here: anything that grants access to a remote system
 * (agent Bearer tokens, tunnel tokens, Cloudflare API token, Telegram bot
 * token, Discord webhook). What is NOT: hostnames, ports, thresholds,
 * fingerprints (public trust anchors), chat IDs, UI settings.
 */

// ── Android glue ────────────────────────────────────────────────────────────

class SecretStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object SecureStorage {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "didban_app_secrets"
    private const val KEY_SIZE_BITS = 256
    private const val FORMAT_V2 = "v2:"

    @Volatile private var key: SecretKey? = null

    private fun generateAesKey(sizeBits: Int): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(sizeBits)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        key?.let { return it }
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null)
        val k: SecretKey = if (existing is KeyStore.SecretKeyEntry) {
            existing.secretKey
        } else {
            // A few older/vendor AndroidKeyStore implementations reject an
            // explicit 256-bit AES key even though AES-GCM itself is present.
            // AES-128-GCM remains cryptographically strong; use it only as a
            // generation fallback, never as plaintext-storage fallback.
            try {
                generateAesKey(KEY_SIZE_BITS)
            } catch (primary: Exception) {
                runCatching { ks.deleteEntry(KEY_ALIAS) }
                try {
                    generateAesKey(128)
                } catch (fallback: Exception) {
                    fallback.addSuppressed(primary)
                    throw fallback
                }
            }
        }
        key = k
        return k
    }

    @Synchronized
    private fun resetKeyAlias() {
        key = null
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
    }

    fun encrypt(plaintext: String): String = SecureCipher.encrypt(getOrCreateKey(), plaintext)

    fun decrypt(payload: String): String = SecureCipher.decrypt(getOrCreateKey(), payload)

    private fun associatedData(prefsName: String, entryKey: String): ByteArray =
        "$prefsName\u0000$entryKey".toByteArray(Charsets.UTF_8)

    private fun encryptBound(prefsName: String, entryKey: String, plaintext: String): String =
        FORMAT_V2 + SecureCipher.encrypt(getOrCreateKey(), plaintext, associatedData(prefsName, entryKey))

    private fun decryptBound(prefsName: String, entryKey: String, payload: String): String =
        SecureCipher.decrypt(
            getOrCreateKey(),
            payload.removePrefix(FORMAT_V2),
            associatedData(prefsName, entryKey)
        )

    /**
     * Reads a secret by [key]. Transparently migrates legacy plaintext
     * entries: on first access the old value is encrypted into
     * "<key>_enc" and the plaintext copy is deleted. Existing unbound
     * ciphertext is also migrated to the v2 format whose AEAD associated data
     * binds it to both the preferences file and entry name. Corruption fails
     * closed rather than being mistaken for a missing credential.
     */
    fun getSecret(ctx: Context, prefsName: String, key: String): String {
        val sp = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val encKey = key + "_enc"
        sp.getString(encKey, null)?.let { enc ->
            if (enc.startsWith(FORMAT_V2)) {
                try {
                    return decryptBound(prefsName, key, enc)
                } catch (e: Exception) {
                    throw SecretStorageException("Encrypted secret is corrupted or misplaced", e)
                }
            }
            // One-time migration from the original unbound AES-GCM format.
            val plaintext = try {
                decrypt(enc)
            } catch (e: Exception) {
                throw SecretStorageException("Encrypted secret cannot be decrypted", e)
            }
            val migrated = encryptBound(prefsName, key, plaintext)
            if (!sp.edit().putString(encKey, migrated).commit()) {
                throw SecretStorageException("Could not migrate encrypted storage format")
            }
            return plaintext
        }
        val legacy = sp.getString(key, null) ?: return ""
        if (legacy.isEmpty()) return ""
        val encrypted = try {
            encryptBound(prefsName, key, legacy)
        } catch (e: Exception) {
            throw SecretStorageException("Could not migrate legacy secret to secure storage", e)
        }
        if (!sp.edit().putString(encKey, encrypted).remove(key).commit()) {
            throw SecretStorageException("Could not finish secure-storage migration")
        }
        return legacy
    }

    /**
     * Writes a secret encrypted with the Android Keystore.
     *
     * This deliberately fails closed. Credentials must never be silently
     * downgraded to plaintext when the Keystore is unavailable: callers can
     * report the storage failure and leave the previous encrypted value intact.
     */
    fun putSecret(ctx: Context, prefsName: String, key: String, plaintext: String) {
        val sp = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val encrypted = try {
            encryptBound(prefsName, key, plaintext)
        } catch (first: Exception) {
            // An interrupted OS/app restore can leave a stale Keystore alias
            // before any encrypted app data exists. It is safe to recreate it
            // only for an empty secure store; never discard a key that protects
            // existing ciphertext.
            val hasEncryptedData = sp.all.keys.any { it.endsWith("_enc") }
            if (hasEncryptedData) {
                throw SecretStorageException("Secure storage is unavailable", first)
            }
            try {
                resetKeyAlias()
                encryptBound(prefsName, key, plaintext)
            } catch (retry: Exception) {
                retry.addSuppressed(first)
                throw SecretStorageException("Secure storage is unavailable", retry)
            }
        }
        if (!sp.edit().putString(key + "_enc", encrypted).remove(key).commit()) {
            throw SecretStorageException("Could not persist encrypted data")
        }
    }

    /**
     * Encrypts every secret first, then commits secrets and ordinary preference
     * values in one SharedPreferences transaction. If any encryption or the
     * final commit fails, none of the supplied values is applied.
     */
    fun putTransaction(
        ctx: Context,
        prefsName: String,
        secrets: Map<String, String>,
        values: Map<String, String> = emptyMap()
    ) {
        val encrypted = try {
            secrets.mapValues { (entryKey, plaintext) -> encryptBound(prefsName, entryKey, plaintext) }
        } catch (e: Exception) {
            throw SecretStorageException("Could not prepare secure transaction", e)
        }
        val editor = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit()
        encrypted.forEach { (key, payload) ->
            editor.putString(key + "_enc", payload).remove(key)
        }
        values.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) {
            throw SecretStorageException("Could not commit secure transaction")
        }
    }

    fun removeSecret(ctx: Context, prefsName: String, key: String) {
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().remove(key + "_enc").remove(key).apply()
    }
}
