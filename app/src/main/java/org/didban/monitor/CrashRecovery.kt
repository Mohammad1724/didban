package org.didban.monitor

/**
 * Crash-loop accounting for startup recovery (H8) — pure logic, unit-tested
 * on the JVM ([CrashRecoveryTest]).
 *
 * Semantics:
 *  - A crash "consecutive" to the previous one is one that happens less than
 *    [CRASH_WINDOW_MS] after it (i.e. the app died before the user could do
 *    anything, the classic cannot-start loop). Any gap longer than the
 *    window resets the counter to 1: an app that ran for minutes and then
 *    crashed is a single crash, not a loop.
 *  - When [CRASH_LOOP_THRESHOLD] consecutive startup crashes are recorded,
 *    the crash handler stops auto-restarting — the process simply dies and
 *    the next (manual) launch lands on the diagnostic screen. The user
 *    decides (Reset & Launch), so the loop is bounded and user-controlled.
 */
object CrashPolicy {

    /** Crashes closer together than this are counted as one loop. */
    const val CRASH_WINDOW_MS = 10_000L

    /** This many consecutive crashes disables auto-restart. */
    const val CRASH_LOOP_THRESHOLD = 3

    /** Upper bound for the stored trace (Compose stacks can be huge). */
    const val MAX_TRACE_CHARS = 65_536

    data class CrashRecord(
        val lastCrashAt: Long,
        val consecutiveCount: Int
    )

    /** Update the record when a crash occurs. [now] is the crash timestamp. */
    fun recordCrash(now: Long, prev: CrashRecord?): CrashRecord {
        val consecutive =
            prev != null && now - prev.lastCrashAt < CRASH_WINDOW_MS
        return CrashRecord(now, if (consecutive) prev.consecutiveCount + 1 else 1)
    }

    /**
     * Whether the crash handler may auto-restart the app. Once the
     * threshold of consecutive startup crashes is reached, restart is
     * suppressed — the next launch must be a user action on the diagnostic
     * screen (whose Reset clears the counter).
     */
    fun shouldAutoRestart(record: CrashRecord): Boolean =
        record.consecutiveCount < CRASH_LOOP_THRESHOLD
}
