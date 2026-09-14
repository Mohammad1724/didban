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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CommandVaultScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var password by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var revealedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
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
                check(Prefs.verifyMasterPassword(context, password)) { "رمز Master نادرست است." }
                Prefs.loadVaultNotes(context, password)
            }
        }.onSuccess {
            notes = it
            unlocked = true
            error = null
        }.onFailure { error = it.message ?: "بازکردن Vault ناموفق بود." }
    }

    fun saveNotes() {
        runCatching { Prefs.saveVaultNotes(context, notes, password) }
            .onFailure { error = it.message ?: "ذخیرهٔ Vault ناموفق بود." }
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
                if (unlocked) CommandTextButton("قفل", ::lock, Icons.Rounded.Lock)
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommandStatusMark(
                            if (unlocked) "Vault باز است" else "Vault قفل است",
                            if (unlocked) CommandHealthTone.HEALTHY else CommandHealthTone.ATTENTION,
                            Modifier.weight(1f),
                            if (unlocked) "Secretها فقط در Session فعلی قابل مشاهده‌اند." else "هیچ Secretی در حالت قفل نمایش داده نمی‌شود."
                        )
                        if (unlocked) CommandTextButton("قفل فوری", ::lock, Icons.Rounded.Lock)
                    }
                    if (!unlocked) {
                        Spacer(Modifier.height(CommandSpacing.md))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Master Password") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        Spacer(Modifier.height(CommandSpacing.sm))
                        CommandPrimaryButton("بازکردن Vault", ::unlock, icon = Icons.Rounded.LockOpen)
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
                    title = "Secretهای ذخیره‌شده",
                    supporting = "${notes.size}",
                    actionLabel = "افزودن Secret",
                    onAction = { showAdd = true }
                )
            }
            if (notes.isEmpty()) {
                item { CommandEmptyState("Vault خالی است", "Secret واقعی خود را فقط پس از Unlock اضافه کنید.", "افزودن Secret", { showAdd = true }) }
            } else {
                items(notes, key = { it.id }) { note ->
                    val revealed = revealedId == note.id
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(CommandSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(note.title, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                    Text(note.tags.ifBlank { "SECRET" }, color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                }
                                CommandIconButton(if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (revealed) "پنهان‌کردن" else "نمایش موقت", { revealedId = if (revealed) null else note.id })
                                CommandIconButton(Icons.Rounded.ContentCopy, "کپی Secret", {
                                    clipboard.setText(AnnotatedString(note.content))
                                })
                                CommandIconButton(Icons.Rounded.DeleteOutline, "حذف Secret", {
                                    notes = notes.filterNot { it.id == note.id }
                                    saveNotes()
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
            title = { Text("افزودن Secret") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, singleLine = true)
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tag") }, singleLine = true)
                    OutlinedTextField(content, { content = it }, label = { Text("محتوای حساس") }, minLines = 4)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && content.isNotBlank(), onClick = {
                    notes = notes + VaultNote(System.currentTimeMillis(), title.trim(), content, tags.trim())
                    saveNotes()
                    title = ""
                    content = ""
                    showAdd = false
                }) { Text(copy.save) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(copy.close) } }
        )
    }
}

@Composable
fun CommandBackupScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var createPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.Merge) }
    var result by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.backup, "Preview → Restore", modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("ساخت Backup", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Text("Backup بدون Password قابل خواندن است؛ برای دادهٔ واقعی از رمز استفاده کنید.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                    OutlinedTextField(
                        createPassword,
                        { createPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password اختیاری") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    CommandPrimaryButton("ساخت Backup رمزنگاری‌شده", {
                        raw = BackupEngine.createBackup(context, createPassword.takeIf { it.isNotBlank() })
                        result = "Backup ساخته شد. مقدار آن را خارج از دستگاه امن نگه دارید."
                    }, icon = Icons.Rounded.Security)
                }
            }
        }
        if (raw.isNotBlank()) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("خروجی Backup", Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                            CommandTextButton("کپی", { clipboard.setText(AnnotatedString(raw)) }, Icons.Rounded.ContentCopy)
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
                    Text("بررسی و Restore", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(raw, { raw = it; preview = null; result = null }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text("متن Backup") })
                    OutlinedTextField(restorePassword, { restorePassword = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password Backup") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandSecondaryButton("Merge", { mode = RestoreMode.Merge }, enabled = mode != RestoreMode.Merge)
                        CommandSecondaryButton("Overwrite", { mode = RestoreMode.Overwrite }, enabled = mode != RestoreMode.Overwrite)
                        CommandSecondaryButton("Inspect", { preview = BackupEngine.inspectBackup(raw, restorePassword.takeIf { it.isNotBlank() }) }, enabled = raw.isNotBlank())
                    }
                    if (preview != null) {
                        val p = preview!!
                        CommandStatusMark(if (p.isValid) "ساختار معتبر" else "ساختار نامعتبر", if (p.isValid) CommandHealthTone.HEALTHY else CommandHealthTone.OFFLINE, detail = if (p.isValid) "${p.serversCount} سرور · ${p.tunnelsCount} تونل · ${p.uptimeCount} Monitor · ${BackupEngine.formatTimestamp(p.timestamp)}" else p.errorMessage)
                        Spacer(Modifier.height(CommandSpacing.xs))
                        CommandPrimaryButton("Restore ${if (mode == RestoreMode.Merge) "Merge" else "Overwrite"}", {
                            val restored = BackupEngine.restoreBackup(context, raw, restorePassword.takeIf { it.isNotBlank() }, mode)
                            result = restored.message
                        }, enabled = p.isValid)
                    }
                    if (result != null) Text(result ?: "", color = CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandAlertsScreen(
    copy: CommandCopy,
    onBack: () -> Unit
) {
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
    var testing by remember { mutableStateOf(false) }

    fun save() {
        Prefs.setTelegramBotToken(context, telegramToken)
        Prefs.setTelegramChatId(context, telegramChat)
        Prefs.setTelegramAlertsEnabled(context, telegramEnabled)
        Prefs.setDiscordWebhookUrl(context, discordUrl)
        Prefs.setDiscordAlertsEnabled(context, discordEnabled)
        Prefs.setAlertTriggerDown(context, downTrigger)
        Prefs.setAlertTriggerSpike(context, spikeTrigger)
        Prefs.setAlertTriggerTunnel(context, tunnelTrigger)
        message = copy.saved
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.alerts, "کانال‌ها و Triggerهای واقعی", modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("Telegram", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(telegramToken, { telegramToken = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Bot Token") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    OutlinedTextField(telegramChat, { telegramChat = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Chat ID") })
                    CommandToggleRow("فعال‌سازی Telegram", telegramEnabled) { telegramEnabled = it }
                    CommandSecondaryButton("Test Telegram", {
                        testing = true
                        scope.launch {
                            val response = AlertEngine.testTelegram(telegramToken, telegramChat)
                            message = response.second
                            testing = false
                        }
                    }, icon = Icons.Rounded.PlayArrow, enabled = !testing)
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("Discord Webhook", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(discordUrl, { discordUrl = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Webhook URL") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    CommandToggleRow("فعال‌سازی Discord", discordEnabled) { discordEnabled = it }
                    CommandSecondaryButton("Test Discord", {
                        testing = true
                        scope.launch {
                            val response = AlertEngine.testDiscord(discordUrl)
                            message = response.second
                            testing = false
                        }
                    }, icon = Icons.Rounded.PlayArrow, enabled = !testing)
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    Text("Triggerها", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    CommandToggleRow("قطع شدن Server", downTrigger) { downTrigger = it }
                    CommandToggleRow("Spike CPU یا Memory", spikeTrigger) { spikeTrigger = it }
                    CommandToggleRow("افتادن Tunnel", tunnelTrigger) { tunnelTrigger = it }
                }
            }
        }
        item {
            CommandPrimaryButton(copy.save, ::save)
            if (message != null) {
                Spacer(Modifier.height(CommandSpacing.sm))
                Text(message ?: "", color = CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
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
