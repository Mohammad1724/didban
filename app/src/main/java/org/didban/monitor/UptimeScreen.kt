@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.Context
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UptimeScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var targets by remember { mutableStateOf<List<UptimeTarget>>(Prefs.loadUptimeTargets(ctx)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<UptimeTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<UptimeTarget?>(null) }
    var expandedIncidentsTargetId by remember { mutableStateOf<Long?>(null) }
    var testingTargetId by remember { mutableStateOf<Long?>(null) }
    var showGuide by remember { mutableStateOf(targets.isEmpty()) }
    var selectedFilterType by remember { mutableStateOf<String?>(null) }

    BackHandler(
        enabled = showAddDialog || editTarget != null || deleteTarget != null || expandedIncidentsTargetId != null
    ) {
        showAddDialog = false
        editTarget = null
        deleteTarget = null
        expandedIncidentsTargetId = null
    }

    fun saveTargets() {
        Prefs.saveUptimeTargets(ctx, targets)
        targets = targets.toList()
    }

    // Auto health check loop
    LaunchedEffect(Unit) {
        while (true) {
            for (target in targets) {
                if (!target.isPaused) {
                    try {
                        UptimeEngine.checkTarget(target, ctx)
                    } catch (_: Exception) {}
                }
            }
            saveTargets()
            delay(15_000)
        }
    }

    val upCount = targets.count { it.lastStatus == 1 }
    val downCount = targets.count { it.lastStatus == 0 }
    val pausedCount = targets.count { it.isPaused }
    val totalCount = targets.size
    val avgUptime = if (targets.isEmpty()) 100f else targets.map { it.uptimePct }.average().toFloat()

    val filteredTargets = remember(targets, selectedFilterType) {
        if (selectedFilterType == null) targets else targets.filter { it.type.equals(selectedFilterType, ignoreCase = true) }
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBadge(
                        icon = Icons.Rounded.Timer,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 36.dp,
                        iconSize = 18.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            t.uptimeMonitoring,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "24/7 Heartbeat & SLA Watch",
                            fontSize = 11.sp,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    text = t.addMonitor,
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true }
                )
            }
        }

        // ── 2. Fleet SLA Hero Bento ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        PulseDot(
                            color = if (downCount > 0) Ds.danger else if (totalCount == 0) Ds.textTertiary else Ds.ok,
                            size = 8.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                totalCount == 0 -> "پایش پایداری آماده راه‌اندازی"
                                downCount > 0 -> "$downCount مانیتور با قطعی مواجه شده است"
                                else -> "تمام $totalCount سرویس فعال و دردسترس هستند"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (downCount > 0) Ds.danger else if (totalCount == 0) Ds.textPrimary else Ds.ok,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    StatusPill(
                        text = if (totalCount > 0) "$upCount آنلاین" else "پایش زنده",
                        level = if (downCount > 0) StatusLevel.Danger else if (totalCount > 0) StatusLevel.Ok else StatusLevel.Neutral
                    )
                }

                Spacer(Modifier.height(14.dp))

                // 3-Column Micro Bento Stat Pods
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UptimeBentoTile(
                        title = "میانگین SLA",
                        value = if (targets.isEmpty()) "100" else "%.1f".format(Locale.US, avgUptime),
                        unit = "%",
                        color = if (avgUptime >= 98f) Ds.ok else if (avgUptime >= 90f) Ds.warn else Ds.danger,
                        modifier = Modifier.weight(1f)
                    )
                    UptimeBentoTile(
                        title = "سرویس‌های آنلاین",
                        value = "$upCount",
                        unit = "Up",
                        color = Ds.ok,
                        modifier = Modifier.weight(1f)
                    )
                    UptimeBentoTile(
                        title = "قطعی / ناموفق",
                        value = "$downCount",
                        unit = "Down",
                        color = if (downCount > 0) Ds.danger else Ds.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 3. Step-by-Step SLA & Uptime Guide (Permanent Expandable Bento Card) ──
        item {
            StepByStepUptimeGuideCard(
                t = t,
                isExpanded = showGuide,
                totalMonitors = totalCount,
                onToggleExpand = { showGuide = !showGuide },
                onAddMonitor = { showAddDialog = true }
            )
        }

        // ── 4. Protocol Filter Chips Bar (Shown when targets exist) ──
        if (targets.isNotEmpty()) {
            item {
                val types = listOf("All (${targets.size})", "HTTP", "TCP", "PING", "KEYWORD", "SSL")
                FilterChipRow(
                    items = types,
                    selectedIndex = if (selectedFilterType == null) 0 else {
                        val idx = types.indexOf(selectedFilterType)
                        if (idx >= 0) idx else 0
                    },
                    onSelect = { idx ->
                        selectedFilterType = if (idx == 0) null else types[idx]
                    }
                )
            }
        }

        // ── 5. Empty State or Uptime Bento Cards ──
        if (filteredTargets.isEmpty()) {
            item {
                EmptyState(
                    title = t.noMonitorsTitle,
                    hint = t.noMonitorsBody,
                    icon = Icons.Rounded.Timer,
                    radar = false,
                    actionLabel = t.addMonitor,
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(filteredTargets, key = { it.id }) { target ->
                val isUp = target.lastStatus == 1
                val isTesting = testingTargetId == target.id
                val isIncidentsExpanded = expandedIncidentsTargetId == target.id

                UptimeBentoCard(
                    target = target,
                    isUp = isUp,
                    isTesting = isTesting,
                    isIncidentsExpanded = isIncidentsExpanded,
                    t = t,
                    onTogglePause = {
                        target.isPaused = !target.isPaused
                        saveTargets()
                    },
                    onTestNow = {
                        testingTargetId = target.id
                        scope.launch {
                            UptimeEngine.checkTarget(target, ctx)
                            saveTargets()
                            testingTargetId = null
                        }
                    },
                    onToggleIncidents = {
                        expandedIncidentsTargetId = if (isIncidentsExpanded) null else target.id
                    },
                    onEdit = { editTarget = target },
                    onDelete = { deleteTarget = target }
                )
            }
        }

        item { Spacer(Modifier.height(30.dp)) }
    }

    // ── Add / Edit Monitor Dialog ──
    if (showAddDialog || editTarget != null) {
        AddOrEditMonitorDialog(
            t = t,
            existing = editTarget,
            onDismiss = {
                showAddDialog = false
                editTarget = null
            },
            onSave = { updatedTarget ->
                val list = targets.toMutableList()
                val idx = list.indexOfFirst { it.id == updatedTarget.id }
                if (idx >= 0) {
                    list[idx] = updatedTarget
                } else {
                    list.add(updatedTarget)
                }
                targets = list
                saveTargets()
                showAddDialog = false
                editTarget = null
                scope.launch {
                    UptimeEngine.checkTarget(updatedTarget, ctx)
                    saveTargets()
                }
            }
        )
    }

    // ── Delete Confirmation Dialog ──
    deleteTarget?.let { dt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t.deleteMonitorTitle, fontWeight = FontWeight.Bold, color = Ds.danger) },
            text = { Text(t.confirmDeleteMonitorTpl.format(dt.name), color = Ds.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    targets = targets.filter { it.id != dt.id }
                    saveTargets()
                    deleteTarget = null
                }) {
                    Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(t.cancel, color = Ds.textSecondary)
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Cyber Bento Metric Tile for Uptime
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun UptimeBentoTile(
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
// COMPONENT: Step-by-Step SLA & Uptime Bento Guide
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepByStepUptimeGuideCard(
    t: Str,
    isExpanded: Boolean,
    totalMonitors: Int,
    onToggleExpand: () -> Unit,
    onAddMonitor: () -> Unit
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconBadge(
                        icon = Icons.Rounded.HelpOutline,
                        tint = Ds.accent,
                        background = Ds.accentDim,
                        size = 32.dp,
                        iconSize = 17.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            t.uptimeGuideHeader,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ds.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "24/7 SLA Watch (3 Easy Steps)",
                            fontSize = 10.5.sp,
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
                    size = 32.dp,
                    onClick = onToggleExpand
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

                    // Step 1: Choose Probe Protocol
                    UptimeStepPod(
                        stepNum = "1",
                        title = t.uptimeStep1Title,
                        desc = t.uptimeStep1Desc
                    )

                    // Step 2: Set Host & Verification Rules
                    UptimeStepPod(
                        stepNum = "2",
                        title = t.uptimeStep2Title,
                        desc = t.uptimeStep2Desc
                    )

                    // Step 3: 24/7 Monitoring & Smart Alerts
                    UptimeStepPod(
                        stepNum = "3",
                        title = t.uptimeStep3Title,
                        desc = t.uptimeStep3Desc
                    ) {
                        PrimaryButton(
                            text = t.addMonitor,
                            icon = Icons.Rounded.Add,
                            onClick = onAddMonitor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UptimeStepPod(
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
// COMPONENT: Cyber Bento Uptime Card
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun UptimeBentoCard(
    target: UptimeTarget,
    isUp: Boolean,
    isTesting: Boolean,
    isIncidentsExpanded: Boolean,
    t: Str,
    onTogglePause: () -> Unit,
    onTestNow: () -> Unit,
    onToggleIncidents: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val heartbeats = remember(target.heartbeats.size) { target.heartbeats.takeLast(24) }
    val latencyValues = remember(heartbeats) { heartbeats.map { it.latencyMs.toFloat() } }

    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: RingGauge + Name + Type + Latency + Pause Switch
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RingGauge(
                        value = target.uptimePct,
                        size = 46.dp,
                        strokeWidth = 4.dp,
                        tone = when {
                            target.isPaused -> Ds.textTertiary
                            isUp && target.uptimePct >= 98f -> Ds.ok
                            isUp -> Ds.warn
                            else -> Ds.danger
                        }
                    ) {
                        Text(
                            "${target.uptimePct.toInt()}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = Ds.textPrimary
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                target.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusPill(text = target.type, level = StatusLevel.Info, pulse = false)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            target.target,
                            fontSize = 11.sp,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (target.isPaused) {
                        StatusPill(text = "PAUSED", level = StatusLevel.Neutral)
                    } else if (isUp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Ds.okDim)
                                .border(BorderStroke(1.dp, Ds.ok.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "⚡ ${target.lastLatencyMs} ms",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.ok
                            )
                        }
                    } else if (target.lastStatus == 0) {
                        StatusPill(text = t.offline, level = StatusLevel.Danger)
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = !target.isPaused,
                        onCheckedChange = { onTogglePause() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Ds.accent,
                            checkedThumbColor = Ds.onAccent,
                            uncheckedTrackColor = Ds.surfaceHighlight
                        )
                    )
                }
            }

            // Sunken Telemetry & Latency Sparkline Well
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ds.surfaceLow)
                    .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Interval: ${target.intervalSec}s", fontSize = 10.sp, color = Ds.textTertiary)
                        Text(
                            if (target.lastChecked > 0) {
                                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                "Checked: ${fmt.format(Date(target.lastChecked))}"
                            } else "Awaiting check",
                            fontSize = 10.sp,
                            fontFamily = Telemetry,
                            color = Ds.textSecondary
                        )
                    }

                    if (latencyValues.size >= 2) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(28.dp)
                        ) {
                            Sparkline(
                                values = latencyValues,
                                modifier = Modifier.fillMaxSize(),
                                color = if (isUp) Ds.accent else Ds.danger
                            )
                        }
                    } else {
                        Text("Collecting telemetry…", fontSize = 10.sp, color = Ds.textTertiary)
                    }
                }
            }

            Hairline()

            // Card Actions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrimaryButton(
                        text = if (isTesting) "Probing…" else "Test Now",
                        icon = Icons.Rounded.Bolt,
                        loading = isTesting,
                        onClick = onTestNow
                    )

                    if (target.incidents.isNotEmpty()) {
                        SoftButton(
                            text = "Incidents (${target.incidents.size})",
                            icon = if (isIncidentsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.History,
                            onClick = onToggleIncidents
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

            // Incidents Drawer
            AnimatedVisibility(
                visible = isIncidentsExpanded && target.incidents.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(t.incidentHistory, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.danger)
                    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                    target.incidents.takeLast(3).reversed().forEach { inc ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ds.dangerDim)
                                .border(BorderStroke(1.dp, Ds.danger.copy(alpha = 0.3f)), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠️", fontSize = 11.sp)
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "${fmt.format(Date(inc.startTime))} — ${inc.error.ifBlank { "Service Outage" }}",
                                        fontSize = 11.sp,
                                        fontFamily = Telemetry,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Ds.danger
                                    )
                                    if (inc.endTime != null) {
                                        Text(
                                            "Duration: ${inc.durationSec}s",
                                            fontSize = 9.5.sp,
                                            fontFamily = Telemetry,
                                            color = Ds.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Add/Edit Monitor Dialog ─────────────────────────────────────────────────

@Composable
private fun AddOrEditMonitorDialog(
    t: Str,
    existing: UptimeTarget?,
    onDismiss: () -> Unit,
    onSave: (UptimeTarget) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "HTTP") }
    var targetUrl by remember { mutableStateOf(existing?.target ?: "") }
    var port by remember { mutableStateOf(existing?.port?.toString() ?: "80") }
    var keyword by remember { mutableStateOf(existing?.keyword ?: "") }
    var intervalSec by remember { mutableStateOf(existing?.intervalSec?.toString() ?: "30") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    val types = listOf("HTTP", "TCP", "PING", "KEYWORD", "SSL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existing == null) t.addMonitor else t.editMonitorTitle, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(400.dp).imePadding()
            ) {
                item {
                    Text(t.protocolTypeLbl, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    SegmentedControl(
                        items = types,
                        selectedIndex = types.indexOf(type).coerceAtLeast(0),
                        onSelect = {
                            type = types[it]
                            if (type == "SSL" && port == "80") port = "443"
                            if (type == "TCP" && port == "80") port = "5432"
                        }
                    )
                }

                item { InputField(value = name, onValueChange = { name = it }, label = t.monitorNameLbl, placeholder = "e.g. Production API") }

                item {
                    InputField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = when (type) {
                            "TCP" -> "IP / Host"
                            "PING" -> "IP / Domain"
                            "SSL" -> "Domain"
                            else -> "URL"
                        },
                        placeholder = when (type) {
                            "TCP" -> "1.2.3.4"
                            "PING" -> "8.8.8.8"
                            "SSL" -> "example.com"
                            else -> "https://example.com"
                        }
                    )
                }

                if (type == "TCP" || type == "SSL") {
                    item { InputField(value = port, onValueChange = { port = it }, label = t.monitorPortLbl, placeholder = "443") }
                }

                if (type == "KEYWORD") {
                    item { InputField(value = keyword, onValueChange = { keyword = it }, label = t.monitorKeywordLbl, placeholder = "e.g. status: ok") }
                }

                item {
                    InputField(
                        value = intervalSec,
                        onValueChange = { intervalSec = it },
                        label = "Check Interval (Seconds)",
                        placeholder = "30"
                    )
                }

                testResult?.let { msg ->
                    item {
                        BannerCard(
                            text = msg,
                            tone = if (testSuccess) BannerTone.Ok else BannerTone.Danger,
                            icon = if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
                        )
                    }
                }

                item {
                    SoftButton(
                        text = if (isTesting) "Testing…" else t.testBeforeSave,
                        icon = Icons.Rounded.Bolt,
                        enabled = !isTesting && targetUrl.isNotBlank(),
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val dummy = UptimeTarget(
                                    id = 0,
                                    name = name,
                                    type = type,
                                    target = targetUrl.trim(),
                                    port = port.toIntOrNull() ?: 80,
                                    keyword = keyword.trim(),
                                    intervalSec = intervalSec.toIntOrNull() ?: 30
                                )
                                val hb = UptimeEngine.checkTarget(dummy)
                                isTesting = false
                                if (hb.status == 1) {
                                    testSuccess = true
                                    testResult = t.probeOkTpl.format(hb.latencyMs)
                                } else {
                                    testSuccess = false
                                    testResult = t.probeFail
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = t.save,
                enabled = name.isNotBlank() && targetUrl.isNotBlank(),
                onClick = {
                    val newTarget = existing ?: UptimeTarget(
                        id = System.currentTimeMillis(),
                        name = name.trim(),
                        type = type,
                        target = targetUrl.trim(),
                        port = port.toIntOrNull() ?: 80,
                        keyword = keyword.trim(),
                        intervalSec = intervalSec.toIntOrNull() ?: 30
                    )
                    if (existing != null) {
                        existing.name = name.trim()
                        existing.type = type
                        existing.target = targetUrl.trim()
                        existing.port = port.toIntOrNull() ?: 80
                        existing.keyword = keyword.trim()
                        existing.intervalSec = intervalSec.toIntOrNull() ?: 30
                    }
                    onSave(newTarget)
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}
