package org.didban.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UptimeSchedulerTest {

    // ── interval clamping ───────────────────────────────────────────────────

    @Test
    fun `clamps intervals below the minimum`() {
        assertEquals(5, UptimeScheduler.clampInterval(0))
        assertEquals(5, UptimeScheduler.clampInterval(3))
        assertEquals(5, UptimeScheduler.clampInterval(-120))
    }

    @Test
    fun `clamps intervals above the maximum`() {
        assertEquals(3600, UptimeScheduler.clampInterval(99999))
    }

    @Test
    fun `keeps valid intervals unchanged`() {
        assertEquals(5, UptimeScheduler.clampInterval(5))
        assertEquals(30, UptimeScheduler.clampInterval(30))
        assertEquals(3600, UptimeScheduler.clampInterval(3600))
    }

    // ── due logic ───────────────────────────────────────────────────────────

    @Test
    fun `never-seen targets are due immediately`() {
        val s = UptimeScheduler()
        assertEquals(listOf(1L, 2L), s.dueNow(now = 1_000L, ids = listOf(1L, 2L), pausedIds = emptySet()))
    }

    @Test
    fun `paused targets are never due`() {
        val s = UptimeScheduler()
        val due = s.dueNow(now = 999_999L, ids = listOf(1L, 2L), pausedIds = setOf(2L))
        assertEquals(listOf(1L), due)
    }

    @Test
    fun `a checked target is not due before its interval elapses`() {
        val s = UptimeScheduler()
        s.markChecked(id = 1L, now = 1_000L, intervalSec = 30)
        assertEquals(emptyList<Long>(), s.dueNow(now = 30_999L, ids = listOf(1L), pausedIds = emptySet()))
        // due exactly at the deadline
        assertEquals(listOf(1L), s.dueNow(now = 31_000L, ids = listOf(1L), pausedIds = emptySet()))
    }

    @Test
    fun `sub-minimum intervals are clamped in the schedule`() {
        val s = UptimeScheduler()
        s.markChecked(id = 1L, now = 0L, intervalSec = 1) // user typed 1s
        assertEquals(emptyList<Long>(), s.dueNow(now = 3_000L, ids = listOf(1L), pausedIds = emptySet()))
        assertEquals(listOf(1L), s.dueNow(now = 5_000L, ids = listOf(1L), pausedIds = emptySet()))
    }

    // ── reschedule / prune ──────────────────────────────────────────────────

    @Test
    fun `reschedule forces an immediate check`() {
        val s = UptimeScheduler()
        s.markChecked(id = 1L, now = 0L, intervalSec = 300)
        assertEquals(emptyList<Long>(), s.dueNow(now = 100L, ids = listOf(1L), pausedIds = emptySet()))
        s.reschedule(1L)
        assertEquals(listOf(1L), s.dueNow(now = 100L, ids = listOf(1L), pausedIds = emptySet()))
    }

    @Test
    fun `prune keeps schedules of remaining targets intact`() {
        val s = UptimeScheduler()
        s.markChecked(id = 1L, now = 0L, intervalSec = 30)
        s.markChecked(id = 2L, now = 0L, intervalSec = 30)
        s.prune(keep = setOf(2L))
        // 2L's schedule is untouched by pruning 1L
        assertEquals(emptyList<Long>(), s.dueNow(now = 10L, ids = listOf(2L), pausedIds = emptySet()))
        assertEquals(listOf(2L), s.dueNow(now = 30_000L, ids = listOf(2L), pausedIds = emptySet()))
    }
}
