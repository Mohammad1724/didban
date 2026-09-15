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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

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

    fun inspectFirewall() {
        if (!guard() || busy) return
        val target = server ?: return
        val port = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        busy = true
        error = null
        notice = null
        scope.launch {
            val res = SshEngine.execute(
                target.host, port, "root", password,
                "ufw status verbose 2>/dev/null || iptables -L -n -v | head -n 25",
                15, policy
            )
            busy = false
            firewallOutput = res.stdout.ifBlank { res.stderr }
            if (!res.isSuccess && firewallOutput.isBlank()) {
                error = res.errorMessage ?: res.stderr
            }
        }
    }

    fun allowPort() {
        if (!guard() || busy) return
        val target = server ?: return
        val port = SecurityValidation.validatePort(portToAllow)
        if (port == null) {
            error = copy.securityBadPort
            return
        }
        val sshPortNum = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        busy = true
        error = null
        notice = null
        scope.launch {
            // The port is already validated as digits only; the rule argument
            // is quoted again as defence in depth.
            val rule = SecurityValidation.shellQuote("$port/tcp")
            val res = SshEngine.execute(
                target.host, sshPortNum, "root", password,
                "ufw allow $rule && ufw reload", 15, policy
            )
            busy = false
            if (res.isSuccess) {
                notice = copy.securityPortOpened.replace("%d", port.toString())
                portToAllow = ""
                inspectFirewall()
            } else {
                error = res.stderr.ifBlank { res.errorMessage ?: "exit ${res.exitCode}" }
            }
        }
    }

    fun refreshBans() {
        if (!guard() || busy) return
        val target = server ?: return
        val port = sshPort.toIntOrNull()?.coerceIn(1, 65535) ?: 22
        busy = true
        error = null
        notice = null
        scope.launch {
            val jailsRes = SshEngine.execute(
                target.host, port, "root", password,
                "fail2ban-client status 2>/dev/null", 15, policy
            )
            if (!jailsRes.isSuccess) {
                busy = false
                bannedFetched = true
                banned.clear()
                val out = (jailsRes.stdout + jailsRes.stderr).trim()
                error = if (out.contains("not found") || jailsRes.stdout.isBlank()) {
                    copy.securityNoFail2ban
                } else {
                    out.take(160)
                }
                return@launch
            }
            val items = mutableListOf<BannedIpItem>()
            for (jail in OutputParsers.fail2banJails(jailsRes.stdout)) {
                val stRes = SshEngine.execute(
                    target.host, port, "root", password,
                    "fail2ban-client status ${SecurityValidation.shellQuote(jail)} 2>/dev/null",
                    15, policy
                )
                if (!stRes.isSuccess) continue
                OutputParsers.fail2banBannedIps(stRes.stdout).forEach { ip ->
                    items.add(BannedIpItem(ip, jail, ""))
                }
            }
            banned.clear()
            banned.addAll(items)
            bannedFetched = true
            busy = false
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
        busy = true
        error = null
        notice = null
        scope.launch {
            val jail = SecurityValidation.shellQuote(item.jail)
            val ip = SecurityValidation.shellQuote(item.ip)
            val res = SshEngine.execute(
                target.host, port, "root", password,
                "fail2ban-client set $jail unbanip $ip", 15, policy
            )
            busy = false
            if (res.isSuccess) {
                notice = copy.securityUnbanned.replace("%s", item.ip)
                refreshBans()
            } else {
                error = res.stderr.ifBlank { res.errorMessage ?: "exit ${res.exitCode}" }
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
                                    enabled = selectedId != candidate.id,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                password, { password = it },
                                modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text(copy.securitySshPassword) },
                                visualTransformation = PasswordVisualTransformation()
                            )
                            OutlinedTextField(
                                sshPort, { sshPort = it.filter(Char::isDigit).take(5) },
                                modifier = Modifier.weight(0.4f), singleLine = true,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                portToAllow, { portToAllow = it.filter(Char::isDigit).take(5) },
                                modifier = Modifier.weight(1f), singleLine = true,
                                label = { Text(copy.securityPortToAllow) }
                            )
                            CommandSecondaryButton(copy.securityAllow, ::allowPort, enabled = !busy, icon = Icons.Rounded.Add)
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
                            CommandTextButton(copy.securityUnban, { unban(entry) }, Icons.Rounded.LockOpen, enabled = !busy)
                        }
                    }
                }
            }

            if (notice != null) item { CommandStateBlock(copy.operationDone, notice ?: "", CommandHealthTone.HEALTHY) }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (prompt != null) {
        CommandHostKeyDialog(prompt!!, onDecision = { approved ->
            prompt = null
            scope.launch { promptChannel.send(approved) }
        })
    }
}
