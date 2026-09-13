package org.didban.monitor

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class VaultNote(
    val id: Long,
    var title: String,
    var content: String,
    var tags: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("tags", tags)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): VaultNote = VaultNote(
            id = o.optLong("id", System.currentTimeMillis()),
            title = o.optString("title"),
            content = o.optString("content"),
            tags = o.optString("tags"),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}

object EncryptedVault {

    // M17: OWASP's current minimum for PBKDF2-HMAC-SHA256 (2023+).
    private const val ITERATIONS = 600_000
    // Legacy payloads (before M17) used 100k; they stay decryptable.
    private const val LEGACY_ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    // Versioned payload format (v2). A 4-byte magic keeps v2 unambiguous:
    // a legacy (v1) payload is a bare [salt][iv][ct] whose first 4 bytes
    // would have to match this exact sequence to be misread (p = 1/2^32).
    //   v2: [magic "DID2":4][version:1][iterations:4 big-endian][salt:16][iv:12][ct+tag]
    //   v1: [salt:16][iv:12][ct+tag]
    private val FORMAT_MAGIC = byteArrayOf(0x44, 0x49, 0x44, 0x32) // "DID2"
    private const val FORMAT_VERSION = 1

    fun encrypt(plaintext: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val secretKey = deriveKey(salt, password, ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Output (v2): [magic 4][version 1][iterations 4][Salt 16][IV 12][Ciphertext + Tag]
        val combined = ByteArray(FORMAT_MAGIC.size + 1 + 4 + SALT_LENGTH + IV_LENGTH + cipherText.size)
        var o = 0
        for (b in FORMAT_MAGIC) {
            combined[o++] = b
        }
        combined[o++] = FORMAT_VERSION.toByte()
        combined[o++] = (ITERATIONS ushr 24).toByte()
        combined[o++] = (ITERATIONS ushr 16).toByte()
        combined[o++] = (ITERATIONS ushr 8).toByte()
        combined[o++] = ITERATIONS.toByte()
        salt.copyInto(combined, o); o += SALT_LENGTH
        iv.copyInto(combined, o); o += IV_LENGTH
        cipherText.copyInto(combined, o)

        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedB64: String, password: String): String {
        val combined = try {
            Base64.getDecoder().decode(encryptedB64.trim())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid encrypted payload encoding", e)
        }

        var offset = 0
        var iterations = LEGACY_ITERATIONS
        if (combined.size > FORMAT_MAGIC.size && combined.copyOfRange(0, FORMAT_MAGIC.size).contentEquals(FORMAT_MAGIC)) {
            // Versioned v2 payload — the iteration count travels with the
            // data, so future KDF tuning cannot strand old backups.
            if (combined.size < FORMAT_MAGIC.size + 1 + 4 + SALT_LENGTH + IV_LENGTH + 16) {
                throw IllegalArgumentException("Invalid encrypted payload size")
            }
            offset = FORMAT_MAGIC.size
            val version = combined[offset].toInt() and 0xFF
            if (version != FORMAT_VERSION) {
                throw IllegalArgumentException("Unsupported vault format version: $version")
            }
            iterations = ((combined[offset + 1].toInt() and 0xFF) shl 24) or
                ((combined[offset + 2].toInt() and 0xFF) shl 16) or
                ((combined[offset + 3].toInt() and 0xFF) shl 8) or
                (combined[offset + 4].toInt() and 0xFF)
            offset += 5
            if (iterations < 1_000) {
                throw IllegalArgumentException("Invalid iteration count in vault payload")
            }
        }

        if (combined.size < offset + SALT_LENGTH + IV_LENGTH + 16) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val salt = combined.copyOfRange(offset, offset + SALT_LENGTH)
        val iv = combined.copyOfRange(offset + SALT_LENGTH, offset + SALT_LENGTH + IV_LENGTH)
        val cipherText = combined.copyOfRange(offset + SALT_LENGTH + IV_LENGTH, combined.size)

        val secretKey = deriveKey(salt, password, iterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * PBKDF2 derivation with the password material wiped as soon as it is
     * no longer needed (M17): the char[] copy is zeroed, and PBEKeySpec's
     * internal copy is cleared via [PBEKeySpec.clearPassword].
     */
    private fun deriveKey(salt: ByteArray, password: String, iterations: Int): SecretKeySpec {
        val chars = password.toCharArray()
        try {
            val keySpec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH)
            try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                return SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")
            } finally {
                keySpec.clearPassword()
            }
        } finally {
            for (i in chars.indices) {
                chars[i] = '\u0000'
            }
        }
    }

    fun exportBackup(servers: List<ServerConfig>, notes: List<VaultNote>, password: String): String {
        val root = JSONObject().apply {
            put("version", 1)
            put("app", "didban")
            put("timestamp", System.currentTimeMillis())
            val sa = JSONArray()
            servers.forEach { sa.put(it.toJson()) }
            put("servers", sa)
            val na = JSONArray()
            notes.forEach { na.put(it.toJson()) }
            put("notes", na)
        }
        return encrypt(root.toString(), password)
    }

    fun importBackup(encryptedB64: String, password: String): Pair<List<ServerConfig>, List<VaultNote>> {
        val jsonStr = decrypt(encryptedB64, password)
        val root = JSONObject(jsonStr)

        val serversList = mutableListOf<ServerConfig>()
        val sa = root.optJSONArray("servers")
        if (sa != null) {
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                serversList.add(ServerConfig.fromJson(o))
            }
        }

        val notesList = mutableListOf<VaultNote>()
        val na = root.optJSONArray("notes")
        if (na != null) {
            for (i in 0 until na.length()) {
                val o = na.optJSONObject(i) ?: continue
                notesList.add(VaultNote.fromJson(o))
            }
        }

        return Pair(serversList, notesList)
    }
}
