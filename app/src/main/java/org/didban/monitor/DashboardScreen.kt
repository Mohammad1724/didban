@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.Intent
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(t: Str, server: ServerConfig, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val api = remember { ApiClient() }
    var tab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()

    // Keep the tab chips and the pager in sync (swipe ↔ chip tap)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { tab = it }
    }

    var metrics by remember { mutableStateOf<Metrics?>(null) }
    var hist by remember { mutableStateOf<List<HistPoint>>(emptyList()) }
    var latency by remember { mutableStateOf(0f) }
    var latHist by remember { mutableStateOf<List<Float>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var procs by remember { mutableStateOf<List<ProcInfo>>(emptyList()) }
    var events by remember { mutableStateOf<List<SpikeEvent>>(emptyList()) }

    // Live refresh loop for the overview tab
    LaunchedEffect(server.id) {
        while (true) {
            try {
                val t0 = System.currentTimeMillis()
                metrics = api.metrics(server)
                latency = (System.currentTimeMillis() - t0).toFloat()
                latHist = (latHist + latency).takeLast(120)
                err = null
            } catch (e: Exception) {
                err = e.message
                latency = -1f
            }
            try {
                hist = api.history(server)
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
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2E2410),
                border = BorderStroke(1.dp, Color(0xFF5C4A1E)),
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
            TabChip(t.overview, tab == 0) { scope.launch { pagerState.animateScrollToPage(0) } }
            Spacer(Modifier.width(8.dp))
            TabChip(t.processes, tab == 1) { scope.launch { pagerState.animateScrollToPage(1) } }
            Spacer(Modifier.width(8.dp))
            TabChip(t.events, tab == 2) { scope.launch { pagerState.animateScrollToPage(2) } }
            if (tab == 2) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { shareEvents(ctx, t, server, events) }) {
                    Text("⇪ ${t.share}", fontSize = 13.sp)
                }
            }
        }

        // ── Content (swipeable pages) ──
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> OverviewTab(t, metrics, hist, err, latency, latHist)
                1 -> ProcessesTab(t, procs)
                2 -> EventsTab(t, events)
            }
        }
    }
}

// ── Overview ────────────────────────────────────────────────────────────────

@Composable
private fun OverviewTab(t: Str, m: Metrics?, hist: List<HistPoint>, err: String?, latency: Float, latHist: List<Float>) {
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
            Card(t.latency, if (latency >= 0f) "${latency.toInt()} ms" else "—", null) {
                if (latHist.size > 1) {
                    Sparkline(latHist, Modifier.fillMaxWidth().height(40.dp), color = Color(0xFF4ADE80))
                }
            }
        }
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
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
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
        "process_down" -> Triple("💀", t.eventProcessDown, Color(0xFFF87171))
        "process_up" -> Triple("✅", t.eventProcessUp, Color(0xFF4ADE80))
        "disk" -> Triple("💽", t.eventDisk, Color(0xFFFBBF24))
        "steal" -> Triple("🥷", t.eventSteal, Color(0xFFC084FC))
        "agent_restart" -> Triple("🔄", t.eventAgentRestart, Color(0xFF94A3B8))
        else -> Triple("•", type, Color(0xFF94A3B8))
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { "${it.time}-${it.value}-${it.type}" }) { e ->
            val (emoji, label, color) = eventStyle(e.type)
            Surface(shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
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

// ── Small building blocks ───────────────────────────────────────────────────

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Card(title: String, bigValue: String?, pct: Float?, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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


// ── Share events as text ────────────────────────────────────────────────────

private fun shareEvents(ctx: android.content.Context, t: Str, server: ServerConfig, events: List<SpikeEvent>) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val sb = StringBuilder()
    sb.appendLine("Didban — ${server.name} (${server.host}:${server.port})")
    sb.appendLine("───")
    if (events.isEmpty()) {
        sb.appendLine("(no events)")
    }
    for (e in events) {
        val value = if (e.value > 0f) " ${Fmt.pct(e.value)}" else ""
        val detail = if (e.detail.isNotBlank()) " — ${e.detail}" else ""
        sb.appendLine("${fmt.format(Date(e.time))}  ${e.type}$value$detail")
        for (p in e.top.take(5)) {
            sb.appendLine("     • ${p.name}  cpu=${Fmt.pct(p.cpu)}  mem=${"%.0f".format(Locale.US, p.memMb)}MB")
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(send, t.share))
}
