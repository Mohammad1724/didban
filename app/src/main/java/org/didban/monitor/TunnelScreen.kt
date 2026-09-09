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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Sensors
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
                if (tun.isEnabled && tun.iranHost.isNotBlank()) {
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
                title = "راهنمای تانل دو سرور (Iran Node ➔ Foreign Node)",
                description = t.guideTunnels,
                bullets = listOf(
                    "Backhaul: تانل معکوس پایدار و ضد فیلتر با WebSocket و انتقال مالتی‌پورت",
                    "Rathole: هسته فوق‌سبک Rust با مصرف ناچیز رم و امنیت Noise Protocol",
                    "GOST: رله و فوروارد ترافیک TCP/UDP/WS/gRPC بین سرور ایران و خارج",
                    "Chisel & FRP: پوشش ترافیک در قالب وب و ریورس پروکسی چندکاناله"
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
                            "با تعریف اولین تانل، مشخصات سرور ایران و خارج را وارد کرده و دستورات نصب خودکار با هسته‌های Backhaul، Rathole یا GOST را دریافت کنید.",
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
                    TunnelCore.BACKHAUL -> Color(0xFF0D9488)
                    TunnelCore.RATHOLE -> Color(0xFFEA580C)
                    TunnelCore.GOST -> Color(0xFF2563EB)
                    TunnelCore.CHISEL -> Color(0xFF7C3AED)
                    TunnelCore.FRP -> Color(0xFFDC2626)
                    TunnelCore.IPTABLES -> Color(0xFF475569)
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
                                Text("🇮🇷 ورودی ایران:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${tun.iranHost.ifBlank { "0.0.0.0" }}:${tun.iranPort}", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🌍 مقصد خارج:", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${tun.foreignHost.ifBlank { "127.0.0.1" }}:${tun.foreignPort}", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🔌 پورت ارتباطی تانل:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Port ${tun.corePort}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
                viewCodeTunnel = savedTun // Automatically open instructions for convenience!
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
    var core by remember { mutableStateOf(existing?.core ?: TunnelCore.BACKHAUL) }
    var transport by remember { mutableStateOf(existing?.transport ?: TunnelTransport.TCP) }
    var iranHost by remember { mutableStateOf(existing?.iranHost ?: "") }
    var iranPort by remember { mutableStateOf(existing?.iranPort?.toString() ?: "443") }
    var foreignHost by remember { mutableStateOf(existing?.foreignHost ?: "") }
    var foreignPort by remember { mutableStateOf(existing?.foreignPort?.toString() ?: "8443") }
    var corePort by remember { mutableStateOf(existing?.corePort?.toString() ?: "3080") }
    var token by remember { mutableStateOf(existing?.token ?: TunnelEngine.generateRandomToken()) }

    var showIranServerDropdown by remember { mutableStateOf(false) }
    var showForeignServerDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(if (existing == null) t.addTunnel else t.editTunnel, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(420.dp).imePadding()
            ) {
                // 1. Name
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام دلخواه تانل (مثلاً ایران آروان -> آلمان هتزنر)") },
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
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) Color(0xFF0D9488) else MaterialTheme.colorScheme.surfaceContainer,
                                border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.clickable {
                                    core = c
                                    if (c == TunnelCore.IPTABLES) {
                                        transport = TunnelTransport.TCP
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

                // 3. Transport Protocol (for Backhaul / GOST)
                if (core == TunnelCore.BACKHAUL || core == TunnelCore.GOST) {
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

                // 4. Iran Server & Listening Port
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

                        OutlinedTextField(
                            value = iranPort,
                            onValueChange = { iranPort = it },
                            label = { Text("پورت ایران") },
                            singleLine = true,
                            modifier = Modifier.width(85.dp)
                        )
                    }
                }

                // 5. Foreign Server & Target Port
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

                        OutlinedTextField(
                            value = foreignPort,
                            onValueChange = { foreignPort = it },
                            label = { Text("پورت مقصد") },
                            singleLine = true,
                            modifier = Modifier.width(85.dp)
                        )
                    }
                }

                // 6. Core Tunnel Port & Secret Token
                if (core != TunnelCore.IPTABLES) {
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
                                    IconButton(onClick = { token = TunnelEngine.generateRandomToken() }) {
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
                        val newTun = existing?.apply {
                            this.name = name.trim()
                            this.core = core
                            this.transport = transport
                            this.iranHost = iranHost.trim()
                            this.iranPort = iranPort.toIntOrNull() ?: 443
                            this.foreignHost = foreignHost.trim()
                            this.foreignPort = foreignPort.toIntOrNull() ?: 8443
                            this.corePort = corePort.toIntOrNull() ?: 3080
                            this.token = token.trim()
                        } ?: TunnelConfig(
                            id = System.currentTimeMillis(),
                            name = name.trim(),
                            core = core,
                            transport = transport,
                            iranHost = iranHost.trim(),
                            iranPort = iranPort.toIntOrNull() ?: 443,
                            foreignHost = foreignHost.trim(),
                            foreignPort = foreignPort.toIntOrNull() ?: 8443,
                            corePort = corePort.toIntOrNull() ?: 3080,
                            token = token.trim()
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
