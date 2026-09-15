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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

/**
 * Connection-quality benchmark: latency, jitter, loss and real download /
 * upload throughput measured against one agent.
 *
 * Restored into the command shell — the route was dropped when the shell was
 * rebuilt, while the agent endpoints (/api/bandwidth/download and /upload)
 * and this measurement logic were both still in the tree.
 *
 * The upload leg needs the agent-side body cap exemption for
 * /api/bandwidth/upload: the global 2 MiB hardening limit used to reject the
 * 100 MiB payload before the handler ever saw it.
 */

/** One completed benchmark run. */
data class BenchmarkResult(
    val downloadMbps: Float,
    val uploadMbps: Float,
    val pingMs: Long,
    val jitterMs: Long,
    val packetLossPct: Int,
    val timestamp: Long = System.currentTimeMillis()
)

private const val BENCH_BYTES = 104_857_600L // 100 MiB, matches the agent cap

@Composable
fun CommandBandwidthScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }
    val servers = remember { Prefs.loadServers(context) }

    // Rotation-safe: the selected target and the last result survive a
    // configuration change instead of silently resetting mid-benchmark.
    var selectedId by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.id ?: servers.firstOrNull()?.id)
    }
    val server = selectedId?.let { id -> servers.firstOrNull { it.id == id } }

    var running by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var liveMbps by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<BenchmarkResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run() {
        val target = server ?: return
        if (running) return
        running = true
        progress = 0f
        liveMbps = 0f
        result = null
        error = null
        val startWall = System.currentTimeMillis()

        scope.launch {
            // ── 1. Latency, jitter and loss: five TCP handshakes ────────────
            val pings = mutableListOf<Long>()
            var lost = 0
            val host = target.host.trim()
            val port = if (target.port > 0) target.port else 80
            for (i in 1..5) {
                progress = (i * 0.09f).coerceAtMost(0.45f)
                val t0 = System.currentTimeMillis()
                try {
                    withContext(Dispatchers.IO) {
                        Socket().use { it.connect(InetSocketAddress(host, port), 2000) }
                    }
                    pings.add(System.currentTimeMillis() - t0)
                } catch (_: Exception) {
                    lost++
                }
                delay(150)
            }
            val avgPing = if (pings.isNotEmpty()) pings.average().toLong() else 999L
            val jitter = if (pings.size > 1) pings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average().toLong() else 2L
            val lossPct = (lost * 100) / 5

            // ── 2. Download: stream from the agent's source endpoint ────────
            val downloadMbps: Float
            try {
                val call = api.openStreamingCall(target, "/api/bandwidth/download?bytes=$BENCH_BYTES")
                val t0 = System.nanoTime()
                var lastUi = System.currentTimeMillis()
                var bytes = 0L
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw bandwidthFailure(resp.code)
                    val source = resp.body?.source() ?: throw ApiException("empty body")
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(buf)
                        if (n == -1) break
                        bytes += n
                        progress = (0.45f + 0.35f * bytes / BENCH_BYTES).coerceAtMost(0.8f)
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 250) {
                            lastUi = now
                            liveMbps = (bytes * 8.0 / (now - startWall) / 1e6).toFloat()
                        }
                    }
                }
                downloadMbps = (bytes * 8.0 / ((System.nanoTime() - t0) / 1e9) / 1e6).toFloat()
            } catch (e: Exception) {
                running = false
                error = describe(e, copy, upload = false)
                return@launch
            } finally {
                // The streaming client is short-lived: release its dispatcher
                // once the call has finished or failed.
                api.releaseStreaming()
            }

            // ── 3. Upload: stream to the agent's sink endpoint ──────────────
            val uploadMbps: Float
            try {
                val t0 = System.nanoTime()
                var lastUi = System.currentTimeMillis()
                val body = BenchmarkUploadBody(BENCH_BYTES) { written ->
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 250) {
                        lastUi = now
                        liveMbps = (written * 8.0 / (now - startWall) / 1e6).toFloat()
                        progress = (0.8f + 0.2f * written / BENCH_BYTES).coerceAtMost(1f)
                    }
                }
                val call = api.openStreamingCall(target, "/api/bandwidth/upload", body)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw bandwidthFailure(resp.code)
                }
                uploadMbps = (BENCH_BYTES * 8.0 / ((System.nanoTime() - t0) / 1e9) / 1e6).toFloat()
            } catch (e: Exception) {
                running = false
                error = describe(e, copy, upload = true)
                return@launch
            } finally {
                api.releaseStreaming()
            }

            result = BenchmarkResult(downloadMbps, uploadMbps, avgPing, jitter, lossPct)
            progress = 1f
            running = false
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

        if (servers.isEmpty()) {
            item { CommandEmptyState(copy.selectServer, copy.noServersBody, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(copy.currentServer, style = MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                            servers.forEach { candidate ->
                                CommandSecondaryButton(
                                    candidate.name,
                                    { selectedId = candidate.id },
                                    enabled = selectedId != candidate.id,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            copy.bandwidthBody,
                            color = CommandColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        CommandPrimaryButton(
                            if (running) copy.waitingForData else copy.run,
                            ::run,
                            enabled = !running && server != null,
                            icon = Icons.Rounded.Speed
                        )
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
                item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
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
                            CommandMetricLine(copy.jitter, "${r.jitterMs} ms", if (r.jitterMs < 40) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION)
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

/** Sentinel used to distinguish "agent too old" from a plain HTTP failure. */
private const val AGENT_UNSUPPORTED = "agent-bandwidth-unsupported"

private fun bandwidthFailure(code: Int): Exception =
    if (code == 404 || code == 405) ApiException(AGENT_UNSUPPORTED) else ApiException("HTTP $code")

private fun describe(e: Exception, copy: CommandCopy, upload: Boolean): String =
    if (e.message == AGENT_UNSUPPORTED) {
        copy.bandwidthUnsupported
    } else {
        val leg = if (upload) copy.upload else copy.download
        val detail = e.message ?: copy.networkError
        // Surface the agent's own reason when it rejected the body: a 400 here
        // used to read as a generic network error and hid the real cause.
        "$leg: $detail"
    }

/** Streams [total] random bytes (a repeated 64 KiB block) and reports progress. */
private class BenchmarkUploadBody(
    private val total: Long,
    private val onProgress: (Long) -> Unit
) : RequestBody() {
    private val block = ByteArray(64 * 1024).also { SecureRandom().nextBytes(it) }
    override fun contentType() = "application/octet-stream".toMediaType()
    override fun contentLength() = total
    override fun writeTo(sink: BufferedSink) {
        var written = 0L
        while (written < total) {
            val n = minOf(block.size.toLong(), total - written)
            sink.write(block, 0, n.toInt())
            written += n
            onProgress(written)
        }
    }
}
