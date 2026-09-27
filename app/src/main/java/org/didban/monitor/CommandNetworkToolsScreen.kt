package org.didban.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class NetworkDiagnosticMode {
    DPI,
    PORTS,
    CERTIFICATE
}

private fun NetworkDiagnosticMode.label(copy: CommandCopy): String = when (this) {
    NetworkDiagnosticMode.DPI -> copy.netModeDpi
    NetworkDiagnosticMode.PORTS -> copy.netModePorts
    NetworkDiagnosticMode.CERTIFICATE -> copy.netModeCertificate
}

/** Injectable network operations keep the state contract testable without live internet. */
interface NetworkToolsRunner {
    suspend fun diagnoseDeep(host: String, port: Int, sni: String?, tries: Int = 3): DpiDeepResult =
        CensorshipTester.diagnoseDeep(host, port, sni, tries)
    suspend fun scanPorts(host: String, ports: List<Int>, onResult: (PortScanResult) -> Unit)
    suspend fun inspectCertificate(host: String, port: Int): SslCertInfo
}

object DefaultNetworkToolsRunner : NetworkToolsRunner {
    override suspend fun scanPorts(host: String, ports: List<Int>, onResult: (PortScanResult) -> Unit) {
        PortScanner.scanPorts(host, ports, concurrency = 10, onResult = onResult)
    }

    override suspend fun inspectCertificate(host: String, port: Int): SslCertInfo =
        SslInspector.inspect(host, port)
}

private fun localizedVerdict(verdict: DpiVerdict, copy: CommandCopy): String = when (verdict) {
    DpiVerdict.TCP_BLOCKED -> copy.netVerdictTcpBlocked
    DpiVerdict.TCP_DOWN -> copy.netVerdictTcpDown
    DpiVerdict.UNSTABLE -> copy.netVerdictUnstable
    DpiVerdict.TLS_BLOCKED -> copy.netVerdictTlsBlocked
    DpiVerdict.SNI_BLOCKED -> copy.netVerdictSniBlocked
    DpiVerdict.HEALTHY -> copy.netVerdictHealthy
    DpiVerdict.MIDDLEBOX -> copy.netVerdictMiddlebox
}

private fun verdictTone(verdict: DpiVerdict): CommandHealthTone = when (verdict) {
    DpiVerdict.TCP_BLOCKED, DpiVerdict.TCP_DOWN, DpiVerdict.TLS_BLOCKED, DpiVerdict.SNI_BLOCKED, DpiVerdict.MIDDLEBOX -> CommandHealthTone.OFFLINE
    DpiVerdict.UNSTABLE -> CommandHealthTone.ATTENTION
    DpiVerdict.HEALTHY -> CommandHealthTone.HEALTHY
}

private fun verdictTip(verdict: DpiVerdict, copy: CommandCopy): String = when (verdict) {
    DpiVerdict.SNI_BLOCKED -> copy.netTipSniBlocked
    DpiVerdict.HEALTHY -> copy.netTipHealthy
    DpiVerdict.UNSTABLE -> copy.netTipRetest
    DpiVerdict.MIDDLEBOX -> copy.netTipMiddlebox
    else -> ""
}

/** Current data network: VPN, Wi-Fi, cellular (with operator) or unknown. */
internal data class NetContext(val wifi: Boolean, val vpn: Boolean, val operator: String)
internal fun currentNetworkContext(context: Context): NetContext {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val operator = if (wifi || vpn) "" else runCatching {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.networkOperatorName.orEmpty()
        }.getOrDefault("")
        NetContext(wifi, vpn, operator)
    } catch (_: Exception) {
        NetContext(false, false, "")
    }
}

private fun localizedCensorshipResult(
    result: CensorshipDiagnosticResult,
    copy: CommandCopy
): Pair<String, String> {
    val value = result.details.ifBlank { copy.netDiagFailed }
    return when (result.diagnosis) {
        CensorshipDiagnosis.TCP_TIMEOUT -> copy.netDpiTcpTimeout to copy.netDpiTcpTimeoutBody
        CensorshipDiagnosis.TCP_RESET -> copy.netDpiTcpReset to copy.netDpiTcpResetBody
        CensorshipDiagnosis.PORT_CLOSED -> copy.netDpiPortClosed.replace("%1", result.port.toString()) to
            copy.netDpiPortClosedBody.replace("%1", result.port.toString())
        CensorshipDiagnosis.TCP_ERROR -> copy.netDpiTcpError.replace("%1", value) to
            copy.netDpiTcpErrorBody.replace("%1", value)
        CensorshipDiagnosis.TLS_HEALTHY -> copy.netDpiTlsHealthy to
            copy.netDpiTlsHealthyBody.replace("%1", value)
        CensorshipDiagnosis.PLAIN_TCP -> copy.netDpiPlainTcp.replace("%1", result.port.toString()) to
            copy.netDpiPlainTcpBody.replace("%1", result.port.toString())
        CensorshipDiagnosis.TLS_FILTERED -> copy.netDpiTlsFiltered to
            copy.netDpiTlsFilteredBody.replace("%1", value)
        CensorshipDiagnosis.SSH_OPEN -> copy.netDpiSshOpen to copy.netDpiSshOpenBody
        CensorshipDiagnosis.PORT_OPEN -> copy.netDpiPortOpen.replace("%1", result.port.toString()) to
            copy.netDpiPortOpenBody.replace("%1", result.port.toString())
    }
}

@Composable
fun CommandNetworkToolsScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onBack: () -> Unit,
    runner: NetworkToolsRunner = DefaultNetworkToolsRunner
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(NetworkDiagnosticMode.DPI) }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var port by remember { mutableStateOf("443") }
    var sni by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var summaryTone by remember { mutableStateOf(CommandHealthTone.INFO) }
    var detail by remember { mutableStateOf("") }
    var portResults by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var emptyResult by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun clearResult() {
        error = null
        summary = null
        summaryTone = CommandHealthTone.INFO
        detail = ""
        portResults = emptyList()
        emptyResult = false
    }

    fun selectMode(candidate: NetworkDiagnosticMode) {
        if (loading || mode == candidate) return
        mode = candidate
        clearResult()
    }

    fun run() {
        if (loading) return
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank()) {
            clearResult()
            error = copy.netNoHost
            return
        }
        val targetPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 443
        loading = true
        clearResult()
        val requestedMode = mode
        job = scope.launch {
            try {
                when (requestedMode) {
                    NetworkDiagnosticMode.DPI -> {
                        val result = runner.diagnoseDeep(clean, targetPort, sni.trim().takeIf { it.isNotBlank() })
                        val verdict = result.verdict
                        summary = localizedVerdict(verdict, copy)
                        summaryTone = verdictTone(verdict)
                        detail = buildString {
                            appendLine(
                                copy.netAttemptsLine
                                    .replace("%1", result.tcpOk.toString()).replace("%2", result.tcpTries.toString())
                                    .replace("%3", result.tlsPlainOk.toString()).replace("%4", result.tlsPlainTries.toString())
                                    .replace("%5", result.tlsSniOk.toString()).replace("%6", result.tlsSniTries.toString())
                            )
                            if (result.avgLatencyMs >= 0) appendLine(copy.netAvgLatency.replace("%1", result.avgLatencyMs.toString()))
                            if (result.controlAvgMs >= 0) appendLine(copy.netCtlLine.replace("%1", result.controlAvgMs.toString()))
                            if (result.sniCertCn.isNotBlank() || result.sniCertIssuer.isNotBlank()) appendLine(
                                copy.netSniCertLine
                                    .replace("%1", result.sniCertCn.ifBlank { "-" })
                                    .replace("%2", result.sniCertIssuer.ifBlank { "-" })
                                    .replace("%3", when (result.sniCertSan) {
                                        "match" -> copy.netCertSanMatch
                                        "mismatch" -> copy.netCertSanMismatch
                                        else -> "-"
                                    })
                            )
                            if (result.tcpReset) appendLine(copy.netResetFlag)
                            else if (result.sniReset) appendLine(copy.netResetFlag)
                            val tip = verdictTip(verdict, copy)
                            if (tip.isNotBlank()) append(tip)
                        }
                    }
                    NetworkDiagnosticMode.PORTS -> {
                        val found = mutableListOf<PortScanResult>()
                        runner.scanPorts(clean, COMMON_PORTS.map { it.first }) { result ->
                            if (result.isOpen) synchronized(found) { found.add(result) }
                        }
                        val sorted = synchronized(found) { found.sortedBy { it.port } }
                        portResults = sorted
                        emptyResult = sorted.isEmpty()
                        if (sorted.isNotEmpty()) {
                            summary = copy.netOpenPortsFound.replace("%1", sorted.size.toString())
                            summaryTone = CommandHealthTone.HEALTHY
                            detail = copy.netOnlyAnsweredPorts
                        }
                    }
                    NetworkDiagnosticMode.CERTIFICATE -> {
                        val cert = runner.inspectCertificate(clean, targetPort)
                        summary = copy.netTlsCertificateSummary.replace("%1", cert.daysRemaining.toString())
                        summaryTone = if (cert.daysRemaining < 0) CommandHealthTone.OFFLINE else if (cert.daysRemaining < 30) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY
                        detail = listOf(
                            "${copy.netSubject}: ${cert.subject}",
                            "${copy.netIssuer}: ${cert.issuer}",
                            "${copy.netValid}: ${cert.validFrom} → ${cert.validTo}",
                            "${copy.netSerial}: ${cert.serialNumber}",
                            "${copy.netSignature}: ${cert.sigAlg}",
                            "${copy.netFingerprint}: ${cert.fingerprintSha256}",
                            "${copy.netSan}: ${cert.sans.joinToString()}"
                        ).joinToString("\n")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.netDiagFailed
            } finally {
                loading = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.networkTools, initialServer?.name ?: copy.netLocalProbe, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                NetworkDiagnosticMode.values().forEach { candidate ->
                    CommandSecondaryButton(candidate.label(copy), { selectMode(candidate) }, enabled = !loading && mode != candidate)
                }
            }
        }
        if (mode == NetworkDiagnosticMode.DPI) item {
            // Filtering decisions are per-operator: state the current network so
            // a Wi-Fi result is never mistaken for an operator verdict.
            val context = LocalContext.current
            val net = remember { currentNetworkContext(context) }
            when {
                net.vpn -> CommandStatusMark(copy.netNetVpnTitle, CommandHealthTone.OFFLINE, detail = copy.netNetVpnBody)
                net.wifi -> CommandStatusMark(copy.netNetWifiTitle, CommandHealthTone.ATTENTION, detail = copy.netNetWifiBody)
                else -> CommandStatusMark(
                    if (net.operator.isBlank()) copy.netNetCellUnknown else copy.netNetCellTitle.replace("%1", net.operator),
                    CommandHealthTone.INFO,
                    detail = copy.netNetCellBody
                )
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.netProbeInput, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    CommandResponsiveRow {
                        OutlinedTextField(host, { host = it }, item(weight = 1f), enabled = !loading, singleLine = true, label = { Text(copy.netHostDomain) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, item(width = CommandMetrics.formAuxFieldWidth), enabled = !loading, singleLine = true, label = { Text(copy.port) })
                    }
                    if (mode == NetworkDiagnosticMode.DPI) {
                        OutlinedTextField(sni, { sni = it }, Modifier.fillMaxWidth(), enabled = !loading, singleLine = true, label = { Text(copy.netSniField) }, placeholder = { Text(copy.netSniHint) })
                    }
                    Text(copy.netProbeBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    CommandResponsiveRow {
                        CommandPrimaryButton(
                            if (loading) copy.waitingForData else copy.netRunMode.replace("%1", mode.label(copy)),
                            ::run,
                            modifier = item(),
                            enabled = !loading,
                            icon = Icons.Rounded.PlayArrow
                        )
                        if (loading) {
                            CommandSecondaryButton(copy.stop, ::cancel, modifier = item())
                        }
                    }
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE, copy.retry, ::run) }
        if (loading && summary == null && error == null) item {
            CommandLoadingState(copy.waitingForData, copy.netProbeBody)
        }
        if (emptyResult) item {
            CommandEmptyState(
                title = copy.netOpenPorts,
                body = copy.netNoOpenPorts,
                actionLabel = copy.retry,
                onAction = ::run,
                actionIcon = Icons.Rounded.Refresh
            )
        }
        if (summary != null) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        val currentSummary = summary.orEmpty()
                        CommandStatusMark(currentSummary, summaryTone)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(detail, Modifier.weight(1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 30, overflow = TextOverflow.Ellipsis)
                            CommandTextButton(copy.netCopyResult, { clipboard.setText(AnnotatedString(detail)) }, icon = Icons.Rounded.ContentCopy)
                        }
                    }
                }
            }
        }
        if (portResults.isNotEmpty()) {
            item { CommandSectionTitle(copy.netOpenPorts, copy.netRealResponses.replace("%1", portResults.size.toString())) }
            items(portResults, key = { it.port }) { result ->
                CommandSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        CommandStatusMark(copy.netOpen, CommandHealthTone.HEALTHY, Modifier.weight(1f), "${result.port} · ${result.service}")
                        Text(copy.netLatencyValue.replace("%1", result.latencyMs.toString()), color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
