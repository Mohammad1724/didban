package org.didban.monitor

/**
 * Polling cadence with backoff for unreachable servers (H7).
 *
 * A healthy server is probed every [baseMs] (the user-configured interval).
 * A failed probe doubles the wait so a downed server is not hammered — with
 * per-probe connect timeouts, a fleet of dead servers would otherwise stall
 * the whole polling cycle. The wait is capped at [MAX_BACKOFF_MS]. A single
 * successful probe restores the user's interval immediately, so recovery is
 * detected fast.
 *
 * Pure logic — unit-tested on the JVM ([PollScheduleTest]).
 */
object PollSchedule {

    /** Never probe a failing server more often than every 2 minutes. */
    const val MAX_BACKOFF_MS = 120_000L

    /** Floor so a user-entered 0s interval cannot busy-loop the poller. */
    private const val MIN_INTERVAL_MS = 1_000L

    fun nextDelayMs(success: Boolean, baseMs: Long): Long {
        val base = baseMs.coerceAtLeast(MIN_INTERVAL_MS)
        return if (success) base else (base * 2L).coerceAtMost(MAX_BACKOFF_MS)
    }
}
