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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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

@Composable
fun CommandSshScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val servers = remember { Prefs.loadServers(context) }
    var selectedId by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.id ?: servers.firstOrNull()?.id)
    }
    val server = selectedId?.let { id -> servers.firstOrNull { it.id == id } }
    var user by remember { mutableStateOf("root") }
    var port by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("uname -a") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
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

    fun execute() {
        val target = server ?: return
        if (password.isBlank() || command.isBlank()) return
        val sshPort = port.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        running = true
        error = null
        output = "[${target.name}] $command\n"
        scope.launch {
            val result = SshEngine.execute(target.host, sshPort, user.ifBlank { "root" }, password, command, 25, policy)
            running = false
            output += buildString {
                if (result.stdout.isNotBlank()) append(result.stdout).append('\n')
                if (result.stderr.isNotBlank()) append(result.stderr).append('\n')
                append("\nExit ${result.exitCode} · ${result.durationMs} ms")
            }
            if (!result.isSuccess) error = result.errorMessage ?: result.stderr
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.ssh, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
            }
        }
        if (servers.isEmpty()) {
            item { CommandEmptyState(copy.selectServer, copy.noServersBody, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(copy.currentServer, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                            servers.forEach { item ->
                                CommandSecondaryButton(item.name, { selectedId = item.id }, enabled = selectedId != item.id, modifier = Modifier.weight(1f))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(user, { user = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("User") })
                            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, modifier = Modifier.width(100.dp), singleLine = true, label = { Text("Port") })
                        }
                        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("SSH Password") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    }
                }
            }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text("Input", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        OutlinedTextField(command, { command = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Command") })
                        CommandPrimaryButton(if (running) copy.waitingForData else copy.run, ::execute, enabled = !running && server != null, icon = Icons.Rounded.PlayArrow)
                    }
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Evidence", Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                            CommandTextButton(copy.copyAction, { clipboard.setText(AnnotatedString(output)) }, Icons.Rounded.ContentCopy)
                        }
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(output.ifBlank { copy.waitingForData }, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 30, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (prompt != null) {
        CommandHostKeyDialog(copy, prompt!!, onDecision = { approved ->
            prompt = null
            scope.launch { promptChannel.send(approved) }
        })
    }
}

@Composable
fun CommandBatchScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val servers = remember { Prefs.loadServers(context) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var port by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("apt update -y") }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<BatchServerResult>>(emptyList()) }
    var prompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
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

    fun runBatch() {
        if (selectedIds.isEmpty() || password.isBlank() || command.isBlank()) return
        val targets = servers.filter { it.id in selectedIds }.map { Triple(it, port.toIntOrNull() ?: 22, password) }
        running = true
        scope.launch {
            results = SshEngine.executeBatch(targets, command, 35, policy)
            running = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.batch, "${selectedIds.size} / ${servers.size}", modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("Fleet Scope", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    servers.forEach { server ->
                        Row(Modifier.fillMaxWidth().clickable {
                            if (server.id in selectedIds) selectedIds.remove(server.id) else selectedIds.add(server.id)
                        }.padding(vertical = CommandSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            CommandStatusMark(if (server.id in selectedIds) copy.selected else copy.notSelected, if (server.id in selectedIds) CommandHealthTone.INFO else CommandHealthTone.UNKNOWN, Modifier.weight(1f), server.name)
                            Text(server.host, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                        }
                    }
                    OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.batchSharedPassword) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    OutlinedTextField(command, { command = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("Command") })
                    CommandPrimaryButton(if (running) copy.waitingForData else copy.run, ::runBatch, enabled = !running, icon = Icons.Rounded.Bolt)
                }
            }
        }
        if (results.isNotEmpty()) {
            item { CommandSectionTitle("Result Matrix", copy.batchSummary.replace("%1", results.count { it.result.isSuccess }.toString()).replace("%2", results.count { !it.result.isSuccess }.toString())) }
            items(results, key = { it.serverId }) { item ->
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        CommandStatusMark(if (item.result.isSuccess) copy.succeeded else copy.failed, if (item.result.isSuccess) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE, detail = "${item.serverName} · ${item.host}")
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(item.result.stdout.ifBlank { item.result.stderr }.ifBlank { copy.noOutput }, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
    if (prompt != null) {
        CommandHostKeyDialog(copy, prompt!!, onDecision = { approved ->
            prompt = null
            scope.launch { promptChannel.send(approved) }
        })
    }
}

@Composable
fun CommandHostKeyDialog(copy: CommandCopy, prompt: HostKeyPrompt, onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text(if (prompt.keyChanged) copy.hostKeyChanged else copy.hostKeyNew, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                Text("${prompt.host}:${prompt.port}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(if (prompt.keyChanged) copy.hostKeyChangedBody else copy.hostKeyFirstBody, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                Text(prompt.fingerprint, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = if (prompt.keyChanged) CommandColors.danger else CommandColors.textPrimary)
            }
        },
        confirmButton = { TextButton(onClick = { onDecision(true) }) { Text(if (prompt.keyChanged) copy.resetTrust else copy.trustAndConnect) } },
        dismissButton = { TextButton(onClick = { onDecision(false) }) { Text(copy.cancel) } }
    )
}
