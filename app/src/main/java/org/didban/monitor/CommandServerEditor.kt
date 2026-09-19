package org.didban.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
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

    fun close() { model.clear(); onClose() }
    fun requestClose() { if (model.dirty) discard = true else close() }
    fun change(value: ServerConnectionDraft) { model.draft = value; error = null; message = null }
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
        val server = draft.server()
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
        val server = draft.server()
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

    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn
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
                            OutlinedTextField(draft.quickConnect, { change(draft.copy(quickConnect = it.take(2048))) },
                                Modifier.fillMaxWidth(), enabled = !busy, label = { Text(copy.srvQuickConnectLabel) },
                                visualTransformation = PasswordVisualTransformation(), minLines = 2, maxLines = 3)
                            CommandTextButton(copy.srvQuickConnectImport, {
                                try {
                                    val code = QuickConnectCodeParser.parse(draft.quickConnect)
                                    change(draft.copy(name = code.name, host = code.host, port = code.port.toString(),
                                        token = code.readToken, adminToken = code.adminToken, fingerprint = code.fingerprint, quickConnect = ""))
                                    message = copy.srvQuickConnectImported
                                } catch (_: Exception) { error = copy.srvQuickConnectInvalid }
                            }, enabled = !busy && draft.quickConnect.isNotBlank())
                        }
                        item { OutlinedTextField(draft.name, { change(draft.copy(name = it)) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.fleetName) }) }
                        item { OutlinedTextField(draft.host, { change(draft.copy(host = it)) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAgentHost) }) }
                        item { OutlinedTextField(draft.port, { change(draft.copy(port = it.filter(Char::isDigit).take(5))) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.port) }) }
                        item { OutlinedTextField(draft.token, { change(draft.copy(token = it)) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAgentToken) }, visualTransformation = PasswordVisualTransformation()) }
                        item { OutlinedTextField(draft.adminToken, { change(draft.copy(adminToken = it)) }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.srvAdminToken) }, visualTransformation = PasswordVisualTransformation()) }
                        item {
                            Text(copy.srvUseTls, color = CommandColors.textSecondary)
                            OutlinedTextField(draft.fingerprint, { change(draft.copy(fingerprint = it)) }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(copy.srvTlsFingerprint) })
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                                OutlinedTextField(draft.cpuAlert, { change(draft.copy(cpuAlert = it.filter(Char::isDigit).take(3))) }, Modifier.weight(1f), enabled = !busy, singleLine = true, label = { Text(copy.srvCpuAlert) })
                                OutlinedTextField(draft.memAlert, { change(draft.copy(memAlert = it.filter(Char::isDigit).take(3))) }, Modifier.weight(1f), enabled = !busy, singleLine = true, label = { Text(copy.srvMemAlert) })
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
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)
    )
}
