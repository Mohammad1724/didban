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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    val scrollState = rememberScrollState()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    val tabs = listOf(
        Icons.Rounded.Public to "چک‌هاست جهانی",
        Icons.Rounded.Security to "تست فیلترینگ و DPI",
        Icons.Rounded.Search to "پورت اسکنر",
        Icons.Rounded.Lock to "بازرس SSL",
        Icons.Rounded.Language to "اطلاعات IP و دامنه",
        Icons.Rounded.Sensors to "پینگ مداوم TCP"
    )

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(
                icon = Icons.Rounded.Public,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                size = 36.dp,
                iconSize = 18.dp
            )
            Text(t.networkHub, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))

        // ── Smooth Horizontally Scrollable Tab Bar (Vector Icons) ──
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, (icon, title) ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            title,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

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

// ── Tab 0: Check-Host.net Global Reachability Suite ──────────────────────────

@Composable
private fun CheckHostHubTab(t: Str) {
    val scope = rememberCoroutineScope()
    var targetHost by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("ping") }
    var tcpPort by remember { mutableStateOf("80") }
    var isChecking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }

    val okCount = nodes.count { it.state == 1 }
    val totalCount = nodes.size

    fun cleanTarget(raw: String): String {
        return raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
    }

    fun startProbe() {
        if (isChecking) return
        val clean = cleanTarget(targetHost)
        if (clean.isEmpty()) return

        val hostToTest = if (selectedType == "tcp") "$clean:$tcpPort" else clean

        isChecking = true
        statusText = "در حال ارسال درخواست به ۲۰ نود در سراسر دنیا…"
        nodes = emptyList()

        scope.launch {
            try {
                val (reqId, initialNodes) = CheckHostService.startCheck(hostToTest, selectedType, 20)
                nodes = initialNodes

                for (i in 0 until 14) {
                    delay(1500)
                    val done = CheckHostService.pollResults(reqId, selectedType, nodes)
                    nodes = nodes.toList() // trigger UI update
                    if (done) break
                }
                nodes.forEach { if (it.state == 0) { it.state = 2; it.resultText = "تایم‌اوت" } }
                nodes = nodes.toList()
                val currentOk = nodes.count { it.state == 1 }
                statusText = "$currentOk از $totalCount نود جهانی در دسترس هستند"
            } catch (e: Exception) {
                statusText = "خطا در تست: ${e.message}"
            } finally {
                isChecking = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "چک‌هاست جهانی (Check-Host.net)",
                description = "بررسی وضعیت پینگ، وب، پورت و دی‌ان‌اس از ۲۰ نود در کشورهای مختلف جهان:",
                bullets = listOf(
                    "PING: تست پکت‌لاس و زمان پاسخ از نقاط مختلف جهان",
                    "HTTP: بررسی بالا بودن وبسایت و کد پاسخ سرور (200 OK)",
                    "TCP: تست باز بودن پورت‌های سرور از خارج کشور",
                    "DNS: بررسی انتشار رکوردهای دامنه در سرورهای نام جهان"
                )
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text("دامنه یا آی‌پی مقصد (مثلاً google.com)", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("ping", "http", "tcp", "dns").forEach { type ->
                            val selected = selectedType == type
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
                                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { selectedType = type }
                            ) {
                                Text(
                                    type.uppercase(Locale.US),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectedType == "tcp") {
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = tcpPort,
                                onValueChange = { tcpPort = it },
                                label = { Text("پورت", fontSize = 9.5.sp) },
                                modifier = Modifier.width(75.dp),
                                singleLine = true
                            )
                        }
                    }

                    PrimaryActionButton(
                        text = if (isChecking) "در حال استعلام از نودهای جهانی…" else "شروع تست نودهای جهانی",
                        onClick = { startProbe() },
                        enabled = !isChecking && targetHost.isNotBlank()
                    )
                }
            }
        }

        if (statusText.isNotBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusText, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (totalCount > 0) {
                        Spacer(Modifier.weight(1f))
                        StatusPill("$okCount / $totalCount", isOnline = okCount > 0)
                    }
                }
            }
        }

        if (nodes.isEmpty() && !isChecking) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(
                            icon = Icons.Rounded.Public,
                            tint = MaterialTheme.colorScheme.primary,
                            background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            size = 46.dp,
                            iconSize = 22.dp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("آدرس دامنه یا آی‌پی را وارد کرده و دکمه شروع تست را بزنید", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(nodes, key = { it.nodeKey }) { node ->
                ModernCard(padding = 9.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(node.flag, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.location.ifEmpty { node.countryCode }, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text(node.nodeKey, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (node.state) {
                                1 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                2 -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                node.resultText,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (node.state) {
                                    1 -> Color(0xFF047857)
                                    2 -> Color(0xFFDC2626)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ── Tab 1: Censorship & DPI Tester ──────────────────────────────────────────

@Composable
private fun CensorshipTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isTesting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CensorshipDiagnosticResult?>(null) }

    fun runTest() {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank() || isTesting) return
        isTesting = true
        result = null
        scope.launch {
            try {
                result = CensorshipTester.diagnose(clean, port.toIntOrNull() ?: 443)
            } finally {
                isTesting = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "تست فیلترینگ و اختلال شبکه (DPI / TLS)",
                description = "این ابزار هوشمند نحوه مسدودسازی یا اختلال ارتباط با سرور را در ۳ مرحله بررسی می‌کند:",
                bullets = listOf(
                    "بررسی دسترسی لایه ۳/۴ (آیا IP سرور فیلتر شده است؟)",
                    "تست تزریق پکت‌های جعلی TCP RST توسط سامانه‌های فیلترینگ (DPI)",
                    "تست هندشیک امن TLS/SNI و کشف دستکاری در ارتباطات"
                )
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("دامنه یا آی‌پی سرور", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("پورت", fontSize = 10.sp) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = { runTest() },
                            enabled = !isTesting && host.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (isTesting) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White)
                            else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("شروع عیب‌یابی و تست", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        result?.let { r ->
            item {
                ModernCard(
                    padding = 12.dp,
                    containerColor = if (r.isFiltered) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
                    borderColor = if (r.isFiltered) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.35f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            r.diagnosis,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (r.isFiltered) Color(0xFFDC2626) else Color(0xFF047857)
                        )
                        Text("مقصد: ${r.host}:${r.port} (تاخیر: ${r.latencyMs}ms)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(3.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("۱. لایه اتصال TCP:", fontSize = 11.5.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = if (r.tcpReachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (r.tcpReachable) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(if (r.tcpReachable) "متصل شد" else "ناموفق", fontWeight = FontWeight.Bold, color = if (r.tcpReachable) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("۲. هندشیک امن TLS:", fontSize = 11.5.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    imageVector = if (r.tlsReachable) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (r.tlsReachable) Color(0xFF10B981) else Color(0xFFEF4444),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(if (r.tlsReachable) "موفق" else "اختلال فیلترینگ", fontWeight = FontWeight.Bold, color = if (r.tlsReachable) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }

                        Spacer(Modifier.height(3.dp))
                        Text("گزارش:\n${r.details}", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
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

    fun runScan() {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank() || isScanning) return
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
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای پورت اسکنر",
                description = "پورت‌های کلیدی سرور (SSH، وب HTTP/HTTPS، دیتابیس‌ها و پنل‌ها) را اسکن می‌کند تا از باز بودن پورت‌ها اطمینان حاصل کنید."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("دامنه یا آی‌پی سرور برای اسکن پورت‌ها", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    PrimaryActionButton(
                        text = if (isScanning) "در حال اسکن ($scannedCount / ${portsToScan.size})…" else "شروع اسکن پورت‌های متداول",
                        onClick = { runScan() },
                        enabled = !isScanning && host.isNotBlank()
                    )
                }
            }
        }

        if (results.isEmpty() && !isScanning) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(t.enterHostToScan, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                }
            }
        } else {
            items(results, key = { it.port }) { r ->
                ModernCard(padding = 9.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF10B981).copy(alpha = 0.15f)) {
                            Text("PORT ${r.port}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 10.5.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.service, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Text("${r.latencyMs} ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("OPEN", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
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

    fun checkSsl() {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank() || isLoading) return
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
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای بازرس سرتیفیکیت SSL",
                description = "بررسی روزهای باقی‌مانده تا انقضای گواهی HTTPS، نام صادرکننده و الگوریتم امنیتی."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("دامنه وبسایت (مثلاً google.com)", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("پورت", fontSize = 10.sp) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = { checkSsl() },
                            enabled = !isLoading && host.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            if (isLoading) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White)
                            else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Text("بررسی گواهی SSL", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (err != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    Text("خطا: $err", color = Color(0xFFEF4444), fontSize = 11.5.sp)
                }
            }
        }

        certInfo?.let { c ->
            item {
                ModernCard(
                    padding = 12.dp,
                    containerColor = if (c.isExpired || c.daysRemaining < 7) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
                    borderColor = if (c.isExpired || c.daysRemaining < 7) Color(0xFFEF4444).copy(alpha = 0.35f) else Color(0xFF10B981).copy(alpha = 0.35f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            imageVector = if (c.isExpired) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (c.isExpired) Color(0xFFDC2626) else Color(0xFF047857),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            if (c.isExpired) "گواهی منقضی شده است!" else "معتبر (${c.daysRemaining} روز باقی‌مانده)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (c.isExpired) Color(0xFFDC2626) else Color(0xFF047857)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text("اعتبار از ${c.validFrom} تا ${c.validTo}", fontSize = 11.sp)
                }
            }

            item {
                ModernCard(padding = 10.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("دامنه (Subject): ${c.subject}", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        Text("صادرکننده (Issuer): ${c.issuer}", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("اثر انگشت SHA-256:\n${c.fingerprintSha256}", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Tab 4: IP & GeoIP Info ──────────────────────────────────────────────────

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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "استعلام مشخصات IP و موقعیت (GeoIP)",
                description = "مشاهده کشور، شهر، شرکت ارائه‌دهنده اینترنت (ISP)، شماره ASN و مختصات جغرافیایی."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetIp,
                        onValueChange = { targetIp = it },
                        label = { Text("آی‌پی یا دامنه (خالی = آی‌پی فعلی من)", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    PrimaryActionButton(
                        text = if (isLoading) "در حال استعلام اطلاعات…" else "استعلام موقعیت و مشخصات IP",
                        onClick = { lookup() },
                        enabled = !isLoading
                    )
                }
            }
        }

        if (err != null) {
            item {
                ModernCard(
                    padding = 10.dp,
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                    borderColor = Color(0xFFEF4444).copy(alpha = 0.35f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Text(err!!, color = Color(0xFFEF4444), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        geoData?.let { g ->
            item {
                ModernCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.flag, fontSize = 28.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(g.ip, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${g.city}, ${g.country}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("ارائه‌دهنده: ${g.isp} (${g.org})", fontSize = 11.5.sp)
                    Text("شماره ASN: ${g.asn}", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("منطقه زمانی: ${g.timezone}", fontSize = 11.sp)
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Tab 5: TCP Continuous Ping ──────────────────────────────────────────────

@Composable
private fun TcpPingTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var isPinging by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun startPing() {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (clean.isBlank()) return
        if (isPinging) {
            isPinging = false
            return
        }
        isPinging = true
        logs = emptyList()

        scope.launch {
            var seq = 1
            while (isPinging) {
                try {
                    val p = port.toIntOrNull() ?: 80
                    val lat = TcpPinger.ping(clean, p, 1500)
                    logs = (listOf("seq=$seq host=$clean:$p time=${lat}ms") + logs).take(50)
                } catch (e: Exception) {
                    logs = (listOf("seq=$seq host=$clean timeout (${e.message})") + logs).take(50)
                }
                seq++
                delay(1000)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای پینگ مداوم TCP",
                description = "اندازه‌گیری زمان پاسخ (Latency) لحظه‌ای سرور در بازه‌های ۱ ثانیه‌ای برای کشف پکت‌لاس."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("آدرس سرور یا دامنه مقصد", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("پورت", fontSize = 10.sp) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = { startPing() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPinging) Color(0xFFEF4444) else Color(0xFF0D9488)
                            ),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text(if (isPinging) "توقف پینگ" else "شروع پینگ مداوم", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        items(logs) { line ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    line,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (line.contains("time=")) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. CLOUDFLARE DNS MANAGER SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CloudflareScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiToken by remember { mutableStateOf(Prefs.getCfToken(ctx)) }
    var isTokenSaved by remember { mutableStateOf(apiToken.isNotBlank()) }
    var zones by remember { mutableStateOf<List<CfZone>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<CfZone?>(null) }
    var records by remember { mutableStateOf<List<CfRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun loadZones() {
        if (apiToken.isBlank()) return
        isLoading = true
        err = null
        scope.launch {
            try {
                zones = CloudflareService.listZones(apiToken)
                if (zones.isNotEmpty() && selectedZone == null) {
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
                records = CloudflareService.listRecords(apiToken, zone.id)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedZone) {
        selectedZone?.let { loadRecords(it) }
    }

    LaunchedEffect(Unit) {
        if (apiToken.isNotBlank()) loadZones()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(
                    icon = Icons.Rounded.Cloud,
                    tint = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Text(t.cloudflareDns, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            FeatureGuideCard(
                title = "مدیریت DNS کلودفلر",
                description = t.guideCloudflare
            )
        }

        if (!isTokenSaved) {
            item {
                ModernCard(padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = apiToken,
                            onValueChange = { apiToken = it },
                            label = { Text("Cloudflare API Token", fontSize = 11.5.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        PrimaryActionButton(
                            text = t.saveToken,
                            onClick = {
                                Prefs.setCfToken(ctx, apiToken)
                                isTokenSaved = true
                                loadZones()
                            }
                        )
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        selectedZone?.name ?: "دامنه‌ها (${zones.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { isTokenSaved = false }) { Text(t.changeToken, fontSize = 10.5.sp) }
                    IconButton(onClick = { selectedZone?.let { loadRecords(it) } }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(15.dp))
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) { Text("+ ${t.addRecord}", fontSize = 11.sp) }
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (err != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    Text(err!!, color = Color(0xFFEF4444), fontSize = 11.sp)
                }
            }
        }

        items(records, key = { it.id }) { rec ->
            val badgeColor = when (rec.type.uppercase()) {
                "A" -> Color(0xFF0D9488)
                "AAAA" -> Color(0xFF3B82F6)
                "CNAME" -> Color(0xFF8B5CF6)
                "TXT" -> Color(0xFF64748B)
                "MX" -> Color(0xFFF59E0B)
                else -> Color(0xFF6366F1)
            }

            ModernCard(padding = 10.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = badgeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            rec.type,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(rec.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(rec.content, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rec.proxiable) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = if (rec.proxied) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (rec.proxied) "پروکسی" else "فقط DNS",
                                fontSize = 9.5.sp,
                                color = if (rec.proxied) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                selectedZone?.let { z ->
                                    CloudflareService.deleteRecord(apiToken, z.id, rec.id)
                                    loadRecords(z)
                                }
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    if (showAddDialog) {
        var recType by remember { mutableStateOf("A") }
        var recName by remember { mutableStateOf("") }
        var recContent by remember { mutableStateOf("") }
        var proxied by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.addDnsRecord, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().imePadding()) {
                    OutlinedTextField(value = recType, onValueChange = { recType = it }, label = { Text("نوع رکورد (A, AAAA, CNAME, TXT)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = recName, onValueChange = { recName = it }, label = { Text("نام (@ یا زیردامنه)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = recContent, onValueChange = { recContent = it }, label = { Text("مقدار / آی‌پی") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = proxied, onCheckedChange = { proxied = it })
                        Spacer(Modifier.width(6.dp))
                        Text("پروکسی کلودفلر (ابر نارنجی)", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            selectedZone?.let { z ->
                                CloudflareService.saveRecord(apiToken, z.id, null, recType, recName, recContent, proxied)
                                showAddDialog = false
                                loadRecords(z)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text(t.save, fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(t.cancel, fontSize = 11.5.sp) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. ENCRYPTED VAULT SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun VaultScreen(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var isVaultInit by remember { mutableStateOf(Prefs.isVaultInitialized(ctx)) }
    var masterPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showMasterPass by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var showAddNote by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<VaultNote?>(null) }
    var deleteNote by remember { mutableStateOf<VaultNote?>(null) }
    var revealedNoteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var backupString by remember { mutableStateOf("") }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    fun unlock() {
        if (masterPass.isBlank()) return
        authError = null
        if (Prefs.verifyMasterPassword(ctx, masterPass)) {
            try {
                notes = Prefs.loadVaultNotes(ctx, masterPass)
                isUnlocked = true
                authError = null
            } catch (e: Exception) {
                authError = "خطا در رمزگشایی اطلاعات گاوصندوق"
            }
        } else {
            authError = "رمز عبور اصلی اشتباه است"
        }
    }

    fun setupNewVault() {
        authError = null
        if (masterPass.length < 4) {
            authError = "رمز عبور اصلی باید حداقل ۴ کاراکتر باشد"
            return
        }
        if (masterPass != confirmPass) {
            authError = "رمز عبور و تکرار آن یکسان نیستند"
            return
        }
        Prefs.setupMasterPassword(ctx, masterPass)
        isVaultInit = true
        notes = emptyList()
        isUnlocked = true
        Toast.makeText(ctx, "گاوصندوق امن با موفقیت ایجاد شد!", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBadge(
                        icon = Icons.Rounded.Security,
                        tint = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Text(t.encryptedVault, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                if (isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable {
                            isUnlocked = false
                            masterPass = ""
                            confirmPass = ""
                            revealedNoteIds = emptySet()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                            Text(
                                "قفل کردن",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }
        }

        item {
            FeatureGuideCard(
                title = "گاوصندوق محرمانه رمزنگاری‌شده",
                description = t.guideVault
            )
        }

        if (!isVaultInit) {
            item {
                ModernCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تعیین رمز عبور اصلی گاوصندوق", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(
                            "برای گاوصندوق خود یک رمز عبور اصلی تعیین کنید. تمامی اطلاعات با این رمز روی حافظه گوشی شما رمزنگاری (AES-256) خواهند شد.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = masterPass,
                            onValueChange = { masterPass = it; authError = null },
                            label = { Text("رمز عبور اصلی جدید", fontSize = 11.5.sp) },
                            singleLine = true,
                            visualTransformation = if (showMasterPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showMasterPass = !showMasterPass }) {
                                    Icon(if (showMasterPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it; authError = null },
                            label = { Text("تکرار رمز عبور اصلی", fontSize = 11.5.sp) },
                            singleLine = true,
                            visualTransformation = if (showMasterPass) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (authError != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Text(authError!!, color = Color(0xFFEF4444), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        PrimaryActionButton(
                            text = "فعال‌سازی و ایجاد گاوصندوق",
                            onClick = { setupNewVault() }
                        )
                    }
                }
            }
        } else if (!isUnlocked) {
            item {
                ModernCard(padding = 14.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(
                                icon = Icons.Rounded.Lock,
                                tint = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                size = 30.dp,
                                iconSize = 16.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("گاوصندوق امن قفل است", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                        Text(
                            "برای دسترسی به کلیدهای SSH، رمزها و یادداشت‌های محرمانه، رمز اصلی را وارد کنید.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = masterPass,
                            onValueChange = { masterPass = it; authError = null },
                            label = { Text(t.masterPassword, fontSize = 11.5.sp) },
                            singleLine = true,
                            visualTransformation = if (showMasterPass) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showMasterPass = !showMasterPass }) {
                                    Icon(if (showMasterPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(17.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (authError != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                Text(authError!!, color = Color(0xFFEF4444), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        PrimaryActionButton(
                            text = t.unlockVault,
                            onClick = { unlock() }
                        )
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill("${notes.size} آیتم ذخیره شده", isOnline = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    val b64 = EncryptedVault.exportBackup(Prefs.loadServers(ctx), notes, masterPass)
                                    backupString = b64
                                    showBackupDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                    Text("بکاپ", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { showRestoreDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
                                    Text("بازیابی", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }

                    PrimaryActionButton(
                        text = "＋ ${t.addNote}",
                        onClick = { showAddNote = true }
                    )
                }
            }

            if (notes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(
                                icon = Icons.Rounded.Security,
                                tint = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                size = 48.dp,
                                iconSize = 24.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("هنوز کلید یا یادداشتی ذخیره نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(notes, key = { it.id }) { n ->
                    val isRevealed = revealedNoteIds.contains(n.id)

                    val tagColor = when (n.tags) {
                        "کلید SSH" -> Color(0xFF0D9488)
                        "پسورد" -> Color(0xFF6366F1)
                        "توکن API" -> Color(0xFFF59E0B)
                        else -> Color(0xFF10B981)
                    }

                    ModernCard(padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = tagColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    if (n.tags.isNotBlank()) n.tags else "محرمانه",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tagColor
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(n.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))

                            TextButton(onClick = {
                                revealedNoteIds = if (isRevealed) revealedNoteIds - n.id else revealedNoteIds + n.id
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text(if (isRevealed) "مخفی" else "نمایش", fontSize = 10.5.sp)
                                }
                            }

                            TextButton(onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("secret", n.content))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Text("کپی", fontSize = 10.5.sp)
                                }
                            }

                            IconButton(onClick = { editingNote = n }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { deleteNote = n }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isRevealed) n.content else "••••••••••••••••••••••••",
                                modifier = Modifier.padding(7.dp),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isRevealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }

    // ── Add/Edit Note Dialog ──
    if (showAddNote || editingNote != null) {
        val isEdit = editingNote != null
        var title by remember { mutableStateOf(editingNote?.title ?: "") }
        var content by remember { mutableStateOf(editingNote?.content ?: "") }
        var tag by remember { mutableStateOf(editingNote?.tags ?: "کلید SSH") }

        AlertDialog(
            onDismissRequest = { showAddNote = false; editingNote = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isEdit) "ویرایش یادداشت" else t.addNote, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().imePadding()) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان (مثلاً کلید SSH سرور)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("کلید SSH", "پسورد", "توکن API", "یادداشت").forEach { tg ->
                            val isSel = tag == tg
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSel) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { tag = tg }
                            ) {
                                Text(
                                    tg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontSize = 9.5.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("محتوا / پسورد / کلید محرمانه") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            if (isEdit) {
                                editingNote?.title = title.trim()
                                editingNote?.content = content.trim()
                                editingNote?.tags = tag
                                notes = notes.toList()
                            } else {
                                notes = notes + VaultNote(System.currentTimeMillis(), title.trim(), content.trim(), tag)
                            }
                            Prefs.saveVaultNotes(ctx, notes, masterPass)
                            showAddNote = false
                            editingNote = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text(t.save, fontWeight = FontWeight.Bold, fontSize = 11.5.sp) }
            },
            dismissButton = {
                TextButton(onClick = { showAddNote = false; editingNote = null }) { Text(t.cancel, fontSize = 11.5.sp) }
            }
        )
    }

    // ── Delete Confirmation Dialog ──
    deleteNote?.let { dn ->
        AlertDialog(
            onDismissRequest = { deleteNote = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("حذف یادداشت محرمانه", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف «${dn.title}» اطمینان دارید؟") },
            confirmButton = {
                TextButton(onClick = {
                    notes = notes.filter { it.id != dn.id }
                    Prefs.saveVaultNotes(ctx, notes, masterPass)
                    deleteNote = null
                }) {
                    Text("حذف", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteNote = null }) { Text(t.cancel) }
            }
        )
    }

    // ── Backup Export Dialog ──
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("پشتیبان‌گیری از اطلاعات", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.imePadding()) {
                    Text(t.backupCopyHint, fontSize = 11.5.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = backupString,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("backup", backupString))
                        Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                        showBackupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text(t.copy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text(t.close) } }
        )
    }

    // ── Backup Restore Dialog ──
    if (showRestoreDialog) {
        var restorePayload by remember { mutableStateOf("") }
        var restorePass by remember { mutableStateOf(masterPass) }
        var restoreErr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("بازیابی اطلاعات از بکاپ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.imePadding()) {
                    Text("رشته پشتیبان رمزنگاری‌شده را در کادر زیر وارد کنید:", fontSize = 11.5.sp)
                    OutlinedTextField(
                        value = restorePayload,
                        onValueChange = { restorePayload = it; restoreErr = null },
                        label = { Text("رشته بکاپ (Base64)") },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                    OutlinedTextField(
                        value = restorePass,
                        onValueChange = { restorePass = it; restoreErr = null },
                        label = { Text("رمز عبور زمان ایجاد بکاپ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restoreErr != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Text(restoreErr!!, color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val (importedServers, importedNotes) = EncryptedVault.importBackup(restorePayload.trim(), restorePass)
                            if (importedServers.isNotEmpty()) {
                                Prefs.saveServers(ctx, importedServers)
                            }
                            if (importedNotes.isNotEmpty()) {
                                notes = importedNotes
                                Prefs.saveVaultNotes(ctx, notes, masterPass)
                            }
                            Toast.makeText(ctx, "بازیابی با موفقیت انجام شد!", Toast.LENGTH_LONG).show()
                            showRestoreDialog = false
                        } catch (e: Exception) {
                            restoreErr = "خطا: رمز عبور اشتباه است یا رشته بکاپ معتبر نیست"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text("بازیابی", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text(t.cancel) } }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. DEVELOPER LAB (STRING LAB & UTILITIES) SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DevLabScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    val tabs = listOf(
        Icons.Rounded.DataObject to "JSON و Base64",
        Icons.Rounded.Lan to "ساب‌نت شبکه (CIDR)",
        Icons.Rounded.Badge to "رمزگشای توکن (JWT)",
        Icons.Rounded.Key to "تولید پسورد و UUID"
    )

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(
                icon = Icons.Rounded.Terminal,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                size = 36.dp,
                iconSize = 18.dp
            )
            Text(t.devLab, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))

        // ── Smooth Horizontally Scrollable Tab Bar (Vector Icons) ──
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, (icon, title) ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            title,
                            fontSize = 11.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> JsonBase64Tab(t)
                1 -> SubnetCalcTab(t)
                2 -> JwtDecoderTab(t)
                3 -> GeneratorTab(t)
            }
        }
    }
}

// ── Tab 0: JSON Formatter, Minifier & Base64 Encoder/Decoder ─────────────────

@Composable
private fun JsonBase64Tab(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "فرمت JSON و کدگذاری Base64",
                description = "کدهای فشرده JSON را خوانا می‌کند، متن‌ها را با Base64 کدگذاری/رمزگشایی می‌کند و هش SHA-256 می‌سازد."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("متن ورودی / JSON / Base64", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Button(
                            onClick = { output = DevLabTools.formatJson(input) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text("مرتب‌سازی JSON", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { output = DevLabTools.minifyJson(input) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.Compress, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                                Text("فشرده‌سازی", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { output = DevLabTools.base64Encode(input) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                                Text("Base64 Encode", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { output = DevLabTools.base64Decode(input) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                                Text("Base64 Decode", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                        }

                        Button(
                            onClick = { output = DevLabTools.hash(input, "SHA-256") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                                Text("SHA-256", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نتیجه خروجی:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        if (output.isNotBlank()) {
                            TextButton(onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("output", output))
                                Toast.makeText(ctx, "خروجی کپی شد!", Toast.LENGTH_SHORT).show()
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Text("کپی نتیجه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = output,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(130.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Tab 1: Subnet / CIDR Calculator ──────────────────────────────────────────

@Composable
private fun SubnetCalcTab(t: Str) {
    var cidr by remember { mutableStateOf("192.168.1.0/24") }
    var info by remember { mutableStateOf<DevLabTools.SubnetInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun calc(targetCidr: String = cidr) {
        err = null
        try {
            info = DevLabTools.calculateSubnet(targetCidr)
        } catch (e: Exception) {
            err = e.message
            info = null
        }
    }

    LaunchedEffect(Unit) { calc() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "محاسبه‌گر ساب‌نت شبکه (CIDR)",
                description = "فرمت CIDR را تحلیل کرده و محدوده آی‌پی‌های قابل استفاده، ساب‌نت ماسک و ظرفیت هاست‌ها را محاسبه می‌کند."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cidr,
                        onValueChange = { cidr = it },
                        label = { Text("آی‌پی و پیشوند (مثلاً 192.168.1.0/24)", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    PrimaryActionButton(
                        text = "محاسبه مشخصات ساب‌نت",
                        onClick = { calc() }
                    )

                    Text("نمونه‌های متداول شبکه:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            "192.168.1.0/24" to "LAN (254)",
                            "10.0.0.0/16" to "Large (65K)",
                            "172.16.0.0/20" to "Corp (4K)",
                            "192.168.1.0/30" to "Tunnel (2)"
                        ).forEach { (preset, desc) ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clickable {
                                    cidr = preset
                                    calc(preset)
                                }
                            ) {
                                Text(
                                    "$preset ($desc)",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        if (err != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    Text(err!!, color = Color(0xFFEF4444), fontSize = 11.5.sp)
                }
            }
        }

        info?.let { s ->
            item {
                ModernCard(padding = 12.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("آدرس شبکه:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.network, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("آدرس برادکست:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.broadcast, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("بازه هاست‌ها:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${s.firstHost}  ➔  ${s.lastHost}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تعداد هاست‌ها:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${s.usableHosts} هاست", fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ساب‌نت ماسک:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.netmask, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Tab 2: JWT Token Decoder ────────────────────────────────────────────────

@Composable
private fun JwtDecoderTab(t: Str) {
    var token by remember { mutableStateOf("") }
    var jwtInfo by remember { mutableStateOf<DevLabTools.JwtInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun decode() {
        err = null
        try {
            jwtInfo = DevLabTools.decodeJwt(token)
        } catch (e: Exception) {
            err = e.message
            jwtInfo = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "رمزگشای توکن لاگین (JWT Decoder)",
                description = "توکن‌های احراز هویت JWT را رمزگشایی کرده و مشخصات کاربر و تاریخ انقضا را نمایش می‌دهد."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("توکن JWT را جای‌گذاری کنید (eyJhbGciOi...)", fontSize = 11.5.sp) },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )

                    PrimaryActionButton(
                        text = "رمزگشایی توکن",
                        onClick = { decode() }
                    )
                }
            }
        }

        if (err != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                    Text(err!!, color = Color(0xFFEF4444), fontSize = 11.5.sp)
                }
            }
        }

        jwtInfo?.let { j ->
            item {
                StatusPill(
                    text = if (j.isExpired) "توکن منقضی شده (${j.expiryDate})" else "معتبر (انقضا: ${j.expiryDate ?: "نامحدود"})",
                    isOnline = !j.isExpired
                )
            }
            item {
                Text("اطلاعات بدنه (Payload):", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                ModernCard(padding = 9.dp) {
                    Text(j.payload, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                }
            }
            item {
                Text("هدر توکن (Header):", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                ModernCard(padding = 9.dp) {
                    Text(j.header, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Tab 3: Password & UUID Generator ────────────────────────────────────────

@Composable
private fun GeneratorTab(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var passLength by remember { mutableStateOf(18) }
    var generatedPass by remember { mutableStateOf(DevLabTools.generatePassword(passLength)) }
    var generatedUuid by remember { mutableStateOf(DevLabTools.generateUuid()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "تولیدکننده پسورد و شناسه UUID",
                description = "تولید پسوردهای تصادفی ضد هک و شناسه‌های یکتای جهانی (UUID v4)."
            )
        }

        item {
            ModernCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("تولید پسورد قوی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    generatedPass,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("طول پسورد:", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(12, 16, 20, 24, 32).forEach { len ->
                        val isSel = passLength == len
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable {
                                passLength = len
                                generatedPass = DevLabTools.generatePassword(len)
                            }
                        ) {
                            Text(
                                "$len",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { generatedPass = DevLabTools.generatePassword(passLength) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) { Text("تولید مجدد", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                    OutlinedButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("password", generatedPass))
                        Toast.makeText(ctx, "پسورد کپی شد!", Toast.LENGTH_SHORT).show()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text("کپی", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            ModernCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Rounded.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("تولید شناسه یکتای UUID v4", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    generatedUuid,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { generatedUuid = DevLabTools.generateUuid() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                    ) { Text("تولید UUID جدید", fontWeight = FontWeight.Bold, fontSize = 11.sp) }

                    OutlinedButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("uuid", generatedUuid))
                        Toast.makeText(ctx, "UUID کپی شد!", Toast.LENGTH_SHORT).show()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text("کپی", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}
