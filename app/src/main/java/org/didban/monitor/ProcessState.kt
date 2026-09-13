package org.didban.monitor

/**
 * Minimal process-foreground tracker (no lifecycle dependency needed).
 *
 * [PollingCoordinator] uses this to preserve the pre-H7 semantics of the
 * "background monitoring" toggle: when the monitoring service is OFF,
 * servers are polled only while the app is actually in the foreground (the
 * screens stay live); when it is ON, polling continues in the background.
 *
 * Counted from Activity onStart/onStop callbacks (main thread only).
 */
object ProcessState {

    @Volatile
    var foreground: Boolean = false
        private set

    private var startedCount = 0

    fun activityStarted() {
        if (startedCount++ == 0) foreground = true
    }

    fun activityStopped() {
        if (startedCount > 0) startedCount--
        if (startedCount == 0) foreground = false
    }
}
