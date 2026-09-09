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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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

    // Direct poll loop for active visibility
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

    // ── Fleet aggregates ──
    val liveMetrics = servers.mapNotNull { states[it.id]?.metrics }
    val downCount = servers.size - liveMetrics.size
    val avgCpu = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.cpuUsage }.average().toFloat() else -1f
    val avgRam = if (liveMetrics.isNotEmpty()) liveMetrics.map { it.memPct }.average().toFloat() else -1f
    val worstPing = servers.mapNotNull { s -> states[s.id]?.latencyMs?.takeIf { it > 0f } }.maxOrNull() ?: -1f

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── 1. Fleet hero ──
            item {
                ModernCard(padding = 18.dp, cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(11.dp).background(
                            if (downCount == 0 && servers.isNotEmpty()) Ds.ok else if (servers.isEmpty()) Ds.accent else Ds.danger,
                            CircleShape
                        ))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            when {
                                servers.isEmpty() -> t.noServers
                                downCount > 0 -> t.fleetDownTpl.format(downCount, servers.size)
                                else -> t.fleetAllOk
                            },
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            color = when {
                                servers.isEmpty() -> Ds.textPrimary
                                downCount > 0 -> Ds.danger
                                else -> Ds.ok
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulseDot(isOnline = monitoring, size = 7.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (monitoring) t.monitoringOn else t.monitoringOff,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (monitoring) Ds.ok else Ds.danger,
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
                                uncheckedTrackColor = Ds.surfaceHigh,
                                uncheckedBorderColor = Ds.hairlineStrong
                            )
                        )
                    }
                }
            }

            // ── 2. Server console rows / Onboarding ──
            if (servers.isEmpty()) {
                item {
                    ModernCard(padding = 18.dp, cornerRadius = 22.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            RadarMark(diameter = 76.dp, tint = Ds.accent.copy(alpha = 0.85f))
                            Spacer(Modifier.height(16.dp))
                            Text(t.quickConnectGuide, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                t.quickConnectBody,
                                fontSize = 11.5.sp,
                                color = Ds.textSecondary,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        TerminalBox(
                            text = AGENT_INSTALL_CMD,
                            copyLabel = t.copy,
                            onCopy = {
                                clipboard.setPrimaryClip(ClipData.newPlainText("install_cmd", AGENT_INSTALL_CMD))
                                Toast.makeText(ctx, t.cmdCopied, Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryActionButton(
                            text = t.addServer,
                            icon = Icons.Rounded.Add,
                            onClick = { showAdd = true }
                        )
                    }
                }
            } else {
                items(servers, key = { it.id }) { s ->
                    val st = states[s.id]
                    ServerConsoleRow(
                        t = t,
                        s = s,
                        st = st,
                        onOpen = { onOpen(s) },
                        onEdit = { editServer = s },
                        onDelete = { deletedServer = s }
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    SecondaryActionCard(
                        title = t.addServer,
                        icon = Icons.Rounded.Add,
                        onClick = { showAdd = true }
                    )
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (showAdd) {
        AddServerDialog(
            t = t,
            onDismiss = { showAdd = false },
            onSaved = { refresh(); showAdd = false }
        )
    }

    editServer?.let { es ->
        EditServerDialog(
            t = t,
            server = es,
            onDismiss = { editServer = null },
            onSaved = { refresh(); editServer = null }
        )
    }

    deletedServer?.let { ds ->
        AlertDialog(
            onDismissRequest = { deletedServer = null },
            title = { Text(t.confirmDelete, fontWeight = FontWeight.Bold) },
            text = { Text("${ds.name} (${ds.host})") },
            confirmButton = {
                TextButton(onClick = {
                    servers = Prefs.loadServers(ctx).filter { it.id != ds.id }
                    Prefs.saveServers(ctx, servers)
                    deletedServer = null
                }) { Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletedServer = null }) { Text(t.cancel) }
            }
        )
    }
}

// ── Server console row ───────────────────────────────────────────────────────

@Composable
private fun ServerConsoleRow(
    t: Str,
    s: ServerConfig,
    st: Repo.State?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val m = st?.metrics
    val isOnline = m != null
    val cpu = m?.cpuUsage ?: 0f

    @Composable
    fun cpuTone(): Color = when {
        !isOnline -> Ds.danger
        cpu > 85f -> Ds.danger
        cpu > 60f -> Ds.warn
        else -> Ds.ok
    }

    ModernCard(
        modifier = Modifier.fillMaxWidth(),
        padding = 13.dp,
        onClick = onOpen
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // live CPU ring — the fleet console's glyph
            RingGauge(
                value = if (isOnline) cpu else 0f,
                size = 48.dp,
                strokeWidth = 4.5.dp,
                tone = cpuTone()
            ) {
                Text(
                    if (isOnline) "${cpu.toInt()}" else "!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Telemetry,
                    color = cpuTone()
                )
            }

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name.ifEmpty { s.host },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Ds.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    PulseDot(isOnline = isOnline, size = 6.dp)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "${s.host}:${s.port}",
                    color = Ds.textTertiary,
                    fontSize = 10.5.sp,
                    fontFamily = Telemetry,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (m != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "RAM ${Fmt.pct(m.memPct)}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = Telemetry,
                            color = Ds.violet
                        )
                        val lat = st?.latencyMs ?: 0f
                        if (lat > 0f) {
                            Text(
                                "${lat.toInt()} ms",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = Telemetry,
                                color = if (lat > 250f) Ds.warn else Ds.textSecondary
                            )
                        }
                        Text(
                            Fmt.uptime(m.uptime),
                            fontSize = 10.5.sp,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary
                        )
                    }
                } else if (st?.error != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "${t.errorShort}: ${st.error}",
                        fontSize = 10.sp,
                        color = Ds.danger,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit",
                    tint = Ds.textTertiary,
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { onEdit() }
                        .padding(1.dp)
                )
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Ds.danger.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(17.dp)
                        .clickable { onDelete() }
                        .padding(1.dp)
                )
            }
        }
    }
}

// ── Add Server Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddServerDialog(t: Str, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0: manual, 1: ssh

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.addServer, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(Modifier.imePadding()) {
                SegmentedTabs(
                    tabs = listOf(
                        TabSpec(t.manual),
                        TabSpec(t.sshInstall)
                    ),
                    selected = mode,
                    onSelect = { mode = it }
                )
                Spacer(Modifier.height(14.dp))
                if (mode == 0) ManualForm(t, onSaved) else SshInstallForm(t, onSaved)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Ds.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Ds.accent,
                checkedThumbColor = Ds.onAccent,
                uncheckedTrackColor = Ds.surfaceHigh,
                uncheckedBorderColor = Ds.hairlineStrong
            )
        )
    }
}

@Composable
private fun ManualForm(t: Str, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var tls by remember { mutableStateOf(true) }
    var fp by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    fun parseClipboard() {
        try {
            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return
            var found = false

            if (clip.startsWith("didban://")) {
                val clean = clip.removePrefix("didban://")
                val parts = clean.split("?")
                val hostPort = parts[0].split(":")
                if (hostPort.isNotEmpty()) host = hostPort[0]
                if (hostPort.size > 1) port = hostPort[1]
                if (parts.size > 1) {
                    val query = parts[1].split("&")
                    for (q in query) {
                        val kv = q.split("=")
                        if (kv.size == 2) {
                            when (kv[0]) {
                                "token" -> token = kv[1]
                                "fp" -> fp = kv[1]
                                "name" -> name = kv[1]
                            }
                        }
                    }
                }
                found = true
            } else {
                val lines = clip.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("URL:", ignoreCase = true) || trimmed.startsWith("Address:", ignoreCase = true)) {
                        val url = trimmed.substringAfter(":").trim()
                        val noProto = url.removePrefix("https://").removePrefix("http://")
                        val hp = noProto.split(":")
                        if (hp.isNotEmpty()) host = hp[0].substringBefore("/")
                        if (hp.size > 1) port = hp[1].substringBefore("/")
                        tls = url.startsWith("https")
                        found = true
                    }
                    if (trimmed.startsWith("Token:", ignoreCase = true)) {
                        token = trimmed.substringAfter(":").trim()
                        found = true
                    }
                    if (trimmed.startsWith("Fingerprint:", ignoreCase = true)) {
                        fp = trimmed.substringAfter(":").trim()
                        found = true
                    }
                }
            }

            if (found) {
                Toast.makeText(ctx, t.clipboardParsed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, t.clipboardNotFound, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(430.dp).imePadding()
    ) {
        item {
            SoftButton(
                text = t.smartPaste,
                onClick = { parseClipboard() },
                icon = Icons.Rounded.ContentPaste,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            DTextField(value = name, onValueChange = { name = it }, label = t.name, modifier = Modifier.fillMaxWidth())
        }
        item {
            DTextField(value = host, onValueChange = { host = it }, label = t.host, modifier = Modifier.fillMaxWidth())
        }
        item {
            DTextField(value = port, onValueChange = { port = it }, label = t.port, modifier = Modifier.fillMaxWidth(), mono = true)
        }
        item {
            DTextField(value = token, onValueChange = { token = it }, label = t.token, modifier = Modifier.fillMaxWidth(), secret = true, mono = true)
        }
        item { ToggleRow(t.useTls, tls) { tls = it } }
        item {
            DTextField(
                value = fp,
                onValueChange = { fp = it },
                label = "${t.fingerprint} (${t.fingerprintOptional})",
                modifier = Modifier.fillMaxWidth(),
                mono = true
            )
        }

        if (testResult != null) {
            item {
                Banner(
                    tone = if (testSuccess) BannerTone.Ok else BannerTone.Danger,
                    text = testResult!!,
                    icon = if (testSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryActionCard(
                    title = if (isTesting) "…" else t.testConnection,
                    icon = Icons.Rounded.Bolt,
                    onClick = {
                        if (host.isBlank() || token.isBlank()) return@SecondaryActionCard
                        isTesting = true
                        testResult = null
                        scope.launch {
                            try {
                                val testCfg = ServerConfig(
                                    id = 0,
                                    name = name,
                                    host = host.trim(),
                                    port = port.toIntOrNull() ?: 8686,
                                    token = token.trim(),
                                    useTls = tls,
                                    fingerprint = fp.trim()
                                )
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
                PrimaryActionButton(
                    text = t.save,
                    onClick = {
                        if (host.isNotBlank() && token.isNotBlank()) {
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
                        }
                    },
                    enabled = host.isNotBlank() && token.isNotBlank(),
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
    var showPass by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SshSetup.Result?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().height(430.dp).imePadding()
    ) {
        item {
            Text(t.sshInstallHint, fontSize = 11.5.sp, color = Ds.textSecondary, lineHeight = 17.sp)
        }
        item { DTextField(value = name, onValueChange = { name = it }, label = t.name, modifier = Modifier.fillMaxWidth()) }
        item { DTextField(value = host, onValueChange = { host = it }, label = t.host, modifier = Modifier.fillMaxWidth()) }
        item { DTextField(value = sshPort, onValueChange = { sshPort = it }, label = t.port, modifier = Modifier.fillMaxWidth(), mono = true) }
        item { DTextField(value = user, onValueChange = { user = it }, label = t.sshUser, modifier = Modifier.fillMaxWidth()) }
        item {
            DTextField(
                value = pass,
                onValueChange = { pass = it },
                label = t.sshPassword,
                secret = !showPass,
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    Icon(
                        imageVector = if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPass) "Hide password" else "Show password",
                        tint = Ds.textTertiary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { showPass = !showPass }
                    )
                }
            )
        }

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
                Banner(
                    tone = if (r.success) BannerTone.Ok else BannerTone.Danger,
                    text = if (r.success) t.installDone else "${t.installFailed}: ${r.error}",
                    icon = if (r.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline
                )
            }
        }

        item {
            PrimaryActionButton(
                text = if (busy) t.installing else t.install,
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
                        pass = ""
                    }
                },
                enabled = !busy && host.isNotBlank() && pass.isNotBlank()
            )
        }
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
                modifier = Modifier.fillMaxWidth().height(400.dp).imePadding()
            ) {
                item { DTextField(value = name, onValueChange = { name = it }, label = t.name, modifier = Modifier.fillMaxWidth()) }
                item { DTextField(value = host, onValueChange = { host = it }, label = t.host, modifier = Modifier.fillMaxWidth()) }
                item { DTextField(value = port, onValueChange = { port = it }, label = t.port, modifier = Modifier.fillMaxWidth(), mono = true) }
                item { DTextField(value = token, onValueChange = { token = it }, label = t.token, modifier = Modifier.fillMaxWidth(), secret = true, mono = true) }
                item { ToggleRow(t.useTls, tls) { tls = it } }
                item { DTextField(value = fp, onValueChange = { fp = it }, label = t.fingerprint, modifier = Modifier.fillMaxWidth(), mono = true) }
                item { DTextField(value = cpuAlert, onValueChange = { cpuAlert = it }, label = t.cpuAlertLbl, modifier = Modifier.fillMaxWidth(), mono = true) }
                item { DTextField(value = memAlert, onValueChange = { memAlert = it }, label = t.memAlertLbl, modifier = Modifier.fillMaxWidth(), mono = true) }
                item {
                    Spacer(Modifier.height(2.dp))
                    PrimaryActionButton(
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
                        enabled = host.isNotBlank() && token.isNotBlank()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}
