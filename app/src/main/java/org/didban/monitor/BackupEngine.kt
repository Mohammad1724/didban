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

        return if (!password.isNullOrBlank()) {
            val encrypted = EncryptedVault.encrypt(plainJson, password)
            "$ENC_PREFIX$encrypted"
        } else {
            plainJson
        }
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
            val sp = ctx.getSharedPreferences("didban", Context.MODE_PRIVATE)
            val editor = sp.edit()

            // 1. Restore Servers
            val serversArr = root.optJSONArray("servers") ?: JSONArray()
            val incomingServers = mutableListOf<ServerConfig>()
            for (i in 0 until serversArr.length()) {
                incomingServers.add(ServerConfig.fromJson(serversArr.getJSONObject(i)))
            }
            val finalServers = if (mode == RestoreMode.Merge) {
                val existing = Prefs.loadServers(ctx)
                val existingIds = existing.map { it.id }.toSet()
                val existingHosts = existing.map { "${it.host}:${it.port}" }.toSet()
                val toAdd = incomingServers.filter { it.id !in existingIds && "${it.host}:${it.port}" !in existingHosts }
                existing + toAdd
            } else {
                incomingServers
            }
            Prefs.saveServers(ctx, finalServers)

            // 2. Restore Tunnels
            val tunnelsArr = root.optJSONArray("tunnels") ?: JSONArray()
            val incomingTunnels = mutableListOf<TunnelConfig>()
            for (i in 0 until tunnelsArr.length()) {
                incomingTunnels.add(TunnelConfig.fromJson(tunnelsArr.getJSONObject(i)))
            }
            val finalTunnels = if (mode == RestoreMode.Merge) {
                val existing = Prefs.loadTunnels(ctx)
                val existingIds = existing.map { it.id }.toSet()
                val toAdd = incomingTunnels.filter { it.id !in existingIds }
                existing + toAdd
            } else {
                incomingTunnels
            }
            Prefs.saveTunnels(ctx, finalTunnels)

            // 3. Restore Uptime Monitors
            val uptimeArr = root.optJSONArray("uptime_targets") ?: JSONArray()
            val incomingUptime = mutableListOf<UptimeTarget>()
            for (i in 0 until uptimeArr.length()) {
                incomingUptime.add(UptimeTarget.fromJson(uptimeArr.getJSONObject(i)))
            }
            val finalUptime = if (mode == RestoreMode.Merge) {
                val existing = Prefs.loadUptimeTargets(ctx)
                val existingIds = existing.map { it.id }.toSet()
                val toAdd = incomingUptime.filter { it.id !in existingIds }
                existing + toAdd
            } else {
                incomingUptime
            }
            Prefs.saveUptimeTargets(ctx, finalUptime)

            // 4. Restore Vault (if present)
            var vaultRestored = false
            val canary = root.optString("vault_canary_enc", "")
            val notes = root.optString("vault_notes_enc", "")
            if (canary.isNotEmpty() && notes.isNotEmpty()) {
                if (mode == RestoreMode.Overwrite || !Prefs.isVaultInitialized(ctx)) {
                    editor.putString("vault_canary_enc", canary)
                    editor.putString("vault_notes_enc", notes)
                    vaultRestored = true
                }
            }

            // 5. Restore Cloudflare & Preferences
            val cfToken = root.optString("cf_token", "")
            if (cfToken.isNotEmpty() && (mode == RestoreMode.Overwrite || Prefs.getCfToken(ctx).isEmpty())) {
                editor.putString("cf_token", cfToken)
            }

            if (mode == RestoreMode.Overwrite) {
                val theme = root.optString("theme_mode", "")
                val lang = root.optString("lang", "")
                val poll = root.optLong("poll_sec", 0L)
                if (theme.isNotEmpty()) editor.putString("theme_mode", theme)
                if (lang.isNotEmpty()) editor.putString("lang", lang)
                if (poll > 0) editor.putString("poll_sec", poll.toString())
            }

            editor.apply()

            val msg = "✅ بازیابی با موفقیت انجام شد: ${incomingServers.size} سرور، ${incomingTunnels.size} تانل، ${incomingUptime.size} مانیتور آپ‌تایم."

            RestoreResult(
                success = true,
                serversRestored = incomingServers.size,
                tunnelsRestored = incomingTunnels.size,
                uptimeRestored = incomingUptime.size,
                vaultRestored = vaultRestored,
                message = msg
            )
        } catch (e: Exception) {
            RestoreResult(success = false, message = "خطا در پردازش اطلاعات بکاپ: ${e.message}")
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
