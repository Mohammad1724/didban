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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@Composable
fun CommandManageServersScreen(
    copy: CommandCopy,
    onOpenServer: (ServerConfig) -> Unit,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialLoad = remember { Prefs.loadServersResult(context) }
    var records by remember { mutableStateOf<List<ServerConfig>>(initialLoad.servers) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8686") }
    var token by remember { mutableStateOf("") }
    var adminToken by remember { mutableStateOf("") }
    var quickConnectCode by remember { mutableStateOf("") }
    var useTls by remember { mutableStateOf(true) }
    var fingerprint by remember { mutableStateOf("") }
    var cpuAlert by remember { mutableStateOf("90") }
    var memAlert by remember { mutableStateOf("90") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember {
        mutableStateOf(
            initialLoad.error?.let {
                securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_READ_FAILED)
            }
        )
    }
    var testResult by remember { mutableStateOf<Metrics?>(null) }
    var deleteServer by remember { mutableStateOf<ServerConfig?>(null) }

    fun reset() {
        selectedId = null
        name = ""
        host = ""
        port = "8686"
        token = ""
        adminToken = ""
        quickConnectCode = ""
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
        adminToken = server.adminToken
        // Plain HTTP records are upgraded in the editor; credentials are never
        // sent again until a valid HTTPS certificate fingerprint is supplied.
        useTls = true
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
        adminToken = adminToken.trim(),
        useTls = useTls,
        fingerprint = fingerprint.trim(),
        cpuAlert = cpuAlert.toIntOrNull()?.coerceIn(1, 100) ?: 90,
        memAlert = memAlert.toIntOrNull()?.coerceIn(1, 100) ?: 90
    )

    fun importQuickConnect() {
        runCatching { QuickConnectCodeParser.parse(quickConnectCode) }
            .onSuccess { code ->
                selectedId = null
                name = code.name
                host = code.host
                port = code.port.toString()
                token = code.readToken
                adminToken = code.adminToken
                fingerprint = code.fingerprint
                useTls = true
                quickConnectCode = ""
                error = null
                message = copy.srvQuickConnectImported
            }
            .onFailure {
                error = copy.srvQuickConnectInvalid
            }
    }

    fun validate(server: ServerConfig): String? = when {
        server.name.isBlank() -> copy.srvNameRequired
        !TunnelFieldValidation.isHost(server.host) -> copy.srvHostInvalid
        server.token.isBlank() -> copy.srvTokenRequired
        !AgentTokenPolicy.isValid(server.token) -> securityMessage(Prefs.getLanguage(context), SecurityMessage.WEAK_AGENT_TOKEN)
        !AgentTokenPolicy.isValid(server.adminToken) -> securityMessage(Prefs.getLanguage(context), SecurityMessage.WEAK_ADMIN_TOKEN)
        server.adminToken == server.token -> securityMessage(Prefs.getLanguage(context), SecurityMessage.TOKENS_MUST_DIFFER)
        !server.useTls -> securityMessage(Prefs.getLanguage(context), SecurityMessage.HTTP_DISABLED)
        !CertFingerprint.isValidSha256(server.fingerprint) -> copy.srvFingerprintInvalid
        else -> null
    }

    fun save() {
        if (initialLoad.error != null) {
            error = securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_WRITE_BLOCKED)
            return
        }
        val server = buildServer()
        val validation = validate(server)
        if (validation != null) {
            error = validation
            return
        }
        val next = records.toMutableList()
        val index = next.indexOfFirst { it.id == server.id }
        if (index >= 0) next[index] = server else next.add(server)
        runCatching { Prefs.saveServers(context, next) }
            .onSuccess {
                records = next
                selectedId = server.id
                PollingCoordinator.requestNow(server.id)
                message = copy.srvSavedPolled
                error = null
            }
            .onFailure {
                error = it.message ?: securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_SAVE_FAILED)
                message = null
            }
    }

    fun test(server: ServerConfig) {
        val validation = validate(server)
        if (validation != null) {
            error = validation
            return
        }
        busy = true
        error = null
        message = copy.srvConnecting
        scope.launch {
            val api = ApiClient()
            runCatching { api.metrics(server) }
                .onSuccess {
                    testResult = it
                    api.lastSeenFingerprint?.let { seen ->
                        if (useTls && fingerprint.isBlank()) fingerprint = seen
                    }
                    message = copy.srvFingerprintSuggested
                }
                .onFailure { error = it.message ?: copy.srvConnectFailed }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.manageServers, copy.srvSavedCount.replace("%1", records.size.toString()), modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.srvConnections, Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        CommandTextButton(copy.srvNewConnection, ::reset, icon = Icons.Rounded.Refresh)
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
                    Text(if (selectedId == null) copy.srvNewConnection else copy.srvEditConnection, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    if (selectedId == null) {
                        Text(
                            copy.srvQuickConnectHint,
                            color = CommandColors.textSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            quickConnectCode,
                            { quickConnectCode = it.take(2048) },
                            Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            label = { Text(copy.srvQuickConnectLabel) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        CommandSecondaryButton(
                            copy.srvQuickConnectImport,
                            ::importQuickConnect,
                            icon = Icons.Rounded.Bolt,
                            enabled = quickConnectCode.isNotBlank() && !busy
                        )
                        CommandRule()
                    }
                    CommandResponsiveRow {
                        OutlinedTextField(name, { name = it }, item(weight = 1f), singleLine = true, label = { Text(copy.uiName) })
                        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, item(width = CommandMetrics.formAuxFieldWidth), singleLine = true, label = { Text(copy.port) })
                    }
                    OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.srvAgentHost) })
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.srvAgentToken) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    OutlinedTextField(adminToken, { adminToken = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.srvAdminToken) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Switch(checked = true, onCheckedChange = null, enabled = false)
                        Text(copy.srvUseTls, color = CommandColors.textSecondary, modifier = Modifier.weight(1f))
                        Text(copy.srvFingerprintPinning, color = CommandColors.textSecondary)
                    }
                    if (useTls) {
                        OutlinedTextField(fingerprint, { fingerprint = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.srvTlsFingerprint) }, textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                    CommandResponsiveRow {
                        OutlinedTextField(cpuAlert, { cpuAlert = it.filter(Char::isDigit).take(3) }, item(weight = 1f), singleLine = true, label = { Text(copy.srvCpuAlert) })
                        OutlinedTextField(memAlert, { memAlert = it.filter(Char::isDigit).take(3) }, item(weight = 1f), singleLine = true, label = { Text(copy.srvMemAlert) })
                    }
                    CommandResponsiveRow {
                        CommandPrimaryButton(copy.save, ::save, modifier = item(), icon = Icons.Rounded.Save, enabled = !busy)
                        CommandSecondaryButton(copy.srvTestAgent, { test(buildServer()) }, modifier = item(), icon = Icons.Rounded.PlayArrow, enabled = !busy)
                    }
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        if (message != null) item { CommandStateBlock(copy.srvConnectionResult, message ?: "", CommandHealthTone.INFO) }
        val currentTestResult = testResult
        if (currentTestResult != null) {
            item {
                val metrics = currentTestResult
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandStatusMark(copy.srvAgentOnline, CommandHealthTone.HEALTHY, detail = "${metrics.hostname} · ${copy.cpu} ${Fmt.pct(metrics.cpuUsage)} · ${copy.memory} ${Fmt.pct(metrics.memPct)}")
                        Text("${copy.metricUptime} ${metrics.uptime}s · ${copy.metricLoad} ${metrics.load1} · ${metrics.disks.size} ${copy.metricDisks} · ${metrics.nets.size} ${copy.metricInterfaces}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                    }
                }
            }
        }
        if (selectedId != null) {
            val selected = records.firstOrNull { it.id == selectedId }
            if (selected != null) item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    CommandResponsiveRow(Modifier.padding(CommandSpacing.md)) {
                        Column(item(weight = 1f)) {
                            Text(copy.srvSavedActions, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            Text(copy.srvDossierHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        }
                        CommandTextButton(copy.srvOpenDossier, { onOpenServer(selected) }, icon = Icons.Rounded.Security, modifier = item())
                        CommandTextButton(copy.delete, { deleteServer = selected }, icon = Icons.Rounded.DeleteOutline, modifier = item())
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val serverToDelete = deleteServer
    if (serverToDelete != null) {
        val server = serverToDelete
        CommandDestructiveDialog(
            title = copy.srvDeleteTitle,
            body = "${copy.srvDeleteBody.replace("%1", server.name)}\n\n${server.name} · ${server.host}:${server.port} · #${server.id}",
            confirmLabel = copy.delete,
            dismissLabel = copy.cancel,
            onDismiss = { if (!busy) deleteServer = null },
            enabled = !busy,
            onConfirm = {
                if (!busy) {
                    if (initialLoad.error != null) {
                        deleteServer = null
                        error = securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_DELETE_BLOCKED)
                    } else {
                        busy = true
                        val current = records.firstOrNull { it.id == server.id }
                        runCatching {
                            check(current != null && current.name == server.name && current.host == server.host && current.token == server.token) {
                                securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_DELETE_BLOCKED)
                            }
                            val next = records.filterNot { it.id == server.id }
                            Prefs.saveServers(context, next)
                            next
                        }.onSuccess { next ->
                            HttpClientPool.evictForServer(server)
                            records = next
                            deleteServer = null
                            reset()
                            message = copy.srvLocalDeleted
                        }.onFailure {
                            deleteServer = null
                            error = SecretRedactor.redact(
                                it.message ?: securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_DELETE_FAILED),
                                listOf(server.token, server.adminToken)
                            ).take(300)
                        }
                        busy = false
                    }
                }
            }
        )
    }
}
