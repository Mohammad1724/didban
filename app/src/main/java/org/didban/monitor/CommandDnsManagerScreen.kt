package org.didban.monitor

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun CommandDnsManagerScreen(copy: CommandCopy, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiToken by remember { mutableStateOf(Prefs.getCfToken(context)) }
    var tokenDraft by remember { mutableStateOf(Prefs.getCfToken(context)) }
    var zones by remember { mutableStateOf<List<CfZone>>(emptyList()) }
    var selectedZone by remember { mutableStateOf<CfZone?>(null) }
    var records by remember { mutableStateOf<List<CfRecord>>(emptyList()) }
    var selectedRecord by remember { mutableStateOf<CfRecord?>(null) }
    var recordType by remember { mutableStateOf("A") }
    var recordName by remember { mutableStateOf("") }
    var recordContent by remember { mutableStateOf("") }
    var recordTtl by remember { mutableStateOf("1") }
    var recordProxied by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf<GeoIpData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteRecord by remember { mutableStateOf<CfRecord?>(null) }

    fun loadZones() {
        if (apiToken.isBlank()) {
            error = copy.dnsNoToken
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val loadedZones = CloudflareService.listZones(apiToken)
                zones = loadedZones
                val first = loadedZones.firstOrNull()
                selectedZone = first
                records = if (first == null) emptyList() else CloudflareService.listRecords(apiToken, first.id)
            } catch (e: Exception) {
                error = e.message ?: copy.dnsZonesLoadFailed
            } finally {
                busy = false
            }
        }
    }

    fun loadRecords(zone: CfZone) {
        selectedZone = zone
        selectedRecord = null
        recordName = ""
        recordContent = ""
        busy = true
        error = null
        scope.launch {
            runCatching { CloudflareService.listRecords(apiToken, zone.id) }
                .onSuccess { records = it; message = copy.dnsRecordsLoaded.replace("%1", it.size.toString()) }
                .onFailure { error = it.message ?: copy.dnsRecordsLoadFailed }
            busy = false
        }
    }

    fun chooseRecord(record: CfRecord) {
        selectedRecord = record
        recordType = record.type
        recordName = record.name
        recordContent = record.content
        recordTtl = record.ttl.toString()
        recordProxied = record.proxied
    }

    fun saveRecord() {
        val zone = selectedZone
        if (zone == null || recordName.isBlank() || recordContent.isBlank()) {
            error = copy.dnsRecordFieldsRequired
            return
        }
        busy = true
        error = null
        scope.launch {
            runCatching {
                CloudflareService.saveRecord(
                    apiToken = apiToken,
                    zoneId = zone.id,
                    recordId = selectedRecord?.id,
                    type = recordType,
                    name = recordName,
                    content = recordContent,
                    proxied = recordProxied,
                    ttl = recordTtl.toIntOrNull()?.coerceIn(1, 86400) ?: 1
                )
            }.onSuccess {
                message = if (selectedRecord == null) copy.dnsRecordCreated else copy.dnsRecordUpdated
                loadRecords(zone)
            }.onFailure { error = it.message ?: copy.dnsRecordSaveFailed }
            busy = false
        }
    }

    fun runLookup() {
        if (lookup.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching { IpInfoService.lookup(lookup) }
                .onSuccess { lookupResult = it }
                .onFailure { error = it.message ?: copy.dnsLookupFailed }
            busy = false
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                CommandBackButton(copy.back, onBack)
                CommandSectionTitle(copy.dns, copy.dnsRecordsSubtitle, modifier = Modifier.weight(1f))
            }
        }
        item {
            CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.dnsConnection, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    OutlinedTextField(tokenDraft, { tokenDraft = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(copy.dnsApiToken) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        CommandPrimaryButton(copy.dnsSaveToken, {
                            apiToken = tokenDraft.trim()
                            Prefs.setCfToken(context, tokenDraft)
                            message = copy.dnsTokenSaved
                        }, icon = Icons.Rounded.Save)
                        CommandSecondaryButton(if (busy) copy.waitingForData else copy.dnsLoadZones, ::loadZones, enabled = !busy, icon = Icons.Rounded.Refresh)
                    }
                    Text(copy.dnsTokenBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE) }
        if (message != null) item { CommandStateBlock("Operation", message ?: "", CommandHealthTone.INFO) }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text("Zones", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    if (zones.isEmpty()) Text(copy.dnsNoZonesYet, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                        zones.forEach { zone -> CommandSecondaryButton("${zone.name} · ${zone.status}", { loadRecords(zone) }, enabled = selectedZone?.id != zone.id) }
                    }
                }
            }
        }
        if (selectedZone != null) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Records · ${selectedZone?.name}", Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                            CommandTextButton("New", { selectedRecord = null; recordName = ""; recordContent = "" }, icon = Icons.Rounded.Add)
                        }
                        if (records.isEmpty()) Text(copy.dnsZoneEmpty, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        records.forEach { record ->
                            Row(Modifier.fillMaxWidth().padding(vertical = CommandSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                                CommandStatusMark(record.type, CommandHealthTone.INFO, Modifier.weight(1f), "${record.name} → ${record.content}")
                                CommandTextButton("Edit", { chooseRecord(record) })
                                CommandTextButton("Delete", { deleteRecord = record }, icon = Icons.Rounded.DeleteOutline)
                            }
                        }
                    }
                }
            }
            item {
                CommandSurface(raised = true, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Text(if (selectedRecord == null) copy.dnsCreateRecord else copy.dnsEditRecord, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            listOf("A", "AAAA", "CNAME", "TXT", "MX", "NS", "CAA").forEach { candidate -> CommandSecondaryButton(candidate, { recordType = candidate }, enabled = recordType != candidate) }
                        }
                        OutlinedTextField(recordName, { recordName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                        OutlinedTextField(recordContent, { recordContent = it }, Modifier.fillMaxWidth(), minLines = 2, label = { Text("Content") })
                        Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(recordTtl, { recordTtl = it.filter(Char::isDigit).take(6) }, Modifier.width(130.dp), singleLine = true, label = { Text("TTL") })
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(recordProxied, { recordProxied = it })
                                Text("Proxied", color = CommandColors.textSecondary)
                            }
                        }
                        CommandPrimaryButton(if (busy) copy.waitingForData else copy.dnsSaveRecord, ::saveRecord, enabled = !busy, icon = Icons.Rounded.Save)
                    }
                }
            }
        }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.dnsDiagnosis, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(lookup, { lookup = it }, Modifier.weight(1f), singleLine = true, label = { Text(copy.dnsDomainOrIp) })
                        CommandPrimaryButton("Lookup", ::runLookup, enabled = !busy, icon = Icons.Rounded.Dns)
                    }
                    lookupResult?.let { result ->
                        CommandStatusMark("${result.flag} ${result.country} · ${result.ip}", CommandHealthTone.INFO, detail = "${result.reverseDns.ifBlank { copy.dnsNoPtr }} · ${result.isp.ifBlank { "No ISP" }}")
                        if (result.dnsRecords.isEmpty()) Text(copy.dnsNoPublicAnswer, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        result.dnsRecords.take(30).forEach { record ->
                            Text("${record.type} ${record.name} → ${record.data} · TTL ${record.ttl}", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    if (deleteRecord != null) {
        val record = deleteRecord!!
        AlertDialog(
            onDismissRequest = { deleteRecord = null },
            title = { Text(copy.dnsDeleteTitle, fontWeight = FontWeight.Bold) },
            text = { Text(copy.dnsDeleteBody.replace("%1", record.type).replace("%2", record.name)) },
            confirmButton = {
                TextButton(onClick = {
                    val zone = selectedZone
                    deleteRecord = null
                    if (zone != null) {
                        busy = true
                        scope.launch {
                            runCatching { CloudflareService.deleteRecord(apiToken, zone.id, record.id) }
                                .onSuccess { message = copy.dnsRecordDeleted; loadRecords(zone) }
                                .onFailure { error = it.message ?: copy.dnsRecordDeleteFailed }
                            busy = false
                        }
                    }
                }) { Text(copy.delete, color = CommandColors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleteRecord = null }) { Text(copy.cancel) } }
        )
    }
}
