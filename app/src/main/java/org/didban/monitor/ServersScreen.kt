@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
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
    var monitoring by remember { mutableStateOf(MonitorService.isRunning) }
    var deletedServer by remember { mutableStateOf<ServerConfig?>(null) }
    var editServer by remember { mutableStateOf<ServerConfig?>(null) }

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
                    Repo.set(s.id, metrics = m, latencyMs = (System.currentTimeMillis() - t0).toFloat())
                } catch (e: Exception) {
                    Repo.set(s.id, error = e.message ?: "error", latencyMs = -1f)
                }
            }
            delay(12_000)
        }
    }

    // Fleet summary calculations
    val liveMetrics = servers.mapNotNull { states[it.id]?.metrics }
    val downCount = servers.size - liveMetrics.size
    val avgCpu = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.cpuUsage }.average().toFloat() else -1f
    val avgRam = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.memPct }.average().toFloat() else -1f
    val worstPing = servers.mapNotNull { s -> states[s.id]?.latencyMs?.takeIf { it > 0f } }.maxOrNull() ?: -1f

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
            // ── 1. Fleet Overview Hero Bento ──
            item {
                ModernCard(padding = 16.dp, cornerRadius = 20.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseDot(
                                color = if (downCount == 0 && servers.isNotEmpty()) Ds.ok else if (servers.isEmpty()) Ds.accent else Ds.danger,
                                size = 8.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    servers.isEmpty() -> t.noServers
                                    downCount > 0 -> t.fleetDownTpl.format(downCount, servers.size)
                                    else -> t.fleetAllOk
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    servers.isEmpty() -> Ds.textPrimary
                                    downCount > 0 -> Ds.danger
                                    else -> Ds.ok
                                }
                            )
                        }

                        PrimaryButton(
                            text = t.addServer,
                            icon = Icons.Rounded.Add,
                            onClick = { showAdd = true },
                            modifier = Modifier.height(38.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    StatBand(
                        stats = listOf(
                            StatItem(t.servers, if (servers.isEmpty()) "0" else "${servers.size}", Ds.accent),
                            StatItem(
                                t.statAvgCpu,
                                if (avgCpu >= 0) Fmt.pct(avgCpu) else "—",
                                if (avgCpu > 85f) Ds.danger else if (avgCpu > 60f) Ds.warn else Ds.ok,
                                if (avgCpu >= 0) avgCpu / 100f else null
                            ),
                            StatItem(
                                t.statAvgRam,
                                if (avgRam >= 0) Fmt.pct(avgRam) else "—",
                                Ds.violet,
                                if (avgRam >= 0) avgRam / 100f else null
                            ),
                            StatItem(
                                t.statWorstPing,
                                if (worstPing > 0) "${worstPing.toInt()} ms" else "—",
                                if (worstPing > 250f) Ds.warn else Ds.textPrimary
                            )
                        )
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
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

            // ── 2. Empty State or Server Cards ──
            if (servers.isEmpty()) {
                item {
                    EmptyState(
                        title = t.noServers,
                        hint = t.noServersHint,
                        radar = true,
                        actionLabel = t.addServer,
                        onAction = { showAdd = true }
                    )
                }
                item {
                    TerminalBox(
                        command = AGENT_INSTALL_CMD,
                        title = "One-Line Agent Installer"
                    )
                }
            } else {
                items(servers, key = { it.id }) { server ->
                    val state = states[server.id]
                    val metrics = state?.metrics
                    val isOnline = metrics != null
                    val ping = state?.latencyMs ?: -1f

                    ModernCard(
                        padding = 14.dp,
                        cornerRadius = 18.dp,
                        onClick = { onOpen(server) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconBadge(
                                icon = Icons.Rounded.Dns,
                                tint = if (isOnline) Ds.accent else Ds.danger,
                                background = if (isOnline) Ds.accentDim else Ds.dangerDim,
                                size = 42.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
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
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${server.host}:${server.port}",
                                    fontSize = 11.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Telemetry ring gauges
                            if (metrics != null) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RingGauge(
                                            value = metrics.cpuUsage,
                                            size = 38.dp,
                                            strokeWidth = 3.5.dp,
                                            tone = if (metrics.cpuUsage > 80f) Ds.danger else if (metrics.cpuUsage > 60f) Ds.warn else Ds.accent
                                        ) {
                                            Text(
                                                "${metrics.cpuUsage.toInt()}%",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Telemetry,
                                                color = Ds.textPrimary
                                            )
                                        }
                                        Text(t.cpu, fontSize = 9.sp, color = Ds.textTertiary)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        RingGauge(
                                            value = metrics.memPct,
                                            size = 38.dp,
                                            strokeWidth = 3.5.dp,
                                            tone = Ds.violet
                                        ) {
                                            Text(
                                                "${metrics.memPct.toInt()}%",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = Telemetry,
                                                color = Ds.textPrimary
                                            )
                                        }
                                        Text(t.memory, fontSize = 9.sp, color = Ds.textTertiary)
                                    }
                                }
                            } else {
                                StatusPill(
                                    text = if (state?.error != null) t.offline else t.connecting,
                                    level = if (state?.error != null) StatusLevel.Danger else StatusLevel.Warn
                                )
                            }
                        }

                        // Bottom row: Latency + Actions
                        Spacer(Modifier.height(10.dp))
                        Hairline()
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (ping > 0) {
                                    Text(
                                        "⚡ ${ping.toInt()} ms",
                                        fontSize = 11.sp,
                                        fontFamily = Telemetry,
                                        color = if (ping > 250f) Ds.warn else Ds.textSecondary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                if (metrics != null) {
                                    Text(
                                        "⏱ ${Fmt.uptime(metrics.uptime)}",
                                        fontSize = 11.sp,
                                        fontFamily = Telemetry,
                                        color = Ds.textTertiary
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CircleIconButton(
                                    icon = Icons.Rounded.Edit,
                                    contentDescription = "Edit",
                                    size = 30.dp,
                                    tint = Ds.textSecondary,
                                    onClick = { editServer = server }
                                )
                                CircleIconButton(
                                    icon = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete",
                                    size = 30.dp,
                                    tint = Ds.danger,
                                    onClick = { deletedServer = server }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Add Server Modal ──
    if (showAdd) {
        AddServerDialog(
            t = t,
            onDismiss = { showAdd = false },
            onSaved = {
                showAdd = false
                refresh()
            }
        )
    }

    // ── Edit Server Modal ──
    editServer?.let { s ->
        EditServerDialog(
            t = t,
            server = s,
            onDismiss = { editServer = null },
            onSaved = {
                editServer = null
                refresh()
            }
        )
    }

    // ── Delete Confirmation ──
    deletedServer?.let { s ->
        AlertDialog(
            onDismissRequest = { deletedServer = null },
            title = { Text(t.confirmDelete, fontWeight = FontWeight.Bold) },
            text = { Text("${t.delete} ${s.name} (${s.host})?") },
            confirmButton = {
                TextButton(onClick = {
                    val list = Prefs.loadServers(ctx).filterNot { it.id == s.id }
                    Prefs.saveServers(ctx, list)
                    deletedServer = null
                    refresh()
                }) {
                    Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletedServer = null }) { Text(t.cancel) }
            }
        )
    }
}

// ── Add Server Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddServerDialog(
    t: Str,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) } // 0: Direct/Manual, 1: SSH 1-Click, 2: Script

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.addServer, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                SegmentedControl(
                    items = listOf(t.manual, t.sshInstall, "Script"),
                    selectedIndex = tab,
                    onSelect = { tab = it }
                )
                Spacer(Modifier.height(14.dp))

                when (tab) {
                    0 -> ManualAddForm(t = t, onSaved = onSaved)
                    1 -> SshInstallForm(t = t, onSaved = onSaved)
                    2 -> ScriptInstallGuide(t = t)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

@Composable
private fun ManualAddForm(t: Str, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var tls by remember { mutableStateOf(true) }
    var fp by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(380.dp)
    ) {
        item {
            SoftButton(
                text = t.smartPaste,
                icon = Icons.Rounded.ContentPaste,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val clip = clipboard.getText()?.text ?: ""
                    val parsed = parseDeepLinkOrLogs(clip)
                    if (parsed != null) {
                        host = parsed.host
                        port = parsed.port.toString()
                        token = parsed.token
                        tls = parsed.useTls
                        fp = parsed.fingerprint
                        if (name.isBlank()) name = parsed.name
                        Toast.makeText(ctx, t.clipboardParsed, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, t.clipboardNotFound, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        item { InputField(value = name, onValueChange = { name = it }, label = t.name, placeholder = "e.g. Frankfurt Main Node") }
        item { InputField(value = host, onValueChange = { host = it }, label = t.host, placeholder = "IP or domain") }
        item { InputField(value = port, onValueChange = { port = it }, label = t.port, placeholder = "8686") }
        item { InputField(value = token, onValueChange = { token = it }, label = t.token, isPassword = true, placeholder = "Agent token") }
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
        item { MonoTextField(value = fp, onValueChange = { fp = it }, label = t.fingerprint, placeholder = "SHA256 Fingerprint (optional)") }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SoftButton(
                    text = if (isTesting) t.connecting else t.testConnection,
                    icon = Icons.Rounded.Bolt,
                    enabled = !isTesting && host.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val testCfg = ServerConfig(
                                id = 0,
                                name = name.ifBlank { host },
                                host = host.trim(),
                                port = port.toIntOrNull() ?: 8686,
                                token = token.trim(),
                                useTls = tls,
                                fingerprint = fp.trim()
                            )
                            try {
                                val t0 = System.currentTimeMillis()
                                val m = ApiClient().metrics(testCfg)
                                val elapsed = System.currentTimeMillis() - t0
                                testSuccess = true
                                testResult = "${t.testOk} (${elapsed}ms — CPU: ${Fmt.pct(m.cpuUsage)})"
                            } catch (e: Exception) {
                                testSuccess = false
                                testResult = "${t.testFail}: ${e.message}"
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                PrimaryButton(
                    text = t.save,
                    enabled = host.isNotBlank() && token.isNotBlank(),
                    onClick = {
                        val list = Prefs.loadServers(ctx)
                        list.add(ServerConfig(
                            id = System.currentTimeMillis(),
                            name = name.ifBlank { host },
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 8686,
                            token = token.trim(),
                            useTls = tls,
                            fingerprint = fp.trim()
                        ))
                        Prefs.saveServers(ctx, list)
                        onSaved()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SshInstallForm(t: Str, onSaved: () -> Unit) {
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
                            list.add(ServerConfig(
                                id = System.currentTimeMillis(),
                                name = name.ifBlank { host },
                                host = host.trim(),
                                port = r.port ?: 8686,
                                token = r.token ?: "",
                                useTls = r.fingerprint != null,
                                fingerprint = r.fingerprint ?: ""
                            ))
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
