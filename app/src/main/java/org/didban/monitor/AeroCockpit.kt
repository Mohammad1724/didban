package org.didban.monitor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 3 · item 3-C — the v3 Server Cockpit (context layer).
 *
 * Replaces the legacy DashboardScreen: same data pipeline (H7 shared Repo,
 * per-section ApiClient refreshes, PollingCoordinator.requestNow on manual
 * refresh), same six sections, same kill dialog and cert-pinning flow —
 * rendered in the Aerospatial language:
 *
 *   - no TopAppBar: one compact instrument header (status dot, identity,
 *     uptime, live latency, theme, refresh)
 *   - one continuous InstrumentBand cluster (CPU/RAM/DISK mini rings +
 *     net/load/cores) instead of a card stack
 *   - hairline single-surface lists instead of one card per row
 *   - 4-state system (SkeletonBlock / ErrorState / EmptyState / StaleBadge)
 *
 * UI-only file: verified by code review (no Android/Compose compile in this
 * environment). Every data call and state transition is a 1:1 port of the
 * audited DashboardScreen logic.
 */

@Composable
fun AeroCockpitScreen(
    t: Str,
    server: ServerConfig,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }
    val reduceMotion = useReduceMotion()
    var section by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 6 }
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { section = it }
    }

    // 3-F: section navigation respects the system reduce-motion setting
    // (instant jump instead of an animated swipe).
    fun gotoPage(page: Int) {
        scope.launch {
            if (reduceMotion) pagerState.scrollToPage(page)
            else pagerState.animateScrollToPage(page)
        }
    }

    // H7: metrics/latency/error come from the shared Repo (written by the
    // single PollingCoordinator) — the same source the map and list show.
    val repoState by Repo.states.collectAsState()
    val live = repoState[server.id]
    val metrics = live?.metrics
    val latency = live?.latencyMs ?: -1f
    val err = live?.error

    var hist by remember { mutableStateOf<List<HistPoint>>(emptyList()) }
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

    fun refreshHistory() {
        scope.launch {
            try { hist = api.history(server) } catch (_: Exception) { }
        }
    }

    // Manual refresh: force an immediate probe via the coordinator and pull
    // the history endpoint.
    fun refreshAll() {
        PollingCoordinator.requestNow(server.id)
        refreshHistory()
    }

    // H7: metrics polling is owned by the single PollingCoordinator. This
    // loop only refreshes the history endpoint — dashboard-specific.
    LaunchedEffect(server.id) {
        PollingCoordinator.requestNow(server.id)
        refreshHistory()
        while (true) {
            delay(Prefs.getPollIntervalMs(ctx))
            refreshHistory()
        }
    }

    // Refresh on section switch
    LaunchedEffect(server.id, section) {
        when (section) {
            1 -> refreshProcs()
            2 -> { try { events = api.events(server, 50) } catch (_: Exception) { } }
            3 -> refreshSockets()
            4 -> refreshDocker()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // ── v3 instrument header (no app bar) ──
        AeroCockpitHeader(
            t = t,
            server = server,
            metrics = metrics,
            err = err,
            updatedMs = live?.updated ?: 0L,
            latency = latency,
            isDarkMode = isDarkMode,
            onBack = onBack,
            onToggleTheme = onToggleTheme,
            onRefresh = { refreshAll() }
        )

        // ── Certificate pinning notice (parity with legacy) ──
        if (server.useTls && server.fingerprint.isEmpty()) {
            val fp = api.lastSeenFingerprint
            AeroPinBanner(
                hint = t.pinCertHint,
                actionLabel = t.pinCert,
                onAction = {
                    if (fp != null) {
                        server.fingerprint = fp
                        val list = Prefs.loadServers(ctx).map { if (it.id == server.id) server else it }
                        Prefs.saveServers(ctx, list)
                    }
                },
                modifier = Modifier.padding(horizontal = 14.dp, bottom = 10.dp)
            )
        }

        // ── One continuous instrument cluster ──
        Box(Modifier.padding(horizontal = 14.dp)) {
            if (metrics != null) {
                AeroInstrumentCluster(t = t, m = metrics)
            } else {
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                    radius = AeroRadii.band
                )
            }
        }

        // ── Section chips ──
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val labels = listOf(
                t.overview, t.processes, t.events,
                t.listeningPorts, "Docker", t.globalCheck
            )
            labels.forEachIndexed { i, label ->
                AeroSectionChip(
                    label = label,
                    active = section == i,
                    onClick = { gotoPage(i) }
                )
            }
        }

        // ── Section pages ──
        HorizontalPager(
            state = pagerState,
            offscreenLimit = 0,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> AeroOverviewSection(
                    t = t,
                    m = metrics,
                    err = err,
                    hist = hist,
                    dockerCount = dockerData?.containers?.size ?: 0,
                    onNavigate = { target -> gotoPage(target) },
                    onRefresh = { refreshAll() },
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
                1 -> AeroProcessesSection(
                    t = t,
                    procs = procs,
                    onKill = { pid, name ->
                        procToKill = Pair(pid, name)
                        killSignal = "SIGTERM"
                    }
                )
                2 -> AeroEventsSection(
                    t = t,
                    events = events,
                    onKill = { pid, name ->
                        procToKill = Pair(pid, name)
                        killSignal = "SIGTERM"
                    }
                )
                3 -> AeroSocketsSection(t = t, data = socketsData, onRefresh = { refreshSockets() })
                4 -> AeroDockerSection(t = t, server = server, data = dockerData, onRefresh = { refreshDocker() })
                5 -> AeroProbeSection(t = t, defaultHost = server.host)
            }
        }
    }

    // ── Kill Process Confirmation Dialog (parity with legacy) ──
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

// ── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun AeroCockpitHeader(
    t: Str,
    server: ServerConfig,
    metrics: Metrics?,
    err: String?,
    updatedMs: Long,
    latency: Float,
    isDarkMode: Boolean,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit
) {
    val statusColor = when {
        metrics != null && err == null -> Ds.ok
        err != null -> Ds.danger
        else -> Ds.textTertiary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = t.back,
            onClick = onBack
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    server.name.ifEmpty { server.host },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ds.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            val sub = buildString {
                append("${server.host}:${server.port}")
                if (metrics != null && err == null) {
                    append(" · ")
                    append(t.lblUptime)
                    append(" ")
                    append(Fmt.uptime(metrics.uptime))
                }
            }
            Text(
                sub,
                fontSize = 9.5.sp,
                fontFamily = Telemetry,
                color = Ds.textTertiary,
                style = TabularNums,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (latency >= 0f) {
            Text(
                "${latency.toInt()} ms",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Telemetry,
                color = if (latency > 250f) Ds.warn else Ds.textSecondary,
                style = TabularNums
            )
            Spacer(Modifier.width(6.dp))
        }
        if (metrics != null && err != null && updatedMs > 0L) {
            StaleBadge(ageLabel = ageLabel(System.currentTimeMillis() - updatedMs, t), modifier = Modifier.padding(end = 8.dp))
        }
        CircleIconButton(
            icon = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = "Theme",
            tint = Ds.warn,
            onClick = onToggleTheme
        )
        Spacer(Modifier.width(6.dp))
        CircleIconButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = "Refresh",
            onClick = onRefresh
        )
    }
}

private fun ageLabel(ms: Long, t: Str): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s < 60) "${s} ${t.staleSuffix}" else "${s / 60}m ${t.staleSuffix}"
}

// ── Cert pinning banner (v3 hairline row) ───────────────────────────────────

@Composable
private fun AeroPinBanner(hint: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AeroRadii.tile))
            .background(Ds.warnDim.copy(alpha = 0.35f))
            .border(BorderStroke(1.dp, Ds.warn.copy(alpha = 0.3f)), RoundedCornerShape(AeroRadii.tile))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = Ds.warn,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            hint,
            fontSize = 10.5.sp,
            color = Ds.textSecondary,
            modifier = Modifier.weight(1f)
        )
        SoftButton(text = actionLabel, onClick = onAction)
    }
}

// ── Instrument cluster ──────────────────────────────────────────────────────

@Composable
private fun AeroInstrumentCluster(t: Str, m: Metrics) {
    val diskPrimary = m.disks.firstOrNull()
    val diskPct = diskPrimary?.pct ?: 0f
    val steal = m.cpuSteal
    InstrumentBand(
        cells = listOf(
            InstrumentCellData(
                label = t.cpu,
                value = "${m.cpuUsage.toInt()}%",
                ringPercent = m.cpuUsage / 100f,
                ringColor = when {
                    m.cpuUsage > 80f -> Ds.danger
                    m.cpuUsage > 60f -> Ds.warn
                    else -> Ds.accent
                },
                delta = if (steal > 2f) "steal ${Fmt.pct(steal)}" else null,
                deltaTone = if (steal > 2f) DeltaTone.BAD else DeltaTone.NEUTRAL
            ),
            InstrumentCellData(
                label = t.memory,
                value = "${m.memPct.toInt()}%",
                ringPercent = m.memPct / 100f,
                ringColor = Ds.violet,
                delta = Fmt.bytes(m.memUsed)
            ),
            InstrumentCellData(
                label = t.disks,
                value = "${diskPct.toInt()}%",
                ringPercent = diskPct / 100f,
                ringColor = if (diskPct > 85f) Ds.danger else Ds.info,
                delta = diskPrimary?.let { Fmt.bytes(it.used) }
            ),
            InstrumentCellData(
                label = t.lblNetLive,
                value = m.nets.firstOrNull()?.let { "↓${Fmt.rate(it.rx)}" } ?: "—"
            ),
            InstrumentCellData(label = t.lblLoad1m, value = "%.2f".format(Locale.US, m.load1)),
            InstrumentCellData(label = t.cores, value = "${m.cores}")
        )
    )
}

// ── Section chip ────────────────────────────────────────────────────────────

@Composable
private fun AeroSectionChip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AeroRadii.chip)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) Ds.accent else Ds.surface)
            .border(BorderStroke(1.dp, if (active) Ds.accent else Ds.hairline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.5.dp)
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Ds.onAccent else Ds.textSecondary
        )
    }
}


// ── Section 0 · Overview ────────────────────────────────────────────────────

@Composable
private fun AeroOverviewSection(
    t: Str,
    m: Metrics?,
    err: String?,
    hist: List<HistPoint>,
    dockerCount: Int,
    onNavigate: (Int) -> Unit,
    onRefresh: () -> Unit,
    onTestAlert: () -> Unit
) {
    if (m == null) {
        if (err != null) {
            ErrorState(
                title = t.error,
                body = err,
                actionLabel = t.retryProbe,
                onAction = onRefresh,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            // skeleton matching the final shape (no layout jump)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SkeletonBlock(Modifier.fillMaxWidth().height(132.dp), radius = AeroRadii.table)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.weight(1f).height(72.dp), radius = AeroRadii.tile)
                    SkeletonBlock(Modifier.weight(1f).height(72.dp), radius = AeroRadii.tile)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.weight(1f).height(72.dp), radius = AeroRadii.tile)
                    SkeletonBlock(Modifier.weight(1f).height(72.dp), radius = AeroRadii.tile)
                }
            }
        }
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)
    ) {
        // 24h chart — one surface, two channels
        item {
            Column(Modifier.v3Surface().padding(14.dp)) {
                SectionHeader(title = t.lblCharts24h, icon = Icons.Rounded.Timeline)
                Spacer(Modifier.height(10.dp))
                if (hist.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CPU (${Fmt.pct(m.cpuUsage)})",
                                fontSize = 9.5.sp,
                                fontFamily = Telemetry,
                                color = Ds.accent,
                                style = TabularNums
                            )
                            Spacer(Modifier.height(4.dp))
                            Sparkline(values = hist.map { it.cpu }, modifier = Modifier.fillMaxWidth().height(56.dp), color = Ds.accent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "RAM (${Fmt.pct(m.memPct)})",
                                fontSize = 9.5.sp,
                                fontFamily = Telemetry,
                                color = Ds.violet,
                                style = TabularNums
                            )
                            Spacer(Modifier.height(4.dp))
                            Sparkline(values = hist.map { it.mem }, modifier = Modifier.fillMaxWidth().height(56.dp), color = Ds.violet)
                        }
                    }
                } else {
                    Text(t.lblCollecting, fontSize = 11.sp, color = Ds.textTertiary)
                }
            }
        }

        // test-alert row
        item {
            Row(
                modifier = Modifier
                    .v3Surface(AeroRadii.tile)
                    .clickable(onClick = onTestAlert)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Send,
                    contentDescription = null,
                    tint = Ds.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(t.testTelegram, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary, modifier = Modifier.weight(1f))
            }
        }

        // 2×2 quick tiles
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroQuickTile(
                        icon = Icons.Rounded.Layers,
                        title = "Docker",
                        value = if (dockerCount > 0) "$dockerCount Active" else "Containers",
                        subtitle = "Manage & Restart",
                        tone = Ds.accent,
                        onClick = { onNavigate(4) }
                    )
                    AeroQuickTile(
                        icon = Icons.Rounded.Sensors,
                        title = t.portsAndSockets,
                        value = "Sockets & Net",
                        subtitle = t.listeningPorts,
                        tone = Ds.info,
                        onClick = { onNavigate(3) }
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AeroQuickTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = t.spikeDetective,
                        value = "Spikes Log",
                        subtitle = t.whatAteCpu,
                        tone = Ds.danger,
                        onClick = { onNavigate(2) }
                    )
                    AeroQuickTile(
                        icon = Icons.Rounded.Memory,
                        title = t.processManager,
                        value = "Top Procs",
                        subtitle = "Watch & Terminate",
                        tone = Ds.violet,
                        onClick = { onNavigate(1) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun AeroQuickTile(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    tone: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .v3Surface(AeroRadii.tile)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(1.dp))
        Text(subtitle, fontSize = 9.sp, color = Ds.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── Section 1 · Processes ───────────────────────────────────────────────────

@Composable
private fun AeroProcessesSection(
    t: Str,
    procs: List<ProcInfo>,
    onKill: (pid: Int, name: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sortByMem by remember { mutableStateOf(false) }

    val filtered = procs.filter {
        query.isEmpty() || it.name.contains(query, ignoreCase = true) || it.pid.toString().contains(query)
    }.sortedByDescending {
        if (sortByMem) it.memMb else it.cpu
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)) {
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
        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            EmptyState(title = t.noData, radar = false)
        } else {
            LazyColumn(Modifier.weight(1f).v3ListSurface()) {
                item {
                    v3ListHeaderRow(
                        listOf(
                            t.processManager to false,
                            "CPU" to true,
                            t.kill to false
                        )
                    )
                }
                itemsIndexed(filtered, key = { _, p -> "${p.pid}-${p.name}" }) { i, p ->
                    if (i > 0) v3RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MeterBar(
                            value01 = p.cpu / 100f,
                            width = 4.dp,
                            height = 26.dp,
                            tone = when {
                                p.cpu > 80f -> Ds.danger
                                p.cpu > 50f -> Ds.warn
                                else -> Ds.accent
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "PID ${p.pid}",
                                    fontSize = 9.sp,
                                    color = Ds.textTertiary,
                                    fontFamily = Telemetry
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${p.user} · ${Fmt.bytes((p.memMb * 1024 * 1024).toLong())} RAM",
                                fontSize = 10.sp,
                                color = Ds.textTertiary
                            )
                        }
                        Text(
                            Fmt.pct(p.cpu),
                            color = if (p.cpu > 50) Ds.danger else Ds.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = Telemetry,
                            style = TabularNums
                        )
                        Spacer(Modifier.width(10.dp))
                        DangerButton(
                            text = t.kill,
                            icon = Icons.Rounded.Stop,
                            onClick = { onKill(p.pid, p.name) }
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

// ── Section 2 · Events (spike forensic timeline) ────────────────────────────

@Composable
private fun AeroEventsSection(
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

    LazyColumn(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp).v3ListSurface()) {
        if (events.isEmpty()) {
            item { EmptyState(title = t.noSpikes24h, icon = Icons.Rounded.CheckCircle) }
        } else {
            itemsIndexed(events, key = { _, e -> "${e.time}-${e.value}-${e.type}" }) { idx, e ->
                if (idx > 0) v3RowDivider()
                val (icon, label, color) = eventStyle(e.type)
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = icon, tint = color, background = color.copy(alpha = 0.12f), size = 30.dp, iconSize = 15.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = color, modifier = Modifier.weight(1f))
                                Text(fmt.format(Date(e.time)), fontSize = 9.5.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                            }
                            if (e.detail.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(e.detail, fontSize = 10.5.sp, color = Ds.textSecondary)
                            }
                        }
                    }

                    if (e.value > 0f) {
                        Spacer(Modifier.height(6.dp))
                        Text(Fmt.pct(e.value), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = color, style = TabularNums)
                    }

                    if (e.top.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Hairline()
                        Spacer(Modifier.height(4.dp))
                        e.top.take(4).forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.size(5.dp).background(color, CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                    Text(p.name, fontSize = 11.5.sp, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.width(6.dp))
                                    Text("PID ${p.pid}", fontSize = 9.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Fmt.pct(p.cpu), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent, fontFamily = Telemetry, style = TabularNums)
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
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── Section 3 · Sockets & ports ─────────────────────────────────────────────

@Composable
private fun AeroSocketsSection(t: Str, data: SocketsData?, onRefresh: () -> Unit) {
    if (data == null) {
        LoadingState(t.connecting)
        return
    }

    var showListeningOnly by remember { mutableStateOf(true) }
    val itemsToShow = if (showListeningOnly) data.listening else data.connections

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedControl(
                items = listOf(
                    "${t.listeningPorts} (${data.listening.size})",
                    "${t.activeConnections} (${data.connections.size})"
                ),
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
        Spacer(Modifier.height(10.dp))

        if (itemsToShow.isEmpty()) {
            EmptyState(title = t.noData, radar = false)
        } else {
            LazyColumn(Modifier.weight(1f).v3ListSurface()) {
                item {
                    v3ListHeaderRow(
                        listOf(
                            "PROTO" to true,
                            "ADDR" to true,
                            if (showListeningOnly) "PROC" to true else "REMOTE" to true,
                            "STATE" to true
                        )
                    )
                }
                itemsIndexed(
                    itemsToShow,
                    key = { _, s -> "${s.proto}-${s.localIp}-${s.localPort}-${s.remoteIp}-${s.remotePort}-${s.pid}" }
                ) { i, s ->
                    if (i > 0) v3RowDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val protoColor = if (s.proto.lowercase().startsWith("tcp")) Ds.info else Ds.warn
                        val stateColor = when (s.state) {
                            "LISTEN", "ESTABLISHED" -> Ds.ok
                            else -> Ds.textTertiary
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.proto.uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = protoColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${s.localIp}:${s.localPort}",
                                fontSize = 10.5.sp,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (showListeningOnly) {
                                Text(
                                    if (s.process.isNotEmpty()) "${s.process} · ${s.pid}" else "—",
                                    fontSize = 10.5.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    "→ ${s.remoteIp}:${s.remotePort}",
                                    fontSize = 10.5.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            s.state,
                            fontSize = 9.5.sp,
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold,
                            color = stateColor
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

// ── Section 4 · Docker ──────────────────────────────────────────────────────

@Composable
private fun AeroDockerSection(t: Str, server: ServerConfig, data: DockerSummaryData?, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }
    val reduceMotion = useReduceMotion()

    if (data == null) {
        LoadingState(t.connecting)
        return
    }

    if (!data.installed) {
        EmptyState(title = t.dockerNotRunning, icon = Icons.Rounded.Layers)
        return
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = "Docker Containers",
                icon = Icons.Rounded.Layers,
                badge = "${data.containers.size}",
                modifier = Modifier.weight(1f)
            )
            CircleIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                onClick = onRefresh
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(Modifier.weight(1f).v3ListSurface()) {
            itemsIndexed(data.containers, key = { _, c -> c.id }) { i, c ->
                if (i > 0) v3RowDivider()
                val isRunning = c.state == "running"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 3-F: a "running" dot stays visible but stops pulsing under reduce-motion
                    PulseDot(color = if (isRunning) Ds.ok else Ds.danger, size = 8.dp, pulsing = isRunning && !reduceMotion)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
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
                        Text(c.status, fontSize = 9.5.sp, color = Ds.textTertiary, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircleIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = t.restartLbl,
                            tint = Ds.accent,
                            size = 30.dp,
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
                                size = 30.dp,
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
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── Section 5 · Global check (check-host probe) ─────────────────────────────

@Composable
private fun AeroProbeSection(t: Str, defaultHost: String) {
    val scope = rememberCoroutineScope()
    var targetHost by remember { mutableStateOf(defaultHost) }
    var selectedType by remember { mutableStateOf("ping") }
    var tcpPort by remember { mutableStateOf("80") }
    var isChecking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }

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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp)
    ) {
        item {
            Column(Modifier.v3Surface(AeroRadii.tile).padding(14.dp)) {
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

        if (nodes.isNotEmpty()) {
            item {
                Column(Modifier.v3Surface()) {
                    nodes.forEachIndexed { i, node ->
                        if (i > 0) v3RowDivider()
                        val isOk = node.state == 1
                        val isFail = node.state == 2
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(node.flag, fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(node.location, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                                Text(node.nodeKey, fontSize = 9.5.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                            }
                            Text(
                                node.resultText,
                                fontSize = 11.sp,
                                fontFamily = Telemetry,
                                fontWeight = FontWeight.Bold,
                                color = if (isOk) Ds.ok else if (isFail) Ds.danger else Ds.textTertiary,
                                style = TabularNums
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}


