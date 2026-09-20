package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.runtime.rememberUpdatedState

/** Real, cancellable agent benchmark. Rotation/navigation cancels the run rather than restoring a stuck busy flag. */
/** One completed benchmark run. */
data class BenchmarkResult(
    val downloadMbps: Float,
    val uploadMbps: Float,
    val pingMs: Long,
    val jitterMs: Long?,
    val packetLossPct: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
internal fun CommandBandwidthScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit,
    benchmark: suspend (ServerConfig, (BenchmarkProgress) -> Unit) -> BenchmarkResult = { server, report ->
        BandwidthBenchmark().run(server, report)
    }
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    // The shell is the single source of truth; do not silently pick a different saved server.
    val server = initialServer
    val currentServer by rememberUpdatedState(server)
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var liveMbps by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<BenchmarkResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var endpointMissing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun run() {
        val target = currentServer ?: return
        if (running) return
        running = true
        progress = 0f
        liveMbps = 0f
        result = null
        error = null
        endpointMissing = false
        notice = null
        job = scope.launch {
            try {
                val activeRun = currentCoroutineContext()[Job]!!
                result = benchmark(target) { update ->
                    // The engine reports from IO/OkHttp workers; UI writes stay on Main.
                    scope.launch {
                        if (activeRun.isActive) {
                            progress = update.fraction
                            liveMbps = update.mbps
                        }
                    }
                }
                progress = 1f
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val stage = (failure as? BenchmarkFailure)?.stage
                val label = when (stage) {
                    BenchmarkStage.UPLOAD -> copy.upload
                    BenchmarkStage.DOWNLOAD -> copy.download
                    else -> copy.latency
                }
                error = "$label: ${describeAgentToolFailure(failure.cause ?: failure, copy, target, bandwidth = true)}"
                endpointMissing = isMissingEndpointFailure(failure.cause ?: failure)
            } finally { running = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(
                    copy.bandwidth,
                    server?.name ?: copy.noServerSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (server == null) {
            item { CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.selectServer, onSelectServer, actionIcon = Icons.Rounded.Dns) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(copy.currentServer, style = MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Text(server.name, color = CommandColors.textPrimary)
                        CommandSecondaryButton(copy.selectServer, onSelectServer, enabled = !running)
                        Text(
                            copy.bandwidthBody,
                            color = CommandColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        CommandPrimaryButton(
                            if (running) copy.waitingForData else copy.run,
                            ::run,
                            enabled = !running,
                            icon = Icons.Rounded.Speed
                        )
                        if (running) CommandSecondaryButton(copy.cancel, { job?.cancel() })
                    }
                }
            }

            if (running) {
                item {
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(copy.bandwidthRunning, Modifier.weight(1f), color = CommandColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${"%,.1f".format(liveMbps)} Mbps",
                                    color = CommandColors.accent,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry)
                                )
                            }
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    CommandStateBlock(
                        copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE,
                        copy.retry, ::run,
                        secondActionLabel = if (endpointMissing) copy.copyAgentUpdateCommand else null,
                        onSecondAction = if (endpointMissing) {
                            {
                                clipboard.setText(AnnotatedString(AGENT_UPDATE_COMMAND))
                                notice = copy.agentUpdateCommandCopied
                            }
                        } else null
                    )
                }
            }

            if (notice != null) {
                item { Text(notice ?: "", color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
            }

            result?.let { r ->
                item { CommandSectionTitle(copy.bandwidthResult, server?.host ?: "") }
                item {
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                CommandMetricTile(
                                    copy.download,
                                    "${"%,.1f".format(r.downloadMbps)}",
                                    "Mbps",
                                    toneFor(r.downloadMbps),
                                    Modifier.weight(1f)
                                )
                                CommandMetricTile(
                                    copy.upload,
                                    "${"%,.1f".format(r.uploadMbps)}",
                                    "Mbps",
                                    toneFor(r.uploadMbps),
                                    Modifier.weight(1f)
                                )
                            }
                            CommandRule()
                            CommandMetricLine(copy.latency, "${r.pingMs} ms", if (r.pingMs < 150) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION)
                            CommandMetricLine(copy.jitter, r.jitterMs?.let { "$it ms" } ?: "—", when { r.jitterMs == null -> CommandHealthTone.UNKNOWN; r.jitterMs < 40 -> CommandHealthTone.HEALTHY; else -> CommandHealthTone.ATTENTION })
                            CommandMetricLine(
                                copy.packetLoss,
                                "${r.packetLossPct}%",
                                when {
                                    r.packetLossPct == 0 -> CommandHealthTone.HEALTHY
                                    r.packetLossPct < 40 -> CommandHealthTone.ATTENTION
                                    else -> CommandHealthTone.OFFLINE
                                }
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

private fun toneFor(mbps: Float): CommandHealthTone = when {
    mbps >= 50f -> CommandHealthTone.HEALTHY
    mbps >= 10f -> CommandHealthTone.INFO
    mbps > 0f -> CommandHealthTone.ATTENTION
    else -> CommandHealthTone.OFFLINE
}
