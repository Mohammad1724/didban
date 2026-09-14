package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 3 · item 3-D — tunnel add/edit form + code viewer (v3 context
 * dialogs). 1:1 port of the audited legacy TunnelFormDialog /
 * ViewTunnelCodeDialog, including the per-core transport auto-selection
 * (LaunchedEffect), the M17 discovered-token blank rule and the H3/H4
 * code-generation guarantees (persist on first use, show validation
 * errors instead of fabricated code).
 *
 * UI-only: verified by code review (no Android/Compose compile here).
 */

// ── Tunnel Form Dialog ──────────────────────────────────────────────────────

@Composable
fun AeroTunnelFormDialog(
    t: Str,
    existing: TunnelConfig?,
    onDismiss: () -> Unit,
    onSave: (TunnelConfig) -> Unit
) {
    val ctx = LocalContext.current
    val servers = remember { Prefs.loadServers(ctx) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKPACK) }
    var transport by remember { mutableStateOf(existing?.transport ?: TunnelTransport.STEALTH) }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var multiPorts by remember { mutableStateOf(existing?.multiPorts ?: "443:8443, 2096:2096") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    // M17: a discovered tunnel owns no credential — do NOT prefill a fresh
    // random token (that would silently convert "unknown" into "invented").
    // It stays blank until the user enters the real one; the deploy gate
    // blocks deploys until then.
    var token by remember {
        mutableStateOf(
            when {
                existing == null -> TunnelEngine.generateRandomToken(24)
                existing.token.isNotBlank() -> existing.token
                existing.discovered -> ""
                else -> TunnelEngine.generateRandomToken(24)
            }
        )
    }
    var preset by remember { mutableStateOf(existing?.preset ?: "turbo") }
    var kcpMode by remember { mutableStateOf(existing?.kcpMode ?: "fast") }
    var encryption by remember { mutableStateOf(existing?.encryption ?: "aes-128-gcm") }
    var spoofSrcIp by remember { mutableStateOf(existing?.spoofSrcIp ?: "1.1.1.1") }
    var spoofPeerIp by remember { mutableStateOf(existing?.spoofPeerIp ?: "8.8.8.8") }
    var virtualIpIran by remember { mutableStateOf(existing?.virtualIpIran ?: "10.200.200.2") }
    var virtualIpKharej by remember { mutableStateOf(existing?.virtualIpKharej ?: "10.200.200.1") }
    var mtu by remember { mutableStateOf((existing?.mtu ?: 1350).toString()) }
    var acceptUdp by remember { mutableStateOf(existing?.acceptUdp ?: true) }
    var proxyProtocol by remember { mutableStateOf(existing?.proxyProtocol ?: false) }
    var autoSync by remember { mutableStateOf(existing?.autoSync ?: true) }
    var iranServerId by remember { mutableStateOf<Long?>(existing?.iranServerId) }
    var foreignServerId by remember { mutableStateOf<Long?>(existing?.foreignServerId) }

    // Auto-update transport options when core changes
    LaunchedEffect(core) {
        when (core) {
            TunnelCore.BACKPACK -> if (transport !in listOf(TunnelTransport.STEALTH, TunnelTransport.PCK, TunnelTransport.KCP_FEC, TunnelTransport.TCP, TunnelTransport.UDP)) transport = TunnelTransport.STEALTH
            TunnelCore.PAQET -> transport = TunnelTransport.RAW_KCP
            TunnelCore.NARNIA -> transport = TunnelTransport.ICMP_CHACHA
            TunnelCore.SPOOF_TUNNEL -> if (transport !in listOf(TunnelTransport.IP_SPOOF_UDP, TunnelTransport.IP_SPOOF_ICMP)) transport = TunnelTransport.IP_SPOOF_UDP
            TunnelCore.BACKHAUL -> if (transport !in listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.WSMUX, TunnelTransport.TCPMUX)) transport = TunnelTransport.TCP
            TunnelCore.RATHOLE -> transport = TunnelTransport.TCP
            TunnelCore.GOST -> if (transport !in listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.GRPC, TunnelTransport.TCPMUX, TunnelTransport.UDP)) transport = TunnelTransport.TCP
            TunnelCore.CHISEL -> transport = TunnelTransport.WS
            TunnelCore.FRP -> transport = TunnelTransport.TCP
            TunnelCore.IPTABLES -> transport = TunnelTransport.TCP
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(450.dp).imePadding()
            ) {
                // Core Engine Selection
                item {
                    Text("1. هسته تانل (Tunnel Core Engine)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                    Spacer(Modifier.height(4.dp))
                    FilterChipRow(
                        items = TunnelCore.values().map { it.displayName },
                        selectedIndex = TunnelCore.values().indexOf(core),
                        onSelect = { core = TunnelCore.values()[it] }
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(core.description, fontSize = 10.5.sp, color = Ds.textSecondary, lineHeight = 15.sp)
                }

                item { Hairline() }

                // Basic Identification
                item {
                    InputField(
                        value = name,
                        onValueChange = { name = it },
                        label = "نام دلخواه تانل",
                        placeholder = "مثال: تانل تهران به فرانکفورت (BackPack)"
                    )
                }

                // Quick Node Selectors (if registered servers exist)
                if (servers.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("انتخاب سریع سرورهای دیدبان:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Ds.textTertiary)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                servers.forEach { s ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (iranServerId == s.id || foreignServerId == s.id) Ds.accentDim else Ds.surfaceLow,
                                        border = BorderStroke(1.dp, if (iranServerId == s.id || foreignServerId == s.id) Ds.accent else Ds.hairline),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                            if (iranHost.isBlank()) {
                                                iranHost = s.host
                                                iranServerId = s.id
                                            } else if (foreignHost.isBlank()) {
                                                foreignHost = s.host
                                                foreignServerId = s.id
                                            } else {
                                                iranHost = s.host
                                                iranServerId = s.id
                                            }
                                        }
                                    ) {
                                        Text(
                                            "${s.name} (${s.host})",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            color = Ds.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    InputField(
                        value = iranHost,
                        onValueChange = { iranHost = it },
                        label = "آدرس سرور ایران (Relay IP / Domain)",
                        placeholder = "IP سرور ایران"
                    )
                }

                item {
                    InputField(
                        value = foreignHost,
                        onValueChange = { foreignHost = it },
                        label = "آدرس سرور خارج (Upstream IP / Domain)",
                        placeholder = "IP سرور خارج"
                    )
                }

                item {
                    InputField(
                        value = multiPorts,
                        onValueChange = { multiPorts = it },
                        label = "نگاشت پورت‌ها (ایران:خارج)",
                        placeholder = "e.g. 443:8443, 2096:2096, 80:8080"
                    )
                }

                item {
                    InputField(
                        value = corePort,
                        onValueChange = { corePort = it },
                        label = "پورت ارتباطی هسته تانل (Core Port)",
                        placeholder = "3080"
                    )
                }

                // Security Token with Generator
                item {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("توکن امنیتی (PSK Token)", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
                            Text(
                                "تولید توکن قوی",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ds.accent,
                                modifier = Modifier.clickable {
                                    token = TunnelEngine.generateRandomToken(24)
                                }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MonoTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = "",
                            placeholder = "Encryption Key / Token"
                        )
                        if (existing?.discovered == true && token.isBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "این تانل کشف‌شده است و توکن واقعی‌اش ناشناخته است — برای deploy، توکن واقعی را وارد کنید.",
                                fontSize = 10.5.sp,
                                color = Ds.warn
                            )
                        }
                    }
                }

                item { Hairline() }

                // Core Specific Controls
                when (core) {
                    TunnelCore.BACKPACK -> {
                        item {
                            Text("پروتکل انتقال BackPack:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            Spacer(Modifier.height(4.dp))
                            val bpTransports = listOf(TunnelTransport.STEALTH, TunnelTransport.PCK, TunnelTransport.KCP_FEC, TunnelTransport.TCP, TunnelTransport.UDP)
                            FilterChipRow(
                                items = bpTransports.map { it.displayName },
                                selectedIndex = bpTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = bpTransports[it] }
                            )
                        }

                        item {
                            Text("پریست سرعت (Preset):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                            val presets = listOf("turbo", "balance", "aggressive", "gaming")
                            FilterChipRow(
                                items = presets,
                                selectedIndex = presets.indexOf(preset).coerceAtLeast(0),
                                onSelect = { preset = presets[it] }
                            )
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("انتقال ترافیک UDP", fontSize = 12.sp, color = Ds.textPrimary)
                                Switch(
                                    checked = acceptUdp,
                                    onCheckedChange = { acceptUdp = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                                )
                            }
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("پشتیبانی از Proxy Protocol v2", fontSize = 12.sp, color = Ds.textPrimary)
                                Switch(
                                    checked = proxyProtocol,
                                    onCheckedChange = { proxyProtocol = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                                )
                            }
                        }
                    }

                    TunnelCore.PAQET -> {
                        item {
                            Text("مود KCP در Paqet:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val kcpModes = listOf("fast", "fast2", "fast3", "normal")
                            FilterChipRow(
                                items = kcpModes,
                                selectedIndex = kcpModes.indexOf(kcpMode).coerceAtLeast(0),
                                onSelect = { kcpMode = kcpModes[it] }
                            )
                        }

                        item {
                            Text("نوع رمزنگاری (Encryption):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                            val encs = listOf("aes-128-gcm", "aes-256-gcm", "chacha20-poly1305", "none")
                            FilterChipRow(
                                items = encs,
                                selectedIndex = encs.indexOf(encryption).coerceAtLeast(0),
                                onSelect = { encryption = encs[it] }
                            )
                        }

                        item {
                            InputField(value = mtu, onValueChange = { mtu = it }, label = "MTU سایز پکت‌ها", placeholder = "1350")
                        }
                    }

                    TunnelCore.NARNIA -> {
                        item {
                            InputField(value = virtualIpIran, onValueChange = { virtualIpIran = it }, label = "IP مجازی سرور ایران", placeholder = "10.200.200.2")
                        }
                        item {
                            InputField(value = virtualIpKharej, onValueChange = { virtualIpKharej = it }, label = "IP مجازی سرور خارج", placeholder = "10.200.200.1")
                        }
                        item {
                            InputField(value = mtu, onValueChange = { mtu = it }, label = "MTU تانل ICMP", placeholder = "1350")
                        }
                    }

                    TunnelCore.SPOOF_TUNNEL -> {
                        item {
                            Text("پروتکل جعل IP:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val spoofTransports = listOf(TunnelTransport.IP_SPOOF_UDP, TunnelTransport.IP_SPOOF_ICMP)
                            FilterChipRow(
                                items = spoofTransports.map { it.displayName },
                                selectedIndex = spoofTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = spoofTransports[it] }
                            )
                        }

                        item {
                            InputField(value = spoofSrcIp, onValueChange = { spoofSrcIp = it }, label = "IP جعلی مبدا (Spoofed Source IP)", placeholder = "1.1.1.1")
                        }
                        item {
                            InputField(value = spoofPeerIp, onValueChange = { spoofPeerIp = it }, label = "IP جعلی مقصد (Spoofed Peer IP)", placeholder = "8.8.8.8")
                        }
                    }

                    TunnelCore.BACKHAUL, TunnelCore.GOST -> {
                        item {
                            Text("پروتکل انتقال:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.accent)
                            val genericTransports = listOf(TunnelTransport.TCP, TunnelTransport.WS, TunnelTransport.WSMUX, TunnelTransport.TCPMUX, TunnelTransport.GRPC)
                            FilterChipRow(
                                items = genericTransports.map { it.displayName },
                                selectedIndex = genericTransports.indexOf(transport).coerceAtLeast(0),
                                onSelect = { transport = genericTransports[it] }
                            )
                        }
                    }

                    else -> {}
                }

                item { Hairline() }

                // Auto-Deploy Switch
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("استقرار خودکار دیدبان (Zero-Touch Auto-Deploy)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("نصب خودکار سرویس و اجرای تانل روی هر دو سرور با یک کلیک", fontSize = 10.5.sp, color = Ds.textTertiary)
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = { autoSync = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ds.accent, checkedThumbColor = Ds.onAccent)
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = t.save,
                onClick = {
                    if (name.isNotBlank()) {
                        val firstPort = run {
                            val tok = multiPorts.split(',', ';', ' ', '\n', '\t').map { it.trim() }.firstOrNull { it.isNotEmpty() }
                            if (tok != null && (tok.contains(':') || tok.contains('='))) {
                                val delim = if (tok.contains(':')) ':' else '='
                                val parts = tok.split(delim)
                                val ip = parts[0].trim().toIntOrNull() ?: 443
                                val fp = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ip
                                PortMapping(ip, fp)
                            } else {
                                val p = tok?.toIntOrNull() ?: 443
                                PortMapping(p, p)
                            }
                        }
                        val newTun = existing?.apply {
                            this.name = name.trim()
                            this.core = core
                            this.transport = transport
                            this.iranHost = iranHost.trim()
                            this.iranPort = firstPort.iranPort
                            this.foreignHost = foreignHost.trim()
                            this.foreignPort = firstPort.foreignPort
                            this.corePort = corePort.toIntOrNull() ?: 3080
                            this.token = token.trim()
                            this.preset = preset
                            this.kcpMode = kcpMode
                            this.encryption = encryption
                            this.spoofSrcIp = spoofSrcIp.trim()
                            this.spoofPeerIp = spoofPeerIp.trim()
                            this.virtualIpIran = virtualIpIran.trim()
                            this.virtualIpKharej = virtualIpKharej.trim()
                            this.mtu = mtu.toIntOrNull() ?: 1350
                            this.acceptUdp = acceptUdp
                            this.proxyProtocol = proxyProtocol
                            this.multiPorts = multiPorts.trim()
                            this.autoSync = autoSync
                            this.iranServerId = iranServerId
                            this.foreignServerId = foreignServerId
                        } ?: TunnelConfig(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            core = core,
                            transport = transport,
                            iranHost = iranHost.trim(),
                            iranPort = firstPort.iranPort,
                            foreignHost = foreignHost.trim(),
                            foreignPort = firstPort.foreignPort,
                            corePort = corePort.toIntOrNull() ?: 3080,
                            token = token.trim(),
                            preset = preset,
                            kcpMode = kcpMode,
                            encryption = encryption,
                            spoofSrcIp = spoofSrcIp.trim(),
                            spoofPeerIp = spoofPeerIp.trim(),
                            virtualIpIran = virtualIpIran.trim(),
                            virtualIpKharej = virtualIpKharej.trim(),
                            mtu = mtu.toIntOrNull() ?: 1350,
                            acceptUdp = acceptUdp,
                            proxyProtocol = proxyProtocol,
                            multiPorts = multiPorts.trim(),
                            autoSync = autoSync,
                            iranServerId = iranServerId,
                            foreignServerId = foreignServerId
                        )
                        onSave(newTun)
                    }
                }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

// ── View Codes & Docker Dialog ──────────────────────────────────────────────

@Composable
fun AeroViewTunnelCodeDialog(
    t: Str,
    tunnel: TunnelConfig,
    onDismiss: () -> Unit
) {
    val code = remember(tunnel) {
        try {
            TunnelEngine.generateCode(tunnel)
        } catch (e: IllegalArgumentException) {
            // H4: invalid field values - show the reason instead of code.
            GeneratedTunnelCode(
                iranConfig = "خطای اعتبارسنجی:\n${e.message}",
                iranInstallCommand = "خطای اعتبارسنجی:\n${e.message}",
                foreignConfig = "خطای اعتبارسنجی:\n${e.message}",
                foreignInstallCommand = "خطای اعتبارسنجی:\n${e.message}",
                dockerComposeIran = "",
                dockerComposeForeign = "",
                description = "فیلدهای تانل را اصلاح کنید؛ تا آن زمان deploy نمی‌شود."
            )
        }
    }
    // generateCode may materialize the tunnel secret on first use (H3):
    // persist it immediately so a copied/redeployed command always matches.
    val dlgCtx = LocalContext.current
    LaunchedEffect(code) { TunnelEngine.persistTunnel(dlgCtx, tunnel) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Iran, 1: Foreign, 2: Docker

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tunnel.core.displayName} Commands & Docker", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentedControl(
                    items = listOf("🇮🇷 Iran", "🌍 Foreign", "🐳 Docker"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )

                val contentToShow = when (selectedTab) {
                    0 -> code.iranInstallCommand
                    1 -> code.foreignInstallCommand
                    else -> "=== docker-compose-iran.yml ===\n${code.dockerComposeIran}\n\n=== docker-compose-foreign.yml ===\n${code.dockerComposeForeign}"
                }

                TerminalBox(
                    command = contentToShow,
                    title = when (selectedTab) { 0 -> "Iran Command"; 1 -> "Foreign Command"; else -> "Docker Compose" }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.close) }
        }
    )
}