package org.didban.monitor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Phase 3 · item 3-D — the v3 tunnel fleet page + tunnel context sheet.
 *
 * Replaces the legacy TunnelScreen (1616 lines): every state and action of
 * phase 2 is preserved 1:1 —
 *   - 15s background latency loop over enabled tunnels
 *   - enable switch → controlRemoteTunnel(start/stop)
 *   - auto-deploy (per tunnel + on-save auto-deploy) with result dialog
 *   - latency test with toast, code viewer (Iran/Foreign/Docker, H3 persist,
 *     H4 validation), delete with remote cleanup, discovery + result dialog
 *   - full add/edit form (per-core transport logic, M17 discovered-token rule)
 *   - search + All/Active/AutoSync filters, step-by-step guide
 *
 * v3 structure: fleet = one InstrumentBand + dense single-surface rows;
 * a row tap opens the full-screen tunnel context sheet (v3 layer 2) with
 * the dual-node bridge visual and the action set.
 *
 * UI-only file: verified by code review (no Android/Compose compile in this
 * environment). All data calls are ports of the audited TunnelScreen logic.
 */

@Composable
fun AeroTunnelFleetScreen(
    t: Str,
    seedTunnels: List<TunnelConfig>,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Seeded from the deck's live list so palette/radial quick actions made
    // while this page is composed stay in sync (same Prefs, one truth).
    var tunnels by remember(seedTunnels) { mutableStateOf(seedTunnels) }
    val servers = remember { Prefs.loadServers(ctx) }

    var showForm by remember { mutableStateOf(false) }
    var editingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var viewingCodeTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var deletingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var showGuide by remember { mutableStateOf(tunnels.isEmpty()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(0) } // 0: All, 1: Active, 2: AutoSync

    // Async operation states
    var isDeployingMap by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var isTestingMap by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var isDiscovering by remember { mutableStateOf(false) }
    var deployResultDialog by remember { mutableStateOf<AutoDeployResult?>(null) }
    var discoveryResultDialog by remember { mutableStateOf<TunnelEngine.DiscoveryResult?>(null) }

    // v3 layer 2: the open tunnel context sheet (addressed by id so it
    // always shows the current, possibly-edited config)
    var selectedTunnelId by remember { mutableStateOf<Long?>(null) }
    val selectedTunnel = selectedTunnelId?.let { id -> tunnels.firstOrNull { it.id == id } }

    fun closeAllOverlays() {
        showForm = false
        editingTunnel = null
        viewingCodeTunnel = null
        deletingTunnel = null
        deployResultDialog = null
        discoveryResultDialog = null
        selectedTunnelId = null
    }

    BackHandler(
        enabled = showForm || editingTunnel != null || viewingCodeTunnel != null ||
            deletingTunnel != null || deployResultDialog != null ||
            discoveryResultDialog != null || selectedTunnelId != null
    ) { closeAllOverlays() }

    fun refreshTunnels() {
        tunnels = Prefs.loadTunnels(ctx)
    }

    // Direct background latency test loop (parity with legacy)
    LaunchedEffect(tunnels) {
        while (true) {
            for (tun in tunnels) {
                if (tun.isEnabled && (tun.iranHost.isNotBlank() || tun.foreignHost.isNotBlank())) {
                    try {
                        val res = TunnelEngine.testTunnel(tun)
                        tun.lastStatus = if (res.first) 1 else 0
                        tun.lastLatencyMs = res.second
                        tun.lastChecked = System.currentTimeMillis()
                    } catch (_: Exception) {}
                }
            }
            Prefs.saveTunnels(ctx, tunnels)
            delay(15_000)
        }
    }

    // Phase 4 · 4-A: per-tunnel watchdog badge. The agent (not the phone)
    // is the source of truth — we poll each owning server's watchdog view
    // every 60s and keep the worst state across the tunnel's role nodes.
    // Unreachable/disabled agents simply yield no data (no badge, no error
    // spam): the phone-side latency test above keeps working regardless.
    var wdStates by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    fun refreshWatchdog() {
        scope.launch {
            val api = ApiClient()
            val owners = servers.filter { srv ->
                tunnels.any { it.iranServerId == srv.id || it.foreignServerId == srv.id }
            }
            val wdByServer = owners.associateBy(
                keySelector = { srv -> srv.id },
                valueTransform = { srv ->
                    try {
                        api.tunnelWatchdog(srv).let { TunnelEngine.parseWatchdog(it) }
                    } catch (_: Exception) {
                        null
                    }
                }
            )
            val map = mutableMapOf<Long, String>()
            tunnels.forEach { tun ->
                val states = listOfNotNull(tun.iranServerId, tun.foreignServerId)
                    .mapNotNull { sid -> wdByServer[sid] }
                    .mapNotNull { wd -> wd.tunnels.firstOrNull { it.id == tun.id.toString() }?.state }
                if (states.isNotEmpty()) {
                    map[tun.id] = TunnelEngine.worstWatchdogState(states)
                }
            }
            wdStates = map
        }
    }

    LaunchedEffect(tunnels) {
        while (true) {
            refreshWatchdog()
            delay(60_000)
        }
    }

    // Calculations
    val activeCount = tunnels.count { it.isEnabled }
    val autoSyncCount = tunnels.count { it.autoSync }
    val onlineCount = tunnels.count { it.lastStatus == 1 }

    val filteredTunnels = tunnels.filter { tun ->
        val matchesSearch = searchQuery.isBlank() ||
                tun.name.contains(searchQuery, ignoreCase = true) ||
                tun.iranHost.contains(searchQuery, ignoreCase = true) ||
                tun.foreignHost.contains(searchQuery, ignoreCase = true) ||
                tun.core.displayName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (filterTab) {
            1 -> tun.isEnabled
            2 -> tun.autoSync
            else -> true
        }
        matchesSearch && matchesFilter
    }

    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        scope.launch {
            val res = TunnelEngine.discoverTunnels(ctx)
            isDiscovering = false
            discoveryResultDialog = res
            refreshTunnels()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            // ── Compact fleet header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.tunnelsHub,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ds.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Dual-Node Tunnel Fleet",
                        fontSize = 9.5.sp,
                        fontFamily = Telemetry,
                        color = Ds.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    text = t.addTunnel,
                    icon = Icons.Rounded.Add,
                    onClick = {
                        editingTunnel = null
                        showForm = true
                    }
                )
            }

            // ── Fleet status band (one continuous cluster) ──
            InstrumentBand(
                cells = listOf(
                    InstrumentCellData(label = "Tunnels", value = "${tunnels.size}"),
                    InstrumentCellData(
                        label = "Active",
                        value = "$activeCount",
                        ringPercent = if (tunnels.isEmpty()) null else activeCount / tunnels.size.toFloat(),
                        ringColor = if (activeCount > 0) Ds.ok else Ds.textTertiary
                    ),
                    InstrumentCellData(label = "AutoSync", value = "$autoSyncCount"),
                    InstrumentCellData(
                        label = "Online",
                        value = "$onlineCount",
                        delta = if (tunnels.isEmpty()) "—" else (if (onlineCount > 0) "متصل" else "پایش زنده"),
                        deltaTone = if (onlineCount > 0) DeltaTone.GOOD else DeltaTone.NEUTRAL
                    )
                )
            )

            // ── Discovery ──
            Spacer(Modifier.height(10.dp))
            SoftButton(
                text = if (isDiscovering) t.discoveringTunnels else t.autoDiscoverTunnels,
                icon = Icons.Rounded.AutoAwesome,
                enabled = !isDiscovering,
                onClick = { startDiscovery() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().imePadding()
            ) {
                // ── Supported cores chips ──
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Supported Core Engines (10 Cores):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TunnelCore.values().forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(AeroRadii.pill))
                                        .background(Ds.surfaceLow)
                                        .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.pill))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(Modifier.size(5.dp).background(Ds.accent, CircleShape))
                                        Text(c.displayName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Step-by-step guide (v3 surface) ──
                item {
                    AeroGuideCard(
                        t = t,
                        isExpanded = showGuide,
                        onToggleExpand = { showGuide = !showGuide },
                        onAddTunnel = {
                            editingTunnel = null
                            showForm = true
                        }
                    )
                }

                // ── Search & filter (when multiple tunnels) ──
                if (tunnels.size > 2) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search tunnels...",
                                modifier = Modifier.weight(1f)
                            )
                            SegmentedControl(
                                items = listOf("All (${tunnels.size})", "Active ($activeCount)", "AutoSync ($autoSyncCount)"),
                                selectedIndex = filterTab,
                                onSelect = { filterTab = it },
                                modifier = Modifier.width(180.dp)
                            )
                        }
                    }
                }

                // ── Empty state or dense tunnel deck ──
                if (tunnels.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconBadge(icon = Icons.Rounded.SwapHoriz, tint = Ds.accent, background = Ds.accentDim, size = 52.dp, iconSize = 24.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("هنوز تانلی تعریف نشده است", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "با تعریف تانل جدید، بسترهای امن BackPack، Paqet، Narnia، Backhaul، Rathole و غیره را برقرار کنید؛ یا با کشف خودکار تانل‌های فعال روی سرورها را وارد نمایید.",
                                fontSize = 12.sp,
                                color = Ds.textSecondary,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (servers.isNotEmpty()) {
                                    SoftButton(
                                        text = if (isDiscovering) t.discoveringTunnels else t.autoDiscoverTunnels,
                                        icon = Icons.Rounded.AutoAwesome,
                                        enabled = !isDiscovering,
                                        onClick = { startDiscovery() }
                                    )
                                }
                                PrimaryButton(
                                    text = t.addTunnel,
                                    icon = Icons.Rounded.Add,
                                    onClick = {
                                        editingTunnel = null
                                        showForm = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (filteredTunnels.isEmpty()) {
                        item { EmptyState(title = t.noData, radar = false) }
                    } else {
                        itemsIndexed(filteredTunnels, key = { _, tun -> tun.id }) { i, tunnel ->
                            if (i > 0) v3RowDivider()
                                    val isOnline = tunnel.lastStatus == 1
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 11.dp)
                                            .clickable { selectedTunnelId = tunnel.id },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // identity
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(7.dp))
                                                        .background(Ds.surfaceElevated)
                                                        .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(7.dp))
                                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                                ) {
                                                    Text(
                                                        tunnel.core.displayName,
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Ds.accent
                                                    )
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    tunnel.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = Ds.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (tunnel.discovered) {
                                                    Spacer(Modifier.width(5.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Ds.surfaceElevated)
                                                            .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                    ) {
                                                        Text(
                                                            "کشف‌شده",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Ds.textSecondary
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(3.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("🇮", fontSize = 9.sp)
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "${tunnel.iranHost.ifBlank { "—" }}:${tunnel.iranPort}",
                                                    fontSize = 9.5.sp,
                                                    fontFamily = Telemetry,
                                                    color = Ds.textTertiary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                                    contentDescription = null,
                                                    tint = Ds.accent,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text(
                                                    "${tunnel.foreignHost.ifBlank { "—" }}:${tunnel.foreignPort}",
                                                    fontSize = 9.5.sp,
                                                    fontFamily = Telemetry,
                                                    color = Ds.textTertiary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                                Text("🌐", fontSize = 9.sp)
                                            }
                                        }
                                        // 4-A: watchdog shield — shown only for
                                        // non-healthy states (a healthy tunnel
                                        // is already vouched for by the ⚡ chip).
                                        wdStates[tunnel.id]?.takeIf { it != "up" }?.let { wstate ->
                                            Spacer(Modifier.width(8.dp))
                                            WatchdogBadge(t = t, state = wstate)
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        // status
                                        if (isOnline) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Ds.okDim)
                                                    .border(BorderStroke(1.dp, Ds.ok.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    "⚡ ${tunnel.lastLatencyMs.toInt()} ms",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = Telemetry,
                                                    color = Ds.ok
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Ds.dangerDim)
                                                    .border(BorderStroke(1.dp, Ds.danger.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    t.offline,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Ds.danger
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        // enable switch (consumes its own clicks)
                                        Switch(
                                            checked = tunnel.isEnabled,
                                            onCheckedChange = { enabled ->
                                                val updated = tunnels.map {
                                                    if (it.id == tunnel.id) it.copy(isEnabled = enabled) else it
                                                }
                                                tunnels = updated
                                                Prefs.saveTunnels(ctx, updated)
                                                scope.launch {
                                                    TunnelEngine.controlRemoteTunnel(ctx, tunnel, if (enabled) "start" else "stop")
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedTrackColor = Ds.accent,
                                                checkedThumbColor = Ds.onAccent,
                                                uncheckedTrackColor = Ds.surfaceHighlight
                                            )
                                        )
                                    }
                                }
                        }
                    }

                    item { Spacer(Modifier.height(30.dp)) }
            }
        }

        // ── v3 layer 2: tunnel context sheet ──
        if (selectedTunnel != null) {
            AeroTunnelSheet(
                t = t,
                tunnel = selectedTunnel,
                isDeploying = isDeployingMap[selectedTunnel.id] ?: false,
                isTesting = isTestingMap[selectedTunnel.id] ?: false,
                onBack = { selectedTunnelId = null },
                onToggleEnabled = { enabled ->
                    val updated = tunnels.map {
                        if (it.id == selectedTunnel.id) it.copy(isEnabled = enabled) else it
                    }
                    tunnels = updated
                    Prefs.saveTunnels(ctx, updated)
                    scope.launch {
                        TunnelEngine.controlRemoteTunnel(ctx, selectedTunnel, if (enabled) "start" else "stop")
                    }
                },
                onViewCode = { viewingCodeTunnel = selectedTunnel },
                onAutoDeploy = {
                    isDeployingMap = isDeployingMap + (selectedTunnel.id to true)
                    scope.launch {
                        val result = TunnelEngine.autoDeployTunnel(ctx, selectedTunnel)
                        isDeployingMap = isDeployingMap + (selectedTunnel.id to false)
                        deployResultDialog = result
                        refreshTunnels()
                    }
                },
                onTestLatency = {
                    isTestingMap = isTestingMap + (selectedTunnel.id to true)
                    scope.launch {
                        val res = TunnelEngine.testTunnel(selectedTunnel)
                        selectedTunnel.lastStatus = if (res.first) 1 else 0
                        selectedTunnel.lastLatencyMs = res.second
                        selectedTunnel.lastChecked = System.currentTimeMillis()
                        Prefs.saveTunnels(ctx, tunnels)
                        isTestingMap = isTestingMap + (selectedTunnel.id to false)
                        refreshTunnels()
                        Toast.makeText(
                            ctx,
                            if (res.first) "اتصال موفق! تاخیر: ${res.second}ms" else "عدم پاسخگویی تانل",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onEdit = {
                    editingTunnel = selectedTunnel
                    showForm = true
                },
                onDelete = { deletingTunnel = selectedTunnel }
            )
        }
    }

    // ── Dialogs (parity with legacy) ──

    if (showForm) {
        AeroTunnelFormDialog(
            t = t,
            existing = editingTunnel,
            onDismiss = { showForm = false },
            onSave = { newTun ->
                val list = tunnels.toMutableList()
                val idx = list.indexOfFirst { it.id == newTun.id }
                if (idx >= 0) {
                    list[idx] = newTun
                } else {
                    list.add(0, newTun)
                }
                tunnels = list
                Prefs.saveTunnels(ctx, list)
                showForm = false

                // Auto-deploy on save if enabled
                if (newTun.autoSync) {
                    isDeployingMap = isDeployingMap + (newTun.id to true)
                    scope.launch {
                        val result = TunnelEngine.autoDeployTunnel(ctx, newTun)
                        isDeployingMap = isDeployingMap + (newTun.id to false)
                        deployResultDialog = result
                        refreshTunnels()
                    }
                }
            }
        )
    }

    if (viewingCodeTunnel != null) {
        AeroViewTunnelCodeDialog(
            t = t,
            tunnel = viewingCodeTunnel!!,
            onDismiss = { viewingCodeTunnel = null }
        )
    }

    if (deletingTunnel != null) {
        AlertDialog(
            onDismissRequest = { deletingTunnel = null },
            title = { Text(t.deleteTunnel, fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف تانل «${deletingTunnel!!.name}» اطمینان دارید؟ در صورت استقرار خودکار، سرویس آن از سرورها نیز متوقف خواهد شد.") },
            confirmButton = {
                PrimaryButton(
                    text = t.delete,
                    onClick = {
                        val toDelete = deletingTunnel!!
                        val list = tunnels.filter { it.id != toDelete.id }
                        tunnels = list
                        Prefs.saveTunnels(ctx, list)
                        deletingTunnel = null
                        if (selectedTunnelId == toDelete.id) selectedTunnelId = null

                        scope.launch {
                            TunnelEngine.controlRemoteTunnel(ctx, toDelete, "delete")
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { deletingTunnel = null }) { Text(t.cancel) }
            }
        )
    }

    if (deployResultDialog != null) {
        val r = deployResultDialog!!
        AlertDialog(
            onDismissRequest = { deployResultDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (r.overallSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = if (r.overallSuccess) Ds.ok else Ds.danger
                    )
                    Text("نتیجه استقرار تانل (Auto-Deploy)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BannerCard(
                        text = r.summaryMessage,
                        tone = if (r.overallSuccess) BannerTone.Ok else BannerTone.Danger
                    )

                    r.iranResult?.let { res ->
                        Column(Modifier.v3Surface(AeroRadii.tile).padding(10.dp)) {
                            Text("سرور ایران (${res.serverName}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ds.textPrimary)
                            Text(res.message, fontSize = 11.sp, color = if (res.success) Ds.ok else Ds.danger)
                        }
                    }

                    r.foreignResult?.let { res ->
                        Column(Modifier.v3Surface(AeroRadii.tile).padding(10.dp)) {
                            Text("سرور خارج (${res.serverName}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ds.textPrimary)
                            Text(res.message, fontSize = 11.sp, color = if (res.success) Ds.ok else Ds.danger)
                        }
                    }
                }
            },
            confirmButton = {
                PrimaryButton(text = t.close, onClick = { deployResultDialog = null })
            }
        )
    }

    if (discoveryResultDialog != null) {
        val d = discoveryResultDialog!!
        AlertDialog(
            onDismissRequest = { discoveryResultDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (d.success) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = if (d.success) Ds.ok else Ds.accent
                    )
                    Text(t.discoverResultTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BannerCard(
                        text = d.summary,
                        tone = if (d.success) BannerTone.Ok else BannerTone.Info
                    )

                    if (d.discoveredItems.isNotEmpty()) {
                        Text("تانل‌های فعال کشف‌شده روی سرورها:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().height((d.discoveredItems.size * 56).coerceIn(60, 220).dp)
                        ) {
                            items(d.discoveredItems) { item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AeroRadii.tile))
                                        .background(Ds.surfaceLow)
                                        .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Ds.accentDim)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(item.core.displayName, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                                                }
                                                Text(item.serverName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(item.rawDetail, fontSize = 10.sp, color = Ds.textTertiary)
                                        }
                                        Text(":${item.port}", fontSize = 12.sp, fontFamily = Telemetry, fontWeight = FontWeight.Bold, color = Ds.ok)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                PrimaryButton(text = t.close, onClick = { discoveryResultDialog = null })
            }
        )
    }
}

// ── Tunnel context sheet (v3 layer 2) ───────────────────────────────────────

@Composable
private fun AeroTunnelSheet(
    t: Str,
    tunnel: TunnelConfig,
    isDeploying: Boolean,
    isTesting: Boolean,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onViewCode: () -> Unit,
    onAutoDeploy: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isOnline = tunnel.lastStatus == 1
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ds.canvas)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp)
        ) {
            // header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = t.back,
                    onClick = onBack
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ds.surfaceElevated)
                        .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        tunnel.core.displayName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.accent
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tunnel.name,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tunnel.discovered) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Ds.surfaceElevated)
                                    .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    "کشف‌شده",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ds.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "transport: ${tunnel.transport.name} · core port: ${tunnel.corePort}",
                        fontSize = 9.5.sp,
                        fontFamily = Telemetry,
                        color = Ds.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = tunnel.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Ds.accent,
                        checkedThumbColor = Ds.onAccent,
                        uncheckedTrackColor = Ds.surfaceHighlight
                    )
                )
            }

            // dual-node bridge (v3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AeroRadii.tile))
                    .background(Ds.surfaceLow)
                    .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🇮", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Iran Relay", fontSize = 9.5.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${tunnel.iranHost.ifBlank { "0.0.0.0" }}:${tunnel.iranPort}",
                            fontSize = 11.sp,
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            tunnel.transport.name,
                            fontSize = 8.5.sp,
                            fontFamily = Telemetry,
                            color = Ds.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(4.dp).background(Ds.accent, CircleShape))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Ds.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Box(Modifier.size(4.dp).background(Ds.accent, CircleShape))
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Foreign Node", fontSize = 9.5.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Text("🌐", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${tunnel.foreignHost.ifBlank { "Remote" }}:${tunnel.foreignPort}",
                            fontSize = 11.sp,
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // multi-port forwarding
            if (tunnel.multiPorts.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Forwarding: ${tunnel.multiPorts}",
                    fontSize = 10.sp,
                    fontFamily = Telemetry,
                    color = Ds.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // status + test
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AeroRadii.tile))
                    .background(Ds.surface)
                    .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ds.okDim)
                            .border(BorderStroke(1.dp, Ds.ok.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "⚡ ${tunnel.lastLatencyMs.toInt()} ms",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = Ds.ok
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ds.dangerDim)
                            .border(BorderStroke(1.dp, Ds.danger.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            t.offline,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.danger
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (tunnel.lastChecked > 0L) "چک: ${tunnel.lastChecked}" else "هنوز چک نشده",
                    fontSize = 9.5.sp,
                    fontFamily = Telemetry,
                    color = Ds.textTertiary,
                    modifier = Modifier.weight(1f)
                )
                SoftButton(
                    text = if (isTesting) "…" else "Test Latency",
                    icon = Icons.Rounded.Speed,
                    enabled = !isTesting,
                    onClick = onTestLatency
                )
            }

            // actions
            Spacer(Modifier.height(12.dp))
            if (tunnel.autoSync) {
                PrimaryButton(
                    text = if (isDeploying) "Deploying…" else "Auto-Deploy",
                    icon = Icons.Rounded.CloudSync,
                    loading = isDeploying,
                    onClick = onAutoDeploy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AeroRadii.tile))
                            .background(Ds.surface)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                            .clickable(onClick = onViewCode)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Script & Docker", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("Commands · compose · token", fontSize = 9.sp, color = Ds.textTertiary)
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AeroRadii.tile))
                            .background(Ds.surface)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                            .clickable(onClick = onDelete)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Ds.danger, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t.deleteTunnel, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.danger)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("remove + remote cleanup", fontSize = 9.sp, color = Ds.textTertiary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AeroRadii.tile))
                            .background(Ds.surface)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                            .clickable(onClick = onEdit)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = Ds.violet, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t.editTunnel, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("core · ports · token", fontSize = 9.sp, color = Ds.textTertiary)
                    }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AeroRadii.tile))
                            .background(Ds.surface)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
                            .clickable(onClick = onTestLatency)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, tint = Ds.info, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ping Check", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("latency · connectivity", fontSize = 9.sp, color = Ds.textTertiary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Guide card (v3) ─────────────────────────────────────────────────────────

@Composable
private fun AeroGuideCard(
    t: Str,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddTunnel: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AeroRadii.tile))
            .background(Ds.surface)
            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(AeroRadii.tile))
            .padding(14.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconBadge(
                    icon = Icons.Rounded.HelpOutline,
                    tint = Ds.accent,
                    background = Ds.accentDim,
                    size = 30.dp,
                    iconSize = 16.dp
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        t.tunnelGuideHeader,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Smite-Style 1-Click Dual-Node Deployment",
                        fontSize = 9.5.sp,
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
                size = 30.dp,
                onClick = onToggleExpand
            )
        }

        // 3-F: motion-aware — instant under the system reduce-motion setting
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(aeroTween(300)) + expandVertically(aeroTweenSpec<IntSize>(300)),
            exit = fadeOut(aeroTween(300)) + shrinkVertically(aeroTweenSpec<IntSize>(300))
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Hairline()

                AeroGuideStep(
                    stepNum = "1",
                    title = t.tunnelAddStep1Title,
                    desc = t.tunnelAddStep1Desc
                )
                AeroGuideStep(
                    stepNum = "2",
                    title = t.tunnelAddStep2Title,
                    desc = t.tunnelAddStep2Desc
                )
                AeroGuideStep(
                    stepNum = "3",
                    title = t.tunnelAddStep3Title,
                    desc = t.tunnelAddStep3Desc
                ) {
                    PrimaryButton(
                        text = t.addTunnel,
                        icon = Icons.Rounded.Add,
                        onClick = onAddTunnel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AeroGuideStep(
    stepNum: String,
    title: String,
    desc: String,
    content: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            Text(desc, fontSize = 10.5.sp, color = Ds.textSecondary, lineHeight = 15.sp)
            if (content != null) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

// ── Phase 4 · 4-A: watchdog shield badge ────────────────────────────────────

/**
 * Compact watchdog state chip for a fleet row: shield + label, colored by
 * severity (down/crash_loop = danger, degraded = warn, unknown = neutral).
 */
@Composable
private fun WatchdogBadge(t: Str, state: String) {
    val (color, dim, label) = when (state) {
        "down" -> Triple(Ds.danger, Ds.dangerDim, t.wdDown)
        "crash_loop" -> Triple(Ds.danger, Ds.dangerDim, t.wdCrashLoop)
        "degraded" -> Triple(Ds.warn, Ds.warnDim, t.wdDegraded)
        else -> Triple(Ds.textTertiary, Ds.surfaceLow, t.wdUnknown)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(dim)
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = t.watchdogTitle,
            tint = color,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
