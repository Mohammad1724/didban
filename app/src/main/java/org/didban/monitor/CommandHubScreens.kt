package org.didban.monitor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ArrowForward
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
    val execute = listOf(CommandRoute.SSH, CommandRoute.BATCH)
    val transfer = listOf(CommandRoute.SFTP)
    val inspect = listOf(CommandRoute.SINGLE_PORT, CommandRoute.PROXY)
    val utilities = listOf(CommandRoute.DEVELOPER_LAB)
    CommandToolIndex(
        title = copy.workbench,
        body = "از سؤال عملیاتی شروع کنید؛ ابزار فقط در Context لازم باز می‌شود.",
        groups = listOf(
            "اجرا" to execute,
            "انتقال فایل" to transfer,
            "بررسی و تولید" to inspect,
            "ابزارهای توسعه" to utilities
        ),
        copy = copy,
        icon = Icons.Rounded.Terminal,
        onNavigate = onNavigate
    )
}

@Composable
fun CommandProtectIndexScreen(
    copy: CommandCopy,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    CommandToolIndex(
        title = copy.protect,
        body = "اطلاعات حساس، اعلان‌ها و بازیابی داده در یک فضای جدا از عملیات عادی.",
        groups = listOf(
            "Secret و Trust" to listOf(CommandRoute.VAULT),
            "تحویل هشدار" to listOf(CommandRoute.ALERTS),
            "داده و بازیابی" to listOf(CommandRoute.BACKUP),
            "رفتار برنامه" to listOf(CommandRoute.SETTINGS)
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
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.md)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = CommandSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(CommandSpacing.sm))
                Column {
                    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = CommandColors.textPrimary)
                    Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = CommandColors.textSecondary)
                }
            }
        }
        groups.forEach { (groupTitle, routes) ->
            item {
                Text(groupTitle, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = CommandColors.textTertiary)
            }
            item {
                CommandSurface(Modifier.fillMaxWidth()) {
                    Column {
                        routes.forEachIndexed { index, route ->
                            if (index > 0) CommandRule()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(route, null) }
                                    .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(route.commandIcon(), contentDescription = route.commandLabel(copy), tint = CommandColors.accent, modifier = Modifier.size(21.dp))
                                Spacer(Modifier.width(CommandSpacing.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(route.commandLabel(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                                    Text(copy.openExistingTool, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Rounded.ArrowForward, contentDescription = copy.openServer, tint = CommandColors.textTertiary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(CommandSpacing.xl)) }
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
