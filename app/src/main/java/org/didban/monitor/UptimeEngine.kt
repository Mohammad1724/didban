package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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

enum class UptimeFailureKind {
    KEYWORD_NOT_FOUND,
    HTTP_STATUS,
    TARGET_UNRESOLVED,
    PRIVATE_NETWORK,
    CERTIFICATE_EXPIRED,
    CONNECTION
}

data class UptimeFailure(
    val kind: UptimeFailureKind,
    val detail: String = ""
) {
    fun localized(copy: CommandCopy): String = when (kind) {
        UptimeFailureKind.KEYWORD_NOT_FOUND -> copy.upKeywordNotFound.replace("%1", detail)
        UptimeFailureKind.HTTP_STATUS -> copy.upHttpFailure.replace("%1", detail)
        UptimeFailureKind.TARGET_UNRESOLVED -> copy.upTargetUnresolved
        UptimeFailureKind.PRIVATE_NETWORK -> copy.upPrivateNetworkRequired
        UptimeFailureKind.CERTIFICATE_EXPIRED -> copy.upCertificateExpired
        UptimeFailureKind.CONNECTION -> copy.upConnectionFailed.replace("%1", detail.ifBlank { copy.upCheckFailed })
    }

    fun toJson() = JSONObject().apply {
        put("kind", kind.name)
        if (detail.isNotBlank()) put("detail", detail)
    }

    companion object {
        fun fromJson(o: JSONObject): UptimeFailure? = runCatching {
            UptimeFailure(
                kind = UptimeFailureKind.valueOf(o.optString("kind")),
                detail = o.optString("detail")
            )
        }.getOrNull()
    }
}

data class UptimeIncident(
    val startTime: Long,
    var endTime: Long? = null,
    val error: String = "",
    val failure: UptimeFailure? = null
) {
    fun localizedError(copy: CommandCopy): String = failure?.localized(copy) ?: error

    val durationSec: Long
        get() {
            val end = endTime ?: System.currentTimeMillis()
            return ((end - startTime) / 1000).coerceAtLeast(0)
        }

    fun toJson() = JSONObject().apply {
        put("st", startTime)
        if (endTime != null) put("et", endTime)
        put("err", error)
        failure?.let { put("failure", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject) = UptimeIncident(
            startTime = o.optLong("st"),
            endTime = if (o.has("et")) o.optLong("et") else null,
            error = o.optString("err"),
            failure = o.optJSONObject("failure")?.let { UptimeFailure.fromJson(it) }
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

private class UptimeFailureException(val failure: UptimeFailure) : Exception()

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
    @Synchronized
    fun start(ctx: Context, onFailure: () -> Unit = {}) {
        val app = ctx.applicationContext
        if (engineScope?.isActive == true) return
        ensureLoaded(app)
        liveTargets.value.forEach { scheduler.reschedule(it.id) }
        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = runScope
        _monitoring.value = true
        runScope.launch {
            var failed = false
            try { schedulerLoop(app, runScope) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally {
                synchronized(UptimeEngine) {
                    if (engineScope === runScope) {
                        _monitoring.value = false
                        engineScope = null
                    }
                }
                runScope.cancel()
                if (failed) onFailure()
            }
        }
    }

    /** Stop future scheduled work. Blocking DNS/socket work may need time to unwind. */
    @Synchronized
    fun stop() {
        val previous = engineScope
        engineScope = null
        _monitoring.value = false
        previous?.cancel()
    }

    private fun normalize(t: UptimeTarget): UptimeTarget {
        t.intervalSec = UptimeScheduler.clampInterval(t.intervalSec)
        return t
    }

    private suspend fun schedulerLoop(app: Context, runScope: CoroutineScope) {
        // Each run owns its set: an old cancelled worker cannot clear a restarted run's IDs.
        val inFlight = java.util.Collections.synchronizedSet(HashSet<Long>())
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
                runScope.launch {
                    try {
                        sem.withPermit {
                            currentCoroutineContext().ensureActive()
                            checkNow(app, t)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (currentCoroutineContext().isActive) {
                            scheduler.markChecked(t.id, System.currentTimeMillis(), t.intervalSec)
                        }
                    } finally { inFlight.remove(id) }
                }
            }
            delay(TICK_MS)
        }
    }

    // ── Mutations (UI entry points) ─────────────────────────────────────────

    /** Add or replace a target; it will be checked immediately. */
    @Synchronized
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
    @Synchronized
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
    @Synchronized
    fun togglePause(ctx: Context, id: Long) {
        val app = ctx.applicationContext
        ensureLoaded(app)
        val t = liveTargets.value.firstOrNull { it.id == id } ?: return
        val next = t.copy(isPaused = !t.isPaused)
        liveTargets.value = liveTargets.value.map { if (it.id == id) next else it }
        if (!next.isPaused) scheduler.reschedule(id)
        commit(app)
    }

    /** Probe a snapshot: mutating objects already held by StateFlow suppresses UI emissions. */
    suspend fun checkNow(ctx: Context, target: UptimeTarget): Heartbeat {
        val app = ctx.applicationContext
        val checked = target.copy(heartbeats = target.heartbeats.toMutableList(),
            incidents = target.incidents.map { it.copy() }.toMutableList())
        val result = checkTarget(checked, app)
        val checkContext = currentCoroutineContext()
        synchronized(UptimeEngine) {
            checkContext.ensureActive()
            // A deleted/edited/paused target must not be resurrected by an old check.
            if (liveTargets.value.any { it === target }) {
                liveTargets.value = liveTargets.value.map { if (it === target) checked else it }
                scheduler.markChecked(target.id, System.currentTimeMillis(), target.intervalSec)
                commit(app)
            }
        }
        return result
    }

    private fun commit(app: Context) {
        Prefs.saveUptimeTargets(app, liveTargets.value)
    }

    suspend fun checkTarget(target: UptimeTarget, ctx: Context? = null): Heartbeat = withContext(Dispatchers.IO) {
        val copy = ctx?.let { CommandCopy.forLanguage(Prefs.getLanguage(it)) } ?: CommandCopyEn
        val t0 = System.currentTimeMillis()
        var isUp = false
        var failure: UptimeFailure? = null

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
                                    failure = UptimeFailure(UptimeFailureKind.KEYWORD_NOT_FOUND, target.keyword)
                                }
                            } else {
                                isUp = true
                            }
                        } else {
                            isUp = false
                            failure = UptimeFailure(UptimeFailureKind.HTTP_STATUS, resp.code.toString())
                        }
                    }
                }

                "TCP", "PING" -> {
                    val port = if (target.port > 0) target.port else 80
                    val addresses = InetAddress.getAllByName(target.target.trim()).toList()
                    if (addresses.isEmpty()) throw UptimeFailureException(UptimeFailure(UptimeFailureKind.TARGET_UNRESOLVED))
                    if (!target.allowPrivateNetwork) {
                        if (!addresses.all(NetworkTargetPolicy::isPublicAddress)) throw UptimeFailureException(UptimeFailure(UptimeFailureKind.PRIVATE_NETWORK))
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
                    if (addresses.isEmpty()) throw UptimeFailureException(UptimeFailure(UptimeFailureKind.TARGET_UNRESOLVED))
                    if (!target.allowPrivateNetwork) {
                        if (!addresses.all(NetworkTargetPolicy::isPublicAddress)) throw UptimeFailureException(UptimeFailure(UptimeFailureKind.PRIVATE_NETWORK))
                    }
                    val cert = SslInspector.inspect(target.target.trim(), port, 5000, addresses.first())
                    isUp = !cert.isExpired
                    if (cert.isExpired) failure = UptimeFailure(UptimeFailureKind.CERTIFICATE_EXPIRED)
                }

                else -> isUp = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: UptimeFailureException) {
            isUp = false
            failure = known.failure
        } catch (e: Exception) {
            isUp = false
            failure = UptimeFailure(UptimeFailureKind.CONNECTION, e.message.orEmpty())
        }

        // Cancelling monitoring is not a failed target and must not produce a down alert.
        currentCoroutineContext().ensureActive()
        val latency = System.currentTimeMillis() - t0
        val statusInt = if (isUp) 1 else 0
        val failureText = failure?.localized(copy) ?: copy.upCheckFailed

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
            target.incidents.add(UptimeIncident(startTime = System.currentTimeMillis(), error = failure?.detail.orEmpty(), failure = failure))
            if (ctx != null) {
                notifyUser(
                    ctx,
                    copy,
                    copy.upServiceDownTitle.replace("%1", target.name),
                    copy.upServiceDownBody.replace("%1", failureText),
                    target.id.toInt()
                )
                AlertEngine.dispatchAlert(
                    ctx,
                    AlertType.UPTIME_FAIL,
                    target.name,
                    copy.upServiceDownBody.replace("%1", failureText),
                    AlertLevel.CRITICAL
                )
            }
        } else if (previousStatus == 0 && statusInt == 1) {
            // Transition DOWN -> UP (Recovery)
            val ongoing = target.incidents.lastOrNull { it.endTime == null }
            ongoing?.endTime = System.currentTimeMillis()
            if (ctx != null) {
                val dur = ongoing?.durationSec ?: 0
                notifyUser(
                    ctx,
                    copy,
                    copy.upServiceRecoveredTitle.replace("%1", target.name),
                    copy.upServiceRecoveredBody.replace("%1", dur.toString()),
                    target.id.toInt()
                )
                AlertEngine.dispatchAlert(
                    ctx,
                    AlertType.UPTIME_RECOVERED,
                    target.name,
                    copy.upServiceRecoveredBody.replace("%1", dur.toString()),
                    AlertLevel.RESOLVED
                )
            }
        }

        hb
    }

    private fun notifyUser(ctx: Context, copy: CommandCopy, title: String, message: String, notificationId: Int) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val chan = NotificationChannel("didban_uptime", copy.uptime, NotificationManager.IMPORTANCE_HIGH)
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
