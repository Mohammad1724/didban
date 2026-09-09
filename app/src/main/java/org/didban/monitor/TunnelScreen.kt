@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Top Bar Header ──
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconBadge(
                        icon = Icons.Rounded.SwapHoriz,
                        tint = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        size = 38.dp,
                        iconSize = 22.dp
                    )
                    Text(t.tunnelsHub, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0D9488),
                    modifier = Modifier.clickable { showAddDialog = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("＋ ${t.addTunnel}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // ── Guide Card ──
        item {
            FeatureGuideCard(
                title = "راهنمای جامع هاب تانل دو سرور (Iran Node ➔ Foreign Node)",
                description = t.guideTunnels,
                bullets = listOf(
                    "پشتیبانی از چندین پورت همزمان (Multi-Port Forwarding): رله همزمان چندین پورت نظیر 2096, 2097, 2098",
                    "BackPack 🎒: تانل نسل جدید با رمزنگاری Stealth Noise، دور زدن کرنل PCK و گیمینگ KCP+FEC",
                    "Paqet & Narnia: عبور از فیلترینگ با سوکت خام (Raw Socket) و پنهان‌سازی در پکت‌های ICMP Ping",
                    "Spoof Tunnel & Backhaul: جعل دوطرفه IP مبدا و رله مالتی‌پورت پرسرعت"
                )
            )
        }

        // ── Summary Cards ──
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernCard(Modifier.weight(1f), padding = 10.dp) {
                    Text("کل تانل‌ها", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(totalTunnels.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                }
                ModernCard(Modifier.weight(1f), padding = 10.dp) {
                    Text("متصل و آنلاین", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(activeTunnels.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                }
                ModernCard(Modifier.weight(1f), padding = 10.dp) {
                    Text("هسته‌های فعال", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text(uniqueCores.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6366F1))
                }
            }
        }

        // ── Tunnels List or Empty State ──
        if (tunnels.isEmpty()) {
            item {
                ModernCard(padding = 20.dp) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconBadge(
                            icon = Icons.Rounded.AltRoute,
                            tint = MaterialTheme.colorScheme.primary,
                            background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            size = 64.dp,
                            iconSize = 34.dp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("هنوز تانلی بین دو سرور تعریف نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "با تعریف اولین تانل، مشخصات سرور ایران و خارج را وارد کرده و پورت‌های دلخواه (تکی یا چندگانه) را با هسته‌های BackPack، Paqet، Narnia، Spoof Tunnel یا Backhaul به آسانی متصل کنید.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryActionButton(
                            text = "＋  ${t.addTunnel}",
                            onClick = { showAddDialog = true }
                        )
                    }
                }
            }
        } else {
            items(tunnels, key = { it.id }) { tun ->
                val coreBadgeColor = when (tun.core) {
                    TunnelCore.BACKPACK -> Color(0xFFF97316)
                    TunnelCore.PAQET -> Color(0xFF0284C7)
                    TunnelCore.NARNIA -> Color(0xFF8B5CF6)
                    TunnelCore.SPOOF_TUNNEL -> Color(0xFFF43F5E)
                    TunnelCore.BACKHAUL -> Color(0xFF0D9488)
                    TunnelCore.RATHOLE -> Color(0xFFEA580C)
                    TunnelCore.GOST -> Color(0xFF2563EB)
                    TunnelCore.CHISEL -> Color(0xFF7C3AED)
                    TunnelCore.FRP -> Color(0xFFDC2626)
                    TunnelCore.IPTABLES -> Color(0xFF475569)
                }

                val ports = remember(tun) { TunnelEngine.parsePortMappings(tun) }
                val portsSummary = remember(ports) {
                    if (ports.size == 1) "${ports[0].iranPort} ➔ ${ports[0].foreignPort}"
                    else ports.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" }
                }

                ModernCard(padding = 14.dp) {
                    // Header Row: Core badge + Status dot + Latency
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = coreBadgeColor.copy(alpha = 0.15f)) {
                            Text(
                                tun.core.displayName,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = coreBadgeColor
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                tun.transport.displayName,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (tun.core == TunnelCore.BACKPACK) {
                            Spacer(Modifier.width(4.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF10B981).copy(alpha = 0.12f)) {
                                Text(
                                    tun.preset.uppercase(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        } else if (tun.core == TunnelCore.PAQET) {
                            Spacer(Modifier.width(4.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0284C7).copy(alpha = 0.12f)) {
                                Text(
                                    tun.kcpMode.uppercase(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Status Pill / Dot
                        Box(
                            Modifier.size(9.dp).background(
                                when (tun.lastStatus) {
                                    1 -> Color(0xFF10B981)
                                    0 -> Color(0xFFEF4444)
                                    else -> Color(0xFF94A3B8)
                                },
                                CircleShape
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (tun.lastStatus) {
                                1 -> if (tun.lastLatencyMs >= 0) "${tun.lastLatencyMs} ms" else "آنلاین"
                                0 -> "آفلاین / قطع"
                                else -> "در انتظار تست"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (tun.lastStatus) {
                                1 -> Color(0xFF10B981)
                                0 -> Color(0xFFEF4444)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(tun.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Spacer(Modifier.height(6.dp))

                    // Route Card: Iran -> Foreign
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🇮🇷 سرور ایران:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(tun.iranHost.ifBlank { "0.0.0.0" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🌍 سرور خارج:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(tun.foreignHost.ifBlank { "127.0.0.1" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🔌 پورت‌های فوروارد (${ports.size} پورت):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(portsSummary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                            }
                            if (tun.core != TunnelCore.IPTABLES && tun.core != TunnelCore.NARNIA) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔑 پورت تانل ارتباطی:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Port ${tun.corePort}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Action Buttons Row: View Configs / Test / Edit / Delete
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. View Configs & Commands
                        TextButton(onClick = { viewCodeTunnel = tun }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(t.viewConfigs, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2. Test Latency
                        TextButton(
                            onClick = {
                                if (testingTunnelId == null) {
                                    testingTunnelId = tun.id
                                    scope.launch {
                                        val (ok, lat) = TunnelEngine.testTunnel(tun)
                                        tun.lastStatus = if (ok) 1 else 0
                                        tun.lastLatencyMs = lat
                                        tun.lastChecked = System.currentTimeMillis()
                                        save()
                                        testingTunnelId = null
                                        Toast.makeText(ctx, if (ok) "تانل پاسخ داد (${lat}ms)" else "پاسخی از پورت تانل دریافت نشد", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            if (testingTunnelId == tun.id) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Text("تست", fontSize = 11.5.sp)
                                }
                            }
                        }

                        // 3. Edit
                        IconButton(onClick = { editingTunnel = tun }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        }

                        // 4. Delete
                        IconButton(onClick = { deletingTunnel = tun }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                PrimaryActionButton(
                    text = "＋  ${t.addTunnel}",
                    onClick = { showAddDialog = true }
                )
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // ── Add/Edit Tunnel Dialog ──
    if (showAddDialog || editingTunnel != null) {
        val isEdit = editingTunnel != null
        AddOrEditTunnelDialog(
            t = t,
            existing = editingTunnel,
            onDismiss = {
                showAddDialog = false
                editingTunnel = null
            },
            onSave = { savedTun ->
                if (isEdit) {
                    val idx = tunnels.indexOfFirst { it.id == savedTun.id }
                    if (idx >= 0) {
                        tunnels = tunnels.toMutableList().also { it[idx] = savedTun }
                    }
                } else {
                    tunnels = tunnels + savedTun
                }
                save()
                showAddDialog = false
                editingTunnel = null
                viewCodeTunnel = savedTun
            }
        )
    }

    // ── View Codes & Commands Dialog ──
    viewCodeTunnel?.let { tun ->
        ViewTunnelCodeDialog(
            t = t,
            tunnel = tun,
            onDismiss = { viewCodeTunnel = null }
        )
    }

    // ── Delete Confirmation Dialog ──
    deletingTunnel?.let { dt ->
        AlertDialog(
            onDismissRequest = { deletingTunnel = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(t.deleteTunnel, fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف تانل «${dt.name}» اطمینان دارید؟") },
            confirmButton = {
                TextButton(onClick = {
                    tunnels = tunnels.filter { it.id != dt.id }
                    save()
                    deletingTunnel = null
                }) {
                    Text(t.delete, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTunnel = null }) { Text(t.cancel) }
            }
        )
    }
}

// ── Add / Edit Tunnel Dialog ────────────────────────────────────────────────

@Composable
private fun AddOrEditTunnelDialog(
    t: Str,
    existing: TunnelConfig?,
    onDismiss: () -> Unit,
    onSave: (TunnelConfig) -> Unit
) {
    val ctx = LocalContext.current
    val servers = remember { Prefs.loadServers(ctx) }

    var name by remember { mutableStateOf(existing?.name ?: "تونل جدید") }
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKPACK) }
    var transport by remember {
        mutableStateOf(
            existing?.transport ?: when (core) {
                TunnelCore.BACKPACK -> TunnelTransport.STEALTH
                TunnelCore.PAQET -> TunnelTransport.RAW_KCP
                TunnelCore.NARNIA -> TunnelTransport.ICMP_CHACHA
                TunnelCore.SPOOF_TUNNEL -> TunnelTransport.IP_SPOOF_UDP
                else -> TunnelTransport.TCP
            }
        )
    }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var multiPorts by remember { mutableStateOf(existing?.multiPorts ?: "2096, 2097, 2098") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    var token by remember { mutableStateOf(existing?.token ?: TunnelEngine.generateRandomToken(24)) }
    var preset by remember { mutableStateOf(existing?.preset ?: "turbo") }
    var kcpMode by remember { mutableStateOf(existing?.kcpMode ?: "fast") }
    var encryption by remember { mutableStateOf(existing?.encryption ?: "aes-128-gcm") }
    var spoofSrcIp by remember { mutableStateOf(existing?.spoofSrcIp ?: "1.1.1.1") }
    var spoofPeerIp by remember { mutableStateOf(existing?.spoofPeerIp ?: "8.8.8.8") }
    var virtualIpIran by remember { mutableStateOf(existing?.virtualIpIran ?: "10.200.200.2") }
    var virtualIpKharej by remember { mutableStateOf(existing?.virtualIpKharej ?: "10.200.200.1") }
    var mtu by remember { mutableStateOf(existing?.mtu?.toString() ?: "1350") }
    var acceptUdp by remember { mutableStateOf(existing?.acceptUdp ?: true) }
    var proxyProtocol by remember { mutableStateOf(existing?.proxyProtocol ?: false) }

    var showIranServerDropdown by remember { mutableStateOf(false) }
    var showForeignServerDropdown by remember { mutableStateOf(false) }

    val dummyConfig = remember(multiPorts) {
        TunnelConfig(0, "", core, transport, multiPorts = multiPorts)
    }
    val parsedPorts = remember(multiPorts) { TunnelEngine.parsePortMappings(dummyConfig) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(480.dp).imePadding()
            ) {
                // 1. Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام دلخواه تانل (مثلاً تانل شاتل به هتزنر)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 2. Core Selector
                item {
                    Text("انتخاب هسته تانلینگ:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TunnelCore.values().forEach { c ->
                            val isSel = core == c
                            val badgeColor = when (c) {
                                TunnelCore.BACKPACK -> Color(0xFFF97316)
                                TunnelCore.PAQET -> Color(0xFF0284C7)
                                TunnelCore.NARNIA -> Color(0xFF8B5CF6)
                                TunnelCore.SPOOF_TUNNEL -> Color(0xFFF43F5E)
                                TunnelCore.BACKHAUL -> Color(0xFF0D9488)
                                TunnelCore.RATHOLE -> Color(0xFFEA580C)
                                TunnelCore.GOST -> Color(0xFF2563EB)
                                TunnelCore.CHISEL -> Color(0xFF7C3AED)
                                TunnelCore.FRP -> Color(0xFFDC2626)
                                TunnelCore.IPTABLES -> Color(0xFF475569)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) badgeColor else MaterialTheme.colorScheme.surfaceContainer,
                                border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable {
                                    core = c
                                    when (c) {
                                        TunnelCore.BACKPACK -> transport = TunnelTransport.STEALTH
                                        TunnelCore.PAQET -> transport = TunnelTransport.RAW_KCP
                                        TunnelCore.NARNIA -> transport = TunnelTransport.ICMP_CHACHA
                                        TunnelCore.SPOOF_TUNNEL -> transport = TunnelTransport.IP_SPOOF_UDP
                                        TunnelCore.IPTABLES -> transport = TunnelTransport.TCP
                                        else -> {}
                                    }
                                }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        c.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    Text(core.description, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                }

                // ── 3. Multi-Port Configurator ──
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("پورت‌های فوروارد (تکی، چندگانه یا رنج):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = multiPorts,
                            onValueChange = { multiPorts = it },
                            label = { Text("پورت‌ها (مثلاً: 2096, 2097, 2098 یا 443:8443)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Presets
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "2096, 2097, 2098" to "⚡ پورت‌های 2096..2098",
                                "80, 443, 8080, 8443" to "🌐 وب و SSL",
                                "80, 443, 2052, 2053, 2082, 2083, 2086, 2087, 2095, 2096" to "🚀 پورت‌های کلودفلر",
                                "443:8443" to "🔌 تک پورت (443:8443)"
                            ).forEach { (presetPorts, label) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable { multiPorts = presetPorts }
                                ) {
                                    Text(label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        // Live Parsed Ports Info
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0D9488).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "📡 پورت‌های تشخیص‌داده‌شده (${parsedPorts.size} پورت): " + parsedPorts.joinToString(", ") { "${it.iranPort}➔${it.foreignPort}" },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF0D9488),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── 4. Specific Options by Core ──
                when (core) {
                    TunnelCore.BACKPACK -> {
                        item {
                            Text("پروتکل انتقال BackPack (ضد فیلترینگ):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    TunnelTransport.STEALTH,
                                    TunnelTransport.PCK,
                                    TunnelTransport.KCP_FEC,
                                    TunnelTransport.WSSMUX,
                                    TunnelTransport.WSMUX,
                                    TunnelTransport.QUIC,
                                    TunnelTransport.TCPMUX,
                                    TunnelTransport.TCP,
                                    TunnelTransport.XDI,
                                    TunnelTransport.SPOOF
                                ).forEach { tp ->
                                    val isSel = transport == tp
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFFF97316) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { transport = tp }
                                    ) {
                                        Text(
                                            tp.displayName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            if (transport.description.isNotBlank()) {
                                Text(transport.description, fontSize = 10.5.sp, color = Color(0xFF059669), modifier = Modifier.padding(top = 4.dp))
                            }
                        }

                        // Performance Preset (BackPack)
                        item {
                            Text("پریست عملکرد و بهینه‌سازی (Preset):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "turbo" to "⚡ توربو (Turbo)",
                                    "balance" to "⚖️ متعادل (Balance)",
                                    "aggressive" to "🔥 تهاجمی (Aggressive)",
                                    "gaming" to "🎮 گیمینگ کم‌تاخیر (Gaming)"
                                ).forEach { (pKey, pLabel) ->
                                    val isSel = preset == pKey
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { preset = pKey }
                                    ) {
                                        Text(
                                            pLabel,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Toggles: Accept UDP & PROXY Protocol
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("انتقال ترافیک UDP (UDP Forwarding)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            Text("مناسب برای V2Ray/Xray، WireGuard، DNS و بازی‌ها", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = acceptUdp, onCheckedChange = { acceptUdp = it })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("پروتکل PROXY v2 (Real Client IP)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                            Text("ارسال IP واقعی کاربران به سرور مقصد", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = proxyProtocol, onCheckedChange = { proxyProtocol = it })
                                    }
                                }
                            }
                        }
                    }

                    TunnelCore.PAQET -> {
                        item {
                            Text("مود عملکرد KCP (Paqet):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "fast" to "⚡ سریع (Fast - پیشنهادی)",
                                    "fast2" to "🚀 توربو (Fast2)",
                                    "fast3" to "🔥 حداکثر توان (Fast3)",
                                    "normal" to "⚖️ عادی (Normal)"
                                ).forEach { (mKey, mLabel) ->
                                    val isSel = kcpMode == mKey
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { kcpMode = mKey }
                                    ) {
                                        Text(
                                            mLabel,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text("الگوریتم رمزنگاری دیتای سوکت خام:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "aes-128-gcm" to "AES-128-GCM (پیش‌فرض)",
                                    "aes-256-gcm" to "AES-256-GCM",
                                    "chacha20-poly1305" to "ChaCha20-Poly1305",
                                    "none" to "بدون رمزنگاری (None)"
                                ).forEach { (eKey, eLabel) ->
                                    val isSel = encryption == eKey
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { encryption = eKey }
                                    ) {
                                        Text(
                                            eLabel,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TunnelCore.NARNIA -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("📡 تانل لایه ۳ درون پکت‌های Ping (ICMP)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFF8B5CF6))
                                    Text("نارنیا تمام ترافیک را با رمزنگاری ChaCha20 داخل بسته‌های عادی پینگ بسته‌بندی می‌کند تا فایروال آن را ترافیک عادی شبکه ببیند.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = virtualIpIran,
                                    onValueChange = { virtualIpIran = it },
                                    label = { Text("IP مجازی ایران") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = virtualIpKharej,
                                    onValueChange = { virtualIpKharej = it },
                                    label = { Text("IP مجازی خارج") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    TunnelCore.SPOOF_TUNNEL -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF43F5E).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("🎭 جعل دوطرفه IP مبدا (Mutual IP Spoofing)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = Color(0xFFF43F5E))
                                    Text("تغییر هدر Source IP بسته‌ها برای عبور از فیلترینگ IP لایه‌های ۳ و ۴ با لایه اختصاصی بازسازی پکت‌ها و FEC.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = spoofSrcIp,
                                    onValueChange = { spoofSrcIp = it },
                                    label = { Text("IP جعلی ایران (فرستنده)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = spoofPeerIp,
                                    onValueChange = { spoofPeerIp = it },
                                    label = { Text("IP جعلی خارج (گیرنده)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Text("ترنسپورت ارسال پکت‌های جعلی:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    TunnelTransport.IP_SPOOF_UDP to "UDP Raw Socket",
                                    TunnelTransport.IP_SPOOF_ICMP to "ICMP Echo/Reply"
                                ).forEach { (tp, label) ->
                                    val isSel = transport == tp
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFFF43F5E) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { transport = tp }
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TunnelCore.BACKHAUL, TunnelCore.GOST -> {
                        item {
                            Text("پروتکل انتقال (Transport):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    TunnelTransport.TCP,
                                    TunnelTransport.WS,
                                    TunnelTransport.WSMUX,
                                    TunnelTransport.GRPC,
                                    TunnelTransport.TCPMUX
                                ).forEach { tp ->
                                    val isSel = transport == tp
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.clickable { transport = tp }
                                    ) {
                                        Text(
                                            tp.displayName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {}
                }

                // 5. Iran Server IP
                item {
                    Text("مشخصات سرور ایران (Bridge / Relay):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = iranHost,
                                onValueChange = { iranHost = it },
                                label = { Text("آی‌پی سرور ایران") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (servers.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = showIranServerDropdown,
                                    onDismissRequest = { showIranServerDropdown = false }
                                ) {
                                    servers.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("${s.name} (${s.host})") },
                                            onClick = {
                                                iranHost = s.host
                                                showIranServerDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (servers.isNotEmpty()) {
                            IconButton(onClick = { showIranServerDropdown = true }) {
                                Icon(Icons.Rounded.Dns, contentDescription = "Select Server", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 6. Foreign Server IP
                item {
                    Text("مشخصات سرور خارج (Upstream / Target):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = foreignHost,
                                onValueChange = { foreignHost = it },
                                label = { Text("آی‌پی سرور خارج") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (servers.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = showForeignServerDropdown,
                                    onDismissRequest = { showForeignServerDropdown = false }
                                ) {
                                    servers.forEach { s ->
                                        DropdownMenuItem(
                                            text = { Text("${s.name} (${s.host})") },
                                            onClick = {
                                                foreignHost = s.host
                                                showForeignServerDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (servers.isNotEmpty()) {
                            IconButton(onClick = { showForeignServerDropdown = true }) {
                                Icon(Icons.Rounded.Language, contentDescription = "Select Server", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 7. Core Tunnel Port & Secret Token
                if (core != TunnelCore.IPTABLES && core != TunnelCore.NARNIA) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = corePort,
                                onValueChange = { corePort = it },
                                label = { Text("پورت تانل") },
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )

                            OutlinedTextField(
                                value = token,
                                onValueChange = { token = it },
                                label = { Text("توکن امنیتی") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { token = TunnelEngine.generateRandomToken(24) }) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Gen Token", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val firstPort = parsedPorts.firstOrNull() ?: PortMapping(443, 8443)
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
                            multiPorts = multiPorts.trim()
                        )
                        onSave(newTun)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
            ) {
                Text(t.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel) }
        }
    )
}

// ── View Tunnel Codes & Docker Dialog ────────────────────────────────────────

@Composable
private fun ViewTunnelCodeDialog(
    t: Str,
    tunnel: TunnelConfig,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val code = remember(tunnel) { TunnelEngine.generateCode(tunnel) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Iran, 1: Foreign, 2: Docker

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("دستورات استقرار ${tunnel.core.displayName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tab Buttons Row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TabButton("🇮🇷 سرور ایران", selectedTab == 0) { selectedTab = 0 }
                    TabButton("🌍 سرور خارج", selectedTab == 1) { selectedTab = 1 }
                    TabButton("🐳 داکر (Docker)", selectedTab == 2) { selectedTab = 2 }
                }

                Text(
                    when (selectedTab) {
                        0 -> "دستور تک‌خطی زیر را در ترمینال SSH سرور ایران اجرا کنید:"
                        1 -> "دستور تک‌خطی زیر را در ترمینال SSH سرور خارج اجرا کنید:"
                        else -> "فایل‌های Docker-Compose برای استقرار با کانتینر داکر:"
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val contentToShow = when (selectedTab) {
                    0 -> code.iranInstallCommand
                    1 -> code.foreignInstallCommand
                    else -> "=== docker-compose-iran.yml ===\n${code.dockerComposeIran}\n\n=== docker-compose-foreign.yml ===\n${code.dockerComposeForeign}"
                }

                // Terminal Code Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(Modifier.padding(10.dp)) {
                        item {
                            Text(
                                contentToShow,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFF1F5F9),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Copy Action Button
                PrimaryActionButton(
                    text = "کپی دستورات در کلیپ‌بورد",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("tunnel_cmd", contentToShow))
                        Toast.makeText(ctx, "دستورات در کلیپ‌بورد کپی شدند!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.close) }
        }
    )
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
