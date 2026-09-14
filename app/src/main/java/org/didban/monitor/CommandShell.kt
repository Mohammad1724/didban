package org.didban.monitor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** سطح‌های اصلی ناوبری بوم خالی. */
enum class CommandWorkspace(val key: String) {
    OBSERVE("observe"),
    FLEET("fleet"),
    OPERATE("operate"),
    DIAGNOSE("diagnose"),
    WORKBENCH("workbench"),
    PROTECT("protect");

    companion object {
        fun fromKey(key: String): CommandWorkspace = values().firstOrNull { it.key == key } ?: OBSERVE
    }
}

/** مقصدهای عملیاتی؛ هیچ مقصدی از Pager فعلی کپی نشده است. */
enum class CommandRoute(val key: String, val workspace: CommandWorkspace) {
    OVERVIEW("overview", CommandWorkspace.OBSERVE),
    INCIDENTS("incidents", CommandWorkspace.OBSERVE),

    FLEET("fleet", CommandWorkspace.FLEET),
    SERVER_DOSSIER("server-dossier", CommandWorkspace.FLEET),
    MANAGE_SERVERS("manage-servers", CommandWorkspace.FLEET),

    TUNNELS("tunnels", CommandWorkspace.OPERATE),
    DOCKER("docker", CommandWorkspace.OPERATE),
    PROCESSES("processes", CommandWorkspace.OPERATE),
    SERVICES("services", CommandWorkspace.OPERATE),

    RADAR("radar", CommandWorkspace.DIAGNOSE),
    UPTIME("uptime", CommandWorkspace.DIAGNOSE),
    NETWORK_TOOLS("network-tools", CommandWorkspace.DIAGNOSE),
    DNS("dns", CommandWorkspace.DIAGNOSE),

    SSH("ssh", CommandWorkspace.WORKBENCH),
    BATCH("batch", CommandWorkspace.WORKBENCH),
    SFTP("sftp", CommandWorkspace.WORKBENCH),
    SINGLE_PORT("single-port", CommandWorkspace.WORKBENCH),
    PROXY("proxy", CommandWorkspace.WORKBENCH),
    DEVELOPER_LAB("developer-lab", CommandWorkspace.WORKBENCH),

    VAULT("vault", CommandWorkspace.PROTECT),
    ALERTS("alerts", CommandWorkspace.PROTECT),
    BACKUP("backup", CommandWorkspace.PROTECT),
    SETTINGS("settings", CommandWorkspace.PROTECT);

    companion object {
        fun fromKey(key: String): CommandRoute = values().firstOrNull { it.key == key } ?: OVERVIEW
    }
}

private fun CommandWorkspace.label(copy: CommandCopy): String = when (this) {
    CommandWorkspace.OBSERVE -> copy.observe
    CommandWorkspace.FLEET -> copy.fleet
    CommandWorkspace.OPERATE -> copy.operate
    CommandWorkspace.DIAGNOSE -> copy.diagnose
    CommandWorkspace.WORKBENCH -> copy.workbench
    CommandWorkspace.PROTECT -> copy.protect
}

private fun CommandWorkspace.icon(): ImageVector = when (this) {
    CommandWorkspace.OBSERVE -> Icons.Rounded.MonitorHeart
    CommandWorkspace.FLEET -> Icons.Rounded.Groups
    CommandWorkspace.OPERATE -> Icons.Rounded.Build
    CommandWorkspace.DIAGNOSE -> Icons.Rounded.NetworkCheck
    CommandWorkspace.WORKBENCH -> Icons.Rounded.Terminal
    CommandWorkspace.PROTECT -> Icons.Rounded.Security
}

private fun CommandRoute.label(copy: CommandCopy): String = when (this) {
    CommandRoute.OVERVIEW -> copy.overview
    CommandRoute.INCIDENTS -> copy.incidents
    CommandRoute.FLEET -> copy.servers
    CommandRoute.SERVER_DOSSIER -> copy.serverDossier
    CommandRoute.MANAGE_SERVERS -> copy.manageServers
    CommandRoute.TUNNELS -> copy.tunnels
    CommandRoute.DOCKER -> copy.docker
    CommandRoute.PROCESSES -> copy.processes
    CommandRoute.SERVICES -> copy.services
    CommandRoute.RADAR -> copy.radar
    CommandRoute.UPTIME -> copy.uptime
    CommandRoute.NETWORK_TOOLS -> copy.networkTools
    CommandRoute.DNS -> copy.dns
    CommandRoute.SSH -> copy.ssh
    CommandRoute.BATCH -> copy.batch
    CommandRoute.SFTP -> copy.sftp
    CommandRoute.SINGLE_PORT -> copy.singlePort
    CommandRoute.PROXY -> copy.proxy
    CommandRoute.DEVELOPER_LAB -> copy.developerLab
    CommandRoute.VAULT -> copy.vault
    CommandRoute.ALERTS -> copy.alerts
    CommandRoute.BACKUP -> copy.backup
    CommandRoute.SETTINGS -> copy.settings
}

private fun CommandRoute.icon(): ImageVector = when (this) {
    CommandRoute.OVERVIEW -> Icons.Rounded.MonitorHeart
    CommandRoute.INCIDENTS -> Icons.Rounded.Timeline
    CommandRoute.FLEET -> Icons.Rounded.Groups
    CommandRoute.SERVER_DOSSIER -> Icons.Rounded.Storage
    CommandRoute.MANAGE_SERVERS -> Icons.Rounded.Settings
    CommandRoute.TUNNELS -> Icons.Rounded.Hub
    CommandRoute.DOCKER -> Icons.Rounded.Widgets
    CommandRoute.PROCESSES -> Icons.Rounded.ListAlt
    CommandRoute.SERVICES -> Icons.Rounded.Tune
    CommandRoute.RADAR -> Icons.Rounded.Public
    CommandRoute.UPTIME -> Icons.Rounded.MonitorHeart
    CommandRoute.NETWORK_TOOLS -> Icons.Rounded.NetworkCheck
    CommandRoute.DNS -> Icons.Rounded.Dns
    CommandRoute.SSH -> Icons.Rounded.Terminal
    CommandRoute.BATCH -> Icons.Rounded.Groups
    CommandRoute.SFTP -> Icons.Rounded.Folder
    CommandRoute.SINGLE_PORT -> Icons.Rounded.Router
    CommandRoute.PROXY -> Icons.Rounded.Public
    CommandRoute.DEVELOPER_LAB -> Icons.Rounded.Code
    CommandRoute.VAULT -> Icons.Rounded.Security
    CommandRoute.ALERTS -> Icons.Rounded.NotificationsNone
    CommandRoute.BACKUP -> Icons.Rounded.Storage
    CommandRoute.SETTINGS -> Icons.Rounded.Settings
}

private fun routesFor(workspace: CommandWorkspace): List<CommandRoute> =
    CommandRoute.values().filter { it.workspace == workspace }

@Composable
fun CommandCenterApp(pendingServerId: MutableState<Long?>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var language by rememberSaveable { mutableStateOf(Prefs.getLanguage(context)) }
    var themeMode by rememberSaveable { mutableStateOf(Prefs.getThemeMode(context)) }
    var routeKey by rememberSaveable { mutableStateOf(CommandRoute.OVERVIEW.key) }
    var selectedServerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var mobileNavigationOpen by rememberSaveable { mutableStateOf(false) }

    val copy = remember(language) { CommandCopy.forLanguage(language) }
    val servers = remember(reloadTick) { Prefs.loadServers(context).toList() }
    val route = CommandRoute.fromKey(routeKey)
    val selectedServer = selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }
    val states by Repo.states.collectAsState()

    fun navigate(next: CommandRoute, server: ServerConfig? = null) {
        routeKey = next.key
        if (server != null) selectedServerId = server.id
        if (next == CommandRoute.FLEET || next == CommandRoute.OVERVIEW || next == CommandRoute.INCIDENTS) {
            if (next != CommandRoute.SERVER_DOSSIER) selectedServerId = null
        }
        reloadTick++
        mobileNavigationOpen = false
    }

    LaunchedEffect(pendingServerId.value, servers) {
        val id = pendingServerId.value ?: return@LaunchedEffect
        val server = servers.firstOrNull { it.id == id }
        if (server != null) navigate(CommandRoute.SERVER_DOSSIER, server)
        pendingServerId.value = null
    }

    CommandTheme(themeMode = themeMode, language = language) {
        val view = LocalView.current
        val darkBars = themeMode == "dark" || (themeMode == "auto" && androidx.compose.foundation.isSystemInDarkTheme())
        if (!view.isInEditMode) {
            SideEffect {
                runCatching {
                    view.context.findActivity()?.window?.let { window ->
                        window.statusBarColor = CommandColors.canvas.toArgb()
                        window.navigationBarColor = CommandColors.canvas.toArgb()
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkBars
                        controller.isAppearanceLightNavigationBars = !darkBars
                    }
                }
            }
        }

        BackHandler(enabled = route != CommandRoute.OVERVIEW || mobileNavigationOpen) {
            when {
                mobileNavigationOpen -> mobileNavigationOpen = false
                route == CommandRoute.SERVER_DOSSIER -> navigate(CommandRoute.FLEET)
                route.workspace != CommandWorkspace.OBSERVE -> navigate(workspaceDefault(route.workspace))
                route == CommandRoute.INCIDENTS -> navigate(CommandRoute.OVERVIEW)
                else -> Unit
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(CommandColors.canvas)) {
            val wide = maxWidth >= 680.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    CommandRail(
                        copy = copy,
                        route = route,
                        expanded = maxWidth >= 920.dp,
                        onNavigate = ::navigate,
                        modifier = Modifier.width(if (maxWidth >= 920.dp) 224.dp else 86.dp)
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        CommandScopeBar(
                            copy = copy,
                            route = route,
                            selectedServer = selectedServer,
                            servers = servers,
                            onSelectedServer = { server ->
                                if (server != null) {
                                    selectedServerId = server.id
                                    navigate(CommandRoute.SERVER_DOSSIER, server)
                                } else {
                                    selectedServerId = null
                                    if (route == CommandRoute.SERVER_DOSSIER) navigate(CommandRoute.FLEET)
                                }
                            },
                            onRefresh = {
                                servers.forEach { PollingCoordinator.requestNow(it.id) }
                            }
                        )
                        CommandRouteContent(
                            copy = copy,
                            route = route,
                            selectedServer = selectedServer,
                            states = states,
                            reloadTick = reloadTick,
                            themeMode = themeMode,
                            language = language,
                            onNavigate = ::navigate,
                            onRefresh = {
                                servers.forEach { PollingCoordinator.requestNow(it.id) }
                            },
                            onManageServers = { navigate(CommandRoute.MANAGE_SERVERS) },
                            onThemeChange = { mode ->
                                themeMode = mode
                                Prefs.setThemeMode(context, mode)
                            },
                            onLanguageChange = { next ->
                                language = next
                                Prefs.setLanguage(context, next)
                            }
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    CommandMobileHeader(
                        copy = copy,
                        route = route,
                        selectedServer = selectedServer,
                        navigationOpen = mobileNavigationOpen,
                        onToggleNavigation = { mobileNavigationOpen = !mobileNavigationOpen },
                        onRefresh = { servers.forEach { PollingCoordinator.requestNow(it.id) } }
                    )
                    if (mobileNavigationOpen) {
                        CommandMobileNavigation(copy, route, ::navigate)
                    }
                    CommandScopeBar(
                        copy = copy,
                        route = route,
                        selectedServer = selectedServer,
                        servers = servers,
                        onSelectedServer = { server ->
                            if (server != null) {
                                selectedServerId = server.id
                                navigate(CommandRoute.SERVER_DOSSIER, server)
                            } else {
                                selectedServerId = null
                                if (route == CommandRoute.SERVER_DOSSIER) navigate(CommandRoute.FLEET)
                            }
                        },
                        onRefresh = { servers.forEach { PollingCoordinator.requestNow(it.id) } }
                    )
                    CommandRouteContent(
                        copy = copy,
                        route = route,
                        selectedServer = selectedServer,
                        states = states,
                        reloadTick = reloadTick,
                        themeMode = themeMode,
                        language = language,
                        onNavigate = ::navigate,
                        onRefresh = { servers.forEach { PollingCoordinator.requestNow(it.id) } },
                        onManageServers = { navigate(CommandRoute.MANAGE_SERVERS) },
                        onThemeChange = { mode ->
                            themeMode = mode
                            Prefs.setThemeMode(context, mode)
                        },
                        onLanguageChange = { next ->
                            language = next
                            Prefs.setLanguage(context, next)
                        }
                    )
                }
            }
        }
    }
}

private fun workspaceDefault(workspace: CommandWorkspace): CommandRoute = when (workspace) {
    CommandWorkspace.OBSERVE -> CommandRoute.OVERVIEW
    CommandWorkspace.FLEET -> CommandRoute.FLEET
    CommandWorkspace.OPERATE -> CommandRoute.TUNNELS
    CommandWorkspace.DIAGNOSE -> CommandRoute.RADAR
    CommandWorkspace.WORKBENCH -> CommandRoute.SSH
    CommandWorkspace.PROTECT -> CommandRoute.VAULT
}

@Composable
private fun CommandRail(
    copy: CommandCopy,
    route: CommandRoute,
    expanded: Boolean,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(CommandColors.surface)
            .padding(vertical = CommandSpacing.md),
        verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        Text(
            "DIDBAN",
            modifier = Modifier.padding(horizontal = if (expanded) CommandSpacing.md else CommandSpacing.sm),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = CommandColors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(CommandSpacing.sm))
        CommandRule()
        Spacer(Modifier.height(CommandSpacing.sm))
        CommandWorkspace.values().forEach { workspace ->
            val active = route.workspace == workspace
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(workspaceDefault(workspace), null) }
                    .background(if (active) CommandColors.infoSurface else Color.Transparent)
                    .padding(horizontal = if (expanded) CommandSpacing.md else CommandSpacing.sm, vertical = CommandSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
            ) {
                Icon(workspace.icon(), contentDescription = workspace.label(copy), tint = if (active) CommandColors.accent else CommandColors.textSecondary, modifier = Modifier.size(21.dp))
                if (expanded) {
                    Spacer(Modifier.width(CommandSpacing.sm))
                    Text(workspace.label(copy), color = if (active) CommandColors.textPrimary else CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                }
            }
            if (active && expanded) {
                routesFor(workspace).forEach { destination ->
                    val selected = destination == route
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(destination, null) }
                            .padding(start = CommandSpacing.xl, end = CommandSpacing.sm, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(destination.icon(), contentDescription = destination.label(copy), tint = if (selected) CommandColors.accent else CommandColors.textTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(CommandSpacing.xs))
                        Text(destination.label(copy), color = if (selected) CommandColors.textPrimary else CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandMobileHeader(
    copy: CommandCopy,
    route: CommandRoute,
    selectedServer: ServerConfig?,
    navigationOpen: Boolean,
    onToggleNavigation: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CommandColors.surface)
            .padding(horizontal = CommandSpacing.sm, vertical = CommandSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommandIconButton(Icons.Rounded.Menu, if (navigationOpen) copy.close else copy.observe, onToggleNavigation)
        Column(Modifier.weight(1f)) {
            Text(route.label(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(selectedServer?.name ?: copy.allSystems, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CommandIconButton(Icons.Rounded.Refresh, copy.refresh, onRefresh)
    }
}

@Composable
private fun CommandMobileNavigation(
    copy: CommandCopy,
    route: CommandRoute,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CommandColors.surfaceRaised)
            .padding(CommandSpacing.sm)
            .verticalScroll(rememberScrollState())
    ) {
        CommandWorkspace.values().forEach { workspace ->
            Text(workspace.label(copy), color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = CommandSpacing.xs, vertical = CommandSpacing.xs))
            routesFor(workspace).forEach { destination ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(destination, null) }
                        .background(if (route == destination) CommandColors.infoSurface else Color.Transparent)
                        .padding(horizontal = CommandSpacing.sm, vertical = CommandSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(destination.icon(), contentDescription = destination.label(copy), tint = if (route == destination) CommandColors.accent else CommandColors.textSecondary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(CommandSpacing.sm))
                    Text(destination.label(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(CommandSpacing.xs))
        }
    }
}

@Composable
private fun CommandScopeBar(
    copy: CommandCopy,
    route: CommandRoute,
    selectedServer: ServerConfig?,
    servers: List<ServerConfig>,
    onSelectedServer: (ServerConfig?) -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(CommandColors.canvas)
            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(copy.scopeLabel(route, copy), color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Box {
                Row(
                    Modifier
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedServer?.name ?: copy.allSystems, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(CommandSpacing.xs))
                    Text("⌄", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(copy.allSystems) },
                        onClick = { expanded = false; onSelectedServer(null) }
                    )
                    servers.forEach { server ->
                        DropdownMenuItem(
                            text = { Text(server.name) },
                            onClick = { expanded = false; onSelectedServer(server) }
                        )
                    }
                }
            }
        }
        Text(copy.sync, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(CommandSpacing.xs))
        CommandIconButton(Icons.Rounded.Refresh, copy.refresh, onRefresh)
    }
}

private fun CommandCopy.scopeLabel(route: CommandRoute, copy: CommandCopy): String = when (route.workspace) {
    CommandWorkspace.OBSERVE -> copy.observe
    CommandWorkspace.FLEET -> copy.fleet
    CommandWorkspace.OPERATE -> copy.operate
    CommandWorkspace.DIAGNOSE -> copy.diagnose
    CommandWorkspace.WORKBENCH -> copy.workbench
    CommandWorkspace.PROTECT -> copy.protect
}

@Composable
private fun CommandRouteContent(
    copy: CommandCopy,
    route: CommandRoute,
    selectedServer: ServerConfig?,
    states: Map<Long, Repo.State>,
    reloadTick: Int,
    themeMode: String,
    language: String,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit,
    onRefresh: () -> Unit,
    onManageServers: () -> Unit,
    onThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    when (route) {
        CommandRoute.OVERVIEW -> CommandOverviewScreen(copy, reloadTick, { onNavigate(CommandRoute.SERVER_DOSSIER, it) }, { onNavigate(CommandRoute.INCIDENTS, null) }, { onNavigate(CommandRoute.FLEET, null) }, onManageServers, onRefresh)
        CommandRoute.INCIDENTS -> CommandIncidentsScreen(copy, reloadTick, { onNavigate(CommandRoute.SERVER_DOSSIER, it) }, onRefresh)
        CommandRoute.FLEET -> CommandFleetScreen(copy, reloadTick, { onNavigate(CommandRoute.SERVER_DOSSIER, it) }, onManageServers, onRefresh)
        CommandRoute.SERVER_DOSSIER -> CommandServerDossierScreen(copy, selectedServer, selectedServer?.let { states[it.id] }, { onNavigate(CommandRoute.FLEET, null) }, onRefresh, onManageServers, { onNavigate(CommandRoute.PROCESSES, selectedServer) }, { onNavigate(CommandRoute.DOCKER, selectedServer) }, { onNavigate(CommandRoute.TUNNELS, selectedServer) })
        CommandRoute.SETTINGS -> CommandSettingsScreen(copy, themeMode, language, onThemeChange, onLanguageChange)
        else -> CommandLegacySurface(
            route = route,
            copy = copy,
            selectedServer = selectedServer,
            themeMode = themeMode,
            language = language,
            onNavigate = onNavigate,
            onManageServers = onManageServers,
            onThemeChange = onThemeChange,
            onLanguageChange = onLanguageChange
        )
    }
}

/**
 * Bridge موقت برای حفظ دسترسی به functionality واقعی در حین مهاجرت. این
 * Wrapper قرار نیست زبان بصری نهایی باشد و با تکمیل هر Workspace حذف می‌شود.
 */
@Composable
private fun CommandLegacySurface(
    route: CommandRoute,
    copy: CommandCopy,
    selectedServer: ServerConfig?,
    themeMode: String,
    language: String,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit,
    onManageServers: () -> Unit,
    onThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val legacyStrings = if (language == "fa") Locales.fa else Locales.en
    Column(Modifier.fillMaxSize()) {
        CommandSurface(modifier = Modifier.fillMaxWidth(), raised = true) {
            Column(Modifier.padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CommandBackButton(copy.back) { onNavigate(workspaceDefault(route.workspace), null) }
                    Spacer(Modifier.width(CommandSpacing.sm))
                    Text(route.label(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(CommandSpacing.xs))
                Text(copy.legacyBridgeBody, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.weight(1f)) {
            AeroTheme(mode = AeroThemeMode.fromId(themeMode), content = {
                when (route) {
                    CommandRoute.MANAGE_SERVERS -> ServersScreen(
                        t = legacyStrings,
                        isDarkMode = themeMode == "dark",
                        onToggleTheme = { onThemeChange(if (themeMode == "dark") "light" else "dark") },
                        onLanguage = onLanguageChange,
                        onOpen = { server -> onNavigate(CommandRoute.SERVER_DOSSIER, server) }
                    )
                    CommandRoute.TUNNELS -> AeroTunnelFleetScreen(legacyStrings, Prefs.loadTunnels(context), onRefresh = {})
                    CommandRoute.UPTIME -> UptimeScreen(legacyStrings)
                    CommandRoute.NETWORK_TOOLS, CommandRoute.RADAR -> NetworkHubScreen(legacyStrings)
                    CommandRoute.DNS -> CloudflareScreen(legacyStrings)
                    CommandRoute.SSH -> SshTerminalScreen(legacyStrings, selectedServer?.id)
                    CommandRoute.BATCH -> BatchExecScreen(legacyStrings)
                    CommandRoute.SFTP -> SftpScreen(legacyStrings)
                    CommandRoute.SINGLE_PORT -> SinglePortScreen(legacyStrings)
                    CommandRoute.PROXY -> ProxyTesterScreen(legacyStrings)
                    CommandRoute.DEVELOPER_LAB -> DevLabScreen(legacyStrings)
                    CommandRoute.VAULT -> VaultScreen(legacyStrings)
                    CommandRoute.ALERTS -> AlertsHubScreen(legacyStrings)
                    CommandRoute.BACKUP -> BackupRestoreScreen(legacyStrings)
                    CommandRoute.DOCKER, CommandRoute.PROCESSES -> {
                        if (selectedServer != null) {
                            AeroCockpitScreen(legacyStrings, selectedServer, themeMode == "dark", { }, { onNavigate(CommandRoute.SERVER_DOSSIER, selectedServer) })
                        } else {
                            CommandStateBlock(copy.selectServer, copy.noServerSelected, CommandHealthTone.INFO, copy.servers) { onNavigate(CommandRoute.FLEET, null) }
                        }
                    }
                    CommandRoute.SERVICES -> SystemdScreen(legacyStrings)
                    else -> CommandStateBlock(copy.stagedWorkspace, copy.legacyBridgeBody, CommandHealthTone.INFO, copy.back) { onNavigate(workspaceDefault(route.workspace), null) }
                }
            })
        }
    }
}
EOF