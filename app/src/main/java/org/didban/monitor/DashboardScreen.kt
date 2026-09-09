@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    t: Str,
    server: ServerConfig,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }
    var tab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 6 }
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()

    // Keep the tab chips and the pager in sync
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { tab = it }
    }

    var metrics by remember { mutableStateOf<Metrics?>(null) }
    var hist by remember { mutableStateOf<List<HistPoint>>(emptyList()) }
    var latency by remember { mutableStateOf(0f) }
    var latHist by remember { mutableStateOf<List<Float>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var procs by remember { mutableStateOf<List<ProcInfo>>(emptyList()) }
    var events by remember { mutableStateOf<List<SpikeEvent>>(emptyList()) }
    var socketsData by remember { mutableStateOf<SocketsData?>(null) }
    var dockerData by remember { mutableStateOf<DockerSummaryData?>(null) }

    // Kill process dialog state
    var procToKill by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var killSignal by remember { mutableStateOf("SIGTERM") }
    var isKilling by remember { mutableStateOf(false) }

    // Test Telegram state
    var isTestingTg by remember { mutableStateOf(false) }

    fun refreshProcs() {
        scope.launch {
            try { procs = api.processes(server) } catch (_: Exception) { }
        }
    }

    fun refreshSockets() {
        scope.launch {
            try { socketsData = api.sockets(server) } catch (_: Exception) { }
        }
    }

    fun refreshDocker() {
        scope.launch {
            try { dockerData = api.dockerContainers(server) } catch (_: Exception) { }
        }
    }

    fun refreshAll() {
        scope.launch {
            try {
                val t0 = System.currentTimeMillis()
                metrics = api.metrics(server)
                latency = (System.currentTimeMillis() - t0).toFloat()
                latHist = (latHist + latency).takeLast(120)
                err = null
            } catch (e: Exception) {
                err = e.message
                latency = -1f
            }
            try { hist = api.history(server) } catch (_: Exception) { }
        }
    }

    // Live refresh loop for overview
    LaunchedEffect(server.id) {
        while (true) {
            refreshAll()
            delay(10_000)
        }
    }

    // Fetch tab data on switch
    LaunchedEffect(server.id, tab) {
        when (tab) {
            1 -> refreshProcs()
            2 -> { try { events = api.events(server, 50) } catch (_: Exception) { } }
            3 -> refreshSockets()
            4 -> refreshDocker()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Top Bar (AutoMirrored Back Button for Persian/English RTL/LTR) ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onBack() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onToggleTheme() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = "Theme",
                        modifier = Modifier.size(18.dp),
                        tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { refreshAll() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(server.name.ifEmpty { server.host }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = MaterialTheme.colorScheme.primary)
                Text("${server.host}:${server.port}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }

        // ── Notice Banner ──
        NoticeBanner(
            text = "اطلاعات منابع و وضعیت سرور هر چند ثانیه به‌صورت زنده از ایجنت پرسرعت Go دریافت می‌شود.",
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // ── Pin certificate banner ──
        if (server.useTls && server.fingerprint.isEmpty()) {
            val fp = api.lastSeenFingerprint
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFEF3C7),
                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.pinCertHint, fontSize = 12.sp, color = Color(0xFF92400E))
                    if (fp != null) {
                        TextButton(onClick = {
                            server.fingerprint = fp
                            val list = Prefs.loadServers(ctx).map { if (it.id == server.id) server else it }
                            Prefs.saveServers(ctx, list)
                        }) {
                            Text(t.pinCert, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        }
                    }
                }
            }
        }

        // ── Tabs (Smooth Horizontally Scrollable Chips with Vector Icons) ──
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScrollState)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TabChip(Icons.Rounded.Dashboard, t.overview, tab == 0) { scope.launch { pagerState.animateScrollToPage(0) } }
            TabChip(Icons.Rounded.Layers, "Docker", tab == 4) { scope.launch { pagerState.animateScrollToPage(4) } }
            TabChip(Icons.Rounded.Timeline, t.events, tab == 2) { scope.launch { pagerState.animateScrollToPage(2) } }
            TabChip(Icons.Rounded.Sensors, t.listeningPorts, tab == 3) { scope.launch { pagerState.animateScrollToPage(3) } }
            TabChip(Icons.Rounded.Memory, t.processes, tab == 1) { scope.launch { pagerState.animateScrollToPage(1) } }
            TabChip(Icons.Rounded.Public, t.globalCheck, tab == 5) { scope.launch { pagerState.animateScrollToPage(5) } }
        }

        // ── Content Pages ──
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ModernOverviewTab(
                    t = t,
                    server = server,
                    m = metrics,
                    hist = hist,
                    err = err,
                    latency = latency,
                    latHist = latHist,
                    onNavigateTab = { targetTab ->
                        scope.launch { pagerState.animateScrollToPage(targetTab) }
                    },
                    onTestAlert = {
                        if (!isTestingTg) {
                            isTestingTg = true
                            scope.launch {
                                try {
                                    api.testTelegram(server)
                                    Toast.makeText(ctx, t.telegramSent, Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "${t.telegramFailed}: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isTestingTg = false
                                }
                            }
                        }
                    }
                )
                1 -> ProcessesTab(
                    t = t,
                    procs = procs,
                    onRefresh = { refreshProcs() },
                    onKill = { pid, name ->
                        procToKill = Pair(pid, name)
                        killSignal = "SIGTERM"
                    }
                )
                2 -> EventsTab(
                    t = t,
                    events = events,
                    onKill = { pid, name ->
                        procToKill = Pair(pid, name)
                        killSignal = "SIGTERM"
                    }
                )
                3 -> SocketsTab(t = t, data = socketsData, onRefresh = { refreshSockets() })
                4 -> DockerTab(server = server, data = dockerData, onRefresh = { refreshDocker() })
                5 -> GlobalCheckTab(t = t, defaultHost = server.host)
            }
        }
    }

    // ── Kill Process Confirmation Dialog ──
    procToKill?.let { (pid, name) ->
        AlertDialog(
            onDismissRequest = { if (!isKilling) procToKill = null },
            title = { Text("${t.killProcessTitle}: $name (PID $pid)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(t.killConfirm, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { killSignal = "SIGTERM" }
                    ) {
                        RadioButton(selected = killSignal == "SIGTERM", onClick = { killSignal = "SIGTERM" })
                        Spacer(Modifier.width(8.dp))
                        Text(t.sigtermDesc, fontSize = 12.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { killSignal = "SIGKILL" }
                    ) {
                        RadioButton(selected = killSignal == "SIGKILL", onClick = { killSignal = "SIGKILL" })
                        Spacer(Modifier.width(8.dp))
                        Text(t.sigkillDesc, fontSize = 12.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isKilling = true
                        scope.launch {
                            try {
                                val res = api.killProcess(server, pid, killSignal)
                                Toast.makeText(ctx, res.message.ifEmpty { t.killSuccess }, Toast.LENGTH_SHORT).show()
                                procToKill = null
                                refreshProcs()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "${t.killError}: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isKilling = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (killSignal == "SIGKILL") Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isKilling
                ) {
                    if (isKilling) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(t.kill, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { procToKill = null }, enabled = !isKilling) {
                    Text(t.cancel)
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// MODERN OVERVIEW TAB (Matching reference UI with Vector Icons)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ModernOverviewTab(
    t: Str,
    server: ServerConfig,
    m: Metrics?,
    hist: List<HistPoint>,
    err: String?,
    latency: Float,
    latHist: List<Float>,
    onNavigateTab: (Int) -> Unit,
    onTestAlert: () -> Unit
) {
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (err != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Text(t.error, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(t.connecting, fontSize = 13.sp)
                }
            }
        }
        return
    }

    val isOnline = err == null && latency >= 0f
    val diskPrimary = m.disks.firstOrNull()
    val diskFreePct = if (diskPrimary != null) (100f - diskPrimary.pct).coerceIn(0f, 100f) else 100f

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 1. Top Profile / Identity Card ──
        item {
            ModernCard(padding = 14.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Pill action (Test Telegram Alert)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onTestAlert() }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(12.dp))
                            Text(
                                t.testTelegram,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Right: Server Name + Status Pill + Avatar Icon
                    Column(horizontalAlignment = Alignment.End) {
                        Text(server.name.ifEmpty { server.host }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        StatusPill(if (isOnline) "• ${t.online}" else "• ${t.offline}", isOnline = isOnline)
                    }

                    Spacer(Modifier.width(10.dp))

                    IconBadge(
                        icon = Icons.Rounded.Dns,
                        tint = Color.White,
                        background = Color(0xFF0D9488),
                        size = 44.dp,
                        iconSize = 22.dp
                    )
                }
            }
        }

        // ── 2. Hero Resource Gauge Card ──
        item {
            ModernCard(padding = 18.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Metrics List
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResourceStatRow("مصرف پردازنده", Fmt.pct(m.cpuUsage), Color.Unspecified)
                        ResourceStatRow("حافظه رم", "${Fmt.bytes(m.memUsed)} / ${Fmt.bytes(m.memTotal)}", Color.Unspecified)
                        if (diskPrimary != null) {
                            ResourceStatRow("فضای آزاد دیسک", "${Fmt.bytes(diskPrimary.total - diskPrimary.used)} (${Fmt.pct(100f - diskPrimary.pct)})", Color(0xFF10B981))
                        }
                        ResourceStatRow("وضعیت پایداری", if (isOnline) "آنلاین (${Fmt.uptime(m.uptime)})" else "آفلاین", Color(0xFF10B981))
                    }

                    Spacer(Modifier.width(16.dp))

                    // Circular Progress Gauge Ring
                    CircularGauge(
                        percentage = m.cpuUsage,
                        label = "مصرف CPU",
                        size = 115.dp,
                        strokeWidth = 10.dp,
                        activeColor = when {
                            m.cpuUsage > 85f -> Color(0xFFEF4444)
                            m.cpuUsage > 60f -> Color(0xFFF59E0B)
                            else -> Color(0xFF10B981)
                        }
                    )
                }
            }
        }

        // ── 3. Primary Full-Width Action Button ──
        item {
            PrimaryActionButton(
                text = "پایش زنده و رصد سریع پروسه‌ها",
                icon = Icons.Rounded.Bolt,
                onClick = { onNavigateTab(1) }
            )
        }

        // ── 4. 2x2 Secondary Action Cards Grid (With Vector Icons) ──
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionCard(
                        title = "کانتینرهای داکر",
                        icon = Icons.Rounded.Layers,
                        onClick = { onNavigateTab(4) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionCard(
                        title = "کارآگاه اسپایک",
                        icon = Icons.Rounded.Timeline,
                        onClick = { onNavigateTab(2) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryActionCard(
                        title = "پورت‌ها و اتصالات",
                        icon = Icons.Rounded.Sensors,
                        onClick = { onNavigateTab(3) },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryActionCard(
                        title = "مدیریت پروسه‌ها",
                        icon = Icons.Rounded.Memory,
                        onClick = { onNavigateTab(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 5. Linear Progress Bar Card ──
        item {
            ModernCard(padding = 16.dp) {
                ProgressMetricBar(
                    title = "فضای آزاد دیسک اصلی (NVMe / SSD)",
                    percentage = diskFreePct,
                    progressColor = Color(0xFF6366F1)
                )
            }
        }

        // ── 6. 2x2 Metric Stat Cards Grid ──
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricStatCard(
                        icon = Icons.Rounded.SwapVert,
                        title = "ترافیک لحظه‌ای شبکه",
                        value = if (m.nets.isNotEmpty()) "↓ ${Fmt.rate(m.nets[0].rx)}\n↑ ${Fmt.rate(m.nets[0].tx)}" else "—",
                        modifier = Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    MetricStatCard(
                        icon = Icons.Rounded.Speed,
                        title = "لود پردازنده (Load 1m)",
                        value = "%.2f (%d cores)".format(Locale.US, m.load1, m.cores),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricStatCard(
                        icon = Icons.Rounded.Public,
                        title = "زمان پاسخ (Latency)",
                        value = if (latency >= 0f) "${latency.toInt()} ms" else "—",
                        modifier = Modifier.weight(1f),
                        valueColor = if (latency > 250f) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                    MetricStatCard(
                        icon = Icons.Rounded.AccessTime,
                        title = "آپتایم سرور",
                        value = Fmt.uptime(m.uptime),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 7. Charts & History Sparklines ──
        item {
            ModernCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("نمودار تغییرات ۲۴ ساعته پردازنده و رم", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                if (hist.isNotEmpty()) {
                    Sparkline(hist.map { it.cpu }, Modifier.fillMaxWidth().height(48.dp), color = Color(0xFF0D9488))
                    Spacer(Modifier.height(6.dp))
                    Sparkline(hist.map { it.mem }, Modifier.fillMaxWidth().height(48.dp), color = Color(0xFF6366F1))
                } else {
                    Text("در حال جمع‌آوری تاریخچه…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ResourceStatRow(title: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
        Text(
            title,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Processes Tab ────────────────────────────────────────────────────────────

@Composable
private fun ProcessesTab(
    t: Str,
    procs: List<ProcInfo>,
    onRefresh: () -> Unit,
    onKill: (pid: Int, name: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sortByMem by remember { mutableStateOf(false) }

    val filtered = procs.filter {
        query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.pid.toString().contains(query)
    }.sortedByDescending {
        if (sortByMem) it.memMb else it.cpu
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Explanatory Help Card
        item {
            FeatureGuideCard(
                title = "راهنمای مدیریت و بستن پروسه‌ها",
                description = "اگر برنامه‌ای مصرف غیرعادی CPU یا رم دارد یا هنگ کرده است، می‌توانید بدون نیاز به SSH با دکمه قرمز آن را متوقف (Kill) کنید."
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(t.searchProcesses, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedButton(
                    onClick = { sortByMem = !sortByMem },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(54.dp)
                ) {
                    Text(if (sortByMem) t.sortByMem else t.sortByCpu, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(t.noData, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filtered, key = { "${it.pid}-${it.name}" }) { p ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("PID ${p.pid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${p.user}  •  ${Fmt.bytes((p.memMb * 1024 * 1024).toLong())} RAM",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Fmt.pct(p.cpu),
                                color = if (p.cpu > 50) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clickable { onKill(p.pid, p.name) }
                                    .padding(top = 2.dp)
                            ) {
                                Icon(Icons.Rounded.Block, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                Text(
                                    t.kill,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ── Events Tab (Spike Forensics) ───────────────────────────────────────────

@Composable
private fun EventsTab(
    t: Str,
    events: List<SpikeEvent>,
    onKill: (pid: Int, name: String) -> Unit
) {
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

    fun eventStyle(type: String): Triple<ImageVector, String, Color> = when (type) {
        "cpu" -> Triple(Icons.Rounded.LocalFireDepartment, t.spikeCpu, Color(0xFFEF4444))
        "memory" -> Triple(Icons.Rounded.Memory, t.spikeMem, Color(0xFF6366F1))
        "container_down" -> Triple(Icons.Rounded.ErrorOutline, "Container Down", Color(0xFFEF4444))
        "container_up" -> Triple(Icons.Rounded.CheckCircle, "Container Up", Color(0xFF10B981))
        "process_down" -> Triple(Icons.Rounded.ErrorOutline, t.eventProcessDown, Color(0xFFEF4444))
        "process_up" -> Triple(Icons.Rounded.CheckCircle, t.eventProcessUp, Color(0xFF10B981))
        "disk" -> Triple(Icons.Rounded.Storage, t.eventDisk, Color(0xFFF59E0B))
        "steal" -> Triple(Icons.Rounded.Speed, t.eventSteal, Color(0xFFA855F7))
        "agent_restart" -> Triple(Icons.Rounded.Refresh, t.eventAgentRestart, Color(0xFF64748B))
        else -> Triple(Icons.Rounded.Timeline, type, Color(0xFF64748B))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای کارآگاه اسپایک (Spike Forensics)",
                description = t.guideSpikes
            )
        }

        if (events.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(
                            icon = Icons.Rounded.CheckCircle,
                            tint = Color(0xFF10B981),
                            background = Color(0xFF10B981).copy(alpha = 0.12f),
                            size = 56.dp,
                            iconSize = 30.dp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("هیچ اسپایک یا اتفاق غیرعادی در ۲۴ ساعت گذشته ثبت نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(events, key = { "${it.time}-${it.value}-${it.type}" }) { e ->
                val (icon, label, color) = eventStyle(e.type)
                ModernCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = icon, tint = color, background = color.copy(alpha = 0.15f), size = 28.dp, iconSize = 15.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
                        Spacer(Modifier.weight(1f))
                        Text(fmt.format(Date(e.time)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.detail.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(e.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.value > 0f) {
                        Text(Fmt.pct(e.value), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (e.top.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(t.topProcesses, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        e.top.take(4).forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("• ${p.name}", fontSize = 12.5.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("PID ${p.pid}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${Fmt.pct(p.cpu)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { onKill(p.pid, p.name) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Rounded.Block, contentDescription = "Kill", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ── Sockets & Ports Tab ──────────────────────────────────────────────────────

@Composable
private fun SocketsTab(t: Str, data: SocketsData?, onRefresh: () -> Unit) {
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showListeningOnly by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای پورت‌ها و سوکت‌های فعال",
                description = t.guideSockets
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (showListeningOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { showListeningOnly = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Sensors,
                            contentDescription = null,
                            tint = if (showListeningOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${t.listeningPorts} (${data.listening.size})",
                            fontSize = 11.5.sp,
                            fontWeight = if (showListeningOnly) FontWeight.Bold else FontWeight.Normal,
                            color = if (showListeningOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!showListeningOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { showListeningOnly = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            tint = if (!showListeningOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${t.activeConnections} (${data.connections.size})",
                            fontSize = 11.5.sp,
                            fontWeight = if (!showListeningOnly) FontWeight.Bold else FontWeight.Normal,
                            color = if (!showListeningOnly) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
            }
        }

        val itemsToShow = if (showListeningOnly) data.listening else data.connections

        if (itemsToShow.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text(t.noData, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(itemsToShow, key = { "${it.proto}-${it.localIp}-${it.localPort}-${it.remoteIp}-${it.remotePort}-${it.pid}" }) { s ->
                ModernCard(padding = 10.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (s.proto.lowercase() == "tcp") Color(0xFF0D9488).copy(alpha = 0.15f) else Color(0xFF6366F1).copy(alpha = 0.15f)
                        ) {
                            Text(
                                s.proto.uppercase(),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (s.proto.lowercase() == "tcp") Color(0xFF0D9488) else Color(0xFF6366F1)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (showListeningOnly) "${s.localIp}:${s.localPort}" else "${s.localIp}:${s.localPort} ➔ ${s.remoteIp}:${s.remotePort}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (s.process.isNotEmpty()) {
                                Text(
                                    "${s.process} (PID ${s.pid})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            s.state,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.state == "LISTEN" || s.state == "ESTABLISHED") Color(0xFF10B981) else Color(0xFF64748B)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ── Docker Containers Tab ───────────────────────────────────────────────────

@Composable
private fun DockerTab(server: ServerConfig, data: DockerSummaryData?, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }

    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!data.installed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(
                    icon = Icons.Rounded.Layers,
                    tint = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    size = 56.dp,
                    iconSize = 28.dp
                )
                Spacer(Modifier.height(10.dp))
                Text("سرویس Docker روی این سرور در حال اجرا نیست", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای کانتینرهای داکر (Docker)",
                description = "تمام کانتینرهای فعال و متوقف Docker را بدون نیاز به دستورات SSH مشاهده و مدیریت کنید. می‌توانید هر کانتینر را با یک کلیک ری‌استارت یا استاپ کنید."
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("کانتینرهای فعال (${data.containers.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
            }
        }

        items(data.containers, key = { it.id }) { c ->
            val isRunning = c.state == "running"
            ModernCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444),
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isRunning) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                    ) {
                        Text(
                            c.state.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(c.image, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                Text(c.status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                api.dockerRestart(server, c.id)
                                Toast.makeText(ctx, "کانتینر ${c.name} ری‌استارت شد", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                            Text("ری‌استارت", fontSize = 11.sp)
                        }
                    }

                    if (isRunning) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    api.dockerStop(server, c.id)
                                    Toast.makeText(ctx, "کانتینر ${c.name} متوقف شد", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "خطا: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                Text("توقف", color = Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ── Global Check-Host Tab ───────────────────────────────────────────────────

@Composable
private fun GlobalCheckTab(t: Str, defaultHost: String) {
    val scope = rememberCoroutineScope()
    var targetHost by remember { mutableStateOf(defaultHost) }
    var selectedType by remember { mutableStateOf("ping") }
    var tcpPort by remember { mutableStateOf("80") }
    var isChecking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }

    val okCount = nodes.count { it.state == 1 }
    val totalCount = nodes.size

    fun startProbe() {
        if (isChecking) return
        val hostToTest = if (selectedType == "tcp") "$targetHost:$tcpPort" else targetHost
        if (hostToTest.trim().isEmpty()) return

        isChecking = true
        statusText = t.probing
        nodes = emptyList()

        scope.launch {
            try {
                val (reqId, initialNodes) = CheckHostService.startCheck(hostToTest, selectedType, 20)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FeatureGuideCard(
                title = "راهنمای تست دسترسی جهانی (Check-Host)",
                description = "وضعیت در دسترس بودن سرور و پاسخ‌دهی پورت‌ها را از ۲۰ نود در سراسر دنیا (اروپا، آمریکا، آسیا و ایران) ارزیابی کنید."
            )
        }

        item {
            ModernCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = { Text(t.probeTarget, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("ping", "http", "tcp", "dns").forEach { type ->
                            val selected = selectedType == type
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
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
            }
        }

        if (statusText.isNotBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    if (totalCount > 0) {
                        Spacer(Modifier.weight(1f))
                        Text("$okCount/$totalCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (nodes.isEmpty() && !isChecking) {
            item {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBadge(
                            icon = Icons.Rounded.Public,
                            tint = MaterialTheme.colorScheme.primary,
                            background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            size = 56.dp,
                            iconSize = 28.dp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(t.enterTarget, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(nodes, key = { it.nodeKey }) { node ->
                ModernCard(padding = 10.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(node.flag, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(node.location.ifEmpty { node.countryCode }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(node.nodeKey, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            node.resultText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (node.state) {
                                1 -> Color(0xFF10B981)
                                2 -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ── Tab Chip with Vector Icon ───────────────────────────────────────────────

@Composable
private fun TabChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
