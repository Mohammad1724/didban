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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingRestoreRequest(
    val raw: String,
    val password: String?,
    val mode: RestoreMode,
    val preview: BackupPreview
)

@Composable
fun CommandVaultScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteNote by remember { mutableStateOf<VaultNote?>(null) }
    var mutating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("SSH") }

    fun unlock() {
        if (password.isBlank()) return
        runCatching {
            if (!Prefs.isVaultInitialized(context)) {
                Prefs.setupMasterPassword(context, password)
                emptyList()
            } else {
                check(Prefs.verifyMasterPassword(context, password)) { copy.vaultBadMaster }
                Prefs.loadVaultNotes(context, password)
            }
        }.onSuccess {
            notes = it
            unlocked = true
            error = null
        }.onFailure { error = it.message ?: copy.vaultUnlockFailed }
    }

    fun commitNotes(next: List<VaultNote>): Boolean {
        return runCatching { Prefs.saveVaultNotes(context, next, password) }
            .onSuccess {
                notes = next
                error = null
            }
            .onFailure { error = it.message ?: copy.vaultSaveFailed }
            .isSuccess
    }

    fun lock() {
        unlocked = false
        notes = emptyList()
        password = ""
        revealedId = null
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.vault, "AES-256-GCM · PBKDF2", modifier = Modifier.weight(1f))
                if (unlocked) CommandTextButton(copy.lock, ::lock, Icons.Rounded.Lock)
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommandStatusMark(
                            if (unlocked) copy.vaultOpen else copy.vaultLocked,
                            if (unlocked) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION,
                            Modifier.weight(1f),
                            if (unlocked) copy.vaultOpenBody else copy.vaultLockedBody
                        )
                        if (unlocked) CommandTextButton(copy.vaultLockNow, ::lock, Icons.Rounded.Lock)
                    }
                    if (!unlocked) {
                        Spacer(Modifier.height(CommandSpacing.md))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(copy.vaultMasterPassword) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Spacer(Modifier.height(CommandSpacing.sm))
                        CommandPrimaryButton(copy.vaultUnlock, ::unlock, icon = Icons.Rounded.LockOpen)
                    }
                    if (error != null) {
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(error ?: "", color = CommandColors.danger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (unlocked) {
            item {
                CommandSectionTitle(
                    title = copy.vaultSecrets,
                    supporting = "${notes.size}",
                    actionLabel = copy.vaultAddSecret,
                    onAction = { showAdd = true }
                )
            }
            if (notes.isEmpty()) {
                item { CommandEmptyState(copy.vaultEmpty, copy.vaultEmptyBody, copy.vaultAddSecret, { showAdd = true }) }
            } else {
                items(notes, key = { it.id }) { note ->
                    val revealed = revealedId == note.id
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text(note.tags.ifBlank { copy.secretType }, color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                }
                                CommandIconButton(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (revealed) copy.hide else copy.revealTemporarily, { revealedId = if (revealed) null else note.id })
                                CommandIconButton(Icons.Rounded.ContentCopy, copy.copySecret, {
                                    SensitiveClipboard.copy(context, note.title, note.content)
                                })
                                CommandIconButton(Icons.Rounded.DeleteOutline, copy.deleteSecret, {
                                    if (!mutating) deleteNote = note
                                })
                            }
                            Spacer(Modifier.height(CommandSpacing.sm))
                            Text(
                                if (revealed) note.content else "••••••••••••••••••••••••",
                                color = if (revealed) CommandColors.textPrimary else CommandColors.textTertiary,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(fontFamily = Telemetry),
                                maxLines = if (revealed) 12 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(copy.vaultAddSecret) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    OutlinedTextField(title, { title = it.take(200) }, label = { Text(copy.secretTitle) }, singleLine = true)
                    OutlinedTextField(tags, { tags = it.take(500) }, label = { Text(copy.secretTags) }, singleLine = true)
                    OutlinedTextField(content, { content = it.take(65_536) }, label = { Text(copy.secretContent) }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && content.isNotBlank() && !mutating, onClick = {
                    if (mutating) return@TextButton
                    mutating = true
                    val nextId = generateSequence(System.currentTimeMillis()) { it + 1 }.first { id -> notes.none { it.id == id } }
                    val next = notes + VaultNote(nextId, title.trim(), content, tags.trim())
                    if (commitNotes(next)) {
                        title = ""
                        content = ""
                        showAdd = false
                    }
                    mutating = false
                }) { Text(copy.save) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(copy.close) } }
        )
    }

    val noteToDelete = deleteNote
    if (noteToDelete != null) {
        CommandDestructiveDialog(
            title = copy.deleteSecret,
            body = "${noteToDelete.title}\n${noteToDelete.tags.ifBlank { copy.secretType }} · #${noteToDelete.id}",
            confirmLabel = copy.delete,
            dismissLabel = copy.cancel,
            onDismiss = { if (!mutating) deleteNote = null },
            enabled = !mutating,
            onConfirm = {
                if (!mutating) {
                    mutating = true
                    val current = notes.firstOrNull { it.id == noteToDelete.id }
                    if (current != null && current.title == noteToDelete.title && current.content == noteToDelete.content) {
                        if (commitNotes(notes.filterNot { it.id == noteToDelete.id })) {
                            if (revealedId == noteToDelete.id) revealedId = null
                            deleteNote = null
                        }
                    } else error = copy.vaultSaveFailed
                    mutating = false
                }
            }
        )
    }

}

@Composable
fun CommandBackupScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var createPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.Merge) }
    var result by remember { mutableStateOf<String?>(null) }
    var resultSuccess by remember { mutableStateOf<Boolean?>(null) }
    var inspectedRaw by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestoreRequest?>(null) }
    var busy by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.backup, copy.backupPreviewRestore, modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.backupCreate, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Text(copy.backupBody, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                    OutlinedTextField(
                        createPassword,
                        { createPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(copy.backupPasswordOptional) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    CommandPrimaryButton(copy.backupCreateEncrypted, {
                        runCatching { BackupEngine.createBackup(context, createPassword) }
                            .onSuccess {
                                raw = it
                                result = copy.backupCreated
                            }
                            .onFailure {
                                result = securityMessage(Prefs.getLanguage(context), SecurityMessage.BACKUP_PASSWORD_REQUIRED)
                            }
                    }, icon = Icons.Rounded.Security, enabled = createPassword.isNotBlank())
                }
            }
        }
        if (raw.isNotBlank()) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(copy.backupOutput, Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                            CommandTextButton(copy.copyAction, { SensitiveClipboard.copy(context, "Didban encrypted backup", raw) }, Icons.Rounded.ContentCopy)
                        }
                        Spacer(Modifier.height(CommandSpacing.sm))
                        Text(raw, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), maxLines = 8, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.backupVerifyRestore, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(raw, { raw = it; preview = null; inspectedRaw = null; result = null; resultSuccess = null }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text(copy.backupText) })
                    OutlinedTextField(restorePassword, { restorePassword = it; preview = null; inspectedRaw = null; result = null; resultSuccess = null }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.backupPassword) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandSecondaryButton(copy.backupModeMerge, { mode = RestoreMode.Merge }, enabled = mode != RestoreMode.Merge)
                        CommandSecondaryButton(copy.backupModeOverwrite, { mode = RestoreMode.Overwrite }, enabled = mode != RestoreMode.Overwrite)
                        CommandSecondaryButton(copy.backupInspect, { preview = BackupEngine.inspectBackup(raw, restorePassword.takeIf { it.isNotBlank() }); inspectedRaw = raw }, enabled = raw.isNotBlank() && !busy)
                    }
                    if (busy) CommandInlineLoading(copy.waitingForData)
                    val currentPreview = preview
                    if (currentPreview != null) {
                        val p = currentPreview
                        CommandStatusMark(if (p.isValid) copy.backupValid else copy.backupInvalid, if (p.isValid) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE, detail = if (p.isValid) copy.backupSummary.replace("%1", p.serversCount.toString()).replace("%2", p.tunnelsCount.toString()).replace("%3", p.uptimeCount.toString()).replace("%4", BackupEngine.formatTimestamp(p.timestamp)) else p.errorMessage)
                        Spacer(Modifier.height(CommandSpacing.xs))
                        CommandPrimaryButton(copy.backupRestoreAction.replace("%1", if (mode == RestoreMode.Merge) copy.backupModeMerge else copy.backupModeOverwrite), {
                            if (raw == inspectedRaw) pendingRestore = PendingRestoreRequest(raw, restorePassword.takeIf { it.isNotBlank() }, mode, p)
                        }, enabled = p.isValid && raw == inspectedRaw && !busy)
                    }
                    if (result != null) Text(result ?: "", color = if (resultSuccess == true) CommandColors.success else CommandColors.danger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val request = pendingRestore
    if (request != null) {
        val destructive = request.mode == RestoreMode.Overwrite
        CommandConfirmDialog(
            title = copy.backupRestoreAction.replace("%1", if (destructive) copy.backupModeOverwrite else copy.backupModeMerge),
            body = copy.backupSummary
                .replace("%1", request.preview.serversCount.toString())
                .replace("%2", request.preview.tunnelsCount.toString())
                .replace("%3", request.preview.uptimeCount.toString())
                .replace("%4", BackupEngine.formatTimestamp(request.preview.timestamp)),
            confirmLabel = copy.run,
            dismissLabel = copy.cancel,
            destructive = destructive,
            onDismiss = { if (!busy) pendingRestore = null },
            enabled = !busy,
            onConfirm = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        val restored = withContext(Dispatchers.Default) {
                            BackupEngine.restoreBackup(context, request.raw, request.password, request.mode)
                        }
                        result = restored.message.take(500)
                        resultSuccess = restored.success
                        if (restored.success) {
                            pendingRestore = null
                            preview = null
                            inspectedRaw = null
                            restorePassword = ""
                        }
                        busy = false
                    }
                }
            }
        )
    }

}

@Composable
fun CommandAlertsScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var telegramToken by remember { mutableStateOf(Prefs.getTelegramBotToken(context)) }
    var telegramChat by remember { mutableStateOf(Prefs.getTelegramChatId(context)) }
    var telegramEnabled by remember { mutableStateOf(Prefs.isTelegramAlertsEnabled(context)) }
    var discordUrl by remember { mutableStateOf(Prefs.getDiscordWebhookUrl(context)) }
    var discordEnabled by remember { mutableStateOf(Prefs.isDiscordAlertsEnabled(context)) }
    var downTrigger by remember { mutableStateOf(Prefs.isAlertTriggerDown(context)) }
    var spikeTrigger by remember { mutableStateOf(Prefs.isAlertTriggerSpike(context)) }
    var tunnelTrigger by remember { mutableStateOf(Prefs.isAlertTriggerTunnel(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    fun save() {
        runCatching {
            // Persist secrets first. If secure storage is unavailable, do not
            // misleadingly report success or enable channels with missing credentials.
            Prefs.setTelegramBotToken(context, telegramToken)
            Prefs.setDiscordWebhookUrl(context, discordUrl)
            Prefs.setTelegramChatId(context, telegramChat)
            Prefs.setTelegramAlertsEnabled(context, telegramEnabled)
            Prefs.setDiscordAlertsEnabled(context, discordEnabled)
            Prefs.setAlertTriggerDown(context, downTrigger)
            Prefs.setAlertTriggerSpike(context, spikeTrigger)
            Prefs.setAlertTriggerTunnel(context, tunnelTrigger)
        }.onSuccess {
            message = copy.saved
            messageIsError = false
        }.onFailure {
            message = it.message ?: copy.operationFailed
            messageIsError = true
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.alerts, copy.alertsChannelsBody, modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.alertsTelegram, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(telegramToken, { telegramToken = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.alertsBotToken) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    OutlinedTextField(telegramChat, { telegramChat = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.alertsChatId) })
                    CommandToggleRow(copy.alertsEnableTelegram, telegramEnabled) { telegramEnabled = it }
                    CommandSecondaryButton(copy.alertsTestTelegram, {
                        testing = true
                        scope.launch {
                            val response = AlertEngine.testTelegram(telegramToken, telegramChat)
                            message = response.second
                            messageIsError = !response.first
                            testing = false
                        }
                    }, icon = Icons.Rounded.PlayArrow, enabled = !testing)
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.alertsDiscordWebhook, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(discordUrl, { discordUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.alertsWebhookUrl) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    CommandToggleRow(copy.alertsEnableDiscord, discordEnabled) { discordEnabled = it }
                    CommandSecondaryButton(copy.alertsTestDiscord, {
                        testing = true
                        scope.launch {
                            val response = AlertEngine.testDiscord(discordUrl)
                            message = response.second
                            messageIsError = !response.first
                            testing = false
                        }
                    }, icon = Icons.Rounded.PlayArrow, enabled = !testing)
                }
            }
        }
        if (testing) item { CommandInlineLoading(copy.waitingForData) }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    Text(copy.alertsTriggers, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    CommandToggleRow(copy.alertsTriggerServerDown, downTrigger) { downTrigger = it }
                    CommandToggleRow(copy.alertsTriggerCpuSpike, spikeTrigger) { spikeTrigger = it }
                    CommandToggleRow(copy.alertsTriggerTunnelDown, tunnelTrigger) { tunnelTrigger = it }
                }
            }
        }
        item {
            CommandPrimaryButton(copy.save, ::save)
            if (message != null) {
                Spacer(Modifier.height(CommandSpacing.sm))
                Text(message ?: "", color = if (messageIsError) CommandColors.danger else CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
private fun CommandToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = CommandSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
