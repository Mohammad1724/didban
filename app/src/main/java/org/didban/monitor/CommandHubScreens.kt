package org.didban.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CommandWorkbenchIndexScreen(
    copy: CommandCopy,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    CommandToolIndex(copy.uiTools, copy.uiToolsIntro,
        listOf(copy.uiPathTools to independentToolRoutes.take(2),
            copy.uiNetworkTools to independentToolRoutes.subList(2, 4),
            copy.uiMoreTools to independentToolRoutes.drop(4)),
        copy, Icons.Rounded.Tune, onNavigate)

}

@Composable
fun CommandProtectIndexScreen(
    copy: CommandCopy,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    CommandToolIndex(
        title = copy.protect,
        body = copy.hubProtectBody,
        groups = listOf(
            copy.hubSecretTrust to listOf(CommandRoute.VAULT),
            copy.hubAlertDelivery to listOf(CommandRoute.ALERTS),
            copy.hubDataRecovery to listOf(CommandRoute.BACKUP),
            copy.hubAppBehaviour to listOf(CommandRoute.SETTINGS)
        ),
        copy = copy,
        icon = Icons.Rounded.Security,
        onNavigate = onNavigate
    )
}

@Composable
private fun CommandToolIndex(
    title: String,
    body: String,
    groups: List<Pair<String, List<CommandRoute>>>,
    copy: CommandCopy,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)) {
        item { CommandSectionTitle(title) }
        item { Text(body, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
        groups.forEach { (groupTitle, routes) ->
            item { CommandSectionTitle(groupTitle) }
            routes.forEach { route -> item(key = route.key) {
                CommandToolLink(copy, route,
                    detail = route.helpContent(if (copy === CommandCopyFa) "fa" else "en").summary,
                    onClick = { onNavigate(route, null) })
            } }
        }
        item { Text(copy.uiServerToolsHint, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        item { Spacer(Modifier.height(24.dp)) }
    }

}

fun CommandRoute.commandIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    CommandRoute.WORKBENCH_HOME -> Icons.Rounded.Terminal
    CommandRoute.SSH -> Icons.Rounded.Terminal
    CommandRoute.BATCH -> Icons.Rounded.Tune
    CommandRoute.SFTP -> Icons.Rounded.Folder
    CommandRoute.SINGLE_PORT -> Icons.Rounded.Tune
    CommandRoute.PROXY -> Icons.Rounded.Tune
    CommandRoute.DEVELOPER_LAB -> Icons.Rounded.Code
    CommandRoute.PROTECT_HOME -> Icons.Rounded.Security
    else -> Icons.Rounded.Tune
}
