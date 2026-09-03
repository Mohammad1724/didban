package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that polls every server's /api/metrics every 30 seconds,
 * updates the shared [Repo] (which the UI observes) and posts alerts when a
 * server is unreachable or crosses its CPU/memory thresholds.
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_STATUS = "didban_status"
        const val CHANNEL_ALERT = "didban_alerts"
        private const val ALERT_COOLDOWN_MS = 10 * 60_000L

        @Volatile
        var isRunning: Boolean = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val lastAlertAt = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Monitoring status", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Didban — دیدبان")
            .setContentText("Monitoring servers")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notif)
        }
        if (pollJob?.isActive != true) startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        pollJob = scope.launch {
            while (isActive) {
                val pollMs = Prefs.getPollIntervalMs(applicationContext)
                val servers = Prefs.loadServers(applicationContext)
                for (s in servers) {
                    val t0 = System.currentTimeMillis()
                    try {
                        val m = ApiClient().metrics(s)
                        val ms = (System.currentTimeMillis() - t0).toFloat()
                        Repo.set(s.id, metrics = m, latencyMs = ms)
                        checkThresholds(s, m)
                    } catch (e: Exception) {
                        Repo.set(s.id, error = e.message ?: "error", latencyMs = -1f)
                        alert(s, "${s.name}: ${e.message}")
                    }
                }
                delay(pollMs)
            }
        }
    }

    private fun checkThresholds(s: ServerConfig, m: Metrics) {
        if (m.cpuUsage >= s.cpuAlert) {
            alert(s, "${s.name}: CPU ${m.cpuUsage.toInt()}%")
        }
        if (m.memPct >= s.memAlert) {
            alert(s, "${s.name}: RAM ${m.memPct.toInt()}%")
        }
    }

    private fun alert(server: ServerConfig, text: String) {
        val now = System.currentTimeMillis()
        val key = "${server.name}:$text"
        if (now - (lastAlertAt[key] ?: 0L) < ALERT_COOLDOWN_MS) return
        lastAlertAt[key] = now

        val openIntent = Intent(this, MainActivity::class.java)
            .putExtra("server_id", server.id)
        val pi = PendingIntent.getActivity(
            this, (server.id % 100000L).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Didban")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java).notify(text.hashCode(), notif)
    }

    override fun onDestroy() {
        isRunning = false
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
