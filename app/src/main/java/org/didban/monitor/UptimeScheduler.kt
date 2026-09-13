package org.didban.monitor

/**
 * Pure scheduling logic for uptime probes (no Android dependencies, JVM-testable).
 *
 * The engine keeps one [nextCheckAt] entry per target id. [dueNow] returns the
 * ids whose deadline has arrived; [markChecked] schedules the next run using the
 * target's *user-configured* interval (clamped to a sane range so a 0/1-second
 * interval cannot flood the network or the disk).
 */
class UptimeScheduler {

    companion object {
        const val MIN_INTERVAL_SEC = 5
        const val MAX_INTERVAL_SEC = 3600

        fun clampInterval(sec: Int): Int = sec.coerceIn(MIN_INTERVAL_SEC, MAX_INTERVAL_SEC)
    }

    // Accessed from the scheduler loop (IO), finishing check coroutines (IO)
    // and UI mutations (main) - every stateful method is synchronized.
    private val nextCheckAt = HashMap<Long, Long>()

    /**
     * Ids that are due for a check at [now]: not paused, and with no recorded
     * next-run time or a next-run time that has already passed.
     */
    @Synchronized
    fun dueNow(now: Long, ids: Collection<Long>, pausedIds: Set<Long>): List<Long> {
        val out = ArrayList<Long>()
        for (id in ids) {
            if (id in pausedIds) continue
            if ((nextCheckAt[id] ?: 0L) <= now) out.add(id)
        }
        return out
    }

    /** Record a finished check and schedule the next one now + clamped interval. */
    @Synchronized
    fun markChecked(id: Long, now: Long, intervalSec: Int) {
        nextCheckAt[id] = now + clampInterval(intervalSec) * 1000L
    }

    /** Force an immediate check on the next tick (new/edited/unpaused target). */
    @Synchronized
    fun reschedule(id: Long) {
        nextCheckAt[id] = 0L
    }

    /** Drop entries for targets that no longer exist (deleted). */
    @Synchronized
    fun prune(keep: Set<Long>) {
        nextCheckAt.keys.retainAll(keep)
    }
}
