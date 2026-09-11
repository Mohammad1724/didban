@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

class MainActivity : ComponentActivity() {

    private val pendingServerId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleServerIntent(intent)

        // Notification permission for background monitoring alerts (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } catch (_: Exception) {}
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
// APP SHELL — Obsidian Zenith Chrome: VisionOS Liquid Spotlight Navigation
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

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        DidbanTheme(dark = isDarkMode) {
            // System bars synchronization with active theme palette (100% safe against ClassCastException)
            val view = LocalView.current
            val canvasColor = Ds.canvas
            val lightBars = !isDarkMode
            if (!view.isInEditMode) {
                SideEffect {
                    try {
                        val activity = view.context.findActivity() ?: ctx.findActivity()
                        activity?.window?.let { window ->
                            window.statusBarColor = canvasColor.toArgb()
                            window.navigationBarColor = canvasColor.toArgb()
                            val controller = WindowCompat.getInsetsController(window, view)
                            controller.isAppearanceLightStatusBars = lightBars
                            controller.isAppearanceLightNavigationBars = lightBars
                        }
                    } catch (_: Exception) {}
                }
            }

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

                        // ── VisionOS Liquid Morphing Spotlight Navigation Dock ──
                        LiquidSpotlightDock(
                            currentNav = currentNav,
                            onNavSelect = { currentNav = it },
                            items = listOf(
                                DockItemSpec(Icons.Rounded.Dns, t.navServers),
                                DockItemSpec(Icons.Rounded.SwapHoriz, t.navTunnels),
                                DockItemSpec(Icons.Rounded.Timer, t.navUptime),
                                DockItemSpec(Icons.Rounded.Public, t.navNetwork),
                                DockItemSpec(Icons.Rounded.Security, t.navVault)
                            )
                        )
                    }
                }
            }
        }
    }
}

data class DockItemSpec(
    val icon: ImageVector,
    val label: String
)

@Composable
fun LiquidSpotlightDock(
    currentNav: Int,
    onNavSelect: (Int) -> Unit,
    items: List<DockItemSpec>,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 2.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Ds.surfaceElevated.copy(alpha = 0.90f),
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
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 5.dp)
            ) {
                val totalWidth = maxWidth
                val totalItems = items.size.coerceAtLeast(1)
                val itemWidth = totalWidth / totalItems

                // Sliding Liquid Spotlight Pill Indicator
                val targetIndicatorX = itemWidth * currentNav

                val indicatorOffset by animateDpAsState(
                    targetValue = targetIndicatorX,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "liquidSpotlightOffset"
                )

                // The glowing fluid spotlight background
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Ds.accent.copy(alpha = 0.22f),
                                    Ds.accent.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(1.dp, Ds.accent.copy(alpha = 0.40f)),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    // Top micro-neon laser line
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 2.dp)
                            .size(width = 16.dp, height = 2.dp)
                            .clip(CircleShape)
                            .background(Ds.accent)
                    )
                }

                // Interactive Navigation Item Glyphs
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    items.forEachIndexed { index, spec ->
                        val isSelected = index == currentNav

                        val liftOffset by animateDpAsState(
                            targetValue = if (isSelected) (-2).dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = 0.58f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "glyphLift"
                        )

                        val glyphScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = 0.6f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "glyphScale"
                        )

                        val glyphColor by animateColorAsState(
                            targetValue = if (isSelected) Ds.accent else Ds.textTertiary,
                            animationSpec = tween(200),
                            label = "glyphColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } catch (_: Exception) {}
                                    onNavSelect(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(y = liftOffset),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = spec.label,
                                    tint = glyphColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .scale(glyphScale)
                                )

                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(tween(160)) + expandVertically(spring(stiffness = Spring.StiffnessMedium)),
                                    exit = fadeOut(tween(120)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = spec.label,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Ds.accent,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
