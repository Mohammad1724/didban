package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Cloudflare clean-IP scanner UI.
 *
 * Runs on the phone on purpose: "clean" means reachable from *this* network,
 * and an agent on the other side of the world would answer a different
 * question. The engine and all of its decision logic live in [CfCleanIp].
 */
@Composable
internal fun CommandCfScannerScreen(
    copy: CommandCopy,
    onBack: () -> Unit,
    scan: suspend (List<CfCandidate>, CfScanConfig, (CfScanProgress) -> Unit) -> List<CfProbeResult> = { plan, config, progress ->
        CloudflareIpScanner.scan(plan, config, progress)
    }
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var mode by rememberSaveable { mutableStateOf("HTTP") }
    var port by rememberSaveable { mutableStateOf("443") }
    var count by rememberSaveable { mutableStateOf("256") }
    var tries by rememberSaveable { mutableStateOf("2") }
    var timeoutMs by rememberSaveable { mutableStateOf("3000") }
    var concurrency by rememberSaveable { mutableStateOf("48") }
    var sniOverride by rememberSaveable { mutableStateOf("") }
    var useCustomList by rememberSaveable { mutableStateOf(false) }
    var customList by rememberSaveable { mutableStateOf("") }
    var rangeKeys by rememberSaveable { mutableStateOf(CloudflareRanges.V4.joinToString(",")) }
    var rangesVisible by rememberSaveable { mutableStateOf(false) }

    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(CfScanProgress(0, 0, 0, null)) }
    var results by remember { mutableStateOf<List<CfProbeResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }


    fun start() {
        if (running) return
        val cfg = CfScanConfig.sanitize(
            CfScanConfig(
                mode = when (mode) {
                    "TCP" -> CfProbeMode.TCP
                    "TLS" -> CfProbeMode.TLS
                    else -> CfProbeMode.HTTP
                },
                port = port.toIntOrNull() ?: 443,
                count = count.toIntOrNull() ?: 256,
                tries = tries.toIntOrNull() ?: 2,
                timeoutMs = timeoutMs.toLongOrNull() ?: 3000L,
                concurrency = concurrency.toIntOrNull() ?: 48,
                sniOverride = sniOverride
            )
        )
        val ips = if (useCustomList) {
            CfIpPlan.parseList(customList, cfg.count)
        } else {
            CfIpPlan.randomV4(ScannerCatalog.selectedRanges(rangeKeys), cfg.count, seed = System.currentTimeMillis())
        }
        if (ips.isEmpty()) {
            error = if (!useCustomList) copy.scannerChooseRange else copy.cfNoAddresses
            return
        }
        error = null
        notice = null
        results = emptyList()
        progress = CfScanProgress(ips.size, 0, 0, null)
        running = true
        job = scope.launch {
            try {
                val out = scan(
                    CloudflareIpScanner.plan(ips, cfg), cfg
                ) { p -> progress = p }
                results = CfRanker.best(out, limit = 100)
                if (out.none { it.healthy }) error = copy.cfNoResultsBody
            } catch (ce: kotlinx.coroutines.CancellationException) {
                notice = copy.cfStopped
            } catch (e: Exception) {
                error = e.message ?: copy.operationFailed
            } finally {
                running = false
                job = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CommandSpacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.cfScanner, copy.cfScannerBody, modifier = Modifier.weight(1f))
            }
        }

        // ── source ─────────────────────────────────────────────────────────
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    CommandSectionTitle(copy.cfSource)
                    CommandChipRow(
                        options = listOf(copy.cfRandom to false, copy.cfCustomList to true),
                        selected = useCustomList,
                        onSelect = { useCustomList = it },
                        enabled = !running
                    )
                    Text(copy.scannerCfReadyHint, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (!useCustomList) {
                        Text(copy.scannerRangeSummary.replace("%1", ScannerCatalog.selectedRanges(rangeKeys).size.toString())
                            .replace("%2", CloudflareRanges.V4.size.toString()), color = CommandColors.textPrimary)
                        CommandSecondaryButton(if (rangesVisible) copy.scannerHideList else copy.scannerShowList,
                            { rangesVisible = !rangesVisible })
                        if (rangesVisible) {
                            Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                CommandTextButton(copy.scannerSelectAll, { rangeKeys = CloudflareRanges.V4.joinToString(",") }, enabled = !running)
                                CommandTextButton(copy.scannerClearSelection, { rangeKeys = "" }, enabled = !running)
                            }
                            CloudflareRanges.V4.forEach { cidr ->
                                Row(Modifier.fillMaxWidth().toggleable(
                                    value = cidr in ScannerCatalog.selectedRanges(rangeKeys), enabled = !running, role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        val selected = ScannerCatalog.selectedRanges(rangeKeys).toMutableSet()
                                        if (checked) selected.add(cidr) else selected.remove(cidr)
                                        rangeKeys = selected.joinToString(",")
                                    }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = cidr in ScannerCatalog.selectedRanges(rangeKeys), enabled = !running, onCheckedChange = null)
                                    Text(cidr, fontFamily = Telemetry, color = CommandColors.textPrimary)
                                }
                            }
                            Text(ScannerCatalog.CF_SOURCE, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            Text(copy.scannerSnapshot.replace("%1", ScannerCatalog.VERSION), color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    CommandScannerImport(copy, !running) { if (!running) { customList = it; useCustomList = true } }
                    if (useCustomList) {
                        OutlinedTextField(
                            value = customList,
                            onValueChange = { customList = it.take(ScannerCatalog.MAX_TEXT_BYTES) },
                            label = { Text(copy.cfCustomList) },
                            placeholder = { Text(copy.cfCustomListHint) },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            enabled = !running
                        )
                    }
                }
            }
        }

        // ── settings ───────────────────────────────────────────────────────
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    CommandSectionTitle(copy.cfSettings)
                    CommandChipRow(
                        options = listOf("TCP" to "TCP", "TLS" to "TLS", "HTTP" to "HTTP"),
                        selected = mode,
                        onSelect = { mode = it },
                        enabled = !running
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandNumberField(copy.port, port, { port = it.filter(Char::isDigit).take(5) }, !running, Modifier.weight(1f))
                        CommandNumberField(copy.cfCount, count, { count = it.filter(Char::isDigit).take(5) }, !running, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandNumberField(copy.cfTries, tries, { tries = it.filter(Char::isDigit).take(2) }, !running, Modifier.weight(1f))
                        CommandNumberField(copy.cfTimeout, timeoutMs, { timeoutMs = it.filter(Char::isDigit).take(5) }, !running, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandNumberField(copy.cfConcurrency, concurrency, { concurrency = it.filter(Char::isDigit).take(3) }, !running, Modifier.weight(1f))
                        OutlinedTextField(
                            value = sniOverride,
                            onValueChange = { sniOverride = it },
                            label = { Text(copy.cfSniOverride) },
                            singleLine = true,
                            enabled = !running,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(copy.cfSettingsHint, color = CommandColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            if (running) {
                CommandSecondaryButton(copy.cfStop, ::stop, icon = Icons.Rounded.Stop, enabled = true)
            } else {
                CommandPrimaryButton(copy.cfStart, ::start, icon = Icons.Rounded.PlayArrow)
            }
        }

        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        if (notice != null) item { CommandStateBlock(copy.operationDone, notice ?: "", CommandHealthTone.INFO) }

        // ── live progress ──────────────────────────────────────────────────
        if (running || progress.done > 0) {
            item {
                CommandSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        val pct = if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
                        LinearProgressIndicator(
                            progress = { pct },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text(
                            copy.cfProgress
                                .replace("%1", progress.done.toString())
                                .replace("%2", progress.total.toString())
                                .replace("%3", progress.healthyFound.toString()),
                            color = CommandColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        progress.currentBest?.let { best ->
                            CommandRule()
                            CommandStatusMark(
                                best.ip,
                                CommandHealthTone.HEALTHY,
                                detail = "${best.avgLatencyMs.toInt()} ms · ${best.colo.ifEmpty { "—" }}"
                            )
                        }
                    }
                }
            }
        }

        // ── results ────────────────────────────────────────────────────────
        if (results.isNotEmpty()) {
            item {
                CommandSectionTitle(
                    copy.cfResults, "${results.size}",
                    copy.cfCopyBest,
                    {
                        val text = results.take(20).joinToString("\n") { it.ip }
                        clipboard.setText(AnnotatedString(text))
                        notice = copy.cfCopied.replace("%1", results.take(20).size.toString())
                    },
                    Modifier.fillMaxWidth()
                )
            }
            items(results.take(50), key = { "${it.ip}:${it.port}" }) { r ->
                val tone = when {
                    r.avgLatencyMs < 150 -> CommandHealthTone.HEALTHY
                    r.avgLatencyMs < 400 -> CommandHealthTone.ATTENTION
                    else -> CommandHealthTone.UNKNOWN
                }
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.ip,
                                color = CommandColors.textPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Telemetry),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${r.avgLatencyMs.toInt()} ms",
                                color = CommandColors.accent,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Telemetry)
                            )
                        }
                        CommandMetricLine(copy.latency, "${r.minLatencyMs}–${r.maxLatencyMs} ms", tone)
                        CommandMetricLine(copy.jitter, "${r.jitterMs.toInt()} ms", CommandHealthTone.INFO)
                        CommandMetricLine(
                            copy.cfLoss,
                            "${(r.loss * 100).toInt()}%  (${r.successes}/${r.attempts})",
                            if (r.loss > 0) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY
                        )
                        if (r.colo.isNotEmpty()) {
                            CommandMetricLine(copy.cfColo, r.colo, CommandHealthTone.INFO)
                        }
                        val bits = mutableListOf<String>()
                        if (r.tlsOk) bits.add(copy.cfTlsOk)
                        if (r.tlsVersion.isNotEmpty()) bits.add(r.tlsVersion)
                        if (r.httpStatus != 0) bits.add("HTTP ${r.httpStatus}")
                        if (bits.isNotEmpty()) {
                            Text(
                                bits.joinToString(" · "),
                                color = CommandColors.textTertiary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else if (!running && progress.done > 0 && error == null) {
            item { CommandEmptyState(copy.cfNoResults, copy.cfNoResultsBody) }
        }

        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

/** Numeric field with a fixed label — keeps the settings grid readable. */
@Composable
private fun CommandNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier
    )
}

/** Single-select chip row used for the source and mode pickers. */
@Composable
internal fun <T> CommandChipRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            CommandSurface(
                raised = active,
                border = true,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled) { onSelect(value) }
            ) {
                Text(
                    label,
                    color = if (active) CommandColors.accent else CommandColors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CommandSpacing.sm, horizontal = CommandSpacing.xs)
                )
            }
        }
    }
}
