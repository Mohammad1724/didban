package org.didban.monitor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CommandScannerImport(copy: CommandCopy, enabled: Boolean, onLoaded: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentLoaded by rememberUpdatedState(onLoaded)
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentEnabled) scope.launch {
            importing = true
            error = false
            try {
                val text = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)).use(ScannerCatalog::readText)
                }
                if (currentEnabled) currentLoaded(text)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { importing = false }
        }
    }
    CommandSecondaryButton(copy.scannerImportFile,
        { picker.launch(arrayOf("text/*", "application/octet-stream")) },
        enabled = enabled && !importing, modifier = Modifier.fillMaxWidth())
    if (error) Text(copy.scannerImportFailed, color = CommandColors.danger)
}

internal fun ScannerCatalog.Group.label(copy: CommandCopy): String = when (this) {
    ScannerCatalog.Group.ALL -> copy.scannerCategoryAll
    ScannerCatalog.Group.TECHNOLOGY -> copy.scannerCategoryTechnology
    ScannerCatalog.Group.DEVELOPMENT -> copy.scannerCategoryDevelopment
    ScannerCatalog.Group.KNOWLEDGE -> copy.scannerCategoryKnowledge
    ScannerCatalog.Group.SERVICES -> copy.scannerCategoryServices
}

@Composable
internal fun CommandSniCategory(copy: CommandCopy, selected: ScannerCatalog.Group, enabled: Boolean,
    onSelected: (ScannerCatalog.Group) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CommandSecondaryButton("${copy.scannerCategory}: ${selected.label(copy)}", { expanded = true },
            enabled = enabled, modifier = Modifier.fillMaxWidth())
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ScannerCatalog.Group.values().forEach { group ->
                DropdownMenuItem(text = { Text("${group.label(copy)} (${ScannerCatalog.domains(group).size})") },
                    onClick = { expanded = false; onSelected(group) }, enabled = enabled)
            }
        }
    }
}

@Composable
internal fun CommandScannerPreview(copy: CommandCopy, names: List<String>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${copy.scannerReadyLists} (${names.size})") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(names, key = { it }) { Text(it, fontFamily = Telemetry) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(copy.close) } })
}
