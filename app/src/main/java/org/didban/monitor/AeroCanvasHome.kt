package org.didban.monitor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Moon
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sunny
import androidx.compose.material.icons.rounded.Tersearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlinx.coroutines.launch

/**
 * Phase 3 · item 3-B — the aerospatial shell home.
 *
 * Full-bleed spatial canvas (map) + floating HUD + horizontal deck.
 * Replaces the legacy 5-tab dock navigation. Existing screens are embedded
 * as deck pages (zero feature loss); the legacy ServersScreen becomes a
 * drill-in overlay reachable from the HUD (its add/edit/delete wizards stay
 * intact — the map deliberately has no half-implemented server forms).
 *
 * UI-only file: verified by code review (no Android/Compose compile in this
 * environment). The data layer (AeroMapModel.kt) is JVM-tested.
 */

const val DECK_PAGES = 5 // 0 map · 1 tunnels · 2 uptime · 3 cloud&alerts · 4 vault&tools

@Composable
fun AeroDeckHome(
    t: Str,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onLanguage: (String) -> Unit,
    onOpenServer: (ServerConfig) -> Unit,
    onManageServers: () -> Unit,
    reloadTick: Int,
    deckBackEnabled: Boolean = true
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = useReduceMotion()
    val api = remember { ApiClient() }

    // Server/tunnel lists reload whenever the manage overlay closes (a server
    // may have been added/edited/deleted inside it). `tunnels` is also
    // writable: palette/radial quick actions mutate it in place so the map,
    // KPIs and the fleet page all stay live without a full shell tick.
    val servers = remember(reloadTick) { Prefs.loadServers(ctx) }
    var tunnels by remember(reloadTick) { mutableStateOf(Prefs.loadTunnels(ctx)) }
    val states by Repo.states.collectAsState()

    // v3 layer 4: command palette
    var showPalette by remember { mutableStateOf(false) }

    val graph = remember(servers, tunnels, states) {
        buildMapGraph(servers, states, tunnels)
    }

    // ── Palette / radial actions on real state ──
    fun testTunnelNow(tun: TunnelConfig) {
        scope.launch {
            try {
                val res = TunnelEngine.testTunnel(tun)
                val updated = tunnels.map {
                    if (it.id == tun.id) it.copy(
                        lastStatus = if (res.first) 1 else 0,
                        lastLatencyMs = res.second,
                        lastChecked = System.currentTimeMillis()
                    ) else it
                }
                Prefs.saveTunnels(ctx, updated)
                tunnels = updated
            } catch (_: Exception) {}
        }
    }

    fun deployTunnelNow(tun: TunnelConfig) {
        if (!tun.autoSync) return
        scope.launch {
            TunnelEngine.autoDeployTunnel(ctx, tun)
            tunnels = Prefs.loadTunnels(ctx)
        }
    }

    fun toggleTunnelNow(tun: TunnelConfig) {
        val updated = tunnels.map {
            if (it.id == tun.id) it.copy(isEnabled = !it.isEnabled) else it
        }
        Prefs.saveTunnels(ctx, updated)
        tunnels = updated
        scope.launch {
            TunnelEngine.controlRemoteTunnel(ctx, tun, if (tun.isEnabled) "stop" else "start")
        }
    }

    fun testServerAlert(server: ServerConfig) {
        scope.launch {
            try {
                api.testTelegram(server)
                Toast.makeText(ctx, t.telegramSent, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "${t.telegramFailed}: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshServerNow(server: ServerConfig) {
        PollingCoordinator.requestNow(server.id)
    }

    val paletteCommands = remember(servers, tunnels, t) {
        val list = mutableListOf<PaletteCommand>()
        servers.forEach { s ->
            list += PaletteCommand(
                id = "cockpit-${s.id}",
                label = t.radialCockpit,
                hint = "${s.name.ifEmpty { s.host }} · ${s.host}:${s.port}",
                icon = Icons.Rounded.Dashboard,
                action = { onOpenServer(s) }
            )
        }
        tunnels.forEach { tun ->
            list += PaletteCommand(
                id = "tun-test-${tun.id}",
                label = t.radialTest,
                hint = tun.name,
                icon = Icons.Rounded.Speed,
                action = { testTunnelNow(tun) }
            )
            if (tun.autoSync) {
                list += PaletteCommand(
                    id = "tun-deploy-${tun.id}",
                    label = "Auto-Deploy",
                    hint = tun.name,
                    icon = Icons.Rounded.CloudSync,
                    action = { deployTunnelNow(tun) }
                )
            }
            list += PaletteCommand(
                id = "tun-toggle-${tun.id}",
                label = if (tun.isEnabled) t.stopShort else t.startShort,
                hint = tun.name,
                icon = if (tun.isEnabled) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                action = { toggleTunnelNow(tun) }
            )
        }
        list += PaletteCommand(
            id = "manage",
            label = t.manageServers,
            icon = Icons.Rounded.Dns,
            action = { onManageServers() }
        )
        list += PaletteCommand(
            id = "theme",
            label = if (isDarkMode) "Light" else "Dark",
            icon = if (isDarkMode) Icons.Rounded.Sunny else Icons.Rounded.Moon,
            action = { onToggleTheme() }
        )
        list += PaletteCommand(
            id = "lang",
            label = t.langButton,
            icon = Icons.Rounded.Public,
            action = { onLanguage(if (t.langButton == "EN") "en" else "fa") }
        )
        list += PaletteCommand(
            id = "refresh-all",
            label = t.refreshNow,
            hint = "${servers.size}",
            icon = Icons.Rounded.Refresh,
            action = { servers.forEach { PollingCoordinator.requestNow(it.id) } }
        )
        // Deck navigation — the four context pages (tool wiring).
        list += PaletteCommand(
            id = "nav-tunnels",
            label = t.navTunnels,
            icon = Icons.Rounded.SwapHoriz,
            action = { scope.launch { pagerState.animateScrollToPage(1) } }
        )
        list += PaletteCommand(
            id = "nav-uptime",
            label = t.navUptime,
            icon = Icons.Rounded.Timer,
            action = { scope.launch { pagerState.animateScrollToPage(2) } }
        )
        list += PaletteCommand(
            id = "nav-network",
            label = t.navNetwork,
            icon = Icons.Rounded.Public,
            action = { scope.launch { pagerState.animateScrollToPage(3) } }
        )
        list += PaletteCommand(
            id = "nav-vault",
            label = t.navVault,
            icon = Icons.Rounded.Security,
            action = { scope.launch { pagerState.animateScrollToPage(4) } }
        )
        list
    }

    // H9: the deck page survives process death via the saveable state and is
    // restored into a fresh PagerState; settled pages persist back.
    var deckPage by rememberSaveable { mutableStateOf(0) }
    val pagerState = remember { PagerState(initialPage = deckPage.coerceIn(0, DECK_PAGES - 1)) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { deckPage = it }
    }

    // Back while off-page-0 returns to the map. This handler is registered
    // AFTER the shell's (it is composed deeper), so it wins while enabled;
    // deckBackEnabled is false under the manage overlay so the overlay's
    // close handler keeps priority there.
    BackHandler(enabled = deckBackEnabled && pagerState.currentPage != 0) {
        scope.launch { pagerState.scrollToPage(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ds.canvas)
    ) {
        HorizontalPager(
            state = pagerState,
            offscreenLimit = 0, // only the current page is composed (parity with the old `when` nav)
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> AeroMapPage(
                    t = t,
                    graph = graph,
                    reduceMotion = reduceMotion,
                    servers = servers,
                    onNodeTap = { node ->
                        servers.firstOrNull { it.id == node.serverId }?.let(onOpenServer)
                    },
                    onNodeTestAlert = { node ->
                        servers.firstOrNull { it.id == node.serverId }?.let { testServerAlert(it) }
                    },
                    onNodeRefresh = { node ->
                        servers.firstOrNull { it.id == node.serverId }?.let { refreshServerNow(it) }
                    },
                    onManageServers = onManageServers
                )
                1 -> AeroTunnelFleetScreen(t = t, seedTunnels = tunnels)
                2 -> UptimeScreen(t = t)
                3 -> NetworkCloudScreen(t = t)
                4 -> VaultToolsScreen(t = t)
            }
        }

        // ── Floating HUD: one thin instrument cluster (no app bar) ──
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            AeroHud(
                t = t,
                isDarkMode = isDarkMode,
                kpis = graph.kpis,
                onManage = onManageServers,
                onToggleTheme = onToggleTheme,
                onLanguage = { onLanguage(if (t.langButton == "EN") "en" else "fa") },
                onOpenPalette = { showPalette = true }
            )
        }

        // ── Deck pagination dots + swipe hint ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = pagerState.currentPage == 0,
                enter = fadeIn(tween(AeroMotion.fadeMs)),
                exit = fadeOut(tween(AeroMotion.fadeMs))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = t.deckHint,
                        fontSize = 9.sp,
                        color = Ds.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AeroRadii.pill))
                            .border(1.dp, Ds.hairline, RoundedCornerShape(AeroRadii.pill))
                            .background(Ds.surface.copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(7.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(DECK_PAGES) { i ->
                    Box(
                        modifier = Modifier
                            .width(if (pagerState.currentPage == i) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (pagerState.currentPage == i) Ds.accent else Ds.hairlineStrong
                            )
                    )
                }
            }
        }

        // ── v3 layer 4: command palette (topmost) ──
        if (showPalette) {
            AeroCommandPalette(
                t = t,
                commands = paletteCommands,
                onDismiss = { showPalette = false }
            )
        }
    }
}

// ── HUD ─────────────────────────────────────────────────────────────────────

@Composable
private fun AeroHud(
    t: Str,
    isDarkMode: Boolean,
    kpis: MapKpis,
    onManage: () -> Unit,
    onToggleTheme: () -> Unit,
    onLanguage: () -> Unit,
    onOpenPalette: () -> Unit
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(horizontal = 14.dp, top = 8.dp)
            .clip(RoundedCornerShape(AeroRadii.hud))
            .background(Ds.surfaceElevated.copy(alpha = 0.92f))
            .border(1.dp, Ds.hairlineStrong, RoundedCornerShape(AeroRadii.hud))
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = t.appName,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Ds.textPrimary,
            modifier = Modifier.padding(start = 14.dp, end = 10.dp)
        )
        Box(Modifier.width(1.dp).height(20.dp).background(Ds.hairline))
        HudKpiCell(
            value = "${kpis.onlineCount}/${kpis.serverCount}",
            label = t.kpiOnline,
            color = if (kpis.serverCount > 0 && kpis.onlineCount == kpis.serverCount) Ds.ok else Ds.textPrimary
        )
        HudKpiCell(value = "${kpis.activeTunnels}", label = t.kpiTunnels, color = Ds.textPrimary)
        HudKpiCell(
            value = "${kpis.alerts}",
            label = t.kpiAlerts,
            color = if (kpis.alerts > 0) Ds.warn else Ds.textPrimary
        )
        Spacer(Modifier.weight(1f))
        HudIcon(Icons.Rounded.Dns, t.manageServers, onManage)
        HudIcon(Icons.Rounded.Tersearch, t.paletteTitle, onOpenPalette)
        HudIcon(
            if (isDarkMode) Icons.Rounded.Sunny else Icons.Rounded.Moon,
            if (isDarkMode) "Light" else "Dark",
            onToggleTheme
        )
        HudIcon(Icons.Rounded.Public, t.langButton, onLanguage)
    }
}

@Composable
private fun HudKpiCell(value: String, label: String, color: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = color, style = TabularNums)
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Ds.textTertiary)
    }
}

@Composable
private fun HudIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Ds.textSecondary, modifier = Modifier.size(16.dp))
    }
}

// ── Map page ────────────────────────────────────────────────────────────────

@Composable
private fun AeroMapPage(
    t: Str,
    graph: MapGraph,
    reduceMotion: Boolean,
    servers: List<ServerConfig>,
    onNodeTap: (MapNode) -> Unit,
    onNodeTestAlert: (MapNode) -> Unit,
    onNodeRefresh: (MapNode) -> Unit,
    onManageServers: () -> Unit
) {
    // v3 layer 3: the radial quick-action menu for a long-pressed node
    var radialNode by remember { mutableStateOf<MapNode?>(null) }
    // Registered deeper than the deck's page handler, so while the radial is
    // open, Back closes the radial first.
    BackHandler(enabled = radialNode != null) { radialNode = null }

    if (graph.nodes.isEmpty()) {
        EmptyState(
            title = t.mapEmptyTitle,
            body = t.mapEmptyBody,
            actionLabel = t.mapAddServer,
            onAction = onManageServers,
            modifier = Modifier.padding(top = 120.dp)
        )
        return
    }
    var mapPx by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp, bottom = 40.dp) // keep the map clear of HUD & dots
            // measured AFTER padding: this is exactly the canvas's size, so
            // node overlays (tap targets, labels) line up with the draw.
            .onSizeChanged { mapPx = it }
    ) {
        AeroMapCanvas(graph = graph, reduceMotion = reduceMotion)
        if (mapPx != IntSize.Zero) {
            val density = LocalDensity.current.density
            val wDp = mapPx.width / density
            val hDp = mapPx.height / density
            graph.nodes.forEachIndexed { index, node ->
                val x = node.fx * wDp
                val y = node.fy * hDp
                // 44dp tap target centered on the node.
                // Tap → cockpit (layer 2). Long-press → radial quick actions
                // (layer 3).
                Box(
                    modifier = Modifier
                        .padding(
                            start = ((x - 22).dp).coerceAtLeast(0.dp),
                            top = ((y - 22).dp).coerceAtLeast(0.dp)
                        )
                        .size(44.dp)
                        .pointerInput(node.serverId) {
                            detectTapGestures(
                                onTap = { onNodeTap(node) },
                                onLongPress = { radialNode = node }
                            )
                        }
                )
                // label + sub-label (clamped so edge nodes never produce
                // negative padding)
                Column(
                    modifier = Modifier
                        .padding(start = ((x - 70).dp).coerceAtLeast(0.dp), top = (y + 18).dp)
                        .width(140.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = node.name,
                        fontSize = if (index == 0) 13.sp else 11.5.sp,
                        fontWeight = if (index == 0) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = Ds.textPrimary,
                        maxLines = 1
                    )
                    MapNodeSubLabel(t = t, node = node)
                }
            }

            // ── v3 layer 3: radial quick actions for the long-pressed node ──
            radialNode?.let { rn ->
                val rx = rn.fx * wDp
                val ry = rn.fy * hDp
                // scrim: any tap outside the radial closes it
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { radialNode = null }
                )
                AeroRadialMenu(
                    t = t,
                    x = rx,
                    y = ry,
                    onCockpit = {
                        radialNode = null
                        onNodeTap(rn)
                    },
                    onTest = {
                        radialNode = null
                        onNodeTestAlert(rn)
                    },
                    onRefresh = {
                        radialNode = null
                        onNodeRefresh(rn)
                    }
                )
            }
        }
    }
}

// ── Radial quick-action menu (v3 layer 3) ───────────────────────────────────

@Composable
private fun AeroRadialMenu(
    t: Str,
    x: Float,
    y: Float,
    onCockpit: () -> Unit,
    onTest: () -> Unit,
    onRefresh: () -> Unit
) {
    // Satellites sit 54dp above the node on either side; the primary
    // (cockpit) action covers the node itself. Same coordinate convention
    // as the node tap targets (clamped so edge nodes stay on screen).
    val satDy = 54f

    // left satellite: test alert
    Column(
        modifier = Modifier
            .padding(
                start = ((x - 54f - 22f).dp).coerceAtLeast(0.dp),
                top = ((y - satDy - 22f).dp).coerceAtLeast(0.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AeroRadialButton(
            icon = Icons.Rounded.Send,
            tone = Ds.accent,
            size = 44.dp,
            onClick = onTest
        )
        Spacer(Modifier.height(3.dp))
        Text(t.radialTest, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Ds.textTertiary, maxLines = 1)
    }

    // right satellite: refresh now
    Column(
        modifier = Modifier
            .padding(
                start = ((x + 54f - 22f).dp).coerceAtLeast(0.dp),
                top = ((y - satDy - 22f).dp).coerceAtLeast(0.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AeroRadialButton(
            icon = Icons.Rounded.Refresh,
            tone = Ds.info,
            size = 44.dp,
            onClick = onRefresh
        )
        Spacer(Modifier.height(3.dp))
        Text(t.radialRefresh, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = Ds.textTertiary, maxLines = 1)
    }

    // center: cockpit (primary destination)
    Column(
        modifier = Modifier
            .padding(
                start = ((x - 28f).dp).coerceAtLeast(0.dp),
                top = ((y - 28f).dp).coerceAtLeast(0.dp)
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Ds.accent)
                .border(2.dp, Ds.accentDeep, CircleShape)
                .clickable(onClick = onCockpit),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Dashboard,
                contentDescription = t.radialCockpit,
                tint = Ds.onAccent,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun AeroRadialButton(
    icon: ImageVector,
    tone: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Ds.surface)
            .border(1.dp, tone.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MapNodeSubLabel(t: Str, node: MapNode) {
    when (node.status) {
        MapNodeStatus.ONLINE -> node.cpuPct?.let {
            Text(
                text = "cpu ${it.toInt()}%",
                fontSize = 9.sp,
                color = Ds.textTertiary,
                fontFamily = Telemetry,
                style = TabularNums
            )
        }
        MapNodeStatus.WARN -> Text(
            text = "▲ ${t.mapSpike}",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Ds.warn
        )
        MapNodeStatus.OFFLINE -> Text(
            text = t.mapOffline,
            fontSize = 9.sp,
            color = Ds.textTertiary
        )
    }
}

// ── Canvas renderer (geometry only; labels/taps are Compose overlays) ───────

@Composable
private fun AeroMapCanvas(graph: MapGraph, reduceMotion: Boolean) {
    val nodeById = remember(graph.nodes) { graph.nodes.associateBy { it.serverId } }

    // Seamless dash-flow for active arcs; frozen under reduce-motion.
    val dash = remember { Animatable(0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        var off = 0f
        while (true) {
            off -= 28f // two dash periods (5+9) per cycle → seamless loop
            dash.animateTo(off, tween(AeroMotion.arcFlowMs, easing = LinearEasing))
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawMapGrid()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val minDim = size.minDimension
        drawOrbitalRing(Offset(cx, cy), minDim * 0.36f)
        drawOrbitalRing(Offset(cx, cy), minDim * 0.56f)

        // arcs
        graph.arcs.forEach { arc ->
            val from = nodeById[arc.fromNodeId] ?: return@forEach
            val to = nodeById[arc.toNodeId] ?: return@forEach
            val f = Offset(from.fx * size.width, from.fy * size.height)
            val p = Offset(to.fx * size.width, to.fy * size.height)
            val mid = Offset((f.x + p.x) / 2f, (f.y + p.y) / 2f)
            val dx = p.x - f.x
            val dy = p.y - f.y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val bow = len * 0.16f
            val ctrl = Offset(mid.x + (-dy / len) * bow, mid.y + (dx / len) * bow)
            val path = Path().apply {
                moveTo(f.x, f.y)
                quadTo(ctrl.x, ctrl.y, p.x, p.y)
            }
            when (arc.state) {
                MapArcState.ACTIVE -> {
                    drawPath(path, Ds.hairline, style = Stroke(width = 2.4.dp.toPx()))
                    drawPath(
                        path,
                        Ds.ok,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 9.dp.toPx()),
                                dash.value
                            )
                        )
                    )
                }
                MapArcState.DOWN -> {
                    drawPath(
                        path,
                        Ds.danger.copy(alpha = 0.55f),
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 6.dp.toPx()), 0f
                            )
                        )
                    )
                }
                MapArcState.DORMANT -> {
                    drawPath(
                        path,
                        Ds.neutral.copy(alpha = 0.3f),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }
        }

        // nodes
        graph.nodes.forEachIndexed { index, node ->
            val c = Offset(node.fx * size.width, node.fy * size.height)
            val primary = index == 0
            val coreColor = when (node.status) {
                MapNodeStatus.ONLINE -> if (primary) Ds.accent else Ds.ok
                MapNodeStatus.WARN -> Ds.warn
                MapNodeStatus.OFFLINE -> Ds.textTertiary
            }
            // radial halo
            val haloR = if (primary) 52.dp.toPx() else 34.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.28f), Color.Transparent),
                    center = c,
                    radius = haloR
                ),
                radius = haloR,
                center = c
            )
            // status ring (dashed when offline)
            val ringR = if (primary) 20.dp.toPx() else 12.dp.toPx()
            drawCircle(
                color = if (node.status == MapNodeStatus.OFFLINE) Ds.textTertiary.copy(alpha = 0.7f) else coreColor,
                radius = ringR,
                center = c,
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = if (node.status == MapNodeStatus.OFFLINE) {
                        PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()), 0f)
                    } else null
                )
            )
            // hollow core: filled disc punched out with canvas color
            val coreR = if (primary) 11.dp.toPx() else 6.5.dp.toPx()
            drawCircle(color = coreColor, radius = coreR, center = c)
            drawCircle(
                color = Ds.canvas,
                radius = (coreR - 2.6.dp.toPx()).coerceAtLeast(0f),
                center = c
            )
            drawCircle(color = coreColor, radius = if (primary) 4.dp.toPx() else 2.4.dp.toPx(), center = c)
        }
    }
}

private fun DrawScope.drawMapGrid() {
    val step = 26.dp.toPx()
    val r = 1.1.dp.toPx()
    var y = step / 2f
    while (y < size.height) {
        var x = step / 2f
        while (x < size.width) {
            drawCircle(color = Ds.hairline.copy(alpha = 0.5f), radius = r, center = Offset(x, y))
            x += step
        }
        y += step
    }
}

private fun DrawScope.drawOrbitalRing(center: Offset, radius: Float) {
    drawCircle(
        color = Ds.hairline.copy(alpha = 0.8f),
        radius = radius,
        center = center,
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)
        )
    )
}
