package org.didban.monitor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
    TUNNELS_EDITOR("tunnels-editor", CommandWorkspace.OPERATE),
    DOCKER("docker", CommandWorkspace.OPERATE),
    PROCESSES("processes", CommandWorkspace.OPERATE),
    SERVICES("services", CommandWorkspace.OPERATE),

    RADAR("radar", CommandWorkspace.DIAGNOSE),
    BANDWIDTH("bandwidth", CommandWorkspace.DIAGNOSE),
    UPTIME("uptime", CommandWorkspace.DIAGNOSE),
    UPTIME_EDITOR("uptime-editor", CommandWorkspace.DIAGNOSE),
    NETWORK_TOOLS("network-tools", CommandWorkspace.DIAGNOSE),
    NETWORK_TOOLS_EDITOR("network-tools-editor", CommandWorkspace.DIAGNOSE),
    DNS("dns", CommandWorkspace.DIAGNOSE),
    DNS_EDITOR("dns-editor", CommandWorkspace.DIAGNOSE),

    WORKBENCH_HOME("workbench-home", CommandWorkspace.WORKBENCH),
    SSH("ssh", CommandWorkspace.WORKBENCH),
    BATCH("batch", CommandWorkspace.WORKBENCH),
    SFTP("sftp", CommandWorkspace.WORKBENCH),
    SINGLE_PORT("single-port", CommandWorkspace.WORKBENCH),
    PROXY("proxy", CommandWorkspace.WORKBENCH),
    DEVELOPER_LAB("developer-lab", CommandWorkspace.WORKBENCH),

    PROTECT_HOME("protect-home", CommandWorkspace.PROTECT),
    VAULT("vault", CommandWorkspace.PROTECT),
    SECURITY("security", CommandWorkspace.PROTECT),
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

fun CommandRoute.commandLabel(copy: CommandCopy): String = when (this) {
    CommandRoute.OVERVIEW -> copy.overview
    CommandRoute.INCIDENTS -> copy.incidents
    CommandRoute.FLEET -> copy.servers
    CommandRoute.SERVER_DOSSIER -> copy.serverDossier
    CommandRoute.MANAGE_SERVERS -> copy.manageServers
    CommandRoute.TUNNELS, CommandRoute.TUNNELS_EDITOR -> copy.tunnels
    CommandRoute.DOCKER -> copy.docker
    CommandRoute.PROCESSES -> copy.processes
    CommandRoute.SERVICES -> copy.services
    CommandRoute.RADAR -> copy.radar
    CommandRoute.BANDWIDTH -> copy.bandwidth
    CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR -> copy.uptime
    CommandRoute.NETWORK_TOOLS, CommandRoute.NETWORK_TOOLS_EDITOR -> copy.networkTools
    CommandRoute.DNS, CommandRoute.DNS_EDITOR -> copy.dns
    CommandRoute.WORKBENCH_HOME -> copy.workbench
    CommandRoute.SSH -> copy.ssh
    CommandRoute.BATCH -> copy.batch
    CommandRoute.SFTP -> copy.sftp
    CommandRoute.SINGLE_PORT -> copy.singlePort
    CommandRoute.PROXY -> copy.proxy
    CommandRoute.DEVELOPER_LAB -> copy.developerLab
    CommandRoute.PROTECT_HOME -> copy.protect
    CommandRoute.VAULT -> copy.vault
    CommandRoute.SECURITY -> copy.security
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
    CommandRoute.TUNNELS, CommandRoute.TUNNELS_EDITOR -> Icons.Rounded.Hub
    CommandRoute.DOCKER -> Icons.Rounded.Widgets
    CommandRoute.PROCESSES -> Icons.AutoMirrored.Rounded.ListAlt
    CommandRoute.SERVICES -> Icons.Rounded.Tune
    CommandRoute.RADAR -> Icons.Rounded.Public
    CommandRoute.BANDWIDTH -> Icons.Rounded.Speed
    CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR -> Icons.Rounded.MonitorHeart
    CommandRoute.NETWORK_TOOLS, CommandRoute.NETWORK_TOOLS_EDITOR -> Icons.Rounded.NetworkCheck
    CommandRoute.DNS, CommandRoute.DNS_EDITOR -> Icons.Rounded.Dns
    CommandRoute.WORKBENCH_HOME -> Icons.Rounded.Terminal
    CommandRoute.SSH -> Icons.Rounded.Terminal
    CommandRoute.BATCH -> Icons.Rounded.Groups
    CommandRoute.SFTP -> Icons.Rounded.Folder
    CommandRoute.SINGLE_PORT -> Icons.Rounded.Router
    CommandRoute.PROXY -> Icons.Rounded.Public
    CommandRoute.DEVELOPER_LAB -> Icons.Rounded.Code
    CommandRoute.PROTECT_HOME -> Icons.Rounded.Security
    CommandRoute.VAULT -> Icons.Rounded.Security
    CommandRoute.SECURITY -> Icons.Rounded.Shield
    CommandRoute.ALERTS -> Icons.Rounded.NotificationsNone
    CommandRoute.BACKUP -> Icons.Rounded.Storage
    CommandRoute.SETTINGS -> Icons.Rounded.Settings
}

private fun routesFor(workspace: CommandWorkspace): List<CommandRoute> =
    CommandRoute.values().filter {
    it.workspace == workspace &&
        it != CommandRoute.TUNNELS_EDITOR &&
        it != CommandRoute.UPTIME_EDITOR &&
        it != CommandRoute.NETWORK_TOOLS_EDITOR &&
        it != CommandRoute.DNS_EDITOR
}

@Composable
fun CommandCenterApp(pendingServerId: MutableState<Long?>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var language by rememberSaveable { mutableStateOf(Prefs.getLanguage(context)) }
    var themeMode by rememberSaveable { mutableStateOf(Prefs.getThemeMode(context)) }
    var routeKey by rememberSaveable { mutableStateOf(CommandRoute.OVERVIEW.key) }
    var selectedServerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var mobileNavigationOpen by rememberSaveable { mutableStateOf(false) }

    // Deliberately plain `remember`: the exit hint is transient, so it should
    // not survive a configuration change (a stale "press again" pill after a
    // rotation would be a lie about the guard's state).
    var exitHintVisible by remember { mutableStateOf(false) }
    var lastBackPressAt by remember { mutableStateOf(0L) }

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
        val canvasColor = CommandColors.canvas
        val darkBars = themeMode == "dark" || (themeMode == "auto" && androidx.compose.foundation.isSystemInDarkTheme())
        if (!view.isInEditMode) {
            SideEffect {
                runCatching {
                    view.context.findActivity()?.window?.let { window ->
                        window.statusBarColor = canvasColor.toArgb()
                        window.navigationBarColor = canvasColor.toArgb()
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkBars
                        controller.isAppearanceLightNavigationBars = !darkBars
                    }
                }
            }
        }

        // Back must always do something. The previous version had two holes:
        // it was disabled at OVERVIEW, so a single accidental tap left the app
        // with no confirmation; and on the five workspace home routes
        // (FLEET, TUNNELS, RADAR, WORKBENCH_HOME, PROTECT_HOME) it navigated
        // to their own workspace default, which is a no-op, so the press was
        // consumed and swallowed — Back appeared broken there.
        BackHandler {
            when (val action = commandBackAction(route, mobileNavigationOpen)) {
                CommandBackAction.CloseNavigation -> {
                    mobileNavigationOpen = false
                    exitHintVisible = false
                }
                is CommandBackAction.Navigate -> {
                    navigate(action.to)
                    exitHintVisible = false
                }
                CommandBackAction.ExitGuard -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressAt < EXIT_GUARD_WINDOW_MS) {
                        context.findActivity()?.finish()
                    } else {
                        lastBackPressAt = now
                        exitHintVisible = true
                    }
                }
            }
        }

        LaunchedEffect(exitHintVisible) {
            if (exitHintVisible) {
                delay(EXIT_GUARD_WINDOW_MS)
                exitHintVisible = false
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CommandColors.infoSurface.copy(alpha = 0.16f), CommandColors.canvas),
                        radius = 1100f
                    )
                )
        ) {
            val availableWidth = maxWidth
            val wide = availableWidth >= 680.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    CommandRail(
                        copy = copy,
                        route = route,
                        expanded = availableWidth >= 920.dp,
                        onNavigate = ::navigate,
                        modifier = Modifier.width(if (availableWidth >= 920.dp) 224.dp else 86.dp)
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

            AnimatedVisibility(
                visible = exitHintVisible,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = CommandSpacing.xl)
            ) {
                CommandSurface(raised = true) {
                    Text(
                        copy.pressAgainToExit,
                        modifier = Modifier.padding(
                            horizontal = CommandSpacing.lg,
                            vertical = CommandSpacing.sm
                        ),
                        color = CommandColors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}


/** How long a first Back press stays armed before it is forgotten again. */
private const val EXIT_GUARD_WINDOW_MS = 2000L

/**
 * What a Back press does at a given route.
 *
 * Kept as a pure function outside the composable so every route can be
 * asserted in a plain JVM test — the previous inline `when` silently
 * swallowed the press on the five workspace home routes, and no test could
 * see it because the logic only existed inside a @Composable.
 *
 * The order matters:
 * 1. an open mobile navigation drawer absorbs the press;
 * 2. the server dossier steps back to the fleet;
 * 3. any other non-default route steps back to its workspace home;
 * 4. a workspace home steps back to the overview;
 * 5. only the overview arms the exit guard.
 */
internal fun commandBackAction(route: CommandRoute, navigationOpen: Boolean): CommandBackAction = when {
    navigationOpen -> CommandBackAction.CloseNavigation
    route == CommandRoute.SERVER_DOSSIER -> CommandBackAction.Navigate(CommandRoute.FLEET)
    route != workspaceDefault(route.workspace) -> CommandBackAction.Navigate(workspaceDefault(route.workspace))
    route != CommandRoute.OVERVIEW -> CommandBackAction.Navigate(CommandRoute.OVERVIEW)
    else -> CommandBackAction.ExitGuard
}

/** Result of [commandBackAction]. */
internal sealed interface CommandBackAction {
    data object CloseNavigation : CommandBackAction
    data class Navigate(val to: CommandRoute) : CommandBackAction
    data object ExitGuard : CommandBackAction
}

internal fun workspaceDefault(workspace: CommandWorkspace): CommandRoute = when (workspace) {
    CommandWorkspace.OBSERVE -> CommandRoute.OVERVIEW
    CommandWorkspace.FLEET -> CommandRoute.FLEET
    CommandWorkspace.OPERATE -> CommandRoute.TUNNELS
    CommandWorkspace.DIAGNOSE -> CommandRoute.RADAR
    CommandWorkspace.WORKBENCH -> CommandRoute.WORKBENCH_HOME
    CommandWorkspace.PROTECT -> CommandRoute.PROTECT_HOME
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
            .background(CommandColors.surface.copy(alpha = 0.92f))
            .border(1.dp, CommandColors.border)
            .padding(horizontal = if (expanded) CommandSpacing.sm else CommandSpacing.xs, vertical = CommandSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CommandColors.accent.copy(alpha = 0.10f))
                    .border(1.dp, CommandColors.accent.copy(alpha = 0.42f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("D", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (expanded) {
                Spacer(Modifier.width(CommandSpacing.sm))
                Column {
                    Text("DIDBAN", color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("SENTINEL CONSOLE", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
                }
            }
        }
        Spacer(Modifier.height(CommandSpacing.md))
        CommandRule()
        Spacer(Modifier.height(CommandSpacing.sm))
        CommandWorkspace.values().forEach { workspace ->
            val active = route.workspace == workspace
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { onNavigate(workspaceDefault(workspace), null) }
                        .background(if (active) CommandColors.infoSurface.copy(alpha = 0.78f) else Color.Transparent)
                        .padding(horizontal = if (expanded) CommandSpacing.sm else CommandSpacing.xs, vertical = CommandSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
                ) {
                    Icon(workspace.icon(), contentDescription = workspace.label(copy), tint = if (active) CommandColors.accent else CommandColors.textSecondary, modifier = Modifier.size(20.dp))
                    if (expanded) {
                        Spacer(Modifier.width(CommandSpacing.sm))
                        Text(workspace.label(copy), color = if (active) CommandColors.textPrimary else CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (active && expanded) {
                    routesFor(workspace).forEach { destination ->
                        val selected = destination == route
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigate(destination, null) }
                                .background(if (selected) CommandColors.accent.copy(alpha = 0.08f) else Color.Transparent)
                                .padding(start = CommandSpacing.lg, end = CommandSpacing.xs, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(if (selected) 5.dp else 4.dp).clip(CircleShape).background(if (selected) CommandColors.accent else CommandColors.textTertiary))
                            Spacer(Modifier.width(CommandSpacing.xs))
                            Text(destination.commandLabel(copy), color = if (selected) CommandColors.textPrimary else CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (expanded) {
            Text("LIVE STATE", color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry))
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
            .background(CommandColors.surface.copy(alpha = 0.96f))
            .border(1.dp, CommandColors.border)
            .padding(horizontal = CommandSpacing.sm, vertical = CommandSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommandIconButton(Icons.Rounded.Menu, if (navigationOpen) copy.close else copy.observe, onToggleNavigation)
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(CommandColors.accent.copy(alpha = 0.10f))
                .border(1.dp, CommandColors.accent.copy(alpha = 0.38f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("D", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(CommandSpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(route.commandLabel(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Icon(destination.icon(), contentDescription = destination.commandLabel(copy), tint = if (route == destination) CommandColors.accent else CommandColors.textSecondary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(CommandSpacing.sm))
                    Text(destination.commandLabel(copy), color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
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
            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(copy.scopeLabel(route).uppercase(), color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry), maxLines = 1)
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { expanded = true }
                        .background(CommandColors.surface.copy(alpha = 0.78f))
                        .border(1.dp, CommandColors.borderStrong, RoundedCornerShape(11.dp))
                        .padding(horizontal = CommandSpacing.sm, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (selectedServer == null) CommandColors.accent else CommandColors.success))
                    Spacer(Modifier.width(CommandSpacing.xs))
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
        CommandTelemetryPill(
            text = "${servers.size} ${copy.servers}",
            tone = if (servers.isEmpty()) CommandHealthTone.UNKNOWN else CommandHealthTone.INFO,
            modifier = Modifier.padding(end = CommandSpacing.xs)
        )
        CommandIconButton(Icons.Rounded.Refresh, copy.refresh, onRefresh)
    }
}

private fun CommandCopy.scopeLabel(route: CommandRoute): String = when (route.workspace) {
    CommandWorkspace.OBSERVE -> observe
    CommandWorkspace.FLEET -> fleet
    CommandWorkspace.OPERATE -> operate
    CommandWorkspace.DIAGNOSE -> diagnose
    CommandWorkspace.WORKBENCH -> workbench
    CommandWorkspace.PROTECT -> protect
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
        CommandRoute.MANAGE_SERVERS -> CommandManageServersScreen(copy, { onNavigate(CommandRoute.SERVER_DOSSIER, it) }) { onNavigate(CommandRoute.FLEET, null) }
        CommandRoute.SERVER_DOSSIER -> CommandServerDossierScreen(copy, selectedServer, selectedServer?.let { states[it.id] }, { onNavigate(CommandRoute.FLEET, null) }, onRefresh, onManageServers, { onNavigate(CommandRoute.PROCESSES, selectedServer) }, { onNavigate(CommandRoute.DOCKER, selectedServer) }, { onNavigate(CommandRoute.TUNNELS, selectedServer) })
        CommandRoute.TUNNELS -> CommandTunnelsScreen(copy, reloadTick, selectedServer, { onNavigate(CommandRoute.TUNNELS_EDITOR, selectedServer) }) { onNavigate(if (selectedServer == null) workspaceDefault(CommandWorkspace.OPERATE) else CommandRoute.SERVER_DOSSIER, selectedServer) }
        CommandRoute.TUNNELS_EDITOR -> CommandTunnelEditorScreen(copy) { onNavigate(CommandRoute.TUNNELS, selectedServer) }
        CommandRoute.DOCKER -> CommandDockerScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }, { onNavigate(if (selectedServer == null) workspaceDefault(CommandWorkspace.OPERATE) else CommandRoute.SERVER_DOSSIER, selectedServer) })
        CommandRoute.PROCESSES -> CommandProcessesScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }, { onNavigate(if (selectedServer == null) workspaceDefault(CommandWorkspace.OPERATE) else CommandRoute.SERVER_DOSSIER, selectedServer) })
        CommandRoute.SERVICES -> CommandServicesScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }, { onNavigate(if (selectedServer == null) workspaceDefault(CommandWorkspace.OPERATE) else CommandRoute.SERVER_DOSSIER, selectedServer) })
        CommandRoute.RADAR -> CommandRadarScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }, { onNavigate(workspaceDefault(CommandWorkspace.DIAGNOSE), null) })
        CommandRoute.BANDWIDTH -> CommandBandwidthScreen(copy, selectedServer, { onNavigate(CommandRoute.MANAGE_SERVERS, null) }) { onNavigate(workspaceDefault(CommandWorkspace.DIAGNOSE), null) }
        CommandRoute.UPTIME -> CommandUptimeScreen(copy) { onNavigate(CommandRoute.UPTIME_EDITOR, null) }
        CommandRoute.UPTIME_EDITOR -> CommandUptimeEditorScreen(copy) { onNavigate(CommandRoute.UPTIME, null) }
        CommandRoute.NETWORK_TOOLS -> CommandNetworkIndexScreen(copy, { onNavigate(CommandRoute.NETWORK_TOOLS_EDITOR, selectedServer) }, { onNavigate(CommandRoute.RADAR, selectedServer) }, { onNavigate(CommandRoute.DNS, null) })
        CommandRoute.NETWORK_TOOLS_EDITOR -> CommandNetworkToolsScreen(copy, selectedServer) { onNavigate(CommandRoute.NETWORK_TOOLS, selectedServer) }
        CommandRoute.DNS -> CommandDnsIndexScreen(copy, { onNavigate(CommandRoute.DNS_EDITOR, null) }, { onNavigate(CommandRoute.NETWORK_TOOLS, selectedServer) })
        CommandRoute.DNS_EDITOR -> CommandDnsManagerScreen(copy) { onNavigate(CommandRoute.DNS, null) }
        CommandRoute.VAULT -> CommandVaultScreen(copy) { onNavigate(CommandRoute.PROTECT_HOME, null) }
        CommandRoute.SECURITY -> CommandSecurityScreen(copy, selectedServer, { onNavigate(CommandRoute.MANAGE_SERVERS, null) }) { onNavigate(CommandRoute.PROTECT_HOME, null) }
        CommandRoute.ALERTS -> CommandAlertsScreen(copy) { onNavigate(CommandRoute.PROTECT_HOME, null) }
        CommandRoute.BACKUP -> CommandBackupScreen(copy) { onNavigate(CommandRoute.PROTECT_HOME, null) }
        CommandRoute.SSH -> CommandSshScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }, { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) })
        CommandRoute.BATCH -> CommandBatchScreen(copy) { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) }
        CommandRoute.SFTP -> CommandSftpScreen(copy, selectedServer, { onNavigate(CommandRoute.FLEET, null) }) { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) }
        CommandRoute.SINGLE_PORT -> CommandSinglePortScreen(copy) { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) }
        CommandRoute.PROXY -> CommandProxyScreen(copy) { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) }
        CommandRoute.DEVELOPER_LAB -> CommandDeveloperLabScreen(copy) { onNavigate(CommandRoute.WORKBENCH_HOME, selectedServer) }
        CommandRoute.WORKBENCH_HOME -> CommandWorkbenchIndexScreen(copy, onNavigate)
        CommandRoute.PROTECT_HOME -> CommandProtectIndexScreen(copy, onNavigate)
        CommandRoute.SETTINGS -> CommandSettingsScreen(copy, themeMode, language, onThemeChange, onLanguageChange)
    }
}
