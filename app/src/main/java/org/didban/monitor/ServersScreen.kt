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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showSettings by remember { mutableStateOf(false) }

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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Modern Top Bar ──
        ModernTopBar(
            title = t.appName,
            subtitle = t.appSubtitle,
            isDarkMode = isDarkMode,
            onToggleTheme = onToggleTheme,
            onRefresh = { refresh() },
            onToggleLang = { onLanguage(if (t.langButton == "EN") "en" else "fa") },
            langLabel = t.langButton
        )

        // ── Notice Banner (Matching Screenshot) ──
        NoticeBanner(
            text = "💡 با اضافه کردن سرورها، مصرف زنده پردازنده، رم، دیسک، کانتینرهای داکر و اسپایک‌ها ثبت و پایش می‌شوند.",
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // ── Monitoring Status Card ──
        ModernCard(
            padding = 12.dp,
            containerColor = if (monitoring) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
            borderColor = if (monitoring) Color(0xFF10B981).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.size(10.dp).background(
                        if (monitoring) Color(0xFF10B981) else Color(0xFFEF4444),
                        CircleShape
                    )
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (monitoring) t.monitoringOn else t.monitoringOff,
                        fontWeight = FontWeight.Bold,
                        color = if (monitoring) Color(0xFF047857) else Color(0xFFEF4444),
                        fontSize = 13.sp
                    )
                    Text(
                        if (monitoring) "هشدارها در صورت قطعی یا اسپایک ارسال می‌شوند" else "پایش پس‌زمینه متوقف است",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable {
                        if (MonitorService.isRunning) {
                            ctx.stopService(Intent(ctx, MonitorService::class.java))
                            MonitorService.isRunning = false
                            monitoring = false
                        } else {
                            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
                            MonitorService.isRunning = true
                            monitoring = true
                        }
                    }
                ) {
                    Text(
                        if (monitoring) "توقف پایش ■" else "شروع پایش ▶",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Server list ──
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("👁️", fontSize = 48.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(t.noServers, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(t.noServersHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                    Spacer(Modifier.height(20.dp))
                    PrimaryActionButton(
                        text = "＋  ${t.addServer}",
                        onClick = { showAdd = true }
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { s ->
                    val st = states[s.id]
                    ModernServerCard(
                        t = t,
                        s = s,
                        st = st,
                        onOpen = { onOpen(s) },
                        onEdit = { editServer = s },
                        onDelete = { deletedServer = s }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    PrimaryActionButton(
                        text = "＋  ${t.addServer}",
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

    if (showSettings) {
        SettingsDialog(t = t, onDismiss = { showSettings = false })
    }

    deletedServer?.let { ds ->
        AlertDialog(
            onDismissRequest = { deletedServer = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.confirmDelete, fontWeight = FontWeight.Bold) },
            text = { Text("${ds.name} (${ds.host})") },
            confirmButton = {
                TextButton(onClick = {
                    servers = Prefs.loadServers(ctx).filter { it.id != ds.id }
                    Prefs.saveServers(ctx, servers)
                    deletedServer = null
                }) { Text(t.delete, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletedServer = null }) { Text(t.cancel) }
            }
        )
    }
}

@Composable
private fun ModernServerCard(
    t: Str,
    s: ServerConfig,
    st: Repo.State?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val m = st?.metrics
    val isOnline = m != null
    val lat = st?.latencyMs ?: 0f

    ModernCard(
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
        padding = 14.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (isOnline) "🟢" else "🔴", fontSize = 16.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name.ifEmpty { s.host }, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = if (isOnline) "• ${t.online}" else "• ${t.offline}",
                        isOnline = isOnline
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(s.host, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)

                Spacer(Modifier.height(6.dp))

                if (m != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CPU: ${Fmt.pct(m.cpuUsage)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "RAM: ${Fmt.pct(m.memPct)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6366F1)
                        )
                        if (lat > 0f) {
                            Text(
                                "${lat.toInt()} ms",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (st?.error != null) {
                    Text("${t.offline}: ${st.error}", fontSize = 11.5.sp, color = Color(0xFFEF4444))
                } else {
                    Text(t.connecting, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) {
                    Text("✏️", fontSize = 14.sp)
                }
                TextButton(onClick = onDelete) {
                    Text("🗑️", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Add Server Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddServerDialog(t: Str, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 = ssh, 1 = manual

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(t.addServer, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    TabButton(t.sshInstall, mode == 0) { mode = 0 }
                    Spacer(Modifier.width(8.dp))
                    TabButton(t.manual, mode == 1) { mode = 1 }
                }
                Spacer(Modifier.height(12.dp))
                if (mode == 0) SshInstallForm(t, onSaved) else ManualForm(t, onSaved)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ManualForm(t: Str, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var tls by remember { mutableStateOf(true) }
    var fp by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true)
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true)
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(t.port) }, singleLine = true)
        OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(t.token) }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = tls, onCheckedChange = { tls = it })
            Text(t.useTls, fontSize = 13.sp)
        }
        OutlinedTextField(
            value = fp, onValueChange = { fp = it },
            label = { Text("${t.fingerprint} (${t.fingerprintOptional})") },
            singleLine = true
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
            enabled = host.isNotBlank() && token.isNotBlank()
        )
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t.sshInstallHint, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true)
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true)
        OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text(t.port) }, singleLine = true)
        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(t.sshUser) }, singleLine = true)
        OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text(t.sshPassword) }, singleLine = true)

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(t.installing, fontSize = 13.sp)
            }
        }

        result?.let { r ->
            if (r.success) {
                Text("✅ ${t.installDone}", color = Color(0xFF10B981), fontSize = 13.sp)
            } else {
                Text("❌ ${t.installFailed}: ${r.error}", color = Color(0xFFEF4444), fontSize = 12.sp)
            }
        }

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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("${t.edit} — ${server.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true)
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true)
                OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(t.port) }, singleLine = true)
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(t.token) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tls, onCheckedChange = { tls = it })
                    Text(t.useTls, fontSize = 13.sp)
                }
                OutlinedTextField(value = fp, onValueChange = { fp = it }, label = { Text(t.fingerprint) }, singleLine = true)
                OutlinedTextField(value = cpuAlert, onValueChange = { cpuAlert = it }, label = { Text(t.cpuAlertLbl) }, singleLine = true)
                OutlinedTextField(value = memAlert, onValueChange = { memAlert = it }, label = { Text(t.memAlertLbl) }, singleLine = true)
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}

// ── Settings Dialog ─────────────────────────────────────────────────────────

@Composable
private fun SettingsDialog(t: Str, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val current = Prefs.getPollIntervalMs(ctx) / 1000L
    var selected by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(t.settings, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(t.pollInterval, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabButton(t.every5, selected == 5L) { selected = 5L }
                    TabButton(t.every10, selected == 10L) { selected = 10L }
                    TabButton(t.every15, selected == 15L) { selected = 15L }
                    TabButton(t.every30, selected == 30L) { selected = 30L }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabButton(t.every60, selected == 60L) { selected = 60L }
                    TabButton(t.every120, selected == 120L) { selected = 120L }
                    TabButton(t.every300, selected == 300L) { selected = 300L }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setPollIntervalSec(ctx, selected)
                onDismiss()
            }) { Text(t.save, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t.cancel) } }
    )
}
