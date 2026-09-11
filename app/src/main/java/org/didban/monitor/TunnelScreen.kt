@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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

// ═════════════════════════════════════════════════════════════════════════════
// TUNNEL SCREEN — Dual-Node Iran-Kharej Smite Auto-Deploy & Reverse Hub
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TunnelScreen(
    t: Str,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tunnels by remember { mutableStateOf<List<TunnelConfig>>(Prefs.loadTunnels(ctx)) }
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
    var deployResultDialog by remember { mutableStateOf<AutoDeployResult?>(null) }

    fun refreshTunnels() {
        tunnels = Prefs.loadTunnels(ctx)
    }

    // Direct background latency test loop
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Rounded.SwapHoriz,
                    tint = Ds.accent,
                    background = Ds.accentDim,
                    size = 38.dp,
                    iconSize = 20.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        t.tunnelsHub,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.textPrimary
                    )
                    Text(
                        "Dual-Node Auto-Deploy & Reverse Bridge",
                        fontSize = 11.sp,
                        fontFamily = Telemetry,
                        color = Ds.textTertiary
                    )
                }
            }

            PrimaryButton(
                text = t.addTunnel,
                icon = Icons.Rounded.Add,
                onClick = {
                    editingTunnel = null
                    showForm = true
                },
                modifier = Modifier.height(36.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 1. Cyber Bento Fleet Status Deck ──
            item {
                ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(
                                color = if (activeCount > 0) Ds.ok else Ds.textTertiary,
                                size = 8.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (tunnels.isEmpty()) "مرکز مدیریت تانل‌های ایران-خارج" else "$activeCount تانل فعال از مجموع ${tunnels.size}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.textPrimary
                            )
                        }

                        if (tunnels.isNotEmpty()) {
                            StatusPill(
                                text = if (onlineCount > 0) "$onlineCount متصل" else "پایش زنده",
                                level = if (onlineCount > 0) StatusLevel.Ok else StatusLevel.Neutral
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 3-Column Micro Bento Stat Pods
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TunnelBentoTile(
                            title = "تعداد کل",
                            value = "${tunnels.size}",
                            unit = "Tunnels",
                            color = Ds.accent,
                            modifier = Modifier.weight(1f)
                        )
                        TunnelBentoTile(
                            title = "استقرار خودکار",
                            value = "$autoSyncCount",
                            unit = "AutoSync",
                            color = Ds.violet,
                            modifier = Modifier.weight(1f)
                        )
                        TunnelBentoTile(
                            title = "متصل و آنلاین",
                            value = "$onlineCount",
                            unit = "Connected",
                            color = if (onlineCount > 0) Ds.ok else Ds.warn,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 2. Supported Tunnel Protocol Chips ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Supported Core Engines (10 Cores):",
                        fontSize = 11.sp,
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
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Ds.surfaceLow,
                                border = BorderStroke(1.dp, Ds.hairline),
                                modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(Modifier.size(5.dp).background(Ds.accent, CircleShape))
                                    Text(c.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Step-by-Step Tunnel Setup Guide (Permanent Expandable Bento Card) ──
            item {
                StepByStepTunnelGuideCard(
                    t = t,
                    isExpanded = showGuide,
                    serverCount = servers.size,
                    onToggleExpand = { showGuide = !showGuide },
                    onAddTunnel = {
                        editingTunnel = null
                        showForm = true
                    }
                )
            }

            // ── 4. Search & Filter Bar (Shown when multiple tunnels exist) ──
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

            // ── 5. Empty State or Bento Tunnel Deck ──
            if (tunnels.isEmpty()) {
                item {
                    EmptyState(
                        title = "هنوز تانلی تعریف نشده است",
                        hint = "با تعریف تانل جدید، بسترهای امن BackPack، Paqet، Narnia، Backhaul، Rathole و غیره را به صورت یک‌کلیکه بین دو سرور برقرار کنید.",
                        icon = Icons.Rounded.SwapHoriz,
                        radar = false,
                        actionLabel = t.addTunnel,
                        onAction = {
                            editingTunnel = null
                            showForm = true
                        }
                    )
                }
            } else {
                items(filteredTunnels, key = { it.id }) { tunnel ->
                    val isDeploying = isDeployingMap[tunnel.id] ?: false
                    val isTesting = isTestingMap[tunnel.id] ?: false
                    val isOnline = tunnel.lastStatus == 1

                    TunnelBentoCard(
                        tunnel = tunnel,
                        isOnline = isOnline,
                        isTesting = isTesting,
                        isOperating = isDeploying,
                        t = t,
                        onToggleEnabled = { enabled ->
                            val updated = tunnels.map {
                                if (it.id == tunnel.id) it.copy(isEnabled = enabled) else it
                            }
                            tunnels = updated
                            Prefs.saveTunnels(ctx, updated)

                            scope.launch {
                                TunnelEngine.controlRemoteTunnel(ctx, tunnel, if (enabled) "start" else "stop")
                            }
                        },
                        onViewCode = { viewingCodeTunnel = tunnel },
                        onAutoDeploy = {
                            isDeployingMap = isDeployingMap + (tunnel.id to true)
                            scope.launch {
                                val result = TunnelEngine.autoDeployTunnel(ctx, tunnel)
                                isDeployingMap = isDeployingMap + (tunnel.id to false)
                                deployResultDialog = result
                                refreshTunnels()
                            }
                        },
                        onTestLatency = {
                            isTestingMap = isTestingMap + (tunnel.id to true)
                            scope.launch {
                                val res = TunnelEngine.testTunnel(tunnel)
                                tunnel.lastStatus = if (res.first) 1 else 0
                                tunnel.lastLatencyMs = res.second
                                tunnel.lastChecked = System.currentTimeMillis()
                                Prefs.saveTunnels(ctx, tunnels)
                                isTestingMap = isTestingMap + (tunnel.id to false)
                                refreshTunnels()
                                Toast.makeText(
                                    ctx,
                                    if (res.first) "اتصال موفق! تاخیر: ${res.second}ms" else "عدم پاسخگویی تانل",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onEdit = {
                            editingTunnel = tunnel
                            showForm = true
                        },
                        onDelete = { deletingTunnel = tunnel }
                    )
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // ── Dialogs & Modals ──

    if (showForm) {
        TunnelFormDialog(
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
        ViewTunnelCodeDialog(
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
                        ModernCard(padding = 10.dp, cornerRadius = 12.dp) {
                            Text("سرور ایران (${res.serverName}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ds.textPrimary)
                            Text(res.message, fontSize = 11.sp, color = if (res.success) Ds.ok else Ds.danger)
                        }
                    }

                    r.foreignResult?.let { res ->
                        ModernCard(padding = 10.dp, cornerRadius = 12.dp) {
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
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Tunnel Bento Tile
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TunnelBentoTile(
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
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Column {
            Text(title, fontSize = 10.sp, color = Ds.textTertiary, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Telemetry,
                    color = color
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    fontSize = 9.5.sp,
                    color = Ds.textTertiary,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Step-by-Step Tunnel Setup Bento Guide
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepByStepTunnelGuideCard(
    t: Str,
    isExpanded: Boolean,
    serverCount: Int,
    onToggleExpand: () -> Unit,
    onAddTunnel: () -> Unit
) {
    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp,
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(Ds.hairline, Ds.accent.copy(alpha = 0.35f), Ds.hairline)))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Permanent Header Row (Never vanishes)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.HelpOutline,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 32.dp,
                        iconSize = 17.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(t.tunnelGuideHeader, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Smite-Style 1-Click Dual-Node Deployment", fontSize = 10.5.sp, color = Ds.textTertiary)
                    }
                }
                SoftButton(
                    text = if (isExpanded) t.hideGuide else t.showGuide,
                    icon = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    onClick = onToggleExpand,
                    modifier = Modifier.height(34.dp)
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

                    // Step 1: Connect Iran & Foreign Servers
                    TunnelStepPod(
                        stepNum = "1",
                        title = t.tunnelAddStep1Title,
                        desc = t.tunnelAddStep1Desc
                    )

                    // Step 2: Select Core & Ports
                    TunnelStepPod(
                        stepNum = "2",
                        title = t.tunnelAddStep2Title,
                        desc = t.tunnelAddStep2Desc
                    )

                    // Step 3: Zero-Touch Auto-Deploy
                    TunnelStepPod(
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
}

@Composable
private fun TunnelStepPod(
    stepNum: String,
    title: String,
    desc: String,
    content: (@Composable () -> Unit)? = null
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            Text(desc, fontSize = 11.sp, color = Ds.textSecondary, lineHeight = 16.sp)
            if (content != null) {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Cyber Bento Tunnel Card
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun TunnelBentoCard(
    tunnel: TunnelConfig,
    isOnline: Boolean,
    isTesting: Boolean,
    isOperating: Boolean,
    t: Str,
    onToggleEnabled: (Boolean) -> Unit,
    onViewCode: () -> Unit,
    onAutoDeploy: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Protocol Badge + Name + Latency + Switch
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Ds.surfaceElevated)
                            .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            tunnel.core.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.accent
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tunnel.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Ds.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.ok
                            )
                        }
                    } else {
                        StatusPill(text = t.offline, level = StatusLevel.Danger)
                    }
                    Spacer(Modifier.width(6.dp))
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
            }

            // Dual Node Visual Bridge Flow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ds.surfaceLow)
                    .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Iran Node
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🇮🇷", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text("Iran Relay", fontSize = 10.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${tunnel.iranHost.ifBlank { "0.0.0.0" }}:${tunnel.iranPort}",
                            fontSize = 11.5.sp,
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Center Animated Bridge Indicator
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    ) {
                        Text(
                            tunnel.transport.name,
                            fontSize = 9.sp,
                            fontFamily = Telemetry,
                            color = Ds.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(4.dp).background(Ds.accent, CircleShape))
                            Icon(
                                imageVector = Icons.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Ds.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Box(Modifier.size(4.dp).background(Ds.accent, CircleShape))
                        }
                    }

                    // Foreign Node
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Foreign Node", fontSize = 10.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Text("🌐", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${tunnel.foreignHost.ifBlank { "Remote" }}:${tunnel.foreignPort}",
                            fontSize = 11.5.sp,
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Multi-Port Forwarding Tag (if specified)
            if (tunnel.multiPorts.isNotBlank()) {
                Text(
                    "Forwarding: ${tunnel.multiPorts}",
                    fontSize = 10.sp,
                    fontFamily = Telemetry,
                    color = Ds.textTertiary
                )
            }

            Hairline()

            // Actions Row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftButton(
                        text = "Script & Docker",
                        icon = Icons.Rounded.Terminal,
                        onClick = onViewCode
                    )

                    if (tunnel.autoSync) {
                        PrimaryButton(
                            text = if (isOperating) "Deploying…" else "Auto-Deploy",
                            icon = Icons.Rounded.CloudSync,
                            loading = isOperating,
                            onClick = onAutoDeploy
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CircleIconButton(
                        icon = Icons.Rounded.Speed,
                        contentDescription = "Test Latency",
                        tint = Ds.accent,
                        size = 28.dp,
                        onClick = onTestLatency
                    )
                    CircleIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        tint = Ds.textSecondary,
                        size = 28.dp,
                        onClick = onEdit
                    )
                    CircleIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Ds.danger,
                        size = 28.dp,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

// ── Tunnel Form Dialog ──────────────────────────────────────────────────────

@Composable
private fun TunnelFormDialog(
    t: Str,
    existing: TunnelConfig?,
    onDismiss: () -> Unit,
    onSave: (TunnelConfig) -> Unit
) {
    val ctx = LocalContext.current
    val servers = remember { Prefs.loadServers(ctx) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKPACK) }
    var transport by remember { mutableStateOf(existing?.transport ?: TunnelTransport.STEALTH) }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var multiPorts by remember { mutableStateOf(existing?.multiPorts ?: "443:8443, 2096:2096") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    var token by remember { mutableStateOf(existing?.token ?: TunnelEngine.generateRandomToken(24)) }
    var preset by remember { mutableStateOf(existing?.preset ?: "turbo") }
    var kcpMode by remember { mutableStateOf(existing?.kcpMode ?: "fast") }
    var encryption by remember { mutableStateOf(existing?.encryption ?: "aes-128-gcm") }
    var spoofSrcIp by remember { mutableStateOf(existing?.spoofSrcIp ?: "1.1.1.1") }
    var spoofPeerIp by remember { mutableStateOf(existing?.spoofPeerIp ?: "8.8.8.8") }
    var virtualIpIran by remember { mutableStateOf(existing?.virtualIpIran ?: "10.200.200.2") }
    var virtualIpKharej by remember { mutableStateOf(existing?.virtualIpKharej ?: "10.200.200.1") }
    var mtu by remember { mutableStateOf((existing?.mtu ?: 1350).toString()) }
    var acceptUdp by remember { mutableStateOf(existing?.acceptUdp ?: true) }
    var proxyProtocol by remember { mutableStateOf(existing?.proxyProtocol ?: false) }
    var autoSync by remember { mutableStateOf(existing?.autoSync ?: true) }
    var iranServerId by remember { mutableStateOf<Long?>(existing?.iranServerId) }
    var foreignServerId by remember { mutableStateOf<Long?>(existing?.foreignServerId) }

    // Auto-update transport options when core changes
    LaunchedEffect(core) {
        when (core) {
            TunnelCore.BACKPACK -> if (transport !in listOf(TunnelTransport.STEALTH, TunnelTransport.PCK, TunnelTransport.KCP_FEC, TunnelTransport.TCP, TunnelTransport.UDP)) transport = TunnelTransport.STEALTH
            TunnelCore.PAQET -> transport = TunnelTransport.RAW_KCP
            TunnelCore.NARNIA -> transport = TunnelTransport.ICMP_CHACHA
            TunnelCore.SPOOF_TUNNEL -> if (transport !in listOf(TunnelTransport.IP_SPOOF_UDP, TunnelTransport.IP_SPOOF_ICMP)) transport = TunnelTransport.IP_SPOOF_UDP
            TunnelCore.BACKHAUL -> if (transport !in listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.WSMUX, TunnelTransport.TCPMUX)) transport = TunnelTransport.TCP
            TunnelCore.RATHOLE -> transport = TunnelTransport.TCP
            TunnelCore.GOST -> if (transport !in listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.GRPC, TunnelTransport.TCPMUX, TunnelTransport.UDP)) transport = TunnelTransport.TCP
            TunnelCore.CHISEL -> transport = TunnelTransport.WS
            TunnelCore.FRP -> transport = TunnelTransport.TCP
            TunnelCore.IPTABLES -> transport = TunnelTransport.TCP
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(450.dp).imePadding()
            ) {
                // Core Engine Selection
                item {
                    Text("1. هسته تانل (Tunnel Core Engine)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                    Spacer(Modifier.height(4.dp))
                    FilterChipRow(
                        items = TunnelCore.values().map { it.displayName },
                        selectedIndex = TunnelCore.values().indexOf(core),
                        onSelect = { core = TunnelCore.values()[it] }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(core.description, fontSize = 10.5.sp, color = Ds.textSecondary, lineHeight = 15.sp)
                }

                item { Hairline() }

                // Basic Identification
                item {
                    InputField(
                        value = name,
                        onValueChange = { name = it },
                        label = "نام دلخواه تانل",
                        placeholder = "مثال: تانل تهران به فرانکفورت (BackPack)"
                    )
                }

                // Quick Node Selectors (if registered servers exist)
                if (servers.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("انتخاب سریع سرورهای دیدبان:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ds.textTertiary)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                servers.forEach { s ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (iranServerId == s.id || foreignServerId == s.id) Ds.accentDim else Ds.surfaceLow,
                                        border = BorderStroke(1.dp, if (iranServerId == s.id || foreignServerId == s.id) Ds.accent else Ds.hairline),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                            if (iranHost.isBlank()) {
                                                iranHost = s.host
                                                iranServerId = s.id
                                            } else if (foreignHost.isBlank()) {
                                                foreignHost = s.host
                                                foreignServerId = s.id
                                            } else {
                                                iranHost = s.host
                                                iranServerId = s.id
                                            }
                                        }
                                    ) {
                                        Text(
                                            "${s.name} (${s.host})",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            color = Ds.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    InputField(
                        value = iranHost,
                        onValueChange = { iranHost = it },
                        label = "آدرس سرور ایران (Relay IP / Domain)",
                        placeholder = "IP سرور ایران"
                    )
                }

                item {
                    InputField(
                        value = foreignHost,
                        onValueChange = { foreignHost = it },
                        label = "آدرس سرور خارج (Upstream IP / Domain)",
                        placeholder = "IP سرور خارج"
                    )
                }

                item {
                    InputField(
                        value = multiPorts,
                        onValueChange = { multiPorts = it },
                        label = "نگاشت پورت‌ها (ایران:خارج)",
                        placeholder = "e.g. 443:8443, 2096:2096, 80:8080"
                    )
                }

                item {
                    InputField(
                        value = corePort,
                        onValueChange = { corePort = it },
                        label = "پورت ارتباطی هسته تانل (Core Port)",
                        placeholder = "3080"
                    )
                }

                // Security Token with Generator
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("توکن امنیتی (PSK Token)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                            Text(
                                "تولید توکن قوی",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.accent,
                                modifier = Modifier.clickable {
                                    token = TunnelEngine.generateRandomToken(24)
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MonoTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = "",
                            placeholder = "Encryption Key / Token"
                        )
                    }
                }

                item { Hairline() }

                // Core Specific Controls
                when (core) {
                    TunnelCore.BACKPACK -> {
                        item {
                            Text("پروتکل انتقال BackPack:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            Spacer(Modifier.height(4.dp))
                            val bpTransports = listOf(TunnelTransport.STEALTH, TunnelTransport.PCK, TunnelTransport.KCP_FEC, TunnelTransport.TCP, TunnelTransport.UDP)
                            FilterChipRow(
                                items = bpTransports.map { it.displayName },
                                selectedIndex = bpTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = bpTransports[it] }
                            )
                        }

                        item {
                            Text("پریست سرعت (Preset):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                            val presets = listOf("turbo", "balance", "aggressive", "gaming")
                            FilterChipRow(
                                items = presets,
                                selectedIndex = presets.indexOf(preset).coerceAtLeast(0),
                                onSelect = { preset = presets[it] }
                            )
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("انتقال ترافیک UDP", fontSize = 12.sp, color = Ds.textPrimary)
                                Switch(
                                    checked = acceptUdp,
                                    onCheckedChange = { acceptUdp = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                                )
                            }
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("پشتیبانی از Proxy Protocol v2", fontSize = 12.sp, color = Ds.textPrimary)
                                Switch(
                                    checked = proxyProtocol,
                                    onCheckedChange = { proxyProtocol = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                                )
                            }
                        }
                    }

                    TunnelCore.PAQET -> {
                        item {
                            Text("مود KCP در Paqet:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val kcpModes = listOf("fast", "fast2", "fast3", "normal")
                            FilterChipRow(
                                items = kcpModes,
                                selectedIndex = kcpModes.indexOf(kcpMode).coerceAtLeast(0),
                                onSelect = { kcpMode = kcpModes[it] }
                            )
                        }

                        item {
                            Text("نوع رمزنگاری (Encryption):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                            val encs = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "none")
                            FilterChipRow(
                                items = encs,
                                selectedIndex = encs.indexOf(encryption).coerceAtLeast(0),
                                onSelect = { encryption = encs[it] }
                            )
                        }

                        item {
                            InputField(value = mtu, onValueChange = { mtu = it }, label = "MTU سایز پکت‌ها", placeholder = "1350")
                        }
                    }

                    TunnelCore.NARNIA -> {
                        item {
                            InputField(value = virtualIpIran, onValueChange = { virtualIpIran = it }, label = "IP مجازی سرور ایران", placeholder = "10.200.200.2")
                        }
                        item {
                            InputField(value = virtualIpKharej, onValueChange = { virtualIpKharej = it }, label = "IP مجازی سرور خارج", placeholder = "10.200.200.1")
                        }
                        item {
                            InputField(value = mtu, onValueChange = { mtu = it }, label = "MTU تانل ICMP", placeholder = "1350")
                        }
                    }

                    TunnelCore.SPOOF_TUNNEL -> {
                        item {
                            Text("پروتکل جعل IP:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val spoofTransports = listOf(TunnelTransport.IP_SPOOF_UDP, TunnelTransport.IP_SPOOF_ICMP)
                            FilterChipRow(
                                items = spoofTransports.map { it.displayName },
                                selectedIndex = spoofTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = spoofTransports[it] }
                            )
                        }

                        item {
                            InputField(value = spoofSrcIp, onValueChange = { spoofSrcIp = it }, label = "IP جعلی مبدا (Spoofed Source IP)", placeholder = "1.1.1.1")
                        }
                        item {
                            InputField(value = spoofPeerIp, onValueChange = { spoofPeerIp = it }, label = "IP جعلی مقصد (Spoofed Peer IP)", placeholder = "8.8.8.8")
                        }
                    }

                    TunnelCore.BACKHAUL, TunnelCore.GOST -> {
                        item {
                            Text("پروتکل انتقال:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val genericTransports = listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.WSMUX, TunnelTransport.TCPMUX, TunnelTransport.GRPC)
                            FilterChipRow(
                                items = genericTransports.map { it.displayName },
                                selectedIndex = genericTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = genericTransports[it] }
                            )
                        }
                    }

                    else -> {}
                }

                item { Hairline() }

                // Auto-Deploy Switch
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("استقرار خودکار دیدبان (Zero-Touch Auto-Deploy)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("نصب خودکار سرویس و اجرای تانل روی هر دو سرور با یک کلیک", fontSize = 10.5.sp, color = Ds.textTertiary)
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = t.save,
                onClick = {
                    if (name.isNotBlank()) {
                        val firstPort = run {
                            val tok = multiPorts.split(',', ';', ' ', '\n', '\t').map { it.trim() }.firstOrNull { it.isNotEmpty() }
                            if (tok != null && (tok.contains(':') || tok.contains('='))) {
                                val delim = if (tok.contains(':')) ':' else '='
                                val parts = tok.split(delim)
                                val ip = parts[0].trim().toIntOrNull() ?: 443
                                val fp = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ip
                                PortMapping(ip, fp)
                            } else {
                                val p = tok?.toIntOrNull() ?: 443
                                PortMapping(p, p)
                            }
                        }
                        val newTun = existing?.apply {
                            this.name = name.trim()
                            this.core = core
                            this.transport = transport
                            this.iranHost = iranHost.trim()
                            this.iranPort = firstPort.iranPort
                            this.foreignHost = foreignHost.trim()
                            this.foreignPort = firstPort.foreignPort
                            this.corePort = corePort.toIntOrNull() ?: 3080
                            this.token = token.trim()
                            this.preset = preset
                            this.kcpMode = kcpMode
                            this.encryption = encryption
                            this.spoofSrcIp = spoofSrcIp.trim()
                            this.spoofPeerIp = spoofPeerIp.trim()
                            this.virtualIpIran = virtualIpIran.trim()
                            this.virtualIpKharej = virtualIpKharej.trim()
                            this.mtu = mtu.toIntOrNull() ?: 1350
                            this.acceptUdp = acceptUdp
                            this.proxyProtocol = proxyProtocol
                            this.multiPorts = multiPorts.trim()
                            this.autoSync = autoSync
                            this.iranServerId = iranServerId
                            this.foreignServerId = foreignServerId
                        } ?: TunnelConfig(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            core = core,
                            transport = transport,
                            iranHost = iranHost.trim(),
                            iranPort = firstPort.iranPort,
                            foreignHost = foreignHost.trim(),
                            foreignPort = firstPort.foreignPort,
                            corePort = corePort.toIntOrNull() ?: 3080,
                            token = token.trim(),
                            preset = preset,
                            kcpMode = kcpMode,
                            encryption = encryption,
                            spoofSrcIp = spoofSrcIp.trim(),
                            spoofPeerIp = spoofPeerIp.trim(),
                            virtualIpIran = virtualIpIran.trim(),
                            virtualIpKharej = virtualIpKharej.trim(),
                            mtu = mtu.toIntOrNull() ?: 1350,
                            acceptUdp = acceptUdp,
                            proxyProtocol = proxyProtocol,
                            multiPorts = multiPorts.trim(),
                            autoSync = autoSync,
                            iranServerId = iranServerId,
                            foreignServerId = foreignServerId
                        )
                        onSave(newTun)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

// ── View Codes & Docker Dialog ──────────────────────────────────────────────

@Composable
private fun ViewTunnelCodeDialog(
    t: Str,
    tunnel: TunnelConfig,
    onDismiss: () -> Unit
) {
    val code = remember(tunnel) { TunnelEngine.generateCode(tunnel) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Iran, 1: Foreign, 2: Docker

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tunnel.core.displayName} Commands & Docker", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedControl(
                    items = listOf("🇮🇷 Iran", "🌍 Foreign", "🐳 Docker"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )

                val contentToShow = when (selectedTab) {
                    0 -> code.iranInstallCommand
                    1 -> code.foreignInstallCommand
                    else -> "=== docker-compose-iran.yml ===\n${code.dockerComposeIran}\n\n=== docker-compose-foreign.yml ===\n${code.dockerComposeForeign}"
                }

                TerminalBox(
                    command = contentToShow,
                    title = when (selectedTab) { 0 -> "Iran Command"; 1 -> "Foreign Command"; else -> "Docker Compose" }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.close) }
        }
    )
}
