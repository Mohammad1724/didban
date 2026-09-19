package org.didban.monitor

import android.content.Context
import org.json.JSONArray

/** Tiny persistence layer on top of SharedPreferences. */
object Prefs {
    private const val FILE = "didban"

    data class ServerLoadResult(
        val servers: MutableList<ServerConfig>,
        val error: Throwable? = null
    )

    /** Detailed form for user-facing screens: corruption/decryption is not disguised as an empty fleet. */
    fun loadServersResult(ctx: Context): ServerLoadResult {
        return try {
            val raw = SecureStorage.getSecret(ctx, FILE, "servers")
            if (raw.isEmpty()) {
                ServerLoadResult(mutableListOf())
            } else {
                val arr = JSONArray(raw)
                val list = mutableListOf<ServerConfig>()
                for (i in 0 until arr.length()) list.add(ServerConfig.fromJson(arr.getJSONObject(i)))
                ServerLoadResult(list)
            }
        } catch (e: Exception) {
            ServerLoadResult(mutableListOf(), e)
        }
    }

    /** Compatibility form for background workers; UI should prefer [loadServersResult]. */
    fun loadServers(ctx: Context): MutableList<ServerConfig> = loadServersResult(ctx).servers

    fun saveServers(ctx: Context, servers: List<ServerConfig>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        SecureStorage.putSecret(ctx, FILE, "servers", arr.toString())
    }

    private const val SCREENSHOT_PROTECTION = "protect_sensitive_screenshots"

    /** User choice: screenshots are allowed by default; secret fields remain masked. */
    fun isScreenshotProtectionEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SCREENSHOT_PROTECTION, false)

    fun setScreenshotProtectionEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SCREENSHOT_PROTECTION, enabled).apply()
    }

    internal fun observeScreenshotProtection(ctx: Context, changed: (Boolean) -> Unit): () -> Unit {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SCREENSHOT_PROTECTION || key == null) changed(isScreenshotProtectionEnabled(ctx))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        changed(isScreenshotProtectionEnabled(ctx))
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getLanguage(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("lang", "fa") ?: "fa"

    fun setLanguage(ctx: Context, lang: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("lang", lang).apply()
    }

    fun getThemeMode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("theme_mode", "light") ?: "light"

    fun setThemeMode(ctx: Context, mode: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("theme_mode", mode).apply()
    }

    /** Phase 3 (3-E): v3 onboarding shown once, on first launch. */
    fun isOnboardingSeen(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("onboarding_seen_v3", false)

    fun setOnboardingSeen(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("onboarding_seen_v3", true).apply()
    }

    fun getPollIntervalMs(ctx: Context): Long {
        val sec = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("poll_sec", "30")?.toLongOrNull() ?: 30L
        return sec * 1000L
    }

    fun setPollIntervalSec(ctx: Context, seconds: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("poll_sec", seconds.toString()).apply()
    }

    fun getCfToken(ctx: Context): String =
        SecureStorage.getSecret(ctx, FILE, "cf_token")

    fun setCfToken(ctx: Context, token: String) {
        SecureStorage.putSecret(ctx, FILE, "cf_token", token.trim())
    }

    // ── Vault Master Password & Data Persistence ──

    fun isVaultInitialized(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.contains("vault_canary_enc") && !sp.getString("vault_canary_enc", "").isNullOrEmpty()
    }

    fun setupMasterPassword(ctx: Context, password: String) {
        val canary = EncryptedVault.encrypt("DIDBAN_VAULT_OK", password)
        val initialNotes = EncryptedVault.encrypt("[]", password)
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("vault_canary_enc", canary)
            .putString("vault_notes_enc", initialNotes)
            .commit()
        check(saved) { "Vault initialization could not be persisted" }
    }

    fun verifyMasterPassword(ctx: Context, password: String): Boolean {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val canaryEnc = sp.getString("vault_canary_enc", null) ?: return false
        return try {
            val decrypted = EncryptedVault.decrypt(canaryEnc, password)
            decrypted == "DIDBAN_VAULT_OK"
        } catch (_: Exception) {
            false
        }
    }

    fun loadVaultNotes(ctx: Context, password: String): List<VaultNote> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val rawEncrypted = sp.getString("vault_notes_enc", null) ?: return emptyList()
        require(rawEncrypted.length <= 16 * 1024 * 1024) { "Vault payload is too large" }
        val jsonStr = EncryptedVault.decrypt(rawEncrypted, password)
        require(jsonStr.length <= 8 * 1024 * 1024) { "Vault payload is too large" }
        val arr = JSONArray(jsonStr)
        require(arr.length() <= 1_000) { "Vault note limit exceeded" }
        val list = MutableList(arr.length()) { i -> VaultNote.fromJson(arr.getJSONObject(i)) }
        require(list.map { it.id }.distinct().size == list.size) { "Duplicate vault note ids" }
        require(list.all { it.title.isNotBlank() && it.title.length <= 200 && it.tags.length <= 500 && it.content.length <= 65_536 }) {
            "Vault note fields exceed safe limits"
        }
        return list
    }

    fun saveVaultNotes(ctx: Context, notes: List<VaultNote>, password: String) {
        require(notes.size <= 1_000) { "Vault note limit exceeded" }
        require(notes.map { it.id }.distinct().size == notes.size) { "Duplicate vault note ids" }
        require(notes.all { it.title.isNotBlank() && it.title.length <= 200 && it.tags.length <= 500 && it.content.length <= 65_536 }) {
            "Vault note fields exceed safe limits"
        }
        val arr = JSONArray()
        notes.forEach { arr.put(it.toJson()) }
        val enc = EncryptedVault.encrypt(arr.toString(), password)
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("vault_notes_enc", enc).commit()
        check(saved) { "Vault changes could not be persisted" }
    }

    fun resetVault(ctx: Context) {
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove("vault_canary_enc")
            .remove("vault_notes_enc")
            .commit()
        check(saved) { "Vault reset could not be persisted" }
    }

    // ── Uptime Targets ──

    fun loadUptimeTargets(ctx: Context): List<UptimeTarget> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("uptime_targets", null) ?: return emptyList()
        val list = mutableListOf<UptimeTarget>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(UptimeTarget.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveUptimeTargets(ctx: Context, targets: List<UptimeTarget>) {
        val arr = JSONArray()
        targets.forEach { arr.put(it.toJson()) }
        val saved = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("uptime_targets", arr.toString()).commit()
        check(saved) { "Uptime monitors could not be persisted" }
    }

    // ── Dual-Node Tunnels ──

    fun loadTunnels(ctx: Context): List<TunnelConfig> {
        val raw = SecureStorage.getSecret(ctx, FILE, "didban_tunnels").ifEmpty { return emptyList() }
        val list = mutableListOf<TunnelConfig>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(TunnelConfig.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTunnels(ctx: Context, tunnels: List<TunnelConfig>) {
        val arr = JSONArray()
        tunnels.forEach { arr.put(it.toJson()) }
        SecureStorage.putSecret(ctx, FILE, "didban_tunnels", arr.toString())
    }

    // ── Telegram & Discord Alert Settings ──

    fun getTelegramBotToken(ctx: Context): String =
        SecureStorage.getSecret(ctx, FILE, "tg_bot_token")

    fun setTelegramBotToken(ctx: Context, token: String) {
        SecureStorage.putSecret(ctx, FILE, "tg_bot_token", token.trim())
    }

    fun getTelegramChatId(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("tg_chat_id", "") ?: ""

    fun setTelegramChatId(ctx: Context, chatId: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("tg_chat_id", chatId.trim()).apply()
    }

    fun isTelegramAlertsEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("tg_alerts_enabled", false)

    fun setTelegramAlertsEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("tg_alerts_enabled", enabled).apply()
    }

    fun getDiscordWebhookUrl(ctx: Context): String =
        SecureStorage.getSecret(ctx, FILE, "discord_webhook")

    fun setDiscordWebhookUrl(ctx: Context, url: String) {
        SecureStorage.putSecret(ctx, FILE, "discord_webhook", url.trim())
    }

    fun isDiscordAlertsEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("discord_alerts_enabled", false)

    fun setDiscordAlertsEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("discord_alerts_enabled", enabled).apply()
    }

    fun isAlertTriggerDown(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("alert_trig_down", true)

    fun setAlertTriggerDown(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("alert_trig_down", enabled).apply()
    }

    fun isAlertTriggerSpike(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("alert_trig_spike", true)

    fun setAlertTriggerSpike(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("alert_trig_spike", enabled).apply()
    }

    fun isAlertTriggerTunnel(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("alert_trig_tunnel", true)

    fun setAlertTriggerTunnel(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("alert_trig_tunnel", enabled).apply()
    }
}
