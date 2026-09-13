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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.runtime.rememberSaveable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        try {
            handleServerIntent(intent)
        } catch (_: Throwable) {}

        // Notification permission for background monitoring alerts (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            } catch (_: Throwable) {}
        }

        setContent {
            // H8: the trace is decrypted here (stored encrypted at rest).
            var crashTrace by remember { mutableStateOf(CrashLog.loadTrace(this)) }
            val consecutiveCrashes = remember { CrashLog.readRecord(this)?.consecutiveCount ?: 0 }

            if (crashTrace != null) {
                CrashRecoveryScreen(
                    trace = crashTrace!!,
                    consecutiveCount = consecutiveCrashes,
                    onReset = {
                        CrashLog.clear(this)
                        crashTrace = null
                    }
                )
            } else {
                DidbanApp(pendingServerId)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ProcessState.activityStarted()
        // H8: the startup survived — clear the consecutive-crash counter so
        // a later (non-startup) crash is not mistaken for a cannot-start loop.
        CrashLog.markStartupOk(this)
    }

    override fun onStop() {
        super.onStop()
        ProcessState.activityStopped()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        try {
            handleServerIntent(intent)
        } catch (_: Throwable) {}
    }

    private fun handleServerIntent(intent: Intent?) {
        val id = intent?.getLongExtra("server_id", -1L) ?: -1L
        pendingServerId.value = if (id > 0) id else null
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// CRASH RECOVERY SCREEN (Zero-Panic Failure Guard)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CrashRecoveryScreen(
    trace: String,
    onReset: () -> Unit,
    consecutiveCount: Int = 0
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    DidbanTheme(dark = true) {
        DidbanBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconBadge(
                    icon = Icons.Rounded.WarningAmber,
                    tint = Ds.warn,
                    background = Ds.warnDim,
                    size = 64.dp,
                    iconSize = 32.dp
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    "Didban Crash Diagnostic",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.textPrimary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "The previous session encountered an unhandled exception. Details are captured below:",
                    fontSize = 12.sp,
                    color = Ds.textSecondary,
                    lineHeight = 17.sp
                )

                // H8: make a detected cannot-start loop explicit to the user.
                if (consecutiveCount >= CrashPolicy.CRASH_LOOP_THRESHOLD) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "$consecutiveCount consecutive crashes at startup were detected — " +
                                "auto-restart is disabled. Use Reset & Launch to try again.",
                        fontSize = 12.sp,
                        color = Ds.warn,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Ds.surfaceLow,
                    border = BorderStroke(1.dp, Ds.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            trace,
                            fontSize = 10.sp,
                            fontFamily = Telemetry,
                            color = Ds.textTertiary,
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SoftButton(
                        text = if (copied) "Copied!" else "Copy Log",
                        icon = Icons.Rounded.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(trace))
                            copied = true
                        },
                        modifier = Modifier.weight(1f).height(44.dp)
                    )

                    PrimaryButton(
                        text = "Reset & Launch",
                        icon = Icons.Rounded.Refresh,
                        onClick = onReset,
                        modifier = Modifier.weight(1.3f).height(44.dp)
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// APP SHELL — Obsidian Zenith Chrome: VisionOS Liquid Spotlight Navigation
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = LocalContext.current
    // H9: navigation state must survive rotation AND process death.
    // rememberSaveable persists into the Activity's saved-instance-state
    // bundle — no ViewModel/lifecycle dependency needed. (lang/themeMode
    // are also persisted to Prefs on change; saving them here too keeps the
    // restoration path uniform.)
    var lang by rememberSaveable { mutableStateOf(Prefs.getLanguage(ctx)) }
    var themeMode by rememberSaveable { mutableStateOf(Prefs.getThemeMode(ctx)) }
    var openServerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var currentNav by rememberSaveable { mutableStateOf(0) } // 0: Fleet, 1: Tunnels, 2: Uptime, 3: Network & Cloud, 4: Vault & Tools

    // H9: the open server is addressed by id (a saveable primitive) and
    // resolved against the persisted server list — so the dashboard comes
    // back after rotation/process death, always shows the current (possibly
    // edited) server data, and a deleted server degrades gracefully to the
    // fleet view instead of a dangling object.
    val openServer: ServerConfig? = openServerId?.let { id ->
        Prefs.loadServers(ctx).firstOrNull { it.id == id }
    }

    val t = if (lang == "fa") Locales.fa else Locales.en
    val isDarkMode = themeMode == "dark"

    var lastBackPressTime by remember { mutableStateOf(0L) }

    // ── Global Back Navigation & Exit Guard Hierarchy ──
    if (openServer != null) {
        BackHandler {
            openServerId = null
        }
    } else if (currentNav != 0) {
        BackHandler {
            currentNav = 0
        }
    } else {
        BackHandler {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                val activity = ctx.findActivity()
                activity?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(ctx, t.pressAgainToExit, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Deep-link routing from push notifications
    LaunchedEffect(pendingServerId.value) {
        val id = pendingServerId.value
        if (id != null) {
            val s = Prefs.loadServers(ctx).firstOrNull { it.id == id }
            if (s != null) openServerId = s.id
            pendingServerId.value = null
        }
    }

    // H9: if the saved id no longer resolves (server deleted), drop it so
    // the state bundle never carries a dangling reference.
    LaunchedEffect(openServerId) {
        if (openServerId != null && openServer == null) openServerId = null
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
                    } catch (_: Throwable) {}
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
                                onBack = { openServerId = null }
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
                                    onOpen = { openServerId = it.id }
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
            color = Ds.surfaceElevated.copy(alpha = 0.92f),
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
            modifier = Modifier.fillMaxWidth().height(62.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEachIndexed { index, spec ->
                    val isSelected = index == currentNav

                    val glyphScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.10f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.65f,
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
                            .clip(RoundedCornerShape(22.dp))
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Ds.accent.copy(alpha = 0.20f),
                                                    Ds.accent.copy(alpha = 0.06f)
                                                )
                                            )
                                        )
                                        .border(
                                            BorderStroke(1.dp, Ds.accent.copy(alpha = 0.38f)),
                                            RoundedCornerShape(22.dp)
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Throwable) {}
                                onNavSelect(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isSelected) {
                                // Top micro laser dot
                                Box(
                                    modifier = Modifier
                                        .size(width = 14.dp, height = 2.dp)
                                        .clip(CircleShape)
                                        .background(Ds.accent)
                                )
                                Spacer(Modifier.height(2.dp))
                            }

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
                                enter = fadeIn(tween(140)) + expandVertically(spring(stiffness = Spring.StiffnessMedium)),
                                exit = fadeOut(tween(100)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
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
