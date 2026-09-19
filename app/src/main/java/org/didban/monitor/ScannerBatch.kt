package org.didban.monitor

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

/** Bounded workers, not one coroutine for every domain in an imported file. */
internal suspend fun scanSniCandidates(
    targets: List<Pair<String, Int>>,
    probe: suspend (String, Int) -> RealityProbeResult,
    onProgress: (List<Pair<RealityProbeResult, RealityAssessment>>) -> Unit
): List<Pair<RealityProbeResult, RealityAssessment>> = coroutineScope {
    val plan = targets.distinct().take(ScannerCatalog.MAX_SNI_TARGETS)
    val next = AtomicInteger()
    val lock = Mutex()
    val results = mutableListOf<Pair<RealityProbeResult, RealityAssessment>>()
    val workers = List(minOf(ScannerCatalog.SNI_CONCURRENCY, plan.size)) {
        launch {
            while (true) {
                coroutineContext.ensureActive()
                val index = next.getAndIncrement()
                if (index >= plan.size) break
                val (host, port) = plan[index]
                val result = probe(host, port)
                coroutineContext.ensureActive()
                lock.withLock {
                    results += result to RealityCriteria.evaluate(result)
                    onProgress(results.toList())
                }
            }
        }
    }
    workers.forEach { it.join() }
    results.toList()
}
