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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TunnelScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var tunnels by remember { mutableStateOf<List<TunnelConfig>>(Prefs.loadTunnels(ctx)) }
    var servers by remember { mutableStateOf<List<ServerConfig>>(Prefs.loadServers(ctx)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var deletingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var viewCodeTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var testingTunnelId by remember { mutableStateOf<Long?>(null) }
    var operatingTunnelId by remember { mutableStateOf<Long?>(null) }
    var showGuide by remember { mutableStateOf(tunnels.isEmpty()) }
    var selectedFilterCore by remember { mutableStateOf<TunnelCore?>(null) }

    fun save() {
        Prefs.saveTunnels(ctx, tunnels)
        tunnels = tunnels.toList()
    }

    // Auto health check loop
    LaunchedEffect(Unit) {
        while (true) {
            for (tun in tunnels) {
                if (tun.isEnabled && (tun.iranHost.isNotBlank() || tun.foreignHost.isNotBlank())) {
                    val (ok, lat) = TunnelEngine.testTunnel(tun)
                    tun.lastStatus = if (ok) 1 else 0
                    tun.lastLatencyMs = lat
                    tun.lastChecked = System.currentTimeMillis()
                }
            }
            save()
            delay(25_000)
        }
    }

    val totalTunnels = tunnels.size
    val activeTunnels = tunnels.count { it.lastStatus == 1 }
    val uniqueCores = tunnels.map { it.core }.distinct().size

    val filteredTunnels = remember(tunnels, selectedFilterCore) {
        if (selectedFilterCore == null) tunnels else tunnels.filter { it.core == selectedFilterCore }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Page Header ──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        icon = Icons.Rounded.SwapHoriz,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.tunnelsHub, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Dual-Node Iran ⇄ Foreign Bridge", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                PrimaryButton(
                    text = t.addTunnel,
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        // ── 2. Cyber Bento Bridge Vitality Hero ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(
                            color = if (activeTunnels > 0) Ds.ok else if (totalTunnels == 0) Ds.textTertiary else Ds.danger,
                            size = 8.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                totalTunnels == 0 -> t.navTunnels
                                activeTunnels == totalTunnels -> "تمام پل‌های تانل فعال هستند"
                                else -> "$activeTunnels از $totalTunnels تانل متصل است"
                            },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalTunnels == 0) Ds.textPrimary else if (activeTunnels > 0) Ds.ok else Ds.danger
                        )
                    }

                    PrimaryButton(
                        text = t.addTunnel,
                        icon = Icons.Rounded.Add,
                        onClick = { showAddDialog = true },
                        modifier = Modifier.height(36.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 3-Column Micro Bento Stat Pods
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TunnelBentoTile(
                        title = "کل تانل‌ها",
                        value = "$totalTunnels",
                        unit = "Bridges",
                        color = Ds.accent,
                        modifier = Modifier.weight(1f)
                    )
                    TunnelBentoTile(
                        title = "پل‌های متصل",
                        value = "$activeTunnels",
                        unit = "Online",
                        color = Ds.ok,
                        modifier = Modifier.weight(1f)
                    )
                    TunnelBentoTile(
                        title = "پروتکل‌های فعال",
                        value = "$uniqueCores",
                        unit = "Cores",
                        color = Ds.violet,
                        modifier = Modifier.weight(1f)
                    )
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
                onAddTunnel = { showAddDialog = true }
            )
        }

        // ── 4. Engine Filter Chips Bar (Shown when tunnels exist) ──
        if (tunnels.isNotEmpty()) {
            item {
                FilterChipRow(
                    items = listOf("All (${tunnels.size})") + listOf(
                        TunnelCore.BACKPACK,
                        TunnelCore.PAQET,
                        TunnelCore.NARNIA,
                        TunnelCore.SPOOF_TUNNEL,
                        TunnelCore.BACKHAUL,
                        TunnelCore.RATHOLE,
                        TunnelCore.GOST
                    ).map { it.displayName },
                    selectedIndex = if (selectedFilterCore == null) 0 else {
                        val idx = listOf(
                            TunnelCore.BACKPACK,
                            TunnelCore.PAQET,
                            TunnelCore.NARNIA,
                            TunnelCore.SPOOF_TUNNEL,
                            TunnelCore.BACKHAUL,
                            TunnelCore.RATHOLE,
                            TunnelCore.GOST
                        ).indexOf(selectedFilterCore)
                        if (idx >= 0) idx + 1 else 0
                    },
                    onSelect = { idx ->
                        selectedFilterCore = if (idx == 0) null else {
                            listOf(
                                TunnelCore.BACKPACK,
                                TunnelCore.PAQET,
                                TunnelCore.NARNIA,
                                TunnelCore.SPOOF_TUNNEL,
                                TunnelCore.BACKHAUL,
                                TunnelCore.RATHOLE,
                                TunnelCore.GOST
                            ).getOrNull(idx - 1)
                        }
                    }
                )
            }
        }

        // ── 5. Empty State or Cyber Bento Tunnel Cards ──
        if (filteredTunnels.isEmpty()) {
            item {
                EmptyState(
                    title = "هنوز تانلی راه‌اندازی نشده است",
                    hint = "با راهنمای بالا، اولین پل ارتباطی پرسرعت و ضد فیلتر خود را بین سرور ایران و خارج برقرار کنید.",
                    icon = Icons.Rounded.SwapHoriz,
                    radar = false,
                    actionLabel = t.addTunnel,
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(filteredTunnels, key = { it.id }) { tunnel ->
                val isOnline = tunnel.lastStatus == 1
                val isTesting = testingTunnelId == tunnel.id
                val isOperating = operatingTunnelId == tunnel.id

                TunnelBentoCard(
                    tunnel = tunnel,
                    isOnline = isOnline,
                    isTesting = isTesting,
                    isOperating = isOperating,
                    t = t,
                    onToggleEnabled = {
                        tunnel.isEnabled = it
                        save()
                    },
                    onViewCode = { viewCodeTunnel = tunnel },
                    onAutoDeploy = {
                        operatingTunnelId = tunnel.id
                        scope.launch {
                            val res = TunnelEngine.autoDeployTunnel(ctx, tunnel)
                            if (res.overallSuccess) {
                                Toast.makeText(ctx, "Tunnel deployed to agents successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "Auto-deploy: ${res.summaryMessage}", Toast.LENGTH_SHORT).show()
                            }
                            operatingTunnelId = null
                        }
                    },
                    onTestLatency = {
                        testingTunnelId = tunnel.id
                        scope.launch {
                            val (ok, lat) = TunnelEngine.testTunnel(tunnel)
                            tunnel.lastStatus = if (ok) 1 else 0
                            tunnel.lastLatencyMs = lat
                            tunnel.lastChecked = System.currentTimeMillis()
                            save()
                            testingTunnelId = null
                        }
                    },
                    onEdit = { editingTunnel = tunnel },
                    onDelete = { deletingTunnel = tunnel }
                )
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }

    // ── Add / Edit Tunnel Dialog ──
    if (showAddDialog || editingTunnel != null) {
        TunnelFormDialog(
            t = t,
            existing = editingTunnel,
            onDismiss = {
                showAddDialog = false
                editingTunnel = null
            },
            onSave = { updated ->
                val list = tunnels.toMutableList()
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    list[idx] = updated
                } else {
                    list.add(updated)
                }
                tunnels = list
                save()
                showAddDialog = false
                editingTunnel = null
            }
        )
    }

    // ── View Codes & Docker Dialog ──
    viewCodeTunnel?.let { tunnel ->
        ViewTunnelCodeDialog(
            t = t,
            tunnel = tunnel,
            onDismiss = { viewCodeTunnel = null }
        )
    }

    // ── Delete Confirmation Dialog ──
    deletingTunnel?.let { tunnel ->
        AlertDialog(
            onDismissRequest = { deletingTunnel = null },
            title = { Text(t.deleteTunnel, fontWeight = FontWeight.Bold, color = Ds.danger) },
            text = { Text("آیا از حذف تانل «${tunnel.name}» اطمینان دارید؟", color = Ds.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    tunnels = tunnels.filterNot { it.id == tunnel.id }
                    save()
                    deletingTunnel = null
                }) {
                    Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTunnel = null }) {
                    Text(t.cancel, color = Ds.textSecondary)
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Cyber Bento Metric Tile for Tunnels
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
                        Text("Dual-Node Setup (3 Easy Steps)", fontSize = 10.5.sp, color = Ds.textTertiary)
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
                    ) {
                        if (serverCount < 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ds.warnDim)
                                    .border(BorderStroke(1.dp, Ds.warn.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💡", fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "نکته: برای تانل حداقل ۲ سرور (ایران و خارج) نیاز دارید. هم‌اکنون $serverCount سرور ثبت شده است.",
                                        fontSize = 11.sp,
                                        color = Ds.warn
                                    )
                                }
                            }
                        }
                    }

                    // Step 2: Protocol & Port Configuration
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
                            modifier = Modifier.fillMaxWidth().height(38.dp)
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
                        onClick = onViewCode,
                        modifier = Modifier.height(32.dp)
                    )

                    if (tunnel.autoSync) {
                        PrimaryButton(
                            text = if (isOperating) "Deploying…" else "Auto-Deploy",
                            icon = Icons.Rounded.CloudSync,
                            loading = isOperating,
                            onClick = onAutoDeploy,
                            modifier = Modifier.height(32.dp)
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
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKPACK) }
    var transport by remember { mutableStateOf(existing?.transport ?: TunnelTransport.TCP) }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var multiPorts by remember { mutableStateOf(existing?.multiPorts ?: "443:8443, 2096:2096") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    var token by remember { mutableStateOf(existing?.token ?: TunnelEngine.generateRandomToken(24)) }
    var preset by remember { mutableStateOf(existing?.preset ?: "balanced") }
    var kcpMode by remember { mutableStateOf(existing?.kcpMode ?: "fast") }
    var encryption by remember { mutableStateOf(existing?.encryption ?: "chacha20-poly1305") }
    var autoSync by remember { mutableStateOf(existing?.autoSync ?: true) }

    val servers = remember { Prefs.loadServers(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp).imePadding()
            ) {
                item {
                    Text("Tunnel Core Engine", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    FilterChipRow(
                        items = TunnelCore.values().map { it.displayName },
                        selectedIndex = TunnelCore.values().indexOf(core),
                        onSelect = { core = TunnelCore.values()[it] }
                    )
                }

                item { InputField(value = name, onValueChange = { name = it }, label = "Tunnel Name", placeholder = "e.g. Tehran-Frankfurt BackPack") }
                item { InputField(value = iranHost, onValueChange = { iranHost = it }, label = "Iran Node Host / IP", placeholder = "Iran Server IP or Domain") }
                item { InputField(value = foreignHost, onValueChange = { foreignHost = it }, label = "Foreign Node Host / IP", placeholder = "Foreign Server IP or Domain") }
                item { InputField(value = multiPorts, onValueChange = { multiPorts = it }, label = "Port Forwarding Map", placeholder = "e.g. 443:8443, 2096:2096") }
                item { InputField(value = corePort, onValueChange = { corePort = it }, label = "Core Tunnel Port", placeholder = "3080") }
                item { InputField(value = token, onValueChange = { token = it }, label = "Security Token / PSK", placeholder = "Encryption Key") }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Agent Zero-Touch Auto-Deploy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("Automatically installs and runs the tunnel on Didban agents", fontSize = 10.5.sp, color = Ds.textTertiary)
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
                            val token = multiPorts.split(',', ';', ' ', '\n', '\t').map { it.trim() }.firstOrNull { it.isNotEmpty() }
                            if (token != null && (token.contains(':') || token.contains('='))) {
                                val delim = if (token.contains(':')) ':' else '='
                                val parts = token.split(delim)
                                val ip = parts[0].trim().toIntOrNull() ?: 443
                                val fp = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ip
                                PortMapping(ip, fp)
                            } else {
                                val p = token?.toIntOrNull() ?: 443
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
                            this.multiPorts = multiPorts.trim()
                            this.autoSync = autoSync
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
                            multiPorts = multiPorts.trim(),
                            autoSync = autoSync
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
