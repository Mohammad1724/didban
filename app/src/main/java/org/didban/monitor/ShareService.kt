package org.didban.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * سرویس پیش‌زمینهٔ اشتراک اینترنت با VPN.
 *
 * چرا سرویس پیش‌زمینه؟ چون سرور پروکسی باید با خاموش‌شدن صفحه و رفتن اپ به
 * پس‌زمینه زنده بماند؛ در غیر این صورت دستگاه‌های متصل (مثلاً تلویزیون) وسط
 * پخش قطع می‌شوند.
 */
class ShareService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ShareServer? = null
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }
        startSharing()
        return START_NOT_STICKY
    }

    private fun startSharing() {
        val config = Prefs.getShareConfig(this)
        val copy = CommandCopy.forLanguage(Prefs.getLanguage(this))
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARE, copy.shareTitle, NotificationManager.IMPORTANCE_LOW)
        )

        server?.stop()
        val engine = ShareServer(
            config = config,
            dialer = AndroidShareDialer(applicationContext, config.requireVpn),
            scope = scope
        ) { event -> ShareRuntime.publish { it.copy(eventKey = shareEventKey(event)) } }
        server = engine

        if (!engine.start()) {
            ShareRuntime.publish { it.copy(running = false, failure = copy.shareErrorStart, eventKey = null) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        ShareRuntime.reset(engine.boundPort)
        publish(true)

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(copy), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(copy))
        }

        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(TICK_MS)
                publish(true)
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification(CommandCopy.forLanguage(Prefs.getLanguage(this@ShareService))))
            }
        }
    }

    private fun publish(running: Boolean) {
        val engine = server ?: return
        val stats = engine.stats
        val vpn = ShareNetworks.vpnNetwork(this)
        ShareRuntime.publish {
            it.copy(
                running = running && engine.running,
                port = engine.boundPort,
                clients = stats.clientCount,
                uploadBytes = stats.uploadBytes,
                downloadBytes = stats.downloadBytes,
                rejected = stats.rejectedCount,
                peers = stats.peers(),
                vpnActive = vpn != null,
                vpnLabel = ShareNetworks.vpnOwnerLabel(applicationContext, vpn),
                addresses = ShareNetworks.localAddresses(),
                failure = null
            )
        }
    }

    private fun notification(copy: CommandCopy): android.app.Notification {
        val status = ShareRuntime.status.value
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_SHARE)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(copy.shareRunning)
            .setContentText("${status.clients} ${copy.shareClientsShort} · ${shareBytes(status.uploadBytes + status.downloadBytes)}")
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, copy.shareStop, stop)
            .build()
    }

    private fun stopSharing() {
        ticker?.cancel()
        ticker = null
        server?.stop()
        server = null
        ShareRuntime.publish { it.copy(running = false, clients = 0, peers = emptyMap()) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ticker?.cancel()
        server?.stop()
        server = null
        ShareRuntime.publish { it.copy(running = false, clients = 0, peers = emptyMap()) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_SHARE = "didban_share"
        const val NOTIFICATION_ID = 2
        const val ACTION_START = "org.didban.monitor.START_SHARE"
        const val ACTION_STOP = "org.didban.monitor.STOP_SHARE"
        private const val TICK_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, ShareService::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShareService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
