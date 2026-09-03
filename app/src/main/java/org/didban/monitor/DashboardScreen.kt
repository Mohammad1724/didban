package org.didban.monitor

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(t: Str, server: ServerConfig, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }
    var tab by remember { mutableStateOf(0) }

    var metrics by remember { mutableStateOf<Metrics?>(null) }
    var hist by remember { mutableStateOf<List<HistPoint>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var procs by remember { mutableStateOf<List<ProcInfo>>(emptyList()) }
    var events by remember { mutableStateOf<List<SpikeEvent>>(emptyList()) }
    var panel by remember { mutableStateOf<PanelState?>(null) }

    // Live refresh loop for the overview tab
    LaunchedEffect(server.id) {
        while (true) {
            try {
                metrics = api.metrics(server)
                err = null
            } catch (e: Exception) {
                err = e.message
            }
            try {
                hist = api.history(server)
            } catch (e: Exception) {
            }
            try {
                panel = api.panel(server)
            } catch (e: Exception) {
            }
            delay(10_000)
        }
    }

    // Fetch processes / events when their tab is opened
    LaunchedEffect(server.id, tab) {
        if (tab == 1) {
            try { procs = api.processes(server) } catch (e: Exception) { }
        } else if (tab == 2) {
            try { events = api.events(server, 50) } catch (e: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹", fontSize = 26.sp) }
            Column {
                Text(server.name.ifEmpty { server.host }, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${server.host}:${server.port}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }

        // ── Pin certificate banner ──
        if (server.useTls && server.fingerprint.isEmpty()) {
            val fp = api.lastSeenFingerprint
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF3B2F14),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(t.pinCertHint, fontSize = 12.sp, color = Color(0xFFFBBF24))
                    if (fp != null) {
                        TextButton(onClick = {
                            server.fingerprint = fp
                            val list = Prefs.loadServers(ctx).map { if (it.id == server.id) server else it }
                            Prefs.saveServers(ctx, list)
                        }) {
                            Text(t.pinCert, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ── Tabs ──
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            TabChip(t.overview, tab == 0) { tab = 0 }
            Spacer(Modifier.width(8.dp))
            TabChip(t.processes, tab == 1) { tab = 1 }
            Spacer(Modifier.width(8.dp))
            TabChip(t.events, tab == 2) { tab = 2 }
            Spacer(Modifier.width(8.dp))
            TabChip(t.panel, tab == 3) { tab = 3 }
        }

        // ── Content ──
        when (tab) {
            0 -> OverviewTab(t, metrics, hist, err)
            1 -> ProcessesTab(t, procs)
            2 -> EventsTab(t, events)
            3 -> PanelTab(t, panel)
        }
    }
}

// ── Overview ────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(t: Str, m: Metrics?, hist: List<HistPoint>, err: String?) {
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (err != null) {
                    Text("❌ ${t.error}", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                    Text(err, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    CircularProgressIndicator()
                    Text(t.connecting, fontSize = 13.sp)
                }
            }
        }
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(t.cpu, Fmt.pct(m.cpuUsage), m.cpuUsage / 100f) {
                Sparkline(hist.map { it.cpu }, Modifier.fillMaxWidth().height(46.dp))
                Spacer(Modifier.height(6.dp))
                BreakRow(t.user, m.cpuUser)
                BreakRow(t.system, m.cpuSystem)
                BreakRow(t.iowait, m.cpuIowait)
                BreakRow(t.steal, m.cpuSteal)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${m.cores} ${t.cores}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${t.load}: ${"%.2f".format(Locale.US, m.load1)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(t.memory, Fmt.pct(m.memPct), m.memPct / 100f) {
                Sparkline(hist.map { it.mem }, Modifier.fillMaxWidth().height(46.dp), color = Color(0xFFA78BFA))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Fmt.bytes(m.memUsed)} / ${Fmt.bytes(m.memTotal)}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (m.swapTotal > 0) {
                    Text("${t.swap}: ${Fmt.bytes(m.swapUsed)} / ${Fmt.bytes(m.swapTotal)} (${Fmt.pct(m.swapPct)})",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (m.nets.isNotEmpty()) {
            item {
                Card(t.network, null, null) {
                    m.nets.forEach { n ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("↓ ${n.name}", fontSize = 13.sp)
                            Text("${Fmt.rate(n.rx)}  ↑ ${Fmt.rate(n.tx)}", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (m.disks.isNotEmpty()) {
            item {
                Card(t.disks, null, null) {
                    m.disks.forEach { d ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(d.mount, fontSize = 13.sp)
                                Text("${Fmt.pct(d.pct)} — ${Fmt.bytes(d.used)} / ${Fmt.bytes(d.total)}",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { d.pct / 100f },
                                modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(t.uptime, null, null) {
                Text(Fmt.uptime(m.uptime), fontSize = 14.sp)
                Text(m.hostname, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Processes ───────────────────────────────────────────────────────────────

@Composable
private fun ProcessesTab(t: Str, procs: List<ProcInfo>) {
    if (procs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(t.noData) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(procs, key = { "${it.pid}-${it.name}" }) { p ->
            Surface(shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Text("${p.user}  •  ${Fmt.bytes((p.memMb * 1024 * 1024).toLong())}",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${Fmt.pct(p.cpu)}", color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── Events ("what ate my CPU?") ─────────────────────────────────────────────

@Composable
private fun EventsTab(t: Str, events: List<SpikeEvent>) {
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("😌", fontSize = 32.sp)
                Text(t.noData, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())

    fun eventStyle(type: String): Triple<String, String, Color> = when (type) {
        "cpu" -> Triple("🔥", t.spikeCpu, Color(0xFFF87171))
        "memory" -> Triple("🧠", t.spikeMem, Color(0xFF93C5FD))
        "node_down" -> Triple("🔴", t.eventNodeDown, Color(0xFFF87171))
        "node_up" -> Triple("🟢", t.eventNodeUp, Color(0xFF4ADE80))
        "process_down" -> Triple("💀", t.eventProcessDown, Color(0xFFF87171))
        "process_up" -> Triple("✅", t.eventProcessUp, Color(0xFF4ADE80))
        "disk" -> Triple("💽", t.eventDisk, Color(0xFFFBBF24))
        "steal" -> Triple("🥷", t.eventSteal, Color(0xFFC084FC))
        "panel_down" -> Triple("🔌", t.eventPanelDown, Color(0xFFF87171))
        "panel_up" -> Triple("⚡", t.eventPanelUp, Color(0xFF4ADE80))
        "agent_restart" -> Triple("🔄", t.eventAgentRestart, Color(0xFF94A3B8))
        else -> Triple("•", type, Color(0xFF94A3B8))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { "${it.time}-${it.value}-${it.type}" }) { e ->
            val (emoji, label, color) = eventStyle(e.type)
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$emoji $label",
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = color)
                        Spacer(Modifier.weight(1f))
                        Text(fmt.format(Date(e.time)), fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.detail.isNotBlank()) {
                        Text(e.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.value > 0f) {
                        Text(Fmt.pct(e.value), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (e.top.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(t.topProcesses, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        e.top.take(3).forEach { p ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• ${p.name}", fontSize = 13.sp)
                                Text("${Fmt.pct(p.cpu)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ── PasarGuard Panel tab ─────────────────────────────────────────────────────

@Composable
private fun PanelTab(t: Str, panel: PanelState?) {
    if (panel == null || !panel.configured) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("🔌", fontSize = 32.sp)
                Text(t.panelNotConfigured, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(t.panelNotConfiguredHint, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        return
    }
    if (!panel.ok) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("❌ ${t.eventPanelDown}", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                Text(panel.lastError, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️ PasarGuard", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("v${panel.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(t.usersTotal, panel.totalUsers.toString())
                        StatCell(t.usersOnline, panel.onlineUsers.toString(), highlight = true)
                        StatCell(t.usersActive, panel.activeUsers.toString())
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell(t.usersExpired, panel.expiredUsers.toString())
                        StatCell(t.usersLimited, panel.limitedUsers.toString())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${t.bandwidthIn}: ${Fmt.bytes(panel.inBand)}   ${t.bandwidthOut}: ${Fmt.bytes(panel.outBand)}",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(t.nodes, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
        }
        items(panel.nodes, key = { it.id }) { n ->
            val up = n.status == "connected" || n.status == "connecting"
            Surface(shape = RoundedCornerShape(12.dp),
                color = if (up) Color(0xFF12291C) else Color(0xFF2A1B1B),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(
                        when (n.status) {
                            "connected" -> Color(0xFF4ADE80)
                            "connecting" -> Color(0xFFFBBF24)
                            "error" -> Color(0xFFF87171)
                            "limited" -> Color(0xFFFBBF24)
                            else -> Color(0xFF94A3B8)
                        }, RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text("↓ ${Fmt.bytes(n.downlink)}  ↑ ${Fmt.bytes(n.uplink)}",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(n.status, fontSize = 11.sp,
                        color = if (up) Color(0xFF4ADE80) else Color(0xFFF87171))
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = if (highlight) Color(0xFF4ADE80) else MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Small building blocks ───────────────────────────────────────────────────

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            color = if (selected) Color(0xFF0B1220) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun Card(title: String, bigValue: String?, pct: Float?, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                if (bigValue != null) {
                    Text(bigValue, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (pct != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BreakRow(label: String, value: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fmt.pct(value), fontSize = 11.sp)
    }
}
