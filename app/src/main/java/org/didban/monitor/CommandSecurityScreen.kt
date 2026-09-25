package org.didban.monitor

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
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Server hardening workspace: firewall state, opening a port, and fail2ban
 * ban management — all over SSH against the selected server.
 *
 * Restored into the command shell. The screen was dropped when the shell was
 * rebuilt even though everything it depends on (SshEngine, SecurityValidation,
 * OutputParsers, the host-key trust store) was still in the tree.
 *
 * Every value that reaches a shell command goes through SecurityValidation
 * first and is then shell-quoted, so a hostile jail name or IP cannot inject
 * arguments.
 */

/** One banned address, as reported by a fail2ban jail. */
data class BannedIpItem(
    val ip: String,
    val jail: String,
    val banTime: String
)

@Composable
fun CommandSecurityScreen(
    copy: CommandCopy,
    initialServer: ServerConfig?,
    onSelectServer: () -> Unit,
    onBack: () -> Unit
) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operationGate = remember { CommandOperationGate() }
    val servers = remember { Prefs.loadServers(context) }

    var selectedId by rememberSaveable(initialServer?.id) {
        mutableStateOf(initialServer?.id ?: servers.firstOrNull()?.id)
    }
    val server = selectedId?.let { id -> servers.firstOrNull { it.id == id } }

    // The SSH port survives rotation; the password deliberately does not —
    // rememberSaveable would persist a credential into the saved state bundle.
    var sshPort by rememberSaveable { mutableStateOf("22") }
    var password by remember { mutableStateOf("") }
    var portToAllow by rememberSaveable { mutableStateOf("") }

    var firewallOutput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingPort by remember { mutableStateOf<Int?>(null) }
    var pendingPortServerId by remember { mutableStateOf<Long?>(null) }
    var pendingUnban by remember { mutableStateOf<BannedIpItem?>(null) }
    var pendingUnbanServerId by remember { mutableStateOf<Long?>(null) }

    val banned = remember { mutableStateListOf<BannedIpItem>() }
    var bannedFetched by remember { mutableStateOf(false) }

    // SSH host-key trust (TOFU). Prompts are serialized so two concurrent
    // connections can never raise two dialogs at once.
    var prompt by remember { mutableStateOf<HostKeyPrompt?>(null) }
    val promptChannel = remember { Channel<Boolean>(Channel.RENDEZVOUS) }
    val promptGate = remember { Mutex() }
    val policy = remember {
        ConfirmingHostKeyPolicy(HostKeyTrustStore) { next ->
            promptGate.withLock {
                withContext(Dispatchers.Main) { prompt = next }
                promptChannel.receive()
            }
        }
    }

    fun safeError(value: String): String =
        SecretRedactor.redact(value, listOf(password)).take(300)

    fun guard(): Boolean {
        if (server == null) {
            error = copy.noServerSelected
            return false
        }
        if (password.isBlank()) {
            error = copy.securityNeedPassword
            return false
        }
        return true
    }

    fun cancel() {
        operationGate.cancel()
        job?.cancel()
        job = null
        prompt = null
        promptChannel.trySend(false)
        busy = false
    }

    fun inspectFirewall() {
        if (!guard() || busy) return
        val target = server ?: return
        val port = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        val operationId = operationGate.begin()
        busy = true
        error = null
        retryAction = null
        notice = null
        job = scope.launch {
            try {
                val res = SshEngine.execute(
                    target.host, port, "root", password,
                    "ufw status verbose 2>/dev/null || iptables -L -n -v | head -n 25",
                    15, policy
                )
                if (operationGate.owns(operationId)) {
                    firewallOutput = res.stdout.ifBlank { res.stderr }
                    if (!res.isSuccess && firewallOutput.isBlank()) {
                        error = copy.securityCommandFailed.replace("%1", safeError(res.errorMessage ?: res.stderr).ifBlank { copy.unknownState })
                        retryAction = { inspectFirewall() }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.securityCommandFailed.replace("%1", safeError(failure.message.orEmpty()).ifBlank { copy.unknownState })
                    retryAction = { inspectFirewall() }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    busy = false
                    job = null
                }
            }
        }
    }

    fun allowPort(confirmedPort: Int) {
        if (!guard() || busy) return
        val target = server ?: return
        val port = SecurityValidation.validatePort(confirmedPort.toString()) ?: run {
            error = copy.securityBadPort
            return
        }
        val sshPortNum = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        val operationId = operationGate.begin()
        busy = true
        error = null
        retryAction = null
        notice = null
        job = scope.launch {
            var refreshAfter = false
            try {
                // The port is already validated as digits only; the rule argument
                // is quoted again as defence in depth.
                val rule = SecurityValidation.shellQuote("$port/tcp")
                val res = SshEngine.execute(
                    target.host, sshPortNum, "root", password,
                    "ufw allow $rule && ufw reload", 15, policy
                )
                if (!operationGate.owns(operationId)) return@launch
                if (res.isSuccess) {
                    notice = copy.securityPortOpened.replace("%d", port.toString())
                    portToAllow = ""
                    refreshAfter = true
                } else {
                    error = copy.securityCommandFailed.replace("%1", safeError(res.stderr.ifBlank { res.errorMessage ?: copy.securityExitCode.replace("%1", res.exitCode.toString()) }).ifBlank { copy.unknownState })
                    retryAction = { allowPort(port) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.securityCommandFailed.replace("%1", safeError(failure.message.orEmpty()).ifBlank { copy.unknownState })
                    retryAction = { allowPort(port) }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    busy = false
                    job = null
                    if (refreshAfter) inspectFirewall()
                }
            }
        }
    }

    fun refreshBans() {
        if (!guard() || busy) return
        val target = server ?: return
        val port = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        val operationId = operationGate.begin()
        busy = true
        error = null
        retryAction = null
        notice = null
        job = scope.launch {
            try {
                val jailsRes = SshEngine.execute(
                    target.host, port, "root", password,
                    "fail2ban-client status 2>/dev/null", 15, policy
                )
                if (!operationGate.owns(operationId)) return@launch
                if (!jailsRes.isSuccess) {
                    bannedFetched = true
                    banned.clear()
                    val out = (jailsRes.stdout + jailsRes.stderr).trim()
                    error = if (out.contains("not found") || jailsRes.stdout.isBlank()) {
                        copy.securityNoFail2ban
                    } else {
                        copy.securityCommandFailed.replace("%1", safeError(out).ifBlank { copy.unknownState })
                    }
                    retryAction = { refreshBans() }
                    return@launch
                }
                val items = mutableListOf<BannedIpItem>()
                for (jail in OutputParsers.fail2banJails(jailsRes.stdout)) {
                    val stRes = SshEngine.execute(
                        target.host, port, "root", password,
                        "fail2ban-client status ${SecurityValidation.shellQuote(jail)} 2>/dev/null",
                        15, policy
                    )
                    if (!operationGate.owns(operationId)) return@launch
                    if (!stRes.isSuccess) continue
                    OutputParsers.fail2banBannedIps(stRes.stdout).forEach { ip ->
                        items.add(BannedIpItem(ip, jail, ""))
                    }
                }
                if (!operationGate.owns(operationId)) return@launch
                banned.clear()
                banned.addAll(items)
                bannedFetched = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.securityCommandFailed.replace("%1", safeError(failure.message.orEmpty()).ifBlank { copy.unknownState })
                    retryAction = { refreshBans() }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    busy = false
                    job = null
                }
            }
        }
    }

    fun unban(item: BannedIpItem) {
        if (!guard() || busy) return
        val target = server ?: return
        if (!SecurityValidation.isValidIpv4(item.ip) || !SecurityValidation.validateJail(item.jail)) {
            error = copy.securityBadValue
            return
        }
        val port = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        val operationId = operationGate.begin()
        busy = true
        error = null
        retryAction = null
        notice = null
        job = scope.launch {
            var refreshAfter = false
            try {
                val jail = SecurityValidation.shellQuote(item.jail)
                val ip = SecurityValidation.shellQuote(item.ip)
                val res = SshEngine.execute(
                    target.host, port, "root", password,
                    "fail2ban-client set $jail unbanip $ip", 15, policy
                )
                if (!operationGate.owns(operationId)) return@launch
                if (res.isSuccess) {
                    notice = copy.securityUnbanned.replace("%s", item.ip)
                    refreshAfter = true
                } else {
                    error = copy.securityCommandFailed.replace("%1", safeError(res.stderr.ifBlank { res.errorMessage ?: copy.securityExitCode.replace("%1", res.exitCode.toString()) }).ifBlank { copy.unknownState })
                    retryAction = { unban(item) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (operationGate.owns(operationId)) {
                    error = copy.securityCommandFailed.replace("%1", safeError(failure.message.orEmpty()).ifBlank { copy.unknownState })
                    retryAction = { unban(item) }
                }
            } finally {
                if (operationGate.owns(operationId)) {
                    busy = false
                    job = null
                    if (refreshAfter) refreshBans()
                }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.security, server?.name ?: copy.noServerSelected, modifier = Modifier.weight(1f))
            }
        }

        if (servers.isEmpty()) {
            item { CommandEmptyState(copy.selectServer, copy.noServersBody, copy.selectServer, onSelectServer) }
        } else {
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(copy.currentServer, style = MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                            servers.forEach { candidate ->
                                CommandSecondaryButton(
                                    candidate.name,
                                    { selectedId = candidate.id },
                                    enabled = selectedId != candidate.id && !busy,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                password, { password = it },
                                modifier = Modifier.weight(1f), enabled = !busy, singleLine = true,
                                label = { Text(copy.securitySshPassword) },
                                visualTransformation = PasswordVisualTransformation()
                            )
                            OutlinedTextField(
                                sshPort, { sshPort = it.filter(Char::isDigit).take(5) },
                                modifier = Modifier.weight(0.4f), enabled = !busy, singleLine = true,
                                label = { Text(copy.port) }
                            )
                        }
                    }
                }
            }

            // ── Firewall ────────────────────────────────────────────────────
            item { CommandSectionTitle(copy.securityFirewall, copy.securityFirewallBody) }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandPrimaryButton(
                            if (busy) copy.waitingForData else copy.securityInspect,
                            ::inspectFirewall,
                            enabled = !busy,
                            icon = Icons.Rounded.Shield
                        )
                        CommandResponsiveRow {
                            OutlinedTextField(
                                portToAllow, { portToAllow = it.filter(Char::isDigit).take(5) },
                                modifier = item(weight = 1f), enabled = !busy, singleLine = true,
                                label = { Text(copy.securityPortToAllow) }
                            )
                            CommandSecondaryButton(copy.securityAllow, {
                                val port = SecurityValidation.validatePort(portToAllow)
                                if (port == null) error = copy.securityBadPort else { pendingPort = port; pendingPortServerId = server?.id }
                            }, modifier = item(), enabled = !busy, icon = Icons.Rounded.Add)
                        }
                        if (firewallOutput.isNotBlank()) {
                            CommandRule()
                            Text(
                                firewallOutput,
                                color = CommandColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 24,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── fail2ban ────────────────────────────────────────────────────
            item {
                CommandSectionTitle(
                    copy.securityBans,
                    if (bannedFetched) "${banned.size}" else null,
                    if (busy) null else copy.refresh,
                    if (busy) null else ({ refreshBans() })
                )
            }
            if (!bannedFetched) {
                item { CommandEmptyState(copy.securityBans, copy.securityBansBody, copy.refresh, ::refreshBans) }
            } else if (banned.isEmpty() && error == null) {
                item { CommandStateBlock(copy.securityNoBans, copy.securityNoBansBody, CommandHealthTone.HEALTHY) }
            } else {
                items(banned, key = { "${it.jail}|${it.ip}" }) { entry ->
                    CommandSurface(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(CommandSpacing.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CommandStatusMark(
                                entry.ip,
                                CommandHealthTone.OFFLINE,
                                Modifier.weight(1f),
                                detail = entry.jail
                            )
                            CommandTextButton(copy.securityUnban, { pendingUnban = entry; pendingUnbanServerId = server?.id }, Icons.Rounded.LockOpen, enabled = !busy)
                        }
                    }
                }
            }

            if (notice != null) item { CommandStateBlock(copy.operationDone, notice ?: "", CommandHealthTone.HEALTHY) }
            if (busy) item {
                Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                    CommandLoadingState(copy.waitingForData)
                    CommandSecondaryButton(copy.cancel, ::cancel, modifier = Modifier.fillMaxWidth())
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE, copy.retry, retryAction) }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val portToConfirm = pendingPort
    if (portToConfirm != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pendingPort = null },
            title = { Text(copy.securityAllow) },
            text = { Text("${server?.name ?: copy.noServerSelected} · ${server?.host.orEmpty()}\nTCP $portToConfirm") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPort = null
                    allowPort(portToConfirm)
                }, enabled = !busy && server?.id == pendingPortServerId) { Text(copy.run, color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingPort = null }, enabled = !busy) { Text(copy.cancel) } }
        )
    }

    val unbanToConfirm = pendingUnban
    if (unbanToConfirm != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pendingUnban = null },
            title = { Text(copy.securityUnban) },
            text = { Text("${server?.name ?: copy.noServerSelected} · ${server?.host.orEmpty()}\n${unbanToConfirm.ip} · ${unbanToConfirm.jail}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingUnban = null
                    unban(unbanToConfirm)
                }, enabled = !busy && server?.id == pendingUnbanServerId) { Text(copy.run, color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingUnban = null }, enabled = !busy) { Text(copy.cancel) } }
        )
    }

    val currentPrompt = prompt
    if (currentPrompt != null) {
        CommandHostKeyDialog(copy, currentPrompt, onDecision = { approved ->
            prompt = null
            promptChannel.trySend(approved)
        })
    }
}
