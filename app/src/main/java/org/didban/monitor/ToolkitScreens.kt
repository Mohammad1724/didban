@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.didban.monitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ═════════════════════════════════════════════════════════════════════════════
// 1. NETWORK HUB SCREEN (Port Scanner · SSL · IP Info · Ping)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NetworkHubScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛰️ ${t.networkHub}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Port Scanner", "SSL Inspector", "IP & GeoIP", "TCP Ping").forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> PortScannerTab(t)
                1 -> SslInspectorTab(t)
                2 -> IpInfoTab(t)
                3 -> TcpPingTab(t)
            }
        }
    }
}

@Composable
private fun PortScannerTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var scannedCount by remember { mutableStateOf(0) }

    val portsToScan = remember { COMMON_PORTS.map { it.first } }

    fun runScan() {
        if (host.isBlank() || isScanning) return
        isScanning = true
        results = emptyList()
        scannedCount = 0

        scope.launch {
            try {
                PortScanner.scanPorts(host.trim(), portsToScan, concurrency = 10) { res ->
                    scannedCount++
                    if (res.isOpen) {
                        results = (results + res).sortedBy { it.port }
                    }
                }
            } finally {
                isScanning = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { runScan() }, enabled = !isScanning && host.isNotBlank()) {
                if (isScanning) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else Text(t.scan)
            }
        }

        if (isScanning) {
            Spacer(Modifier.height(8.dp))
            Text("Scanning $scannedCount / ${portsToScan.size} ports…", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(10.dp))

        if (results.isEmpty() && !isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(t.enterHostToScan, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.port }) { r ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E3A2B)) {
                            Text("PORT ${r.port}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.service, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("${r.latencyMs} ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("OPEN", fontSize = 12.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SslInspectorTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var isLoading by remember { mutableStateOf(false) }
    var certInfo by remember { mutableStateOf<SslCertInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun checkSsl() {
        if (host.isBlank() || isLoading) return
        isLoading = true
        err = null
        certInfo = null
        scope.launch {
            try {
                certInfo = SslInspector.inspect(host.trim(), port.toIntOrNull() ?: 443)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Domain / Host", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port", fontSize = 10.sp) },
                modifier = Modifier.width(65.dp),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = { checkSsl() }, enabled = !isLoading && host.isNotBlank()) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else Text(t.inspect)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ Error: $err", color = Color(0xFFF87171), fontSize = 12.sp)
        }

        certInfo?.let { c ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (c.isExpired || c.daysRemaining < 7) Color(0xFF3B1515) else Color(0xFF0F2E22),
                        border = BorderStroke(1.dp, if (c.isExpired || c.daysRemaining < 7) Color(0xFF7A2E2E) else Color(0xFF1E5C40)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (c.isExpired) "❌ Certificate Expired!" else "✅ Valid (${c.daysRemaining} days remaining)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (c.isExpired) Color(0xFFF87171) else Color(0xFF4ADE80)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Valid: ${c.validFrom} ➔ ${c.validTo}", fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Subject: ${c.subject}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("Issuer: ${c.issuer}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Sig Algorithm: ${c.sigAlg}", fontSize = 11.sp)
                            Text("SHA-256 Fingerprint: ${c.fingerprintSha256}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            if (c.sans.isNotEmpty()) {
                                Text("SANs: ${c.sans.take(6).joinToString(", ")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IpInfoTab(t: Str) {
    val scope = rememberCoroutineScope()
    var targetIp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var geoData by remember { mutableStateOf<GeoIpData?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun lookup() {
        isLoading = true
        err = null
        scope.launch {
            try {
                geoData = IpInfoService.lookup(targetIp)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { lookup() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = targetIp,
                onValueChange = { targetIp = it },
                label = { Text("IP address (empty = my IP)", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { lookup() }, enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else Text(t.lookup)
            }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
        }

        geoData?.let { g ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.flag, fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(g.ip, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("${g.city}, ${g.country}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("ISP / Org: ${g.isp} (${g.org})", fontSize = 12.sp)
                    Text("ASN: ${g.asn}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Timezone: ${g.timezone}", fontSize = 12.sp)
                    Text("Coordinates: ${g.lat}, ${g.lon}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TcpPingTab(t: Str) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("80") }
    var isPinging by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    fun startPing() {
        if (host.isBlank()) return
        if (isPinging) {
            isPinging = false
            return
        }
        isPinging = true
        logs = emptyList()

        scope.launch {
            var seq = 1
            while (isPinging) {
                try {
                    val p = port.toIntOrNull() ?: 80
                    val lat = TcpPinger.ping(host.trim(), p, 1500)
                    logs = (listOf("seq=$seq host=$host:$p time=${lat}ms") + logs).take(50)
                } catch (e: Exception) {
                    logs = (listOf("seq=$seq host=$host timeout (${e.message})") + logs).take(50)
                }
                seq++
                delay(1000)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Target Host / IP", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port", fontSize = 10.sp) },
                modifier = Modifier.width(65.dp),
                singleLine = true
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { startPing() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPinging) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isPinging) t.stop else t.start)
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(logs) { line ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        line,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (line.contains("time=")) Color(0xFF4ADE80) else Color(0xFFF87171)
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. CLOUDFLARE DNS MANAGER SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CloudflareScreen(t: Str) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiToken by remember { mutableStateOf(Prefs.getCfToken(ctx)) }
    var isTokenSaved by remember { mutableStateOf(apiToken.isNotBlank()) }
    var zones by remember { mutableStateOf<List<CfZone>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<CfZone?>(null) }
    var records by remember { mutableStateOf<List<CfRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    fun loadZones() {
        if (apiToken.isBlank()) return
        isLoading = true
        err = null
        scope.launch {
            try {
                zones = CloudflareService.listZones(apiToken)
                if (zones.isNotEmpty() && selectedZone == null) {
                    selectedZone = zones[0]
                }
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun loadRecords(zone: CfZone) {
        isLoading = true
        err = null
        scope.launch {
            try {
                records = CloudflareService.listRecords(apiToken, zone.id)
            } catch (e: Exception) {
                err = e.message
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedZone) {
        selectedZone?.let { loadRecords(it) }
    }

    LaunchedEffect(Unit) {
        if (apiToken.isNotBlank()) loadZones()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("☁️ ${t.cloudflareDns}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        // API Token bar
        if (!isTokenSaved) {
            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text("Cloudflare API Token", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                Prefs.setCfToken(ctx, apiToken)
                isTokenSaved = true
                loadZones()
            }) { Text(t.saveToken) }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selectedZone?.name ?: "Zones (${zones.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { isTokenSaved = false }) { Text(t.changeToken, fontSize = 11.sp) }
                TextButton(onClick = { selectedZone?.let { loadRecords(it) } }) { Text("🔄", fontSize = 14.sp) }
                Button(onClick = { showAddDialog = true }) { Text("+ ${t.addRecord}") }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        if (err != null) {
            Text("❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(records, key = { it.id }) { rec ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                rec.type,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rec.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(rec.content, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (rec.proxiable) {
                            Text(if (rec.proxied) "☁️ Proxied" else "☁️ DNS", fontSize = 10.sp, color = if (rec.proxied) Color(0xFFF59E0B) else Color(0xFF94A3B8))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            "🗑️",
                            modifier = Modifier.clickable {
                                scope.launch {
                                    selectedZone?.let { z ->
                                        CloudflareService.deleteRecord(apiToken, z.id, rec.id)
                                        loadRecords(z)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var recType by remember { mutableStateOf("A") }
        var recName by remember { mutableStateOf("") }
        var recContent by remember { mutableStateOf("") }
        var proxied by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(t.addDnsRecord) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = recType, onValueChange = { recType = it }, label = { Text("Type (A, AAAA, CNAME, TXT)") })
                    OutlinedTextField(value = recName, onValueChange = { recName = it }, label = { Text("Name (@ or subdomain)") })
                    OutlinedTextField(value = recContent, onValueChange = { recContent = it }, label = { Text("Content (IP / Value)") })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = proxied, onCheckedChange = { proxied = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Cloudflare Proxy (Orange Cloud)")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        selectedZone?.let { z ->
                            CloudflareService.saveRecord(apiToken, z.id, null, recType, recName, recContent, proxied)
                            showAddDialog = false
                            loadRecords(z)
                        }
                    }
                }) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(t.cancel) }
            }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. ENCRYPTED VAULT SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun VaultScreen(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var masterPass by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<List<VaultNote>>(emptyList()) }
    var showAddNote by remember { mutableStateOf(false) }
    var backupString by remember { mutableStateOf("") }
    var showBackupDialog by remember { mutableStateOf(false) }

    fun unlock() {
        if (masterPass.isBlank()) return
        try {
            notes = Prefs.loadVaultNotes(ctx, masterPass)
            isUnlocked = true
        } catch (e: Exception) {
            Toast.makeText(ctx, "Incorrect password or corrupt vault", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔐 ${t.encryptedVault}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        if (!isUnlocked) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(t.vaultHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = masterPass,
                        onValueChange = { masterPass = it },
                        label = { Text(t.masterPassword, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(onClick = { unlock() }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.unlockVault)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${notes.size} ${t.notes}", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val b64 = EncryptedVault.exportBackup(Prefs.loadServers(ctx), notes, masterPass)
                    backupString = b64
                    showBackupDialog = true
                }) { Text("📦 ${t.backup}", fontSize = 12.sp) }
                Button(onClick = { showAddNote = true }) { Text("+ ${t.addNote}") }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { n ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("secret", n.content))
                                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                                }) { Text("📋 ${t.copy}", fontSize = 11.sp) }
                                TextButton(onClick = {
                                    notes = notes.filter { it.id != n.id }
                                    Prefs.saveVaultNotes(ctx, notes, masterPass)
                                }) { Text("🗑️", fontSize = 12.sp) }
                            }
                            Text(n.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showAddNote) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddNote = false },
            title = { Text(t.addNote) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Secret / Content") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        notes = notes + VaultNote(System.currentTimeMillis(), title, content)
                        Prefs.saveVaultNotes(ctx, notes, masterPass)
                        showAddNote = false
                    }
                }) { Text(t.save) }
            },
            dismissButton = { TextButton(onClick = { showAddNote = false }) { Text(t.cancel) } }
        )
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("📦 ${t.backup}") },
            text = {
                Column {
                    Text(t.backupCopyHint, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = backupString, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setPrimaryClip(ClipData.newPlainText("backup", backupString))
                    Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                    showBackupDialog = false
                }) { Text(t.copy) }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text(t.close) } }
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. DEVELOPER LAB (STRING LAB) SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DevLabScreen(t: Str) {
    var subTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) { subTab = pagerState.currentPage }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛠️ ${t.devLab}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("JSON & Base64", "Subnet / CIDR", "JWT Decoder", "Generators").forEachIndexed { index, title ->
                val selected = subTab == index
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                ) {
                    Text(
                        title,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> JsonBase64Tab(t)
                1 -> SubnetCalcTab(t)
                2 -> JwtDecoderTab(t)
                3 -> GeneratorTab(t)
            }
        }
    }
}

@Composable
private fun JsonBase64Tab(t: Str) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Input Text / JSON / Base64", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { output = DevLabTools.formatJson(input) }, modifier = Modifier.weight(1f)) { Text("Format JSON", fontSize = 11.sp) }
            Button(onClick = { output = DevLabTools.base64Encode(input) }, modifier = Modifier.weight(1f)) { Text("B64 Enc", fontSize = 11.sp) }
            Button(onClick = { output = DevLabTools.base64Decode(input) }, modifier = Modifier.weight(1f)) { Text("B64 Dec", fontSize = 11.sp) }
            Button(onClick = { output = DevLabTools.hash(input, "SHA-256") }, modifier = Modifier.weight(1f)) { Text("SHA-256", fontSize = 11.sp) }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = output,
            onValueChange = {},
            readOnly = true,
            label = { Text("Output", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

@Composable
private fun SubnetCalcTab(t: Str) {
    var cidr by remember { mutableStateOf("192.168.1.50/24") }
    var info by remember { mutableStateOf<DevLabTools.SubnetInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun calc() {
        err = null
        try {
            info = DevLabTools.calculateSubnet(cidr)
        } catch (e: Exception) {
            err = e.message
            info = null
        }
    }

    LaunchedEffect(Unit) { calc() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cidr,
                onValueChange = { cidr = it },
                label = { Text("CIDR (e.g. 10.0.0.1/24)", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { calc() }) { Text(t.calculate) }
        }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
        }

        info?.let { s ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Network Address: ${s.network}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Broadcast Address: ${s.broadcast}", fontWeight = FontWeight.Bold)
                    Text("Usable Host Range: ${s.firstHost} — ${s.lastHost}")
                    Text("Usable Hosts Count: ${s.usableHosts} (${s.totalHosts} total)")
                    Text("Subnet Mask: ${s.netmask}")
                    Text("Wildcard Mask: ${s.wildcard}")
                }
            }
        }
    }
}

@Composable
private fun JwtDecoderTab(t: Str) {
    var token by remember { mutableStateOf("") }
    var jwtInfo by remember { mutableStateOf<DevLabTools.JwtInfo?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun decode() {
        err = null
        try {
            jwtInfo = DevLabTools.decodeJwt(token)
        } catch (e: Exception) {
            err = e.message
            jwtInfo = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Paste JWT Token", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = { decode() }, modifier = Modifier.fillMaxWidth()) { Text("Decode JWT") }

        Spacer(Modifier.height(10.dp))

        if (err != null) {
            Text("❌ $err", color = Color(0xFFF87171), fontSize = 12.sp)
        }

        jwtInfo?.let { j ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        if (j.isExpired) "❌ Token is Expired (${j.expiryDate})" else "✅ Token Valid (Expires: ${j.expiryDate ?: "Never"})",
                        color = if (j.isExpired) Color(0xFFF87171) else Color(0xFF4ADE80),
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Text("Payload:", fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(j.payload, modifier = Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorTab(t: Str) {
    val ctx = LocalContext.current
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var generatedPass by remember { mutableStateOf(DevLabTools.generatePassword(18)) }
    var generatedUuid by remember { mutableStateOf(DevLabTools.generateUuid()) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🔑 Strong Password Generator", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(generatedPass, fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { generatedPass = DevLabTools.generatePassword(18) }) { Text("Generate New") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("password", generatedPass))
                        Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("🆔 UUID v4 Generator", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(generatedUuid, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { generatedUuid = DevLabTools.generateUuid() }) { Text("Generate UUID") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("uuid", generatedUuid))
                        Toast.makeText(ctx, t.copied, Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. LOCAL FILE & WEB SERVER WITH QR CODE SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun LocalServerScreen(t: Str) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(LocalHttpServer.isRunning) }
    var shareText by remember { mutableStateOf("Hello from Didban!") }
    var serverPort by remember { mutableStateOf("8080") }
    var localUrl by remember { mutableStateOf("") }
    var qrBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    fun toggleServer() {
        if (isRunning) {
            LocalHttpServer.stop()
            isRunning = false
            qrBmp = null
        } else {
            val port = serverPort.toIntOrNull() ?: 8080
            scope.launch {
                LocalHttpServer.start(port = port, text = shareText)
                val ip = LocalHttpServer.getLocalIpAddress()
                val url = "http://$ip:$port"
                localUrl = url
                qrBmp = QrGenerator.generateSimpleBitmap(url, 400)
                isRunning = true
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("📡 ${t.localWebServer}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shareText,
                    onValueChange = { shareText = it },
                    label = { Text("Shared Content / Text", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it },
                        label = { Text("Port", fontSize = 10.sp) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { toggleServer() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isRunning) t.stopServer else t.startServer)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (isRunning && qrBmp != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅ Server Live at:", fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                    Text(localUrl, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Image(
                        bitmap = qrBmp!!.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.size(200.dp)
                    )
                }
            }
        }
    }
}
