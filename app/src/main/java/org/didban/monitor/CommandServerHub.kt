@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CommandServerHub(
    copy: CommandCopy,
    servers: List<ServerConfig>,
    loadFailed: Boolean,
    states: Map<Long, Repo.State>,
    destination: CommandDestination,
    onReload: () -> Unit,
    onHelp: () -> Unit,
    onOpenServer: (ServerConfig) -> Unit,
    onEdit: (Long?) -> Unit,
    onClosePane: () -> Unit,
    onSaved: (ServerConfig) -> Unit,
    onDeleted: (Long) -> Unit,
    onTool: (CommandRoute, ServerConfig?) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filterKey by rememberSaveable { mutableStateOf(FleetFilter.ALL.name) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var deleteTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var mutationError by remember { mutableStateOf<String?>(null) }
    val refreshState by PollingCoordinator.refreshState.collectAsState()
    val selected = servers.firstOrNull { it.id == destination.serverId }
    val filter = FleetFilter.values().firstOrNull { it.name == filterKey } ?: FleetFilter.ALL
    val visible = remember(servers, states, query, filter, now) { visibleFleet(servers, states, query, filter, now) }
    val health = servers.associate { it.id to fleetHealth(it, states[it.id], now) }
    val alerts = servers.filter { health[it.id] == FleetHealth.OFFLINE || health[it.id] == FleetHealth.ATTENTION }
    val refreshScope = refreshState.targets.singleOrNull()?.let { id -> servers.firstOrNull { it.id == id }?.name } ?: copy.allSystems

    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
    LaunchedEffect(destination.serverId) { mutationError = null }

    fun refresh(server: ServerConfig? = null) {
        if (refreshState.running) return
        val loaded = Prefs.loadServersResult(context)
        onReload()
        if (loaded.error != null) return
        val targets = if (server == null) loaded.servers else loaded.servers.filter { it.id == server.id }
        PollingCoordinator.refresh(context, targets)
    }

    @Composable
    fun details(modifier: Modifier = Modifier) {
        if (selected == null || loadFailed) {
            Column(modifier.padding(CommandSpacing.md)) {
                CommandCloseButton(copy.close, onClosePane)
                CommandStateBlock(copy.operationFailed,
                    if (loadFailed) securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_READ_FAILED) else copy.fleetMissing,
                    CommandHealthTone.OFFLINE)
            }
        } else key(selected.id) {
            CommandServerDetails(copy, selected, states[selected.id], now, refreshState, refreshScope,
                mutationError, onClosePane, { refresh(selected) }, { onEdit(selected.id) },
                { deleteTarget = selected.copy() }, { route -> onTool(route, selected) }, onHelp, modifier)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = maxWidth >= 840.dp
        val showDetails = destination.serverPane == ServerPane.DETAILS
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                item(key = "summary") {
                    Text(copy.servers, style = MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    Text(copy.fleetSummary, style = MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                    Spacer(Modifier.height(CommandSpacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        CommandPrimaryButton(copy.addServer, { onEdit(null) }, enabled = !loadFailed)
                        CommandRefreshButton(copy, refreshState.running, { refresh() }, enabled = !loadFailed)
                        CommandHelpButton(Prefs.getLanguage(context), onHelp)
                    }
                    if (!loadFailed) {
                        Text("${servers.size} ${copy.servers} · ${health.values.count { it == FleetHealth.HEALTHY }} ${copy.healthy} · " +
                            "${health.values.count { it == FleetHealth.OFFLINE }} ${copy.offline} · " +
                            "${health.values.count { it == FleetHealth.ATTENTION }} ${copy.attention} · ${health.values.count { it == FleetHealth.UNKNOWN }} ${copy.unknownState}",
                            color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = CommandSpacing.sm))
                        CommandRefreshFeedback(copy, refreshState, refreshScope)
                    }
                }
                if (loadFailed) item(key = "load-error") {
                    CommandStateBlock(copy.operationFailed,
                        securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_READ_FAILED),
                        CommandHealthTone.OFFLINE, copy.retry, onReload)
                } else {
                    if (alerts.isNotEmpty()) item(key = "alerts") {
                        CommandSurface(Modifier.fillMaxWidth(), raised = true) {
                            Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                                Text("${copy.fleetLiveAlerts} · ${alerts.size}", color = CommandColors.warning, style = MaterialTheme.typography.titleSmall)
                                Text(copy.fleetAlertsHint, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                alerts.sortedBy { health[it.id]?.ordinal }.take(3).forEach { server ->
                                    Row(Modifier.fillMaxWidth().clickable { onOpenServer(server) }.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                        Text(server.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = CommandColors.textPrimary)
                                        Text(health.getValue(server.id).label(copy), color = CommandColors.warning, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                CommandTextButton("${copy.attention} (${alerts.size})", { query = ""; filterKey = FleetFilter.ATTENTION.name })
                            }
                        }
                    }
                    if (servers.isEmpty()) item(key = "empty") {
                        CommandEmptyState(copy.noServersTitle, copy.noServersBody, copy.addServer, { onEdit(null) })
                    } else {
                        item(key = "filters") {
                            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(copy.serversSearchPlaceholder) })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                listOf(FleetFilter.ALL to copy.fleetAll, FleetFilter.OFFLINE to copy.offline, FleetFilter.ATTENTION to copy.attention).forEach { (value, label) ->
                                    FilterChip(selected = filter == value, onClick = { filterKey = value.name }, label = { Text(label) })
                                }
                            }
                            Text("${visible.size}/${servers.size} ${copy.servers}", color = CommandColors.textTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                        if (visible.isEmpty()) item(key = "no-matches") {
                            CommandEmptyState(copy.fleetNoMatches, copy.fleetSummary, copy.fleetClearFilters,
                                { query = ""; filterKey = FleetFilter.ALL.name })
                        }
                        items(visible, key = { "server-${it.id}" }) { server ->
                            CommandServerCard(copy, server, states[server.id], now, server.id == destination.serverId) { onOpenServer(server) }
                        }
                    }
                }
                item { Spacer(Modifier.height(CommandSpacing.lg)) }
            }
            if (split && showDetails) {
                CommandSurface(Modifier.weight(1f).fillMaxHeight()) { details(Modifier.fillMaxSize()) }
            }
        }
        if (!split && showDetails) {
            ModalBottomSheet(onDismissRequest = onClosePane,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = CommandColors.canvas) {
                details(Modifier.fillMaxWidth().fillMaxHeight(0.92f))
            }
        }
    }
    if (destination.serverPane.editing) {
        CommandServerEditor(copy, if (destination.serverPane == ServerPane.ADD) null else destination.serverId, onSaved, onClosePane)
    }
    deleteTarget?.let { expected ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null }, title = { Text(copy.srvDeleteTitle) },
            text = { Text(copy.srvDeleteBody.replace("%1", expected.name) + "\n${expected.host}:${expected.port}") },
            confirmButton = { TextButton(onClick = {
                val loaded = Prefs.loadServersResult(context)
                if (loaded.error != null) {
                    mutationError = securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_DELETE_BLOCKED)
                } else {
                    val next = try { ServerRecordEdits.delete(loaded.servers, expected) }
                    catch (_: IllegalStateException) { mutationError = copy.fleetRecordChanged; null }
                    if (next != null) {
                        try {
                            Prefs.saveServers(context, next)
                            HttpClientPool.evictForServer(expected)
                            Repo.remove(expected.id)
                            onDeleted(expected.id)
                        } catch (_: Exception) {
                            mutationError = securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_DELETE_FAILED)
                        }
                    }
                }
                deleteTarget = null
            }) { Text(copy.delete, color = CommandColors.danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(copy.cancel) } }
        )
    }
}

private fun FleetHealth.label(copy: CommandCopy): String = when (this) {
    FleetHealth.HEALTHY -> copy.healthy
    FleetHealth.OFFLINE -> copy.offline
    FleetHealth.ATTENTION -> copy.attention
    FleetHealth.UNKNOWN -> copy.waitingForData
}
private fun FleetHealth.tone(): CommandHealthTone = when (this) {
    FleetHealth.HEALTHY -> CommandHealthTone.HEALTHY
    FleetHealth.OFFLINE -> CommandHealthTone.OFFLINE
    FleetHealth.ATTENTION -> CommandHealthTone.ATTENTION
    FleetHealth.UNKNOWN -> CommandHealthTone.UNKNOWN
}

@Composable
private fun CommandServerCard(copy: CommandCopy, server: ServerConfig, state: Repo.State?, now: Long, selected: Boolean, onClick: () -> Unit) {
    val health = fleetHealth(server, state, now)
    CommandSurface(Modifier.fillMaxWidth().then(if (selected) Modifier.border(2.dp, CommandColors.accent, RoundedCornerShape(16.dp)) else Modifier).clickable(onClick = onClick)) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name, Modifier.weight(1f), color = CommandColors.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CommandStatusMark(health.label(copy), health.tone())
            }
            Text("${server.host}:${server.port}", color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state?.metrics?.let { m ->
                Text("CPU ${Fmt.pct(m.cpuUsage)} · RAM ${Fmt.pct(m.memPct)} · ${state.latencyMs.toInt()} ms", color = CommandColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
            }
            if (state?.error != null) Text(SecretRedactor.redact(state.error, listOf(server.token, server.adminToken)),
                color = CommandColors.danger, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (state != null && state.updated > 0) "${copy.updated}: ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.updated))}" else copy.waitingForData,
                color = CommandColors.textTertiary, style = MaterialTheme.typography.labelSmall)
            if (state?.metrics != null && now - state.updated > 120_000L) Text(copy.dataIsStale, color = CommandColors.warning, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CommandServerDetails(
    copy: CommandCopy, server: ServerConfig, state: Repo.State?, now: Long,
    refreshState: RefreshState, refreshScope: String, mutationError: String?,
    onClose: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onTool: (CommandRoute) -> Unit, onHelp: () -> Unit, modifier: Modifier = Modifier
) {
    val health = fleetHealth(server, state, now)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(copy.fleetDetails, Modifier.weight(1f), color = CommandColors.textPrimary, style = MaterialTheme.typography.titleMedium)
            CommandHelpButton(Prefs.getLanguage(LocalContext.current), onHelp)
            CommandCloseButton(copy.close, onClose)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
            item {
                Text(server.name, color = CommandColors.textPrimary, style = MaterialTheme.typography.headlineSmall)
                Text("${server.host}:${server.port}", color = CommandColors.textSecondary)
                CommandStatusMark(health.label(copy), health.tone())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    CommandRefreshButton(copy, refreshState.running, onRefresh)
                    CommandTextButton(copy.edit, onEdit)
                }
                if (server.id in refreshState.targets) CommandRefreshFeedback(copy, refreshState, refreshScope)
            }
            if (mutationError != null) item { CommandStateBlock(copy.operationFailed, mutationError, CommandHealthTone.OFFLINE) }
            if (state?.error != null) item {
                CommandStateBlock(copy.offline, SecretRedactor.redact(state.error, listOf(server.token, server.adminToken)), CommandHealthTone.OFFLINE)
            }
            val metrics = state?.metrics
            if (metrics == null && state?.error == null) item { Text(copy.waitingForData, color = CommandColors.textSecondary) }
            if (metrics != null) {
                item {
                    if (now - state.updated > 120_000L) Text(copy.dataIsStale, color = CommandColors.warning)
                    Text("${copy.updated}: ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.updated))}", color = CommandColors.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandMetricTile(copy.cpu, Fmt.pct(metrics.cpuUsage), "${server.cpuAlert}%", health.tone(), Modifier.weight(1f))
                        CommandMetricTile(copy.memory, Fmt.pct(metrics.memPct), "${Fmt.bytes(metrics.memUsed)} / ${Fmt.bytes(metrics.memTotal)}", health.tone(), Modifier.weight(1f))
                    }
                    Text("${copy.metricUptime}: ${Fmt.uptime(metrics.uptime)} · ${copy.metricLoad}: ${metrics.load1} · ${copy.latency}: ${state.latencyMs.toInt()} ms", color = CommandColors.textSecondary)
                }
                if (metrics.disks.isNotEmpty()) item { CommandSectionTitle(copy.metricDisks) }
                items(metrics.disks, key = { "disk-${it.mount}" }) { disk ->
                    Text("${disk.mount} · ${Fmt.pct(disk.pct)} · ${Fmt.bytes(disk.used)} / ${Fmt.bytes(disk.total)}", color = CommandColors.textSecondary)
                }
                if (metrics.nets.isNotEmpty()) item { CommandSectionTitle(copy.metricInterfaces) }
                items(metrics.nets, key = { "net-${it.name}" }) { net ->
                    Text("${net.name} · ↓ ${Fmt.rate(net.rx)} · ↑ ${Fmt.rate(net.tx)}", color = CommandColors.textSecondary)
                }
            }
            item { CommandSectionTitle(copy.capabilities) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    listOf(CommandRoute.DOCKER, CommandRoute.PROCESSES, CommandRoute.SSH, CommandRoute.TUNNELS, CommandRoute.SERVICES, CommandRoute.SFTP).forEach { route ->
                        CommandSecondaryButton(route.commandLabel(copy), { onTool(route) })
                    }
                }
            }
            item { CommandRule(); CommandTextButton(copy.delete, onDelete) }
        }
    }
}
