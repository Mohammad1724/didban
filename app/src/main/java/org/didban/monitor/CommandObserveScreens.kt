package org.didban.monitor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class CommandServerView(
    val server: ServerConfig,
    val state: Repo.State?,
    val tone: CommandHealthTone,
    val status: String,
    val stale: Boolean = false
)

@Composable
private fun observeToneColor(tone: CommandHealthTone) = when (tone) {
    CommandHealthTone.HEALTHY -> CommandColors.success
    CommandHealthTone.ATTENTION -> CommandColors.warning
    CommandHealthTone.OFFLINE -> CommandColors.danger
    CommandHealthTone.INFO -> CommandColors.info
    CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
}

private fun buildServerView(server: ServerConfig, state: Repo.State?, copy: CommandCopy): CommandServerView {
    val stale = state?.updated?.let { it > 0L && System.currentTimeMillis() - it > 120_000L } == true
    if (state == null || (state.metrics == null && state.error == null)) {
        return CommandServerView(server, state, CommandHealthTone.UNKNOWN, if (stale) copy.dataIsStale else copy.waitingForData, stale)
    }
    if (state.error != null) {
        return CommandServerView(server, state, CommandHealthTone.OFFLINE, copy.offline, stale)
    }
    val metrics = state.metrics ?: return CommandServerView(server, state, CommandHealthTone.UNKNOWN, copy.unknownState, stale)
    val attention = metrics.cpuUsage >= server.cpuAlert || metrics.memPct >= server.memAlert
    return CommandServerView(
        server = server,
        state = state,
        tone = if (attention || stale) CommandHealthTone.ATTENTION else CommandHealthTone.HEALTHY,
        status = when {
            stale -> copy.dataIsStale
            attention -> copy.attention
            else -> copy.healthy
        },
        stale = stale
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
    val score = commandHealthScore(views)
    val metricStates = views.mapNotNull { it.state?.metrics }
    val averageCpu = metricStates.map { it.cpuUsage }.averageOrNull()
    val averageMemory = metricStates.map { it.memPct }.averageOrNull()
    val averageLatency = views.mapNotNull { it.state?.latencyMs?.takeIf { latency -> latency > 0f } }.averageOrNull()
    val knownCount = views.count { it.state?.metrics != null || it.state?.error != null }
    val statusTone = when {
        offlineCount > 0 -> CommandHealthTone.OFFLINE
        attentionCount > 0 -> CommandHealthTone.ATTENTION
        knownCount > 0 && unknownCount == 0 -> CommandHealthTone.HEALTHY
        else -> CommandHealthTone.UNKNOWN
    }
    val statusLabel = when (statusTone) {
        CommandHealthTone.OFFLINE -> copy.offline
        CommandHealthTone.ATTENTION -> copy.attention
        CommandHealthTone.HEALTHY -> copy.healthy
        else -> copy.waitingForData
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 920.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = CommandSpacing.md),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(copy.overview, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                        Spacer(Modifier.height(CommandSpacing.xxs))
                        Text(
                            if (servers.isEmpty()) copy.noServersBody else copy.incidentsFromLiveState,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = CommandColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CommandTextButton(copy.refresh, onRefresh, Icons.Rounded.Refresh)
                }
            }

            if (servers.isEmpty()) {
                item {
                    CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                        CommandEmptyState(copy.noServersTitle, copy.noServersBody, copy.addServer, onAddServer)
                    }
                }
            } else {
                item {
                    CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.lg)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(copy.activeAttention.uppercase(), color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                                    Spacer(Modifier.height(CommandSpacing.xs))
                                    Text(
                                        when {
                                            score == null -> copy.waitingForData
                                            attentionCount + offlineCount > 0 -> copy.attention
                                            else -> copy.healthy
                                        },
                                        color = CommandColors.textPrimary,
                                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                                    )
                                    Spacer(Modifier.height(CommandSpacing.xs))
                                    Text(
                                        if (knownCount == 0) copy.waitingForData else copy.incidentsFromLiveState,
                                        color = CommandColors.textSecondary,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        maxLines = if (wide) 2 else 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                CommandTelemetryPill(statusLabel, statusTone)
                            }
                            Spacer(Modifier.height(CommandSpacing.lg))
                            if (wide) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CommandRingGauge(score, copy.scoreOutOf)
                                        Text("${knownCount}/${views.size} ${copy.coverage}", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                                    }
                                    CommandTelemetryOrbit(views.map { it.tone }, Modifier.weight(1f), "${views.size} ${copy.nodes}")
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    CommandRingGauge(score, copy.scoreOutOf)
                                    Spacer(Modifier.height(CommandSpacing.xs))
                                    Text("${knownCount}/${views.size} ${copy.coverage}", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                                    Spacer(Modifier.height(CommandSpacing.md))
                                    CommandTelemetryOrbit(views.map { it.tone }, Modifier.fillMaxWidth(), "${views.size} ${copy.nodes}")
                                }
                            }
                            Spacer(Modifier.height(CommandSpacing.lg))
                            if (wide) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                    CommandMetricTile(copy.online, "$healthyCount / ${views.size}", copy.healthy, CommandHealthTone.HEALTHY, Modifier.weight(1f))
                                    CommandMetricTile(copy.averageCpu, averageCpu?.let { Fmt.pct(it) } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f))
                                    CommandMetricTile(copy.averageMemory, averageMemory?.let { Fmt.pct(it) } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f), CommandColors.violet)
                                    CommandMetricTile(copy.latency, averageLatency?.let { "${it.roundToInt()} ms" } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f))
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                        CommandMetricTile(copy.online, "$healthyCount / ${views.size}", copy.healthy, CommandHealthTone.HEALTHY, Modifier.weight(1f))
                                        CommandMetricTile(copy.averageCpu, averageCpu?.let { Fmt.pct(it) } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                        CommandMetricTile(copy.averageMemory, averageMemory?.let { Fmt.pct(it) } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f), CommandColors.violet)
                                        CommandMetricTile(copy.latency, averageLatency?.let { "${it.roundToInt()} ms" } ?: "—", copy.telemetry, CommandHealthTone.INFO, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (wide) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.md), verticalAlignment = Alignment.Top) {
                            CommandIncidentPanel(views, copy, onOpenServer, onOpenIncidents, Modifier.weight(1f))
                            CommandFleetPanel(views, copy, onOpenServer, onOpenFleet, Modifier.weight(1f))
                        }
                    }
                } else {
                    item { CommandIncidentPanel(views, copy, onOpenServer, onOpenIncidents) }
                    item { CommandFleetPanel(views, copy, onOpenServer, onOpenFleet) }
                }
            }
            item { Spacer(Modifier.height(CommandSpacing.xl)) }
        }
    }
}

private fun List<Float>.averageOrNull(): Float? = takeIf { it.isNotEmpty() }?.average()?.toFloat()

private fun commandHealthScore(views: List<CommandServerView>): Int? {
    val known = views.filter { it.state?.metrics != null || it.state?.error != null }
    if (known.isEmpty()) return null
    return known.map {
        when (it.tone) {
            CommandHealthTone.HEALTHY -> 100
            CommandHealthTone.ATTENTION -> 65
            CommandHealthTone.OFFLINE -> 0
            CommandHealthTone.UNKNOWN -> 0
            CommandHealthTone.INFO -> 100
        }
    }.average().roundToInt()
}

@Composable
private fun CommandIncidentPanel(
    views: List<CommandServerView>,
    copy: CommandCopy,
    onOpenServer: (ServerConfig) -> Unit,
    onOpenIncidents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val issues = views.filter { it.tone == CommandHealthTone.ATTENTION || it.tone == CommandHealthTone.OFFLINE }
    CommandSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(copy.activeAttention, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("${issues.size} · ${copy.incidents}", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                }
                CommandTextButton(copy.incidents, onOpenIncidents)
            }
            CommandRule()
            if (issues.isEmpty()) {
                CommandStateBlock(copy.noAttention, copy.noAttentionBody, CommandHealthTone.HEALTHY, modifier = Modifier.padding(CommandSpacing.sm))
            } else {
                issues.take(4).forEachIndexed { index, view ->
                    if (index > 0) CommandRule()
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenServer(view.server) }.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(observeToneColor(view.tone)))
                        Spacer(Modifier.width(CommandSpacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(view.status, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(view.server.name, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(view.state?.metrics?.let { metrics -> "${Fmt.pct(metrics.cpuUsage)} · ${Fmt.pct(metrics.memPct)}" } ?: copy.offline, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandFleetPanel(
    views: List<CommandServerView>,
    copy: CommandCopy,
    onOpenServer: (ServerConfig) -> Unit,
    onOpenFleet: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommandSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(copy.fleet, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("${views.size} ${copy.nodes}", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                }
                CommandTextButton(copy.servers, onOpenFleet)
            }
            CommandRule()
            Column(Modifier.padding(CommandSpacing.sm)) {
                views.take(3).forEach { view ->
                    CommandServerBentoCard(view, copy, { onOpenServer(view.server) })
                    if (view != views.take(3).last()) Spacer(Modifier.height(CommandSpacing.xs))
                }
            }
        }
    }
}

@Composable
private fun CommandServerBentoCard(view: CommandServerView, copy: CommandCopy, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = view.state?.metrics
    Column(
        modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .background(CommandColors.canvas.copy(alpha = 0.72f))
            .border(1.dp, CommandColors.border, androidx.compose.foundation.shape.RoundedCornerShape(11.dp))
            .padding(CommandSpacing.sm)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(view.server.name, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(view.server.host, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CommandTelemetryPill(view.status, view.tone)
        }
        Spacer(Modifier.height(CommandSpacing.sm))
        CommandTelemetryBar(copy.cpu, metrics?.cpuUsage, if (metrics != null && metrics.cpuUsage >= view.server.cpuAlert) CommandHealthTone.ATTENTION else CommandHealthTone.INFO)
        Spacer(Modifier.height(CommandSpacing.xs))
        CommandTelemetryBar(copy.memory, metrics?.memPct, if (metrics != null && metrics.memPct >= view.server.memAlert) CommandHealthTone.ATTENTION else CommandHealthTone.INFO, colorOverride = if (metrics != null && metrics.memPct >= view.server.memAlert) null else CommandColors.violet)
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
                detail = if (view.stale) "${view.server.host} · ${copy.dataIsStale}" else view.server.host
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
                } else if (state.updated > 0L && System.currentTimeMillis() - state.updated > 120_000L) {
                    add(LiveIncident(server, copy.dataIsStale, copy.lastSeen, CommandHealthTone.ATTENTION))
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
        ) {
            item {
                CommandSectionTitle(
                    title = copy.fleet,
                    supporting = "${servers.size} ${copy.nodes}",
                    actionLabel = copy.refresh,
                    onAction = onRefresh,
                    modifier = Modifier.padding(top = CommandSpacing.md)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
                        label = { Text(if (copy == CommandCopy.fa) copy.serversSearchPlaceholder else "Search name or host") }
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
                    CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                        CommandEmptyState(copy.noServersTitle, copy.noServersBody, copy.addServer, onManageServers)
                    }
                }
            } else if (wide) {
                items(visible.chunked(2), key = { pair -> pair.first().server.id }) { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
                        pair.forEach { view ->
                            CommandServerBentoCard(view, copy, { onOpenServer(view.server) }, Modifier.weight(1f))
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                items(visible, key = { it.server.id }) { view ->
                    CommandServerBentoCard(view, copy, { onOpenServer(view.server) })
                }
            }
            item { Spacer(Modifier.height(CommandSpacing.xl)) }
        }
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
    val metrics = state?.metrics
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CommandSpacing.md),
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
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(copy.serverDossier.uppercase(), color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                            Spacer(Modifier.height(CommandSpacing.xs))
                            Text(server.name, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(CommandSpacing.xxs))
                            Text("${server.host}:${server.port}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        CommandTelemetryPill(view.status, view.tone)
                    }
                    Spacer(Modifier.height(CommandSpacing.md))
                    CommandRule()
                    Spacer(Modifier.height(CommandSpacing.sm))
                    Text(view.updatedLabel(copy) ?: copy.waitingForData, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                }
            }
        }
        if (state?.error != null) {
            item {
                CommandStateBlock(copy.offline, state.error ?: copy.offline, CommandHealthTone.OFFLINE, copy.retry, onRefresh)
            }
        }
        if (metrics == null && state?.error == null) {
            item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        }
        if (metrics != null) {
            item {
                CommandSectionTitle(copy.telemetry, copy.lastSeen)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandMetricTile(copy.cpu, Fmt.pct(metrics.cpuUsage), "threshold ${server.cpuAlert}%", if (metrics.cpuUsage >= server.cpuAlert) CommandHealthTone.ATTENTION else CommandHealthTone.INFO, Modifier.weight(1f))
                        CommandMetricTile(copy.memory, Fmt.pct(metrics.memPct), "threshold ${server.memAlert}%", if (metrics.memPct >= server.memAlert) CommandHealthTone.ATTENTION else CommandHealthTone.INFO, Modifier.weight(1f), if (metrics.memPct >= server.memAlert) null else CommandColors.violet)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandMetricTile(copy.load, metrics.load1.toString(), "${metrics.cores} cores", CommandHealthTone.INFO, Modifier.weight(1f))
                        CommandMetricTile(copy.latency, "${state?.latencyMs?.toInt() ?: 0} ms", copy.lastSeen, CommandHealthTone.INFO, Modifier.weight(1f))
                    }
                }
            }
            item {
                CommandTelemetryOrbit(listOf(view.tone), Modifier.fillMaxWidth(), "${server.name} · ${copy.telemetry}")
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
