package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class DeveloperTool {
    BASE64,
    JSON,
    HASH,
    SUBNET,
    JWT,
    GENERATOR
}

private fun DeveloperTool.label(copy: CommandCopy): String = when (this) {
    DeveloperTool.BASE64 -> copy.wtToolBase64
    DeveloperTool.JSON -> copy.wtToolJson
    DeveloperTool.HASH -> copy.wtToolHash
    DeveloperTool.SUBNET -> copy.wtToolSubnet
    DeveloperTool.JWT -> copy.wtToolJwt
    DeveloperTool.GENERATOR -> copy.wtToolGenerator
}

@Composable
fun CommandSftpScreen(copy: CommandCopy, initialServer: ServerConfig?, onSelectServer: () -> Unit, onBack: () -> Unit) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operationGate = remember { CommandOperationGate() }
    val servers = remember { Prefs.loadServers(context) }
    var selectedId by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.id ?: servers.firstOrNull()?.id)
    }
    val server = selectedId?.let { id -> servers.firstOrNull { it.id == id } }
    var user by remember { mutableStateOf("root") }
    var port by remember { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("/etc") }
    var files by remember { mutableStateOf<List<SftpFileItem>>(emptyList()) }
    var activeFile by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var prompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
    val promptChannel = remember { Channel<Boolean>(Channel.RENDEZVOUS) }
    val promptGate = remember { Mutex() }
    val hostKeyPolicy = remember {
        ConfirmingHostKeyPolicy(HostKeyTrustStore) { nextPrompt ->
            promptGate.withLock {
                withContext(Dispatchers.Main) { prompt = nextPrompt }
                promptChannel.receive()
            }
        }
    }

    fun cancel() {
        operationGate.cancel()
        job?.cancel()
        job = null
        prompt = null
        promptChannel.trySend(false)
        loading = false
    }

    fun refresh() {
        val target = server ?: return
        if (password.isBlank()) {
            error = copy.securityNeedPassword
            return
        }
        if (loading) return
        val requestedPath = path
        val requestedUser = user.ifBlank { "root" }
        val requestedPort = port.toIntOrNull() ?: 22
        val requestedPassword = password
        val operationId = operationGate.begin()
        loading = true
        error = null
        retryAction = null
        job = scope.launch {
            try {
                val result = SftpEngine.listFiles(target.host, requestedPort, requestedUser, requestedPassword, requestedPath, true, SftpSortMode.NAME_ASC, hostKeyPolicy)
                if (operationGate.owns(operationId)) {
                    files = result
                    status = copy.hostKeysStatus.replace("%d", result.size.toString())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: SftpFailureException) {
                if (operationGate.owns(operationId)) {
                    error = e.failure.localized(copy)
                    retryAction = { refresh() }
                }
            } catch (_: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.wtSftpBrowseFailed
                    retryAction = { refresh() }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    loading = false
                    job = null
                }
            }
        }
    }
    fun openItem(item: SftpFileItem) {
        val target = server ?: return
        if (item.isDirectory) {
            path = item.path
            refresh()
        } else {
            if (loading) return
            val requestedUser = user.ifBlank { "root" }
            val requestedPort = port.toIntOrNull() ?: 22
            val requestedPassword = password
            val operationId = operationGate.begin()
            loading = true
            error = null
            retryAction = null
            job = scope.launch {
                try {
                    val result = SftpEngine.readFile(target.host, requestedPort, requestedUser, requestedPassword, item.path, hostKeyPolicy = hostKeyPolicy)
                    if (operationGate.owns(operationId)) {
                        content = result
                        activeFile = item.path
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: SftpFailureException) {
                    if (operationGate.owns(operationId)) {
                        error = e.failure.localized(copy)
                        retryAction = { openItem(item) }
                    }
                } catch (_: Exception) {
                    if (operationGate.owns(operationId)) {
                        error = copy.wtFileReadFailed
                        retryAction = { openItem(item) }
                    }
                } finally {
                    if (operationGate.owns(operationId)) {
                        loading = false
                        job = null
                    }
                }
            }
        }
    }
    fun save() {
        val target = server ?: return
        val file = activeFile ?: return
        if (loading) return
        val requestedUser = user.ifBlank { "root" }
        val requestedPort = port.toIntOrNull() ?: 22
        val requestedPassword = password
        val contentToSave = content
        val operationId = operationGate.begin()
        loading = true
        error = null
        retryAction = null
        job = scope.launch {
            try {
                SftpEngine.saveFile(target.host, requestedPort, requestedUser, requestedPassword, file, contentToSave, hostKeyPolicy)
                if (operationGate.owns(operationId)) status = copy.wtFileSaved.replace("%1", file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: SftpFailureException) {
                if (operationGate.owns(operationId)) {
                    error = e.failure.localized(copy)
                    retryAction = { save() }
                }
            } catch (_: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.wtFileSaveFailed
                    retryAction = { save() }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    loading = false
                    job = null
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.sftp, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
            }
        }
        if (servers.isEmpty()) {
            item { CommandEmptyState(copy.selectServer, copy.noServersBody, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            servers.forEach { item -> CommandSecondaryButton(item.name, { selectedId = item.id }, enabled = selectedId != item.id && !loading) }
                        }
                        CommandResponsiveRow {
                            OutlinedTextField(user, { user = it }, item(weight = 1f), enabled = !loading, singleLine = true, label = { Text(copy.uiUser) })
                            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, item(width = CommandMetrics.formAuxFieldWidth), enabled = !loading, singleLine = true, label = { Text(copy.port) })
                        }
                        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), enabled = !loading, singleLine = true, label = { Text(copy.uiSshPassword) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        CommandResponsiveRow {
                            OutlinedTextField(path, { path = it; retryAction = null }, item(weight = 1f), enabled = !loading, singleLine = true, label = { Text(copy.wtRemotePath) })
                            CommandPrimaryButton(if (loading) copy.waitingForData else copy.refresh, ::refresh, modifier = item(), enabled = !loading && server != null, icon = Icons.Rounded.Refresh)
                        }
                        Text(copy.sftpBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loading) item {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    CommandLoadingState(copy.waitingForData)
                    CommandSecondaryButton(copy.cancel, ::cancel, modifier = Modifier.fillMaxWidth())
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE, copy.retry, retryAction) }
            if (status != null) item { CommandStatusMark(status ?: "", CommandHealthTone.INFO) }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        Text(copy.wtRemoteEntries, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        if (files.isEmpty()) Text(copy.waitingForData, color = CommandColors.textSecondary)
                        files.forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = CommandMetrics.compactRowMinHeight)
                                    .testTag("sftp-entry-${item.path}")
                                    .clickable(role = Role.Button) { openItem(item) }
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = listOfNotNull(
                                            if (item.isDirectory) copy.wtDirectory else copy.wtFile,
                                            item.name,
                                            item.formattedSize(copy)
                                        ).joinToString(" · ")
                                    }
                                    .padding(vertical = CommandSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (item.isDirectory) copy.wtDirectory else copy.wtFile, color = if (item.isDirectory) CommandColors.info else CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.width(CommandMetrics.sftpKindWidth))
                                Text(item.name, Modifier.weight(1f), color = CommandColors.textPrimary)
                                Text(item.formattedSize(copy), color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (activeFile != null) item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandResponsiveRow {
                            Text(activeFile ?: "", item(weight = 1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            CommandTextButton(copy.copyAction, { if (content.isNotEmpty()) SensitiveClipboard.copy(context, copy.wtRemoteFileClipboardLabel, content) }, Icons.Rounded.ContentCopy, modifier = item())
                        }
                        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().height(CommandMetrics.textEditorMinHeight), enabled = !loading, textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), label = { Text(copy.wtTextEditorMax) })
                        CommandPrimaryButton(copy.wtSaveRemoteFile, ::save, enabled = !loading, icon = Icons.Rounded.Security)
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
            promptChannel.trySend(approved)
        })
    }
}
