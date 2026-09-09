@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
                    UptimeEngine.checkTarget(target, ctx)
                }
            }
            saveTargets()
            delay(15_000)
        }
    }

    val upCount = targets.count { it.lastStatus == 1 }
    val downCount = targets.count { it.lastStatus == 0 }
    val totalCount = targets.size

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱️ ${t.uptimeMonitoring}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Button(onClick = { showAddDialog = true }) {
                Text("+ ${t.addMonitor}")
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Summary Stats ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("Total Monitors", totalCount.toString(), Color(0xFF4CC2FF), Modifier.weight(1f))
            SummaryCard("Online", upCount.toString(), Color(0xFF4ADE80), Modifier.weight(1f))
            SummaryCard("Down", downCount.toString(), if (downCount > 0) Color(0xFFF87171) else Color(0xFF94A3B8), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        if (targets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⏱️", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.noMonitorsHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            return
        }

        // ── Monitors List ──
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(targets, key = { it.id }) { item ->
                val isExpanded = expandedTargetId == item.id

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().clickable {
                        expandedTargetId = if (isExpanded) null else item.id
                    }
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Dot
                            Box(
                                Modifier.size(12.dp).background(
                                    when {
                                        item.isPaused -> Color(0xFF94A3B8)
                                        item.lastStatus == 1 -> Color(0xFF4ADE80)
                                        item.lastStatus == 0 -> Color(0xFFF87171)
                                        else -> Color(0xFFFBBF24)
                                    },
                                    CircleShape
                                )
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Text(item.type, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(item.target, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "%.1f%%".format(Locale.US, item.uptimePct),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.uptimePct > 98f) Color(0xFF4ADE80) else Color(0xFFF87171)
                                )
                                Text(
                                    if (item.lastLatencyMs > 0) "${item.lastLatencyMs} ms" else "—",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // ── 30 Heartbeat Bars (Iconic Uptime Kuma design) ──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val emptyBars = (30 - item.heartbeats.size).coerceAtLeast(0)
                            repeat(emptyBars) {
                                Box(
                                    Modifier.weight(1f).height(18.dp)
                                        .background(Color(0xFF1E283D), RoundedCornerShape(4.dp))
                                )
                            }
                            item.heartbeats.takeLast(30).forEach { hb ->
                                Box(
                                    Modifier.weight(1f).height(18.dp)
                                        .background(
                                            if (hb.status == 1) Color(0xFF4ADE80) else Color(0xFFF87171),
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }

                        // ── Expanded Incidents & Actions ──
                        if (isExpanded) {
                            Spacer(Modifier.height(12.dp))
                            Text("Recent Incidents & History:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))

                            if (item.incidents.isEmpty()) {
                                Text("No downtime incidents recorded 🎉", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                item.incidents.takeLast(3).reversed().forEach { inc ->
                                    val start = fmt.format(Date(inc.startTime))
                                    val dur = inc.durationSec
                                    Text(
                                        "• $start — Down for ${dur}s (${inc.error})",
                                        fontSize = 11.sp,
                                        color = Color(0xFFF87171)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    item.isPaused = !item.isPaused
                                    saveTargets()
                                }) {
                                    Text(if (item.isPaused) "▶️ Resume" else "⏸️ Pause", fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        UptimeEngine.checkTarget(item, ctx)
                                        saveTargets()
                                    }
                                }) {
                                    Text("🔄 Check Now", fontSize = 12.sp)
                                }
                                TextButton(onClick = {
                                    targets = targets.filter { it.id != item.id }
                                    saveTargets()
                                }) {
                                    Text("🗑️ Delete", color = Color(0xFFF87171), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Add Monitor Dialog ──
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("HTTP") }
        var targetUrl by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("80") }
        var keyword by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(t.addMonitor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Monitor Name (e.g. My Website)") }, singleLine = true)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("HTTP", "TCP", "PING", "KEYWORD", "SSL").forEach { tp ->
                            val isSel = type == tp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { type = tp }
                            ) {
                                Text(tp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    OutlinedTextField(value = targetUrl, onValueChange = { targetUrl = it }, label = { Text(if (type == "TCP" || type == "PING" || type == "SSL") "Host / IP" else "URL (https://...)") }, singleLine = true)

                    if (type == "TCP") {
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port (e.g. 5432, 3306, 80)") }, singleLine = true)
                    }

                    if (type == "KEYWORD") {
                        OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("Expected Keyword in Response") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && targetUrl.isNotBlank()) {
                        val newTarget = UptimeTarget(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            type = type,
                            target = targetUrl.trim(),
                            port = port.toIntOrNull() ?: 80,
                            keyword = keyword.trim()
                        )
                        targets = targets + newTarget
                        saveTargets()
                        showAddDialog = false
                    }
                }) { Text(t.save) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(t.cancel) } }
        )
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
