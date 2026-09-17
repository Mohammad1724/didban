package org.didban.monitor

import android.content.Context
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

    private var key: SecretKey? = null

    private fun getOrCreateKey(): SecretKey {
        key?.let { return it }
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null)
        val k: SecretKey = if (existing is KeyStore.SecretKeyEntry) {
            existing.secretKey
        } else {
            val gen = KeyGenerator.getInstance("AES", KEYSTORE)
            gen.init(KEY_SIZE_BITS)
            gen.generateKey()
        }
        key = k
        return k
    }

    fun encrypt(plaintext: String): String = SecureCipher.encrypt(getOrCreateKey(), plaintext)

    fun decrypt(payload: String): String = SecureCipher.decrypt(getOrCreateKey(), payload)

    /**
     * Reads a secret by [key]. Transparently migrates legacy plaintext
     * entries: on first access the old value is encrypted into
     * "<key>_enc" and the plaintext copy is deleted. If encryption fails
     * (should not happen), the legacy value is returned so data is never
     * lost.
     */
    fun getSecret(ctx: Context, prefsName: String, key: String): String {
        val sp = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val encKey = key + "_enc"
        sp.getString(encKey, null)?.let { enc ->
            try {
                return decrypt(enc)
            } catch (_: Exception) {
                // Corrupted ciphertext (e.g. storage anomaly) - fall through
                // to the legacy value and re-migrate below.
            }
        }
        val legacy = sp.getString(key, null) ?: return ""
        if (legacy.isEmpty()) return ""
        val encrypted = try {
            encrypt(legacy)
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
            encrypt(plaintext)
        } catch (e: Exception) {
            throw SecretStorageException("Secure storage is unavailable", e)
        }
        if (!sp.edit().putString(key + "_enc", encrypted).remove(key).commit()) {
            throw SecretStorageException("Could not persist encrypted data")
        }
    }

    fun removeSecret(ctx: Context, prefsName: String, key: String) {
        ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit().remove(key + "_enc").remove(key).apply()
    }
}
