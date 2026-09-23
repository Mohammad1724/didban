package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHECK_HOST_MAX_NODES = 20
private const val CHECK_HOST_MAX_POLLS = 30
private const val CHECK_HOST_POLL_DELAY_MS = 1_000L

/**
 * Removes the optional scheme/path users commonly paste and returns the host
 * in the form expected by check-host.net. TCP and UDP accept either a host
 * with an embedded port or a separate port field.
 */
internal fun normalizeCheckHostTarget(input: String, type: String, portText: String): String? {
    var value = input.trim()
    if (value.isEmpty()) return null

    value = value.replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .trim()
    if (value.isEmpty() || value.any { it.isWhitespace() } || value.contains('@')) return null

    val needsPort = type == "tcp" || type == "udp"
    val host = when {
        value.startsWith('[') -> value.substringBefore(']').removePrefix("[")
        needsPort && value.count { it == ':' } == 1 && value.substringAfterLast(':').toIntOrNull() != null ->
            value.substringBeforeLast(':')
        else -> value
    }
    if (host.isEmpty() || !host.any { it.isLetterOrDigit() }) return null
    if (!host.matches(Regex("[A-Za-z0-9._:-]+"))) return null

    if (!needsPort) return value.trimEnd('.')

    // TCP and UDP checks need a port. Preserve an explicitly entered
    // host:port; otherwise append the value from the dedicated field. IPv6 is bracketed
    // so that the API receives an unambiguous host:port pair.
    val embeddedPort = when {
        value.startsWith('[') && value.contains("]:") -> value.substringAfterLast(':').toIntOrNull()
        value.count { it == ':' } == 1 -> value.substringAfterLast(':').toIntOrNull()
        else -> null
    }
    if (embeddedPort != null && embeddedPort in 1..65535) return value

    val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

private fun checkHostTone(node: CheckHostNode): CommandHealthTone = when (node.state) {
    1 -> CommandHealthTone.HEALTHY
    2 -> CommandHealthTone.OFFLINE
    else -> CommandHealthTone.UNKNOWN
}

/**
 * Check-Host style global reachability. It deliberately has no ServerConfig:
 * the checks run on check-host.net's public vantage points and therefore work
 * for a plain domain or IP even when the user's Didban fleet is empty.
 */
@Composable
fun CommandCheckHostScreen(
    copy: CommandCopy,
    onBack: () -> Unit,
    startCheck: suspend (String, String, Int) -> Pair<String, List<CheckHostNode>> = { target, type, maxNodes ->
        CheckHostService.startCheck(target, type, maxNodes)
    },
    pollResults: suspend (String, String, List<CheckHostNode>) -> Boolean = { requestId, type, nodes ->
        CheckHostService.pollResults(requestId, type, nodes)
    }
) {
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("ping") }
    var port by rememberSaveable { mutableStateOf("443") }
    var nodes by remember { mutableStateOf<List<CheckHostNode>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose { job?.cancel() }
    }

    fun runCheck() {
        if (running) return
        val normalized = normalizeCheckHostTarget(target, type, port)
        if (normalized == null) {
            error = if (target.isBlank()) copy.netNoHost else copy.checkHostInvalidTarget
            return
        }

        error = null
        nodes = emptyList()
        running = true
        val requestedType = type
        job = scope.launch {
            try {
                val (requestId, startedNodes) = startCheck(normalized, requestedType, CHECK_HOST_MAX_NODES)
                if (startedNodes.isEmpty()) throw IllegalStateException(copy.checkHostNoNodes)
                nodes = startedNodes.toList()

                repeat(CHECK_HOST_MAX_POLLS) {
                    val done = pollResults(requestId, requestedType, startedNodes)
                    // CheckHostNode keeps mutable result fields because the
                    // API parser updates one node at a time. Publish a fresh
                    // list so Compose observes every polling update.
                    nodes = startedNodes.map { it.copy() }
                    if (done) return@launch
                    delay(CHECK_HOST_POLL_DELAY_MS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.operationFailed
            } finally {
                running = false
                job = null
            }
        }
    }

    fun stopCheck() {
        job?.cancel()
        job = null
        running = false
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md),
        contentPadding = PaddingValues(CommandSpacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.netQReachable, copy.netQReachableTools, modifier = Modifier.weight(1f))
            }
        }

        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.checkHostInput, style = MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.take(255) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        singleLine = true,
                        label = { Text(copy.netHostDomain) },
                        placeholder = { Text("example.com / 1.1.1.1") }
                    )
                    CommandChipRow(
                        options = listOf("PING" to "ping", "HTTP" to "http", "TCP" to "tcp", "UDP" to "udp", "DNS" to "dns"),
                        selected = type,
                        onSelect = { type = it },
                        enabled = !running
                    )
                    if (type == "tcp" || type == "udp") {
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.width(128.dp),
                            enabled = !running,
                            singleLine = true,
                            label = { Text(copy.port) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        CommandPrimaryButton(
                            if (running) copy.waitingForData else copy.test,
                            ::runCheck,
                            enabled = !running,
                            icon = Icons.Rounded.PlayArrow
                        )
                        if (running) {
                            CommandSecondaryButton(copy.stop, ::stopCheck, icon = Icons.Rounded.Stop)
                        }
                    }
                }
            }
        }

        if (error != null) {
            item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        }

        if (nodes.isNotEmpty()) {
            item {
                CommandSectionTitle(
                    copy.nodes,
                    "${nodes.count { it.state != 0 }}/${nodes.size}"
                )
            }
            items(nodes, key = { it.nodeKey }) { node ->
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(node.flag, modifier = Modifier.padding(end = CommandSpacing.xs))
                            CommandStatusMark(
                                when (node.state) {
                                    1 -> copy.online
                                    2 -> copy.offline
                                    else -> copy.waitingForData
                                },
                                checkHostTone(node),
                                modifier = Modifier.weight(1f),
                                detail = node.location.ifBlank { node.nodeKey }
                            )
                        }
                        Text(
                            node.resultText,
                            color = CommandColors.textPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            node.nodeKey,
                            color = CommandColors.textTertiary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}
