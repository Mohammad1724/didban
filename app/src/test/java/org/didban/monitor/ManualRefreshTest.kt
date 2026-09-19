package org.didban.monitor

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ManualRefreshTest {
    private suspend fun ManualRefreshController.finished(): RefreshState =
        withTimeout(2000) { state.first { !it.running } }

    @Test fun `running is immediate and rapid taps coalesce until actual response`() = runBlocking {
        val response = CompletableDeferred<Boolean>()
        val refresh = ManualRefreshController(this, clock = { 123L })
        assertTrue(refresh.request(listOf(1)) { response.await() })
        assertTrue(refresh.state.value.running)
        assertEquals(0, refresh.state.value.results.size)
        assertFalse(refresh.request(listOf(1)) { error("duplicate") })
        response.complete(true)
        val final = refresh.finished()
        assertEquals(RefreshPhase.COMPLETE, final.phase)
        assertEquals(1, final.succeeded)
        assertEquals(123L, final.finishedAt)
    }

    @Test fun `empty fleet makes no requests and does not claim success`() = runBlocking {
        val refresh = ManualRefreshController(this)
        refresh.request(emptyList()) { error("must not probe") }
        assertEquals(RefreshPhase.EMPTY, refresh.state.value.phase)
        assertFalse(refresh.state.value.running)
    }

    @Test fun `mixed outcomes and thrown errors are counted without leaking messages`() = runBlocking {
        val refresh = ManualRefreshController(this)
        refresh.request(listOf(1, 2, 3, 3)) { when (it) {
            1L -> true
            2L -> false
            else -> error("sensitive remote error")
        } }
        val final = refresh.finished()
        assertEquals(3, final.targets.size)
        assertEquals(1, final.succeeded)
        assertEquals(2, final.failed)
        assertEquals(0, final.pending)
        assertFalse(final.toString().contains("sensitive"))
    }

    @Test fun `timeout preserves completed results and marks pending targets`() = runBlocking {
        val refresh = ManualRefreshController(this, timeoutMs = 100)
        refresh.request(listOf(1, 2)) { if (it == 1L) true else { awaitCancellation() } }
        val final = refresh.finished()
        assertEquals(RefreshPhase.TIMED_OUT, final.phase)
        assertEquals(1, final.succeeded)
        assertEquals(1, final.pending)
    }

    @Test fun `new refresh cannot borrow success from a previous batch`() = runBlocking {
        val refresh = ManualRefreshController(this)
        refresh.request(listOf(1)) { true }
        refresh.finished()
        val response = CompletableDeferred<Boolean>()
        refresh.request(listOf(1)) { response.await() }
        assertTrue(refresh.state.value.results.isEmpty())
        assertEquals(0L, refresh.state.value.finishedAt)
        response.complete(false)
        assertEquals(1, refresh.finished().failed)
    }

    @Test fun `parallel requests are limited to four`() = runBlocking {
        val refresh = ManualRefreshController(this)
        val started = AtomicInteger()
        val allFour = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        refresh.request((1L..20L).toList()) {
            if (started.incrementAndGet() == 4) allFour.complete(Unit)
            release.await()
            true
        }
        withTimeout(1000) { allFour.await() }
        yield()
        assertEquals(4, started.get())
        release.complete(Unit)
        assertEquals(20, refresh.finished().succeeded)
    }

    @Test fun `scope cancellation clears spinner without inventing success`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val refresh = ManualRefreshController(scope)
        refresh.request(listOf(1)) { awaitCancellation() }
        scope.cancel()
        val final = refresh.finished()
        assertEquals(RefreshPhase.INTERRUPTED, final.phase)
        assertEquals(0, final.succeeded)
    }

    @Test fun `cached list does not hide loading or show its previous completion time`() {
        val state = commandLoadState(true, null, 123L, 7)
        assertTrue(state.running)
        assertEquals(0L, state.finishedAt)
        assertTrue(state.results.isEmpty())
        assertEquals(1, commandLoadState(false, "HTTP 500", 124L, 7).failed)
        assertEquals(1, commandLoadState(false, null, 125L, 7).succeeded)
    }
    @Test fun `cancelled individual probe is not reported as a completed batch`() = runBlocking {
        val refresh = ManualRefreshController(this)
        refresh.request(listOf(1)) { throw CancellationException("cancelled") }
        val final = refresh.finished()
        assertEquals(RefreshPhase.INTERRUPTED, final.phase)
        assertEquals(1, final.pending)
    }

}
