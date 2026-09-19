package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/** One foreground lifetime for background fleet polling and scheduled uptime checks. */
class MonitorService : Service() {
    companion object {
        const val CHANNEL_STATUS = "didban_status"
        const val CHANNEL_ALERT = "didban_alerts"
        const val ACTION_START = "org.didban.monitor.START_MONITORING"
        const val ACTION_STOP = "org.didban.monitor.STOP_MONITORING"
        // Existing poller uses actual service state, never a saved/requested toggle.
        val isRunning: Boolean get() = MonitoringControl.status.value.running
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            try { MonitoringControl.setRequested(this, false) }
            catch (_: Exception) {
                // Do not claim success if the explicit-stop preference could not be saved.
                MonitoringControl.lifecycle.requestStop { throw IllegalStateException("Stop preference failed") }
                return START_NOT_STICKY
            }
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        // A stale start or sticky restart cannot override an explicit user stop.
        if (!MonitoringControl.requested(this)) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val copy = CommandCopy.forLanguage(Prefs.getLanguage(this))
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_STATUS, copy.monitorTitle, NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERT, copy.alerts, NotificationManager.IMPORTANCE_HIGH))
            val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL_STATUS)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(copy.monitorOn)
                .setContentText(copy.monitorNotification)
                .setOngoing(true)
                .setContentIntent(openApp)
                .addAction(android.R.drawable.ic_media_pause, copy.monitorStop, stop)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(1, notification)

            // One process-wide poller; start() is idempotent, not another loop.
            PollingCoordinator.start(applicationContext)
            UptimeEngine.start(applicationContext) {
                Handler(Looper.getMainLooper()).post {
                    if (isRunning && !UptimeEngine.monitoring.value) fail(MonitoringFailure.ENGINE)
                }
            }
            MonitoringControl.lifecycle.started()
            return START_STICKY
        } catch (_: Exception) {
            fail(MonitoringFailure.START)
            return START_NOT_STICKY
        }
    }

    private fun fail(reason: MonitoringFailure) {
        runCatching { MonitoringControl.setRequested(this, false) }
        UptimeEngine.stop()
        MonitoringControl.lifecycle.failed(reason)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shutdown() {
        UptimeEngine.stop()
        MonitoringControl.lifecycle.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
