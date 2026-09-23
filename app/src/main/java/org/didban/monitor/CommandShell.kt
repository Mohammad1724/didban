package org.didban.monitor

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
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Cast
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
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
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
    CHECK_HOST("check-host", CommandWorkspace.DIAGNOSE),
    BANDWIDTH("bandwidth", CommandWorkspace.DIAGNOSE),
    CF_SCANNER("cf-scanner", CommandWorkspace.DIAGNOSE),
    REALITY_SNI("reality-sni", CommandWorkspace.DIAGNOSE),
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
    SHARE("share", CommandWorkspace.WORKBENCH),
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
    CommandWorkspace.FLEET -> copy.servers
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
    CommandRoute.CHECK_HOST -> copy.netQReachable
    CommandRoute.BANDWIDTH -> copy.bandwidth
    CommandRoute.CF_SCANNER -> copy.cfScanner
    CommandRoute.REALITY_SNI -> copy.realitySni
    CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR -> copy.uptime
    CommandRoute.NETWORK_TOOLS, CommandRoute.NETWORK_TOOLS_EDITOR -> copy.networkTools
    CommandRoute.DNS, CommandRoute.DNS_EDITOR -> copy.dns
    CommandRoute.WORKBENCH_HOME -> copy.workbench
    CommandRoute.SSH -> copy.ssh
    CommandRoute.BATCH -> copy.batch
    CommandRoute.SFTP -> copy.sftp
    CommandRoute.SINGLE_PORT -> copy.singlePort
    CommandRoute.PROXY -> copy.proxy
    CommandRoute.SHARE -> copy.shareTitle
    CommandRoute.DEVELOPER_LAB -> copy.developerLab
    CommandRoute.PROTECT_HOME -> copy.protect
    CommandRoute.VAULT -> copy.vault
    CommandRoute.SECURITY -> copy.security
    CommandRoute.ALERTS -> copy.alerts
    CommandRoute.BACKUP -> copy.backup
    CommandRoute.SETTINGS -> copy.settings
}

internal fun CommandRoute.navIcon(): ImageVector = when (this) {
    CommandRoute.OVERVIEW -> Icons.Rounded.MonitorHeart
    CommandRoute.INCIDENTS -> Icons.Rounded.Timeline
    CommandRoute.FLEET -> Icons.Rounded.Groups
    CommandRoute.SERVER_DOSSIER -> Icons.Rounded.Storage
    CommandRoute.MANAGE_SERVERS -> Icons.Rounded.Settings
    CommandRoute.TUNNELS, CommandRoute.TUNNELS_EDITOR -> Icons.Rounded.Hub
    CommandRoute.DOCKER -> Icons.Rounded.Widgets
    CommandRoute.PROCESSES -> Icons.AutoMirrored.Rounded.ListAlt
    CommandRoute.SERVICES -> Icons.Rounded.Tune
    CommandRoute.RADAR, CommandRoute.CHECK_HOST -> Icons.Rounded.Public
    CommandRoute.BANDWIDTH -> Icons.Rounded.Speed
    CommandRoute.CF_SCANNER -> Icons.Rounded.TravelExplore
    CommandRoute.REALITY_SNI -> Icons.Rounded.VerifiedUser
    CommandRoute.UPTIME, CommandRoute.UPTIME_EDITOR -> Icons.Rounded.MonitorHeart
    CommandRoute.NETWORK_TOOLS, CommandRoute.NETWORK_TOOLS_EDITOR -> Icons.Rounded.NetworkCheck
    CommandRoute.DNS, CommandRoute.DNS_EDITOR -> Icons.Rounded.Dns
    CommandRoute.WORKBENCH_HOME -> Icons.Rounded.Terminal
    CommandRoute.SSH -> Icons.Rounded.Terminal
    CommandRoute.BATCH -> Icons.Rounded.Groups
    CommandRoute.SFTP -> Icons.Rounded.Folder
    CommandRoute.SINGLE_PORT -> Icons.Rounded.Router
    CommandRoute.PROXY -> Icons.Rounded.Public
    CommandRoute.SHARE -> Icons.Rounded.Cast
    CommandRoute.DEVELOPER_LAB -> Icons.Rounded.Code
    CommandRoute.PROTECT_HOME -> Icons.Rounded.Security
    CommandRoute.VAULT -> Icons.Rounded.Security
    CommandRoute.SECURITY -> Icons.Rounded.Shield
    CommandRoute.ALERTS -> Icons.Rounded.NotificationsNone
    CommandRoute.BACKUP -> Icons.Rounded.Storage
    CommandRoute.SETTINGS -> Icons.Rounded.Settings
}

private fun routesFor(workspace: CommandWorkspace): List<CommandRoute> = commandMenuRoutes(workspace)

@Composable
fun CommandCenterApp(
    pendingServerId: MutableState<Long?>,
    loadServers: (android.content.Context) -> Prefs.ServerLoadResult = Prefs::loadServersResult
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var language by rememberSaveable { mutableStateOf(Prefs.getLanguage(context)) }
    var themeMode by rememberSaveable { mutableStateOf(Prefs.getThemeMode(context)) }
    var navigation by rememberSaveable(stateSaver = listSaver(
        save = { it: CommandNavigation -> it.save() },
        restore = { CommandNavigation.restore(it) }
    )) { mutableStateOf(CommandNavigation.root()) }
    val contentStateHolder = rememberSaveableStateHolder()
    var reloadTick by remember { mutableIntStateOf(0) }
    var mobileNavigationOpen by rememberSaveable { mutableStateOf(false) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }

    // Deliberately plain `remember`: the exit hint is transient, so it should
    // not survive a configuration change (a stale "press again" pill after a
    // rotation would be a lie about the guard's state).
    var exitHintVisible by remember { mutableStateOf(false) }
    var lastBackPressAt by remember { mutableStateOf(0L) }

    val copy = remember(language) { CommandCopy.forLanguage(language) }
    val serverLoad = remember(reloadTick, loadServers) { loadServers(context) }
    val servers = serverLoad.servers.toList()
    val route = navigation.current.route
    var serverPickerOpen by rememberSaveable(route.key) { mutableStateOf(false) }
    val selectedServer = navigation.current.serverId?.let { id -> servers.firstOrNull { it.id == id } }
    val states by Repo.states.collectAsState()

    fun navigate(next: CommandRoute, server: ServerConfig? = null) {
        navigation = navigation.navigate(next, if (next == CommandRoute.MANAGE_SERVERS) server?.id else server?.id ?: navigation.current.serverId)
        reloadTick++
        mobileNavigationOpen = false
        exitHintVisible = false
        lastBackPressAt = 0L
    }

    fun selectScope(server: ServerConfig?) {
        navigation = navigation.selectScope(server?.id)
        serverPickerOpen = false
        exitHintVisible = false
        lastBackPressAt = 0L
    }

    LaunchedEffect(route, servers.map { it.id }, serverLoad.error != null) {
        navigation = navigation.resolveRadarScope(servers.map { it.id }, serverLoad.error != null)
    }

    fun goBack() {
        when (navigation.backAction(mobileNavigationOpen, helpVisible)) {
            CommandBackAction.CLOSE_HELP -> helpVisible = false
            CommandBackAction.CLOSE_NAVIGATION -> mobileNavigationOpen = false
            CommandBackAction.CLOSE_PANE -> {
                navigation = navigation.closePane()
                reloadTick++
            }
            CommandBackAction.POP -> {
                navigation = navigation.back()
                reloadTick++
            }
            CommandBackAction.EXIT -> {
                val now = SystemClock.elapsedRealtime()
                if (exitHintVisible && now - lastBackPressAt < EXIT_GUARD_WINDOW_MS) {
                    context.findActivity()?.finish()
                } else {
                    lastBackPressAt = now
                    exitHintVisible = true
                }
                return
            }
        }
        exitHintVisible = false
        lastBackPressAt = 0L
    }

    val backLabel = navigation.previous?.let { destination ->
        val serverName = destination.serverId?.let { id -> servers.firstOrNull { it.id == id }?.name }
        val destinationLabel = if (destination.route == CommandRoute.FLEET && destination.serverPane == ServerPane.DETAILS) copy.fleetDetails else destination.route.commandLabel(copy)
        "${copy.backTo} $destinationLabel" + (serverName?.let { " · $it" } ?: "")
    }

    LaunchedEffect(pendingServerId.value, servers, navigation.current.serverPane) {
        // Don't throw away an in-memory credential draft for a notification.
        if (navigation.current.serverPane.editing) return@LaunchedEffect
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

        // Hardware/gesture Back and every in-page Back use the same visit history.
        BackHandler(onBack = ::goBack)

        LaunchedEffect(exitHintVisible) {
            if (exitHintVisible) {
                delay(EXIT_GUARD_WINDOW_MS)
                exitHintVisible = false
                lastBackPressAt = 0L
            }
        }

        if (serverPickerOpen) CommandServerPicker(
            copy, servers, serverLoad.error != null,
            onSelect = ::selectScope,
            onDismiss = { serverPickerOpen = false },
            onRetry = { reloadTick++ },
            onAdd = { serverPickerOpen = false; navigation = navigation.editServer(null) }
        )

        BoxWithConstraints(Modifier.fillMaxSize().commandAtmosphere()) {
            val wide = maxWidth >= 840.dp
            @Composable fun page() {
                Column(Modifier.fillMaxSize()) {
                    if (route != CommandRoute.FLEET) {
                        CommandPageChrome(route.commandLabel(copy), copy, language,
                            showBack = route !in CommandPrimary.values().map { it.root },
                            onBack = ::goBack,
                            onHelp = { helpVisible = true; exitHintVisible = false; lastBackPressAt = 0L })
                        if (route == CommandRoute.RADAR && selectedServer != null) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.md)) {
                                CommandSecondaryButton(selectedServer.name, { serverPickerOpen = true })
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth().then(
                        if (route == CommandRoute.FLEET || route == CommandRoute.CF_SCANNER || route == CommandRoute.REALITY_SNI)
                            Modifier else Modifier.padding(horizontal = CommandSpacing.md)
                    )) {
                        CompositionLocalProvider(LocalCommandHeader provides if (route == CommandRoute.FLEET) null else route.commandLabel(copy)) {
        CommandRouteContent(
            copy = copy,
            route = route,
            destination = navigation.current,
            contentStateHolder = contentStateHolder,
            servers = servers,
            loadFailed = serverLoad.error != null,
            onReload = { reloadTick++; exitHintVisible = false; lastBackPressAt = 0L },
            onSelectServer = { reloadTick++; serverPickerOpen = true },
            onHubHelp = { helpVisible = true; exitHintVisible = false; lastBackPressAt = 0L },
            onEdit = { id -> navigation = navigation.editServer(id); exitHintVisible = false; lastBackPressAt = 0L },
            onSaved = { server ->
                reloadTick++
                navigation = navigation.finishEditing(server.id)
                android.widget.Toast.makeText(context, copy.srvSavedPolled, android.widget.Toast.LENGTH_SHORT).show()
            },
            onDeleted = { id ->
                reloadTick++
                navigation = navigation.removeServer(id)
                android.widget.Toast.makeText(context, copy.srvLocalDeleted, android.widget.Toast.LENGTH_SHORT).show()
            },
            selectedServer = selectedServer,
            states = states,
            onBack = ::goBack,
            reloadTick = reloadTick,
            themeMode = themeMode,
            language = language,
            onNavigate = ::navigate,
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
            Row(Modifier.fillMaxSize()) {
                if (wide && !navigation.current.serverPane.editing) CommandPrimaryNavigation(copy, route, true) { navigate(it) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f)) { page() }
                    if (!wide && !navigation.current.serverPane.editing) CommandPrimaryNavigation(copy, route, false) { navigate(it) }
                }
            }

            if (helpVisible) {
                CommandHelpDialog(route = navigation.current.helpRoute(), language = language, copy = copy, onDismiss = { helpVisible = false })
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

internal fun workspaceDefault(workspace: CommandWorkspace): CommandRoute = when (workspace) {
    CommandWorkspace.OBSERVE -> CommandRoute.FLEET
    CommandWorkspace.FLEET -> CommandRoute.FLEET
    CommandWorkspace.OPERATE -> CommandRoute.TUNNELS
    CommandWorkspace.DIAGNOSE -> CommandRoute.RADAR
    CommandWorkspace.WORKBENCH -> CommandRoute.WORKBENCH_HOME
    CommandWorkspace.PROTECT -> CommandRoute.PROTECT_HOME
}

@Composable
private fun CommandRouteContent(
    copy: CommandCopy,
    route: CommandRoute,
    destination: CommandDestination,
    contentStateHolder: SaveableStateHolder,
    servers: List<ServerConfig>,
    loadFailed: Boolean,
    onReload: () -> Unit,
    onSelectServer: () -> Unit,
    onHubHelp: () -> Unit,
    onEdit: (Long?) -> Unit,
    onSaved: (ServerConfig) -> Unit,
    onDeleted: (Long) -> Unit,
    selectedServer: ServerConfig?,
    states: Map<Long, Repo.State>,
    onBack: () -> Unit,
    reloadTick: Int,
    themeMode: String,
    language: String,
    onNavigate: (CommandRoute, ServerConfig?) -> Unit,
    onThemeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    contentStateHolder.SaveableStateProvider(route.key) {
        when (route) {
            CommandRoute.OVERVIEW, CommandRoute.INCIDENTS, CommandRoute.FLEET,
            CommandRoute.MANAGE_SERVERS, CommandRoute.SERVER_DOSSIER -> CommandServerHub(
                copy, servers, loadFailed, states, destination, onReload, onHubHelp,
                { onNavigate(CommandRoute.SERVER_DOSSIER, it) }, onEdit, onBack, onSaved, onDeleted, onNavigate
            )
            CommandRoute.TUNNELS -> key(selectedServer) { CommandTunnelsScreen(copy, reloadTick, selectedServer, { onNavigate(CommandRoute.TUNNELS_EDITOR, selectedServer) }) { onBack() } }
            CommandRoute.TUNNELS_EDITOR -> CommandTunnelEditorScreen(copy) { onBack() }
            CommandRoute.DOCKER -> key(selectedServer) { CommandDockerScreen(copy, selectedServer, onSelectServer, { onBack() }) }
            CommandRoute.PROCESSES -> key(selectedServer) { CommandProcessesScreen(copy, selectedServer, onSelectServer, { onBack() }) }
            CommandRoute.SERVICES -> key(selectedServer) { CommandServicesScreen(copy, selectedServer, onSelectServer, { onBack() }) }
            CommandRoute.RADAR -> CommandRadarScreen(copy, selectedServer, onSelectServer, { onBack() })
            CommandRoute.CHECK_HOST -> CommandCheckHostScreen(copy, onBack = onBack)
            CommandRoute.BANDWIDTH -> key(selectedServer) { CommandBandwidthScreen(copy, selectedServer, onSelectServer, onBack = onBack) }
            CommandRoute.CF_SCANNER -> CommandCfScannerScreen(copy, onBack = onBack)
            CommandRoute.REALITY_SNI -> CommandRealitySniScreen(copy, onBack = onBack)
            CommandRoute.UPTIME -> CommandUptimeScreen(copy, onOpenEditor = { onNavigate(CommandRoute.UPTIME_EDITOR, null) }, onOpenRadar = { onNavigate(CommandRoute.RADAR, selectedServer) })
            CommandRoute.UPTIME_EDITOR -> CommandUptimeEditorScreen(copy) { onBack() }
            CommandRoute.NETWORK_TOOLS -> CommandNetworkIndexScreen(copy, { onNavigate(CommandRoute.NETWORK_TOOLS_EDITOR, selectedServer) }, { onNavigate(CommandRoute.CHECK_HOST, null) }, { onNavigate(CommandRoute.DNS, null) }, { onNavigate(CommandRoute.CF_SCANNER, null) }, { onNavigate(CommandRoute.REALITY_SNI, null) })
            CommandRoute.NETWORK_TOOLS_EDITOR -> key(selectedServer) { CommandNetworkToolsScreen(copy, selectedServer) { onBack() } }
            CommandRoute.DNS -> CommandDnsIndexScreen(copy, { onNavigate(CommandRoute.DNS_EDITOR, null) }, { onNavigate(CommandRoute.NETWORK_TOOLS, selectedServer) })
            CommandRoute.DNS_EDITOR -> CommandDnsManagerScreen(copy) { onBack() }
            CommandRoute.VAULT -> CommandVaultScreen(copy) { onBack() }
            CommandRoute.SECURITY -> key(selectedServer) { CommandSecurityScreen(copy, selectedServer, onSelectServer) { onBack() } }
            CommandRoute.ALERTS -> CommandAlertsScreen(copy) { onBack() }
            CommandRoute.BACKUP -> CommandBackupScreen(copy) { onBack() }
            CommandRoute.SSH -> key(selectedServer) { CommandSshScreen(copy, selectedServer, onSelectServer, { onBack() }) }
            CommandRoute.BATCH -> CommandBatchScreen(copy) { onBack() }
            CommandRoute.SFTP -> key(selectedServer) { CommandSftpScreen(copy, selectedServer, onSelectServer) { onBack() } }
            CommandRoute.SINGLE_PORT -> CommandSinglePortScreen(copy) { onBack() }
            CommandRoute.PROXY -> CommandProxyScreen(copy) { onBack() }
            CommandRoute.SHARE -> CommandShareScreen(copy, onBack) { onNavigate(CommandRoute.SHARE, null) }
            CommandRoute.DEVELOPER_LAB -> CommandDeveloperLabScreen(copy) { onBack() }
            CommandRoute.WORKBENCH_HOME -> CommandWorkbenchIndexScreen(copy, onNavigate)
            CommandRoute.PROTECT_HOME -> CommandProtectIndexScreen(copy, onNavigate)
            CommandRoute.SETTINGS -> CommandSettingsScreen(copy, themeMode, language, onThemeChange, onLanguageChange, onNavigate)
        }
    }
}
