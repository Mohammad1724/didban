package org.didban.monitor

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
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

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BIT = 128

    fun encrypt(plaintext: String, password: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val cipherText = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Output: [Salt (16B)] + [IV (12B)] + [Ciphertext + Tag]
        val combined = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedB64: String, password: String): String {
        val combined = Base64.decode(encryptedB64.trim(), Base64.NO_WRAP)
        if (combined.size < SALT_LENGTH + IV_LENGTH + 16) {
            throw IllegalArgumentException("Invalid encrypted payload size")
        }

        val salt = ByteArray(SALT_LENGTH)
        System.arraycopy(combined, 0, salt, 0, SALT_LENGTH)

        val iv = ByteArray(IV_LENGTH)
        System.arraycopy(combined, SALT_LENGTH, iv, 0, IV_LENGTH)

        val cipherTextSize = combined.size - SALT_LENGTH - IV_LENGTH
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, SALT_LENGTH + IV_LENGTH, cipherText, 0, cipherTextSize)

        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
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
