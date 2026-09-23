package org.didban.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Presentation-only grouping. Persisted route keys and the existing Back stack stay intact. */
internal enum class CommandPrimary(val root: CommandRoute) {
    SERVERS(CommandRoute.FLEET), MONITORING(CommandRoute.UPTIME), TOOLS(CommandRoute.WORKBENCH_HOME), SETTINGS(CommandRoute.SETTINGS)
}
internal fun CommandRoute.primary(): CommandPrimary = when (this) {
    CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.FLEET, CommandRoute.MANAGE_SERVERS,
    CommandRoute.SERVER_DOSSIER, CommandRoute.DOCKER, CommandRoute.PROCESSES, CommandRoute.SERVICES,
    CommandRoute.SSH, CommandRoute.SFTP, CommandRoute.SECURITY, CommandRoute.BANDWIDTH,
    CommandRoute.TUNNELS, CommandRoute.TUNNELS_EDITOR -> CommandPrimary.SERVERS
    CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR, CommandRoute.RADAR -> CommandPrimary.MONITORING
    CommandRoute.CHECK_HOST, CommandRoute.CF_SCANNER, CommandRoute.REALITY_SNI, CommandRoute.NETWORK_TOOLS,
    CommandRoute.NETWORK_TOOLS_EDITOR, CommandRoute.DNS, CommandRoute.DNS_EDITOR,
    CommandRoute.WORKBENCH_HOME, CommandRoute.BATCH, CommandRoute.SINGLE_PORT,
    CommandRoute.PROXY, CommandRoute.SHARE, CommandRoute.DEVELOPER_LAB -> CommandPrimary.TOOLS
    CommandRoute.PROTECT_HOME, CommandRoute.VAULT, CommandRoute.BACKUP,
    CommandRoute.ALERTS, CommandRoute.SETTINGS -> CommandPrimary.SETTINGS
}
internal val serverToolRoutes = listOf(CommandRoute.DOCKER, CommandRoute.SERVICES, CommandRoute.SSH,
    CommandRoute.SFTP, CommandRoute.PROCESSES, CommandRoute.BANDWIDTH, CommandRoute.TUNNELS, CommandRoute.SECURITY)
internal val networkToolRoutes = listOf(
    CommandRoute.CF_SCANNER, CommandRoute.REALITY_SNI, CommandRoute.NETWORK_TOOLS,
    CommandRoute.CHECK_HOST, CommandRoute.DNS
)
internal val workbenchUtilityRoutes = listOf(
    CommandRoute.PROXY, CommandRoute.SHARE, CommandRoute.SINGLE_PORT,
    CommandRoute.DEVELOPER_LAB, CommandRoute.BATCH
)
internal val independentToolRoutes = networkToolRoutes + workbenchUtilityRoutes
internal val settingsToolRoutes = listOf(CommandRoute.ALERTS, CommandRoute.VAULT, CommandRoute.BACKUP)
internal val LocalCommandHeader = staticCompositionLocalOf<String?> { null }

private fun CommandPrimary.label(copy: CommandCopy) = when (this) {
    CommandPrimary.SERVERS -> copy.uiServers
    CommandPrimary.MONITORING -> copy.uiMonitoring
    CommandPrimary.TOOLS -> copy.uiTools
    CommandPrimary.SETTINGS -> copy.settings
}
private fun CommandPrimary.icon(): ImageVector = when (this) {
    CommandPrimary.SERVERS -> Icons.Rounded.Dns
    CommandPrimary.MONITORING -> Icons.Rounded.MonitorHeart
    CommandPrimary.TOOLS -> Icons.Rounded.GridView
    CommandPrimary.SETTINGS -> Icons.Rounded.Settings
}

@Composable
internal fun CommandPrimaryNavigation(copy: CommandCopy, route: CommandRoute, rail: Boolean,
    onNavigate: (CommandRoute) -> Unit) {
    val selected = route.primary()
    @Composable fun entry(section: CommandPrimary, modifier: Modifier) {
        val active = section == selected
        Column(modifier.clip(RoundedCornerShape(CommandRadii.tile))
            .background(if (active) CommandColors.infoSurface else Color.Transparent)
            .border(1.dp, if (active) CommandColors.borderStrong.copy(alpha = .45f) else Color.Transparent, RoundedCornerShape(CommandRadii.tile))
            .testTag("primary-${section.name.lowercase()}")
            .selectable(active, role = Role.Tab, onClick = { onNavigate(section.root) })
            .padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(48.dp).height(30.dp).clip(RoundedCornerShape(CommandRadii.icon))
                .background(Color.Transparent), contentAlignment = Alignment.Center) {
                Icon(section.icon(), null, tint = if (active) CommandColors.accent else CommandColors.textSecondary, modifier = Modifier.size(21.dp))
            }
            Text(section.label(copy), color = if (active) CommandColors.accent else CommandColors.textSecondary,
                style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
    if (rail) {
        CommandLayerSurface(Modifier.padding(8.dp), chrome = true) {
            Column(Modifier.width(96.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandPrimary.values().forEach { entry(it, Modifier.fillMaxWidth()) }
            }
        }
    } else {
        CommandLayerSurface(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), chrome = true) {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CommandPrimary.values().forEach { entry(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun CommandPageChrome(title: String, copy: CommandCopy, language: String,
    showBack: Boolean, onBack: () -> Unit, onHelp: () -> Unit) {
    CommandLayerSurface(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), chrome = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) CommandIconButton(Icons.AutoMirrored.Rounded.ArrowBack, copy.back, onBack)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = CommandColors.textPrimary)
            CommandHelpButton(language, onHelp)
        }
    }
}

/** No hidden state resets when a section collapses: callers own their field state. */
@Composable
internal fun CommandDisclosure(copy: CommandCopy, title: String = copy.uiAdvanced, initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(CommandRadii.control))
            .semantics { stateDescription = if (expanded) copy.scannerHideList else copy.scannerShowList }
            .clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = CommandColors.textSecondary, style = MaterialTheme.typography.labelLarge)
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = CommandColors.textSecondary)
        }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun CommandToolLink(copy: CommandCopy, route: CommandRoute, detail: String? = null, onClick: () -> Unit) {
    CommandSurface(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(CommandColors.infoSurface), contentAlignment = Alignment.Center) {
                Icon(route.navIcon(), null, tint = CommandColors.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(route.commandLabel(copy), color = CommandColors.textPrimary, style = MaterialTheme.typography.titleMedium)
                if (detail != null) Text(detail, color = CommandColors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = CommandColors.textTertiary, modifier = Modifier.size(20.dp))
        }
    }
}
