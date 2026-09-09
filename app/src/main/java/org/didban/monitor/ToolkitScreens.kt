@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
        "🌐 چک‌هاست جهانی",
        "🛡️ تست فیلترینگ و DPI",
        "🔍 پورت اسکنر",
        "🔒 بازرس SSL",
        "🌍 اطلاعات IP و دامنه",
        "📶 پینگ مداوم TCP"
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛰️ ${t.networkHub}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))

        // ── Smooth Horizontally Scrollable Tab Bar (No squashing or wrapping) ──
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
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
                statusText = "$currentOk از $totalCount نود جهانی در دسترس هستند ✅"
            } catch (e: Exception) {
                statusText = "خطا در تست: ${e.message}"
            } finally {
                isChecking = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "🌐 راهنمای چک‌هاست جهانی (Check-Host.net)",
            description = "تست وضعیت اتصال، پینگ، پورت باز و سلامت سایت از ۲۰ نود در کشورهای مختلف (آلمان، آمریکا، ایران، فرانسه، انگلستان، هلند و...):",
            bullets = listOf(
                "PING: تست پکت‌لاس و میانگین زمان پاسخ به میلی‌ثانیه از نقاط مختلف جهان",
                "HTTP: بررسی بالا بودن وبسایت و کد پاسخ سرور (200 OK, 403, 301)",
                "TCP: تست باز بودن پورت‌های SSH، دیتابیس یا پنل از خارج کشور",
                "DNS: بررسی رزولوشن و انتشار رکوردهای دامنه در سرورهای نام جهان"
            )
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            OutlinedTextField(
                value = targetHost,
                onValueChange = { targetHost = it },
                label = { Text("دامنه یا آی‌پی (مثلاً google.com یا 1.2.3.4)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ping", "http", "tcp", "dns").forEach { type ->
                    val selected = selectedType == type
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable { selectedType = type }
                    ) {
                        Text(
                            type.uppercase(Locale.US),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                        label = { Text("Port", fontSize = 10.sp) },
                        modifier = Modifier.width(70.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { startProbe() },
                    enabled = !isChecking && targetHost.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(t.runProbe, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (statusText.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (totalCount > 0) {
                    Spacer(Modifier.weight(1f))
                    StatusPill("$okCount / $totalCount", isOnline = okCount > 0)
                }
            }
        }

        if (nodes.isEmpty() && !isChecking) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌐", fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("آدرس دامنه یا آی‌پی را وارد کرده و دکمه شروع تست را بزنید", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(nodes, key = { it.nodeKey }) { node ->
                ModernCard(padding = 10.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(node.flag, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.location.ifEmpty { node.countryCode }, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(node.nodeKey, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (node.state) {
                                1 -> Color(0xFF10B981).copy(alpha = 0.15f)
                                2 -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Text(
                                node.resultText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.5.sp,
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

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "🛡️ تست فیلترینگ و اختلال شبکه (DPI / TLS)",
            description = "این ابزار هوشمند نحوه مسدودسازی یا اختلال ارتباط با سرور را در ۳ مرحله بررسی می‌کند:",
            bullets = listOf(
                "بررسی دسترسی لایه ۳/۴ (آیا IP سرور بلک‌هول یا فیلتر شده است؟)",
                "تست تزریق پکت‌های جعلی TCP RST توسط سامانه‌های فیلترینگ هوشمند (DPI)",
                "تست هندشیک امن TLS/SNI و کشف دستکاری در ارتباطات رمزنگاری‌شده"
            )
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("دامنه یا آی‌پی سرور", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("پورت", fontSize = 10.sp) },
                    modifier = Modifier.width(65.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { runTest() },
                    enabled = !isTesting && host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text("عیب‌یابی", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        result?.let { r ->
            ModernCard(
                padding = 14.dp,
                containerColor = if (r.isFiltered) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
                borderColor = if (r.isFiltered) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        r.diagnosis,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (r.isFiltered) Color(0xFFDC2626) else Color(0xFF047857)
                    )
                    Text("مقصد: ${r.host}:${r.port} (تاخیر: ${r.latencyMs} میلی‌ثانیه)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("۱. لایه اتصال اولیه TCP (SYN/ACK):", fontSize = 12.sp)
                        Text(if (r.tcpReachable) "✅ متصل شد" else "❌ ناموفق / فیلتر", fontWeight = FontWeight.Bold, color = if (r.tcpReachable) Color(0xFF10B981) else Color(0xFFEF4444))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("۲. لایه هندشیک امن TLS / SNI:", fontSize = 12.sp)
                        Text(if (r.tlsReachable) "✅ هندشیک تمیز" else "❌ قطع توسط فیلترینگ", fontWeight = FontWeight.Bold, color = if (r.tlsReachable) Color(0xFF10B981) else Color(0xFFEF4444))
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("گزارش فنی:\n${r.details}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "🔍 راهنمای پورت اسکنر",
            description = "این ابزار پورت‌های کلیدی سرور (SSH، وب HTTP/HTTPS، دیتابیس‌ها و پنل‌ها) را تست می‌کند تا از باز بودن پورت‌ها و در دسترس بودن سرویس‌ها اطمینان حاصل کنید."
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("دامنه یا آی‌پی سرور", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { runScan() },
                    enabled = !isScanning && host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.scan, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (isScanning) {
            Spacer(Modifier.height(8.dp))
            Text("در حال اسکن $scannedCount از ${portsToScan.size} پورت…", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        if (results.isEmpty() && !isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t.enterHostToScan, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.port }) { r ->
                ModernCard(padding = 10.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF10B981).copy(alpha = 0.15f)) {
                            Text("PORT ${r.port}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.service, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${r.latencyMs} ms", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("OPEN", fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
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

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "🔒 راهنمای بازرس سرتیفیکیت SSL",
            description = "بررسی روزهای باقی‌مانده تا انقضای گواهی HTTPS، نام صادرکننده (CA)، الگوریتم و اثر انگشت سرتیفیکیت جهت جلوگیری از قطعی سایت‌ها بخاطر اکسپایر شدن SSL."
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("دامنه (مثلاً google.com)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("پورت", fontSize = 10.sp) },
                    modifier = Modifier.width(65.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { checkSsl() },
                    enabled = !isLoading && host.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.inspect, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ خطا: $err", color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        certInfo?.let { c ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    ModernCard(
                        padding = 14.dp,
                        containerColor = if (c.isExpired || c.daysRemaining < 7) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
                        borderColor = if (c.isExpired || c.daysRemaining < 7) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f)
                    ) {
                        Text(
                            if (c.isExpired) "❌ گواهی منقضی شده است!" else "✅ معتبر (${c.daysRemaining} روز باقی‌مانده)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (c.isExpired) Color(0xFFDC2626) else Color(0xFF047857)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("اعتبار از ${c.validFrom} تا ${c.validTo}", fontSize = 11.5.sp)
                    }
                }

                item {
                    ModernCard(padding = 12.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("دامنه (Subject): ${c.subject}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("صادرکننده (Issuer): ${c.issuer}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("الگوریتم امضا: ${c.sigAlg}", fontSize = 11.sp)
                            Text("اثر انگشت SHA-256:\n${c.fingerprintSha256}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            if (c.sans.isNotEmpty()) {
                                Text("دامنه های تحت پوشش (SANs):\n${c.sans.take(6).joinToString(", ")}", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
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

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "🌍 راهنمای استعلام اطلاعات آی‌پی و دامنه (GeoIP)",
            description = "مشاهده کشور، شهر، شرکت ارائه‌دهنده اینترنت (ISP)، شماره ASN و مختصات جغرافیایی هر آی‌پی یا دامنه اینترنتی."
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("آی‌پی یا دامنه (خالی = آی‌پی من)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { lookup() },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.lookup, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        geoData?.let { g ->
            ModernCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.flag, fontSize = 32.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(g.ip, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${g.city}, ${g.country}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("ارائه‌دهنده / سازمان: ${g.isp} (${g.org})", fontSize = 12.sp)
                Text("شماره ASN: ${g.asn}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("منطقه زمانی: ${g.timezone}", fontSize = 12.sp)
                Text("مختصات جغرافیایی: ${g.lat}, ${g.lon}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

    Column(Modifier.fillMaxSize()) {
        FeatureGuideCard(
            title = "📶 راهنمای پینگ مداوم TCP",
            description = "اندازه‌گیری پایداری شبکه و زمان پاسخ (Latency) لحظه‌ای سرور در بازه‌های ۱ ثانیه‌ای برای کشف پکت‌لاس."
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("آدرس سرور / IP", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("پورت", fontSize = 10.sp) },
                    modifier = Modifier.width(65.dp),
                    singleLine = true
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { startPing() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPinging) Color(0xFFEF4444) else Color(0xFF0D9488)
                    )
                ) {
                    Text(if (isPinging) t.stop else t.start, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs) { line ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        line,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.contains("time=")) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }
        }
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("☁️ ${t.cloudflareDns}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureGuideCard(
            title = "☁️ راهنمای مدیریت DNS کلودفلر",
            description = t.guideCloudflare
        )

        Spacer(Modifier.height(10.dp))

        if (!isTokenSaved) {
            ModernCard(padding = 14.dp) {
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = { Text("Cloudflare API Token", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                PrimaryActionButton(
                    text = t.saveToken,
                    onClick = {
                        Prefs.setCfToken(ctx, apiToken)
                        isTokenSaved = true
                        loadZones()
                    }
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedZone?.name ?: "دامنه‌ها (${zones.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { isTokenSaved = false }) { Text(t.changeToken, fontSize = 11.sp) }
                TextButton(onClick = { selectedZone?.let { loadRecords(it) } }) { Text("🔄", fontSize = 14.sp) }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text("+ ${t.addRecord}") }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (err != null) {
            Text("❌ $err", color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(records, key = { it.id }) { rec ->
                ModernCard(padding = 12.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                rec.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rec.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(rec.content, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (rec.proxiable) {
                            Text(if (rec.proxied) "☁️ پروکسی روشن" else "☁️ فقط DNS", fontSize = 10.sp, color = if (rec.proxied) Color(0xFFF59E0B) else Color(0xFF94A3B8))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            "🗑️",
                            modifier = Modifier.clickable {
                                scope.launch {
                                    selectedZone?.let { z ->
                                        CloudflareService.deleteRecord(apiToken, z.id, rec.id)
                                        loadRecords(z)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var recType by remember { mutableStateOf("A") }
        var recName by remember { mutableStateOf("") }
        var recContent by remember { mutableStateOf("") }
        var proxied by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.addDnsRecord, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = recType, onValueChange = { recType = it }, label = { Text("نوع رکورد (A, AAAA, CNAME, TXT)") })
                    OutlinedTextField(value = recName, onValueChange = { recName = it }, label = { Text("نام (@ یا زیردامنه)") })
                    OutlinedTextField(value = recContent, onValueChange = { recContent = it }, label = { Text("مقدار / آی‌پی") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = proxied, onCheckedChange = { proxied = it })
                        Spacer(Modifier.width(8.dp))
                        Text("پروکسی کلودفلر (ابر نارنجی)", fontSize = 12.sp)
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
                ) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(t.cancel) }
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
    var isUnlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var showPasswordText by remember { mutableStateOf(false) }

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
            authError = "❌ رمز عبور اصلی اشتباه است"
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
        Toast.makeText(ctx, "گاوصندوق امن با موفقیت ایجاد شد! 🔐", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Top Header ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔐 ${t.encryptedVault}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (isUnlocked) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable {
                        isUnlocked = false
                        masterPass = ""
                        confirmPass = ""
                        revealedNoteIds = emptySet()
                    }
                ) {
                    Text(
                        "🔒 قفل کردن",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }

        FeatureGuideCard(
            title = "🔐 راهنمای گاوصندوق امن محرمانه",
            description = t.guideVault
        )

        Spacer(Modifier.height(10.dp))

        // ── Case 1: First-time setup (Vault Not Initialized Yet) ──
        if (!isVaultInit) {
            ModernCard(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تعیین رمز عبور اصلی گاوصندوق", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "برای گاوصندوق خود یک رمز عبور اصلی تعیین کنید. تمامی اطلاعات با این رمز روی حافظه گوشی شما رمزنگاری (AES-256) خواهند شد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    OutlinedTextField(
                        value = masterPass,
                        onValueChange = { masterPass = it; authError = null },
                        label = { Text("رمز عبور اصلی جدید", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it; authError = null },
                        label = { Text("تکرار رمز عبور اصلی", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (authError != null) {
                        Text(authError!!, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    PrimaryActionButton(
                        text = "🔐 فعال‌سازی و گشایش گاوصندوق",
                        onClick = { setupNewVault() }
                    )
                }
            }
        }
        // ── Case 2: Vault is Initialized but Locked ──
        else if (!isUnlocked) {
            ModernCard(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("گاوصندوق امن قفل است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text(
                        "برای دسترسی به کلیدهای SSH، رمزها و یادداشت‌های محرمانه، رمز اصلی را وارد کنید.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = masterPass,
                        onValueChange = { masterPass = it; authError = null },
                        label = { Text(t.masterPassword, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (authError != null) {
                        Text(authError!!, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    PrimaryActionButton(
                        text = "🔓 ${t.unlockVault}",
                        onClick = { unlock() }
                    )
                }
            }
        }
        // ── Case 3: Vault is Unlocked (Full Access) ──
        else {
            // Action & Backup Bar (Clean horizontal arrangement without vertical squeezing!)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill("• ${notes.size} یادداشت و کلید ذخیره شده", isOnline = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable {
                                val b64 = EncryptedVault.exportBackup(Prefs.loadServers(ctx), notes, masterPass)
                                backupString = b64
                                showBackupDialog = true
                            }
                        ) {
                            Text(
                                "📦 پشتیبان‌گیری",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable { showRestoreDialog = true }
                        ) {
                            Text(
                                "📥 بازیابی",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                PrimaryActionButton(
                    text = "＋ ${t.addNote}",
                    onClick = { showAddNote = true }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔐", fontSize = 42.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("هنوز کلید یا یادداشتی ذخیره نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { n ->
                        val isRevealed = revealedNoteIds.contains(n.id)

                        ModernCard(padding = 12.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        if (n.tags.isNotBlank()) n.tags else "🔑 محرمانه",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))

                                TextButton(onClick = {
                                    revealedNoteIds = if (isRevealed) revealedNoteIds - n.id else revealedNoteIds + n.id
                                }) {
                                    Text(if (isRevealed) "🙈 مخفی" else "👁️ نمایش", fontSize = 11.sp)
                                }

                                TextButton(onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("secret", n.content))
                                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                                }) { Text("📋 کپی", fontSize = 11.sp) }

                                TextButton(onClick = { editingNote = n }) { Text("✏️", fontSize = 13.sp) }
                                TextButton(onClick = { deleteNote = n }) { Text("🗑️", fontSize = 13.sp) }
                            }

                            Spacer(Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isRevealed) n.content else "••••••••••••••••••••••••",
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isRevealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }

    // ── Add/Edit Note Dialog ──
    if (showAddNote || editingNote != null) {
        val isEdit = editingNote != null
        var title by remember { mutableStateOf(editingNote?.title ?: "") }
        var content by remember { mutableStateOf(editingNote?.content ?: "") }
        var tag by remember { mutableStateOf(editingNote?.tags ?: "🔑 کلید SSH") }

        AlertDialog(
            onDismissRequest = { showAddNote = false; editingNote = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isEdit) "ویرایش یادداشت محرمانه" else t.addNote, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان (مثلاً کلید SSH سرور آلمان)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("🔑 کلید SSH", "🔐 پسورد", "🎫 توکن API", "📝 یادداشت").forEach { tg ->
                            val isSel = tag == tg
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { tag = tg }
                            ) {
                                Text(
                                    tg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
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
                ) { Text(t.save, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddNote = false; editingNote = null }) { Text(t.cancel) }
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
            title = { Text("📦 ${t.backup}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t.backupCopyHint, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupString,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(140.dp)
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
            title = { Text("📥 بازیابی اطلاعات از بکاپ", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("رشته پشتیبان رمزنگاری‌شده را در کادر زیر وارد کنید:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = restorePayload,
                        onValueChange = { restorePayload = it; restoreErr = null },
                        label = { Text("رشته بکاپ (Base64)") },
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                    OutlinedTextField(
                        value = restorePass,
                        onValueChange = { restorePass = it; restoreErr = null },
                        label = { Text("رمز عبور زمان ایجاد بکاپ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restoreErr != null) {
                        Text(restoreErr!!, color = Color(0xFFEF4444), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                            Toast.makeText(ctx, "بازیابی با موفقیت انجام شد! (${importedServers.size} سرور و ${importedNotes.size} یادداشت)", Toast.LENGTH_LONG).show()
                            showRestoreDialog = false
                        } catch (e: Exception) {
                            restoreErr = "❌ خطا: رمز عبور اشتباه است یا رشته بکاپ معتبر نیست"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text("بازیابی اطلاعات", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text(t.cancel) } }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. DEVELOPER LAB (STRING LAB) SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DevLabScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛠️ ${t.devLab}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureGuideCard(
            title = "🛠️ راهنمای جعبه‌ابزار توسعه‌دهنده (Dev Lab)",
            description = t.guideDevLab
        )

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("JSON & Base64", "ساب‌نت CIDR", "دیکودر JWT", "تولیدکننده پسورد").forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
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

@Composable
private fun JsonBase64Tab(t: Str) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("متن ورودی / JSON / Base64", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { output = DevLabTools.formatJson(input) }, modifier = Modifier.weight(1f)) { Text("فرمت JSON", fontSize = 10.5.sp) }
            Button(onClick = { output = DevLabTools.base64Encode(input) }, modifier = Modifier.weight(1f)) { Text("B64 Enc", fontSize = 10.5.sp) }
            Button(onClick = { output = DevLabTools.base64Decode(input) }, modifier = Modifier.weight(1f)) { Text("B64 Dec", fontSize = 10.5.sp) }
            Button(onClick = { output = DevLabTools.hash(input, "SHA-256") }, modifier = Modifier.weight(1f)) { Text("SHA-256", fontSize = 10.5.sp) }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = output,
            onValueChange = {},
            readOnly = true,
            label = { Text("خروجی", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun SubnetCalcTab(t: Str) {
    var cidr by remember { mutableStateOf("192.168.1.50/24") }
    var info by remember { mutableStateOf<DevLabTools.SubnetInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun calc() {
        err = null
        try {
            info = DevLabTools.calculateSubnet(cidr)
        } catch (e: Exception) {
            err = e.message
            info = null
        }
    }

    LaunchedEffect(Unit) { calc() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cidr,
                onValueChange = { cidr = it },
                label = { Text("فرمت CIDR (مثلاً 10.0.0.1/24)", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { calc() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
            ) { Text(t.calculate) }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        info?.let { s ->
            ModernCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("آدرس شبکه: ${s.network}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("آدرس Broadcast: ${s.broadcast}", fontWeight = FontWeight.Bold)
                    Text("بازه هاست‌های قابل استفاده: ${s.firstHost} — ${s.lastHost}")
                    Text("تعداد هاست‌های مجاز: ${s.usableHosts} (${s.totalHosts} کل)")
                    Text("ساب‌نت ماسک: ${s.netmask}")
                    Text("وایلدکارت ماسک: ${s.wildcard}")
                }
            }
        }
    }
}

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

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("توکن JWT را اینجا جای‌گذاری کنید", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { decode() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
        ) { Text("دیکود و بررسی توکن") }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFEF4444), fontSize = 12.sp)
        }

        jwtInfo?.let { j ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        if (j.isExpired) "❌ توکن منقضی شده است (${j.expiryDate})" else "✅ توکن معتبر است (انقضا: ${j.expiryDate ?: "نامحدود"})",
                        color = if (j.isExpired) Color(0xFFEF4444) else Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Text("محتوای Payload:", fontWeight = FontWeight.Bold)
                    ModernCard(padding = 10.dp) {
                        Text(j.payload, fontFamily = FontFamily.Monospace, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorTab(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var generatedPass by remember { mutableStateOf(DevLabTools.generatePassword(18)) }
    var generatedUuid by remember { mutableStateOf(DevLabTools.generateUuid()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ModernCard(padding = 14.dp) {
            Text("🔑 تولیدکننده رمز عبور قوی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(generatedPass, fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { generatedPass = DevLabTools.generatePassword(18) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text("تولید جدید") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("password", generatedPass))
                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                }) { Text("کپی") }
            }
        }

        ModernCard(padding = 14.dp) {
            Text("🆔 تولیدکننده شناسه UUID v4", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(generatedUuid, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row {
                Button(
                    onClick = { generatedUuid = DevLabTools.generateUuid() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                ) { Text("تولید UUID") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("uuid", generatedUuid))
                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                }) { Text("کپی") }
            }
        }
    }
}
