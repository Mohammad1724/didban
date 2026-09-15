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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class CommandProbePoint(
    val name: String,
    val mode: String,
    val host: String,
    val port: Int,
    val state: String,
    val observed: Boolean,
    val latencyMs: Long,
    val detail: String,
    val source: String
)

/**
 * Maps an agent's /api/probe payload onto the shell's own row model.
 *
 * Parsing is delegated to [ProbeSnapshot.parse] — the fault-tolerant parser
 * this screen used to duplicate inline, which also threw away the agent's
 * per-target uptime history.
 */
/**
 * One uptime monitor as seen from every vantage point at once.
 *
 * This is the point of multi-point probing: a target that is down from the
 * phone but up from three servers is the phone's network, not the target.
 */
private data class CommandMatrixRow(
    val targetId: Long,
    val name: String,
    val kind: String,
    val phoneStatus: Int,                   // -1 pending, 1 up, 0 down
    val servers: List<Pair<String, String>> // server name -> "" | "up" | "down"
) {
    /** True when the phone says down but at least one server says up. */
    val divergent: Boolean
        get() = phoneStatus == 0 && servers.any { it.second == "up" }
}

private fun matrixTone(state: String): CommandHealthTone = when (state) {
    "up" -> CommandHealthTone.HEALTHY
    "down" -> CommandHealthTone.OFFLINE
    else -> CommandHealthTone.UNKNOWN
}

private fun parseProbePoints(payload: JSONObject): List<CommandProbePoint> {
    val snapshot = ProbeSnapshot.parse(payload)
    return snapshot.points.map { point ->
        CommandProbePoint(
            name = point.name,
            mode = point.mode,
            host = point.host,
            port = point.port,
            state = point.state,
            observed = point.observed,
            latencyMs = point.lastLatencyMs,
            detail = point.lastDetail,
            source = snapshot.hostname.ifBlank { "Agent" }
        )
    }
}

@Composable
fun CommandRadarScreen(
    copy: CommandCopy,
    server: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var points by remember(server?.id) { mutableStateOf<List<CommandProbePoint>>(emptyList()) }
    var loading by remember(server?.id) { mutableStateOf(false) }
    var error by remember(server?.id) { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var targetName by remember { mutableStateOf("") }
    var targetHost by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("443") }
    var targetMode by remember { mutableStateOf("tcp") }

    fun load() {
        val target = server ?: return
        loading = true
        error = null
        scope.launch {
            runCatching { ApiClient().probeStatus(target) }
                .onSuccess { payload -> points = parseProbePoints(payload) }
                .onFailure { error = it.message ?: copy.operationFailed }
            loading = false
        }
    }

    fun syncTarget() {
        val target = server ?: return
        val name = targetName.trim()
        val host = targetHost.trim()
        val port = targetPort.toIntOrNull()
        if (name.isEmpty() || host.isEmpty() || port == null) {
            error = "${copy.target}: ${copy.host} ${copy.and} ${copy.port}"
            return
        }
        scope.launch {
            loading = true
            runCatching {
                val targets = JSONArray()
                points.forEach { point ->
                    targets.put(JSONObject().apply {
                        put("name", point.name)
                        put("mode", point.mode)
                        put("host", point.host)
                        put("port", point.port)
                    })
                }
                targets.put(JSONObject().apply {
                    put("name", name)
                    put("mode", targetMode.lowercase())
                    put("host", host)
                    put("port", port)
                })
                ApiClient().probeTargetsSync(target, JSONObject().put("targets", targets))
            }.onSuccess {
                message = copy.operationDone
                targetName = ""
                targetHost = ""
                load()
            }.onFailure { error = it.message ?: copy.operationFailed }
            loading = false
        }
    }

    fun runProbe(name: String) {
        val target = server ?: return
        scope.launch {
            loading = true
            runCatching { ApiClient().probeNow(target, name) }
                .onSuccess { message = copy.operationDone; load() }
                .onFailure { error = it.message ?: copy.operationFailed }
            loading = false
        }
    }

    // ── Coverage matrix (phase 4-B, restored) ─────────────────────────────
    // Every uptime monitor is registered on every agent, then read back, so
    // one row shows the same target from the phone and from each server.
    val context = LocalContext.current
    var matrixRows by remember { mutableStateOf<List<CommandMatrixRow>>(emptyList()) }
    var matrixBusy by remember { mutableStateOf(false) }
    var matrixNotice by remember { mutableStateOf<String?>(null) }

    fun syncAllMonitors() {
        if (matrixBusy) return
        UptimeEngine.ensureLoaded(context)
        val chosen = UptimeEngine.liveTargets.value.filter { !it.isPaused }.take(50)
        if (chosen.isEmpty()) {
            matrixNotice = copy.radarNoTargetsBody
            return
        }
        val fleet = Prefs.loadServers(context)
        if (fleet.isEmpty()) {
            matrixNotice = copy.noServersBody
            return
        }
        // The same de-duplicated names are used to write and to read back, so
        // two monitors sharing a display name can no longer make the agent
        // reject the whole batch (which used to blank every server column).
        val names = ProbeSpecs.uniqueNames(chosen)
        val payload = ProbeSpecs.targetsPayload(chosen)
        matrixBusy = true
        matrixNotice = null
        scope.launch {
            val probed = fleet.map { node ->
                async {
                    val client = ApiClient()
                    runCatching { client.probeTargetsSync(node, payload) }
                    val snapshot = runCatching { ProbeSnapshot.parse(client.probeStatus(node)) }.getOrNull()
                    node.name to snapshot
                }
            }.awaitAll()
            matrixRows = chosen.map { target ->
                val key = names[target.id].orEmpty()
                CommandMatrixRow(
                    targetId = target.id,
                    name = target.name.ifBlank { target.target },
                    kind = target.type,
                    phoneStatus = target.lastStatus,
                    servers = probed.map { (nodeName, snapshot) ->
                        nodeName to (snapshot?.point(key)?.state ?: "")
                    }
                )
            }
            matrixBusy = false
            matrixNotice = copy.radarSynced.replace("%d", chosen.size.toString())
        }
    }

    LaunchedEffect(server?.id) { load() }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.radar, server?.name ?: copy.noServerSelected, copy.refresh, ::load, Modifier.weight(1f))
            }
        }
        if (server == null) {
            item { CommandEmptyState(copy.selectServer, copy.noServerSelected, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(copy.target, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(targetName, { targetName = it }, label = { Text(copy.target) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(targetMode, { targetMode = it }, label = { Text(copy.mode) }, modifier = Modifier.width(110.dp), singleLine = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(targetHost, { targetHost = it }, label = { Text(copy.host) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(targetPort, { targetPort = it.filter(Char::isDigit).take(5) }, label = { Text(copy.port) }, modifier = Modifier.width(110.dp), singleLine = true)
                        }
                        CommandPrimaryButton(copy.addTarget, ::syncTarget, enabled = !loading)
                    }
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE, copy.retry, ::load) }
            if (message != null) item { Text(message ?: "", color = CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
            item { CommandSectionTitle(copy.sources, "${points.size}") }
            if (loading && points.isEmpty()) {
                item { CommandStateBlock(copy.waitingForData, copy.waitingForData, CommandHealthTone.UNKNOWN) }
            } else if (points.isEmpty()) {
                item { CommandEmptyState(copy.sources, copy.noAttentionBody) }
            } else {
                items(points, key = { "${it.source}:${it.name}" }) { point ->
                    val tone = when {
                        !point.observed -> CommandHealthTone.UNKNOWN
                        point.state == "up" -> CommandHealthTone.HEALTHY
                        point.state == "down" -> CommandHealthTone.OFFLINE
                        else -> CommandHealthTone.UNKNOWN
                    }
                    CommandSurface(Modifier.fillMaxWidth().clickable { runProbe(point.name) }) {
                        Column(Modifier.padding(CommandSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CommandStatusMark(point.state.ifBlank { copy.waitingForData }, tone, Modifier.weight(1f), point.source)
                                Text(copy.runProbe, color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(CommandSpacing.xs))
                            Text("${point.mode} ${point.host}:${point.port}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                            if (point.observed) Text("${point.latencyMs} ms · ${point.detail}", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                        }
                    }
                }
            }
        }

        // ── Coverage matrix ────────────────────────────────────────────────
        item {
            CommandSectionTitle(
                copy.coverage,
                if (matrixRows.isEmpty()) null else "${matrixRows.size}",
                if (matrixBusy) null else copy.radarSyncAll,
                if (matrixBusy) null else ({ syncAllMonitors() }),
                Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                copy.radarSyncAllBody,
                color = CommandColors.textTertiary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
        if (matrixNotice != null) {
            item { CommandStateBlock(copy.operationDone, matrixNotice ?: "", CommandHealthTone.INFO) }
        }
        if (matrixBusy) {
            item { CommandStateBlock(copy.sync, copy.waitingForData, CommandHealthTone.UNKNOWN) }
        }
        items(matrixRows, key = { it.targetId }) { row ->
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommandStatusMark(
                            row.name,
                            matrixTone(if (row.phoneStatus == 1) "up" else if (row.phoneStatus == 0) "down" else ""),
                            Modifier.weight(1f),
                            "${copy.vantagePhone} · ${row.kind}"
                        )
                        if (row.divergent) {
                            CommandTelemetryPill(copy.attention, CommandHealthTone.ATTENTION)
                        }
                    }
                    CommandRule()
                    row.servers.forEach { (nodeName, state) ->
                        CommandMetricLine(
                            nodeName,
                            state.ifBlank { copy.unknownState },
                            matrixTone(state)
                        )
                    }
                    if (row.divergent) {
                        Text(
                            copy.noAttentionBody,
                            color = CommandColors.warning,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandUptimeScreen(
    copy: CommandCopy,
    onOpenEditor: () -> Unit
) {
    val context = LocalContext.current
    val targets by UptimeEngine.liveTargets.collectAsState()
    UptimeEngine.ensureLoaded(context)
    val scope = rememberCoroutineScope()
    var testingId by remember { mutableStateOf<Long?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            CommandSectionTitle(copy.uptime, copy.incidentsFromLiveState, copy.refresh, { UptimeEngine.ensureLoaded(context) }, Modifier.padding(top = CommandSpacing.sm))
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Text("Monitor configuration", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Spacer(Modifier.height(CommandSpacing.xs))
                    Text("${targets.count { it.lastStatus == 1 }} ${copy.healthy} · ${targets.count { it.lastStatus == 0 }} ${copy.offline}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(CommandSpacing.sm))
                    CommandSecondaryButton(copy.addMonitor, onOpenEditor)
                }
            }
        }
        if (targets.isEmpty()) {
            item { CommandEmptyState(copy.uptime, copy.noServersBody, copy.addMonitor, onOpenEditor) }
        } else {
            items(targets, key = { it.id }) { target ->
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CommandStatusMark(
                                when (target.lastStatus) { 1 -> copy.healthy; 0 -> copy.offline; else -> copy.waitingForData },
                                when (target.lastStatus) { 1 -> CommandHealthTone.HEALTHY; 0 -> CommandHealthTone.OFFLINE; else -> CommandHealthTone.UNKNOWN },
                                Modifier.weight(1f),
                                target.name
                            )
                            CommandTextButton(copy.test, {
                                testingId = target.id
                                scope.launch {
                                    runCatching { UptimeEngine.checkNow(context, target) }
                                        .onSuccess { result = copy.operationDone }
                                        .onFailure { result = "${copy.operationFailed}: ${it.message}" }
                                    testingId = null
                                }
                            }, Icons.Rounded.PlayArrow)
                        }
                        Spacer(Modifier.height(CommandSpacing.xs))
                        Text("${target.type} · ${target.target}:${target.port}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                        Text("${target.uptimePct.toInt()}% · ${target.lastLatencyMs} ms · ${target.intervalSec}s", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                        if (testingId == target.id) CircularLoadingLine()
                    }
                }
            }
        }
        if (result != null) item { Text(result ?: "", color = CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
private fun CircularLoadingLine() {
    Text("…", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
}
