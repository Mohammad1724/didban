package org.didban.monitor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class AlertLevel {
    INFO,
    WARNING,
    CRITICAL,
    RESOLVED
}

enum class AlertType {
    SERVER_DOWN,
    SERVER_RECOVERED,
    CPU_SPIKE,
    RAM_SPIKE,
    DISK_SPIKE,
    TUNNEL_DROP,
    TUNNEL_UP,
    UPTIME_FAIL,
    UPTIME_RECOVERED,
    SSL_EXPIRING
}

object AlertEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 5-minute cooldown per target and alert type to prevent spamming
    private val alertCooldowns = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 5 * 60_000L

    fun clearCooldowns() {
        alertCooldowns.clear()
    }

    private fun getFormattedTime(): String {
        return try {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date())
        } catch (_: Exception) {
            System.currentTimeMillis().toString()
        }
    }

    /**
     * Test Telegram Bot connection
     */
    suspend fun testTelegram(botToken: String, chatId: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val token = botToken.trim()
            val chat = chatId.trim()
            if (token.isEmpty() || chat.isEmpty()) {
                return@withContext Pair(false, "Bot Token یا Chat ID نمی‌تواند خالی باشد")
            }

            val url = "https://api.telegram.org/bot$token/sendMessage"
            val text = """
                🛰️ <b>دیدبان — تست ارتباط موفق</b>
                ━━━━━━━━━━━━━━━━━━
                ✅ اتصال ربات تلگرام با موفقیت برقرار شد!
                📊 <b>حالت:</b> هشدارهای بلادرنگ فعال است.
                ⏱ <b>زمان:</b> ${getFormattedTime()}
                ━━━━━━━━━━━━━━━━━━
                <i>Didban Sentinel Alert Engine</i>
            """.trimIndent()

            val json = JSONObject().apply {
                put("chat_id", chat)
                put("text", text)
                put("parse_mode", "HTML")
            }

            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val reqBody = json.toString().toRequestBody(mediaType)
                val request = Request.Builder().url(url).post(reqBody).build()
                httpClient.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string() ?: ""
                    if (resp.isSuccessful) {
                        Pair(true, "پیام تست تلگرام با موفقیت ارسال شد! 🚀")
                    } else {
                        Pair(false, "خطای تلگرام: HTTP ${resp.code} - $respBody")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "خطا در اتصال به سرور تلگرام: ${e.message}")
            }
        }

    /**
     * Test Discord Webhook connection
     */
    suspend fun testDiscord(webhookUrl: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val url = webhookUrl.trim()
            if (url.isEmpty() || !url.startsWith("http")) {
                return@withContext Pair(false, "آدرس وبهوک دیسکورد نامعتبر است")
            }

            val embed = JSONObject().apply {
                put("title", "🛰️ Didban Sentinel — Connection Test")
                put("description", "✅ Discord Webhook connection established successfully!\nReal-time ops alerts are now active.")
                put("color", 0x00F2FE) // Cyber Cyan
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "Status")
                        put("value", "ONLINE 🟢")
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", "Timestamp")
                        put("value", getFormattedTime())
                        put("inline", true)
                    })
                }
                put("fields", fields)
                put("footer", JSONObject().apply {
                    put("text", "Didban Monitoring Engine")
                })
            }

            val json = JSONObject().apply {
                put("username", "Didban Sentinel")
                put("embeds", JSONArray().apply { put(embed) })
            }

            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val reqBody = json.toString().toRequestBody(mediaType)
                val request = Request.Builder().url(url).post(reqBody).build()
                httpClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful || resp.code == 204) {
                        Pair(true, "پیام تست دیسکورد با موفقیت ارسال شد! 🚀")
                    } else {
                        Pair(false, "خطای دیسکورد: HTTP ${resp.code}")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "خطا در اتصال به دیسکورد: ${e.message}")
            }
        }

    /**
     * Dispatches alert to all enabled channels (Telegram / Discord)
     */
    suspend fun dispatchAlert(
        ctx: Context,
        type: AlertType,
        targetName: String,
        targetDetail: String,
        level: AlertLevel = AlertLevel.WARNING,
        forceBypassCooldown: Boolean = false
    ) = withContext(Dispatchers.IO) {
        // 1. Check cooldown
        val key = "${type.name}:$targetName"
        val now = System.currentTimeMillis()
        if (!forceBypassCooldown) {
            val last = alertCooldowns[key] ?: 0L
            if (now - last < COOLDOWN_MS) return@withContext
        }
        alertCooldowns[key] = now

        val tgEnabled = Prefs.isTelegramAlertsEnabled(ctx)
        val discordEnabled = Prefs.isDiscordAlertsEnabled(ctx)

        if (!tgEnabled && !discordEnabled) return@withContext

        val levelEmoji = when (level) {
            AlertLevel.CRITICAL -> "🚨"
            AlertLevel.WARNING -> "⚠️"
            AlertLevel.RESOLVED -> "🟢"
            AlertLevel.INFO -> "ℹ️"
        }

        val levelName = when (level) {
            AlertLevel.CRITICAL -> "CRITICAL"
            AlertLevel.WARNING -> "WARNING"
            AlertLevel.RESOLVED -> "RESOLVED"
            AlertLevel.INFO -> "INFO"
        }

        val eventTitle = when (type) {
            AlertType.SERVER_DOWN -> "Server Unreachable / Down"
            AlertType.SERVER_RECOVERED -> "Server Back Online"
            AlertType.CPU_SPIKE -> "High CPU Spike"
            AlertType.RAM_SPIKE -> "High Memory Pressure"
            AlertType.DISK_SPIKE -> "High Disk Usage"
            AlertType.TUNNEL_DROP -> "Dual-Node Tunnel Dropped"
            AlertType.TUNNEL_UP -> "Dual-Node Tunnel Active"
            AlertType.UPTIME_FAIL -> "Service Probe Failed"
            AlertType.UPTIME_RECOVERED -> "Service SLA Restored"
            AlertType.SSL_EXPIRING -> "SSL Certificate Expiring Soon"
        }

        val embedColor = when (level) {
            AlertLevel.CRITICAL -> 0xEF4444 // Red
            AlertLevel.WARNING -> 0xF59E0B  // Amber
            AlertLevel.RESOLVED -> 0x10B981 // Green
            AlertLevel.INFO -> 0x00F2FE     // Cyan
        }

        // 2. Dispatch Telegram
        if (tgEnabled) {
            val token = Prefs.getTelegramBotToken(ctx)
            val chatId = Prefs.getTelegramChatId(ctx)
            if (token.isNotEmpty() && chatId.isNotEmpty()) {
                val tgHtml = """
                    $levelEmoji <b>Didban Alert — $levelName</b>
                    ━━━━━━━━━━━━━━━━━━
                    📌 <b>Target:</b> $targetName
                    ⚡ <b>Event:</b> $eventTitle
                    📝 <b>Details:</b> $targetDetail
                    ⏱ <b>Time:</b> ${getFormattedTime()}
                    ━━━━━━━━━━━━━━━━━━
                    <i>Didban Sentinel Monitoring</i>
                """.trimIndent()

                try {
                    val url = "https://api.telegram.org/bot$token/sendMessage"
                    val json = JSONObject().apply {
                        put("chat_id", chatId)
                        put("text", tgHtml)
                        put("parse_mode", "HTML")
                    }
                    val reqBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url(url).post(reqBody).build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {}
            }
        }

        // 3. Dispatch Discord Webhook
        if (discordEnabled) {
            val webhook = Prefs.getDiscordWebhookUrl(ctx)
            if (webhook.isNotEmpty() && webhook.startsWith("http")) {
                val embed = JSONObject().apply {
                    put("title", "$levelEmoji Didban Alert — $eventTitle")
                    put("description", "**Target:** `$targetName`\n**Details:** $targetDetail")
                    put("color", embedColor)
                    val fields = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "Severity")
                            put("value", levelName)
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", "Timestamp")
                            put("value", getFormattedTime())
                            put("inline", true)
                        })
                    }
                    put("fields", fields)
                    put("footer", JSONObject().apply {
                        put("text", "Didban Sentinel Alert Engine")
                    })
                }

                val json = JSONObject().apply {
                    put("username", "Didban Sentinel")
                    put("embeds", JSONArray().apply { put(embed) })
                }

                try {
                    val reqBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url(webhook).post(reqBody).build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {}
            }
        }
    }
}
