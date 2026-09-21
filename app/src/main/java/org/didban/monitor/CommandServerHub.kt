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
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date

/** میانگین یک سنجهٔ اختیاری روی ناوگان؛ «—» وقتی هیچ گرهی داده ندارد. */
private fun averageOf(views: List<CommandServerView>, pick: (CommandServerView) -> Float?): String {
    val values = views.mapNotNull(pick).filter { it.isFinite() && it > 0f }
    if (values.isEmpty()) return "\u2014"
    return "${values.average().roundToInt()}%"
}

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
    val refreshScope = refreshState.targets.singleOrNull()?.let { id -> servers.firstOrNull { it.id == id }?.name } ?: copy.allSystems
    val fleetViews = remember(servers, states, now, copy) { servers.map { buildServerView(it, states[it.id], copy) } }
    val fleetScore = commandHealthScore(fleetViews)
    val fleetHealths = fleetViews.map { it.tone }
    val fleetIssues = fleetViews.filter { it.tone == CommandHealthTone.OFFLINE || it.tone == CommandHealthTone.ATTENTION }
    val fleetTone = when {
        fleetViews.isEmpty() -> CommandHealthTone.UNKNOWN
        fleetHealths.any { it == CommandHealthTone.OFFLINE } -> CommandHealthTone.OFFLINE
        fleetHealths.any { it == CommandHealthTone.ATTENTION } -> CommandHealthTone.ATTENTION
        fleetHealths.any { it == CommandHealthTone.UNKNOWN } -> CommandHealthTone.UNKNOWN
        else -> CommandHealthTone.HEALTHY
    }

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

    when {
        destination.serverPane.editing -> CommandServerEditor(copy,
            if (destination.serverPane == ServerPane.ADD) null else destination.serverId,
            onSaved, onClosePane, embedded = true)
        destination.serverPane == ServerPane.DETAILS -> details(Modifier.fillMaxSize())
        else -> LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "summary") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(copy.uiMyServers, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    CommandHelpButton(Prefs.getLanguage(context), onHelp)
                }
                Text(copy.uiServerIntro, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandPrimaryButton(copy.addServer, { onEdit(null) }, enabled = !loadFailed)
                    CommandRefreshButton(copy, refreshState.running, { refresh() }, enabled = !loadFailed)
                }
                if (!loadFailed) CommandRefreshFeedback(copy, refreshState, refreshScope)
            }
            if (loadFailed) item(key = "load-error") {
                CommandStateBlock(copy.operationFailed,
                    securityMessage(Prefs.getLanguage(context), SecurityMessage.SERVER_READ_FAILED),
                    CommandHealthTone.OFFLINE, copy.retry, onReload)
            } else if (servers.isEmpty()) item(key = "empty") {
                CommandEmptyState(copy.noServersTitle, copy.noServersBody, copy.addServer, { onEdit(null) })
            } else {
                item(key = "filters") {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(copy.serversSearchPlaceholder) }, shape = RoundedCornerShape(CommandRadii.field))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(FleetFilter.ALL to copy.fleetAll, FleetFilter.OFFLINE to copy.offline,
                            FleetFilter.ATTENTION to copy.attention, FleetFilter.UNKNOWN to copy.uiUnknown).forEach { (value, label) ->
                            FilterChip(selected = filter == value, onClick = { filterKey = value.name },
                                shape = RoundedCornerShape(CommandRadii.pill), label = { Text(label) })
                        }
                    }
                    Text("${visible.size}/${servers.size} ${copy.nodes}", color = CommandColors.textSecondary, style = MaterialTheme.typography.labelMedium)
                }
                if (visible.isEmpty()) item(key = "no-matches") {
                    CommandEmptyState(copy.fleetNoMatches, copy.fleetSummary, copy.fleetClearFilters,
                        { query = ""; filterKey = FleetFilter.ALL.name })
                }
                item(key = "fleet-hero") {
                    CommandHeroCard(
                        modifier = Modifier.fillMaxWidth().commandEntrance(0),
                        eyebrow = copy.overview,
                        title = when {
                            fleetScore == null -> copy.waitingForData
                            fleetIssues.isEmpty() -> copy.healthy
                            else -> copy.attention
                        },
                        body = copy.fleetSummary,
                        score = fleetScore,
                        gaugeLabel = copy.scoreOutOf,
                        statusLabel = when (fleetTone) {
                            CommandHealthTone.OFFLINE -> copy.offline
                            CommandHealthTone.ATTENTION -> copy.attention
                            CommandHealthTone.HEALTHY -> copy.healthy
                            else -> copy.uiUnknown
                        },
                        statusTone = fleetTone,
                        segments = commandStatusSegments(listOf(
                            fleetHealths.count { it == CommandHealthTone.HEALTHY },
                            fleetHealths.count { it == CommandHealthTone.ATTENTION },
                            fleetHealths.count { it == CommandHealthTone.OFFLINE },
                            fleetHealths.count { it == CommandHealthTone.UNKNOWN }
                        )),
                        badgeIcon = Icons.Rounded.MonitorHeart,
                        actionLabel = copy.addServer,
                        onAction = { onEdit(null) }
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${servers.size} ${copy.nodes} · ${copy.cpu} ${averageOf(fleetViews) { it.state?.metrics?.cpuUsage }}",
                                color = CommandHeroInk.muted,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (fleetIssues.isNotEmpty()) item(key = "fleet-notice") {
                    CommandNoticeRow(
                        modifier = Modifier.fillMaxWidth().commandEntrance(1),
                        title = fleetIssues.first().server.name,
                        body = "${fleetIssues.first().status} · ${if (fleetIssues.size > 1) "${fleetIssues.size} ${copy.nodes}" else copy.attention}",
                        tone = fleetIssues.first().tone,
                        onClick = { onOpenServer(fleetIssues.first().server) }
                    )
                }
                items(visible, key = { "server-${it.id}" }) { server ->
                    CommandServerCard(copy, server, states[server.id], now, false) { onOpenServer(server) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
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
    FleetHealth.UNKNOWN -> copy.uiUnknown
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
    CommandSurface(Modifier.fillMaxWidth().then(if (selected) Modifier.border(2.dp, CommandColors.accent, RoundedCornerShape(CommandRadii.card)) else Modifier).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(server.name, Modifier.weight(1f), color = CommandColors.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                CommandStatusMark(health.label(copy), health.tone())
            }
            Text("${server.host}:${server.port}", color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state?.metrics?.let { m ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CommandResourcePreview(copy.cpu, m.cpuUsage, Modifier.weight(1f))
                    CommandResourcePreview(copy.memory, m.memPct, Modifier.weight(1f))
                }
            }
            if (state?.error != null) Text(SecretRedactor.redact(state.error, listOf(server.token, server.adminToken)),
                color = CommandColors.danger, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (state != null && state.updated > 0) "${copy.updated}: ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.updated))}" else copy.waitingForData,
                color = CommandColors.textTertiary, style = MaterialTheme.typography.labelSmall)
            if (state?.metrics != null && now - state.updated > 120_000L) Text(copy.dataIsStale, color = CommandColors.warning, style = MaterialTheme.typography.labelSmall)
            Text(copy.openServer, color = CommandColors.accent, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp))
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
    val clipboard = LocalClipboardManager.current
    var updateCopied by remember(server.id) { mutableStateOf(false) }
    Column(modifier) {
        CommandPageChrome(copy.fleetDetails, copy, Prefs.getLanguage(LocalContext.current), true, onClose, onHelp)
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
                // A pin rotation is explained in the user's language with a
                // pointer to the re-pin flow; other errors keep redacted raw text.
                val mismatch = state.fingerprintMismatch
                val detail = if (mismatch != null) {
                    copy.connFingerprintMismatch
                        .replace("%1", mismatch.expectedPrefix)
                        .replace("%2", mismatch.observedPrefix)
                } else {
                    SecretRedactor.redact(state.error, listOf(server.token, server.adminToken))
                }
                CommandStateBlock(copy.offline, detail, CommandHealthTone.OFFLINE)
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
                    if (metrics.agentVersion.isNotBlank()) {
                        Text("${copy.agentVersion}: ${metrics.agentVersion}", color = CommandColors.textSecondary)
                        if (isAgentOutdated(metrics.agentVersion)) {
                            CommandStateBlock(
                                copy.agentOutdated, copy.agentOutdatedBody, CommandHealthTone.ATTENTION,
                                copy.copyAgentUpdateCommand,
                                {
                                    clipboard.setText(AnnotatedString(AGENT_UPDATE_COMMAND))
                                    updateCopied = true
                                }
                            )
                            if (updateCopied) Text(copy.agentUpdateCommandCopied, color = CommandColors.textSecondary)
                        }
                    }
                }
            }
            item { CommandSectionTitle(copy.uiServerTools, copy.uiScopedTools.replace("%1", server.name)) }
            items(serverToolRoutes, key = { "tool-${it.key}" }) { route ->
                CommandToolLink(copy, route, onClick = { onTool(route) })
            }
            if (metrics != null) item {
                CommandDisclosure(copy, title = copy.uiResources) {
                    if (metrics.disks.isNotEmpty()) CommandSectionTitle(copy.metricDisks)
                    metrics.disks.forEach { disk ->
                        Text("${disk.mount} · ${Fmt.pct(disk.pct)} · ${Fmt.bytes(disk.used)} / ${Fmt.bytes(disk.total)}", color = CommandColors.textSecondary)
                    }
                    if (metrics.nets.isNotEmpty()) CommandSectionTitle(copy.metricInterfaces)
                    metrics.nets.forEach { net ->
                        Text("${net.name} · ↓ ${Fmt.rate(net.rx)} · ↑ ${Fmt.rate(net.tx)}", color = CommandColors.textSecondary)
                    }
                }
            }
            item { CommandRule(); CommandTextButton(copy.delete, onDelete) }
        }
    }
}

@Composable
private fun CommandResourcePreview(label: String, value: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Text(Fmt.pct(value), color = CommandColors.textPrimary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { (value / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
            color = CommandColors.accent, trackColor = CommandColors.track)
    }
}
