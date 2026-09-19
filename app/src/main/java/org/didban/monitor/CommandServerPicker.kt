package org.didban.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Choosing an existing connection is not navigation to the connection editor. */
@Composable
internal fun CommandServerPicker(
    copy: CommandCopy,
    servers: List<ServerConfig>,
    loadFailed: Boolean,
    onSelect: (ServerConfig) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit
) {
    val language = Prefs.getLanguage(LocalContext.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(copy.selectServer) },
        text = {
            when {
                loadFailed -> Text(securityMessage(language, SecurityMessage.SERVER_READ_FAILED))
                servers.isEmpty() -> Text(copy.noServersBody)
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(servers, key = { it.id }) { server ->
                        OutlinedButton(
                            onClick = { onSelect(server) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${server.host}:${server.port}", style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                loadFailed -> TextButton(onClick = onRetry) { Text(copy.retry) }
                servers.isEmpty() -> TextButton(onClick = onAdd) { Text(copy.addServer) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(copy.cancel) } }
    )
}
