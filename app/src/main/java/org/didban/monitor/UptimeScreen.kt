@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        // ── Header ──
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBadge(
                    icon = Icons.Rounded.Timer,
                    tint = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    size = 36.dp,
                    iconSize = 18.dp
                )
                Text(t.uptimeMonitoring, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0D9488),
                modifier = Modifier.clickable { showAddDialog = true }
            ) {
                Row(
                    Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+ ${t.addMonitor}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // ── Explanatory Guide Card ──
        FeatureGuideCard(
            title = "پایش پایداری و سلامت سرویس‌ها",
            description = t.guideUptime
        )

        Spacer(Modifier.height(8.dp))

        // ── Summary Stats ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModernSummaryCard("کل مانیتورها", totalCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            ModernSummaryCard("آنلاین", upCount.toString(), Color(0xFF10B981), Modifier.weight(1f))
            ModernSummaryCard("دارای قطعی", downCount.toString(), if (downCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))

        // ── Monitors List or Empty State ──
        if (targets.isEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ModernCard(padding = 18.dp) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconBadge(
                                icon = Icons.Rounded.Timer,
                                tint = MaterialTheme.colorScheme.primary,
                                background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                size = 56.dp,
                                iconSize = 28.dp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("هنوز مانیتوری ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "با افزودن اولین مانیتور، وضعیت در دسترس بودن سرویس‌های شما به صورت خودکار چک می‌شود.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            PrimaryActionButton(
                                text = "＋  ${t.addMonitor}",
                                onClick = { showAddDialog = true }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(targets, key = { it.id }) { item ->
                    val isExpanded = expandedTargetId == item.id

                    ModernCard(
                        padding = 12.dp,
                        modifier = Modifier.clickable { expandedTargetId = if (isExpanded) null else item.id }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Status Dot
                            PulseDot(
                                isOnline = item.lastStatus == 1,
                                size = 10.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                        Text(item.type, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(item.target, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "%.1f%%".format(Locale.US, item.uptimePct),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.uptimePct > 98f) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                                Text(
                                    if (item.lastLatencyMs > 0) "${item.lastLatencyMs} ms" else "—",
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ── 30 Heartbeat Bars (Uptime Kuma Style) ──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val emptyBars = (30 - item.heartbeats.size).coerceAtLeast(0)
                            repeat(emptyBars) {
                                Box(
                                    Modifier.weight(1f).height(14.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                                )
                            }
                            item.heartbeats.takeLast(30).forEach { hb ->
                                Box(
                                    Modifier.weight(1f).height(14.dp)
                                        .background(
                                            if (hb.status == 1) Color(0xFF10B981) else Color(0xFFEF4444),
                                            RoundedCornerShape(3.dp)
                                        )
                                )
                            }
                        }

                        // ── Expanded Incidents & Actions ──
                        if (isExpanded) {
                            Spacer(Modifier.height(10.dp))
                            Text("تاریخچه حوادث و قطعی‌ها:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                            Spacer(Modifier.height(3.dp))

                            if (item.incidents.isEmpty()) {
                                Text("هیچ حادثه قطعی برای این سرویس ثبت نشده است", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                item.incidents.takeLast(3).reversed().forEach { inc ->
                                    val start = fmt.format(Date(inc.startTime))
                                    val dur = inc.durationSec
                                    Text(
                                        "• $start — قطعی به مدت ${dur} ثانیه (${inc.error})",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    item.isPaused = !item.isPaused
                                    saveTargets()
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(if (item.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Text(if (item.isPaused) "ادامه پایش" else "توقف موقت", fontSize = 11.sp)
                                    }
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        UptimeEngine.checkTarget(item, ctx)
                                        saveTargets()
                                        Toast.makeText(ctx, "وضعیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Text("بررسی مجدد", fontSize = 11.sp)
                                    }
                                }
                                TextButton(onClick = {
                                    deleteTarget = item
                                }) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                        Text("حذف", color = Color(0xFFEF4444), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    PrimaryActionButton(
                        text = "＋  ${t.addMonitor}",
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

    // ── Delete Confirmation Dialog ──
    deleteTarget?.let { dt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("حذف مانیتور", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف مانیتور «${dt.name}» اطمینان دارید؟") },
            confirmButton = {
                TextButton(onClick = {
                    targets = targets.filter { it.id != dt.id }
                    saveTargets()
                    deleteTarget = null
                }) {
                    Text("حذف", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(t.cancel) }
            }
        )
    }
}

// ── Add/Edit Monitor Dialog Form ────────────────────────────────────────────

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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(if (existing == null) t.addMonitor else "ویرایش مانیتور", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(380.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام دلخواه مانیتور") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("نوع پروتکل:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("HTTP", "TCP", "PING", "KEYWORD", "SSL").forEach { tp ->
                            val isSel = type == tp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    type = tp
                                    if (tp == "SSL" && port == "80") port = "443"
                                    if (tp == "TCP" && port == "80") port = "5432"
                                }
                            ) {
                                Text(
                                    tp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { targetUrl = it },
                        label = {
                            Text(
                                when (type) {
                                    "TCP" -> "آدرس IP یا هاست (مثلاً 1.2.3.4)"
                                    "PING" -> "آدرس IP یا دامنه (مثلاً 8.8.8.8)"
                                    "SSL" -> "دامنه (مثلاً google.com)"
                                    else -> "آدرس وبسایت (مثلاً https://example.com)"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (type == "TCP" || type == "SSL") {
                    item {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("شماره پورت (مثلاً 443, 80, 5432, 3306, 6379, 22)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (type == "KEYWORD") {
                    item {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            label = { Text("کلمه کلیدی مورد انتظار (مثلاً ok یا status)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Live Test Feedback
                if (testResult != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (testSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (testSuccess) Color(0xFF047857) else Color(0xFFDC2626),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    testResult!!,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (testSuccess) Color(0xFF047857) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }

                // Quick Test Button
                item {
                    OutlinedButton(
                        onClick = {
                            if (targetUrl.isBlank()) return@OutlinedButton
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
                                    testResult = "پاسخ دریافت شد! (زمان پاسخ: ${hb.latencyMs} میلی‌ثانیه)"
                                } else {
                                    testSuccess = false
                                    testResult = "پاسخ دریافت نشد یا خطایی رخ داد"
                                }
                            }
                        },
                        enabled = !isTesting && targetUrl.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        if (isTesting) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                        else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Text("تست اتصال قبل از ذخیره", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
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
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
            ) {
                Text(t.save, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, fontSize = 12.sp) }
        }
    )
}

@Composable
private fun ModernSummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ModernCard(modifier = modifier, padding = 10.dp) {
        Text(title, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
