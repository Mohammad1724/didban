@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    var expandedTargetId by remember { mutableStateOf<Long?>(null) }

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
    val totalCount = targets.size

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
                    .padding(top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.Timer, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.uptimeMonitoring, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("24/7 Heartbeat & SLA Watch", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                PrimaryButton(
                    text = t.addMonitor,
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(38.dp)
                )
            }
        }

        // ── 2. Fleet Uptime Hero Bento ──
        item {
            ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (targets.isEmpty()) "—" else "%.1f%%".format(Locale.US, targets.map { it.uptimePct }.average().toFloat()),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = if (downCount > 0) Ds.danger else Ds.ok
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(t.fleetUptimeAvg, fontSize = 11.sp, color = Ds.textTertiary)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = Ds.accent)
                            Text(t.monitorsTotalLbl, fontSize = 10.sp, color = Ds.textTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$upCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = Ds.ok)
                            Text(t.upLbl, fontSize = 10.sp, color = Ds.textTertiary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$downCount", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Telemetry, color = if (downCount > 0) Ds.danger else Ds.textSecondary)
                            Text(t.withDowntimeLbl, fontSize = 10.sp, color = Ds.textTertiary)
                        }
                    }
                }
            }
        }

        // ── 3. Monitors List or Empty State ──
        if (targets.isEmpty()) {
            item {
                EmptyState(
                    title = t.noMonitorsTitle,
                    hint = t.noMonitorsBody,
                    radar = true,
                    actionLabel = t.addMonitor,
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(targets, key = { it.id }) { item ->
                val isExpanded = expandedTargetId == item.id
                val isUp = item.lastStatus == 1

                ModernCard(
                    padding = 14.dp,
                    cornerRadius = 18.dp,
                    onClick = { expandedTargetId = if (isExpanded) null else item.id }
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RingGauge(
                            value = item.uptimePct,
                            size = 46.dp,
                            strokeWidth = 4.dp,
                            tone = if (isUp && item.uptimePct > 98f) Ds.ok else if (isUp) Ds.warn else Ds.danger
                        ) {
                            Text(
                                "${item.uptimePct.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textPrimary
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(6.dp))
                                StatusPill(item.type, level = StatusLevel.Info, pulse = false)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.target,
                                fontSize = 11.sp,
                                color = Ds.textTertiary,
                                fontFamily = Telemetry,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(
                                text = if (item.isPaused) "Paused" else if (isUp) "UP" else "DOWN",
                                level = if (item.isPaused) StatusLevel.Neutral else if (isUp) StatusLevel.Ok else StatusLevel.Danger,
                                pulse = isUp && !item.isPaused
                            )
                            if (item.lastLatencyMs > 0) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${item.lastLatencyMs} ms",
                                    fontSize = 10.5.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.accent
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── 30-Pulse Heartbeat Rhythm Bar ──
                    HeartbeatBar(statuses = item.heartbeats.map { it.status }, height = 12.dp)

                    // Expanded incident logs and controls
                    if (isExpanded) {
                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SoftButton(
                                    text = if (item.isPaused) t.resumeWatch else t.pauseWatch,
                                    icon = if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    onClick = {
                                        item.isPaused = !item.isPaused
                                        saveTargets()
                                    }
                                )
                                SoftButton(
                                    text = t.recheckNow,
                                    icon = Icons.Rounded.Refresh,
                                    onClick = {
                                        scope.launch {
                                            UptimeEngine.checkTarget(item, ctx)
                                            saveTargets()
                                            Toast.makeText(ctx, t.statusUpdated, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CircleIconButton(
                                    icon = Icons.Rounded.Edit,
                                    contentDescription = "Edit",
                                    tint = Ds.textSecondary,
                                    size = 32.dp,
                                    onClick = { editTarget = item }
                                )
                                CircleIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Ds.danger,
                                    size = 32.dp,
                                    onClick = { deleteTarget = item }
                                )
                            }
                        }

                        // Recent Incidents
                        if (item.incidents.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(t.incidentHistory, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                            Spacer(Modifier.height(4.dp))
                            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            item.incidents.takeLast(3).forEach { inc ->
                                Text(
                                    "• ${fmt.format(Date(inc.startTime))} — ${inc.error}",
                                    fontSize = 11.sp,
                                    color = Ds.danger,
                                    fontFamily = Telemetry
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // ── Add Monitor Dialog ──
    if (showAddDialog) {
        AddOrEditMonitorDialog(
            t = t,
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { newTarget ->
                targets = targets + newTarget
                saveTargets()
                showAddDialog = false
                scope.launch {
                    UptimeEngine.checkTarget(newTarget, ctx)
                    saveTargets()
                }
            }
        )
    }

    // ── Edit Monitor Dialog ──
    editTarget?.let { et ->
        AddOrEditMonitorDialog(
            t = t,
            existing = et,
            onDismiss = { editTarget = null },
            onSave = {
                editTarget = null
                saveTargets()
            }
        )
    }

    // ── Delete Confirmation Dialog ──
    deleteTarget?.let { dt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t.deleteMonitorTitle, fontWeight = FontWeight.Bold) },
            text = { Text(t.confirmDeleteMonitorTpl.format(dt.name)) },
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
                TextButton(onClick = { deleteTarget = null }) { Text(t.cancel) }
            }
        )
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
                modifier = Modifier.fillMaxWidth().height(380.dp).imePadding()
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
                                    keyword = keyword.trim()
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
                        keyword = keyword.trim()
                    )
                    if (existing != null) {
                        existing.name = name.trim()
                        existing.type = type
                        existing.target = targetUrl.trim()
                        existing.port = port.toIntOrNull() ?: 80
                        existing.keyword = keyword.trim()
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
