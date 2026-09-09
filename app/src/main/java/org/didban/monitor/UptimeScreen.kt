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
            Text("⏱️ ${t.uptimeMonitoring}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showAddDialog = true }
            ) {
                Text(
                    "+ ${t.addMonitor}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Explanatory Guide Card ──
        FeatureGuideCard(
            title = "⏱️ راهنمای پایش پایداری و ضربان قلب (Uptime Kuma)",
            description = t.guideUptime
        )

        Spacer(Modifier.height(10.dp))

        // ── Summary Stats (2x2 / 3-Card Grid) ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernSummaryCard("کل مانیتورها", totalCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            ModernSummaryCard("سرویس‌های آنلاین", upCount.toString(), Color(0xFF10B981), Modifier.weight(1f))
            ModernSummaryCard("دارای قطعی", downCount.toString(), if (downCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        if (targets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("⏱️", fontSize = 48.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(t.noMonitorsHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    PrimaryActionButton(
                        text = "+ ${t.addMonitor}",
                        onClick = { showAddDialog = true }
                    )
                }
            }
            return
        }

        // ── Monitors List ──
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(targets, key = { it.id }) { item ->
                val isExpanded = expandedTargetId == item.id

                ModernCard(
                    padding = 14.dp,
                    modifier = Modifier.clickable { expandedTargetId = if (isExpanded) null else item.id }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Status Dot
                        Box(
                            Modifier.size(12.dp).background(
                                when {
                                    item.isPaused -> Color(0xFF94A3B8)
                                    item.lastStatus == 1 -> Color(0xFF10B981)
                                    item.lastStatus == 0 -> Color(0xFFEF4444)
                                    else -> Color(0xFFF59E0B)
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
                            Text(item.target, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "%.1f%%".format(Locale.US, item.uptimePct),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.uptimePct > 98f) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Text(
                                if (item.lastLatencyMs > 0) "${item.lastLatencyMs} ms" else "—",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── 30 Heartbeat Bars (Uptime Kuma Style) ──
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val emptyBars = (30 - item.heartbeats.size).coerceAtLeast(0)
                        repeat(emptyBars) {
                            Box(
                                Modifier.weight(1f).height(18.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            )
                        }
                        item.heartbeats.takeLast(30).forEach { hb ->
                            Box(
                                Modifier.weight(1f).height(18.dp)
                                    .background(
                                        if (hb.status == 1) Color(0xFF10B981) else Color(0xFFEF4444),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }

                    // ── Expanded Incidents & Actions ──
                    if (isExpanded) {
                        Spacer(Modifier.height(12.dp))
                        Text("تاریخچه قطعی‌ها و حوادث:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))

                        if (item.incidents.isEmpty()) {
                            Text("هیچ حادثه قطعی برای این سرویس ثبت نشده است 🎉", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            item.incidents.takeLast(3).reversed().forEach { inc ->
                                val start = fmt.format(Date(inc.startTime))
                                val dur = inc.durationSec
                                Text(
                                    "• $start — قطعی به مدت ${dur} ثانیه (${inc.error})",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                item.isPaused = !item.isPaused
                                saveTargets()
                            }) {
                                Text(if (item.isPaused) "▶️ ادامه پایش" else "⏸️ توقف موقت", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    UptimeEngine.checkTarget(item, ctx)
                                    saveTargets()
                                }
                            }) {
                                Text("🔄 بررسی مجدد", fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                targets = targets.filter { it.id != item.id }
                                saveTargets()
                            }) {
                                Text("🗑️ حذف", color = Color(0xFFEF4444), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
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
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.addMonitor, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام مانیتور (مثلاً سایت من)") }, singleLine = true)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("HTTP", "TCP", "PING", "KEYWORD", "SSL").forEach { tp ->
                            val isSel = type == tp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { type = tp }
                            ) {
                                Text(tp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    OutlinedTextField(value = targetUrl, onValueChange = { targetUrl = it }, label = { Text(if (type == "TCP" || type == "PING" || type == "SSL") "آدرس سرور / IP" else "آدرس URL (https://...)") }, singleLine = true)

                    if (type == "TCP") {
                        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("شماره پورت (مثلا 5432, 3306, 80)") }, singleLine = true)
                    }

                    if (type == "KEYWORD") {
                        OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("کلمه کلیدی مورد انتظار در پاسخ") }, singleLine = true)
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
private fun ModernSummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ModernCard(modifier = modifier, padding = 10.dp) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
