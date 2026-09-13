package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PollSchedule] — the H7 backoff policy for failing servers (JVM).
 */
class PollScheduleTest {

    @Test
    fun healthyServerKeepsUserInterval() {
        assertEquals(30_000L, PollSchedule.nextDelayMs(true, 30_000L))
        assertEquals(5_000L, PollSchedule.nextDelayMs(true, 5_000L))
        assertEquals(120_000L, PollSchedule.nextDelayMs(true, 120_000L))
    }

    @Test
    fun failedProbeDoublesInterval() {
        assertEquals(60_000L, PollSchedule.nextDelayMs(false, 30_000L))
        assertEquals(10_000L, PollSchedule.nextDelayMs(false, 5_000L))
    }

    @Test
    fun backoffIsCapped() {
        // 3 min base -> would double to 6 min, capped at 2 min
        assertEquals(PollSchedule.MAX_BACKOFF_MS, PollSchedule.nextDelayMs(false, 180_000L))
        assertEquals(PollSchedule.MAX_BACKOFF_MS, PollSchedule.nextDelayMs(false, 60_000L))
        // 10 s base doubles to 20 s, under the cap
        assertEquals(20_000L, PollSchedule.nextDelayMs(false, 10_000L))
    }

    @Test
    fun zeroOrNegativeIntervalIsFloored() {
        assertEquals(1_000L, PollSchedule.nextDelayMs(true, 0L))
        assertEquals(2_000L, PollSchedule.nextDelayMs(false, 0L))
        assertEquals(1_000L, PollSchedule.nextDelayMs(true, -500L))
    }

    @Test
    fun recoveryRestoresBaseImmediately() {
        // failed (backoff) -> succeeded (back to base)
        assertEquals(30_000L, PollSchedule.nextDelayMs(true, 30_000L))
    }
}
