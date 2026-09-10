@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// 1. NETWORK HUB SCREEN (Check-Host · DPI Censorship · Port Scan · SSL · IP · Ping)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NetworkHubScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 6 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    val tabTitles = listOf(
        "Check-Host",
        "DPI & Filter",
        "Port Scanner",
        "SSL Inspector",
        "GeoIP & DNS",
        "TCP Ping"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        // Top Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Rounded.Public, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.networkHub, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                Text("Diagnostic Suite & Probes", fontSize = 11.sp, color = Ds.textTertiary)
            }
        }

        FilterChipRow(
            items = tabTitles,
            selectedIndex = subTab,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> CheckHostHubTab(t)
                1 -> CensorshipTab(t)
                2 -> PortScannerTab(t)
                3 -> SslInspectorTab(t)
                4 -> IpInfoTab(t)
                5 -> TcpPingTab(t)
            }
        }
    }
}

// ── Tab 0: Global Reachability Check (Check-Host) ───────────────────────────

@Composable
private fun CheckHostHubTab(t: Str) {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("ping") }
    var port by remember { mutableStateOf("443") }
    var isChecking by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }
    var statusText by remember { mutableStateOf("") }

    val totalCount = nodes.size

    fun startProbe() {
        if (isChecking) return
        val hostToTest = if (selectedType == "tcp") "$target:$port" else target
        if (hostToTest.trim().isEmpty()) return

        isChecking = true
        statusText = t.probing
        nodes = emptyList()

        scope.launch {
            try {
                val (reqId, initialNodes) = CheckHostService.startCheck(hostToTest.trim(), selectedType, 20)
                nodes = initialNodes

                for (i in 0 until 12) {
                    delay(1500)
                    val done = CheckHostService.pollResults(reqId, selectedType, nodes)
                    nodes = nodes.toList()
                    if (done) break
                }
                nodes.forEach { if (it.state == 0) { it.state = 2; it.resultText = "timeout" } }
                nodes = nodes.toList()
                val currentOk = nodes.count { it.state == 1 }
                statusText = "$currentOk/$totalCount ${t.probeSuccess}"
            } catch (e: Exception) {
                statusText = "${t.error}: ${e.message}"
            } finally {
                isChecking = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            BannerCard(
                text = "Worldwide reachability testing across 20+ nodes (Europe, US, Asia, Iran).",
                tone = BannerTone.Info,
                icon = Icons.Rounded.Public
            )
        }

        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = target, onValueChange = { target = it }, label = t.probeTarget, placeholder = "IP or Domain (e.g. 1.1.1.1 or example.com)")
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Ping", "HTTP", "TCP"),
                    selectedIndex = when (selectedType) { "http" -> 1; "tcp" -> 2; else -> 0 },
                    onSelect = { selectedType = when (it) { 1 -> "http"; 2 -> "tcp"; else -> "ping" } }
                )
                if (selectedType == "tcp") {
                    Spacer(Modifier.height(8.dp))
                    InputField(value = port, onValueChange = { port = it }, label = t.portNumber, placeholder = "443")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isChecking) t.probing else t.runProbe,
                    icon = Icons.Rounded.Bolt,
                    loading = isChecking,
                    enabled = !isChecking && target.isNotBlank(),
                    onClick = { startProbe() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (statusText.isNotEmpty()) {
            item {
                Text(statusText, fontSize = 12.sp, color = Ds.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }

        items(nodes) { node ->
            val isOk = node.state == 1
            val isFail = node.state == 2
            ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(node.flag, fontSize = 17.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.location, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text(node.nodeKey, fontSize = 10.5.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                    }
                    Text(
                        node.resultText,
                        fontSize = 11.5.sp,
                        fontFamily = Telemetry,
                        fontWeight = FontWeight.Bold,
                        color = if (isOk) Ds.ok else if (isFail) Ds.danger else Ds.textTertiary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 1: DPI & Censorship Inspector ───────────────────────────────────────

@Composable
private fun CensorshipTab(t: Str) {
    val scope = rememberCoroutineScope()
    var targetHost by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isDiagnosing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CensorshipDiagnosticResult?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            BannerCard(
                text = t.guideCensorship,
                tone = BannerTone.Info,
                icon = Icons.Rounded.Security
            )
        }

        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = targetHost, onValueChange = { targetHost = it }, label = "Target Host / IP", placeholder = "e.g. 1.1.1.1 or vps.example.com")
                Spacer(Modifier.height(8.dp))
                InputField(value = port, onValueChange = { port = it }, label = "Port", placeholder = "443")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isDiagnosing) "Analyzing DPI Handshake…" else "Run DPI Diagnosis",
                    icon = Icons.Rounded.Security,
                    loading = isDiagnosing,
                    enabled = !isDiagnosing && targetHost.isNotBlank(),
                    onClick = {
                        isDiagnosing = true
                        result = null
                        scope.launch {
                            result = CensorshipTester.diagnose(targetHost.trim(), port.toIntOrNull() ?: 443)
                            isDiagnosing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        result?.let { r ->
            item {
                ModernCard(
                    padding = 16.dp,
                    cornerRadius = 18.dp,
                    containerColor = if (r.isFiltered) Ds.dangerDim else Ds.okDim,
                    borderColor = if (r.isFiltered) Ds.danger.copy(alpha = 0.4f) else Ds.ok.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (r.isFiltered) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (r.isFiltered) Ds.danger else Ds.ok,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (r.isFiltered) "Interference / Filtering Detected" else "Connection Clean (No DPI Reset)",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (r.isFiltered) Ds.danger else Ds.ok
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        r.diagnosis,
                        fontSize = 12.5.sp,
                        color = Ds.textPrimary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Hairline()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Stage: ${r.stage} (${r.latencyMs}ms)\n${r.details}",
                        fontSize = 11.sp,
                        fontFamily = Telemetry,
                        color = Ds.textSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 2: Port Scanner ─────────────────────────────────────────────────────

@Composable
private fun PortScannerTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var scannedCount by remember { mutableStateOf(0) }
    val portsToScan = remember { COMMON_PORTS.map { it.first } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "Target Domain or IP", placeholder = "e.g. example.com or 1.2.3.4")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isScanning) "Scanning ($scannedCount / ${portsToScan.size})…" else "Scan Common Ports",
                    icon = Icons.Rounded.Search,
                    loading = isScanning,
                    enabled = !isScanning && host.isNotBlank(),
                    onClick = {
                        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
                        isScanning = true
                        results = emptyList()
                        scannedCount = 0
                        scope.launch {
                            try {
                                PortScanner.scanPorts(clean, portsToScan, concurrency = 10) { res ->
                                    scannedCount++
                                    if (res.isOpen) {
                                        results = (results + res).sortedBy { it.port }
                                    }
                                }
                            } finally {
                                isScanning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (results.isEmpty() && !isScanning) {
            item {
                EmptyState(title = t.enterHostToScan, icon = Icons.Rounded.Search)
            }
        } else {
            items(results, key = { it.port }) { r ->
                ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(text = "PORT ${r.port}", level = StatusLevel.Ok, pulse = false)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.service, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("${r.latencyMs} ms", fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                        }
                        StatusPill(text = "OPEN", level = StatusLevel.Ok)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 3: SSL Inspector ────────────────────────────────────────────────────

@Composable
private fun SslInspectorTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isLoading by remember { mutableStateOf(false) }
    var certInfo by remember { mutableStateOf<SslCertInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "Target Domain", placeholder = "e.g. google.com or myvps.com")
                Spacer(Modifier.height(8.dp))
                InputField(value = port, onValueChange = { port = it }, label = "Port", placeholder = "443")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLoading) "Inspecting Certificate…" else "Inspect SSL Certificate",
                    icon = Icons.Rounded.Lock,
                    loading = isLoading,
                    enabled = !isLoading && host.isNotBlank(),
                    onClick = {
                        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
                        isLoading = true
                        err = null
                        certInfo = null
                        scope.launch {
                            try {
                                certInfo = SslInspector.inspect(clean, port.toIntOrNull() ?: 443)
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let { msg ->
            item {
                BannerCard(text = "SSL Error: $msg", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline)
            }
        }

        certInfo?.let { c ->
            val isDanger = c.isExpired || c.daysRemaining < 7
            item {
                ModernCard(
                    padding = 16.dp,
                    cornerRadius = 18.dp,
                    containerColor = if (isDanger) Ds.dangerDim else Ds.okDim,
                    borderColor = if (isDanger) Ds.danger.copy(alpha = 0.4f) else Ds.ok.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (c.isExpired) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (isDanger) Ds.danger else Ds.ok,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (c.isExpired) "Certificate Expired!" else "Valid Certificate (${c.daysRemaining} days left)",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDanger) Ds.danger else Ds.ok
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Valid from ${c.validFrom} until ${c.validTo}", fontSize = 12.sp, color = Ds.textPrimary)
                }
            }

            item {
                ModernCard(padding = 14.dp, cornerRadius = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Subject: ${c.subject}", fontSize = 11.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                        Text("Issuer: ${c.issuer}", fontSize = 11.5.sp, fontFamily = Telemetry, color = Ds.textSecondary)
                        Text("SHA-256 Fingerprint:\n${c.fingerprintSha256}", fontSize = 10.sp, fontFamily = Telemetry, color = Ds.accent)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 4: GeoIP & DNS ──────────────────────────────────────────────────────

@Composable
private fun IpInfoTab(t: Str) {
    val scope = rememberCoroutineScope()
    var targetIp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var geoData by remember { mutableStateOf<GeoIpData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun lookup() {
        val clean = targetIp.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        isLoading = true
        err = null
        scope.launch {
            try {
                geoData = IpInfoService.lookup(clean)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { lookup() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = targetIp, onValueChange = { targetIp = it }, label = "IP or Domain (Leave blank for current IP)", placeholder = "e.g. 8.8.8.8")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLoading) "Looking up GeoIP Data…" else "Lookup IP & ASN",
                    icon = Icons.Rounded.Language,
                    loading = isLoading,
                    onClick = { lookup() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let { msg ->
            item { BannerCard(text = "Lookup Error: $msg", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        geoData?.let { g ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(g.ip, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = Ds.accent)
                        StatusPill(text = "${g.countryCode} ${g.country}", level = StatusLevel.Info, pulse = false)
                    }
                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("City / Region: ${g.city}, ${g.region}", fontSize = 12.5.sp, color = Ds.textPrimary)
                        Text("ISP / Organization: ${g.isp}", fontSize = 12.5.sp, color = Ds.textPrimary)
                        Text("ASN: ${g.asn}", fontSize = 12.sp, fontFamily = Telemetry, color = Ds.textSecondary)
                        Text("Coordinates: ${g.latitude}, ${g.longitude}", fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 5: TCP Continuous Ping ──────────────────────────────────────────────

@Composable
private fun TcpPingTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isPinging by remember { mutableStateOf(false) }
    var pings by remember { mutableStateOf<List<Float>>(emptyList()) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isPinging) {
        while (isPinging && host.isNotBlank()) {
            val t0 = System.currentTimeMillis()
            try {
                val elapsed = TcpPinger.ping(host.trim(), port.toIntOrNull() ?: 443, timeoutMs = 2500).toFloat()
                pings = (pings + elapsed).takeLast(40)
                lastStatus = "Reply from $host:${port} time=${elapsed.toInt()}ms"
            } catch (e: Exception) {
                pings = (pings + 999f).takeLast(40)
                lastStatus = "Request timeout to $host:$port"
            }
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "Target Host or IP", placeholder = "e.g. 1.1.1.1 or example.com")
                Spacer(Modifier.height(8.dp))
                InputField(value = port, onValueChange = { port = it }, label = "TCP Port", placeholder = "443")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isPinging) "Stop Ping" else "Start Continuous TCP Ping",
                    icon = if (isPinging) Icons.Rounded.Bolt else Icons.Rounded.Sensors,
                    onClick = { isPinging = !isPinging },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (pings.isNotEmpty()) {
            val validPings = pings.filter { it < 900f }
            val avg = if (validPings.isNotEmpty()) validPings.average().toInt() else 0
            val min = if (validPings.isNotEmpty()) validPings.minOrNull()?.toInt() ?: 0 else 0
            val max = if (validPings.isNotEmpty()) validPings.maxOrNull()?.toInt() ?: 0 else 0

            item {
                ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Latency Graph", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Avg: ${avg}ms · Min: ${min}ms · Max: ${max}ms", fontSize = 11.sp, fontFamily = Telemetry, color = Ds.accent)
                    }
                    Spacer(Modifier.height(12.dp))
                    Sparkline(values = pings.map { if (it > 900f) 0f else it }, modifier = Modifier.fillMaxWidth().height(64.dp), color = Ds.accent)
                    Spacer(Modifier.height(8.dp))
                    lastStatus?.let {
                        Text(it, fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. CLOUDFLARE DNS SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CloudflareScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(Prefs.getCfToken(ctx)) }
    var zones by remember { mutableStateOf<List<CfZone>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<CfZone?>(null) }
    var records by remember { mutableStateOf<List<CfRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var showAddRecord by remember { mutableStateOf(false) }

    fun loadZones() {
        if (token.isBlank()) return
        isLoading = true
        err = null
        scope.launch {
            try {
                zones = CloudflareService.listZones(token)
                if (zones.isNotEmpty()) {
                    selectedZone = zones[0]
                }
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun loadRecords(zone: CfZone) {
        isLoading = true
        err = null
        scope.launch {
            try {
                records = CloudflareService.listRecords(token, zone.id)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (token.isNotBlank()) loadZones()
    }

    LaunchedEffect(selectedZone) {
        selectedZone?.let { loadRecords(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Cloud, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.cloudflareDns, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Zones & DNS Records Management", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
            }
        }

        // Token Input Card
        item {
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                InputField(
                    value = token,
                    onValueChange = { token = it },
                    label = "Cloudflare API Token",
                    isPassword = true,
                    placeholder = "Global / Edit DNS API Token"
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Save Token & Load Zones",
                    onClick = {
                        Prefs.setCfToken(ctx, token.trim())
                        loadZones()
                    },
                    loading = isLoading,
                    enabled = !isLoading && token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let { msg ->
            item { BannerCard(text = "Cloudflare Error: $msg", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        // Zones Selector
        if (zones.isNotEmpty()) {
            item {
                FilterChipRow(
                    items = zones.map { it.name },
                    selectedIndex = zones.indexOf(selectedZone).coerceAtLeast(0),
                    onSelect = { selectedZone = zones[it] }
                )
            }
        }

        // Records List
        if (records.isNotEmpty()) {
            items(records, key = { it.id }) { rec ->
                ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(rec.type, level = StatusLevel.Info, pulse = false)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rec.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rec.content, fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (rec.proxiable) {
                            StatusPill(
                                text = if (rec.proxied) "Proxied ☁️" else "DNS Only",
                                level = if (rec.proxied) StatusLevel.Warn else StatusLevel.Neutral,
                                pulse = false,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        try {
                                            CloudflareService.toggleProxy(token, rec.zoneId, rec.id, !rec.proxied)
                                            selectedZone?.let { loadRecords(it) }
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. ENCRYPTED SECRETS VAULT SCREEN (AES-256-GCM)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun VaultScreen(t: Str) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var password by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var showAddNote by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    fun unlock() {
        if (password.isBlank()) return
        val raw = Prefs.getVaultData(ctx)
        if (raw.isBlank()) {
            notes = emptyList()
            isUnlocked = true
            err = null
            return
        }
        try {
            val decrypted = EncryptedVault.decrypt(raw, password)
            notes = EncryptedVault.parseNotes(decrypted)
            isUnlocked = true
            err = null
        } catch (e: Exception) {
            err = "Incorrect master password or corrupted vault"
        }
    }

    fun saveVault() {
        val serialized = EncryptedVault.serializeNotes(notes)
        val encrypted = EncryptedVault.encrypt(serialized, password)
        Prefs.setVaultData(ctx, encrypted)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Security, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.encryptedVault, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Client-Side AES-256-GCM Storage", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                if (isUnlocked) {
                    PrimaryButton(
                        text = t.addNote,
                        icon = Icons.Rounded.Add,
                        onClick = { showAddNote = true },
                        modifier = Modifier.height(38.dp)
                    )
                }
            }
        }

        if (!isUnlocked) {
            item {
                BannerCard(
                    text = t.guideVault,
                    tone = BannerTone.Info,
                    icon = Icons.Rounded.Lock
                )
            }

            item {
                ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                    InputField(
                        value = password,
                        onValueChange = { password = it },
                        label = t.masterPassword,
                        isPassword = true,
                        placeholder = t.vaultHint
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = t.unlockVault,
                        icon = Icons.Rounded.LockOpen,
                        enabled = password.isNotBlank(),
                        onClick = { unlock() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            err?.let { msg ->
                item { BannerCard(text = msg, tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
            }
        } else {
            // Unlocked State
            if (notes.isEmpty()) {
                item {
                    EmptyState(
                        title = "No Secrets Stored",
                        hint = "Store passwords, SSH keys, server tokens securely.",
                        icon = Icons.Rounded.Key,
                        actionLabel = t.addNote,
                        onAction = { showAddNote = true }
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    ModernCard(padding = 14.dp, cornerRadius = 16.dp) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(note.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = Ds.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CircleIconButton(
                                    icon = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
                                    size = 30.dp,
                                    tint = Ds.accent,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(note.content))
                                        Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                                CircleIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete",
                                    size = 30.dp,
                                    tint = Ds.danger,
                                    onClick = {
                                        notes = notes.filterNot { it.id == note.id }
                                        saveVault()
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Ds.surfaceLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                note.content,
                                fontSize = 12.sp,
                                fontFamily = Telemetry,
                                color = Ds.textSecondary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddNote) {
        AlertDialog(
            onDismissRequest = { showAddNote = false },
            title = { Text(t.addNote, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InputField(value = newTitle, onValueChange = { newTitle = it }, label = "Secret Label / Title")
                    InputField(value = newContent, onValueChange = { newContent = it }, label = "Secret Content / Password / Key")
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = t.save,
                    enabled = newTitle.isNotBlank() && newContent.isNotBlank(),
                    onClick = {
                        notes = notes + VaultNote(
                            id = System.currentTimeMillis(),
                            title = newTitle.trim(),
                            content = newContent.trim()
                        )
                        saveVault()
                        newTitle = ""
                        newContent = ""
                        showAddNote = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddNote = false }) { Text(t.cancel) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. DEV LAB SCREEN (Base64 · JSON · CIDR · JWT · Hashes · Local Web Server)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DevLabScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    val tabTitles = listOf(
        "Base64 / URL",
        "JSON Lab",
        "CIDR Subnet",
        "JWT Decoder",
        "Hashes & UUID"
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(icon = Icons.Rounded.Terminal, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.devLab, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                Text("String & DevOps Utilities", fontSize = 11.sp, color = Ds.textTertiary)
            }
        }

        FilterChipRow(
            items = tabTitles,
            selectedIndex = subTab,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> Base64Tab(t)
                1 -> JsonTab(t)
                2 -> SubnetTab(t)
                3 -> JwtTab(t)
                4 -> HashesTab(t)
            }
        }
    }
}

@Composable
private fun Base64Tab(t: Str) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "Input String")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SoftButton(
                        text = "Base64 Encode",
                        onClick = { output = DevLabTools.base64Encode(input) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "Base64 Decode",
                        onClick = { output = DevLabTools.base64Decode(input) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (output.isNotEmpty()) {
            item {
                TerminalBox(command = output, title = "Output Result")
            }
        }
    }
}

@Composable
private fun JsonTab(t: Str) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "JSON String", placeholder = "{\"key\": \"value\"}")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SoftButton(
                        text = "Format / Prettify",
                        onClick = { output = DevLabTools.formatJson(input) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "Minify",
                        onClick = { output = DevLabTools.minifyJson(input) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (output.isNotEmpty()) {
            item { TerminalBox(command = output, title = "Formatted JSON") }
        }
    }
}

@Composable
private fun SubnetTab(t: Str) {
    var cidr by remember { mutableStateOf("192.168.1.0/24") }
    var result by remember { mutableStateOf<DevLabTools.SubnetInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = cidr, onValueChange = { cidr = it }, label = "CIDR Notation", placeholder = "10.0.0.0/16")
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Calculate Subnet",
                    onClick = {
                        try {
                            result = DevLabTools.calculateSubnet(cidr.trim())
                            err = null
                        } catch (e: Exception) {
                            err = e.message
                            result = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let { msg ->
            item { BannerCard(text = "Subnet Error: $msg", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        result?.let { r ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Netmask: ${r.netmask}", fontSize = 12.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                        Text("Network Address: ${r.network}", fontSize = 12.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                        Text("Broadcast Address: ${r.broadcast}", fontSize = 12.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                        Text("Usable Host Range: ${r.firstHost} — ${r.lastHost}", fontSize = 12.sp, fontFamily = Telemetry, color = Ds.accent)
                        Text("Total Usable Hosts: ${r.usableHosts}", fontSize = 12.sp, fontFamily = Telemetry, color = Ds.violet)
                    }
                }
            }
        }
    }
}

@Composable
private fun JwtTab(t: Str) {
    var jwt by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<DevLabTools.JwtInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = jwt, onValueChange = { jwt = it }, label = "JWT Token", placeholder = "eyJhbGciOiJIUzI1NiIsIn...")
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Decode JWT Token",
                    onClick = {
                        try {
                            info = DevLabTools.decodeJwt(jwt.trim())
                            err = null
                        } catch (e: Exception) {
                            err = e.message
                            info = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let { msg ->
            item { BannerCard(text = "JWT Error: $msg", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        info?.let { jwtInfo ->
            if (jwtInfo.expiryDate != null) {
                item {
                    StatusPill(
                        text = if (jwtInfo.isExpired) "Expired: ${jwtInfo.expiryDate}" else "Valid until: ${jwtInfo.expiryDate}",
                        level = if (jwtInfo.isExpired) StatusLevel.Danger else StatusLevel.Ok
                    )
                }
            }
            item { TerminalBox(command = jwtInfo.header, title = "JWT Header") }
            item { TerminalBox(command = jwtInfo.payload, title = "JWT Payload") }
        }
    }
}

@Composable
private fun HashesTab(t: Str) {
    var input by remember { mutableStateOf("") }
    var md5 by remember { mutableStateOf("") }
    var sha1 by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 18.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "Input String for Hashes")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = "Compute Hashes",
                        onClick = {
                            md5 = DevLabTools.hash(input, "MD5")
                            sha1 = DevLabTools.hash(input, "SHA-1")
                            sha256 = DevLabTools.hash(input, "SHA-256")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "Gen UUID",
                        onClick = { uuid = DevLabTools.generateUuid() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (sha256.isNotEmpty()) {
            item { TerminalBox(command = sha256, title = "SHA-256") }
            item { TerminalBox(command = md5, title = "MD5") }
            item { TerminalBox(command = sha1, title = "SHA-1") }
        }

        if (uuid.isNotEmpty()) {
            item { TerminalBox(command = uuid, title = "Generated UUID v4") }
        }
    }
}
