package org.didban.monitor

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The single poller for all servers (H7).
 *
 * Before H7 three loops polled /api/metrics concurrently — MonitorService
 * (background engine), ServersScreen and DashboardScreen — and every
 * request built a fresh OkHttpClient. With N servers that meant up to 3N
 * requests per interval, no connection reuse, and three inconsistent
 * "latency / online" sources.
 *
 * Now one process-lifetime loop drives every server:
 *  - per-server "next check" timestamps with backoff for failing servers
 *    ([PollSchedule]), so a dead server is probed at most every 2 minutes;
 *  - results feed [Repo] — every screen observes the same state;
 *  - alert notifications (local + [AlertEngine]) fire only while the
 *    background engine is on ([MonitorService.isRunning]) — exactly the
 *    pre-H7 semantics (engine off = live data on screens, no alerts);
 *  - while the engine is off, polling additionally requires the app to be
 *    in the foreground ([ProcessState]), so "off" still means "no
 *    background polling";
 *  - client cache hygiene: when a server's key changes (re-pin, port, TLS
 *    toggle) or a server is deleted, the stale cached OkHttp client is
 *    evicted so it can never serve a connection under an old pin.
 *
 * The alert logic below (local notification with 10-minute cooldown,
 * SERVER_DOWN / SERVER_RECOVERED / CPU / RAM spike dispatch) is moved
 * verbatim from MonitorService — behavior is unchanged, only the owner.
 */
object PollingCoordinator {

    private const val TICK_MS = 2_000L
    private const val ALERT_COOLDOWN_MS = 10 * 60_000L
    private const val CHANNEL_ALERT = "didban_alerts" // same channel MonitorService creates

    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null

    private val nextCheckAt = ConcurrentHashMap<Long, Long>()
    private val lastServerKey = ConcurrentHashMap<Long, ClientKey>()
    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    /** Idempotent: starts the polling loop for the life of the process. */
    fun start(ctx: Context) {
        if (scope != null) return
        contextRef = ctx.applicationContext
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MS)
            }
        }
    }

    /** Force the server to be re-probed on the next tick (manual refresh). */
    fun requestNow(serverId: Long) {
        nextCheckAt[serverId] = 0L
    }

    private suspend fun tick() {
        val ctx = contextRef ?: return
        // Preserve pre-H7 semantics: no background polling while the app is
        // backgrounded and the monitoring engine is off.
        if (!MonitorService.isRunning && !ProcessState.foreground) return

        val servers = Prefs.loadServers(ctx)
        val pollMs = Prefs.getPollIntervalMs(ctx)
        val now = System.currentTimeMillis()

        // Server list / config changed → drop the stale client cache entry
        // before any request goes out under the new config.
        val alive = servers.map { it.id }.toHashSet()
        for (id in nextCheckAt.keys) if (id !in alive) {
            nextCheckAt.remove(id)
            lastServerKey.remove(id)?.let { HttpClientPool.evict(it) }
        }
        for (s in servers) {
            val key = HttpClientPool.keyFor(s)
            val prev = lastServerKey[s.id]
            if (prev != key) {
                // First sighting (prev == null): just record. Otherwise the
                // server's connection key changed (re-pin, port, TLS toggle)
                // — the stale client must not serve it any more.
                if (prev != null) HttpClientPool.evict(prev)
                lastServerKey[s.id] = key
            }
        }

        for (s in servers) {
            if ((nextCheckAt[s.id] ?: 0L) > now) continue
            probe(ctx, s, pollMs)
        }
    }

    private suspend fun probe(ctx: Context, s: ServerConfig, pollMs: Long) {
        val t0 = System.currentTimeMillis()
        val prevState = Repo.states.value[s.id]
        try {
            val m = ApiClient().metrics(s)
            val ms = (System.currentTimeMillis() - t0).toFloat()
            Repo.set(s.id, metrics = m, latencyMs = ms)
            nextCheckAt[s.id] = System.currentTimeMillis() + PollSchedule.nextDelayMs(true, pollMs)
            if (MonitorService.isRunning) {
                if (prevState?.error != null) {
                    scope?.launch {
                        AlertEngine.dispatchAlert(
                            ctx,
                            AlertType.SERVER_RECOVERED,
                            s.name,
                            "سرور مجدداً آنلاین شد و معیارهای سلامت نرمال هستند.",
                            AlertLevel.RESOLVED
                        )
                    }
                }
                checkThresholds(ctx, s, m)
            }
        } catch (e: Exception) {
            Repo.set(s.id, error = e.message ?: "error", latencyMs = -1f)
            nextCheckAt[s.id] = System.currentTimeMillis() + PollSchedule.nextDelayMs(false, pollMs)
            if (MonitorService.isRunning) {
                alert(ctx, s, "${s.name}: ${e.message}")
                if (Prefs.isAlertTriggerDown(ctx)) {
                    scope?.launch {
                        AlertEngine.dispatchAlert(
                            ctx,
                            AlertType.SERVER_DOWN,
                            s.name,
                            "سرور در دسترس نیست یا اتصال قطع شد: ${e.message}",
                            AlertLevel.CRITICAL
                        )
                    }
                }
            }
        }
    }

    // ── Threshold checks (moved verbatim from MonitorService) ─────────────

    private fun checkThresholds(ctx: Context, s: ServerConfig, m: Metrics) {
        if (m.cpuUsage >= s.cpuAlert) {
            alert(ctx, s, "${s.name}: CPU ${m.cpuUsage.toInt()}%")
            if (Prefs.isAlertTriggerSpike(ctx)) {
                scope?.launch {
                    AlertEngine.dispatchAlert(
                        ctx,
                        AlertType.CPU_SPIKE,
                        s.name,
                        "مصرف پردازنده به ${m.cpuUsage.toInt()}% افزایش یافت (آستانه: ${s.cpuAlert.toInt()}%)",
                        AlertLevel.WARNING
                    )
                }
            }
        }
        if (m.memPct >= s.memAlert) {
            alert(ctx, s, "${s.name}: RAM ${m.memPct.toInt()}%")
            if (Prefs.isAlertTriggerSpike(ctx)) {
                scope?.launch {
                    AlertEngine.dispatchAlert(
                        ctx,
                        AlertType.RAM_SPIKE,
                        s.name,
                        "مصرف حافظه رم به ${m.memPct.toInt()}% افزایش یافت (آستانه: ${s.memAlert.toInt()}%)",
                        AlertLevel.WARNING
                    )
                }
            }
        }
    }

    // ── Local alert notification (moved verbatim from MonitorService) ─────

    private fun alert(ctx: Context, server: ServerConfig, text: String) {
        val now = System.currentTimeMillis()
        val key = "${server.name}:$text"
        if (now - (lastAlertAt[key] ?: 0L) < ALERT_COOLDOWN_MS) return
        lastAlertAt[key] = now

        val appCtx = ctx.applicationContext
        val openIntent = Intent(appCtx, MainActivity::class.java)
            .putExtra("server_id", server.id)
        val pi = PendingIntent.getActivity(
            appCtx, (server.id % 100000L).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(appCtx, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Didban")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        appCtx.getSystemService(NotificationManager::class.java).notify(text.hashCode(), notif)
    }
}
