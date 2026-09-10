@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private val pendingServerId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleServerIntent(intent)

        // Notification permission for background monitoring alerts (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContent {
            DidbanApp(pendingServerId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleServerIntent(intent)
    }

    private fun handleServerIntent(intent: Intent?) {
        val id = intent?.getLongExtra("server_id", -1L) ?: -1L
        pendingServerId.value = if (id > 0) id else null
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// APP SHELL — Obsidian Zenith Chrome: Deep Canvas, Floating Island Navigation
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLanguage(ctx)) }
    var themeMode by remember { mutableStateOf(Prefs.getThemeMode(ctx)) }
    var openServer by remember { mutableStateOf<ServerConfig?>(null) }
    var currentNav by remember { mutableStateOf(0) } // 0: Fleet, 1: Tunnels, 2: Uptime, 3: Network, 4: Cloudflare, 5: Vault, 6: DevLab

    val t = if (lang == "fa") Locales.fa else Locales.en
    val isDarkMode = themeMode == "dark"
    val navScrollState = rememberScrollState()

    // Deep-link routing from push notifications
    LaunchedEffect(pendingServerId.value) {
        val id = pendingServerId.value
        if (id != null) {
            val s = Prefs.loadServers(ctx).firstOrNull { it.id == id }
            if (s != null) openServer = s
            pendingServerId.value = null
        }
    }

    // System bars synchronization with active theme palette
    val view = LocalView.current
    if (!view.isInEditMode) {
        val canvasColor = Ds.canvas
        val lightBars = !isDarkMode
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = canvasColor.toArgb()
            window.navigationBarColor = canvasColor.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightBars
            controller.isAppearanceLightNavigationBars = lightBars
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        DidbanTheme(dark = isDarkMode) {
            DidbanBackground {
                if (openServer != null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        Box(Modifier.weight(1f)) {
                            DashboardScreen(
                                t = t,
                                server = openServer!!,
                                isDarkMode = isDarkMode,
                                onToggleTheme = {
                                    val newMode = if (isDarkMode) "light" else "dark"
                                    themeMode = newMode
                                    Prefs.setThemeMode(ctx, newMode)
                                },
                                onBack = { openServer = null }
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        Box(Modifier.weight(1f)) {
                            when (currentNav) {
                                0 -> ServersScreen(
                                    t = t,
                                    isDarkMode = isDarkMode,
                                    onToggleTheme = {
                                        val newMode = if (isDarkMode) "light" else "dark"
                                        themeMode = newMode
                                        Prefs.setThemeMode(ctx, newMode)
                                    },
                                    onLanguage = { new ->
                                        lang = new
                                        Prefs.setLanguage(ctx, new)
                                    },
                                    onOpen = { openServer = it }
                                )
                                1 -> TunnelScreen(t = t)
                                2 -> UptimeScreen(t = t)
                                3 -> NetworkHubScreen(t = t)
                                4 -> CloudflareScreen(t = t)
                                5 -> VaultScreen(t = t)
                                6 -> DevLabScreen(t = t)
                            }
                        }

                        // ── Floating Island Bottom Dock Navigation ──
                        Surface(
                            color = Ds.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Ds.hairline),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(navScrollState)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                DockItem(Icons.Rounded.Dns, t.navServers, currentNav == 0) { currentNav = 0 }
                                DockItem(Icons.Rounded.SwapHoriz, t.navTunnels, currentNav == 1) { currentNav = 1 }
                                DockItem(Icons.Rounded.Timer, t.navUptime, currentNav == 2) { currentNav = 2 }
                                DockItem(Icons.Rounded.Public, t.navNetwork, currentNav == 3) { currentNav = 3 }
                                DockItem(Icons.Rounded.Cloud, t.navCloudflare, currentNav == 4) { currentNav = 4 }
                                DockItem(Icons.Rounded.Security, t.navVault, currentNav == 5) { currentNav = 5 }
                                DockItem(Icons.Rounded.Terminal, t.navTools, currentNav == 6) { currentNav = 6 }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val pillBg by animateColorAsState(
        targetValue = if (selected) Ds.accentDim else Color.Transparent,
        animationSpec = tween(220), label = "dockPill"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) Ds.accent else Ds.textTertiary,
        animationSpec = tween(220), label = "dockIcon"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 28.dp)
                .background(pillBg, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = iconColor,
            maxLines = 1
        )
    }
}
