package org.didban.monitor

import android.app.Application
import android.content.Intent
import android.util.Log

class DidbanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CryptoSecurity.ensureInitialized()
        HostKeyTrustStore.init(this)
        // H7: the single poller for all servers runs for the life of the
        // process (see PollingCoordinator for the gating semantics).
        PollingCoordinator.start(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("DidbanCrash", "Uncaught exception in thread: ${thread.name}", throwable)
            // H8: record the crash (encrypted trace + loop counter, synchronous
            // commits) before doing anything else, since the process may die
            // milliseconds later.
            CrashLog.saveCrash(applicationContext, throwable)

            // H8: auto-restart only while the crash-loop threshold has not
            // been reached. Once it has, the process simply dies and the next
            // (manual) launch lands on the diagnostic screen, where the user
            // decides — so a cannot-start loop is bounded and user-controlled.
            val record = CrashLog.readRecord(applicationContext)
            if (record != null && CrashPolicy.shouldAutoRestart(record)) {
                try {
                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } catch (_: Throwable) {
                }
            }

            // H8: do NOT kill the process inline. An inline kill races the
            // activity-launch transaction and can drop it (black screen).
            // Instead, give the new process a short grace period to start,
            // then terminate. This runs on a dedicated thread so it never
            // blocks or re-enters the crashed one.
            Thread {
                try {
                    Thread.sleep(RESTART_GRACE_MS)
                } catch (_: Throwable) {
                }
                try {
                    android.os.Process.killProcess(android.os.Process.myPid())
                } catch (_: Throwable) {
                }
                System.exit(10)
            }.start()
        }
    }

    companion object {
        /** Grace period for the relaunched process to commit before we exit. */
        private const val RESTART_GRACE_MS = 1500L
    }
}
