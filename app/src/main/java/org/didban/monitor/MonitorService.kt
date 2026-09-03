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
        private const val POLL_MS = 30_000L
        private const val ALERT_COOLDOWN_MS = 10 * 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val lastAlertAt = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Monitoring status", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Didban — دیدبان")
            .setContentText("Monitoring servers")
            .setOngoing(true)
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
                val servers = Prefs.loadServers(applicationContext)
                for (s in servers) {
                    try {
                        val m = ApiClient().metrics(s)
                        Repo.set(s.id, metrics = m)
                        checkThresholds(s, m)
                    } catch (e: Exception) {
                        Repo.set(s.id, error = e.message ?: "error")
                        alert(s.name, "${s.name}: ${e.message}")
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    private fun checkThresholds(s: ServerConfig, m: Metrics) {
        if (m.cpuUsage >= s.cpuAlert) {
            alert(s.name, "${s.name}: CPU ${m.cpuUsage.toInt()}%")
        }
        if (m.memPct >= s.memAlert) {
            alert(s.name, "${s.name}: RAM ${m.memPct.toInt()}%")
        }
    }

    private fun alert(serverName: String, text: String) {
        val now = System.currentTimeMillis()
        val key = "$serverName:$text"
        if (now - (lastAlertAt[key] ?: 0L) < ALERT_COOLDOWN_MS) return
        lastAlertAt[key] = now

        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
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
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
