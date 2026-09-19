package org.didban.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

enum class RefreshPhase { IDLE, RUNNING, COMPLETE, EMPTY, TIMED_OUT, INTERRUPTED }

data class RefreshState(
    val phase: RefreshPhase = RefreshPhase.IDLE,
    val targets: List<Long> = emptyList(),
    val results: Map<Long, Boolean> = emptyMap(),
    val finishedAt: Long = 0L
) {
    val running: Boolean get() = phase == RefreshPhase.RUNNING
    val succeeded: Int get() = results.values.count { it }
    val failed: Int get() = results.values.count { !it }
    val pending: Int get() = targets.size - results.size
}

/**
 * A manual refresh has its own identity and completion, independent of cached
 * Repo timestamps and automatic polling. Repeated taps coalesce, work is bounded,
 * and neither timeout nor cancellation can be reported as a successful refresh.
 */
internal class ManualRefreshController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 30_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val mutableState = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = mutableState.asStateFlow()

    @Synchronized
    fun request(ids: List<Long>, probe: suspend (Long) -> Boolean): Boolean {
        if (mutableState.value.running) return false
        val targets = ids.distinct()
        mutableState.value = RefreshState(
            phase = if (targets.isEmpty()) RefreshPhase.EMPTY else RefreshPhase.RUNNING,
            targets = targets
        )
        if (targets.isEmpty()) return true
        val job = scope.launch {
            val completed = withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    val permits = Semaphore(4)
                    targets.forEach { id ->
                        launch {
                            permits.withPermit {
                                val success = try {
                                    probe(id)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    false
                                }
                                mutableState.update { it.copy(results = it.results + (id to success)) }
                            }
                        }
                    }
                }
                true
            } ?: false
            mutableState.update {
                it.copy(phase = when {
                    !completed -> RefreshPhase.TIMED_OUT
                    it.pending > 0 -> RefreshPhase.INTERRUPTED
                    else -> RefreshPhase.COMPLETE
                }, finishedAt = clock())
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause != null) mutableState.update {
                if (it.running) it.copy(phase = RefreshPhase.INTERRUPTED, finishedAt = clock()) else it
            }
        }
        return true
    }
}
