@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    Column(Modifier.fillMaxSize().padding(14.dp)) {
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

        // ── Monitoring Status Card ──
        ModernCard(
            padding = 10.dp,
            containerColor = if (monitoring) Color(0xFF10B981).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
            borderColor = if (monitoring) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PulseDot(isOnline = monitoring, size = 9.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (monitoring) t.monitoringOn else t.monitoringOff,
                        fontWeight = FontWeight.Bold,
                        color = if (monitoring) Color(0xFF047857) else Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                    Text(
                        if (monitoring) "پایش خودکار پس‌زمینه فعال است" else "پایش پس‌زمینه متوقف است",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (monitoring) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            if (monitoring) "توقف" else "شروع",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Server list / Onboarding ──
        if (servers.isEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    ModernCard(padding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(
                                    icon = Icons.Rounded.RocketLaunch,
                                    tint = MaterialTheme.colorScheme.primary,
                                    background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    size = 36.dp,
                                    iconSize = 18.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("راهنمای سریع اتصال سرور", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.primary)
                            }

                            Text(
                                "برای اتصال سرور لینوکس (Ubuntu, Debian, CentOS, AlmaLinux) دستور زیر را در ترمینال سرور اجرا کنید:",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Terminal Code Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("root@server:~#", fontSize = 10.5.sp, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.weight(1f))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1E293B),
                                            modifier = Modifier.clickable {
                                                clipboard.setPrimaryClip(ClipData.newPlainText("install_cmd", AGENT_INSTALL_CMD))
                                                Toast.makeText(ctx, "دستور نصب کپی شد!", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                                Text("کپی", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        AGENT_INSTALL_CMD,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFF1F5F9),
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            PrimaryActionButton(
                                text = "＋  ${t.addServer}",
                                onClick = { showAdd = true }
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Spacer(Modifier.height(6.dp))
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
        padding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (isOnline) Icons.Rounded.Dns else Icons.Rounded.ErrorOutline,
                tint = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                background = if (isOnline) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                size = 38.dp,
                iconSize = 20.dp
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name.ifEmpty { s.host }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        text = if (isOnline) t.online else t.offline,
                        isOnline = isOnline
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(s.host, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontFamily = FontFamily.Monospace)

                if (m != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CPU: ${Fmt.pct(m.cpuUsage)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "RAM: ${Fmt.pct(m.memPct)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6366F1)
                        )
                        if (lat > 0f) {
                            Text(
                                "${lat.toInt()} ms",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (st?.error != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("${t.offline}: ${st.error}", fontSize = 10.5.sp, color = Color(0xFFEF4444))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ── Add Server Dialog ───────────────────────────────────────────────────────

@Composable
private fun AddServerDialog(t: Str, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var mode by remember { mutableStateOf(1) } // Default to manual

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(t.addServer, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.imePadding()) {
                Row(Modifier.fillMaxWidth()) {
                    TabButton(t.manual, mode == 1) { mode = 1 }
                    Spacer(Modifier.width(8.dp))
                    TabButton(t.sshInstall, mode == 0) { mode = 0 }
                }
                Spacer(Modifier.height(10.dp))
                if (mode == 1) ManualForm(t, onSaved) else SshInstallForm(t, onSaved)
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
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
                Toast.makeText(ctx, "اطلاعات از کلیپ‌بورد شناسایی شد!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "مشخصات معتبری در کلیپ‌بورد یافت نشد", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(420.dp)
    ) {
        // Smart Paste Button
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().clickable { parseClipboard() }
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("الصاق خودکار مشخصات از کلیپ‌بورد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(t.port) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(t.token) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = tls, onCheckedChange = { tls = it })
                Text(t.useTls, fontSize = 12.5.sp)
            }
        }
        item {
            OutlinedTextField(
                value = fp, onValueChange = { fp = it },
                label = { Text("${t.fingerprint} (${t.fingerprintOptional})") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

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

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        if (host.isBlank() || token.isBlank()) return@OutlinedButton
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
                                testResult = "اتصال موفق! (${elapsed}ms - CPU: ${Fmt.pct(m.cpuUsage)})"
                            } catch (e: Exception) {
                                testSuccess = false
                                testResult = "خطا در اتصال: ${e.message}"
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    enabled = !isTesting && host.isNotBlank() && token.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    if (isTesting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("تست اتصال", fontSize = 11.sp)
                        }
                    }
                }

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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text(t.save, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().height(420.dp)
    ) {
        item {
            Text(t.sshInstallHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text(t.port) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text(t.sshUser) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(t.sshPassword) },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(
                            imageVector = if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showPass) "Hide password" else "Show password",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (busy) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(t.installing, fontSize = 12.sp)
                }
            }
        }

        result?.let { r ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (r.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = if (r.success) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        if (r.success) t.installDone else "${t.installFailed}: ${r.error}",
                        color = if (r.success) Color(0xFF10B981) else Color(0xFFEF4444),
                        fontSize = 11.5.sp
                    )
                }
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
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("${t.edit} — ${server.name}", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(380.dp).imePadding()
            ) {
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(t.name) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text(t.host) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text(t.port) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(t.token) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tls, onCheckedChange = { tls = it })
                        Text(t.useTls, fontSize = 12.5.sp)
                    }
                }
                item {
                    OutlinedTextField(value = fp, onValueChange = { fp = it }, label = { Text(t.fingerprint) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = cpuAlert, onValueChange = { cpuAlert = it }, label = { Text(t.cpuAlertLbl) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    OutlinedTextField(value = memAlert, onValueChange = { memAlert = it }, label = { Text(t.memAlertLbl) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Spacer(Modifier.height(4.dp))
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
                Text(t.pollInterval, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
