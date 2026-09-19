package org.didban.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Activity-retained RAM only. Never put tokens into rememberSaveable or SavedStateHandle. */
internal class ServerEditorViewModel : ViewModel() {
    var draft by mutableStateOf<ServerConnectionDraft?>(null)
    private var baseline: ServerConnectionDraft? = null
    var original: ServerConfig? = null
        private set
    var blocked = false
        private set
    var missing = false
        private set
    private var session: String? = null
    val dirty: Boolean get() = draft != baseline

    fun begin(id: Long?, loaded: Prefs.ServerLoadResult) {
        val key = id?.toString() ?: "new"
        if (session == key && draft != null) return
        session = key
        original = loaded.servers.firstOrNull { it.id == id }?.copy()
        blocked = loaded.error != null
        missing = id != null && original == null && !blocked
        baseline = original?.let(ServerConnectionDraft::from)
            ?: ServerConnectionDraft(id ?: (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE))
        draft = baseline
    }

    /** Actions and field callbacks must read the live model, not a captured composition snapshot. */
    fun edit(transform: (ServerConnectionDraft) -> ServerConnectionDraft) {
        draft = draft?.let(transform)
    }

    fun serverForAction(): ServerConfig? = draft?.server()

    fun clear() { draft = null; baseline = null; original = null; session = null; blocked = false; missing = false }
}

@Composable
internal fun CommandServerEditor(
    copy: CommandCopy,
    serverId: Long?,
    onSaved: (ServerConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity() as? androidx.activity.ComponentActivity ?: return
    val model = remember(activity) { ViewModelProvider(activity)[ServerEditorViewModel::class.java] }
    remember(model, serverId) { model.begin(serverId, Prefs.loadServersResult(context)); true }
    val draft = model.draft ?: return
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val language = Prefs.getLanguage(context)
    val protectScreenshots = rememberScreenshotProtection()

    fun close() { model.clear(); onClose() }
    fun requestClose() { if (model.dirty) discard = true else close() }
    fun change(transform: (ServerConnectionDraft) -> ServerConnectionDraft) {
        if (busy) return
        model.edit(transform)
        error = null
        message = null
    }
    fun validation(server: ServerConfig): String? = when (connectionValidation(server)) {
        ConnectionValidation.NAME -> copy.srvNameRequired
        ConnectionValidation.HOST -> copy.srvHostInvalid
        ConnectionValidation.PORT -> copy.fleetPortInvalid
        ConnectionValidation.READ_TOKEN -> securityMessage(language, SecurityMessage.WEAK_AGENT_TOKEN)
        ConnectionValidation.ADMIN_TOKEN -> securityMessage(language, SecurityMessage.WEAK_ADMIN_TOKEN)
        ConnectionValidation.SAME_TOKENS -> securityMessage(language, SecurityMessage.TOKENS_MUST_DIFFER)
        ConnectionValidation.TLS -> securityMessage(language, SecurityMessage.HTTP_DISABLED)
        ConnectionValidation.FINGERPRINT -> copy.srvFingerprintInvalid
        ConnectionValidation.THRESHOLD -> copy.fleetThresholdInvalid
        null -> null
    }

    fun save() {
        if (busy || model.blocked || model.missing) return
        val server = model.serverForAction() ?: return
        validation(server)?.let { error = it; return }
        val loaded = Prefs.loadServersResult(context)
        if (loaded.error != null) {
            error = securityMessage(language, SecurityMessage.SERVER_WRITE_BLOCKED)
            return
        }
        val updated = try { ServerRecordEdits.save(loaded.servers, model.original, server) }
        catch (_: IllegalStateException) { error = copy.fleetRecordChanged; return }
        try {
            Prefs.saveServers(context, updated)
        } catch (_: Exception) {
            error = securityMessage(language, SecurityMessage.SERVER_SAVE_FAILED)
            return
        }
        model.original?.let { HttpClientPool.evictForServer(it) }
        Repo.remove(server.id)
        PollingCoordinator.requestNow(server.id)
        model.clear()
        onSaved(server)
    }

    fun test() {
        if (busy || model.blocked || model.missing) return
        val server = model.serverForAction() ?: return
        validation(server)?.let { error = it; return }
        busy = true; error = null; message = null
        scope.launch {
            try {
                withTimeout(15_000L) { ApiClient().metrics(server) }
                message = copy.fleetTestVerified
            } catch (_: TimeoutCancellationException) {
                error = copy.refreshTimedOut
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = SecretRedactor.redact(failure.message ?: copy.srvConnectFailed,
                    listOf(server.token, server.adminToken)).take(300)
            } finally { busy = false }
        }
    }

    fun importConnection() {
        if (busy) return
        try {
            val code = QuickConnectCodeParser.parse(model.draft?.quickConnect.orEmpty())
            change { current -> current.copy(name = code.name, host = code.host, port = code.port.toString(),
                token = code.readToken, adminToken = code.adminToken, fingerprint = code.fingerprint, quickConnect = "") }
            message = copy.srvQuickConnectImported
        } catch (_: Exception) { error = copy.srvQuickConnectInvalid }
    }

    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, securePolicy = screenshotDialogPolicy(protectScreenshots)
    )) {
        SecureWindowEffect()
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight(0.94f),
            color = CommandColors.canvas, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Column(Modifier.imePadding().padding(CommandSpacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (serverId == null) copy.addServer else copy.srvEditConnection,
                        Modifier.weight(1f), color = CommandColors.textPrimary,
                        style = MaterialTheme.typography.titleLarge)
                    CommandCloseButton(copy.close, ::requestClose)
                }
                if (model.blocked || model.missing) {
                    CommandStateBlock(copy.operationFailed,
                        if (model.blocked) securityMessage(language, SecurityMessage.SERVER_READ_FAILED) else copy.fleetMissing,
                        CommandHealthTone.OFFLINE)
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        item { Text(copy.fleetDraftPrivacy, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall) }
                        if (serverId == null) item {
                            OutlinedTextField(draft.quickConnect, { input -> change { current -> current.copy(quickConnect = input.take(2048)) } },
                                Modifier.fillMaxWidth(), enabled = !busy, label = { Text(copy.srvQuickConnectLabel) },
                                visualTransformation = PasswordVisualTransformation(), minLines = 2, maxLines = 3)
                            Spacer(Modifier.height(CommandSpacing.xs))
                            CommandPrimaryButton(
                                copy.srvQuickConnectImport, ::importConnection,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                icon = Icons.Rounded.Bolt,
                                enabled = !busy && draft.quickConnect.isNotBlank()
                            )
                        }
                        item { OutlinedTextField(draft.name, { input -> change { current -> current.copy(name = input) } }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.fleetName) }) }
                        item { OutlinedTextField(draft.host, { input -> change { current -> current.copy(host = input) } }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAgentHost) }) }
                        item { OutlinedTextField(draft.port, { input -> change { current -> current.copy(port = input.filter(Char::isDigit).take(5)) } }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.port) }) }
                        item { OutlinedTextField(draft.token, { input -> change { current -> current.copy(token = input) } }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAgentToken) }, visualTransformation = PasswordVisualTransformation()) }
                        item { OutlinedTextField(draft.adminToken, { input -> change { current -> current.copy(adminToken = input) } }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAdminToken) }, visualTransformation = PasswordVisualTransformation()) }
                        item {
                            Text(copy.srvUseTls, color = CommandColors.textSecondary)
                            OutlinedTextField(draft.fingerprint, { input -> change { current -> current.copy(fingerprint = input) } }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(copy.srvTlsFingerprint) })
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                OutlinedTextField(draft.cpuAlert, { input -> change { current -> current.copy(cpuAlert = input.filter(Char::isDigit).take(3)) } }, Modifier.weight(1f), enabled = !busy, singleLine = true, label = { Text(copy.srvCpuAlert) })
                                OutlinedTextField(draft.memAlert, { input -> change { current -> current.copy(memAlert = input.filter(Char::isDigit).take(3)) } }, Modifier.weight(1f), enabled = !busy, singleLine = true, label = { Text(copy.srvMemAlert) })
                            }
                        }
                        if (error != null) item { CommandStateBlock(copy.operationFailed, error.orEmpty(), CommandHealthTone.OFFLINE) }
                        if (message != null) item { CommandStateBlock(copy.srvConnectionResult, message.orEmpty(), CommandHealthTone.INFO) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandPrimaryButton(copy.save, ::save, enabled = !busy, modifier = Modifier.weight(1f))
                        CommandSecondaryButton(if (busy) copy.srvConnecting else copy.srvTestAgent, ::test, enabled = !busy, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text(copy.fleetDiscardTitle) }, text = { Text(copy.fleetDiscardBody) },
        confirmButton = { TextButton(onClick = { discard = false; close() }) { Text(copy.fleetDiscard) } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(copy.cancel) } },
        properties = DialogProperties(securePolicy = screenshotDialogPolicy(protectScreenshots))
    )
}
