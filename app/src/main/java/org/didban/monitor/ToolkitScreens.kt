@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
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
                Text(t.networkHub, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
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
    val okCount = nodes.count { it.state == 1 }

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
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
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

        if (nodes.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reachability ($okCount / $totalCount Online)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )
                    Text(statusText, fontSize = 11.sp, fontFamily = Telemetry, color = Ds.accent)
                }
            }
        }

        items(nodes) { node ->
            val isOk = node.state == 1
            val isFail = node.state == 2
            ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(node.flag, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.location, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text(node.nodeKey, fontSize = 10.5.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isOk) Ds.okDim else if (isFail) Ds.dangerDim else Ds.surfaceHighlight)
                            .border(BorderStroke(1.dp, if (isOk) Ds.ok.copy(alpha = 0.35f) else if (isFail) Ds.danger.copy(alpha = 0.35f) else Ds.hairline), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
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
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
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
                    cornerRadius = 20.dp,
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
                        "Details (${r.latencyMs}ms):\n${r.details}",
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
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
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
                EmptyState(title = t.enterHostToScan, icon = Icons.Rounded.Search, radar = false)
            }
        } else {
            items(results) { res ->
                ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ds.okDim)
                                .border(BorderStroke(1.dp, Ds.ok.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "PORT ${res.port}",
                                fontSize = 11.sp,
                                fontFamily = Telemetry,
                                fontWeight = FontWeight.Bold,
                                color = Ds.ok
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(res.service, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("Open & Listening", fontSize = 10.5.sp, color = Ds.textTertiary)
                        }
                        Text("⚡ ${res.latencyMs} ms", fontSize = 11.sp, fontFamily = Telemetry, color = Ds.ok)
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
    var isChecking by remember { mutableStateOf(false) }
    var cert by remember { mutableStateOf<SslCertInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "Domain", placeholder = "e.g. google.com")
                Spacer(Modifier.height(8.dp))
                InputField(value = port, onValueChange = { port = it }, label = "Port", placeholder = "443")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isChecking) "Inspecting Certificate…" else "Inspect SSL Certificate",
                    icon = Icons.Rounded.Security,
                    loading = isChecking,
                    enabled = !isChecking && host.isNotBlank(),
                    onClick = {
                        isChecking = true
                        cert = null
                        err = null
                        scope.launch {
                            try {
                                cert = SslInspector.inspect(host.trim(), port.toIntOrNull() ?: 443)
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                isChecking = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let {
            item { BannerCard(text = "SSL Error: $it", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        cert?.let { c ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingGauge(
                            value = (c.daysRemaining.toFloat() / 90f * 100f).coerceIn(0f, 100f),
                            size = 52.dp,
                            strokeWidth = 4.5.dp,
                            tone = if (c.daysRemaining > 30) Ds.ok else if (c.daysRemaining > 7) Ds.warn else Ds.danger
                        ) {
                            Text("${c.daysRemaining}d", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = Ds.textPrimary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(c.subject, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("Issuer: ${c.issuer}", fontSize = 11.sp, color = Ds.textSecondary)
                            Text("Expires: ${c.validTo}", fontSize = 10.5.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                        }
                    }

                    if (c.sans.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Hairline()
                        Spacer(Modifier.height(8.dp))
                        Text("SANs (${c.sans.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(c.sans.joinToString(", "), fontSize = 10.5.sp, fontFamily = Telemetry, color = Ds.textTertiary, lineHeight = 15.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 4: GeoIP & DNS Info ─────────────────────────────────────────────────

@Composable
private fun IpInfoTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var isLookingUp by remember { mutableStateOf(false) }
    var geo by remember { mutableStateOf<GeoIpData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "IP or Domain", placeholder = "e.g. 1.1.1.1 or cloudflare.com")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isLookingUp) "Looking up Geo Data…" else "Lookup GeoIP & ASN",
                    icon = Icons.Rounded.Language,
                    loading = isLookingUp,
                    enabled = !isLookingUp && host.isNotBlank(),
                    onClick = {
                        isLookingUp = true
                        geo = null
                        err = null
                        scope.launch {
                            try {
                                geo = IpInfoService.lookup(host.trim())
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                isLookingUp = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        err?.let {
            item { BannerCard(text = "Lookup Error: $it", tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        geo?.let { g ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.flag.ifBlank { "🌐" }, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(g.country.ifBlank { "Unknown Location" }, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("${g.city}, ${g.region}", fontSize = 11.5.sp, color = Ds.textSecondary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ds.surfaceLow)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("ISP / Org", fontSize = 10.sp, color = Ds.textTertiary)
                                Spacer(Modifier.height(2.dp))
                                Text(g.isp.ifBlank { g.org }, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ds.surfaceLow)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("ASN", fontSize = 10.sp, color = Ds.textTertiary)
                                Spacer(Modifier.height(2.dp))
                                Text(g.asn.ifBlank { "—" }, fontSize = 11.5.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab 5: TCP Ping ─────────────────────────────────────────────────────────

@Composable
private fun TcpPingTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isPinging by remember { mutableStateOf(false) }
    var pings by remember { mutableStateOf<List<Float>>(emptyList()) }
    var lastStatus by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = host, onValueChange = { host = it }, label = "Target IP or Domain", placeholder = "e.g. 1.1.1.1")
                Spacer(Modifier.height(8.dp))
                InputField(value = port, onValueChange = { port = it }, label = "Port", placeholder = "443")
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isPinging) "Stop Continuous Ping" else "Start Continuous Ping",
                    icon = if (isPinging) Icons.Rounded.Bolt else Icons.Rounded.Speed,
                    onClick = {
                        isPinging = !isPinging
                        if (isPinging) {
                            pings = emptyList()
                            scope.launch {
                                while (isPinging) {
                                    val lat = try {
                                        TcpPinger.ping(host.trim(), port.toIntOrNull() ?: 443)
                                    } catch (_: Exception) { -1L }
                                    val ok = lat >= 0L
                                    pings = (pings + if (ok) lat.toFloat() else 999f).takeLast(30)
                                    lastStatus = if (ok) "Reply from $host: time=${lat}ms" else "Request timed out"
                                    delay(1000)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (pings.isNotEmpty()) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    val validPings = pings.filter { it < 900f }
                    val avg = if (validPings.isEmpty()) 0f else validPings.average().toFloat()
                    val loss = ((pings.count { it >= 900f }.toFloat() / pings.size) * 100).toInt()

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Telemetry Sparkline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Avg: ${avg.toInt()}ms · Loss: $loss%", fontSize = 11.sp, fontFamily = Telemetry, color = if (loss > 0) Ds.warn else Ds.ok)
                    }

                    Spacer(Modifier.height(10.dp))
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
    var showGuide by remember { mutableStateOf(zones.isEmpty()) }

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
                    .padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Cloud, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.cloudflareDns, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Zones & DNS Records Management", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                if (selectedZone != null) {
                    PrimaryButton(
                        text = "New Record",
                        icon = Icons.Rounded.Add,
                        onClick = { showAddRecord = true },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }

        // Hero Cloudflare Bento
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(
                            color = if (selectedZone != null) Ds.ok else Ds.textTertiary,
                            size = 8.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            selectedZone?.name ?: "No Domain Selected",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedZone != null) Ds.textPrimary else Ds.textSecondary
                        )
                    }

                    SoftButton(
                        text = if (isLoading) "Syncing…" else "Refresh",
                        icon = Icons.Rounded.Refresh,
                        onClick = { selectedZone?.let { loadRecords(it) } },
                        modifier = Modifier.height(34.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BentoMicroPod(
                        title = "Active Zones",
                        value = "${zones.size}",
                        unit = "Domains",
                        color = Ds.accent,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMicroPod(
                        title = "DNS Records",
                        value = "${records.size}",
                        unit = "Entries",
                        color = Ds.ok,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMicroPod(
                        title = "Proxied (☁️)",
                        value = "${records.count { it.proxied }}",
                        unit = "Orange",
                        color = Ds.warn,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Step-by-Step Cloudflare Guide (Permanent Expandable Bento Card)
        item {
            StepByStepCloudflareGuideCard(
                t = t,
                isExpanded = showGuide,
                onToggleExpand = { showGuide = !showGuide }
            )
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
                ModernCard(padding = 12.dp, cornerRadius = 16.dp) {
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
                                            CloudflareService.saveRecord(
                                                apiToken = token,
                                                zoneId = rec.zoneId,
                                                recordId = rec.id,
                                                type = rec.type,
                                                name = rec.name,
                                                content = rec.content,
                                                proxied = !rec.proxied,
                                                ttl = rec.ttl
                                            )
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

    if (showAddRecord && selectedZone != null) {
        var recType by remember { mutableStateOf("A") }
        var recName by remember { mutableStateOf("") }
        var recContent by remember { mutableStateOf("") }
        var recProxied by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddRecord = false },
            title = { Text("Add DNS Record", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SegmentedControl(
                        items = listOf("A", "AAAA", "CNAME", "TXT"),
                        selectedIndex = listOf("A", "AAAA", "CNAME", "TXT").indexOf(recType).coerceAtLeast(0),
                        onSelect = { recType = listOf("A", "AAAA", "CNAME", "TXT")[it] }
                    )
                    InputField(value = recName, onValueChange = { recName = it }, label = "Record Name", placeholder = "e.g. sub or @")
                    InputField(value = recContent, onValueChange = { recContent = it }, label = "Target IP / Value", placeholder = "e.g. 1.2.3.4")
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Proxy Status (Orange Cloud ☁️)", fontSize = 12.sp, color = Ds.textSecondary)
                        Switch(
                            checked = recProxied,
                            onCheckedChange = { recProxied = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ds.warn, checkedThumbColor = Ds.onAccent)
                        )
                    }
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = t.save,
                    enabled = recName.isNotBlank() && recContent.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                CloudflareService.saveRecord(
                                    apiToken = token,
                                    zoneId = selectedZone!!.id,
                                    recordId = null,
                                    type = recType,
                                    name = recName.trim(),
                                    content = recContent.trim(),
                                    proxied = recProxied,
                                    ttl = 1
                                )
                                showAddRecord = false
                                selectedZone?.let { loadRecords(it) }
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddRecord = false }) { Text(t.cancel) }
            }
        )
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
    var newTags by remember { mutableStateOf("SSH") }
    var revealedNoteId by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(!isUnlocked) }

    fun unlock() {
        if (password.isBlank()) return
        if (!Prefs.isVaultInitialized(ctx)) {
            Prefs.setupMasterPassword(ctx, password)
            notes = emptyList()
            isUnlocked = true
            err = null
            return
        }
        if (Prefs.verifyMasterPassword(ctx, password)) {
            notes = Prefs.loadVaultNotes(ctx, password)
            isUnlocked = true
            err = null
        } else {
            err = "Incorrect master password"
        }
    }

    fun saveVault() {
        Prefs.saveVaultNotes(ctx, notes, password)
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
                    .padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Security, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.encryptedVault, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Client-Side AES-256-GCM Storage", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                if (isUnlocked) {
                    PrimaryButton(
                        text = t.addNote,
                        icon = Icons.Rounded.Add,
                        onClick = { showAddNote = true },
                        modifier = Modifier.height(36.dp)
                    )
                }
            }
        }

        // Hero Vault Bento
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(
                            color = if (isUnlocked) Ds.ok else Ds.warn,
                            size = 8.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isUnlocked) "گاوصندوق باز است (AES-256)" else "گاوصندوق قفل است",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) Ds.ok else Ds.warn
                        )
                    }

                    if (isUnlocked) {
                        SoftButton(
                            text = "Lock Vault",
                            icon = Icons.Rounded.Lock,
                            onClick = {
                                isUnlocked = false
                                password = ""
                                notes = emptyList()
                            },
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BentoMicroPod(
                        title = "Encryption",
                        value = "AES-256",
                        unit = "GCM",
                        color = Ds.accent,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMicroPod(
                        title = "Saved Secrets",
                        value = if (isUnlocked) "${notes.size}" else "🔒",
                        unit = "Items",
                        color = Ds.ok,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMicroPod(
                        title = "Derivation",
                        value = "PBKDF2",
                        unit = "Key",
                        color = Ds.violet,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Step-by-Step Vault Guide (Permanent Expandable Bento Card)
        item {
            StepByStepVaultGuideCard(
                t = t,
                isExpanded = showGuide,
                onToggleExpand = { showGuide = !showGuide }
            )
        }

        if (!isUnlocked) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    InputField(
                        value = password,
                        onValueChange = { password = it },
                        label = t.masterPassword,
                        isPassword = true,
                        placeholder = "Master Password"
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = if (Prefs.isVaultInitialized(ctx)) t.unlockVault else "Set Master Password & Initialize",
                        icon = Icons.Rounded.LockOpen,
                        onClick = { unlock() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            err?.let {
                item { BannerCard(text = it, tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
            }
        } else {
            if (notes.isEmpty()) {
                item {
                    EmptyState(
                        title = "هیچ رازی در گاوصندوق ثبت نشده است",
                        hint = "کلیدهای خصوصی SSH، پسورد سرورها یا کانفیگ‌های وایرگارد خود را به صورت کاملا محلی و رمزگذاری شده ذخیره کنید.",
                        icon = Icons.Rounded.Key,
                        radar = false,
                        actionLabel = t.addNote,
                        onAction = { showAddNote = true }
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    val isRevealed = revealedNoteId == note.id

                    ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Ds.surfaceElevated)
                                        .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        note.tags.ifBlank { "SECRET" },
                                        fontSize = 10.5.sp,
                                        fontFamily = Telemetry,
                                        fontWeight = FontWeight.Bold,
                                        color = Ds.accent
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(note.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ds.textPrimary)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CircleIconButton(
                                    icon = if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle",
                                    tint = Ds.accent,
                                    size = 28.dp,
                                    onClick = { revealedNoteId = if (isRevealed) null else note.id }
                                )
                                CircleIconButton(
                                    icon = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Ds.textSecondary,
                                    size = 28.dp,
                                    onClick = {
                                        clipboard.setText(AnnotatedString(note.content))
                                        Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                                    }
                                )
                                CircleIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Ds.danger,
                                    size = 28.dp,
                                    onClick = {
                                        notes = notes.filter { it.id != note.id }
                                        saveVault()
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ds.surfaceLow)
                                .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                if (isRevealed) note.content else "••••••••••••••••••••••••",
                                fontSize = 12.sp,
                                fontFamily = Telemetry,
                                color = if (isRevealed) Ds.textPrimary else Ds.textTertiary,
                                lineHeight = 16.sp
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
                    SegmentedControl(
                        items = listOf("SSH", "Password", "WireGuard", "Config"),
                        selectedIndex = listOf("SSH", "Password", "WireGuard", "Config").indexOf(newTags).coerceAtLeast(0),
                        onSelect = { newTags = listOf("SSH", "Password", "WireGuard", "Config")[it] }
                    )
                    InputField(value = newTitle, onValueChange = { newTitle = it }, label = "Title", placeholder = "e.g. Frankfurt Root SSH Key")
                    InputField(value = newContent, onValueChange = { newContent = it }, label = "Confidential Content", placeholder = "Paste private key, password, or config…")
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = t.save,
                    enabled = newTitle.isNotBlank() && newContent.isNotBlank(),
                    onClick = {
                        val newNote = VaultNote(
                            id = System.currentTimeMillis(),
                            title = newTitle.trim(),
                            content = newContent.trim(),
                            tags = newTags
                        )
                        notes = notes + newNote
                        saveVault()
                        showAddNote = false
                        newTitle = ""
                        newContent = ""
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
// 4. DEV LAB SCREEN (Base64 · JSON · Subnet · JWT · Hashes)
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
                Text(t.devLab, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
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
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "Input String")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
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
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(
                        text = "URL Encode",
                        onClick = { output = DevLabTools.urlEncode(input) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "URL Decode",
                        onClick = { output = DevLabTools.urlDecode(input) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (output.isNotEmpty()) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Output Result", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        SoftButton(
                            text = "Copy",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(output))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ds.surfaceLow)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(output, fontSize = 12.sp, fontFamily = Telemetry, color = Ds.accent, lineHeight = 16.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun JsonTab(t: Str) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "Raw JSON Input")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Format & Beautify",
                        onClick = {
                            val res = DevLabTools.formatJson(input)
                            if (res.startsWith("Invalid") || res.startsWith("JSON Error")) {
                                err = res
                                output = ""
                            } else {
                                output = res
                                err = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SoftButton(
                        text = "Minify",
                        onClick = {
                            val res = DevLabTools.minifyJson(input)
                            if (res.startsWith("Invalid") || res.startsWith("JSON Error")) {
                                err = res
                                output = ""
                            } else {
                                output = res
                                err = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        err?.let {
            item { BannerCard(text = it, tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        if (output.isNotEmpty()) {
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Processed JSON", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        SoftButton(
                            text = "Copy",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(output))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ds.surfaceLow)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(output, fontSize = 12.sp, fontFamily = Telemetry, color = Ds.textPrimary, lineHeight = 16.sp)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubnetTab(t: Str) {
    var cidr by remember { mutableStateOf("192.168.1.0/24") }
    var result by remember { mutableStateOf<DevLabTools.SubnetInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cidr) {
        try {
            result = DevLabTools.calculateSubnet(cidr)
            err = null
        } catch (e: Exception) {
            result = null
            err = e.message
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = cidr, onValueChange = { cidr = it }, label = "CIDR Notation", placeholder = "e.g. 10.0.0.0/16")
            }
        }

        err?.let {
            item { BannerCard(text = it, tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        result?.let { r ->
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Text("Subnet Calculation", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    SubnetRow("Netmask", r.netmask)
                    SubnetRow("Network Address", r.network)
                    SubnetRow("Broadcast Address", r.broadcast)
                    SubnetRow("First Usable Host", r.firstHost)
                    SubnetRow("Last Usable Host", r.lastHost)
                    SubnetRow("Total Usable Hosts", "${r.usableHosts}")
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubnetRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.5.sp, color = Ds.textSecondary)
        Text(value, fontSize = 12.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.accent)
    }
}

@Composable
private fun JwtTab(t: Str) {
    var token by remember { mutableStateOf("") }
    var jwtInfo by remember { mutableStateOf<DevLabTools.JwtInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        if (token.isBlank()) {
            jwtInfo = null
            err = null
            return@LaunchedEffect
        }
        try {
            jwtInfo = DevLabTools.decodeJwt(token)
            err = null
        } catch (e: Exception) {
            jwtInfo = null
            err = e.message
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = token, onValueChange = { token = it }, label = "Paste JWT Token")
            }
        }

        err?.let {
            item { BannerCard(text = it, tone = BannerTone.Danger, icon = Icons.Rounded.ErrorOutline) }
        }

        jwtInfo?.let { info ->
            item {
                ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                    Text("Header (JOSE)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(info.header, fontSize = 11.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                }
            }

            item {
                ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                    Text("Payload (Claims)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.ok)
                    Spacer(Modifier.height(6.dp))
                    Text(info.payload, fontSize = 11.5.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                    info.expiryDate?.let { exp ->
                        Spacer(Modifier.height(8.dp))
                        Text("Expires: $exp", fontSize = 10.5.sp, color = if (info.isExpired) Ds.danger else Ds.textSecondary)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HashesTab(t: Str) {
    var input by remember { mutableStateOf("") }
    var md5 by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var sha512 by remember { mutableStateOf("") }
    var randomUuid by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    LaunchedEffect(input) {
        if (input.isNotEmpty()) {
            md5 = DevLabTools.hash(input, "MD5")
            sha256 = DevLabTools.hash(input, "SHA-256")
            sha512 = DevLabTools.hash(input, "SHA-512")
        } else {
            md5 = ""
            sha256 = ""
            sha512 = ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                InputField(value = input, onValueChange = { input = it }, label = "Input String to Hash")
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = "Generate Random UUID v4",
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { randomUuid = DevLabTools.generateUuid() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (randomUuid.isNotEmpty()) {
            item {
                ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("UUID v4", fontSize = 10.5.sp, color = Ds.textTertiary)
                            Text(randomUuid, fontSize = 12.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.ok)
                        }
                        SoftButton(
                            text = "Copy",
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboard.setText(AnnotatedString(randomUuid))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (sha256.isNotEmpty()) {
            item {
                ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                    Text("SHA-256", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                    Spacer(Modifier.height(4.dp))
                    Text(sha256, fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textPrimary)

                    Spacer(Modifier.height(8.dp))
                    Hairline()
                    Spacer(Modifier.height(8.dp))

                    Text("MD5", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.warn)
                    Spacer(Modifier.height(4.dp))
                    Text(md5, fontSize = 11.sp, fontFamily = Telemetry, color = Ds.textPrimary)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SUB-COMPONENTS & GUIDE CARDS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BentoMicroPod(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Ds.surfaceLow)
            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp)
    ) {
        Column {
            Text(title, fontSize = 10.sp, color = Ds.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    value,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Telemetry,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit.isNotBlank()) {
                    Text(
                        unit,
                        fontSize = 9.5.sp,
                        color = Ds.textTertiary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepByStepCloudflareGuideCard(
    t: Str,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp,
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Ds.hairline, Ds.accent.copy(alpha = 0.35f), Ds.hairline)))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBadge(
                        icon = Icons.Rounded.HelpOutline,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 32.dp,
                        iconSize = 17.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            t.cfGuideHeader,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "DNS & Proxy Control (3 Easy Steps)",
                            fontSize = 10.5.sp,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(
                    icon = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) t.hideGuide else t.showGuide,
                    tint = Ds.accent,
                    size = 32.dp,
                    onClick = onToggleExpand
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Hairline()
                    ToolkitStepPod(stepNum = "1", title = t.cfStep1Title, desc = t.cfStep1Desc)
                    ToolkitStepPod(stepNum = "2", title = t.cfStep2Title, desc = t.cfStep2Desc)
                    ToolkitStepPod(stepNum = "3", title = t.cfStep3Title, desc = t.cfStep3Desc)
                }
            }
        }
    }
}

@Composable
private fun StepByStepVaultGuideCard(
    t: Str,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp,
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Ds.hairline, Ds.accent.copy(alpha = 0.35f), Ds.hairline)))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBadge(
                        icon = Icons.Rounded.HelpOutline,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 32.dp,
                        iconSize = 17.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            t.vaultGuideHeader,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Zero-Knowledge Security (3 Easy Steps)",
                            fontSize = 10.5.sp,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(
                    icon = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) t.hideGuide else t.showGuide,
                    tint = Ds.accent,
                    size = 32.dp,
                    onClick = onToggleExpand
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Hairline()
                    ToolkitStepPod(stepNum = "1", title = t.vaultStep1Title, desc = t.vaultStep1Desc)
                    ToolkitStepPod(stepNum = "2", title = t.vaultStep2Title, desc = t.vaultStep2Desc)
                    ToolkitStepPod(stepNum = "3", title = t.vaultStep3Title, desc = t.vaultStep3Desc)
                }
            }
        }
    }
}

@Composable
private fun ToolkitStepPod(
    stepNum: String,
    title: String,
    desc: String
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Ds.accentDim)
                .border(BorderStroke(1.dp, Ds.accent.copy(alpha = 0.4f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(stepNum, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            Text(desc, fontSize = 11.sp, color = Ds.textSecondary, lineHeight = 16.sp)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// UNIFIED SCREEN HUBS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NetworkCloudScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedControl(
                items = listOf(t.navNetwork, t.navCloudflare),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }
        Box(Modifier.weight(1f)) {
            if (subTab == 0) {
                NetworkHubScreen(t = t)
            } else {
                CloudflareScreen(t = t)
            }
        }
    }
}

@Composable
fun VaultToolsScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            SegmentedControl(
                items = listOf(t.navVault, t.navTools),
                selectedIndex = subTab,
                onSelect = { subTab = it }
            )
        }
        Box(Modifier.weight(1f)) {
            if (subTab == 0) {
                VaultScreen(t = t)
            } else {
                DevLabScreen(t = t)
            }
        }
    }
}
