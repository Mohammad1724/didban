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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
// APP SHELL — Obsidian Zenith Chrome: Deep Canvas, Floating Cyber Capsule Dock
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLanguage(ctx)) }
    var themeMode by remember { mutableStateOf(Prefs.getThemeMode(ctx)) }
    var openServer by remember { mutableStateOf<ServerConfig?>(null) }
    var currentNav by remember { mutableStateOf(0) } // 0: Fleet, 1: Tunnels, 2: Uptime, 3: Network & Cloud, 4: Vault & Tools

    val t = if (lang == "fa") Locales.fa else Locales.en
    val isDarkMode = themeMode == "dark"

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
                                3 -> NetworkCloudScreen(t = t)
                                4 -> VaultToolsScreen(t = t)
                            }
                        }

                        // ── Floating Cyber-Glass Capsule Dock Navigation ──
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 2.dp)
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                color = Ds.surfaceElevated.copy(alpha = 0.94f),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            Ds.hairline,
                                            Ds.accent.copy(alpha = 0.35f),
                                            Ds.hairline
                                        )
                                    )
                                ),
                                shape = RoundedCornerShape(32.dp),
                                shadowElevation = 16.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CyberDockItem(
                                        icon = Icons.Rounded.Dns,
                                        label = t.navServers,
                                        selected = currentNav == 0
                                    ) { currentNav = 0 }

                                    CyberDockItem(
                                        icon = Icons.Rounded.SwapHoriz,
                                        label = t.navTunnels,
                                        selected = currentNav == 1
                                    ) { currentNav = 1 }

                                    CyberDockItem(
                                        icon = Icons.Rounded.Timer,
                                        label = t.navUptime,
                                        selected = currentNav == 2
                                    ) { currentNav = 2 }

                                    CyberDockItem(
                                        icon = Icons.Rounded.Public,
                                        label = t.navNetwork,
                                        selected = currentNav == 3
                                    ) { currentNav = 3 }

                                    CyberDockItem(
                                        icon = Icons.Rounded.Security,
                                        label = t.navVault,
                                        selected = currentNav == 4
                                    ) { currentNav = 4 }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CyberDockItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dockScale"
    )
    val pillBg by animateColorAsState(
        targetValue = if (selected) Ds.accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(220),
        label = "pillBg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) Ds.accent.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = tween(220),
        label = "borderCol"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) Ds.accent else Ds.textTertiary,
        animationSpec = tween(220),
        label = "iconColor"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(pillBg)
            .border(BorderStroke(1.dp, borderCol), RoundedCornerShape(22.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = if (selected) 12.dp else 10.dp, vertical = 8.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(19.dp)
            )

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(180)) + expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(140)) + shrinkHorizontally(spring(stiffness = Spring.StiffnessMediumLow))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ds.accent,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(Ds.accent, CircleShape)
                    )
                }
            }
        }
    }
}
