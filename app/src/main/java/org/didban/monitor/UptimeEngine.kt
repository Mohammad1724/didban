package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

data class Heartbeat(
    val status: Int, // 1 = Up, 0 = Down
    val latencyMs: Long,
    val time: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().apply {
        put("s", status)
        put("l", latencyMs)
        put("t", time)
    }

    companion object {
        fun fromJson(o: JSONObject) = Heartbeat(
            status = o.optInt("s", 1),
            latencyMs = o.optLong("l", 0),
            time = o.optLong("t", System.currentTimeMillis())
        )
    }
}

data class UptimeIncident(
    val startTime: Long,
    var endTime: Long? = null,
    val error: String = ""
) {
    val durationSec: Long
        get() {
            val end = endTime ?: System.currentTimeMillis()
            return ((end - startTime) / 1000).coerceAtLeast(0)
        }

    fun toJson() = JSONObject().apply {
        put("st", startTime)
        if (endTime != null) put("et", endTime)
        put("err", error)
    }

    companion object {
        fun fromJson(o: JSONObject) = UptimeIncident(
            startTime = o.optLong("st"),
            endTime = if (o.has("et")) o.optLong("et") else null,
            error = o.optString("err")
        )
    }
}

data class UptimeTarget(
    val id: Long,
    var name: String,
    var type: String, // "HTTP", "TCP", "PING", "KEYWORD", "SSL"
    var target: String,
    var port: Int = 80,
    var intervalSec: Int = 30,
    var keyword: String = "",
    var isPaused: Boolean = false,
    var lastStatus: Int = -1, // -1 = pending, 1 = up, 0 = down
    var lastLatencyMs: Long = 0,
    var lastChecked: Long = 0,
    val heartbeats: MutableList<Heartbeat> = mutableListOf(),
    val incidents: MutableList<UptimeIncident> = mutableListOf()
) {
    val uptimePct: Float
        get() {
            if (heartbeats.isEmpty()) return 100f
            val upCount = heartbeats.count { it.status == 1 }
            return (upCount.toFloat() / heartbeats.size) * 100f
        }

    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("target", target)
        put("port", port)
        put("intervalSec", intervalSec)
        put("keyword", keyword)
        put("isPaused", isPaused)
        put("lastStatus", lastStatus)
        put("lastLatencyMs", lastLatencyMs)
        put("lastChecked", lastChecked)

        val ha = JSONArray()
        heartbeats.takeLast(30).forEach { ha.put(it.toJson()) }
        put("heartbeats", ha)

        val ia = JSONArray()
        incidents.takeLast(10).forEach { ia.put(it.toJson()) }
        put("incidents", ia)
    }

    companion object {
        fun fromJson(o: JSONObject): UptimeTarget {
            val t = UptimeTarget(
                id = o.optLong("id", System.currentTimeMillis()),
                name = o.optString("name"),
                type = o.optString("type", "HTTP"),
                target = o.optString("target"),
                port = o.optInt("port", 80),
                intervalSec = o.optInt("intervalSec", 30),
                keyword = o.optString("keyword"),
                isPaused = o.optBoolean("isPaused", false),
                lastStatus = o.optInt("lastStatus", -1),
                lastLatencyMs = o.optLong("lastLatencyMs", 0),
                lastChecked = o.optLong("lastChecked", 0)
            )
            val ha = o.optJSONArray("heartbeats")
            if (ha != null) {
                for (i in 0 until ha.length()) {
                    t.heartbeats.add(Heartbeat.fromJson(ha.getJSONObject(i)))
                }
            }
            val ia = o.optJSONArray("incidents")
            if (ia != null) {
                for (i in 0 until ia.length()) {
                    t.incidents.add(UptimeIncident.fromJson(ia.getJSONObject(i)))
                }
            }
            return t
        }
    }
}

// ── Background Uptime Monitor Runner ─────────────────────────────────────────

object UptimeEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    val liveTargets = MutableStateFlow<List<UptimeTarget>>(emptyList())

    suspend fun checkTarget(target: UptimeTarget, ctx: Context? = null): Heartbeat = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        var isUp = false
        var errMsg = ""

        try {
            when (target.type.uppercase()) {
                "HTTP", "HTTPS", "KEYWORD" -> {
                    var url = target.target.trim()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    val req = Request.Builder().url(url).build()
                    httpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (resp.isSuccessful) {
                            if (target.type.uppercase() == "KEYWORD" && target.keyword.isNotBlank()) {
                                if (body.contains(target.keyword)) {
                                    isUp = true
                                } else {
                                    isUp = false
                                    errMsg = "Keyword '${target.keyword}' not found"
                                }
                            } else {
                                isUp = true
                            }
                        } else {
                            isUp = false
                            errMsg = "HTTP ${resp.code}"
                        }
                    }
                }

                "TCP", "PING" -> {
                    val port = if (target.port > 0) target.port else 80
                    Socket().use { s ->
                        s.connect(InetSocketAddress(target.target.trim(), port), 5000)
                        isUp = true
                    }
                }

                "SSL" -> {
                    val port = if (target.port > 0) target.port else 443
                    val cert = SslInspector.inspect(target.target.trim(), port, 5000)
                    isUp = !cert.isExpired
                    if (cert.isExpired) errMsg = "Certificate expired"
                }

                else -> isUp = true
            }
        } catch (e: Exception) {
            isUp = false
            errMsg = e.message ?: "Connection failed"
        }

        val latency = System.currentTimeMillis() - t0
        val statusInt = if (isUp) 1 else 0

        // Handle State Transition & Notifications
        val previousStatus = target.lastStatus
        target.lastStatus = statusInt
        target.lastLatencyMs = latency
        target.lastChecked = System.currentTimeMillis()

        val hb = Heartbeat(status = statusInt, latencyMs = latency)
        target.heartbeats.add(hb)
        if (target.heartbeats.size > 30) {
            target.heartbeats.removeAt(0)
        }

        if (previousStatus == 1 && statusInt == 0) {
            // Transition UP -> DOWN
            target.incidents.add(UptimeIncident(startTime = System.currentTimeMillis(), error = errMsg))
            if (ctx != null) {
                notifyUser(ctx, "🔴 Service Down: ${target.name}", "Error: $errMsg", target.id.toInt())
            }
        } else if (previousStatus == 0 && statusInt == 1) {
            // Transition DOWN -> UP (Recovery)
            val ongoing = target.incidents.lastOrNull { it.endTime == null }
            ongoing?.endTime = System.currentTimeMillis()
            if (ctx != null) {
                val dur = ongoing?.durationSec ?: 0
                notifyUser(ctx, "🟢 Service Recovered: ${target.name}", "Service is back online (was down for ${dur}s)", target.id.toInt())
            }
        }

        hb
    }

    private fun notifyUser(ctx: Context, title: String, message: String, notificationId: Int) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel("didban_uptime", "Uptime Alerts", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(chan)
            }
            val notif = NotificationCompat.Builder(ctx, "didban_uptime")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(notificationId, notif)
        } catch (_: Exception) {}
    }
}
