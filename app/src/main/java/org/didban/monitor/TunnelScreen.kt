@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TunnelScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var tunnels by remember { mutableStateOf<List<TunnelConfig>>(Prefs.loadTunnels(ctx)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var deletingTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var viewCodeTunnel by remember { mutableStateOf<TunnelConfig?>(null) }
    var testingTunnelId by remember { mutableStateOf<Long?>(null) }
    var operatingTunnelId by remember { mutableStateOf<Long?>(null) }
    var showGuide by remember { mutableStateOf(false) }
    var selectedFilterCore by remember { mutableStateOf<TunnelCore?>(null) }

    fun save() {
        Prefs.saveTunnels(ctx, tunnels)
        tunnels = tunnels.toList()
    }

    // Auto health check loop
    LaunchedEffect(Unit) {
        while (true) {
            for (tun in tunnels) {
                if (tun.isEnabled && (tun.iranHost.isNotBlank() || tun.foreignHost.isNotBlank())) {
                    val (ok, lat) = TunnelEngine.testTunnel(tun)
                    tun.lastStatus = if (ok) 1 else 0
                    tun.lastLatencyMs = lat
                    tun.lastChecked = System.currentTimeMillis()
                }
            }
            save()
            delay(25_000)
        }
    }

    val totalTunnels = tunnels.size
    val activeTunnels = tunnels.count { it.lastStatus == 1 }
    val uniqueCores = tunnels.map { it.core }.distinct().size

    val filteredTunnels = remember(tunnels, selectedFilterCore) {
        if (selectedFilterCore == null) tunnels else tunnels.filter { it.core == selectedFilterCore }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. Page Header ──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = Icons.Rounded.SwapHoriz, tint = Ds.accent, background = Ds.accentDim, size = 36.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(t.tunnelsHub, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                        Text("Dual-Node Iran ➔ Kharej", fontSize = 11.sp, color = Ds.textTertiary)
                    }
                }
                PrimaryButton(
                    text = t.addTunnel,
                    icon = Icons.Rounded.Add,
                    onClick = { showAddDialog = true },
                    modifier = Modifier.height(38.dp)
                )
            }
        }

        // ── 2. Bento Stat Summary ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BentoMetricCard(
                    title = "Total Tunnels",
                    value = "$totalTunnels",
                    icon = Icons.Rounded.AltRoute,
                    tone = Ds.accent,
                    modifier = Modifier.weight(1f)
                )
                BentoMetricCard(
                    title = "Connected",
                    value = "$activeTunnels",
                    icon = Icons.Rounded.CheckCircle,
                    tone = Ds.ok,
                    modifier = Modifier.weight(1f)
                )
                BentoMetricCard(
                    title = "Active Cores",
                    value = "$uniqueCores",
                    icon = Icons.Rounded.Security,
                    tone = Ds.violet,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 3. Engine Filter Chips Bar ──
        item {
            FilterChipRow(
                items = listOf("All (${tunnels.size})") + listOf(
                    TunnelCore.BACKPACK,
                    TunnelCore.PAQET,
                    TunnelCore.NARNIA,
                    TunnelCore.SPOOF_TUNNEL,
                    TunnelCore.BACKHAUL,
                    TunnelCore.RATHOLE,
                    TunnelCore.GOST
                ).map { it.displayName },
                selectedIndex = if (selectedFilterCore == null) 0 else {
                    val idx = listOf(
                        TunnelCore.BACKPACK,
                        TunnelCore.PAQET,
                        TunnelCore.NARNIA,
                        TunnelCore.SPOOF_TUNNEL,
                        TunnelCore.BACKHAUL,
                        TunnelCore.RATHOLE,
                        TunnelCore.GOST
                    ).indexOf(selectedFilterCore)
                    if (idx >= 0) idx + 1 else 0
                },
                onSelect = { idx ->
                    selectedFilterCore = if (idx == 0) null else {
                        listOf(
                            TunnelCore.BACKPACK,
                            TunnelCore.PAQET,
                            TunnelCore.NARNIA,
                            TunnelCore.SPOOF_TUNNEL,
                            TunnelCore.BACKHAUL,
                            TunnelCore.RATHOLE,
                            TunnelCore.GOST
                        ).getOrNull(idx - 1)
                    }
                }
            )
        }

        // ── 4. Guide Banner ──
        item {
            BannerCard(
                text = t.guideTunnels,
                tone = BannerTone.Info,
                icon = Icons.Rounded.Info
            )
        }

        // ── 5. Empty State or Tunnel Cards ──
        if (filteredTunnels.isEmpty()) {
            item {
                EmptyState(
                    title = "No Tunnels Configured",
                    hint = "Create your first high-speed dual-node tunnel between Iran and foreign servers.",
                    radar = true,
                    actionLabel = t.addTunnel,
                    onAction = { showAddDialog = true }
                )
            }
        } else {
            items(filteredTunnels, key = { it.id }) { tunnel ->
                val isOnline = tunnel.lastStatus == 1
                val isTesting = testingTunnelId == tunnel.id
                val isOperating = operatingTunnelId == tunnel.id

                ModernCard(
                    padding = 16.dp,
                    cornerRadius = 18.dp
                ) {
                    // Header: Core badge + Name + Status
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = tunnel.core.displayName,
                                level = StatusLevel.Info,
                                pulse = false
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tunnel.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Ds.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(
                                text = if (isOnline) "${tunnel.lastLatencyMs.toInt()} ms" else "Offline",
                                level = if (isOnline) StatusLevel.Ok else StatusLevel.Danger,
                                pulse = isOnline
                            )
                            Spacer(Modifier.width(6.dp))
                            Switch(
                                checked = tunnel.isEnabled,
                                onCheckedChange = {
                                    tunnel.isEnabled = it
                                    save()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Ds.accent,
                                    checkedThumbColor = Ds.onAccent,
                                    uncheckedTrackColor = Ds.surfaceHighlight
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Dual Node Visual Flow: Iran ➔ Kharej ──
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Ds.surfaceLow,
                        border = BorderStroke(1.dp, Ds.hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Iran Node
                            Column(Modifier.weight(1f)) {
                                Text("🇮🇷 Iran Relay", fontSize = 10.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    tunnel.iranHost.ifBlank { "0.0.0.0" } + ":${tunnel.iranPort}",
                                    fontSize = 11.5.sp,
                                    fontFamily = Telemetry,
                                    fontWeight = FontWeight.Bold,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Center bridge indicator
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    tunnel.transport.name,
                                    fontSize = 9.sp,
                                    fontFamily = Telemetry,
                                    color = Ds.accent,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Ds.accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Foreign Node
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🌍 Foreign Upstream", fontSize = 10.sp, color = Ds.textTertiary, fontWeight = FontWeight.SemiBold)
                                Text(
                                    tunnel.foreignHost.ifBlank { "Remote" } + ":${tunnel.foreignPort}",
                                    fontSize = 11.5.sp,
                                    fontFamily = Telemetry,
                                    fontWeight = FontWeight.Bold,
                                    color = Ds.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Multi-port or details badge if present
                    if (tunnel.multiPorts.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Multi-Port Forwarding: ${tunnel.multiPorts}",
                            fontSize = 10.5.sp,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    Spacer(Modifier.height(8.dp))

                    // ── Actions Row ──
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SoftButton(
                                text = "Code & Docker",
                                icon = Icons.Rounded.Terminal,
                                onClick = { viewCodeTunnel = tunnel }
                            )

                            if (tunnel.autoSync) {
                                SoftButton(
                                    text = if (isOperating) "Syncing…" else "Auto-Deploy",
                                    icon = Icons.Rounded.CloudSync,
                                    tone = Ds.ok,
                                    enabled = !isOperating,
                                    onClick = {
                                        operatingTunnelId = tunnel.id
                                        scope.launch {
                                            val ok = TunnelEngine.applyTunnelToAgents(ctx, tunnel)
                                            if (ok) {
                                                Toast.makeText(ctx, "Tunnel deployed to agents successfully!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(ctx, "Auto-deploy failed. Check agent connection.", Toast.LENGTH_SHORT).show()
                                            }
                                            operatingTunnelId = null
                                        }
                                    }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CircleIconButton(
                                icon = Icons.Rounded.Speed,
                                contentDescription = "Test Latency",
                                tint = Ds.accent,
                                size = 32.dp,
                                onClick = {
                                    testingTunnelId = tunnel.id
                                    scope.launch {
                                        val (ok, lat) = TunnelEngine.testTunnel(tunnel)
                                        tunnel.lastStatus = if (ok) 1 else 0
                                        tunnel.lastLatencyMs = lat
                                        tunnel.lastChecked = System.currentTimeMillis()
                                        save()
                                        testingTunnelId = null
                                    }
                                }
                            )
                            CircleIconButton(
                                icon = Icons.Rounded.Edit,
                                contentDescription = "Edit",
                                tint = Ds.textSecondary,
                                size = 32.dp,
                                onClick = { editingTunnel = tunnel }
                            )
                            CircleIconButton(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete",
                                tint = Ds.danger,
                                size = 32.dp,
                                onClick = { deletingTunnel = tunnel }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // ── Add/Edit Tunnel Dialog ──
    if (showAddDialog || editingTunnel != null) {
        val target = editingTunnel
        TunnelFormDialog(
            t = t,
            existing = target,
            onDismiss = {
                showAddDialog = false
                editingTunnel = null
            },
            onSave = { updated ->
                val list = tunnels.toMutableList()
                val idx = list.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    list[idx] = updated
                } else {
                    list.add(updated)
                }
                tunnels = list
                save()
                showAddDialog = false
                editingTunnel = null
            }
        )
    }

    // ── View Codes & Docker Dialog ──
    viewCodeTunnel?.let { tunnel ->
        ViewTunnelCodeDialog(
            t = t,
            tunnel = tunnel,
            onDismiss = { viewCodeTunnel = null }
        )
    }

    // ── Delete Confirmation Dialog ──
    deletingTunnel?.let { tunnel ->
        AlertDialog(
            onDismissRequest = { deletingTunnel = null },
            title = { Text(t.deleteTunnel, fontWeight = FontWeight.Bold) },
            text = { Text("Delete tunnel “${tunnel.name}”?") },
            confirmButton = {
                TextButton(onClick = {
                    tunnels = tunnels.filterNot { it.id == tunnel.id }
                    save()
                    deletingTunnel = null
                }) {
                    Text(t.delete, color = Ds.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTunnel = null }) { Text(t.cancel) }
            }
        )
    }
}

// ── Tunnel Form Dialog ──────────────────────────────────────────────────────

@Composable
private fun TunnelFormDialog(
    t: Str,
    existing: TunnelConfig?,
    onDismiss: () -> Unit,
    onSave: (TunnelConfig) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKPACK) }
    var transport by remember { mutableStateOf(existing?.transport ?: TunnelTransport.TCP) }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var multiPorts by remember { mutableStateOf(existing?.multiPorts ?: "443:8443, 2096:2096") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    var token by remember { mutableStateOf(existing?.token ?: TunnelEngine.generateRandomToken(24)) }
    var preset by remember { mutableStateOf(existing?.preset ?: "balanced") }
    var kcpMode by remember { mutableStateOf(existing?.kcpMode ?: "fast") }
    var encryption by remember { mutableStateOf(existing?.encryption ?: "chacha20-poly1305") }
    var autoSync by remember { mutableStateOf(existing?.autoSync ?: false) }

    val servers = remember { Prefs.loadServers(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp).imePadding()
            ) {
                item {
                    Text("Tunnel Core Engine", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ds.textSecondary)
                    Spacer(Modifier.height(4.dp))
                    FilterChipRow(
                        items = TunnelCore.values().map { it.displayName },
                        selectedIndex = TunnelCore.values().indexOf(core),
                        onSelect = { core = TunnelCore.values()[it] }
                    )
                }

                item { InputField(value = name, onValueChange = { name = it }, label = "Tunnel Name", placeholder = "e.g. Tehran-Frankfurt BackPack") }
                item { InputField(value = iranHost, onValueChange = { iranHost = it }, label = "Iran Node Host / IP", placeholder = "Iran Server IP or Domain") }
                item { InputField(value = foreignHost, onValueChange = { foreignHost = it }, label = "Foreign Node Host / IP", placeholder = "Foreign Server IP or Domain") }
                item { InputField(value = multiPorts, onValueChange = { multiPorts = it }, label = "Port Forwarding Map", placeholder = "e.g. 443:8443, 2096:2096") }
                item { InputField(value = corePort, onValueChange = { corePort = it }, label = "Core Tunnel Port", placeholder = "3080") }
                item { InputField(value = token, onValueChange = { token = it }, label = "Security Token / PSK", placeholder = "Encryption Key") }

                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Agent Zero-Touch Auto-Deploy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
                            Text("Automatically installs and runs the tunnel on Didban agents", fontSize = 10.5.sp, color = Ds.textTertiary)
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
                            val token = multiPorts.split(',', ';', ' ', '\n', '\t').map { it.trim() }.firstOrNull { it.isNotEmpty() }
                            if (token != null && (token.contains(':') || token.contains('='))) {
                                val delim = if (token.contains(':')) ':' else '='
                                val parts = token.split(delim)
                                val ip = parts[0].trim().toIntOrNull() ?: 443
                                val fp = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ip
                                PortMapping(ip, fp)
                            } else {
                                val p = token?.toIntOrNull() ?: 443
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
                            this.multiPorts = multiPorts.trim()
                            this.autoSync = autoSync
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
                            multiPorts = multiPorts.trim(),
                            autoSync = autoSync
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
private fun ViewTunnelCodeDialog(
    t: Str,
    tunnel: TunnelConfig,
    onDismiss: () -> Unit
) {
    val code = remember(tunnel) { TunnelEngine.generateCode(tunnel) }
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
