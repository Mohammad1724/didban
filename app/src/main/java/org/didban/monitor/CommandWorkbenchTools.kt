package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
fun CommandDeveloperLabScreen(copy: CommandCopy, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(DeveloperTool.BASE64) }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun subnetErrorMessage(code: String): String = when (code) {
        "prefix" -> copy.subnetErrPrefix
        "ip" -> copy.subnetErrIp
        "dns" -> copy.subnetErrDns
        "ipv6" -> copy.subnetErrIpv6
        else -> copy.subnetErrFormat
    }

    fun runSubnet() {
        if (busy) return
        busy = true
        error = null
        output = ""
        scope.launch {
            try {
                val request = DevLabTools.parseSubnetRequest(input)
                val ip = withContext(Dispatchers.IO) { DevLabTools.resolveIpv4(request.host) }
                val result = DevLabTools.calculateSubnet("$ip/${request.prefix}")
                output = buildString {
                    if (!DevLabTools.looksLikeIpv4(request.host)) appendLine("${copy.subnetDomainLabel}: ${request.host}")
                    append(copy.subnetIpLabel + ": " + ip)
                    if (request.prefixAssumed) append(" " + copy.subnetAssumedPrefix)
                    appendLine()
                    appendLine("${copy.netNetwork}: ${result.network}")
                    appendLine("${copy.netBroadcast}: ${result.broadcast}")
                    appendLine("${copy.netFirstHost}: ${result.firstHost}")
                    appendLine("${copy.netLastHost}: ${result.lastHost}")
                    appendLine("${copy.netNetmask}: ${result.netmask}")
                    appendLine("${copy.netWildcard}: ${result.wildcard}")
                    appendLine("${copy.netUsableHosts}: ${result.usableHosts}")
                    append("${copy.netTotalHosts}: ${result.totalHosts}")
                }
            } catch (e: DevLabTools.SubnetInputException) {
                error = subnetErrorMessage(e.code)
            } catch (e: Exception) {
                error = e.message ?: copy.subnetErrFormat
            } finally {
                busy = false
            }
        }
    }

    fun execute() {
        if (selected == DeveloperTool.SUBNET) {
            runSubnet()
            return
        }
        error = null
        output = try {
            when (selected) {
                DeveloperTool.BASE64 -> buildString {
                    appendLine(copy.wtBase64Encode)
                    appendLine(DevLabTools.base64Encode(input))
                    appendLine()
                    appendLine(copy.wtBase64Decode)
                    appendLine(DevLabTools.base64Decode(input))
                    appendLine()
                    appendLine(copy.wtUrlEncode)
                    appendLine(DevLabTools.urlEncode(input))
                    appendLine()
                    appendLine(copy.wtUrlDecode)
                    appendLine(DevLabTools.urlDecode(input))
                }
                DeveloperTool.JSON -> "${copy.wtFormatted}\n${DevLabTools.formatJson(input)}\n\n${copy.wtMinified}\n${DevLabTools.minifyJson(input)}"
                DeveloperTool.HASH -> buildString {
                    appendLine("MD5  ${DevLabTools.hash(input, "MD5")}")
                    appendLine("SHA-256  ${DevLabTools.hash(input, "SHA-256")}")
                    appendLine("SHA-512  ${DevLabTools.hash(input, "SHA-512")}")
                    appendLine("${copy.wtHashDetected}: ${DevLabTools.identifyHash(input)}")
                }
                DeveloperTool.JWT -> DevLabTools.decodeJwt(input).let { result ->
                    "${copy.wtHeader}\n${result.header}\n\n${copy.wtPayload}\n${result.payload}\n\n${copy.wtExpired}: ${result.isExpired}\n${copy.wtExpiry}: ${result.expiryDate ?: copy.wtNotProvided}"
                }
                DeveloperTool.GENERATOR -> "${copy.genPasswordLabel}\n${DevLabTools.generatePassword()}\n\n${copy.genUuidLabel}\n${DevLabTools.generateUuid()}"
                DeveloperTool.SUBNET -> ""
            }
        } catch (e: Exception) {
            error = e.message ?: copy.operationFailed
            ""
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.developerLab, copy.wtLocalTools, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                DeveloperTool.values().forEach { tool ->
                    CommandSecondaryButton(tool.label(copy), { selected = tool; error = null }, enabled = selected != tool)
                }
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.wtInputTransform, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    if (selected == DeveloperTool.GENERATOR) {
                        Text(copy.genTitle, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Text(copy.devLabBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else if (selected == DeveloperTool.SUBNET) {
                        OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.uiInput) }, placeholder = { Text(copy.subnetPlaceholder) })
                        Text(copy.subnetHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(input, { input = it }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text(copy.uiInput) })
                    }
                    CommandPrimaryButton("${copy.runVerb} ${selected.label(copy)}", ::execute, icon = Icons.Rounded.PlayArrow, enabled = !busy)
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.wtInvalidInput, error ?: "", CommandHealthTone.OFFLINE, copy.retry, ::execute) }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.outputTitle, Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        CommandTextButton(copy.copyAction, { clipboard.setText(AnnotatedString(output)) }, Icons.Rounded.ContentCopy, enabled = output.isNotBlank())
                    }
                    Spacer(Modifier.height(CommandSpacing.sm))
                    Text(output.ifBlank { copy.waitingForData }, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandSinglePortScreen(copy: CommandCopy, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var bindPort by remember { mutableStateOf("443") }
    var inspectDelay by remember { mutableStateOf("5") }
    var panelDomain by remember { mutableStateOf("panel.example.com") }
    var panelPort by remember { mutableStateOf("10000") }
    var subDomain by remember { mutableStateOf("sub.example.com") }
    var subPort by remember { mutableStateOf("11000") }
    var realitySni by remember { mutableStateOf("yahoo.com") }
    var realityPort by remember { mutableStateOf("12000") }
    var fallbackPort by remember { mutableStateOf("13000") }
    var selectedArtifact by remember { mutableStateOf("HAProxy") }
    var output by remember { mutableStateOf("") }

    fun config() = SinglePortConfig(
        bindPort = bindPort.toIntOrNull()?.coerceIn(1, 65535) ?: 443,
        inspectDelaySec = inspectDelay.toIntOrNull()?.coerceIn(1, 60) ?: 5,
        panelDomain = panelDomain,
        panelLocalPort = panelPort.toIntOrNull()?.coerceIn(1, 65535) ?: 10000,
        subTlsDomain = subDomain,
        subTlsLocalPort = subPort.toIntOrNull()?.coerceIn(1, 65535) ?: 11000,
        realitySni = realitySni,
        realityLocalPort = realityPort.toIntOrNull()?.coerceIn(1, 65535) ?: 12000,
        fallbackLocalPort = fallbackPort.toIntOrNull()?.coerceIn(1, 65535) ?: 13000
    )

    fun generate() {
        val cfg = config()
        output = when (selectedArtifact) {
            "Bash deploy" -> SinglePortEngine.generateBashScript(cfg)
            "Docker Compose" -> SinglePortEngine.generateDockerCompose(cfg)
            else -> SinglePortEngine.generateHaproxyCfg(cfg)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.singlePort, copy.wtArtifactGenerator, modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.spIntroTitle, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Text(copy.spIntroBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.wtRoutingContract, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Text(copy.spFormHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(bindPort, { bindPort = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtBindPort) })
                        OutlinedTextField(inspectDelay, { inspectDelay = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtInspectSec) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(panelDomain, { panelDomain = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtPanelSni) })
                        OutlinedTextField(panelPort, { panelPort = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), singleLine = true, label = { Text(copy.spLocalPort) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(subDomain, { subDomain = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtSubscriptionSni) })
                        OutlinedTextField(subPort, { subPort = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), singleLine = true, label = { Text(copy.spLocalPort) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(realitySni, { realitySni = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtRealitySni) })
                        OutlinedTextField(realityPort, { realityPort = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), singleLine = true, label = { Text(copy.spLocalPort) })
                    }
                    OutlinedTextField(fallbackPort, { fallbackPort = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.wtFallbackPort) })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                listOf("HAProxy", "Bash deploy", "Docker Compose").forEach { artifact ->
                    CommandSecondaryButton(artifact, { selectedArtifact = artifact }, enabled = selectedArtifact != artifact)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                Text(copy.spArtifactHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                CommandPrimaryButton(copy.spGenerateVerb + " " + selectedArtifact, ::generate, icon = Icons.Rounded.Tune)
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(copy.wtGeneratedArtifact, Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        CommandTextButton(copy.copyAction, { clipboard.setText(AnnotatedString(output)) }, Icons.Rounded.ContentCopy, enabled = output.isNotBlank())
                    }
                    Spacer(Modifier.height(CommandSpacing.sm))
                    Text(copy.spNextHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(CommandSpacing.xs))
                    Text(output.ifBlank { copy.waitingForData }, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry))
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandProxyScreen(copy: CommandCopy, onBack: () -> Unit) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf("") }
    var subscription by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedProxyConfig?>(null) }
    var probe by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    var subscriptionInfo by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun inspectConfig() {
        error = null
        probe = null
        val config = ProxyEngine.parseConfig(uri)
        parsed = config
        if (config == null) { error = copy.vaultConfigUnparseable; return }
        busy = true
        scope.launch {
            probe = ProxyEngine.probeConfig(config)
            busy = false
        }
    }
    fun inspectSubscription() {
        if (subscription.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try { subscriptionInfo = ProxyEngine.fetchSubscription(subscription) }
            catch (e: Exception) {
                error = SecretRedactor.redact(e.message ?: "Subscription failed", listOf(subscription)).take(300)
            }
            finally { busy = false }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.proxy, "parse · probe · inspect", modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.wtSingleConfig, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(uri, { uri = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("VLESS / VMess / Trojan / SS URI") })
                    CommandPrimaryButton(if (busy) copy.waitingForData else copy.wtParseProbe, ::inspectConfig, enabled = !busy, icon = Icons.Rounded.Bolt)
                    parsed?.let { cfg ->
                        CommandStatusMark(if (probe?.second == true) "reachable" else if (probe != null) "unreachable" else "parsed", if (probe?.second == true) CommandHealthTone.HEALTHY else CommandHealthTone.UNKNOWN, detail = "${cfg.protocol} · ${cfg.host}:${cfg.port} · ${cfg.remark}")
                        probe?.let { result -> Text("TCP/TLS latency: ${if (result.first >= 0) "${result.first} ms" else "failed"}", color = CommandColors.textSecondary) }
                        CommandTextButton(copy.wtCopyNormalized, { SensitiveClipboard.copy(context, "Didban proxy configuration", cfg.rawUri) }, Icons.Rounded.ContentCopy)
                    }
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("Subscription", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(subscription, { subscription = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.wtSubscriptionUrl) })
                    CommandSecondaryButton(if (busy) copy.waitingForData else copy.wtFetchSubscription, ::inspectSubscription, enabled = !busy)
                    subscriptionInfo?.let { info ->
                        CommandStatusMark("${info.configs.size} ${copy.metricConfigs}", CommandHealthTone.INFO, detail = "${copy.metricUsed} ${info.usedFormatted} · ${copy.metricTotal} ${info.totalFormatted} · ${copy.metricExpire} ${info.expireDateFormatted}")
                        info.configs.take(20).forEach { cfg ->
                            Row(Modifier.fillMaxWidth().padding(vertical = CommandSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                                Text(cfg.remark, Modifier.weight(1f), color = CommandColors.textPrimary)
                                Text("${cfg.protocol} · ${cfg.host}:${cfg.port}", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }
}

@Composable
fun CommandSftpScreen(copy: CommandCopy, initialServer: ServerConfig?, onSelectServer: () -> Unit, onBack: () -> Unit) {
    SecureWindowEffect()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    fun refresh() {
        val target = server ?: return
        if (password.isBlank()) return
        loading = true
        error = null
        scope.launch {
            try {
                files = SftpEngine.listFiles(target.host, port.toIntOrNull() ?: 22, user.ifBlank { "root" }, password, path, true, SftpSortMode.NAME_ASC, hostKeyPolicy)
                status = copy.hostKeysStatus.replace("%d", files.size.toString())
            } catch (e: Exception) { error = e.message ?: "SFTP browse failed" }
            finally { loading = false }
        }
    }
    fun openItem(item: SftpFileItem) {
        val target = server ?: return
        if (item.isDirectory) {
            path = item.path
            refresh()
        } else {
            loading = true
            error = null
            scope.launch {
                try { content = SftpEngine.readFile(target.host, port.toIntOrNull() ?: 22, user.ifBlank { "root" }, password, item.path, hostKeyPolicy = hostKeyPolicy); activeFile = item.path }
                catch (e: Exception) { error = e.message ?: "File read failed" }
                finally { loading = false }
            }
        }
    }
    fun save() {
        val target = server ?: return
        val file = activeFile ?: return
        loading = true
        error = null
        scope.launch {
            try { SftpEngine.saveFile(target.host, port.toIntOrNull() ?: 22, user.ifBlank { "root" }, password, file, content, hostKeyPolicy); status = "Saved $file" }
            catch (e: Exception) { error = e.message ?: "File save failed" }
            finally { loading = false }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                            servers.forEach { item -> CommandSecondaryButton(item.name, { selectedId = item.id }, enabled = selectedId != item.id, modifier = Modifier.weight(1f)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(user, { user = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.uiUser) })
                            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), singleLine = true, label = { Text(copy.port) })
                        }
                        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.uiSshPassword) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                            OutlinedTextField(path, { path = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.wtRemotePath) })
                            CommandPrimaryButton(if (loading) copy.waitingForData else copy.refresh, ::refresh, enabled = !loading && server != null, icon = Icons.Rounded.Refresh)
                        }
                        Text(copy.sftpBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
            if (status != null) item { CommandStatusMark(status ?: "", CommandHealthTone.INFO) }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        Text(copy.wtRemoteEntries, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        if (files.isEmpty()) Text(copy.waitingForData, color = CommandColors.textSecondary)
                        files.forEach { item ->
                            Row(Modifier.fillMaxWidth().clickable { openItem(item) }.padding(vertical = CommandSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (item.isDirectory) "DIR" else "FILE", color = if (item.isDirectory) CommandColors.info else CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                                Text(item.name, Modifier.weight(1f), color = CommandColors.textPrimary)
                                Text(item.formattedSize, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (activeFile != null) item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(activeFile ?: "", Modifier.weight(1f), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            CommandTextButton("Copy", { if (content.isNotEmpty()) SensitiveClipboard.copy(context, "Didban remote file", content) }, Icons.Rounded.ContentCopy)
                        }
                        OutlinedTextField(content, { content = it }, Modifier.fillMaxWidth().height(280.dp), textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry), label = { Text(copy.wtTextEditorMax) })
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
            scope.launch { promptChannel.send(approved) }
        })
    }
}
