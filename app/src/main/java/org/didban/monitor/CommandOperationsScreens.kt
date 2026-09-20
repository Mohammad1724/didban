package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class PendingTunnelOperation(
    val tunnel: TunnelConfig,
    val server: ServerConfig,
    val action: String
)

private fun tunnelTone(tunnel: TunnelConfig): CommandHealthTone = when {
    !tunnel.isEnabled -> CommandHealthTone.UNKNOWN
    tunnel.lastStatus == 1 -> CommandHealthTone.HEALTHY
    tunnel.lastStatus == 0 -> CommandHealthTone.OFFLINE
    else -> CommandHealthTone.UNKNOWN
}

private fun tunnelStatus(tunnel: TunnelConfig, copy: CommandCopy): String = when {
    !tunnel.isEnabled -> copy.unknownState
    tunnel.lastStatus == 1 -> copy.healthy
    tunnel.lastStatus == 0 -> copy.offline
    else -> copy.waitingForData
}

@Composable
fun CommandTunnelsScreen(
    copy: CommandCopy,
    reloadTick: Int,
    selectedServer: ServerConfig?,
    onOpenEditor: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tunnels by remember(reloadTick) { mutableStateOf(Prefs.loadTunnels(context)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var pendingOperation by remember { mutableStateOf<PendingTunnelOperation?>(null) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> tunnels.firstOrNull { it.id == id } }

    val refreshController = remember { ManualRefreshController(scope) }
    val refreshState by refreshController.state.collectAsState()

    fun refresh() {
        if (refreshState.running) return
        tunnels = Prefs.loadTunnels(context)
        val targets = tunnels.filter { it.isEnabled && (it.iranHost.isNotBlank() || it.foreignHost.isNotBlank()) }
            .associateBy { it.id }
        refreshController.request(targets.keys.toList()) { id ->
            val result = TunnelEngine.testTunnel(targets.getValue(id))
            // Merge only health into the latest preferences; don't resurrect a
            // deleted tunnel or overwrite an edit made while the probe was running.
            val latest = Prefs.loadTunnels(context)
            latest.firstOrNull { it.id == id }?.let {
                it.lastStatus = if (result.first) 1 else 0
                it.lastLatencyMs = result.second
                it.lastChecked = System.currentTimeMillis()
            }
            Prefs.saveTunnels(context, latest)
            tunnels = latest
            result.first
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = CommandSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    if (onBack != null) CommandBackButton(copy.back, onBack)
                    Text(copy.tunnels, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    Text("${tunnels.size} · ${copy.operate}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                }
                CommandRefreshButton(copy, refreshState.running, ::refresh)
            }
        }
        item { CommandRefreshFeedback(copy, refreshState, copy.tunnels) }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Text(copy.tunConfiguration, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Spacer(Modifier.height(CommandSpacing.xs))
                    Text(copy.tunnelsBody, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                    Spacer(Modifier.height(CommandSpacing.sm))
                    CommandSecondaryButton(copy.addTunnel, onOpenEditor, icon = Icons.Rounded.Add)
                }
            }
        }
        if (tunnels.isEmpty()) {
            item { CommandEmptyState(copy.tunnels, copy.noServersBody, copy.addTunnel, onOpenEditor) }
        } else {
            items(tunnels, key = { it.id }) { tunnel ->
                CommandSurface(
                    modifier = Modifier.fillMaxWidth().clickable { selectedId = tunnel.id }
                ) {
                    Column(Modifier.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CommandStatusMark(tunnelStatus(tunnel, copy), tunnelTone(tunnel), Modifier.weight(1f), tunnel.name)
                            Text(tunnel.core.name, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = CommandColors.textSecondary)
                        }
                        Spacer(Modifier.height(CommandSpacing.xs))
                        Text(
                            "${tunnel.iranHost.ifBlank { "—" }} → ${tunnel.foreignHost.ifBlank { "—" }} · ${tunnel.transport.name}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry),
                            color = CommandColors.textSecondary
                        )
                    }
                }
            }
        }
        if (selected != null) {
            item {
                CommandTunnelDetail(copy, selected, selectedServer, busyId == selected.id, operationMessage, onOpenEditor, onAction = { action ->
                    val server = selectedServer
                    if (server == null) operationMessage = copy.selectServer
                    else if (busyId == null && action in setOf("start", "stop", "restart")) {
                        pendingOperation = PendingTunnelOperation(selected, server, action)
                    }
                })
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val operation = pendingOperation
    if (operation != null) {
        AlertDialog(
            onDismissRequest = { if (busyId == null) pendingOperation = null },
            title = { Text(operation.action, fontWeight = FontWeight.Bold) },
            text = { Text("${operation.server.name} · ${operation.server.host}:${operation.server.port}\n${operation.tunnel.name} · #${operation.tunnel.id}\n${operation.action}") },
            confirmButton = {
                TextButton(onClick = {
                    if (busyId != null) return@TextButton
                    pendingOperation = null
                    busyId = operation.tunnel.id
                    operationMessage = null
                    scope.launch {
                        runCatching {
                            val current = Prefs.loadTunnels(context).firstOrNull { it.id == operation.tunnel.id }
                            check(current != null && current.name == operation.tunnel.name && current.iranHost == operation.tunnel.iranHost && current.foreignHost == operation.tunnel.foreignHost) {
                                copy.tunActionFailedGeneric
                            }
                            check(selectedServer?.id == operation.server.id) { copy.selectServer }
                            val api = ApiClient()
                            api.tunnelStatus(operation.server, operation.tunnel.id.toString())
                            when (operation.action) {
                                "start" -> api.tunnelStart(operation.server, operation.tunnel.id.toString())
                                "stop" -> api.tunnelStop(operation.server, operation.tunnel.id.toString())
                                "restart" -> api.tunnelRestart(operation.server, operation.tunnel.id.toString())
                                else -> error("Unsupported tunnel action")
                            }
                        }.onSuccess {
                            operationMessage = copy.operationDone
                            refresh()
                        }.onFailure {
                            operationMessage = "${copy.operationFailed}: ${SecretRedactor.redact(it.message ?: copy.unknownState, listOf(operation.server.token, operation.tunnel.token)).take(300)}"
                        }
                        busyId = null
                    }
                }, enabled = busyId == null) { Text(copy.run, color = if (operation.action == "stop") CommandColors.danger else CommandColors.accent) }
            },
            dismissButton = { TextButton(onClick = { pendingOperation = null }, enabled = busyId == null) { Text(copy.close) } }
        )
    }
}

@Composable
private fun CommandTunnelDetail(
    copy: CommandCopy,
    tunnel: TunnelConfig,
    selectedServer: ServerConfig?,
    busy: Boolean,
    operationMessage: String?,
    onOpenEditor: () -> Unit,
    onAction: (String) -> Unit
) {
    CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
            Text(tunnel.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = CommandColors.textPrimary)
            Text("${tunnel.core.name} · ${tunnel.transport.name}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), color = CommandColors.textSecondary)
            CommandRule()
            CommandMetricLine(copy.latency, if (tunnel.lastLatencyMs >= 0) "${tunnel.lastLatencyMs} ms" else copy.waitingForData, tunnelTone(tunnel))
            CommandMetricLine(copy.currentServer, selectedServer?.name ?: copy.noServerSelected, CommandHealthTone.INFO)
            if (operationMessage != null) {
                Text(operationMessage, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.warning)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                CommandSecondaryButton(copy.start, { onAction("start") }, icon = Icons.Rounded.PlayArrow, enabled = !busy)
                CommandSecondaryButton(copy.stop, { onAction("stop") }, icon = Icons.Rounded.Stop, enabled = !busy)
                CommandSecondaryButton(copy.restart, { onAction("restart") }, icon = Icons.Rounded.Refresh, enabled = !busy)
            }
            CommandTextButton(copy.edit, onOpenEditor)
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = CommandColors.accent)
        }
    }
}

@Composable
fun CommandProcessesScreen(
    copy: CommandCopy,
    server: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var processes by remember(server?.id) { mutableStateOf<List<ProcInfo>>(emptyList()) }
    var loading by remember(server?.id) { mutableStateOf(false) }
    var error by remember(server?.id) { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ProcInfo?>(null) }
    var killSignal by remember { mutableStateOf("SIGTERM") }
    var killConfirm by remember { mutableStateOf<ProcInfo?>(null) }
    var acting by remember(server?.id) { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    var loadJob by remember(server?.id) { mutableStateOf<Job?>(null) }
    var refreshedAt by remember(server?.id) { mutableStateOf(0L) }
    DisposableEffect(server?.id) { onDispose { loadJob?.cancel() } }

    fun load() {
        val target = server ?: return
        if (loading) return
        loading = true
        error = null
        loadJob = scope.launch {
            try {
                processes = ApiClient().processes(target)
                refreshedAt = System.currentTimeMillis()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.operationFailed
                refreshedAt = System.currentTimeMillis()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) { load() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.processes, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
                CommandRefreshButton(copy, loading, ::load, enabled = server != null)
            }
        }
        item {
            CommandRefreshFeedback(copy, commandLoadState(loading, error, refreshedAt, server?.id), copy.processes)
        }
        if (server == null) item { CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.selectServer, onSelectServer) }
        else if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: copy.operationFailed, CommandHealthTone.OFFLINE, copy.retry, ::load) }
        else if (loading && processes.isEmpty()) item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        else if (processes.isEmpty()) item { CommandEmptyState(copy.processes, copy.noAttentionBody) }
        else {
            item {
                CommandSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        processes.sortedByDescending { it.cpu }.forEachIndexed { index, process ->
                            if (index > 0) CommandRule()
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = process }.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(process.name, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text("PID ${process.pid} · ${process.user}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${Fmt.pct(process.cpu)} CPU", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontFamily = Telemetry))
                                    Text("${Fmt.pct(process.memPct)} RAM", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (result != null) item { Text(result ?: "", color = CommandColors.warning, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(selected?.name ?: copy.processes) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(selected?.cmd ?: "", style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandSecondaryButton("SIGTERM", { killSignal = "SIGTERM" }, enabled = killSignal != "SIGTERM" && !acting)
                        CommandSecondaryButton("SIGKILL", { killSignal = "SIGKILL" }, enabled = killSignal != "SIGKILL" && !acting)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { killConfirm = selected; selected = null }) { Text(copy.stop) } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(copy.close) } }
        )
    }
    if (killConfirm != null) {
        AlertDialog(
            onDismissRequest = { killConfirm = null },
            title = { Text(copy.stop) },
            text = { Text("PID ${killConfirm?.pid} · $killSignal") },
            confirmButton = {
                TextButton(onClick = {
                    val target = server
                    val process = killConfirm
                    killConfirm = null
                    if (target != null && process != null && !acting) {
                        acting = true
                        scope.launch {
                            runCatching {
                                require(killSignal == "SIGTERM" || killSignal == "SIGKILL") { "Unsupported signal" }
                                // Re-fetch immediately before signalling. This prevents a stale
                                // row from killing an unrelated process after PID reuse.
                                val current = ApiClient().processes(target).firstOrNull { it.pid == process.pid }
                                check(current != null && current.name == process.name && current.user == process.user) {
                                    "Process changed or exited; refresh before retrying"
                                }
                                ApiClient().killProcess(target, process.pid, killSignal)
                            }.onSuccess { result = it.message; load() }
                                .onFailure {
                                    result = "${copy.operationFailed}: ${SecretRedactor.redact(it.message ?: copy.operationFailed, listOf(target.token)).take(300)}"
                                }
                            acting = false
                        }
                    }
                }, enabled = !acting) { Text(copy.run) }
            },
            dismissButton = { TextButton(onClick = { killConfirm = null }, enabled = !acting) { Text(copy.close) } }
        )
    }
}

@Composable
fun CommandDockerScreen(
    copy: CommandCopy,
    server: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var data by remember(server?.id) { mutableStateOf<DockerSummaryData?>(null) }
    var loading by remember(server?.id) { mutableStateOf(false) }
    var error by remember(server?.id) { mutableStateOf<String?>(null) }
    var endpointMissing by remember(server?.id) { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DockerContainerItem?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var pendingContainerId by remember { mutableStateOf<String?>(null) }
    var operating by remember(server?.id) { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    var loadJob by remember(server?.id) { mutableStateOf<Job?>(null) }
    var refreshedAt by remember(server?.id) { mutableStateOf(0L) }
    DisposableEffect(server?.id) { onDispose { loadJob?.cancel() } }

    fun load() {
        val target = server ?: return
        if (loading) return
        loading = true
        error = null
        endpointMissing = false
        loadJob = scope.launch {
            try {
                data = ApiClient().dockerContainers(target)
                refreshedAt = System.currentTimeMillis()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = describeAgentToolFailure(e, copy, target, bandwidth = false)
                endpointMissing = isMissingEndpointFailure(e)
                refreshedAt = System.currentTimeMillis()
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(server?.id) { load() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.docker, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
                CommandRefreshButton(copy, loading, ::load, enabled = server != null)
            }
        }
        item {
            CommandRefreshFeedback(copy, commandLoadState(loading, error, refreshedAt, server?.id), copy.docker)
        }
        if (server == null) item { CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.selectServer, onSelectServer) }
        else if (error != null) item {
            CommandStateBlock(
                copy.operationFailed, error ?: copy.operationFailed, CommandHealthTone.OFFLINE,
                copy.retry, ::load,
                secondActionLabel = if (endpointMissing) copy.copyAgentUpdateCommand else null,
                onSecondAction = if (endpointMissing) {
                    {
                        clipboard.setText(AnnotatedString(AGENT_UPDATE_COMMAND))
                        result = copy.agentUpdateCommandCopied
                    }
                } else null
            )
        }
        else if (loading && data == null) item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        else if (data?.installed == false || !data?.error.isNullOrBlank()) item {
            CommandStateBlock(copy.docker, copy.dockerUnavailable + data?.error?.takeIf { it.isNotBlank() }?.let {
                "\n" + SecretRedactor.redact(it, listOf(server.token, server.adminToken)).take(300)
            }.orEmpty(), CommandHealthTone.ATTENTION, copy.retry, ::load)
        }
        else if (data?.containers.isNullOrEmpty()) item { CommandEmptyState(copy.docker, copy.noAttentionBody) }
        else {
            item {
                CommandSurface(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        data?.containers?.forEachIndexed { index, container ->
                            if (index > 0) CommandRule()
                            Row(Modifier.fillMaxWidth().clickable { selected = container }.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CommandStatusMark(container.state, if (container.state.equals("running", true)) CommandHealthTone.HEALTHY else CommandHealthTone.UNKNOWN, Modifier.weight(1f), container.name)
                                Text(container.image, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (result != null) item { Text(result ?: "", color = CommandColors.warning, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(selected?.name ?: copy.docker) },
            text = { Text("${selected?.status}\n${selected?.image}\n${selected?.ports?.joinToString { "${it.publicPort}:${it.privatePort}" } ?: ""}", fontFamily = Telemetry) },
            confirmButton = { TextButton(onClick = { pendingContainerId = selected?.id; confirmAction = "restart"; selected = null }, enabled = !operating) { Text(copy.restart) } },
            dismissButton = { TextButton(onClick = { pendingContainerId = selected?.id; confirmAction = "stop"; selected = null }, enabled = !operating) { Text(copy.stop) } }
        )
    }
    if (confirmAction != null) {
        val action = confirmAction
        val pendingContainer = data?.containers?.firstOrNull { it.id == pendingContainerId }
        AlertDialog(
            onDismissRequest = { if (!operating) confirmAction = null },
            title = { Text(action ?: copy.docker) },
            text = { Text("${server?.name ?: copy.noServerSelected}\n${pendingContainer?.name ?: pendingContainerId.orEmpty()} · ${pendingContainer?.id.orEmpty()}\n${action ?: ""}", fontFamily = Telemetry) },
            confirmButton = {
                TextButton(onClick = {
                    val target = server
                    val container = data?.containers?.firstOrNull { it.id == pendingContainerId }
                    confirmAction = null
                    pendingContainerId = null
                    if (target != null && container != null && !operating) {
                        operating = true
                        scope.launch {
                            runCatching {
                                val api = ApiClient()
                                val current = api.dockerContainers(target).containers.firstOrNull { it.id == container.id }
                                check(current != null && current.name == container.name) {
                                    "Container changed or disappeared; refresh before retrying"
                                }
                                if (action == "restart") api.dockerRestart(target, current.id)
                                else api.dockerStop(target, current.id)
                            }.onSuccess { result = copy.operationDone; load() }
                                .onFailure {
                                    result = "${copy.operationFailed}: ${SecretRedactor.redact(it.message ?: copy.operationFailed, listOf(target.token)).take(300)}"
                                }
                            operating = false
                        }
                    }
                }, enabled = !operating && pendingContainer != null) { Text(copy.run) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }, enabled = !operating) { Text(copy.close) } }
        )
    }
}
