package org.didban.monitor

import android.content.Context
import org.json.JSONArray

/** Tiny persistence layer on top of SharedPreferences. */
object Prefs {
    private const val FILE = "didban"

    fun loadServers(ctx: Context): MutableList<ServerConfig> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("servers", null) ?: return mutableListOf()
        val list = mutableListOf<ServerConfig>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(ServerConfig.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveServers(ctx: Context, servers: List<ServerConfig>) {
        val arr = JSONArray()
        servers.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("servers", arr.toString()).apply()
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
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("cf_token", "") ?: ""

    fun setCfToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("cf_token", token.trim()).apply()
    }

    // ── Vault Master Password & Data Persistence ──

    fun isVaultInitialized(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return sp.contains("vault_canary_enc") && !sp.getString("vault_canary_enc", "").isNullOrEmpty()
    }

    fun setupMasterPassword(ctx: Context, password: String) {
        val canary = EncryptedVault.encrypt("DIDBAN_VAULT_OK", password)
        val initialNotes = EncryptedVault.encrypt("[]", password)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("vault_canary_enc", canary)
            .putString("vault_notes_enc", initialNotes)
            .apply()
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
        val jsonStr = EncryptedVault.decrypt(rawEncrypted, password)
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<VaultNote>()
        for (i in 0 until arr.length()) {
            list.add(VaultNote.fromJson(arr.getJSONObject(i)))
        }
        return list
    }

    fun saveVaultNotes(ctx: Context, notes: List<VaultNote>, password: String) {
        val arr = JSONArray()
        notes.forEach { arr.put(it.toJson()) }
        val enc = EncryptedVault.encrypt(arr.toString(), password)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("vault_notes_enc", enc).apply()
    }

    fun resetVault(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove("vault_canary_enc")
            .remove("vault_notes_enc")
            .apply()
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
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("uptime_targets", arr.toString()).apply()
    }

    // ── Dual-Node Tunnels ──

    fun loadTunnels(ctx: Context): List<TunnelConfig> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("didban_tunnels", null) ?: return emptyList()
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
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("didban_tunnels", arr.toString()).apply()
    }

    // ── Telegram & Discord Alert Settings ──

    fun getTelegramBotToken(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("tg_bot_token", "") ?: ""

    fun setTelegramBotToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("tg_bot_token", token.trim()).apply()
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
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("discord_webhook", "") ?: ""

    fun setDiscordWebhookUrl(ctx: Context, url: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString("discord_webhook", url.trim()).apply()
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
