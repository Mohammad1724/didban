@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.widget.Toast
import androidx.activity.compose.BackHandler
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

    BackHandler { onBack() }

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

    // Test Alert state
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

    // Direct background poll loop
    LaunchedEffect(server.id) {
        while (true) {
            refreshAll()
            delay(10_000)
        }
    }

    // Refresh on tab switch
    LaunchedEffect(server.id, tab) {
        when (tab) {
            1 -> refreshProcs()
            2 -> { try { events = api.events(server, 50) } catch (_: Exception) { } }
            3 -> refreshSockets()
            4 -> refreshDocker()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        // ── Top Navigation Bar ──
        HeaderWithBack(
            title = server.name.ifEmpty { server.host },
            subtitle = "${server.host}:${server.port}",
            onBack = onBack,
            actions = {
                CircleIconButton(
                    icon = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    contentDescription = "Theme",
                    tint = Ds.warn,
                    onClick = onToggleTheme
                )
                Spacer(Modifier.width(6.dp))
                CircleIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    onClick = { refreshAll() }
                )
            }
        )

        // ── Certificate Pinning Notice ──
        if (server.useTls && server.fingerprint.isEmpty()) {
            val fp = api.lastSeenFingerprint
            BannerCard(
                text = t.pinCertHint,
                tone = BannerTone.Warn,
                action = {
                    if (fp != null) {
                        SoftButton(
                            text = t.pinCert,
                            onClick = {
                                server.fingerprint = fp
                                val list = Prefs.loadServers(ctx).map { if (it.id == server.id) server else it }
                                Prefs.saveServers(ctx, list)
                            }
                        )
                    }
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // ── Modern Tab Switcher ──
        FilterChipRow(
            items = listOf(
                t.overview,
                t.processes,
                t.events,
                t.listeningPorts,
                "Docker",
                t.globalCheck
            ),
            selectedIndex = tab,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ── Tab Pages ──
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
                    dockerCount = dockerData?.containers?.size ?: 0,
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
                PrimaryButton(
                    text = t.kill,
                    loading = isKilling,
                    enabled = !isKilling,
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
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { procToKill = null }, enabled = !isKilling) { Text(t.cancel) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// OVERVIEW TAB — The Cockpit
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
    dockerCount: Int = 0,
    onNavigateTab: (Int) -> Unit,
    onTestAlert: () -> Unit
) {
    if (m == null) {
        if (err != null) {
            EmptyState(title = t.error, hint = err, icon = Icons.Rounded.ErrorOutline)
        } else {
            LoadingState(t.connecting)
        }
        return
    }

    val isOnline = err == null && latency >= 0f
    val diskPrimary = m.disks.firstOrNull()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().imePadding()
    ) {
        // ── 1. Telemetry Gauges Bento ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusPill(if (isOnline) t.online else t.offline, level = if (isOnline) StatusLevel.Ok else StatusLevel.Danger)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latency >= 0f) {
                            Text(
                                "⚡ ${latency.toInt()} ms",
                                fontSize = 11.sp,
                                fontFamily = Telemetry,
                                color = if (latency > 250f) Ds.warn else Ds.textSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        CircleIconButton(
                            icon = Icons.Rounded.Send,
                            contentDescription = t.testTelegram,
                            tint = Ds.accent,
                            size = 30.dp,
                            onClick = onTestAlert
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Three Ring Gauges (CPU, RAM, Disk)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // CPU Ring
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RingGauge(
                            value = m.cpuUsage,
                            size = 64.dp,
                            strokeWidth = 5.5.dp,
                            tone = if (m.cpuUsage > 80f) Ds.danger else if (m.cpuUsage > 60f) Ds.warn else Ds.accent
                        ) {
                            Text(
                                "${m.cpuUsage.toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.cpu, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        if (m.cpuSteal > 2f) {
                            Text("Steal: ${Fmt.pct(m.cpuSteal)}", fontSize = 9.sp, color = Ds.danger, fontFamily = Telemetry)
                        }
                    }

                    // RAM Ring
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RingGauge(
                            value = m.memPct,
                            size = 64.dp,
                            strokeWidth = 5.5.dp,
                            tone = Ds.violet
                        ) {
                            Text(
                                "${m.memPct.toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.memory, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        Text(Fmt.bytes(m.memUsed), fontSize = 9.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                    }

                    // Disk Ring
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val diskPct = diskPrimary?.pct ?: 0f
                        RingGauge(
                            value = diskPct,
                            size = 64.dp,
                            strokeWidth = 5.5.dp,
                            tone = if (diskPct > 85f) Ds.danger else Ds.info
                        ) {
                            Text(
                                "${diskPct.toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.disks, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        if (diskPrimary != null) {
                            Text(Fmt.bytes(diskPrimary.used), fontSize = 9.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                StatBand(
                    stats = listOf(
                        StatItem(t.lblNetLive, m.nets.firstOrNull()?.let { "↓${Fmt.rate(it.rx)}" } ?: "—", Ds.info),
                        StatItem(t.lblLoad1m, "%.2f".format(Locale.US, m.load1), Ds.textPrimary),
                        StatItem(t.cores, "${m.cores}", Ds.accent),
                        StatItem(t.lblUptime, Fmt.uptime(m.uptime), Ds.textSecondary)
                    )
                )
            }
        }

        // ── 2. Live Telemetry History Chart ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionHeader(
                        title = t.lblCharts24h,
                        icon = Icons.Rounded.Timeline
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (hist.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("CPU (${Fmt.pct(m.cpuUsage)})", fontSize = 10.sp, color = Ds.accent, fontFamily = Telemetry)
                            Spacer(Modifier.height(4.dp))
                            Sparkline(values = hist.map { it.cpu }, modifier = Modifier.fillMaxWidth().height(60.dp), color = Ds.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("RAM (${Fmt.pct(m.memPct)})", fontSize = 10.sp, color = Ds.violet, fontFamily = Telemetry)
                            Spacer(Modifier.height(4.dp))
                            Sparkline(values = hist.map { it.mem }, modifier = Modifier.fillMaxWidth().height(60.dp), color = Ds.violet)
                        }
                    }
                } else {
                    Text(t.lblCollecting, fontSize = 11.5.sp, color = Ds.textTertiary)
                }
            }
        }

        // ── 3. Quick Navigation Bento Tiles ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BentoMetricCard(
                    title = "Docker",
                    value = if (dockerCount > 0) "$dockerCount Active" else "Containers",
                    subtitle = "Manage & Restart",
                    icon = Icons.Rounded.Layers,
                    tone = Ds.accent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(4) }
                )
                BentoMetricCard(
                    title = t.spikeDetective,
                    value = "Spikes Log",
                    subtitle = t.whatAteCpu,
                    icon = Icons.Rounded.LocalFireDepartment,
                    tone = Ds.danger,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(2) }
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BentoMetricCard(
                    title = t.portsAndSockets,
                    value = "Sockets & Net",
                    subtitle = t.listeningPorts,
                    icon = Icons.Rounded.Sensors,
                    tone = Ds.info,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(3) }
                )
                BentoMetricCard(
                    title = t.processManager,
                    value = "Top Procs",
                    subtitle = "Watch & Terminate",
                    icon = Icons.Rounded.Memory,
                    tone = Ds.violet,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateTab(1) }
                )
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// PROCESSES TAB
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = t.searchProcesses,
                    modifier = Modifier.weight(1f)
                )
                SoftButton(
                    text = if (sortByMem) t.sortByMem else t.sortByCpu,
                    onClick = { sortByMem = !sortByMem }
                )
            }
        }

        if (filtered.isEmpty()) {
            item { EmptyState(title = t.noData, radar = false) }
        } else {
            items(filtered, key = { "${it.pid}-${it.name}" }) { p ->
                ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MeterBar(
                            value01 = p.cpu / 100f,
                            tone = when {
                                p.cpu > 80f -> Ds.danger
                                p.cpu > 50f -> Ds.warn
                                else -> Ds.accent
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
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
                                fontSize = 11.sp,
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
                            Spacer(Modifier.height(2.dp))
                            DangerButton(
                                text = t.kill,
                                icon = Icons.Rounded.Stop,
                                onClick = { onKill(p.pid, p.name) }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// EVENTS TAB (Spike Forensic Timeline)
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (events.isEmpty()) {
            item { EmptyState(title = t.noSpikes24h, icon = Icons.Rounded.CheckCircle) }
        } else {
            itemsIndexed(events, key = { _, e -> "${e.time}-${e.value}-${e.type}" }) { idx, e ->
                val (icon, label, color) = eventStyle(e.type)
                ModernCard(padding = 14.dp, cornerRadius = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = icon, tint = color, background = color.copy(alpha = 0.12f), size = 32.dp, iconSize = 16.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = color)
                                Spacer(Modifier.weight(1f))
                                Text(fmt.format(Date(e.time)), fontSize = 10.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                            }
                            if (e.detail.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(e.detail, fontSize = 11.sp, color = Ds.textSecondary)
                            }
                        }
                    }

                    if (e.value > 0f) {
                        Spacer(Modifier.height(8.dp))
                        Text(Fmt.pct(e.value), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = color)
                    }

                    if (e.top.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Hairline()
                        Spacer(Modifier.height(6.dp))
                        e.top.take(4).forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.size(5.dp).background(color, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(p.name, fontSize = 12.sp, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(6.dp))
                                    Text("PID ${p.pid}", fontSize = 9.5.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Fmt.pct(p.cpu), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.accent, fontFamily = Telemetry)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Rounded.Stop,
                                        contentDescription = "Kill",
                                        tint = Ds.danger,
                                        modifier = Modifier.size(15.dp).clickable { onKill(p.pid, p.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SOCKETS & PORTS TAB
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SegmentedControl(
                    items = listOf("${t.listeningPorts} (${data.listening.size})", "${t.activeConnections} (${data.connections.size})"),
                    selectedIndex = if (showListeningOnly) 0 else 1,
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
            item { EmptyState(title = t.noData, radar = false) }
        } else {
            items(itemsToShow, key = { "${it.proto}-${it.localIp}-${it.localPort}-${it.remoteIp}-${it.remotePort}-${it.pid}" }) { s ->
                ModernCard(padding = 12.dp, cornerRadius = 14.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(s.proto.uppercase(), level = if (s.proto.lowercase().startsWith("tcp")) StatusLevel.Info else StatusLevel.Warn, pulse = false)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (showListeningOnly) "${s.localIp}:${s.localPort}" else "${s.localIp}:${s.localPort} → ${s.remoteIp}:${s.remotePort}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (s.process.isNotEmpty()) {
                                Text(
                                    "${s.process} · PID ${s.pid}",
                                    fontSize = 11.sp,
                                    color = Ds.textTertiary
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusPill(
                            s.state,
                            level = when (s.state) {
                                "LISTEN", "ESTABLISHED" -> StatusLevel.Ok
                                else -> StatusLevel.Neutral
                            },
                            pulse = s.state == "ESTABLISHED"
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// DOCKER TAB
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun DockerTab(t: Str, server: ServerConfig, data: DockerSummaryData?, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }

    if (data == null) {
        LoadingState(t.connecting)
        return
    }

    if (!data.installed) {
        EmptyState(title = t.dockerNotRunning, icon = Icons.Rounded.Layers)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionHeader(
                title = "Docker Containers",
                icon = Icons.Rounded.Layers,
                badge = "${data.containers.size}",
                action = {
                    CircleIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        onClick = onRefresh
                    )
                }
            )
        }

        items(data.containers, key = { it.id }) { c ->
            val isRunning = c.state == "running"
            ModernCard(padding = 14.dp, cornerRadius = 16.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PulseDot(color = if (isRunning) Ds.ok else Ds.danger, size = 8.dp)
                    Spacer(Modifier.width(10.dp))
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
                            fontSize = 10.5.sp,
                            color = Ds.accent,
                            fontFamily = Telemetry,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(c.status, fontSize = 10.sp, color = Ds.textTertiary, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircleIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = t.restartLbl,
                            tint = Ds.accent,
                            size = 32.dp,
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
                            }
                        )
                        if (isRunning) {
                            CircleIconButton(
                                icon = Icons.Rounded.Stop,
                                contentDescription = t.stopShort,
                                tint = Ds.danger,
                                size = 32.dp,
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
// GLOBAL CHECK (Check-Host) TAB
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
            ModernCard(padding = 14.dp, cornerRadius = 18.dp) {
                InputField(value = targetHost, onValueChange = { targetHost = it }, label = t.probeTarget, placeholder = "IP or domain")
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    items = listOf("Ping", "HTTP", "TCP"),
                    selectedIndex = when (selectedType) { "http" -> 1; "tcp" -> 2; else -> 0 },
                    onSelect = { selectedType = when (it) { 1 -> "http"; 2 -> "tcp"; else -> "ping" } }
                )
                if (selectedType == "tcp") {
                    Spacer(Modifier.height(8.dp))
                    InputField(value = tcpPort, onValueChange = { tcpPort = it }, label = t.portNumber, placeholder = "80")
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (isChecking) t.probing else t.runProbe,
                    icon = Icons.Rounded.Public,
                    loading = isChecking,
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
            ModernCard(padding = 10.dp, cornerRadius = 12.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(node.flag, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(node.location, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text(node.nodeKey, fontSize = 10.sp, fontFamily = Telemetry, color = Ds.textTertiary)
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
