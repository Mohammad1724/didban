@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val AGENT_INSTALL_CMD =
    "curl -fsSL https://raw.githubusercontent.com/Mohammad1724/didban/main/agent/install.sh -o didban-install.sh && sudo bash didban-install.sh"

private fun detectFlag(server: ServerConfig): String {
    val name = server.name.lowercase()
    val host = server.host.lowercase()
    return when {
        "ir" in name || "iran" in name || "teh" in name || "mci" in name || "mtn" in name -> "🇮🇷"
        "de" in name || "germany" in name || "fra" in name || "hetzner" in name -> "🇩🇪"
        "fi" in name || "finland" in name || "hel" in name -> "🇫🇮"
        "nl" in name || "netherland" in name || "ams" in name -> "🇳🇱"
        "us" in name || "usa" in name || "america" in name -> "🇺🇸"
        "uk" in name || "london" in name || "gb" in name -> "🇬🇧"
        "fr" in name || "france" in name || "paris" in name -> "🇫🇷"
        "tr" in name || "turkey" in name || "istanbul" in name -> "🇹🇷"
        "sg" in name || "singapore" in name -> "🇸🇬"
        "ae" in name || "dubai" in name -> "🇦🇪"
        else -> "🖥️"
    }
}

@Composable
fun ServersScreen(
    t: Str,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLanguage: (String) -> Unit,
    onOpen: (ServerConfig) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val states by Repo.states.collectAsState()

    var servers by remember { mutableStateOf<List<ServerConfig>>(Prefs.loadServers(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(servers.isEmpty()) }
    var monitoring by remember { mutableStateOf(MonitorService.isRunning) }
    var deletedServer by remember { mutableStateOf<ServerConfig?>(null) }
    var editServer by remember { mutableStateOf<ServerConfig?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf(0) } // 0: All, 1: Online, 2: Offline

    // Rolling telemetry history for sparklines
    var cpuHistory by remember { mutableStateOf<Map<Long, List<Float>>>(emptyMap()) }

    fun refresh() {
        servers = Prefs.loadServers(ctx)
    }

    // Direct background poll loop for active dashboard metrics
    LaunchedEffect(servers) {
        while (true) {
            for (s in servers) {
                try {
                    val t0 = System.currentTimeMillis()
                    val m = ApiClient().metrics(s)
                    val latency = (System.currentTimeMillis() - t0).toFloat()
                    Repo.set(s.id, metrics = m, latencyMs = latency)

                    // Record sparkline history
                    val existing = cpuHistory[s.id] ?: listOf(m.cpuUsage * 0.85f, m.cpuUsage * 1.1f)
                    val updated = (existing + m.cpuUsage).takeLast(12)
                    cpuHistory = cpuHistory + (s.id to updated)
                } catch (e: Exception) {
                    Repo.set(s.id, error = e.message ?: "error", latencyMs = -1f)
                }
            }
            delay(10_000)
        }
    }

    // Fleet summary calculations
    val liveMetrics = servers.mapNotNull { states[it.id]?.metrics }
    val downCount = servers.size - liveMetrics.size
    val avgCpu = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.cpuUsage }.average().toFloat() else -1f
    val avgRam = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.memPct }.average().toFloat() else -1f
    val worstPing = servers.mapNotNull { s -> states[s.id]?.latencyMs?.takeIf { it > 0f } }.maxOrNull() ?: -1f

    // Filtering logic
    val filteredServers = servers.filter { server ->
        val matchesSearch = searchQuery.isBlank() ||
                server.name.contains(searchQuery, ignoreCase = true) ||
                server.host.contains(searchQuery, ignoreCase = true)
        val isOnline = states[server.id]?.metrics != null
        val matchesFilter = when (filterTab) {
            1 -> isOnline
            2 -> !isOnline
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        ModernTopBar(
            title = t.appName,
            subtitle = t.appSubtitle,
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            onRefresh = { refresh() },
            onToggleLang = { onLanguage(if (t.langButton == "EN") "en" else "fa") },
            langLabel = t.langButton
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 1. Cyber Bento Fleet Vitality Hero ──
            item {
                ModernCard(padding = 16.dp, cornerRadius = 22.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(
                                color = if (downCount == 0 && servers.isNotEmpty()) Ds.ok else if (servers.isEmpty()) Ds.textTertiary else Ds.danger,
                                size = 8.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (servers.isEmpty()) t.servers else if (downCount > 0) t.fleetDownTpl.format(downCount, servers.size) else t.fleetAllOk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (servers.isEmpty()) Ds.textPrimary else if (downCount > 0) Ds.danger else Ds.ok
                            )
                        }

                        PrimaryButton(
                            text = t.addServer,
                            icon = Icons.Rounded.Add,
                            onClick = { showAdd = true },
                            modifier = Modifier.height(36.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 3-Column Micro Bento Stat Pods
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pod 1: Server Count
                        BentoMetricTile(
                            title = t.servers,
                            value = if (servers.isEmpty()) "0" else "${servers.size}",
                            unit = "Nodes",
                            color = Ds.accent,
                            modifier = Modifier.weight(1f)
                        )

                        // Pod 2: Avg CPU
                        BentoMetricTile(
                            title = t.statAvgCpu,
                            value = if (avgCpu >= 0) Fmt.pct(avgCpu) else "—",
                            unit = "Avg Load",
                            color = if (avgCpu > 80f) Ds.danger else if (avgCpu > 60f) Ds.warn else Ds.ok,
                            modifier = Modifier.weight(1f)
                        )

                        // Pod 3: Best/Worst Ping
                        BentoMetricTile(
                            title = t.statWorstPing,
                            value = if (worstPing > 0) "${worstPing.toInt()}" else "—",
                            unit = "ms Ping",
                            color = if (worstPing > 250f) Ds.warn else Ds.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Monitoring Switch Bar
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ds.surfaceLow)
                            .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulseDot(color = if (monitoring) Ds.ok else Ds.danger, size = 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (monitoring) t.monitoringOn else t.monitoringOff,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (monitoring) Ds.ok else Ds.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = monitoring,
                            onCheckedChange = { want ->
                                if (want) {
                                    ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
                                    MonitorService.isRunning = true
                                } else {
                                    ctx.stopService(Intent(ctx, MonitorService::class.java))
                                    MonitorService.isRunning = false
                                }
                                monitoring = want
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = Ds.accent,
                                checkedThumbColor = Ds.onAccent,
                                uncheckedTrackColor = Ds.surfaceHighlight,
                                uncheckedBorderColor = Ds.hairlineStrong
                            )
                        )
                    }
                }
            }

            // ── 2. Interactive Step-by-Step Setup Guide (Permanent Expandable Card) ──
            item {
                StepByStepGuideCard(
                    t = t,
                    isExpanded = showGuide,
                    onToggleExpand = { showGuide = !showGuide },
                    onAddServer = { showAdd = true }
                )
            }

            // ── 3. Search & Filter Bar (Shown when multiple servers exist) ──
            if (servers.size > 2) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search servers by name or IP...",
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedControl(
                            items = listOf("All (${servers.size})", "Online (${liveMetrics.size})", "Down ($downCount)"),
                            selectedIndex = filterTab,
                            onSelect = { filterTab = it },
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }
            }

            // ── 4. Empty State or Cyber Bento Server Deck ──
            if (servers.isEmpty()) {
                item {
                    EmptyState(
                        title = t.noServers,
                        hint = t.noServersHint,
                        icon = Icons.Rounded.Dns,
                        radar = false,
                        actionLabel = t.addServer,
                        onAction = { showAdd = true }
                    )
                }
            } else {
                items(filteredServers, key = { it.id }) { server ->
                    val state = states[server.id]
                    val metrics = state?.metrics
                    val isOnline = metrics != null
                    val ping = state?.latencyMs ?: -1f
                    val history = cpuHistory[server.id] ?: if (metrics != null) listOf(metrics.cpuUsage * 0.9f, metrics.cpuUsage * 1.05f, metrics.cpuUsage) else emptyList()

                    ServerBentoCard(
                        server = server,
                        metrics = metrics,
                        isOnline = isOnline,
                        ping = ping,
                        cpuHistory = history,
                        t = t,
                        onClick = { onOpen(server) },
                        onEdit = { editServer = server },
                        onDelete = { deletedServer = server }
                    )
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }

    // Modals
    if (showAdd) {
        AddServerDialog(
            t = t,
            onDismiss = { showAdd = false },
            onSaved = {
                refresh()
                showAdd = false
            }
        )
    }

    editServer?.let { server ->
        EditServerDialog(
            t = t,
            server = server,
            onDismiss = { editServer = null },
            onSaved = {
                refresh()
                editServer = null
            }
        )
    }

    deletedServer?.let { server ->
        AlertDialog(
            onDismissRequest = { deletedServer = null },
            title = { Text(t.confirmDelete, fontWeight = FontWeight.Bold, color = Ds.danger) },
            text = { Text("${server.name} (${server.host})", color = Ds.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val list = Prefs.loadServers(ctx)
                    list.removeAll { it.id == server.id }
                    Prefs.saveServers(ctx, list)
                    refresh()
                    deletedServer = null
                }) {
                    Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletedServer = null }) {
                    Text(t.cancel, color = Ds.textSecondary)
                }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// COMPONENT: Cyber Bento Metric Tile
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun BentoMetricTile(
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
// COMPONENT: Step-by-Step Server Onboarding Bento Guide
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepByStepGuideCard(
    t: Str,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddServer: () -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current

    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp,
        modifier = Modifier.border(
            BorderStroke(1.dp, Brush.horizontalGradient(listOf(Ds.hairline, Ds.accent.copy(alpha = 0.35f), Ds.hairline))),
            RoundedCornerShape(20.dp)
        )
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
                        Text(t.showGuide, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Linux Server Agent Setup (3 Steps)", fontSize = 10.5.sp, color = Ds.textTertiary)
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

                    // Step 1: Run Installer Script
                    StepItemPod(
                        stepNum = "1",
                        title = t.serverAddStep1Title,
                        desc = t.serverAddStep1Desc
                    ) {
                        TerminalBox(
                            command = AGENT_INSTALL_CMD,
                            title = "Linux 1-Line Installer"
                        )
                    }

                    // Step 2: Copy Connection Link
                    StepItemPod(
                        stepNum = "2",
                        title = t.serverAddStep2Title,
                        desc = t.serverAddStep2Desc
                    )

                    // Step 3: Connect & Encrypt
                    StepItemPod(
                        stepNum = "3",
                        title = t.serverAddStep3Title,
                        desc = t.serverAddStep3Desc
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PrimaryButton(
                                text = t.addServer,
                                icon = Icons.Rounded.Add,
                                onClick = onAddServer,
                                modifier = Modifier.weight(1f).height(38.dp)
                            )
                            SoftButton(
                                text = t.smartPaste,
                                icon = Icons.Rounded.ContentPaste,
                                onClick = {
                                    val clipText = clipboard.getText()?.text ?: ""
                                    val parsed = parseDeepLinkOrLogs(clipText)
                                    if (parsed != null) {
                                        val list = Prefs.loadServers(ctx)
                                        list.add(parsed)
                                        Prefs.saveServers(ctx, list)
                                        Toast.makeText(ctx, t.clipboardParsed, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, t.clipboardNotFound, Toast.LENGTH_SHORT).show()
                                        onAddServer()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(38.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItemPod(
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
// COMPONENT: Cyber Bento Server Card (Live Sparkline & Telemetry Deck)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ServerBentoCard(
    server: ServerConfig,
    metrics: Metrics?,
    isOnline: Boolean,
    ping: Float,
    cpuHistory: List<Float>,
    t: Str,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val flag = detectFlag(server)

    ModernCard(
        padding = 14.dp,
        cornerRadius = 20.dp,
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Flag + Name + Ping + Status
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Ds.surfaceElevated)
                            .border(BorderStroke(1.dp, Ds.hairlineStrong), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(flag, fontSize = 17.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                server.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            PulseDot(
                                color = if (isOnline) Ds.ok else Ds.danger,
                                size = 6.dp,
                                pulsing = isOnline
                            )
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            "${server.host}:${server.port}",
                            fontSize = 11.sp,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Latency Badge Pill
                if (ping > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ping > 200f) Ds.dangerDim else if (ping > 90f) Ds.warnDim else Ds.accentDim)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (ping > 200f) Ds.danger.copy(alpha = 0.35f) else if (ping > 90f) Ds.warn.copy(alpha = 0.35f) else Ds.accent.copy(alpha = 0.35f)
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "⚡ ${ping.toInt()} ms",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = Telemetry,
                            color = if (ping > 200f) Ds.danger else if (ping > 90f) Ds.warn else Ds.accent
                        )
                    }
                } else {
                    StatusPill(
                        text = if (isOnline) t.online else t.offline,
                        level = if (isOnline) StatusLevel.Ok else StatusLevel.Danger
                    )
                }
            }

            // Live Telemetry Sparkline Box
            if (metrics != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ds.surfaceLow)
                        .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(t.lblCpuUse, fontSize = 10.sp, color = Ds.textTertiary)
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    Fmt.pct(metrics.cpuUsage),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = Telemetry,
                                    color = if (metrics.cpuUsage > 80f) Ds.danger else if (metrics.cpuUsage > 60f) Ds.warn else Ds.accent
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "${metrics.cores} Cores",
                                    fontSize = 10.sp,
                                    color = Ds.textSecondary,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                        }

                        // SVG Sparkline Wave
                        Sparkline(
                            values = if (cpuHistory.size >= 2) cpuHistory else listOf(metrics.cpuUsage * 0.85f, metrics.cpuUsage * 1.1f, metrics.cpuUsage),
                            color = if (metrics.cpuUsage > 80f) Ds.danger else if (metrics.cpuUsage > 60f) Ds.warn else Ds.accent,
                            modifier = Modifier
                                .size(width = 110.dp, height = 28.dp)
                        )
                    }
                }

                // Sub-Metrics Bento Strip: RAM & Uptime
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // RAM Mini Pod
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ds.surfaceLow)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(t.lblRamUse, fontSize = 9.5.sp, color = Ds.textTertiary)
                            Text(
                                "${Fmt.pct(metrics.memPct)} (${Fmt.bytes(metrics.memUsed)})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.violet
                            )
                        }
                    }

                    // Uptime Mini Pod
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ds.surfaceLow)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(t.lblUptime, fontSize = 9.5.sp, color = Ds.textTertiary)
                            Text(
                                "⏱ ${Fmt.uptime(metrics.uptime)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = Telemetry,
                                color = Ds.textSecondary
                            )
                        }
                    }
                }
            }

            Hairline()

            // Card Footer: Actions
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onClick() }
                ) {
                    Text(
                        "Open Telemetry Dashboard",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ds.accent
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = "Open",
                        tint = Ds.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CircleIconButton(
                        icon = Icons.Rounded.Edit,
                        contentDescription = "Edit",
                        size = 28.dp,
                        tint = Ds.textSecondary,
                        onClick = onEdit
                    )
                    CircleIconButton(
                        icon = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        size = 28.dp,
                        tint = Ds.danger,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

// ── Add Server Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddServerDialog(t: Str, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var tab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.addServer, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                SegmentedControl(
                    items = listOf(t.sshInstall, t.manual, "1-Line Script"),
                    selectedIndex = tab,
                    onSelect = { tab = it }
                )
            }
        },
        text = {
            when (tab) {
                0 -> SshInstallTab(t = t, onSaved = onSaved)
                1 -> ManualAddTab(t = t, onSaved = onSaved)
                2 -> ScriptInstallGuide(t = t)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t.cancel, color = Ds.textSecondary)
            }
        }
    )
}

@Composable
private fun ManualAddTab(t: Str, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var useTls by remember { mutableStateOf(false) }
    var fingerprint by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(380.dp).imePadding()
    ) {
        item {
            SoftButton(
                text = t.smartPaste,
                icon = Icons.Rounded.ContentPaste,
                onClick = {
                    val text = clipboard.getText()?.text ?: ""
                    val parsed = parseDeepLinkOrLogs(text)
                    if (parsed != null) {
                        name = parsed.name
                        host = parsed.host
                        port = parsed.port.toString()
                        token = parsed.token
                        useTls = parsed.useTls
                        fingerprint = parsed.fingerprint
                        Toast.makeText(ctx, t.clipboardParsed, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, t.clipboardNotFound, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { InputField(value = name, onValueChange = { name = it }, label = t.name, placeholder = "e.g. Frankfurt Primary") }
        item { InputField(value = host, onValueChange = { host = it }, label = t.host, placeholder = "192.168.1.1 or vps.example.com") }
        item { InputField(value = port, onValueChange = { port = it }, label = t.port, placeholder = "8686") }
        item { InputField(value = token, onValueChange = { token = it }, label = t.token, placeholder = "Agent secret token", isPassword = true) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t.useTls, fontSize = 12.sp, color = Ds.textPrimary)
                Switch(
                    checked = useTls,
                    onCheckedChange = { useTls = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                )
            }
        }
        if (useTls) {
            item {
                MonoTextField(
                    value = fingerprint,
                    onValueChange = { fingerprint = it },
                    label = t.fingerprint,
                    placeholder = "SHA-256 fingerprint"
                )
            }
        }
        item {
            PrimaryButton(
                text = t.save,
                onClick = {
                    val list = Prefs.loadServers(ctx)
                    list.add(
                        ServerConfig(
                            id = System.currentTimeMillis(),
                            name = name.ifBlank { host },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 8686,
                            token = token.trim(),
                            useTls = useTls,
                            fingerprint = fingerprint.trim()
                        )
                    )
                    Prefs.saveServers(ctx, list)
                    onSaved()
                },
                enabled = host.isNotBlank() && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SshInstallTab(t: Str, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("root") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SshSetup.Result?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(380.dp)
    ) {
        item { Text(t.sshInstallHint, fontSize = 11.5.sp, color = Ds.textSecondary, lineHeight = 17.sp) }
        item { InputField(value = name, onValueChange = { name = it }, label = t.name, placeholder = "e.g. My Ubuntu VPS") }
        item { InputField(value = host, onValueChange = { host = it }, label = t.host, placeholder = "Server IP") }
        item { InputField(value = sshPort, onValueChange = { sshPort = it }, label = t.port, placeholder = "22") }
        item { InputField(value = user, onValueChange = { user = it }, label = t.sshUser, placeholder = "root") }
        item { InputField(value = pass, onValueChange = { pass = it }, label = t.sshPassword, isPassword = true) }

        if (busy) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(color = Ds.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(18.dp))
                    Text(t.installing, fontSize = 12.5.sp, color = Ds.textSecondary)
                }
            }
        }

        result?.let { r ->
            item {
                BannerCard(
                    text = if (r.success) t.installDone else "${t.installFailed}: ${r.error}",
                    tone = if (r.success) BannerTone.Ok else BannerTone.Danger,
                    icon = if (r.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
                )
            }
        }

        item {
            PrimaryButton(
                text = if (busy) t.installing else t.install,
                loading = busy,
                enabled = !busy && host.isNotBlank() && pass.isNotBlank(),
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        val r = SshSetup.installAgent(
                            host = host.trim(),
                            sshPort = sshPort.toIntOrNull() ?: 22,
                            user = user.trim(),
                            password = pass
                        )
                        if (r.success && r.token != null) {
                            val list = Prefs.loadServers(ctx)
                            list.add(
                                ServerConfig(
                                    id = System.currentTimeMillis(),
                                    name = name.ifBlank { host },
                                    host = host.trim(),
                                    port = r.port ?: 8686,
                                    token = r.token ?: "",
                                    useTls = r.fingerprint != null,
                                    fingerprint = r.fingerprint ?: ""
                                )
                            )
                            Prefs.saveServers(ctx, list)
                            busy = false
                            result = r
                            onSaved()
                        } else {
                            busy = false
                            result = r
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ScriptInstallGuide(t: Str) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(320.dp)
    ) {
        Text(
            t.quickConnectBody,
            fontSize = 12.sp,
            color = Ds.textSecondary,
            lineHeight = 17.5.sp
        )
        TerminalBox(
            command = AGENT_INSTALL_CMD,
            title = "Linux 1-Click Installer"
        )
        Text(
            "After running the script, Didban will print a one-click connection link (didban://...). Copy it and click 'Smart-paste' in the manual tab.",
            fontSize = 11.sp,
            color = Ds.textTertiary,
            lineHeight = 16.sp
        )
    }
}

// ── Edit Server Dialog ──────────────────────────────────────────────────────

@Composable
private fun EditServerDialog(t: Str, server: ServerConfig, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(server.name) }
    var host by remember { mutableStateOf(server.host) }
    var port by remember { mutableStateOf(server.port.toString()) }
    var token by remember { mutableStateOf(server.token) }
    var tls by remember { mutableStateOf(server.useTls) }
    var fp by remember { mutableStateOf(server.fingerprint) }
    var cpuAlert by remember { mutableStateOf(server.cpuAlert.toString()) }
    var memAlert by remember { mutableStateOf(server.memAlert.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${t.edit} — ${server.name}", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(380.dp).imePadding()
            ) {
                item { InputField(value = name, onValueChange = { name = it }, label = t.name) }
                item { InputField(value = host, onValueChange = { host = it }, label = t.host) }
                item { InputField(value = port, onValueChange = { port = it }, label = t.port) }
                item { InputField(value = token, onValueChange = { token = it }, label = t.token, isPassword = true) }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(t.useTls, fontSize = 12.sp, color = Ds.textPrimary)
                        Switch(
                            checked = tls,
                            onCheckedChange = { tls = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                        )
                    }
                }
                item { MonoTextField(value = fp, onValueChange = { fp = it }, label = t.fingerprint) }
                item { InputField(value = cpuAlert, onValueChange = { cpuAlert = it }, label = t.cpuAlertLbl) }
                item { InputField(value = memAlert, onValueChange = { memAlert = it }, label = t.memAlertLbl) }
                item {
                    PrimaryButton(
                        text = t.save,
                        onClick = {
                            val list = Prefs.loadServers(ctx)
                            val idx = list.indexOfFirst { it.id == server.id }
                            if (idx >= 0) {
                                list[idx].name = name
                                list[idx].host = host.trim()
                                list[idx].port = port.toIntOrNull() ?: server.port
                                list[idx].token = token.trim()
                                list[idx].useTls = tls
                                list[idx].fingerprint = fp.trim()
                                list[idx].cpuAlert = cpuAlert.toIntOrNull() ?: server.cpuAlert
                                list[idx].memAlert = memAlert.toIntOrNull() ?: server.memAlert
                                Prefs.saveServers(ctx, list)
                                onSaved()
                            }
                        },
                        enabled = host.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

private fun parseDeepLinkOrLogs(text: String): ServerConfig? {
    val clean = text.trim()
    if (clean.startsWith("didban://")) {
        try {
            val withoutScheme = clean.removePrefix("didban://")
            val parts = withoutScheme.split("?")
            val hostPort = parts[0].split(":")
            val host = hostPort[0]
            val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 8686 else 8686
            var token = ""
            var fp = ""
            var name = host
            if (parts.size > 1) {
                val query = parts[1].split("&")
                for (param in query) {
                    val kv = param.split("=")
                    if (kv.size == 2) {
                        when (kv[0]) {
                            "token" -> token = kv[1]
                            "fp" -> fp = kv[1]
                            "name" -> name = java.net.URLDecoder.decode(kv[1], "UTF-8")
                        }
                    }
                }
            }
            if (host.isNotBlank() && token.isNotBlank()) {
                return ServerConfig(
                    id = System.currentTimeMillis(),
                    name = name,
                    host = host,
                    port = port,
                    token = token,
                    useTls = fp.isNotBlank(),
                    fingerprint = fp
                )
            }
        } catch (_: Exception) {}
    }
    return null
}
