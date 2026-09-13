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

/**
 * Foreground "background monitoring" service.
 *
 * Since H7 this service no longer polls: the single process-lifetime
 * [PollingCoordinator] owns all /api/metrics polling and feeds [Repo]. The
 * service's job now is:
 *  - keep the process alive in the background (foreground notification) so
 *    the coordinator keeps polling while the app is closed;
 *  - expose [isRunning], which the coordinator uses to gate alert
 *    notifications (engine off = no alerts, exactly as before);
 *  - start/stop [UptimeEngine] with its lifetime (unchanged since H1).
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_STATUS = "didban_status"
        const val CHANNEL_ALERT = "didban_alerts"

        @Volatile
        var isRunning: Boolean = false
    }

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
        // The single poller runs for the life of the process (started by
        // DidbanApplication); this is idempotent. While this service is
        // alive the coordinator polls in the background and posts alerts.
        PollingCoordinator.start(applicationContext)
        // Uptime probes run in this same long-lived engine (H1): they no
        // longer depend on the UptimeScreen being open, and each target's
        // user-configured interval is honored by the UptimeEngine scheduler.
        UptimeEngine.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        UptimeEngine.stop()
        super.onDestroy()
    }
}
