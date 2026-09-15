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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    var operationMessage by remember { mutableStateOf<String?>(null) }
    val selected = selectedId?.let { id -> tunnels.firstOrNull { it.id == id } }

    fun refresh() {
        tunnels = Prefs.loadTunnels(context)
        scope.launch {
            tunnels.filter { it.isEnabled && (it.iranHost.isNotBlank() || it.foreignHost.isNotBlank()) }.forEach { tunnel ->
                runCatching {
                    val result = TunnelEngine.testTunnel(tunnel)
                    tunnel.lastStatus = if (result.first) 1 else 0
                    tunnel.lastLatencyMs = result.second
                    tunnel.lastChecked = System.currentTimeMillis()
                }
            }
            Prefs.saveTunnels(context, tunnels)
            tunnels = Prefs.loadTunnels(context)
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
                CommandTextButton(copy.refresh, ::refresh, Icons.Rounded.Refresh)
            }
        }
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
                    busyId = selected.id
                    operationMessage = null
                    scope.launch {
                        val server = selectedServer
                        if (server == null) {
                            operationMessage = copy.selectServer
                            busyId = null
                            return@launch
                        }
                        runCatching {
                            val api = ApiClient()
                            when (action) {
                                "start" -> api.tunnelStart(server, selected.id.toString())
                                "stop" -> api.tunnelStop(server, selected.id.toString())
                                else -> api.tunnelRestart(server, selected.id.toString())
                            }
                        }.onSuccess {
                            operationMessage = copy.operationDone
                            refresh()
                        }.onFailure {
                            operationMessage = "${copy.operationFailed}: ${it.message ?: copy.unknownState}"
                        }
                        busyId = null
                    }
                })
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
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
    var result by remember { mutableStateOf<String?>(null) }

    fun load() {
        val target = server ?: return
        loading = true
        error = null
        scope.launch {
            runCatching { ApiClient().processes(target) }
                .onSuccess { processes = it }
                .onFailure { error = it.message ?: copy.operationFailed }
            loading = false
        }
    }

    LaunchedEffect(server?.id) { load() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.processes, server?.name ?: copy.noServerSelected, copy.refresh, ::load, Modifier.weight(1f))
            }
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
                    OutlinedTextField(killSignal, { killSignal = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(12) }, label = { Text("Signal") }, singleLine = true)
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
                    if (target != null && process != null) {
                        scope.launch {
                            runCatching { ApiClient().killProcess(target, process.pid, killSignal) }
                                .onSuccess { result = it.message; load() }
                                .onFailure { result = "${copy.operationFailed}: ${it.message}" }
                        }
                    }
                }) { Text(copy.run) }
            },
            dismissButton = { TextButton(onClick = { killConfirm = null }) { Text(copy.close) } }
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
    var data by remember(server?.id) { mutableStateOf<DockerSummaryData?>(null) }
    var loading by remember(server?.id) { mutableStateOf(false) }
    var error by remember(server?.id) { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<DockerContainerItem?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var pendingContainerId by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    fun load() {
        val target = server ?: return
        loading = true
        error = null
        scope.launch {
            runCatching { ApiClient().dockerContainers(target) }
                .onSuccess { data = it }
                .onFailure { error = it.message ?: copy.operationFailed }
            loading = false
        }
    }
    LaunchedEffect(server?.id) { load() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.docker, server?.name ?: copy.noServerSelected, copy.refresh, ::load, Modifier.weight(1f))
            }
        }
        if (server == null) item { CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.selectServer, onSelectServer) }
        else if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: copy.operationFailed, CommandHealthTone.OFFLINE, copy.retry, ::load) }
        else if (loading && data == null) item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        else if (data?.installed == false) item { CommandStateBlock(copy.docker, copy.notAvailable, CommandHealthTone.INFO) }
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
            confirmButton = { TextButton(onClick = { pendingContainerId = selected?.id; confirmAction = "restart"; selected = null }) { Text(copy.restart) } },
            dismissButton = { TextButton(onClick = { pendingContainerId = selected?.id; confirmAction = "stop"; selected = null }) { Text(copy.stop) } }
        )
    }
    if (confirmAction != null) {
        val action = confirmAction
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action ?: copy.docker) },
            text = { Text("${copy.docker} · ${action ?: ""}") },
            confirmButton = {
                TextButton(onClick = {
                    val target = server
                    val container = data?.containers?.firstOrNull { it.id == pendingContainerId }
                    confirmAction = null
                    pendingContainerId = null
                    if (target != null && container != null) {
                        scope.launch {
                            runCatching {
                                if (action == "restart") ApiClient().dockerRestart(target, container.id)
                                else ApiClient().dockerStop(target, container.id)
                            }.onSuccess { result = copy.operationDone; load() }
                                .onFailure { result = "${copy.operationFailed}: ${it.message}" }
                        }
                    }
                }) { Text(copy.run) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(copy.close) } }
        )
    }
}
