package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CrashPolicy] — the H8 crash-loop accounting (JVM).
 */
class CrashRecoveryTest {

    private fun rec(at: Long, count: Int) = CrashPolicy.CrashRecord(at, count)

    @Test
    fun firstCrashStartsCountAtOne() {
        val r = CrashPolicy.recordCrash(1_000L, null)
        assertEquals(CrashPolicy.CrashRecord(1_000L, 1), r)
    }

    @Test
    fun crashInsideWindowIncrementsCount() {
        val r1 = CrashPolicy.recordCrash(1_000L, null)
        val r2 = CrashPolicy.recordCrash(5_000L, r1)
        val r3 = CrashPolicy.recordCrash(9_999L, r2)
        assertEquals(2, r2.consecutiveCount)
        assertEquals(3, r3.consecutiveCount)
        assertEquals(9_999L, r3.lastCrashAt)
    }

    @Test
    fun crashAfterWindowResetsCount() {
        val r1 = CrashPolicy.recordCrash(1_000L, null)
        // 11s later: outside the 10s window
        val r2 = CrashPolicy.recordCrash(1_000L + CrashPolicy.CRASH_WINDOW_MS + 1_000L, r1)
        assertEquals(1, r2.consecutiveCount)
    }

    @Test
    fun windowBoundaryIsNotConsecutive() {
        // exactly the window distance is NOT inside the window (strict <)
        val r1 = CrashPolicy.recordCrash(1_000L, null)
        val r2 = CrashPolicy.recordCrash(1_000L + CrashPolicy.CRASH_WINDOW_MS, r1)
        assertEquals(1, r2.consecutiveCount)
    }

    @Test
    fun autoRestartAllowedBelowThreshold() {
        assertTrue(CrashPolicy.shouldAutoRestart(rec(0L, 1)))
        assertTrue(CrashPolicy.shouldAutoRestart(rec(0L, CrashPolicy.CRASH_LOOP_THRESHOLD - 1)))
    }

    @Test
    fun autoRestartSuppressedAtThreshold() {
        assertFalse(CrashPolicy.shouldAutoRestart(rec(0L, CrashPolicy.CRASH_LOOP_THRESHOLD)))
        assertFalse(CrashPolicy.shouldAutoRestart(rec(0L, CrashPolicy.CRASH_LOOP_THRESHOLD + 5)))
    }

    @Test
    fun fullLoopScenarioDiesAfterThreeConsecutiveCrashes() {
        // crash -> restart -> crash -> restart -> crash -> NO restart
        var record: CrashPolicy.CrashRecord? = null
        var now = 0L
        val restarts = mutableListOf<Boolean>()
        repeat(5) {
            record = CrashPolicy.recordCrash(now, record)
            restarts.add(CrashPolicy.shouldAutoRestart(record!!))
            now += 2_000L // each crash-restart cycle ~2s, inside the window
        }
        assertEquals(
            listOf(true, true, false, false, false),
            restarts
        )
    }

    @Test
    fun traceCapIsSane() {
        assertTrue(CrashPolicy.MAX_TRACE_CHARS > 10_000)
        assertTrue(CrashPolicy.MAX_TRACE_CHARS < 1_000_000)
    }
}
