@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
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
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // Keep segmented tabs and pager synchronized
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        // ── Top bar ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = t.back,
                onClick = onBack
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.name.ifEmpty { server.host },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Ds.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${server.host}:${server.port}",
                    color = Ds.textTertiary,
                    fontSize = 10.sp,
                    fontFamily = Telemetry
                )
            }
            CircleIconButton(
                icon = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                contentDescription = "Theme",
                tint = Ds.warn,
                onClick = onToggleTheme
            )
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                onClick = { refreshAll() }
            )
        }

        // ── Pin certificate banner if unpinned ──
        if (server.useTls && server.fingerprint.isEmpty()) {
            val fp = api.lastSeenFingerprint
            Banner(
                tone = BannerTone.Warn,
                text = t.pinCertHint,
                actionLabel = if (fp != null) t.pinCert else null,
                onAction = {
                    server.fingerprint = fp!!
                    val list = Prefs.loadServers(ctx).map { if (it.id == server.id) server else it }
                    Prefs.saveServers(ctx, list)
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // ── Segmented tabs ──
        SegmentedTabs(
            tabs = listOf(
                TabSpec(t.overview, Icons.Rounded.Dashboard),
                TabSpec(t.processes, Icons.Rounded.Memory),
                TabSpec(t.events, Icons.Rounded.Timeline),
                TabSpec(t.listeningPorts, Icons.Rounded.Sensors),
                TabSpec("Docker", Icons.Rounded.Layers),
                TabSpec(t.globalCheck, Icons.Rounded.Public)
            ),
            selected = tab,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            scrollable = true,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // ── Content pages ──
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> OverviewTab(
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
                4 -> DockerTab(t = t, server = server, data = dockerData, onRefresh = { refreshDocker() })
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
                        RadioButton(
                            selected = killSignal == "SIGTERM",
                            onClick = { killSignal = "SIGTERM" },
                            colors = RadioButtonDefaults.colors(selectedColor = Ds.accent, unselectedColor = Ds.textTertiary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(t.sigtermDesc, fontSize = 12.sp)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { killSignal = "SIGKILL" }
                    ) {
                        RadioButton(
                            selected = killSignal == "SIGKILL",
                            onClick = { killSignal = "SIGKILL" },
                            colors = RadioButtonDefaults.colors(selectedColor = Ds.danger, unselectedColor = Ds.textTertiary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(t.sigkillDesc, fontSize = 12.sp, color = Ds.danger, fontWeight = FontWeight.Bold)
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
                        containerColor = if (killSignal == "SIGKILL") Ds.danger else Ds.accent,
                        contentColor = if (killSignal == "SIGKILL") Color.White else Ds.onAccent
                    ),
                    enabled = !isKilling
                ) {
                    if (isKilling) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(t.kill, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { procToKill = null }, enabled = !isKilling) { Text(t.cancel) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// OVERVIEW — the cockpit
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun OverviewTab(
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
        if (err != null) {
            EmptyState(
                title = t.error,
                hint = err,
                icon = Icons.Rounded.ErrorOutline
            )
        } else {
            LoadingState(t.connecting)
        }
        return
    }

    val isOnline = err == null && latency >= 0f
    val diskPrimary = m.disks.firstOrNull()
    val diskFreePct = if (diskPrimary != null) (100f - diskPrimary.pct).coerceIn(0f, 100f) else 100f

    @Composable
    fun cpuTone(v: Float): Color = when {
        v > 85f -> Ds.danger
        v > 60f -> Ds.warn
        else -> Ds.ok
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().imePadding()
    ) {
        // ── 1. Cockpit: gauge cluster ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularGauge(
                        percentage = m.cpuUsage,
                        label = "CPU",
                        size = 112.dp,
                        strokeWidth = 9.dp,
                        activeColor = cpuTone(m.cpuUsage)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // identity + alert test
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(if (isOnline) t.online else t.offline, isOnline = isOnline)
                            if (latency >= 0f) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${latency.toInt()} ms",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = Telemetry,
                                    color = if (latency > 250f) Ds.warn else Ds.textSecondary
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            CircleIconButton(
                                icon = Icons.Rounded.Send,
                                contentDescription = t.testTelegram,
                                onClick = onTestAlert,
                                tint = Ds.accent,
                                size = 30.dp
                            )
                        }
                        // RAM ring
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RingGauge(
                                value = m.memPct,
                                size = 44.dp,
                                strokeWidth = 4.dp,
                                tone = Ds.violet
                            ) {
                                Text(
                                    "${m.memPct.toInt()}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Telemetry,
                                    color = Ds.violet
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(t.lblRamUse, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ds.textTertiary)
                                Text(
                                    "${Fmt.bytes(m.memUsed)} / ${Fmt.bytes(m.memTotal)}",
                                    fontSize = 11.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textSecondary
                                )
                            }
                        }
                        // Disk ring
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RingGauge(
                                value = if (diskPrimary != null) 100f - diskPrimary.pct else 0f,
                                size = 44.dp,
                                strokeWidth = 4.dp,
                                tone = Ds.accent
                            ) {
                                Text(
                                    if (diskPrimary != null) "${(100f - diskPrimary.pct).toInt()}" else "—",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Telemetry,
                                    color = Ds.accent
                                )
                            }
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text(t.lblDiskFree, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ds.textTertiary)
                                Text(
                                    if (diskPrimary != null) Fmt.bytes(diskPrimary.total - diskPrimary.used) else "—",
                                    fontSize = 11.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                StatBand(
                    stats = listOf(
                        StatItem(
                            t.lblNetLive,
                            m.nets.firstOrNull()?.let { "↓${Fmt.rate(it.rx)}" } ?: "—",
                            Ds.info
                        ),
                        StatItem(
                            t.lblLoad1m,
                            "%.2f".format(Locale.US, m.load1),
                            Ds.textPrimary
                        ),
                        StatItem(
                            t.lblLatency,
                            if (latency >= 0f) "${latency.toInt()}ms" else "—",
                            if (latency > 250f) Ds.danger else Ds.ok
                        ),
                        StatItem(
                            t.lblUptime,
                            Fmt.uptime(m.uptime),
                            Ds.textSecondary
                        )
                    )
                )
            }
        }

        // ── 2. Live telemetry chart hero ──
        item {
            ModernCard(padding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(t.lblCharts24h, icon = Icons.Rounded.Timeline)
                    Spacer(Modifier.weight(1f))
                    ChartLegendChip(t.cpu, Fmt.pct(m.cpuUsage), Ds.accent)
                    Spacer(Modifier.width(10.dp))
                    ChartLegendChip(t.memory, Fmt.pct(m.memPct), Ds.violet)
                }
                Spacer(Modifier.height(12.dp))
                if (hist.isNotEmpty()) {
                    Sparkline(hist.map { it.cpu }, Modifier.fillMaxWidth().height(58.dp), color = Ds.accent)
                    Spacer(Modifier.height(10.dp))
                    Sparkline(hist.map { it.mem }, Modifier.fillMaxWidth().height(58.dp), color = Ds.violet)
                } else {
                    Text(t.lblCollecting, fontSize = 11.5.sp, color = Ds.textTertiary)
                }
            }
        }

        // ── 3. Cockpit action tiles ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(Icons.Rounded.Layers, t.dockerContainersLbl, { onNavigateTab(4) }, Modifier.weight(1f))
                IconTile(Icons.Rounded.LocalFireDepartment, t.spikeDetective, { onNavigateTab(2) }, Modifier.weight(1f), tone = Ds.danger)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(Icons.Rounded.Sensors, t.portsAndSockets, { onNavigateTab(3) }, Modifier.weight(1f), tone = Ds.info)
                IconTile(Icons.Rounded.Memory, t.processManager, { onNavigateTab(1) }, Modifier.weight(1f), tone = Ds.violet)
            }
        }

        // ── 4. Live process watch ──
        item {
            PrimaryActionButton(
                text = t.liveProcessWatch,
                icon = Icons.Rounded.Bolt,
                onClick = { onNavigateTab(1) }
            )
        }

        item { Spacer(Modifier.height(26.dp)) }
    }
}

@Composable
private fun ChartLegendChip(label: String, value: String, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(tone, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, color = Ds.textTertiary)
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            fontSize = 10.5.sp,
            fontFamily = Telemetry,
            color = tone
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PROCESSES — meter columns
// ═════════════════════════════════════════════════════════════════════════════

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = t.searchProcesses,
                    modifier = Modifier.weight(1f),
                    trailing = {
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = Ds.textTertiary,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clickable { query = "" }
                            )
                        }
                    }
                )
                SoftButton(
                    text = if (sortByMem) t.sortByMem else t.sortByCpu,
                    onClick = { sortByMem = !sortByMem }
                )
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyState(title = t.noData, radar = true) }
        } else {
            items(filtered, key = { "${it.pid}-${it.name}" }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CPU meter column
                    MeterBar(
                        value01 = p.cpu / 100f,
                        tone = when {
                            p.cpu > 85f -> Ds.danger
                            p.cpu > 50f -> Ds.warn
                            else -> Ds.accent
                        }
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "PID ${p.pid}",
                                fontSize = 10.sp,
                                color = Ds.textTertiary,
                                fontFamily = Telemetry
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${p.user} · ${Fmt.bytes((p.memMb * 1024 * 1024).toLong())} RAM",
                            fontSize = 10.5.sp,
                            color = Ds.textTertiary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            Fmt.pct(p.cpu),
                            color = if (p.cpu > 50) Ds.danger else Ds.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            fontFamily = Telemetry
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clickable { onKill(p.pid, p.name) }
                                .padding(top = 3.dp)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, tint = Ds.danger, modifier = Modifier.size(11.dp))
                            Text(t.kill, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ds.danger)
                        }
                    }
                }
                Hairline()
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// EVENTS — spike timeline
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventsTab(
    t: Str,
    events: List<SpikeEvent>,
    onKill: (pid: Int, name: String) -> Unit
) {
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

    @Composable
    fun eventStyle(type: String): Triple<ImageVector, String, Color> = when (type) {
        "cpu" -> Triple(Icons.Rounded.LocalFireDepartment, t.spikeCpu, Ds.danger)
        "memory" -> Triple(Icons.Rounded.Memory, t.spikeMem, Ds.violet)
        "container_down" -> Triple(Icons.Rounded.ErrorOutline, "Container Down", Ds.danger)
        "container_up" -> Triple(Icons.Rounded.CheckCircle, "Container Up", Ds.ok)
        "process_down" -> Triple(Icons.Rounded.ErrorOutline, t.eventProcessDown, Ds.danger)
        "process_up" -> Triple(Icons.Rounded.CheckCircle, t.eventProcessUp, Ds.ok)
        "disk" -> Triple(Icons.Rounded.Storage, t.eventDisk, Ds.warn)
        "steal" -> Triple(Icons.Rounded.Speed, t.eventSteal, Ds.violet)
        "agent_restart" -> Triple(Icons.Rounded.Refresh, t.eventAgentRestart, Ds.neutral)
        else -> Triple(Icons.Rounded.Timeline, type, Ds.neutral)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (events.isEmpty()) {
            item {
                EmptyState(
                    title = t.noSpikes24h,
                    icon = Icons.Rounded.CheckCircle
                )
            }
        } else {
            itemsIndexed(events, key = { _, e -> "${e.time}-${e.value}-${e.type}" }) { idx, e ->
                val (icon, label, color) = eventStyle(e.type)
                val isLast = idx == events.lastIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 7.dp)
                ) {
                    // ── timeline spine ──
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(18.dp)) {
                        Box(
                            Modifier
                                .size(13.dp)
                                .background(color.copy(alpha = 0.15f), CircleShape)
                                .border(1.2.dp, color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(7.dp))
                        }
                        if (!isLast) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .width(1.5.dp)
                                    .fillMaxHeight()
                                    .background(Ds.hairline)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // ── event body ──
                    Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.weight(1f))
                            Text(
                                fmt.format(Date(e.time)),
                                fontSize = 10.sp,
                                color = Ds.textTertiary,
                                fontFamily = Telemetry
                            )
                        }
                        if (e.detail.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(e.detail, fontSize = 11.sp, color = Ds.textSecondary)
                        }
                        if (e.value > 0f) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Fmt.pct(e.value),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = color
                            )
                        }
                        if (e.top.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            e.top.take(4).forEach { p ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(Modifier.size(5.dp).background(color.copy(alpha = 0.7f), CircleShape))
                                        Spacer(Modifier.width(7.dp))
                                        Text(p.name, fontSize = 12.sp, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(6.dp))
                                        Text("PID ${p.pid}", fontSize = 9.5.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            Fmt.pct(p.cpu),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Ds.accent,
                                            fontFamily = Telemetry
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Rounded.Stop,
                                            contentDescription = "Kill",
                                            tint = Ds.danger,
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { onKill(p.pid, p.name) }
                                        )
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

// ═════════════════════════════════════════════════════════════════════════════
// SOCKETS & PORTS
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SocketsTab(t: Str, data: SocketsData?, onRefresh: () -> Unit) {
    if (data == null) {
        LoadingState(t.connecting)
        return
    }

    var showListeningOnly by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedTabs(
                    tabs = listOf(
                        TabSpec(t.listeningPorts, badge = "${data.listening.size}"),
                        TabSpec(t.activeConnections, badge = "${data.connections.size}")
                    ),
                    selected = if (showListeningOnly) 0 else 1,
                    onSelect = { showListeningOnly = it == 0 },
                    modifier = Modifier.weight(1f)
                )
                CircleIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    onClick = onRefresh
                )
            }
        }

        val itemsToShow = if (showListeningOnly) data.listening else data.connections

        if (itemsToShow.isEmpty()) {
            item { EmptyState(title = t.noData, radar = true) }
        } else {
            items(itemsToShow, key = { "${it.proto}-${it.localIp}-${it.localPort}-${it.remoteIp}-${it.remotePort}-${it.pid}" }) { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ValuePill(
                        s.proto.uppercase(),
                        if (s.proto.lowercase().startsWith("tcp")) Ds.accent else Ds.violet
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (showListeningOnly) "${s.localIp}:${s.localPort}" else "${s.localIp}:${s.localPort} → ${s.remoteIp}:${s.remotePort}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Telemetry,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (s.process.isNotEmpty()) {
                            Text(
                                "${s.process} · PID ${s.pid}",
                                fontSize = 10.5.sp,
                                color = Ds.textTertiary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val stateTone = when (s.state) {
                        "LISTEN", "ESTABLISHED" -> Ds.ok
                        else -> Ds.neutral
                    }
                    ValuePill(s.state, stateTone)
                }
                Hairline()
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// DOCKER — panel list
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DockerTab(t: Str, server: ServerConfig, data: DockerSummaryData?, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }

    if (data == null) {
        LoadingState("…")
        return
    }

    if (!data.installed) {
        EmptyState(
            title = t.dockerNotRunning,
            icon = Icons.Rounded.Layers
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Docker", icon = Icons.Rounded.Layers)
                Spacer(Modifier.width(8.dp))
                ValuePill("${data.containers.size}", Ds.accent)
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    onClick = onRefresh
                )
            }
        }

        item {
            ModernCard(padding = 0.dp) {
                Column {
                    data.containers.forEachIndexed { idx, c ->
                        val isRunning = c.state == "running"
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(if (isRunning) Ds.ok else Ds.danger, CircleShape)
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    c.image,
                                    fontSize = 10.sp,
                                    color = Ds.accent,
                                    fontFamily = Telemetry,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(c.status, fontSize = 10.sp, color = Ds.textTertiary, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircleIconButton(
                                    icon = Icons.Rounded.Refresh,
                                    contentDescription = t.restartLbl,
                                    onClick = {
                                        scope.launch {
                                            try {
                                                api.dockerRestart(server, c.id)
                                                Toast.makeText(ctx, t.containerRestartedTpl.format(c.name), Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            } catch (e: Exception) {
                                                Toast.makeText(ctx, "${t.errorShort}: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    tint = Ds.accent,
                                    size = 28.dp
                                )
                                if (isRunning) {
                                    CircleIconButton(
                                        icon = Icons.Rounded.Stop,
                                        contentDescription = t.stopShort,
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    api.dockerStop(server, c.id)
                                                    Toast.makeText(ctx, t.containerStoppedTpl.format(c.name), Toast.LENGTH_SHORT).show()
                                                    onRefresh()
                                                } catch (e: Exception) {
                                                    Toast.makeText(ctx, "${t.errorShort}: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        tint = Ds.danger,
                                        size = 28.dp
                                    )
                                } else {
                                    // keep rows aligned when only one action exists
                                    Spacer(Modifier.size(28.dp))
                                }
                            }
                        }
                        if (idx != data.containers.lastIndex) {
                            Hairline(Modifier.padding(horizontal = 14.dp))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// GLOBAL CHECK (Check-Host)
// ═════════════════════════════════════════════════════════════════════════════

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ModernCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DTextField(
                        value = targetHost,
                        onValueChange = { targetHost = it },
                        label = t.probeTarget,
                        mono = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SegmentedTabs(
                        tabs = listOf("ping", "http", "tcp", "dns").map { TabSpec(it.uppercase()) },
                        selected = listOf("ping", "http", "tcp", "dns").indexOf(selectedType),
                        onSelect = { selectedType = listOf("ping", "http", "tcp", "dns")[it] }
                    )
                    if (selectedType == "tcp") {
                        DTextField(
                            value = tcpPort,
                            onValueChange = { tcpPort = it },
                            label = t.portNumber,
                            mono = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PrimaryActionButton(
                        text = if (isChecking) t.probing else t.runProbe,
                        onClick = { startProbe() },
                        enabled = !isChecking && targetHost.isNotBlank(),
                        icon = Icons.Rounded.Public
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
                    Text(statusText, fontSize = 12.sp, color = Ds.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (totalCount > 0) {
                        Text(
                            "$okCount/$totalCount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = Ds.textPrimary
                        )
                    }
                }
            }
        }

        if (nodes.isEmpty() && !isChecking) {
            item { EmptyState(title = t.enterTarget, icon = Icons.Rounded.Public, hint = t.globalCheck) }
        } else {
            items(nodes, key = { it.nodeKey }) { node ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(node.flag, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.location.ifEmpty { node.countryCode },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ds.textPrimary
                        )
                        Text(node.nodeKey, fontSize = 9.5.sp, color = Ds.textTertiary, fontFamily = Telemetry, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        node.resultText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Telemetry,
                        color = when (node.state) {
                            1 -> Ds.ok
                            2 -> Ds.danger
                            else -> Ds.textTertiary
                        }
                    )
                }
                Hairline()
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}
