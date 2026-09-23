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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
    onLanguageChange: (String) -> Unit,
    onNavigate: ((CommandRoute, ServerConfig?) -> Unit)? = null
) {
    val context = LocalContext.current
    val protectScreenshots = rememberScreenshotProtection()
    var interval by remember { mutableStateOf((Prefs.getPollIntervalMs(context) / 1000L).toString()) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveMessageIsError by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<SettingsConfirmAction?>(null) }
    var trustEntries by remember { mutableStateOf(loadTrustEntries()) }
    var vaultInitialized by remember { mutableStateOf(Prefs.isVaultInitialized(context)) }
    var mutationBusy by remember { mutableStateOf(false) }

    fun saveInterval() {
        val seconds = interval.toLongOrNull()?.coerceIn(5L, 3600L)
        if (seconds == null) {
            saveMessage = copy.setPollRange
            saveMessageIsError = true
        } else {
            Prefs.setPollIntervalSec(context, seconds)
            interval = seconds.toString()
            saveMessage = copy.setPollSaved
            saveMessageIsError = false
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
            SettingsSection(title = copy.uiAppearance, detail = copy.setAppearanceBody) {
                Text(copy.language, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                CommandChipRow(listOf(copy.persian to "fa", copy.english to "en"), language, onLanguageChange, true)
                Text(copy.theme, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                CommandChipRow(listOf(copy.light to "light", copy.dark to "dark", copy.automatic to "auto"), themeMode, onThemeChange, true)
            }
        }
        if (onNavigate != null) {
            item { CommandSectionTitle(copy.uiDataSafety) }
            settingsToolRoutes.forEach { route -> item(key = route.key) {
                CommandToolLink(copy, route, onClick = { onNavigate(route, null) })
            } }
        }
        item {
            SettingsSection(title = copy.uiPreferences, detail = copy.setPollBody) {
                OutlinedTextField(
                    value = interval,
                    onValueChange = { input -> interval = input.filter(Char::isDigit).take(4); saveMessage = null; saveMessageIsError = false },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(copy.setPollIntervalSec) }
                )
                CommandPrimaryButton(copy.save, ::saveInterval, icon = Icons.Rounded.Settings)
                if (saveMessage != null) {
                    val currentMessage = saveMessage.orEmpty()
                    Text(currentMessage, color = if (saveMessageIsError) CommandColors.danger else CommandColors.success, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SettingsSection(title = copy.uiSecuritySettings, detail = copy.setDangerBody) {
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
                    if (vaultInitialized) copy.uiVaultInitialized else copy.uiVaultNotInitialized,
                    if (vaultInitialized) CommandHealthTone.HEALTHY else CommandHealthTone.UNKNOWN,
                    detail = if (vaultInitialized) copy.setVaultReady else copy.setVaultLockedHint
                )
                CommandSecondaryButton(copy.setResetVault, { confirmAction = SettingsConfirmAction.RESET_VAULT }, icon = Icons.Rounded.Lock, enabled = vaultInitialized)
                CommandRule()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(copy.setTrustStore, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
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
            SettingsSection(title = copy.uiAdvanced, detail = copy.setRuntimeBody) {
                CommandStatusMark("Didban ${BuildConfig.VERSION_NAME}", CommandHealthTone.INFO, detail = copy.setHostKeyStoreInit.replace("%1", HostKeyTrustStore.initialized.toString()))
                Text(copy.setConnectivityBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        if (mutationBusy) item { CommandInlineLoading(copy.waitingForData) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (confirmAction != null) {
        val action = confirmAction
        val resetVault = action == SettingsConfirmAction.RESET_VAULT
        CommandDestructiveDialog(
            title = if (resetVault) copy.setResetVaultTitle else copy.setPurgeTrustTitle,
            body = if (resetVault) copy.setResetVaultBody else copy.setPurgeTrustBody,
            confirmLabel = if (resetVault) copy.setResetVaultAction else copy.setPurgeTrustAction,
            dismissLabel = copy.cancel,
            onDismiss = { if (!mutationBusy) confirmAction = null },
            enabled = !mutationBusy,
            onConfirm = {
                if (!mutationBusy) {
                    mutationBusy = true
                    runCatching {
                        if (resetVault) Prefs.resetVault(context) else HostKeyTrustStore.clearAll()
                    }.onSuccess {
                        if (resetVault) vaultInitialized = false else trustEntries = loadTrustEntries()
                        confirmAction = null
                    }.onFailure {
                        saveMessage = (it.message ?: copy.operationFailed).take(300)
                        saveMessageIsError = true
                    }
                    mutationBusy = false
                }
            }
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
