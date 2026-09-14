package org.didban.monitor

import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private data class CommandServerView(
    val server: ServerConfig,
    val state: Repo.State?,
    val tone: CommandHealthTone,
    val status: String
)

private fun buildServerView(server: ServerConfig, state: Repo.State?, copy: CommandCopy): CommandServerView {
    if (state == null || (state.metrics == null && state.error == null)) {
        return CommandServerView(server, state, CommandHealthTone.UNKNOWN, copy.waitingForData)
    }
    if (state.error != null) {
        return CommandServerView(server, state, CommandHealthTone.OFFLINE, copy.offline)
    }
    val metrics = state.metrics ?: return CommandServerView(server, state, CommandHealthTone.UNKNOWN, copy.unknownState)
    val attention = metrics.cpuUsage >= server.cpuAlert || metrics.memPct >= server.memAlert
    return CommandServerView(
        server = server,
        state = state,
        tone = if (attention) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY,
        status = if (attention) copy.attention else copy.healthy
    )
}

private fun CommandServerView.updatedLabel(copy: CommandCopy): String? {
    val updated = state?.updated ?: return null
    if (updated <= 0L) return null
    val formatted = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(updated))
    return "${copy.updated}: $formatted"
}

@Composable
fun CommandOverviewScreen(
    copy: CommandCopy,
    reloadTick: Int,
    onOpenServer: (ServerConfig) -> Unit,
    onOpenIncidents: () -> Unit,
    onOpenFleet: () -> Unit,
    onAddServer: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val servers = remember(reloadTick) { Prefs.loadServers(context).toList() }
    val states by Repo.states.collectAsState()
    val views = remember(servers, states, copy) {
        servers.map { buildServerView(it, states[it.id], copy) }
    }
    val healthyCount = views.count { it.tone == CommandHealthTone.HEALTHY }
    val attentionCount = views.count { it.tone == CommandHealthTone.ATTENTION }
    val offlineCount = views.count { it.tone == CommandHealthTone.OFFLINE }
    val unknownCount = views.count { it.tone == CommandHealthTone.UNKNOWN }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            CommandSectionTitle(
                title = copy.overview,
                supporting = if (servers.isEmpty()) copy.noServersBody else copy.activeAttention,
                actionLabel = copy.refresh,
                onAction = onRefresh,
                modifier = Modifier.padding(top = CommandSpacing.sm)
            )
        }

        if (servers.isEmpty()) {
            item {
                CommandEmptyState(
                    title = copy.noServersTitle,
                    body = copy.noServersBody,
                    actionLabel = copy.addServer,
                    onAction = onAddServer
                )
            }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    copy.activeAttention,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                    color = CommandColors.textPrimary
                                )
                                Spacer(Modifier.height(CommandSpacing.xxs))
                                Text(
                                    if (attentionCount + offlineCount > 0) copy.attention else copy.healthy,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = if (attentionCount + offlineCount > 0) CommandColors.warning else CommandColors.success
                                )
                            }
                            CommandSecondaryButton(
                                text = copy.incidents,
                                onClick = onOpenIncidents,
                                icon = Icons.Rounded.ArrowForward
                            )
                        }
                        Spacer(Modifier.height(CommandSpacing.md))
                        CommandRule()
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CommandSpacing.md)
                        ) {
                            CommandStatusMark(copy.healthy, CommandHealthTone.HEALTHY, Modifier.weight(1f), healthyCount.toString())
                            CommandStatusMark(copy.attention, CommandHealthTone.ATTENTION, Modifier.weight(1f), attentionCount.toString())
                            CommandStatusMark(copy.offline, CommandHealthTone.OFFLINE, Modifier.weight(1f), offlineCount.toString())
                            CommandStatusMark(copy.unknownState, CommandHealthTone.UNKNOWN, Modifier.weight(1f), unknownCount.toString())
                        }
                    }
                }
            }

            item {
                CommandSectionTitle(
                    title = copy.affectedServers,
                    supporting = "${servers.size} ${if (copy == CommandCopy.fa) "اتصال" else "connections"}",
                    actionLabel = copy.servers,
                    onAction = onOpenFleet
                )
            }

            items(views, key = { it.server.id }) { view ->
                CommandServerRow(view = view, copy = copy, onClick = { onOpenServer(view.server) })
            }

            item {
                CommandSectionTitle(
                    title = copy.recentActivity,
                    supporting = copy.incidentsFromLiveState
                )
            }
            item {
                if (attentionCount == 0 && offlineCount == 0) {
                    CommandStateBlock(
                        title = copy.noAttention,
                        body = copy.noAttentionBody,
                        tone = CommandHealthTone.HEALTHY
                    )
                } else {
                    CommandAttentionList(views, copy, onOpenServer)
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
private fun CommandServerRow(
    view: CommandServerView,
    copy: CommandCopy,
    onClick: () -> Unit
) {
    CommandSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CommandStatusMark(
                label = view.status,
                tone = view.tone,
                modifier = Modifier.weight(1f),
                detail = view.server.host
            )
            val metrics = view.state?.metrics
            if (metrics != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${Fmt.pct(metrics.cpuUsage)} CPU",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(fontFamily = Telemetry),
                        color = CommandColors.textPrimary
                    )
                    Text(
                        "${Fmt.pct(metrics.memPct)} ${copy.memory}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry),
                        color = CommandColors.textSecondary
                    )
                }
                Spacer(Modifier.width(CommandSpacing.sm))
            }
            Text(
                "›",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = CommandColors.textTertiary
            )
        }
    }
}

@Composable
private fun CommandAttentionList(
    views: List<CommandServerView>,
    copy: CommandCopy,
    onOpenServer: (ServerConfig) -> Unit
) {
    CommandSurface(modifier = Modifier.fillMaxWidth()) {
        Column {
            views.filter { it.tone == CommandHealthTone.ATTENTION || it.tone == CommandHealthTone.OFFLINE }
                .forEachIndexed { index, view ->
                    if (index > 0) CommandRule()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenServer(view.server) }
                            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CommandStatusMark(view.status, view.tone, Modifier.weight(1f), view.server.name)
                        Text(
                            copy.openServer,
                            color = CommandColors.accent,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                        )
                    }
                }
        }
    }
}

@Composable
fun CommandIncidentsScreen(
    copy: CommandCopy,
    reloadTick: Int,
    onOpenServer: (ServerConfig) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val servers = remember(reloadTick) { Prefs.loadServers(context).toList() }
    val states by Repo.states.collectAsState()
    val liveIncidents = remember(servers, states, copy) {
        buildList {
            servers.forEach { server ->
                val state = states[server.id] ?: return@forEach
                if (state.error != null) {
                    add(LiveIncident(server, copy.offline, state.error, CommandHealthTone.OFFLINE))
                } else {
                    val metrics = state.metrics ?: return@forEach
                    if (metrics.cpuUsage >= server.cpuAlert) {
                        add(LiveIncident(server, "${copy.cpu} ${copy.attention}", "${Fmt.pct(metrics.cpuUsage)} · threshold ${server.cpuAlert}%", CommandHealthTone.ATTENTION))
                    }
                    if (metrics.memPct >= server.memAlert) {
                        add(LiveIncident(server, "${copy.memory} ${copy.attention}", "${Fmt.pct(metrics.memPct)} · threshold ${server.memAlert}%", CommandHealthTone.ATTENTION))
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            CommandSectionTitle(
                title = copy.incidents,
                supporting = copy.incidentsFromLiveState,
                actionLabel = copy.refresh,
                onAction = onRefresh,
                modifier = Modifier.padding(top = CommandSpacing.sm)
            )
        }
        if (liveIncidents.isEmpty()) {
            item {
                CommandEmptyState(copy.noIncidentsTitle, copy.noIncidentsBody)
            }
        } else {
            items(liveIncidents, key = { "${it.server.id}:${it.title}" }) { incident ->
                CommandSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenServer(incident.server) }
                ) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        CommandStatusMark(incident.title, incident.tone, detail = incident.server.name)
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(
                            incident.detail,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry),
                            color = CommandColors.textSecondary
                        )
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(copy.openServer, color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

private data class LiveIncident(
    val server: ServerConfig,
    val title: String,
    val detail: String,
    val tone: CommandHealthTone
)

@Composable
fun CommandFleetScreen(
    copy: CommandCopy,
    reloadTick: Int,
    onOpenServer: (ServerConfig) -> Unit,
    onManageServers: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val servers = remember(reloadTick) { Prefs.loadServers(context).toList() }
    val states by Repo.states.collectAsState()
    var query by remember { mutableStateOf("") }
    val visible = remember(servers, states, query, copy) {
        servers
            .map { buildServerView(it, states[it.id], copy) }
            .filter { query.isBlank() || it.server.name.contains(query, true) || it.server.host.contains(query, true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            CommandSectionTitle(
                title = copy.servers,
                supporting = "${servers.size}",
                actionLabel = copy.refresh,
                onAction = onRefresh,
                modifier = Modifier.padding(top = CommandSpacing.sm)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(if (copy == CommandCopy.fa) "جستجوی نام یا Host" else "Search name or host") }
                )
                CommandSecondaryButton(
                    text = copy.manageServers,
                    onClick = onManageServers,
                    icon = Icons.Rounded.Edit,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        if (visible.isEmpty()) {
            item {
                CommandEmptyState(copy.noServersTitle, copy.noServersBody, copy.addServer, onManageServers)
            }
        } else {
            items(visible, key = { it.server.id }) { view ->
                CommandServerRow(view, copy, onClick = { onOpenServer(view.server) })
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandServerDossierScreen(
    copy: CommandCopy,
    server: ServerConfig?,
    state: Repo.State?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onManage: () -> Unit,
    onOpenProcesses: () -> Unit,
    onOpenDocker: () -> Unit,
    onOpenTunnels: () -> Unit
) {
    if (server == null) {
        CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.back, onBack)
        return
    }
    val view = buildServerView(server, state, copy)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CommandSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CommandBackButton(copy.back, onBack)
                CommandTextButton(copy.refresh, onRefresh, Icons.Rounded.Refresh)
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.lg)) {
                    CommandStatusMark(view.status, view.tone, detail = "${server.name} · ${server.host}:${server.port}")
                    Spacer(Modifier.height(CommandSpacing.md))
                    Text(
                        copy.serverDossier,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        color = CommandColors.textPrimary
                    )
                    Spacer(Modifier.height(CommandSpacing.xs))
                    Text(
                        view.updatedLabel(copy) ?: copy.waitingForData,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = CommandColors.textSecondary
                    )
                }
            }
        }
        if (state?.error != null) {
            item {
                CommandStateBlock(copy.offline, state.error ?: copy.offline, CommandHealthTone.OFFLINE, copy.retry, onRefresh)
            }
        }
        val metrics = state?.metrics
        if (metrics == null && state?.error == null) {
            item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        }
        if (metrics != null) {
            item {
                CommandSectionTitle(copy.recentActivity, copy.lastSeen)
            }
            item {
                CommandSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = CommandSpacing.md)) {
                        CommandMetricLine(copy.cpu, Fmt.pct(metrics.cpuUsage), if (metrics.cpuUsage >= server.cpuAlert) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY)
                        CommandRule()
                        CommandMetricLine(copy.memory, Fmt.pct(metrics.memPct), if (metrics.memPct >= server.memAlert) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY)
                        CommandRule()
                        CommandMetricLine(copy.load, "${metrics.load1}", CommandHealthTone.INFO)
                        CommandRule()
                        CommandMetricLine(copy.uptimeValue, Fmt.uptime(metrics.uptime), CommandHealthTone.INFO)
                        CommandRule()
                        CommandMetricLine(copy.latency, "${state?.latencyMs?.toInt() ?: 0} ms", CommandHealthTone.INFO)
                    }
                }
            }
        }
        item { CommandSectionTitle(copy.capabilities, copy.serverDossier) }
        item {
            CommandSurface(modifier = Modifier.fillMaxWidth()) {
                Column {
                    CommandDossierAction(copy.processes, copy, onOpenProcesses)
                    CommandRule()
                    CommandDossierAction(copy.docker, copy, onOpenDocker)
                    CommandRule()
                    CommandDossierAction(copy.tunnels, copy, onOpenTunnels)
                    CommandRule()
                    CommandDossierAction(copy.manageServers, copy, onManage)
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
private fun CommandDossierAction(label: String, copy: CommandCopy, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        Text("›", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    }
}
