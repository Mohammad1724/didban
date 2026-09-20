@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package org.didban.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CommandUptimeEditorScreen(copy: CommandCopy, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    UptimeEngine.ensureLoaded(context)
    val targets by UptimeEngine.liveTargets.collectAsState()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf(copy.upNewMonitor) }
    var type by remember { mutableStateOf("HTTP") }
    var target by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var interval by remember { mutableStateOf("30") }
    var keyword by remember { mutableStateOf("") }
    var allowPrivateNetwork by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<UptimeTarget?>(null) }

    fun reset() {
        selectedId = null
        name = copy.upNewMonitor
        type = "HTTP"
        target = ""
        port = "443"
        interval = "30"
        keyword = ""
        allowPrivateNetwork = false
        message = null
        error = null
    }

    fun select(item: UptimeTarget) {
        selectedId = item.id
        name = item.name
        type = item.type
        target = item.target
        port = item.port.toString()
        interval = item.intervalSec.toString()
        keyword = item.keyword
        allowPrivateNetwork = item.allowPrivateNetwork
        message = null
        error = null
    }

    fun buildTarget(): UptimeTarget {
        val original = selectedId?.let { id -> targets.firstOrNull { it.id == id } }
        return UptimeTarget(
        id = selectedId ?: System.currentTimeMillis(),
        name = name.trim().ifBlank { copy.upNewMonitor },
        type = type,
        target = target.trim(),
        port = port.toIntOrNull()?.coerceIn(1, 65535) ?: if (type == "HTTPS" || type == "SSL") 443 else 80,
        intervalSec = interval.toIntOrNull()?.coerceIn(10, 86400) ?: 30,
        keyword = keyword.trim(),
        allowPrivateNetwork = allowPrivateNetwork,
        isPaused = selectedId?.let { id -> targets.firstOrNull { it.id == id }?.isPaused } ?: false,
        lastStatus = selectedId?.let { id -> targets.firstOrNull { it.id == id }?.lastStatus } ?: -1,
        lastLatencyMs = original?.lastLatencyMs ?: 0,
        lastChecked = original?.lastChecked ?: 0,
        heartbeats = original?.heartbeats?.toMutableList() ?: mutableListOf(),
        incidents = original?.incidents?.toMutableList() ?: mutableListOf()
        )
    }

    fun save() {
        val item = buildTarget()
        if (item.target.isBlank()) {
            error = copy.upTargetRequired
            return
        }
        if (item.type == "KEYWORD" && item.keyword.isBlank()) {
            error = copy.upKeywordRequired
            return
        }
        UptimeEngine.upsert(context, item)
        selectedId = item.id
        message = copy.upSaved
        error = null
    }

    fun test() {
        val item = buildTarget()
        if (item.target.isBlank()) {
            error = copy.upTargetFirst
            return
        }
        UptimeEngine.upsert(context, item)
        selectedId = item.id
        busy = true
        error = null
        scope.launch {
            runCatching { UptimeEngine.checkNow(context, item) }
                .onSuccess { heartbeat -> message = copy.upCheckDone.replace("%1", if (heartbeat.status == 1) "UP" else "DOWN").replace("%2", heartbeat.latencyMs.toString()) }
                .onFailure { error = it.message ?: copy.upCheckFailed }
            busy = false
        }
    }

    val selected = selectedId?.let { id -> targets.firstOrNull { it.id == id } }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.uptime, if (selectedId == null) copy.upNewMonitor else copy.upEditing.replace("%1", "$selectedId"), modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.upSavedMonitors, Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        CommandTextButton(copy.upNewMonitor, ::reset, icon = Icons.Rounded.Refresh)
                    }
                    if (targets.isEmpty()) {
                        Text(copy.upNoMonitorYet, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            targets.forEach { item ->
                                CommandSecondaryButton(item.name, { select(item) }, enabled = selectedId != item.id)
                            }
                        }
                    }
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.upMonitorContract, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.uiName) })
                    Text(copy.upProbeType, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    // All six check types stay visible: a scroll row clipped
                    // PING/KEYWORD/SSL with no affordance that more exist.
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        listOf("HTTP", "HTTPS", "TCP", "PING", "KEYWORD", "SSL").forEach { candidate ->
                            CommandSecondaryButton(candidate, { type = candidate }, enabled = type != candidate)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(target, { target = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.upTargetUrlHost) }, placeholder = { Text(copy.radarTargetHostHint) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), singleLine = true, label = { Text(copy.port) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), singleLine = true, label = { Text(copy.upIntervalSeconds) })
                        // The keyword field only makes sense for KEYWORD
                        // checks; showing it always confused HTTP/TCP users.
                        if (type == "KEYWORD") {
                            OutlinedTextField(keyword, { keyword = it }, Modifier.weight(2f), singleLine = true, label = { Text(copy.upKeywordHint) })
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(copy.upAllowPrivateTitle, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Text(copy.upAllowPrivateBody, color = CommandColors.warning, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = allowPrivateNetwork, onCheckedChange = { allowPrivateNetwork = it })
                    }
                    Text(copy.upEditorBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                CommandPrimaryButton(copy.save, ::save, Modifier.weight(1f), Icons.Rounded.Save, enabled = !busy)
                CommandSecondaryButton(copy.upTestNow, ::test, Modifier.weight(1f), Icons.Rounded.PlayArrow, enabled = !busy)
            }
        }
        if (selected != null) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandStatusMark(
                            if (selected.lastStatus == 1) "UP" else if (selected.lastStatus == 0) "DOWN" else "PENDING",
                            if (selected.lastStatus == 1) CommandHealthTone.HEALTHY else if (selected.lastStatus == 0) CommandHealthTone.OFFLINE else CommandHealthTone.UNKNOWN,
                            detail = "${selected.lastLatencyMs} ms · ${selected.intervalSec}s"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            CommandSecondaryButton(if (selected.isPaused) copy.upResume else copy.upPause, { UptimeEngine.togglePause(context, selected.id) }, icon = if (selected.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause)
                            CommandTextButton(copy.delete, { deleteTarget = selected }, icon = Icons.Rounded.DeleteOutline)
                        }
                        if (selected.incidents.isNotEmpty()) {
                            Text(copy.upIncidentCount.replace("%1", selected.incidents.size.toString()), color = CommandColors.warning, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (message != null) item { CommandStateBlock(copy.operationDone, message ?: "", CommandHealthTone.INFO) }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val targetToDelete = deleteTarget
    if (targetToDelete != null) {
        val item = targetToDelete
        AlertDialog(
            onDismissRequest = { if (!busy) deleteTarget = null },
            title = { Text(copy.upDeleteTitle, fontWeight = FontWeight.Bold) },
            text = { Text("${copy.upDeleteBody.replace("%1", item.name)}\n\n${item.type} · ${item.target}:${item.port} · #${item.id}") },
            confirmButton = {
                TextButton(onClick = {
                    if (busy) return@TextButton
                    busy = true
                    val current = targets.firstOrNull { it.id == item.id }
                    runCatching {
                        check(current != null && current.name == item.name && current.type == item.type && current.target == item.target) {
                            copy.upCheckFailed
                        }
                        UptimeEngine.remove(context, item.id)
                    }.onSuccess {
                        deleteTarget = null
                        reset()
                        message = copy.upDeleted
                    }.onFailure {
                        error = (it.message ?: copy.operationFailed).take(300)
                    }
                    busy = false
                }, enabled = !busy) { Text(copy.delete, color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }, enabled = !busy) { Text(copy.cancel) } }
        )
    }
}
