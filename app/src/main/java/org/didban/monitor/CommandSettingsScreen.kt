package org.didban.monitor

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private enum class SettingsConfirmAction {
    RESET_VAULT,
    CLEAR_TRUST
}

@Composable
fun CommandSettingsScreen(
    copy: CommandCopy,
    themeMode: String,
    language: String,
    onThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val protectScreenshots = rememberScreenshotProtection()
    var interval by remember { mutableStateOf((Prefs.getPollIntervalMs(context) / 1000L).toString()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<SettingsConfirmAction?>(null) }
    var trustEntries by remember { mutableStateOf(loadTrustEntries()) }
    var vaultInitialized by remember { mutableStateOf(Prefs.isVaultInitialized(context)) }
    var mutationBusy by remember { mutableStateOf(false) }

    fun saveInterval() {
        val seconds = interval.toLongOrNull()?.coerceIn(5L, 3600L)
        if (seconds == null) {
            saveMessage = copy.setPollRange
        } else {
            Prefs.setPollIntervalSec(context, seconds)
            interval = seconds.toString()
            saveMessage = copy.setPollSaved
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            CommandSectionTitle(
                title = copy.settings,
                supporting = copy.setBody,
                modifier = Modifier.padding(top = CommandSpacing.sm)
            )
        }
        item {
            SettingsSection(title = "Appearance", detail = copy.setAppearanceBody) {
                Text(copy.language, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                    CommandSecondaryButton(copy.persian, { onLanguageChange("fa") }, enabled = language != "fa", modifier = Modifier.weight(1f))
                    CommandSecondaryButton(copy.english, { onLanguageChange("en") }, enabled = language != "en", modifier = Modifier.weight(1f))
                }
                Text(copy.theme, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                    CommandSecondaryButton(copy.light, { onThemeChange("light") }, enabled = themeMode != "light", modifier = Modifier.weight(1f))
                    CommandSecondaryButton(copy.dark, { onThemeChange("dark") }, enabled = themeMode != "dark", modifier = Modifier.weight(1f))
                    CommandSecondaryButton(copy.automatic, { onThemeChange("auto") }, enabled = themeMode != "auto", modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            SettingsSection(title = "Monitoring", detail = copy.setPollBody) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { input -> interval = input.filter(Char::isDigit).take(4); saveMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(copy.setPollIntervalSec) }
                )
                CommandPrimaryButton(copy.save, ::saveInterval, icon = Icons.Rounded.Settings)
                if (saveMessage != null) {
                    val currentMessage = saveMessage.orEmpty()
                    Text(currentMessage, color = if (currentMessage.contains(copy.save)) CommandColors.success else CommandColors.danger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingsSection(title = "Security", detail = copy.setDangerBody) {
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(
                        value = protectScreenshots, role = Role.Switch,
                        onValueChange = { Prefs.setScreenshotProtectionEnabled(context, it) }
                    ).padding(vertical = CommandSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(copy.setScreenshotProtection, color = CommandColors.textPrimary,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(copy.setScreenshotProtectionHint, color = CommandColors.textSecondary,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = protectScreenshots, onCheckedChange = null)
                }
                CommandRule()
                CommandStatusMark(
                    if (vaultInitialized) "Vault initialized" else "Vault not initialized",
                    if (vaultInitialized) CommandHealthTone.HEALTHY else CommandHealthTone.UNKNOWN,
                    detail = if (vaultInitialized) copy.setVaultReady else copy.setVaultLockedHint
                )
                CommandSecondaryButton(copy.setResetVault, { confirmAction = SettingsConfirmAction.RESET_VAULT }, icon = Icons.Rounded.Lock, enabled = vaultInitialized)
                CommandRule()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("SSH Trust Store", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(copy.setTrustCount.replace("%1", trustEntries.size.toString()), color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                    CommandTextButton(copy.setPurge, { confirmAction = SettingsConfirmAction.CLEAR_TRUST }, icon = Icons.Rounded.DeleteOutline, enabled = trustEntries.isNotEmpty())
                }
                if (trustEntries.isEmpty()) {
                    Text(copy.setTrustEmpty, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                } else {
                    trustEntries.forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = CommandSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${entry.host}:${entry.port}", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                Text(entry.fingerprint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item {
            SettingsSection(title = "Diagnostics", detail = copy.setRuntimeBody) {
                CommandStatusMark("Didban ${BuildConfig.VERSION_NAME}", CommandHealthTone.INFO, detail = copy.setHostKeyStoreInit.replace("%1", HostKeyTrustStore.initialized.toString()))
                Text(copy.setConnectivityBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (confirmAction != null) {
        val action = confirmAction
        AlertDialog(
            onDismissRequest = { if (!mutationBusy) confirmAction = null },
            title = { Text(if (action == SettingsConfirmAction.RESET_VAULT) copy.setResetVaultTitle else copy.setPurgeTrustTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (action == SettingsConfirmAction.RESET_VAULT) copy.setResetVaultBody
                    else copy.setPurgeTrustBody
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (mutationBusy) return@TextButton
                    mutationBusy = true
                    runCatching {
                        if (action == SettingsConfirmAction.RESET_VAULT) Prefs.resetVault(context)
                        else HostKeyTrustStore.clearAll()
                    }.onSuccess {
                        if (action == SettingsConfirmAction.RESET_VAULT) vaultInitialized = false
                        else trustEntries = loadTrustEntries()
                        confirmAction = null
                    }.onFailure {
                        saveMessage = (it.message ?: copy.operationFailed).take(300)
                    }
                    mutationBusy = false
                }, enabled = !mutationBusy) { Text(if (action == SettingsConfirmAction.RESET_VAULT) copy.setResetVaultAction else copy.setPurgeTrustAction, color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }, enabled = !mutationBusy) { Text(copy.cancel) } }
        )
    }
}

private fun loadTrustEntries(): List<TrustedHostKey> =
    runCatching { HostKeyTrustStore.entries() }.getOrDefault(emptyList())

@Composable
private fun SettingsSection(
    title: String,
    detail: String,
    content: @Composable ColumnScope.() -> Unit
) {
    CommandSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
            Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
            content()
        }
    }
}
