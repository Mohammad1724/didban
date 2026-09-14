package org.didban.monitor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun CommandManageServersScreen(
    copy: CommandCopy,
    onOpenServer: (ServerConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<ServerConfig>>(Prefs.loadServers(context)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var useTls by remember { mutableStateOf(true) }
    var fingerprint by remember { mutableStateOf("") }
    var cpuAlert by remember { mutableStateOf("90") }
    var memAlert by remember { mutableStateOf("90") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<Metrics?>(null) }
    var deleteServer by remember { mutableStateOf<ServerConfig?>(null) }

    fun reset() {
        selectedId = null
        name = ""
        host = ""
        port = "8686"
        token = ""
        useTls = true
        fingerprint = ""
        cpuAlert = "90"
        memAlert = "90"
        testResult = null
        message = null
        error = null
    }

    fun select(server: ServerConfig) {
        selectedId = server.id
        name = server.name
        host = server.host
        port = server.port.toString()
        token = server.token
        useTls = server.useTls
        fingerprint = server.fingerprint
        cpuAlert = server.cpuAlert.toString()
        memAlert = server.memAlert.toString()
        testResult = null
        message = null
        error = null
    }

    fun buildServer(): ServerConfig = ServerConfig(
        id = selectedId ?: System.currentTimeMillis(),
        name = name.trim().ifBlank { host.trim() },
        host = host.trim(),
        port = port.toIntOrNull()?.coerceIn(1, 65535) ?: 8686,
        token = token.trim(),
        useTls = useTls,
        fingerprint = fingerprint.trim(),
        cpuAlert = cpuAlert.toIntOrNull()?.coerceIn(1, 100) ?: 90,
        memAlert = memAlert.toIntOrNull()?.coerceIn(1, 100) ?: 90
    )

    fun validate(server: ServerConfig): String? = when {
        server.name.isBlank() -> "نام اتصال اجباری است."
        !TunnelFieldValidation.isHost(server.host) -> "Host باید hostname یا IPv4 معتبر باشد."
        server.token.isBlank() -> "Agent token اجباری است."
        server.useTls && server.fingerprint.isNotBlank() && runCatching { CertFingerprint.normalizeFingerprint(server.fingerprint) }.getOrNull().isNullOrBlank() -> "Fingerprint TLS معتبر نیست."
        else -> null
    }

    fun save() {
        val server = buildServer()
        val validation = validate(server)
        if (validation != null) {
            error = validation
            return
        }
        val next = records.toMutableList()
        val index = next.indexOfFirst { it.id == server.id }
        if (index >= 0) next[index] = server else next.add(server)
        records = next
        selectedId = server.id
        Prefs.saveServers(context, next)
        PollingCoordinator.requestNow(server.id)
        message = "اتصال ذخیره شد؛ Polling واقعی برای آن درخواست شد."
        error = null
    }

    fun test(server: ServerConfig) {
        val validation = validate(server)
        if (validation != null) {
            error = validation
            return
        }
        busy = true
        error = null
        message = "در حال اتصال به Agent واقعی..."
        scope.launch {
            val api = ApiClient()
            runCatching { api.metrics(server) }
                .onSuccess {
                    testResult = it
                    api.lastSeenFingerprint?.let { seen ->
                        if (useTls && fingerprint.isBlank()) fingerprint = seen
                    }
                    message = "Agent پاسخ داد؛ fingerprint فقط به‌صورت پیشنهادی در فرم قرار گرفت و تا Save pin نمی‌شود."
                }
                .onFailure { error = it.message ?: "اتصال به Agent ناموفق بود." }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.manageServers, "${records.size} saved connections", modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Connections", Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        CommandTextButton("New", ::reset, icon = Icons.Rounded.Refresh)
                    }
                    if (records.isEmpty()) {
                        Text(copy.noServersBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            records.forEach { server -> CommandSecondaryButton(server.name, { select(server) }, enabled = selectedId != server.id) }
                        }
                    }
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(if (selectedId == null) "New connection" else "Edit connection", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(name, { name = it }, Modifier.weight(1f), singleLine = true, label = { Text("Name") })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), singleLine = true, label = { Text("Port") })
                    }
                    OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Agent host") })
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Agent token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Switch(useTls, { useTls = it })
                        Text("Use TLS", color = CommandColors.textSecondary, modifier = Modifier.weight(1f))
                        Text("Fingerprint pinning", color = CommandColors.textSecondary)
                    }
                    if (useTls) {
                        OutlinedTextField(fingerprint, { fingerprint = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("TLS SHA-256 fingerprint (optional TOFU)") }, textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(cpuAlert, { cpuAlert = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("CPU alert %") })
                        OutlinedTextField(memAlert, { memAlert = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text("Memory alert %") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandPrimaryButton("Save", ::save, icon = Icons.Rounded.Save, enabled = !busy)
                        CommandSecondaryButton("Test Agent", { test(buildServer()) }, icon = Icons.Rounded.PlayArrow, enabled = !busy)
                    }
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        if (message != null) item { CommandStateBlock("Connection result", message ?: "", CommandHealthTone.INFO) }
        if (testResult != null) {
            item {
                val metrics = testResult!!
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandStatusMark("Agent online", CommandHealthTone.HEALTHY, detail = "${metrics.hostname} · CPU ${Fmt.pct(metrics.cpuUsage)} · RAM ${Fmt.pct(metrics.memPct)}")
                        Text("Uptime ${metrics.uptime}s · Load ${metrics.load1} · ${metrics.disks.size} disks · ${metrics.nets.size} interfaces", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                    }
                }
            }
        }
        if (selectedId != null) {
            val selected = records.firstOrNull { it.id == selectedId }
            if (selected != null) item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Saved actions", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text("از اینجا پروندهٔ واقعی سرور باز می‌شود؛ حذف فقط local connection را حذف می‌کند.", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        CommandTextButton("Open dossier", { onOpenServer(selected) }, icon = Icons.Rounded.Security)
                        CommandTextButton("Delete", { deleteServer = selected }, icon = Icons.Rounded.DeleteOutline)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (deleteServer != null) {
        val server = deleteServer!!
        AlertDialog(
            onDismissRequest = { deleteServer = null },
            title = { Text("Delete connection؟", fontWeight = FontWeight.Bold) },
            text = { Text("اتصال ${server.name} از Prefs حذف می‌شود؛ چیزی روی خود سرور حذف نخواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    records = records.filterNot { it.id == server.id }
                    Prefs.saveServers(context, records)
                    deleteServer = null
                    reset()
                    message = "اتصال local حذف شد."
                }) { Text("حذف", color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteServer = null }) { Text("لغو") } }
        )
    }
}
