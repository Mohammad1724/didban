package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
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
    var allowPrivateNetwork: Boolean = false,
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
        put("allowPrivateNetwork", allowPrivateNetwork)
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
                allowPrivateNetwork = o.optBoolean("allowPrivateNetwork", false),
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
    private val publicHttpClient = OkHttpClient.Builder()
        .dns(PublicOnlyDns)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val privateHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ── Single source of truth ─────────────────────────────────────────────
    // The engine (driven by MonitorService while the monitoring engine is on)
    // owns the target list in memory. UI observes [liveTargets]; all mutations
    // go through the mutate/upsert/remove/togglePause functions below, which
    // persist and emit. Same-process singleton => no read-modify-write races
    // between the screen and the background loop.
    val liveTargets = MutableStateFlow<List<UptimeTarget>>(emptyList())
    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val scheduler = UptimeScheduler()
    // Checked coroutines remove on IO threads; the loop adds on IO; stop()
    // clears on main - synchronized set keeps this race-free.
    private val inFlight = java.util.Collections.synchronizedSet(HashSet<Long>())
    private var engineScope: CoroutineScope? = null

    private const val TICK_MS = 2_000L
    private const val MAX_CONCURRENT_CHECKS = 5

    /** Load targets from disk into memory if memory is empty (idempotent). */
    fun ensureLoaded(ctx: Context) {
        if (liveTargets.value.isEmpty()) {
            liveTargets.value = Prefs.loadUptimeTargets(ctx).map { normalize(it) }
        }
    }

    /** Start the background scheduler (idempotent). Called by MonitorService. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (engineScope?.isActive == true) return
        ensureLoaded(app)
        _monitoring.value = true
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope!!.launch { schedulerLoop(app) }
    }

    /** Stop the background scheduler. Called by MonitorService on destroy. */
    fun stop() {
        _monitoring.value = false
        engineScope?.cancel()
        engineScope = null
        inFlight.clear()
    }

    private fun normalize(t: UptimeTarget): UptimeTarget {
        t.intervalSec = UptimeScheduler.clampInterval(t.intervalSec)
        return t
    }

    private suspend fun schedulerLoop(app: Context) {
        val sem = Semaphore(MAX_CONCURRENT_CHECKS)
        while (currentCoroutineContext().isActive) {
            // Reconcile with disk: backup restore or another writer may have
            // changed the id set. Only act when the sets differ, so normal
            // in-memory heartbeat updates are never clobbered.
            val disk = Prefs.loadUptimeTargets(app)
            val diskIds = disk.map { it.id }.toHashSet()
            val memIds = liveTargets.value.map { it.id }.toHashSet()
            if (diskIds != memIds) {
                liveTargets.value = disk.map { normalize(it) }
                inFlight.clear()
            }

            val targets = liveTargets.value
            scheduler.prune(targets.map { it.id }.toHashSet())
            val now = System.currentTimeMillis()
            val paused = targets.filter { it.isPaused }.map { it.id }.toHashSet()
            val due = scheduler.dueNow(now, targets.map { it.id }, paused)

            for (id in due) {
                val t = targets.firstOrNull { it.id == id } ?: continue
                if (!inFlight.add(id)) continue // previous check still running
                engineScope!!.launch {
                    sem.withPermit {
                        try {
                            checkTarget(t, app)
                            scheduler.markChecked(t.id, System.currentTimeMillis(), t.intervalSec)
                        } catch (_: Exception) {
                            // checkTarget never throws (it records errors as
                            // down-heartbeats); defensive re-schedule anyway.
                            scheduler.markChecked(t.id, System.currentTimeMillis(), t.intervalSec)
                        } finally {
                            inFlight.remove(id)
                            commit(app)
                        }
                    }
                }
            }
            delay(TICK_MS)
        }
    }

    // ── Mutations (UI entry points) ─────────────────────────────────────────

    /** Add or replace a target; it will be checked immediately. */
    fun upsert(ctx: Context, target: UptimeTarget) {
        val app = ctx.applicationContext
        ensureLoaded(app)
        normalize(target)
        val list = liveTargets.value.toMutableList()
        val idx = list.indexOfFirst { it.id == target.id }
        if (idx >= 0) list[idx] = target else list.add(target)
        liveTargets.value = list
        scheduler.reschedule(target.id)
        commit(app)
    }

    /** Remove a target and its schedule entry. */
    fun remove(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        ensureLoaded(app)
        val next = liveTargets.value.filter { it.id != id }
        // Persist first: a storage failure must not make the monitor disappear
        // from the live UI while leaving it on disk to return after restart.
        Prefs.saveUptimeTargets(app, next)
        liveTargets.value = next
        scheduler.prune(next.map { it.id }.toHashSet())
    }

    /** Toggle pause; unpausing triggers an immediate check. */
    fun togglePause(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        ensureLoaded(app)
        val t = liveTargets.value.firstOrNull { it.id == id } ?: return
        t.isPaused = !t.isPaused
        if (!t.isPaused) scheduler.reschedule(id)
        commit(app)
    }

    /** Manual "Test Now": one immediate check, reschedules normally. */
    suspend fun checkNow(ctx: Context, target: UptimeTarget) {
        val app = ctx.applicationContext
        checkTarget(target, app)
        scheduler.markChecked(target.id, System.currentTimeMillis(), target.intervalSec)
        commit(app)
    }

    private fun commit(app: Context) {
        liveTargets.value = liveTargets.value.toList() // new instance => UI recomposes
        Prefs.saveUptimeTargets(app, liveTargets.value)
    }

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
                    val validatedUrl = NetworkTargetPolicy.requirePublicWebUrl(url).toASCIIString()
                    val req = Request.Builder().url(validatedUrl).build()
                    val client = if (target.allowPrivateNetwork) privateHttpClient else publicHttpClient
                    client.newCall(req).execute().use { resp ->
                        val body = BoundedResponseReader.readUtf8(resp.body, BoundedResponseReader.STANDARD_BYTES)
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
                    val addresses = InetAddress.getAllByName(target.target.trim()).toList()
                    require(addresses.isNotEmpty()) { "Target did not resolve" }
                    if (!target.allowPrivateNetwork) {
                        require(addresses.all(NetworkTargetPolicy::isPublicAddress)) { "Private network target requires explicit permission" }
                    }
                    Socket().use { s ->
                        // Connect to the already validated address to avoid a
                        // second DNS lookup between policy and connection.
                        s.connect(InetSocketAddress(addresses.first(), port), 5000)
                        isUp = true
                    }
                }

                "SSL" -> {
                    val port = if (target.port > 0) target.port else 443
                    val addresses = InetAddress.getAllByName(target.target.trim()).toList()
                    require(addresses.isNotEmpty()) { "Target did not resolve" }
                    if (!target.allowPrivateNetwork) {
                        require(addresses.all(NetworkTargetPolicy::isPublicAddress)) { "Private network target requires explicit permission" }
                    }
                    val cert = SslInspector.inspect(target.target.trim(), port, 5000, addresses.first())
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
                AlertEngine.dispatchAlert(
                    ctx,
                    AlertType.UPTIME_FAIL,
                    target.name,
                    "پایش سلامت ناموفق بود: $errMsg",
                    AlertLevel.CRITICAL
                )
            }
        } else if (previousStatus == 0 && statusInt == 1) {
            // Transition DOWN -> UP (Recovery)
            val ongoing = target.incidents.lastOrNull { it.endTime == null }
            ongoing?.endTime = System.currentTimeMillis()
            if (ctx != null) {
                val dur = ongoing?.durationSec ?: 0
                notifyUser(ctx, "🟢 Service Recovered: ${target.name}", "Service is back online (was down for ${dur}s)", target.id.toInt())
                AlertEngine.dispatchAlert(
                    ctx,
                    AlertType.UPTIME_RECOVERED,
                    target.name,
                    "سرویس با موفقیت به مدار بازگشت (مدت زمان قطعی: ${dur} ثانیه)",
                    AlertLevel.RESOLVED
                )
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
