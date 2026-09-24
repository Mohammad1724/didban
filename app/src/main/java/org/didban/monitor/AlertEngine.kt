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
        .dns(PublicOnlyDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun validatedDiscordWebhook(raw: String): String {
        val uri = NetworkTargetPolicy.requirePublicHttps(
            raw.trim(),
            setOf("discord.com", "canary.discord.com", "ptb.discord.com", "discordapp.com")
        )
        require(uri.rawPath.startsWith("/api/webhooks/")) { "Invalid Discord webhook path" }
        return uri.toASCIIString()
    }

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
    suspend fun testTelegram(
        botToken: String,
        chatId: String,
        copy: CommandCopy = CommandCopyEn
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
            val token = botToken.trim()
            val chat = chatId.trim()
            if (token.isEmpty() || chat.isEmpty()) {
                return@withContext Pair(false, copy.alertsMissingCredentials)
            }

            val url = "https://api.telegram.org/bot$token/sendMessage"
            val text = """
                ${copy.alertsTestTelegramTitle}
                ━━━━━━━━━━━━━━━━━━
                ${copy.alertsTestTelegramBody}
                📊 <b>${copy.alertsFieldSeverity}:</b> ${copy.alertsTestTelegramMode}
                ⏱ <b>${copy.alertsFieldTime}:</b> ${getFormattedTime()}
                ━━━━━━━━━━━━━━━━━━
                <i>${copy.alertsTestTelegramFooter}</i>
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
                    // Do not surface the provider's raw body: gateways may
                    // echo request metadata and it is not needed for diagnosis.
                    if (resp.isSuccessful) {
                        Pair(true, copy.alertsTelegramTestSuccess)
                    } else {
                        Pair(false, copy.alertsTelegramHttpFailure.replace("%1", resp.code.toString()))
                    }
                }
            } catch (e: Exception) {
                Pair(false, copy.alertsConnectionFailure.replace("%1", SecretRedactor.redact(e.message ?: "network error", listOf(token))))
            }
        }

    /**
     * Test Discord Webhook connection
     */
    suspend fun testDiscord(
        webhookUrl: String,
        copy: CommandCopy = CommandCopyEn
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
            val url = try {
                validatedDiscordWebhook(webhookUrl)
            } catch (_: Exception) {
                return@withContext Pair(false, copy.alertsDiscordWebhookInvalid)
            }

            val embed = JSONObject().apply {
                put("title", copy.alertsTestDiscordTitle)
                put("description", copy.alertsTestDiscordDescription)
                put("color", 0x00F2FE) // Cyber Cyan
                val fields = JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", copy.alertsFieldStatus)
                        put("value", copy.alertsTestDiscordStatus)
                        put("inline", true)
                    })
                    put(JSONObject().apply {
                        put("name", copy.alertsTestDiscordTimestamp)
                        put("value", getFormattedTime())
                        put("inline", true)
                    })
                }
                put("fields", fields)
                put("footer", JSONObject().apply {
                    put("text", copy.alertsTestDiscordFooter)
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
                        Pair(true, copy.alertsDiscordTestSuccess)
                    } else {
                        Pair(false, copy.alertsDiscordHttpFailure.replace("%1", resp.code.toString()))
                    }
                }
            } catch (e: Exception) {
                Pair(false, copy.alertsConnectionFailure.replace("%1", SecretRedactor.redact(e.message ?: "network error", listOf(url))))
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
        val copy = CommandCopy.forLanguage(Prefs.getLanguage(ctx))
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
            AlertLevel.CRITICAL -> copy.alertsLevelCritical
            AlertLevel.WARNING -> copy.alertsLevelWarning
            AlertLevel.RESOLVED -> copy.alertsLevelResolved
            AlertLevel.INFO -> copy.alertsLevelInfo
        }

        val eventTitle = when (type) {
            AlertType.SERVER_DOWN -> copy.alertsEventServerDown
            AlertType.SERVER_RECOVERED -> copy.alertsEventServerRecovered
            AlertType.CPU_SPIKE -> copy.alertsEventCpuSpike
            AlertType.RAM_SPIKE -> copy.alertsEventRamSpike
            AlertType.DISK_SPIKE -> copy.alertsEventDiskSpike
            AlertType.TUNNEL_DROP -> copy.alertsEventTunnelDrop
            AlertType.TUNNEL_UP -> copy.alertsEventTunnelUp
            AlertType.UPTIME_FAIL -> copy.alertsEventUptimeFail
            AlertType.UPTIME_RECOVERED -> copy.alertsEventUptimeRecovered
            AlertType.SSL_EXPIRING -> copy.alertsEventSslExpiring
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
                    📌 <b>${copy.alertsFieldTarget}:</b> $targetName
                    ⚡ <b>${copy.alertsFieldEvent}:</b> $eventTitle
                    📝 <b>${copy.alertsFieldDetails}:</b> $targetDetail
                    ⏱ <b>${copy.alertsFieldTime}:</b> ${getFormattedTime()}
                    ━━━━━━━━━━━━━━━━━━
                    <i>${copy.alertsTestTelegramFooter}</i>
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
            if (webhook.isNotEmpty()) {
                val embed = JSONObject().apply {
                    put("title", "$levelEmoji Didban Alert — $eventTitle")
                    put("description", "**${copy.alertsFieldTarget}:** `$targetName`\n**${copy.alertsFieldDetails}:** $targetDetail")
                    put("color", embedColor)
                    val fields = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", copy.alertsFieldSeverity)
                            put("value", levelName)
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", copy.alertsFieldTimestamp)
                            put("value", getFormattedTime())
                            put("inline", true)
                        })
                    }
                    put("fields", fields)
                    put("footer", JSONObject().apply {
                        put("text", copy.alertsTestTelegramFooter)
                    })
                }

                val json = JSONObject().apply {
                    put("username", "Didban Sentinel")
                    put("embeds", JSONArray().apply { put(embed) })
                }

                try {
                    val validatedWebhook = validatedDiscordWebhook(webhook)
                    val reqBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                    val request = Request.Builder().url(validatedWebhook).post(reqBody).build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) {}
            }
        }
    }
}
