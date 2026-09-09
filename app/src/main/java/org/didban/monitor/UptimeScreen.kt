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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
        targets = targets.toList() // trigger recomposition
    }

    // Background polling loop
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PageHeader(
            icon = Icons.Rounded.Timer,
            title = t.uptimeMonitoring,
            actionLabel = t.addMonitor,
            onAction = { showAddDialog = true }
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Summary strip ──
            item {
                ModernCard(padding = 14.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SummaryTile(t.monitorsTotalLbl, totalCount.toString(), Ds.accent, Modifier.weight(1f))
                        VerticalHairline()
                        SummaryTile(t.upLbl, upCount.toString(), Ds.ok, Modifier.weight(1f))
                        VerticalHairline()
                        SummaryTile(t.withDowntimeLbl, downCount.toString(), if (downCount > 0) Ds.danger else Ds.textSecondary, Modifier.weight(1f))
                    }
                }
            }

            // ── Monitors list or empty state ──
            if (targets.isEmpty()) {
                item {
                    ModernCard(padding = 18.dp) {
                        EmptyState(
                            title = t.noMonitorsTitle,
                            hint = t.noMonitorsBody,
                            radar = true
                        )
                        PrimaryActionButton(
                            text = t.addMonitor,
                            icon = Icons.Rounded.Add,
                            onClick = { showAddDialog = true }
                        )
                    }
                }
            } else {
                items(targets, key = { it.id }) { item ->
                    val isExpanded = expandedTargetId == item.id

                    ModernCard(
                        padding = 14.dp,
                        onClick = { expandedTargetId = if (isExpanded) null else item.id }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(isOnline = item.lastStatus == 1, size = 9.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Ds.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    ValuePill(item.type, Ds.accent)
                                }
                                Spacer(Modifier.height(3.dp))
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
                                Text(
                                    "%.1f%%".format(Locale.US, item.uptimePct),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Telemetry,
                                    color = if (item.uptimePct > 98f) Ds.ok else Ds.danger
                                )
                                Text(
                                    if (item.lastLatencyMs > 0) "${item.lastLatencyMs} ms" else "—",
                                    fontSize = 10.5.sp,
                                    color = Ds.accent,
                                    fontFamily = Telemetry
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── 30 Heartbeat bars (Uptime Kuma style) ──
                        HeartbeatBar(statuses = item.heartbeats.map { it.status })

                        // ── Expanded: incidents & actions ──
                        if (isExpanded) {
                            Spacer(Modifier.height(14.dp))
                            SectionLabel(t.incidentHistory)
                            Spacer(Modifier.height(6.dp))

                            if (item.incidents.isEmpty()) {
                                Text(t.noIncidents, fontSize = 10.5.sp, color = Ds.textTertiary)
                            } else {
                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                item.incidents.takeLast(3).reversed().forEach { inc ->
                                    Text(
                                        t.incidentTpl.format(fmt.format(Date(inc.startTime)), inc.durationSec, inc.error),
                                        fontSize = 10.5.sp,
                                        color = Ds.danger,
                                        lineHeight = 15.5.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SoftButton(
                                    text = if (item.isPaused) t.resumeWatch else t.pauseWatch,
                                    onClick = {
                                        item.isPaused = !item.isPaused
                                        saveTargets()
                                    },
                                    icon = if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                                )
                                Spacer(Modifier.width(8.dp))
                                SoftButton(
                                    text = t.recheckNow,
                                    onClick = {
                                        scope.launch {
                                            UptimeEngine.checkTarget(item, ctx)
                                            saveTargets()
                                            Toast.makeText(ctx, t.statusUpdated, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    icon = Icons.Rounded.Refresh
                                )
                                Spacer(Modifier.width(8.dp))
                                SoftButton(
                                    text = t.delete,
                                    onClick = { deleteTarget = item },
                                    icon = Icons.Rounded.DeleteOutline,
                                    tone = Ds.danger,
                                    toneDim = Ds.dangerDim
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    SecondaryActionCard(
                        title = t.addMonitor,
                        icon = Icons.Rounded.Add,
                        onClick = { showAddDialog = true }
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
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

    editTarget?.let { et ->
        AddOrEditMonitorDialog(
            t = t,
            existing = et,
            onDismiss = { editTarget = null },
            onSave = { editTarget = null; saveTargets() }
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

@Composable
private fun SummaryTile(label: String, value: String, tone: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Telemetry,
            color = tone
        )
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.5.sp, color = Ds.textTertiary)
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Ds.hairline)
    )
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
            Text(if (existing == null) t.addMonitor else t.editMonitorTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(400.dp).imePadding()
            ) {
                item {
                    DTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = t.monitorNameLbl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Column {
                        Text(
                            t.protocolTypeLbl,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ds.textTertiary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        SegmentedTabs(
                            tabs = types.map { TabSpec(it) },
                            selected = types.indexOf(type),
                            onSelect = {
                                type = types[it]
                                if (type == "SSL" && port == "80") port = "443"
                                if (type == "TCP" && port == "80") port = "5432"
                            },
                            scrollable = true
                        )
                    }
                }

                item {
                    DTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = when (type) {
                            "TCP" -> "IP / host (e.g. 1.2.3.4)"
                            "PING" -> "IP / domain (e.g. 8.8.8.8)"
                            "SSL" -> "domain (e.g. google.com)"
                            else -> "https://example.com"
                        },
                        mono = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (type == "TCP" || type == "SSL") {
                    item {
                        DTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = t.monitorPortLbl,
                            mono = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (type == "KEYWORD") {
                    item {
                        DTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            label = t.monitorKeywordLbl,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Live test feedback
                if (testResult != null) {
                    item {
                        Banner(
                            tone = if (testSuccess) BannerTone.Ok else BannerTone.Danger,
                            text = testResult!!,
                            icon = if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
                        )
                    }
                }

                // Quick test button
                item {
                    SecondaryActionCard(
                        title = t.testBeforeSave,
                        icon = Icons.Rounded.Bolt,
                        onClick = {
                            if (targetUrl.isBlank()) return@SecondaryActionCard
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
                        }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (name.isNotBlank() && targetUrl.isNotBlank()) {
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
                },
                enabled = name.isNotBlank() && targetUrl.isNotBlank(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Ds.accent,
                    contentColor = Ds.onAccent
                )
            ) {
                if (isTesting) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Ds.onAccent)
                } else {
                    Text(t.save, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, fontSize = 12.sp) }
        }
    )
}
