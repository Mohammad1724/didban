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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CommandDnsManagerScreen(copy: CommandCopy, onBack: () -> Unit) {
    SecureWindowEffect()
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
    var job by remember { mutableStateOf<Job?>(null) }
    var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteRecord by remember { mutableStateOf<CfRecord?>(null) }
    var deleteZoneId by remember { mutableStateOf<String?>(null) }

    fun cancel() {
        job?.cancel()
        job = null
        busy = false
    }

    fun loadZones() {
        if (busy) return
        if (apiToken.isBlank()) {
            error = copy.dnsNoToken
            retryAction = ::loadZones
            return
        }
        busy = true
        error = null
        message = null
        retryAction = ::loadZones
        job = scope.launch {
            try {
                val loadedZones = CloudflareService.listZones(apiToken)
                zones = loadedZones
                val first = loadedZones.firstOrNull()
                selectedZone = first
                records = if (first == null) emptyList() else CloudflareService.listRecords(apiToken, first.id)
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.dnsZonesLoadFailed
            } finally {
                busy = false
                job = null
            }
        }
    }

    fun loadRecords(zone: CfZone) {
        if (busy) return
        selectedZone = zone
        selectedRecord = null
        recordName = ""
        recordContent = ""
        busy = true
        error = null
        message = null
        retryAction = { loadRecords(zone) }
        job = scope.launch {
            try {
                val loaded = CloudflareService.listRecords(apiToken, zone.id)
                records = loaded
                message = copy.dnsRecordsLoaded.replace("%1", loaded.size.toString())
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.dnsRecordsLoadFailed
            } finally {
                busy = false
                job = null
            }
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
            retryAction = ::saveRecord
            return
        }
        busy = true
        error = null
        message = null
        retryAction = ::saveRecord
        job = scope.launch {
            try {
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
                message = if (selectedRecord == null) copy.dnsRecordCreated else copy.dnsRecordUpdated
                records = CloudflareService.listRecords(apiToken, zone.id)
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.dnsRecordSaveFailed
            } finally {
                busy = false
                job = null
            }
        }
    }

    fun runLookup() {
        if (lookup.isBlank()) return
        busy = true
        error = null
        message = null
        lookupResult = null
        retryAction = ::runLookup
        job = scope.launch {
            try {
                lookupResult = IpInfoService.lookup(lookup)
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message ?: copy.dnsLookupFailed
            } finally {
                busy = false
                job = null
            }
        }
    }

    fun deleteRecordNow(zone: CfZone, record: CfRecord) {
        if (busy) return
        busy = true
        error = null
        message = null
        retryAction = { deleteRecordNow(zone, record) }
        job = scope.launch {
            try {
                val current = CloudflareService.listRecords(apiToken, zone.id)
                    .firstOrNull { it.id == record.id }
                check(current != null && current.type == record.type && current.name == record.name) {
                    copy.dnsRecordDeleteFailed
                }
                CloudflareService.deleteRecord(apiToken, zone.id, current.id)
                message = copy.dnsRecordDeleted
                records = CloudflareService.listRecords(apiToken, zone.id)
                retryAction = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = SecretRedactor.redact(
                    e.message ?: copy.dnsRecordDeleteFailed,
                    listOf(apiToken)
                ).take(300)
            } finally {
                busy = false
                job = null
            }
        }
    }

    val retry = retryAction
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
                    OutlinedTextField(tokenDraft, { tokenDraft = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.dnsApiToken) }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                    CommandResponsiveRow {
                        CommandPrimaryButton(
                            copy.dnsSaveToken,
                            {
                                runCatching { Prefs.setCfToken(context, tokenDraft) }
                                    .onSuccess {
                                        apiToken = tokenDraft.trim()
                                        message = copy.dnsTokenSaved
                                        error = null
                                        retryAction = null
                                    }
                                    .onFailure {
                                        error = it.message ?: copy.operationFailed
                                        message = null
                                    }
                            },
                            modifier = item(),
                            enabled = !busy,
                            icon = Icons.Rounded.Save
                        )
                        CommandSecondaryButton(
                            if (busy) copy.waitingForData else copy.dnsLoadZones,
                            ::loadZones,
                            modifier = item(),
                            enabled = !busy,
                            icon = Icons.Rounded.Refresh
                        )
                        if (busy) {
                            CommandSecondaryButton(copy.stop, ::cancel, modifier = item())
                        }
                    }
                    Text(copy.dnsTokenBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (error != null) item { CommandStateBlock(copy.operationFailed, error ?: "", CommandHealthTone.OFFLINE, copy.retry, retry) }
        if (message != null) item { CommandStateBlock(copy.dnsOperation, message ?: "", CommandHealthTone.INFO) }
        if (busy) item { CommandInlineLoading(copy.waitingForData) }
        item {
            CommandSurface(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                    Text(copy.dnsZones, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                    if (zones.isEmpty()) {
                        CommandEmptyState(copy.dnsZones, copy.dnsNoZonesYet)
                    } else {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                            zones.forEach { zone -> CommandSecondaryButton("${zone.name} · ${zone.status}", { loadRecords(zone) }, enabled = !busy && selectedZone?.id != zone.id) }
                        }
                    }
                }
            }
        }
        if (selectedZone != null) {
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(copy.dnsRecords.replace("%1", selectedZone?.name.orEmpty()), Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = CommandColors.textPrimary)
                            CommandTextButton(copy.dnsNewRecord, { selectedRecord = null; recordName = ""; recordContent = "" }, enabled = !busy, icon = Icons.Rounded.Add)
                        }
                        if (records.isEmpty()) {
                            CommandEmptyState(
                                title = copy.dnsRecords.replace("%1", selectedZone?.name.orEmpty()),
                                body = copy.dnsZoneEmpty
                            )
                        }
                        records.forEach { record ->
                            CommandResponsiveRow {
                                CommandStatusMark(record.type, CommandHealthTone.INFO, item(weight = 1f), "${record.name} → ${record.content}")
                                CommandTextButton(copy.edit, { chooseRecord(record) }, modifier = item(), enabled = !busy)
                                CommandTextButton(copy.delete, { deleteRecord = record; deleteZoneId = selectedZone?.id }, modifier = item(), enabled = !busy, icon = Icons.Rounded.DeleteOutline)
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
                            listOf("A", "AAAA", "CNAME", "TXT", "MX", "NS", "CAA").forEach { candidate -> CommandSecondaryButton(candidate, { recordType = candidate }, enabled = !busy && recordType != candidate) }
                        }
                        OutlinedTextField(recordName, { recordName = it }, Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, label = { Text(copy.uiName) })
                        OutlinedTextField(recordContent, { recordContent = it }, Modifier.fillMaxWidth(), enabled = !busy, minLines = 2, label = { Text(copy.dnsContent) })
                        CommandResponsiveRow {
                            OutlinedTextField(recordTtl, { recordTtl = it.filter(Char::isDigit).take(6) }, item(width = CommandMetrics.formAuxFieldWidth), enabled = !busy, singleLine = true, label = { Text(copy.dnsTtl) })
                            Row(item(), verticalAlignment = Alignment.CenterVertically) {
                                Switch(recordProxied, { recordProxied = it }, enabled = !busy)
                                Text(copy.dnsProxied, color = CommandColors.textSecondary)
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
                    CommandResponsiveRow {
                        OutlinedTextField(lookup, { lookup = it }, item(weight = 1f), enabled = !busy, singleLine = true, label = { Text(copy.dnsDomainOrIp) })
                        CommandPrimaryButton(copy.dnsLookup, ::runLookup, enabled = !busy, icon = Icons.Rounded.Dns, modifier = item())
                    }
                    lookupResult?.let { result ->
                        CommandStatusMark("${result.flag} ${result.country} · ${result.ip}", CommandHealthTone.INFO, detail = "${result.reverseDns.ifBlank { copy.dnsNoPtr }} · ${result.isp.ifBlank { copy.dnsNoIsp }}")
                        if (result.dnsRecords.isEmpty()) Text(copy.dnsNoPublicAnswer, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        result.dnsRecords.take(30).forEach { record ->
                            Text(copy.dnsRecordSummary.replace("%1", record.type).replace("%2", record.name).replace("%3", record.data).replace("%4", record.ttl.toString()), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
    }

    val recordToDelete = deleteRecord
    if (recordToDelete != null) {
        val record = recordToDelete
        CommandDestructiveDialog(
            title = copy.dnsDeleteTitle,
            body = "${copy.dnsDeleteBody.replace("%1", record.type).replace("%2", record.name)}\n\n${selectedZone?.name.orEmpty()} · ${record.type} ${record.name} · ${record.id}",
            confirmLabel = copy.delete,
            dismissLabel = copy.cancel,
            onDismiss = { deleteRecord = null },
            enabled = !busy && selectedZone?.id == deleteZoneId,
            onConfirm = {
                val zone = selectedZone
                deleteRecord = null
                if (zone != null && zone.id == deleteZoneId && !busy) {
                    deleteRecordNow(zone, record)
                }
            }
        )
    }
}
