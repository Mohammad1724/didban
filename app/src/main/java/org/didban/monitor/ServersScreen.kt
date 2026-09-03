package org.didban.monitor

import android.content.Intent
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@Composable
fun ServersScreen(
    t: Str,
    onLanguage: (String) -> Unit,
    onOpen: (ServerConfig) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val states by Repo.states.collectAsState()

    var servers by remember { mutableStateOf<List<ServerConfig>>(Prefs.loadServers(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var monitoring by remember { mutableStateOf(false) }
    var deletedServer by remember { mutableStateOf<ServerConfig?>(null) }

    fun refresh() {
        servers = Prefs.loadServers(ctx)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.appName, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("👁", fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onLanguage(if (t.langButton == "EN") "fa" else "en") }) {
                Text(t.langButton)
            }
        }

        // ── Monitoring toggle ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (monitoring) Color(0xFF123524) else Color(0xFF351414),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Box(
                    Modifier.size(10.dp).background(
                        if (monitoring) Color(0xFF4ADE80) else Color(0xFFF87171),
                        CircleShape
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(if (monitoring) t.monitoringOn else t.monitoringOff,
                    color = if (monitoring) Color(0xFF4ADE80) else Color(0xFFF87171),
                    fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    monitoring = !monitoring
                    if (monitoring) {
                        ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
                    } else {
                        ctx.stopService(Intent(ctx, MonitorService::class.java))
                    }
                }) { Text(if (monitoring) "■" else "▶", fontSize = 18.sp) }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Server list ──
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.noServers, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.noServersHint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(servers, key = { it.id }) { s ->
                    val st = states[s.id]
                    ServerCard(t, s, st, onOpen = { onOpen(s) }, onDelete = { deletedServer = s })
                }
                item { Spacer(Modifier.height(70.dp)) }
            }
        }
    }

    // ── Add button (floating bottom) ──
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("＋  ${t.addServer}", fontSize = 16.sp) }
    }

    if (showAdd) {
        AddServerDialog(
            t = t,
            onDismiss = { showAdd = false },
            onSaved = { refresh(); showAdd = false }
        )
    }

    deletedServer?.let { ds ->
        AlertDialog(
            onDismissRequest = { deletedServer = null },
            title = { Text(t.confirmDelete) },
            text = { Text("${ds.name} (${ds.host})") },
            confirmButton = {
                TextButton(onClick = {
                    servers = Prefs.loadServers(ctx).filter { it.id != ds.id }
                    Prefs.saveServers(ctx, servers)
                    deletedServer = null
                }) { Text(t.delete, color = Color(0xFFF87171)) }
            },
            dismissButton = {
                TextButton(onClick = { deletedServer = null }) { Text(t.cancel) }
            }
        )
    }
}

@Composable
private fun ServerCard(t: Str, s: ServerConfig, st: Repo.State?, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name.ifEmpty { s.host }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(s.host, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                val m = st?.metrics
                if (m != null) {
                    Text(
                        "CPU ${Fmt.pct(m.cpuUsage)}   •   RAM ${Fmt.pct(m.memPct)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (st?.error != null) {
                    Text("${t.offline}: ${st.error}", fontSize = 12.sp, color = Color(0xFFF87171))
                } else {
                    Text(t.connecting, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onDelete) {
                Text("×", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Add server dialog (manual / SSH install) ────────────────────────────────

@Composable
private fun AddServerDialog(t: Str, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var mode by remember { mutableStateOf(0) } // 0 = ssh, 1 = manual

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.addServer) },
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
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            color = if (selected) Color(0xFF0B1220) else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ManualForm(t: Str, onSaved: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
        Button(
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
            modifier = Modifier.fillMaxWidth()
        ) { Text(t.save) }
    }
}

@Composable
private fun SshInstallForm(t: Str, onSaved: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var sshPort by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("root") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SshSetup.Result?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t.sshInstallHint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("✅ ${t.installDone}", color = Color(0xFF4ADE80), fontSize = 13.sp)
            } else {
                Text("❌ ${t.installFailed}: ${r.error}", color = Color(0xFFF87171), fontSize = 12.sp)
            }
        }

        Button(
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
            enabled = !busy && host.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) t.installing else t.install) }
    }
}
