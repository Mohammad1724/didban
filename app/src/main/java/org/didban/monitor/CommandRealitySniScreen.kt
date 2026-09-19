package org.didban.monitor

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * REALITY donor (dest / SNI) checker.
 *
 * A REALITY config looks correct and still fails silently when the donor is
 * wrong, so this screen reports *why* a candidate is or is not usable rather
 * than just a pass/fail. The rules themselves live in [RealityCriteria] and
 * are unit-tested there.
 */
@Composable
internal fun CommandRealitySniScreen(
    copy: CommandCopy,
    onBack: () -> Unit,
    probe: suspend (String, Int) -> RealityProbeResult = { host, port -> RealitySniScanner.probe(host, port, timeoutMs = 3_000) }
) {
    val scope = rememberCoroutineScope()

    var target by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("443") }
    var customSource by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf(ScannerCatalog.Group.ALL.name) }
    var limitText by rememberSaveable { mutableStateOf("50") }
    var customDomains by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    val group = ScannerCatalog.Group.values().firstOrNull { it.name == category } ?: ScannerCatalog.Group.ALL
    val limit = (limitText.toIntOrNull() ?: 50).coerceIn(1, ScannerCatalog.MAX_SNI_TARGETS)
    val imported = ScannerCatalog.parseSniList(customDomains, port.toIntOrNull() ?: 443, limit)
    val readyNames = ScannerCatalog.domains(group)

    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var batchTotal by remember { mutableStateOf(0) }
    var batchDone by remember { mutableStateOf(0) }
    var single by remember { mutableStateOf<Pair<RealityProbeResult, RealityAssessment>?>(null) }
    var batch by remember { mutableStateOf<List<Pair<RealityProbeResult, RealityAssessment>>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    fun startSingle() {
        if (running) return
        val parsed = RealitySniScanner.parseTarget(target, port.toIntOrNull() ?: 443)
        if (parsed == null || !ScannerCatalog.validDomain(parsed.first)) {
            error = copy.realityBadTarget
            return
        }
        error = null
        single = null
        batch = emptyList()
        batchTotal = 0
        batchDone = 0
        running = true
        job = scope.launch {
            try {
                val r = probe(parsed.first, parsed.second)
                single = r to RealityCriteria.evaluate(r)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = e.message ?: copy.operationFailed
            } finally {
                running = false
                job = null
            }
        }
    }

    fun startBatch() {
        if (running) return
        error = null
        single = null
        batch = emptyList()
        val scanPort = port.toIntOrNull()?.takeIf { it in 1..65535 }
        if (scanPort == null) { error = copy.fleetPortInvalid; return }
        // Read saved input state AT THE CLICK, not an immutable composition snapshot.
        val scanLimit = (limitText.toIntOrNull() ?: 50).coerceIn(1, ScannerCatalog.MAX_SNI_TARGETS)
        val selectedGroup = ScannerCatalog.Group.values().firstOrNull { it.name == category } ?: ScannerCatalog.Group.ALL
        val names = if (customSource) ScannerCatalog.parseSniList(customDomains, scanPort, scanLimit).targets
            else ScannerCatalog.sniPlan(selectedGroup, scanLimit, scanPort)
        if (names.isEmpty()) { error = copy.realityBadTarget; return }
        batchTotal = names.size
        batchDone = 0
        running = true
        job = scope.launch {
            try {
                scanSniCandidates(names, probe) { completed ->
                    batch = completed
                    batchDone = completed.size
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = e.message ?: copy.operationFailed
            } finally {
                running = false
                job = null
            }
        }
    }

    if (preview) CommandScannerPreview(copy, readyNames) { preview = false }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CommandSpacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.realitySni, copy.realityBody, modifier = Modifier.weight(1f))
            }
        }

        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    CommandSectionTitle(copy.scannerReadyLists)
                    CommandChipRow(listOf(copy.scannerBuiltIn to false, copy.scannerManual to true), customSource,
                        { customSource = it }, !running)
                    Text(copy.scannerSniReadyHint, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    if (!customSource) {
                        CommandSniCategory(copy, group, !running) { category = it.name }
                        Text(copy.scannerPlanSummary.replace("%1", minOf(limit, readyNames.size).toString())
                            .replace("%2", readyNames.size.toString()), color = CommandColors.textPrimary)
                        CommandSecondaryButton(copy.scannerShowList, { preview = true })
                        CommandTextButton(copy.scannerEditList, {
                            customDomains = ScannerCatalog.domains(ScannerCatalog.Group.valueOf(category)).joinToString("\n")
                            customSource = true
                        }, enabled = !running)
                        Text(copy.scannerSnapshot.replace("%1", ScannerCatalog.VERSION), color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(customDomains, { customDomains = it.take(ScannerCatalog.MAX_TEXT_BYTES) },
                            Modifier.fillMaxWidth().height(140.dp), enabled = !running,
                            label = { Text(copy.scannerManual) }, placeholder = { Text(copy.scannerSniListHint) })
                        Text(copy.scannerInputSummary.replace("%1", imported.targets.size.toString())
                            .replace("%2", imported.rejected.toString()).replace("%3", limit.toString()),
                            color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    CommandScannerImport(copy, !running) { if (!running) { customDomains = it; customSource = true } }
                    CommandDisclosure(copy) {
                    OutlinedTextField(limitText, { limitText = it.filter(Char::isDigit).take(3) },
                        Modifier.fillMaxWidth(), enabled = !running, singleLine = true,
                        label = { Text("${copy.scannerLimit} (1–${ScannerCatalog.MAX_SNI_TARGETS})") })
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            label = { Text(copy.port) },
                            singleLine = true,
                            enabled = !running,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CommandPrimaryButton(copy.realityCheckAll, ::startBatch, enabled = !running, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                    if (running) CommandSecondaryButton(copy.stop, { job?.cancel() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        OutlinedTextField(
                            value = target,
                            onValueChange = { target = it },
                            label = { Text(copy.realityDomain) },
                            placeholder = { Text("www.microsoft.com") },
                            singleLine = true,
                            enabled = !running,
                            modifier = Modifier.weight(1f)
                        )

                    }
                    CommandPrimaryButton(copy.realityCheck, ::startSingle, icon = Icons.Rounded.PlayArrow, enabled = !running)

                }
            }
        }

        // Discouraged donor reference remains available without burying scan results.
        item {
            CommandSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    CommandDisclosure(copy, title = copy.realityDiscouraged) {
                    RealitySniScanner.DISCOURAGED.forEach { (name, why) ->
                        Row(Modifier.fillMaxWidth().clickable { target = name }) {
                            Text(
                                name,
                                color = CommandColors.danger,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(why, color = CommandColors.textTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    }
                }
            }
        }

        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }

        if (running && batchTotal > 0) {
            item {
                CommandSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        LinearProgressIndicator(
                            progress = { if (batchTotal == 0) 0f else batchDone.toFloat() / batchTotal },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            copy.realityProgress.replace("%1", batchDone.toString())
                                .replace("%2", batchTotal.toString())
                                .replace("%3", batch.count { it.second.verdict != RealityVerdict.REJECT }.toString()),
                            color = CommandColors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        single?.let { (r, a) ->
            item { RealityVerdictCard(copy, r, a) }
            item { RealityDetailCard(copy, r, a) }
        }

        if (batch.isNotEmpty()) {
            item { CommandSectionTitle(copy.realityResults, "${batch.size}") }
            items(batch.sortedBy { -it.second.score }, key = { "${it.first.sni}:${it.first.port}" }) { (r, a) ->
                CommandSurface(
                    Modifier.fillMaxWidth().clickable {
                        expanded = if (expanded == "${r.sni}:${r.port}") null else "${r.sni}:${r.port}"
                    }
                ) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CommandStatusMark(
                                r.sni,
                                verdictTone(a.verdict),
                                Modifier.weight(1f),
                                "${copy.realityScore}: ${a.score}"
                            )
                        }
                        if (a.blockers.isNotEmpty()) {
                            Text(
                                a.blockers.first(),
                                color = CommandColors.danger,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (a.warnings.isNotEmpty()) {
                            Text(
                                a.warnings.first(),
                                color = CommandColors.warning,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "${r.tlsVersion.ifEmpty { "—" }} · ${r.alpn.ifEmpty { "no ALPN" }} · ${r.totalMs} ms",
                            color = CommandColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry)
                        )
                        if (expanded == "${r.sni}:${r.port}") {
                            CommandRule()
                            RealityDetailBody(copy, r, a)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

private fun verdictTone(v: RealityVerdict): CommandHealthTone = when (v) {
    RealityVerdict.GOOD -> CommandHealthTone.HEALTHY
    RealityVerdict.USABLE -> CommandHealthTone.INFO
    RealityVerdict.RISKY -> CommandHealthTone.ATTENTION
    RealityVerdict.REJECT -> CommandHealthTone.OFFLINE
}

@Composable
private fun RealityVerdictCard(
    copy: CommandCopy,
    r: RealityProbeResult,
    a: RealityAssessment
) {
    CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.sni,
                    color = CommandColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CommandTelemetryPill(verdictLabel(copy, a.verdict), verdictTone(a.verdict))
            }
            CommandTelemetryBar(copy.realityScore, a.score.toFloat(), scoreTone(a.score))
            if (r.resolvedIp.isNotEmpty()) {
                CommandMetricLine(copy.realityResolved, r.resolvedIp, CommandHealthTone.INFO)
            }
            if (a.blockers.isNotEmpty()) {
                CommandRule()
                Text(copy.realityBlockers, color = CommandColors.danger,
                    style = MaterialTheme.typography.labelLarge)
                a.blockers.forEach {
                    Text("• $it", color = CommandColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (a.warnings.isNotEmpty()) {
                CommandRule()
                Text(copy.realityWarnings, color = CommandColors.warning,
                    style = MaterialTheme.typography.labelLarge)
                a.warnings.forEach {
                    Text("• $it", color = CommandColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (a.notes.isNotEmpty()) {
                CommandRule()
                Text(copy.realityNotes, color = CommandColors.textTertiary,
                    style = MaterialTheme.typography.labelLarge)
                a.notes.forEach {
                    Text("• $it", color = CommandColors.textTertiary,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun scoreTone(score: Int): CommandHealthTone = when {
    score >= 90 -> CommandHealthTone.HEALTHY
    score >= 60 -> CommandHealthTone.INFO
    score > 0 -> CommandHealthTone.ATTENTION
    else -> CommandHealthTone.OFFLINE
}

private fun verdictLabel(copy: CommandCopy, v: RealityVerdict): String = when (v) {
    RealityVerdict.GOOD -> copy.realityGood
    RealityVerdict.USABLE -> copy.realityUsable
    RealityVerdict.RISKY -> copy.realityRisky
    RealityVerdict.REJECT -> copy.realityReject
}

@Composable
private fun RealityDetailCard(
    copy: CommandCopy,
    r: RealityProbeResult,
    a: RealityAssessment
) {
    CommandSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CommandSpacing.md)) {
            RealityDetailBody(copy, r, a)
        }
    }
}

@Composable
private fun RealityDetailBody(
    copy: CommandCopy,
    r: RealityProbeResult,
    a: RealityAssessment
) {
    Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
        CommandSectionTitle(copy.realityTiming)
        CommandMetricLine(copy.realityDns, if (r.dnsMs < 0) "—" else "${r.dnsMs} ms", CommandHealthTone.INFO)
        CommandMetricLine(copy.realityTcp, if (r.tcpMs < 0) "—" else "${r.tcpMs} ms", CommandHealthTone.INFO)
        CommandMetricLine(
            copy.realityTls,
            if (r.tlsMs < 0) "—" else "${r.tlsMs} ms",
            if (r.tlsMs in 0..300) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION
        )
        CommandMetricLine(copy.realityTotal, "${r.totalMs} ms", CommandHealthTone.INFO)

        CommandRule()
        CommandSectionTitle(copy.realityTlsDetails)
        CommandMetricLine(
            copy.realityTlsVersion,
            r.tlsVersion.ifEmpty { "—" },
            if (r.isTls13) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE
        )
        CommandMetricLine(
            copy.realityAlpn,
            r.alpn.ifEmpty { copy.realityNotNegotiated },
            if (r.hasH2) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION
        )
        CommandMetricLine(copy.realityCipher, r.cipher.ifEmpty { "—" }, CommandHealthTone.INFO)

        CommandRule()
        CommandSectionTitle(copy.realityCert)
        CommandMetricLine(
            copy.realityCertValid,
            if (r.certValid) copy.realityYes else copy.realityNo,
            if (r.certValid) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE
        )
        CommandMetricLine(
            copy.realitySniInSan,
            if (r.sniMatchesSan) copy.realityYes else copy.realityNo,
            if (r.sniMatchesSan) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE
        )
        if (r.certSans.isNotEmpty()) {
            Text(
                r.certSans.take(8).joinToString(", "),
                color = CommandColors.textTertiary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry)
            )
        }
        CommandMetricLine(copy.realityCertIssuer, r.certIssuer.ifEmpty { "—" }, CommandHealthTone.INFO)
        CommandMetricLine(
            copy.realityCertDays,
            r.certDaysRemaining.toString(),
            when {
                r.certExpired -> CommandHealthTone.OFFLINE
                r.certExpiringSoon -> CommandHealthTone.ATTENTION
                else -> CommandHealthTone.HEALTHY
            }
        )
        CommandMetricLine(copy.realityCertKey, r.certPublicKeyAlg.ifEmpty { "—" }, CommandHealthTone.INFO)
        CommandMetricLine(copy.realityCertChain, "${r.certChainBytes} B", CommandHealthTone.INFO)

        CommandRule()
        CommandSectionTitle(copy.realityHttp)
        CommandMetricLine(
            copy.realityHttpStatus,
            if (r.httpStatus == 0) copy.realityNotNegotiated else r.httpStatus.toString(),
            if (r.isRedirect) CommandHealthTone.OFFLINE else CommandHealthTone.INFO
        )
        if (r.redirectLocation.isNotEmpty()) {
            CommandMetricLine(copy.realityRedirectTo, r.redirectLocation, CommandHealthTone.OFFLINE)
        }
        CommandMetricLine(
            copy.realityCdn,
            when {
                r.behindCloudflare -> copy.realityCloudflare
                r.behindCdn -> copy.realityYes
                else -> copy.realityNo
            },
            if (r.behindCdn || r.behindCloudflare) CommandHealthTone.OFFLINE else CommandHealthTone.HEALTHY
        )
        if (r.serverHeader.isNotEmpty()) {
            CommandMetricLine(copy.realityServerHeader, r.serverHeader, CommandHealthTone.INFO)
        }
        if (r.error.isNotEmpty()) {
            CommandRule()
            CommandStateBlock(copy.operationFailed, r.error, CommandHealthTone.OFFLINE)
        }
    }
}
