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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class NetworkDiagnosticMode(val label: String) {
    DPI("DPI / TLS"),
    PORTS("Port scan"),
    CERTIFICATE("TLS certificate"),
    GEO_DNS("GeoIP / DNS")
}

@Composable
fun CommandNetworkToolsScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(NetworkDiagnosticMode.DPI) }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var port by remember { mutableStateOf("443") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf("") }
    var portResults by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }

    fun run() {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank()) {
            error = copy.netNoHost
            return
        }
        val targetPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 443
        loading = true
        error = null
        summary = null
        detail = ""
        portResults = emptyList()
        scope.launch {
            try {
                when (mode) {
                    NetworkDiagnosticMode.DPI -> {
                        val result = CensorshipTester.diagnose(clean, targetPort)
                        summary = result.diagnosis
                        detail = "TCP reachable: ${result.tcpReachable}\nTLS reachable: ${result.tlsReachable}\nFiltered: ${result.isFiltered}\nLatency: ${result.latencyMs} ms\n\n${result.details}"
                    }
                    NetworkDiagnosticMode.PORTS -> {
                        val found = mutableListOf<PortScanResult>()
                        PortScanner.scanPorts(clean, COMMON_PORTS.map { it.first }, concurrency = 10) { result ->
                            if (result.isOpen) found.add(result)
                        }
                        portResults = found.sortedBy { it.port }
                        summary = "${portResults.size} open ports found"
                        detail = if (portResults.isEmpty()) copy.netNoOpenPorts else copy.netOnlyAnsweredPorts
                    }
                    NetworkDiagnosticMode.CERTIFICATE -> {
                        val cert = SslInspector.inspect(clean, targetPort)
                        summary = "TLS certificate · ${cert.daysRemaining} days remaining"
                        detail = "Subject: ${cert.subject}\nIssuer: ${cert.issuer}\nValid: ${cert.validFrom} → ${cert.validTo}\nSerial: ${cert.serialNumber}\nSignature: ${cert.sigAlg}\nFingerprint SHA-256: ${cert.fingerprintSha256}\nSAN: ${cert.sans.joinToString()}"
                    }
                    NetworkDiagnosticMode.GEO_DNS -> {
                        val info = IpInfoService.lookup(clean)
                        summary = "${info.flag} ${info.country} · ${info.ip}"
                        detail = "${copy.netDomainLabel}: ${info.domainName.ifBlank { "—" }}\n${copy.netReverseDnsLabel}: ${info.reverseDns.ifBlank { "—" }}\n${copy.netIspLabel}: ${info.isp.ifBlank { "—" }}\n${copy.netAsnLabel}: ${info.asn.ifBlank { "—" }}\n${copy.netRegionLabel}: ${info.region} / ${info.city}\n${copy.netDnsRecordsLabel}: ${info.dnsRecords.size}"
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: copy.netDiagFailed
            } finally {
                loading = false
            }
        }
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
                    CommandSecondaryButton(candidate.label, { mode = candidate }, enabled = mode != candidate)
                }
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.netProbeInput, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(host, { host = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.netHostDomain) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), singleLine = true, label = { Text("Port") })
                    }
                    Text(copy.netProbeBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    CommandPrimaryButton(if (loading) copy.waitingForData else "Run ${mode.label}", ::run, enabled = !loading, icon = Icons.Rounded.PlayArrow)
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        if (summary != null) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandStatusMark(summary ?: "", if (summary!!.contains("failed", true) || summary!!.contains("filtered", true)) CommandHealthTone.OFFLINE else CommandHealthTone.INFO)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(detail, Modifier.weight(1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 30, overflow = TextOverflow.Ellipsis)
                            CommandTextButton("Copy", { clipboard.setText(AnnotatedString(detail)) }, icon = Icons.Rounded.ContentCopy)
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
                        CommandStatusMark("OPEN", CommandHealthTone.HEALTHY, Modifier.weight(1f), "${result.port} · ${result.service}")
                        Text("${result.latencyMs} ms", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
