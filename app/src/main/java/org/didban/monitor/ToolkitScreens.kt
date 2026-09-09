@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
// 1. NETWORK HUB SCREEN (Port Scanner · DPI Censorship · SSL · IP · Ping)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NetworkHubScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 5 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛰️ ${t.networkHub}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("پورت اسکنر", "🛡️ تست فیلترینگ", "بازرس SSL", "اطلاعات IP", "پینگ TCP").forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PortScannerTab(t)
                1 -> CensorshipTab(t)
                2 -> SslInspectorTab(t)
                3 -> IpInfoTab(t)
                4 -> TcpPingTab(t)
            }
        }
    }
}

@Composable
private fun CensorshipTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isTesting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CensorshipDiagnosticResult?>(null) }

    fun runTest() {
        if (host.isBlank() || isTesting) return
        isTesting = true
        result = null
        scope.launch {
            try {
                result = CensorshipTester.diagnose(host.trim(), port.toIntOrNull() ?: 443)
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
                Button(onClick = { runTest() }, enabled = !isTesting && host.isNotBlank()) {
                    if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text("عیب‌یابی")
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

@Composable
private fun PortScannerTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var scannedCount by remember { mutableStateOf(0) }

    val portsToScan = remember { COMMON_PORTS.map { it.first } }

    fun runScan() {
        if (host.isBlank() || isScanning) return
        isScanning = true
        results = emptyList()
        scannedCount = 0

        scope.launch {
            try {
                PortScanner.scanPorts(host.trim(), portsToScan, concurrency = 10) { res ->
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
                Button(onClick = { runScan() }, enabled = !isScanning && host.isNotBlank()) {
                    if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.scan)
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

@Composable
private fun SslInspectorTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isLoading by remember { mutableStateOf(false) }
    var certInfo by remember { mutableStateOf<SslCertInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun checkSsl() {
        if (host.isBlank() || isLoading) return
        isLoading = true
        err = null
        certInfo = null
        scope.launch {
            try {
                certInfo = SslInspector.inspect(host.trim(), port.toIntOrNull() ?: 443)
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
                    label = { Text("دامنه (مثلا google.com)", fontSize = 12.sp) },
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
                Button(onClick = { checkSsl() }, enabled = !isLoading && host.isNotBlank()) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.inspect)
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

@Composable
private fun IpInfoTab(t: Str) {
    val scope = rememberCoroutineScope()
    var targetIp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var geoData by remember { mutableStateOf<GeoIpData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun lookup() {
        isLoading = true
        err = null
        scope.launch {
            try {
                geoData = IpInfoService.lookup(targetIp)
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
            title = "🌍 راهنمای استعلام اطلاعات آی‌پی (GeoIP)",
            description = "مشاهده کشور، شهر، شرکت ارائه‌دهنده اینترنت (ISP)، شماره ASN و مختصات جغرافیایی هر آی‌پی یا دامنه."
        )

        Spacer(Modifier.height(10.dp))

        ModernCard(padding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("آی‌پی (خالی = آی‌پی من)", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { lookup() }, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    else Text(t.lookup)
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
                Text("مختصات: ${g.lat}, ${g.lon}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TcpPingTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var isPinging by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun startPing() {
        if (host.isBlank()) return
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
                    val lat = TcpPinger.ping(host.trim(), p, 1500)
                    logs = (listOf("seq=$seq host=$host:$p time=${lat}ms") + logs).take(50)
                } catch (e: Exception) {
                    logs = (listOf("seq=$seq host=$host timeout (${e.message})") + logs).take(50)
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
                        containerColor = if (isPinging) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isPinging) t.stop else t.start)
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
                Button(onClick = { showAddDialog = true }) { Text("+ ${t.addRecord}") }
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
                Button(onClick = {
                    scope.launch {
                        selectedZone?.let { z ->
                            CloudflareService.saveRecord(apiToken, z.id, null, recType, recName, recContent, proxied)
                            showAddDialog = false
                            loadRecords(z)
                        }
                    }
                }) { Text(t.save) }
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

    var masterPass by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var showAddNote by remember { mutableStateOf(false) }
    var backupString by remember { mutableStateOf("") }
    var showBackupDialog by remember { mutableStateOf(false) }

    fun unlock() {
        if (masterPass.isBlank()) return
        try {
            notes = Prefs.loadVaultNotes(ctx, masterPass)
            isUnlocked = true
        } catch (e: Exception) {
            Toast.makeText(ctx, "رمز عبور نادرست است یا گاوصندوق مخدوش شده", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔐 ${t.encryptedVault}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureGuideCard(
            title = "🔐 راهنمای گاوصندوق امن محرمانه",
            description = t.guideVault
        )

        Spacer(Modifier.height(10.dp))

        if (!isUnlocked) {
            ModernCard(padding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t.vaultHint, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = masterPass,
                        onValueChange = { masterPass = it },
                        label = { Text(t.masterPassword, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    PrimaryActionButton(
                        text = t.unlockVault,
                        onClick = { unlock() }
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${notes.size} ${t.notes}", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val b64 = EncryptedVault.exportBackup(Prefs.loadServers(ctx), notes, masterPass)
                    backupString = b64
                    showBackupDialog = true
                }) { Text("📦 ${t.backup}", fontSize = 12.sp) }
                Button(onClick = { showAddNote = true }) { Text("+ ${t.addNote}") }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { n ->
                    ModernCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("secret", n.content))
                                Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                            }) { Text("📋 ${t.copy}", fontSize = 11.sp) }
                            TextButton(onClick = {
                                notes = notes.filter { it.id != n.id }
                                Prefs.saveVaultNotes(ctx, notes, masterPass)
                            }) { Text("🗑️", fontSize = 12.sp) }
                        }
                        Text(n.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAddNote) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddNote = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.addNote, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان (مثلا کلید SSH سرور آلمان)") })
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("محتوا / پسورد محرمانه") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        notes = notes + VaultNote(System.currentTimeMillis(), title, content)
                        Prefs.saveVaultNotes(ctx, notes, masterPass)
                        showAddNote = false
                    }
                }) { Text(t.save) }
            },
            dismissButton = { TextButton(onClick = { showAddNote = false }) { Text(t.cancel) } }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("📦 ${t.backup}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t.backupCopyHint, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = backupString, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("backup", backupString))
                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                    showBackupDialog = false
                }) { Text(t.copy) }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text(t.close) } }
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
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("JSON & Base64", "ساب‌نت CIDR", "دیکودر JWT", "تولیدکننده پسورد").forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = if (selected) 2.dp else 0.dp,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
            Button(onClick = { calc() }) { Text(t.calculate) }
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
        Button(onClick = { decode() }, modifier = Modifier.fillMaxWidth()) { Text("دیکود و بررسی توکن") }

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
                Button(onClick = { generatedPass = DevLabTools.generatePassword(18) }) { Text("تولید جدید") }
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
                Button(onClick = { generatedUuid = DevLabTools.generateUuid() }) { Text("تولید UUID") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("uuid", generatedUuid))
                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                }) { Text("کپی") }
            }
        }
    }
}
