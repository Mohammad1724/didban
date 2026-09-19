package org.didban.monitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class MonitoringPhase { STOPPED, STARTING, RUNNING, STOPPING, FAILED }
internal enum class MonitoringFailure { START, STOP, ENGINE }
internal data class MonitoringStatus(
    val phase: MonitoringPhase = MonitoringPhase.STOPPED,
    // Authoritative service state, not the user's requested state.
    val running: Boolean = false,
    val failure: MonitoringFailure? = null
)

/** All lifecycle transitions are serialized on Android's main thread. */
internal class MonitoringState {
    private val mutable = MutableStateFlow(MonitoringStatus())
    val status = mutable.asStateFlow()

    fun requestStart(start: () -> Unit) {
        if (status.value.phase in setOf(MonitoringPhase.STARTING, MonitoringPhase.RUNNING, MonitoringPhase.STOPPING)) return
        mutable.value = MonitoringStatus(MonitoringPhase.STARTING)
        try { start() } catch (_: Exception) { failed(MonitoringFailure.START) }
    }

    fun requestStop(stop: () -> Boolean) {
        if (status.value.phase in setOf(MonitoringPhase.STOPPED, MonitoringPhase.STOPPING)) return
        val wasRunning = status.value.running
        mutable.value = MonitoringStatus(MonitoringPhase.STOPPING, wasRunning)
        try {
            // A false result means Android has no service to stop, not an error.
            if (!stop()) stopped()
        } catch (_: Exception) {
            mutable.value = MonitoringStatus(
                if (wasRunning) MonitoringPhase.RUNNING else MonitoringPhase.FAILED,
                wasRunning, MonitoringFailure.STOP
            )
        }
    }

    fun started() { mutable.value = MonitoringStatus(MonitoringPhase.RUNNING, running = true) }
    fun failed(reason: MonitoringFailure) { mutable.value = MonitoringStatus(MonitoringPhase.FAILED, failure = reason) }
    fun stopped() {
        // Keep a startup/runner failure visible after Android destroys the service.
        if (status.value.phase != MonitoringPhase.FAILED) mutable.value = MonitoringStatus()
    }
}

/** No automatic start on page/app entry. Only explicit UI actions call these methods. */
internal object MonitoringControl {
    val lifecycle = MonitoringState()
    val status = lifecycle.status
    private const val STORE = "didban_monitoring"
    private const val WANTED = "requested"

    fun requested(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(WANTED, false)
    fun setRequested(context: Context, wanted: Boolean) {
        check(context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(WANTED, wanted).commit()) {
            "Unable to save monitoring preference"
        }
    }

    fun start(context: Context) = lifecycle.requestStart {
        val app = context.applicationContext
        setRequested(app, true)
        try {
            ContextCompat.startForegroundService(app, Intent(app, MonitorService::class.java).setAction(MonitorService.ACTION_START))
        } catch (failure: Exception) {
            runCatching { setRequested(app, false) }
            throw failure
        }
    }

    fun stop(context: Context) = lifecycle.requestStop {
        val app = context.applicationContext
        // Save explicit stop before asking Android, so a sticky restart cannot undo it.
        setRequested(app, false)
        app.stopService(Intent(app, MonitorService::class.java))
    }
}
