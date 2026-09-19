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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class PendingServiceAction(
    val unit: String,
    val action: String,
    val serverId: Long?
)

@Composable
fun CommandServicesScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers = remember { Prefs.loadServers(context) }
    var selectedId by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.id ?: servers.firstOrNull()?.id)
    }
    val server = selectedId?.let { id -> servers.firstOrNull { it.id == id } }
    var user by remember { mutableStateOf("root") }
    var port by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var services by remember { mutableStateOf<List<OutputParsers.SystemdUnitLine>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var evidence by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
    var pendingAction by remember { mutableStateOf<PendingServiceAction?>(null) }
    val promptChannel = remember { Channel<Boolean>(Channel.RENDEZVOUS) }
    val promptGate = remember { Mutex() }
    val policy = remember {
        ConfirmingHostKeyPolicy(HostKeyTrustStore) { nextPrompt ->
            promptGate.withLock {
                withContext(Dispatchers.Main) { prompt = nextPrompt }
                promptChannel.receive()
            }
        }
    }

    fun execute(command: String, label: String, after: (SshExecResult) -> Unit = {}) {
        val target = server ?: return
        if (password.isBlank()) {
            error = copy.svcNoPassword
            return
        }
        loading = true
        operation = label
        error = null
        scope.launch {
            val result = SshEngine.execute(
                host = target.host,
                sshPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                user = user.ifBlank { "root" },
                password = password,
                command = command,
                timeoutSec = 25,
                hostKeyPolicy = policy
            )
            loading = false
            operation = null
            evidence = SecretRedactor.redact(buildString {
                if (result.stdout.isNotBlank()) append(result.stdout.trimEnd()).append('\n')
                if (result.stderr.isNotBlank()) append(result.stderr.trimEnd()).append('\n')
                append("\nExit ${result.exitCode} · ${result.durationMs} ms")
            }, listOf(password)).take(16_384)
            if (!result.isSuccess) {
                error = SecretRedactor.redact(
                    result.errorMessage ?: result.stderr.ifBlank { "SSH operation failed" },
                    listOf(password)
                ).take(300)
            }
            after(result)
        }
    }

    fun refresh() {
        execute(
            command = "systemctl list-units --type=service --state=running,failed --no-pager --no-legend --output=plain 2>/dev/null",
            label = copy.svcFetching
        ) { result ->
            if (result.isSuccess) services = OutputParsers.systemdUnits(result.stdout)
            else services = emptyList()
        }
    }

    fun runAction(action: PendingServiceAction) {
        if (server?.id != action.serverId) {
            error = copy.noServerSelected
            return
        }
        if (action.action !in setOf("start", "stop", "restart", "logs")) {
            error = copy.operationFailed
            return
        }
        val unit = SecurityValidation.shellQuote(action.unit)
        if (action.action == "logs") {
            execute("journalctl -u $unit -n 40 --no-pager", copy.svcReadingJournal)
        } else {
            // Validate the unit again in the same remote command immediately
            // before mutating it; never act on a stale or fabricated UI row.
            execute(
                "test \"$(systemctl show --property=LoadState --value $unit)\" = loaded && " +
                    "systemctl ${action.action} $unit && systemctl is-active $unit",
                copy.svcRunningAction.replace("%s", action.action)
            ) {
                if (it.isSuccess) refresh()
            }
        }
    }

    val visibleServices = services.filter {
        query.isBlank() || it.unit.contains(query, true) || it.description.contains(query, true)
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.services, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
            }
        }
        if (servers.isEmpty()) {
            item { CommandEmptyState(copy.selectServer, copy.noServersBody, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            servers.forEach { item ->
                                CommandSecondaryButton(item.name, { selectedId = item.id }, enabled = selectedId != item.id)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(user, { user = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.uiUser) })
                            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), singleLine = true, label = { Text(copy.port) })
                        }
                        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.uiSshPassword) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.svcFilter) })
                            CommandPrimaryButton(if (loading) (operation ?: copy.waitingForData) else copy.svcLoad, ::refresh, enabled = !loading && server != null, icon = Icons.Rounded.Refresh)
                        }
                    }
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
            if (services.isEmpty() && !loading && error == null) {
                item { CommandStateBlock(copy.svcNoLiveData, copy.svcLoadHint.replace("%1", server?.host ?: copy.svcSelectedServer), CommandHealthTone.UNKNOWN) }
            } else {
                item { CommandStatusMark("${visibleServices.size} / ${services.size} services", CommandHealthTone.INFO, detail = copy.svcOnlySystemdUnits) }
                items(visibleServices, key = { it.unit }) { service ->
                    val tone = when {
                        service.active == "failed" || service.sub == "failed" -> CommandHealthTone.OFFLINE
                        service.active == "active" -> CommandHealthTone.HEALTHY
                        else -> CommandHealthTone.UNKNOWN
                    }
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            CommandStatusMark(
                                label = service.unit,
                                tone = tone,
                                detail = "${service.active} (${service.sub}) · ${service.description}"
                            )
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                                CommandSecondaryButton("Start", { pendingAction = PendingServiceAction(service.unit, "start", server?.id) }, enabled = !loading, icon = Icons.Rounded.PlayArrow)
                                CommandSecondaryButton("Stop", { pendingAction = PendingServiceAction(service.unit, "stop", server?.id) }, enabled = !loading, icon = Icons.Rounded.Stop)
                                CommandSecondaryButton("Restart", { pendingAction = PendingServiceAction(service.unit, "restart", server?.id) }, enabled = !loading, icon = Icons.Rounded.Refresh)
                                CommandSecondaryButton("Logs", { runAction(PendingServiceAction(service.unit, "logs", server?.id)) }, enabled = !loading, icon = Icons.Rounded.Terminal)
                            }
                        }
                    }
                }
            }
            if (evidence.isNotBlank()) {
                item {
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Evidence", Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                                Text(copy.svcRawSsh, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(CommandSpacing.sm))
                            Text(evidence, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 40, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val currentPrompt = prompt
    if (currentPrompt != null) {
        CommandHostKeyDialog(copy, currentPrompt, onDecision = { approved ->
            prompt = null
            scope.launch { promptChannel.send(approved) }
        })
    }
    val actionToConfirm = pendingAction
    if (actionToConfirm != null) {
        val action = actionToConfirm
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(copy.svcConfirmTitle.replace("%1", action.action).replace("%2", action.unit), fontWeight = FontWeight.Bold) },
            text = {
                Text("${copy.svcConfirmBody}\n\n${server?.name ?: copy.noServerSelected} · ${server?.host.orEmpty()}\n${action.unit} · ${action.action}", fontFamily = FontFamily.Monospace)
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    runAction(action)
                }, enabled = !loading && server?.id == action.serverId) { Text(copy.run, color = if (action.action == "stop") CommandColors.danger else CommandColors.accent) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }, enabled = !loading) { Text(copy.cancel) } }
        )
    }
}
