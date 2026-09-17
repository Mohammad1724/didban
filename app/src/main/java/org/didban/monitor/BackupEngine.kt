package org.didban.monitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupPreview(
    val isValid: Boolean,
    val isEncrypted: Boolean,
    val serversCount: Int = 0,
    val tunnelsCount: Int = 0,
    val uptimeCount: Int = 0,
    val hasVault: Boolean = false,
    val hasCfToken: Boolean = false,
    val timestamp: Long = 0L,
    val errorMessage: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val serversRestored: Int = 0,
    val tunnelsRestored: Int = 0,
    val uptimeRestored: Int = 0,
    val vaultRestored: Boolean = false,
    val message: String
)

enum class RestoreMode {
    Merge,
    Overwrite
}

object BackupEngine {

    private const val ENC_PREFIX = "DIDBAN_BACKUP_V2:"

    /**
     * Creates a full backup of Didban (Servers, Tunnels, Uptime, Vault, Settings).
     * If [password] is provided and not blank, encrypts the payload with AES-256-GCM.
     */
    fun createBackup(ctx: Context, password: String? = null): String {
        val backupPassword = password?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A backup password is required because exports contain credentials")
        val sp = ctx.getSharedPreferences("didban", Context.MODE_PRIVATE)

        val root = JSONObject().apply {
            put("version", 2)
            put("timestamp", System.currentTimeMillis())

            // 1. Servers
            val serversArr = JSONArray()
            Prefs.loadServers(ctx).forEach { serversArr.put(it.toJson()) }
            put("servers", serversArr)

            // 2. Tunnels
            val tunnelsArr = JSONArray()
            Prefs.loadTunnels(ctx).forEach { tunnelsArr.put(it.toJson()) }
            put("tunnels", tunnelsArr)

            // 3. Uptime Monitors
            val uptimeArr = JSONArray()
            Prefs.loadUptimeTargets(ctx).forEach { uptimeArr.put(it.toJson()) }
            put("uptime_targets", uptimeArr)

            // 4. Vault (Keep encrypted blobs intact)
            val canary = sp.getString("vault_canary_enc", "")
            val notes = sp.getString("vault_notes_enc", "")
            put("vault_canary_enc", canary)
            put("vault_notes_enc", notes)

            // 5. App Settings
            put("cf_token", Prefs.getCfToken(ctx))
            put("lang", Prefs.getLanguage(ctx))
            put("theme_mode", Prefs.getThemeMode(ctx))
            put("poll_sec", Prefs.getPollIntervalMs(ctx) / 1000L)
        }

        val plainJson = root.toString()

        val encrypted = EncryptedVault.encrypt(plainJson, backupPassword)
        return "$ENC_PREFIX$encrypted"
    }

    /**
     * Inspects and previews a backup blob without applying it.
     */
    fun inspectBackup(raw: String, password: String? = null): BackupPreview {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return BackupPreview(isValid = false, isEncrypted = false, errorMessage = "رشته بکاپ خالی است")
        }

        val isEncrypted = trimmed.startsWith(ENC_PREFIX)

        val jsonStr = if (isEncrypted) {
            if (password.isNullOrBlank()) {
                return BackupPreview(
                    isValid = true,
                    isEncrypted = true,
                    errorMessage = "این بکاپ رمزنگاری شده است. لطفاً رمز عبور را وارد کنید."
                )
            }
            try {
                val encPayload = trimmed.removePrefix(ENC_PREFIX)
                EncryptedVault.decrypt(encPayload, password)
            } catch (e: Exception) {
                return BackupPreview(
                    isValid = false,
                    isEncrypted = true,
                    errorMessage = "رمز عبور نادرست است یا داده‌های بکاپ آسیب دیده‌اند."
                )
            }
        } else {
            trimmed
        }

        return try {
            val root = JSONObject(jsonStr)
            val serversArr = root.optJSONArray("servers") ?: JSONArray()
            val tunnelsArr = root.optJSONArray("tunnels") ?: JSONArray()
            val uptimeArr = root.optJSONArray("uptime_targets") ?: JSONArray()
            val canary = root.optString("vault_canary_enc", "")
            val cfToken = root.optString("cf_token", "")
            val ts = root.optLong("timestamp", System.currentTimeMillis())

            BackupPreview(
                isValid = true,
                isEncrypted = isEncrypted,
                serversCount = serversArr.length(),
                tunnelsCount = tunnelsArr.length(),
                uptimeCount = uptimeArr.length(),
                hasVault = canary.isNotEmpty(),
                hasCfToken = cfToken.isNotEmpty(),
                timestamp = ts
            )
        } catch (e: Exception) {
            BackupPreview(isValid = false, isEncrypted = isEncrypted, errorMessage = "ساختار فایل بکاپ نامعتبر است.")
        }
    }

    /**
     * Restores data from a backup blob with optional merge or overwrite mode.
     */
    fun restoreBackup(
        ctx: Context,
        raw: String,
        password: String? = null,
        mode: RestoreMode = RestoreMode.Merge
    ): RestoreResult {
        val trimmed = raw.trim()
        val isEncrypted = trimmed.startsWith(ENC_PREFIX)

        val jsonStr = if (isEncrypted) {
            if (password.isNullOrBlank()) {
                return RestoreResult(success = false, message = "رمز عبور بکاپ وارد نشده است.")
            }
            try {
                val encPayload = trimmed.removePrefix(ENC_PREFIX)
                EncryptedVault.decrypt(encPayload, password)
            } catch (e: Exception) {
                return RestoreResult(success = false, message = "رمز عبور اشتباه است یا فایل بکاپ خراب است.")
            }
        } else {
            trimmed
        }

        return try {
            val root = JSONObject(jsonStr)

            // Parse and validate the complete document before touching storage.
            val serversArr = root.optJSONArray("servers") ?: JSONArray()
            val incomingServers = MutableList(serversArr.length()) { i ->
                ServerConfig.fromJson(serversArr.getJSONObject(i))
            }
            val unsafeServer = incomingServers.firstOrNull {
                !it.useTls || it.token.isBlank() || !TunnelFieldValidation.isHost(it.host) ||
                    !CertFingerprint.isValidSha256(it.fingerprint)
            }
            require(unsafeServer == null) {
                "Backup contains an insecure or invalid server connection: ${unsafeServer?.name}"
            }

            val tunnelsArr = root.optJSONArray("tunnels") ?: JSONArray()
            val incomingTunnels = MutableList(tunnelsArr.length()) { i ->
                TunnelConfig.fromJson(tunnelsArr.getJSONObject(i))
            }
            val uptimeArr = root.optJSONArray("uptime_targets") ?: JSONArray()
            val incomingUptime = MutableList(uptimeArr.length()) { i ->
                UptimeTarget.fromJson(uptimeArr.getJSONObject(i))
            }

            val currentServerResult = Prefs.loadServersResult(ctx)
            check(currentServerResult.error == null) { "Existing secure server data cannot be read" }
            val currentServers = currentServerResult.servers
            val finalServers = if (mode == RestoreMode.Merge) {
                val ids = currentServers.map { it.id }.toSet()
                val hosts = currentServers.map { "${it.host}:${it.port}" }.toSet()
                currentServers + incomingServers.filter { it.id !in ids && "${it.host}:${it.port}" !in hosts }
            } else incomingServers

            val currentTunnels = Prefs.loadTunnels(ctx)
            val finalTunnels = if (mode == RestoreMode.Merge) {
                val ids = currentTunnels.map { it.id }.toSet()
                currentTunnels + incomingTunnels.filter { it.id !in ids }
            } else incomingTunnels

            val currentUptime = Prefs.loadUptimeTargets(ctx)
            val finalUptime = if (mode == RestoreMode.Merge) {
                val ids = currentUptime.map { it.id }.toSet()
                currentUptime + incomingUptime.filter { it.id !in ids }
            } else incomingUptime

            val serverJson = JSONArray().apply { finalServers.forEach { put(it.toJson()) } }.toString()
            val tunnelJson = JSONArray().apply { finalTunnels.forEach { put(it.toJson()) } }.toString()
            val uptimeJson = JSONArray().apply { finalUptime.forEach { put(it.toJson()) } }.toString()

            val secrets = mutableMapOf(
                "servers" to serverJson,
                "didban_tunnels" to tunnelJson
            )
            val values = mutableMapOf("uptime_targets" to uptimeJson)

            var vaultRestored = false
            val canary = root.optString("vault_canary_enc", "")
            val notes = root.optString("vault_notes_enc", "")
            if (canary.isNotEmpty() && notes.isNotEmpty() &&
                (mode == RestoreMode.Overwrite || !Prefs.isVaultInitialized(ctx))) {
                values["vault_canary_enc"] = canary
                values["vault_notes_enc"] = notes
                vaultRestored = true
            }

            val cfToken = root.optString("cf_token", "")
            if (cfToken.isNotEmpty() && (mode == RestoreMode.Overwrite || Prefs.getCfToken(ctx).isEmpty())) {
                secrets["cf_token"] = cfToken
            }
            if (mode == RestoreMode.Overwrite) {
                root.optString("theme_mode", "").takeIf { it.isNotEmpty() }?.let { values["theme_mode"] = it }
                root.optString("lang", "").takeIf { it.isNotEmpty() }?.let { values["lang"] = it }
                root.optLong("poll_sec", 0L).takeIf { it > 0 }?.let { values["poll_sec"] = it.toString() }
            }

            // One commit: no partially-restored servers/tunnels/settings.
            SecureStorage.putTransaction(ctx, "didban", secrets, values)

            RestoreResult(
                success = true,
                serversRestored = incomingServers.size,
                tunnelsRestored = incomingTunnels.size,
                uptimeRestored = incomingUptime.size,
                vaultRestored = vaultRestored,
                message = "✅ بازیابی با موفقیت انجام شد: ${incomingServers.size} سرور، ${incomingTunnels.size} تانل، ${incomingUptime.size} مانیتور آپ‌تایم."
            )
        } catch (_: Exception) {
            // JSON/crypto exceptions can quote the malformed source, which may
            // contain credentials from a legacy plaintext backup.
            RestoreResult(success = false, message = "خطا در پردازش یا ذخیره امن اطلاعات بکاپ")
        }
    }

    fun formatTimestamp(ts: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            sdf.format(Date(ts))
        } catch (_: Exception) {
            "-"
        }
    }
}
