package org.didban.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val pendingServerId = androidx.compose.runtime.mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleServerIntent(intent)

        // Notification permission (Android 13+)
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
// 1. LIGHT PALETTE (Matches the SaaS card aesthetic from user's screenshot)
// ═════════════════════════════════════════════════════════════════════════════

private val LightBg = Color(0xFFF3F6FA)            // Soft light background
private val LightSurface = Color(0xFFFFFFFF)       // Crisp white cards
private val LightSurfaceLow = Color(0xFFF8FAFC)    // Soft sub-card surface
private val LightSurfaceHigh = Color(0xFFEBF2F7)   // Highlighted pill/chip
private val LightPrimary = Color(0xFF0D9488)       // Vibrant Emerald Teal ("اتصال مستقیم")
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFCCFBF1)
private val LightOnPrimaryContainer = Color(0xFF115E59)
private val LightSecondary = Color(0xFF6366F1)     // Soft Violet/Indigo
private val LightSecondaryContainer = Color(0xFFEEF2FF) // Lightbulb banner container
private val LightOnSecondaryContainer = Color(0xFF4338CA)
private val LightOutline = Color(0xFFCBD5E1)
private val LightOutlineVariant = Color(0xFFE2E8F0)
private val LightTextPrimary = Color(0xFF0F172A)
private val LightTextSecondary = Color(0xFF64748B)

val DidbanLightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

// ═════════════════════════════════════════════════════════════════════════════
// 2. DARK PALETTE (Slate & Emerald Dark Mode)
// ═════════════════════════════════════════════════════════════════════════════

private val DarkBg = Color(0xFF0B1120)             // Deep modern slate
private val DarkSurface = Color(0xFF1E293B)        // Sleek navy slate cards
private val DarkSurfaceLow = Color(0xFF151F32)     // Sub-surface
private val DarkSurfaceHigh = Color(0xFF24324D)
private val DarkPrimary = Color(0xFF14B8A6)        // Vibrant teal
private val DarkSecondary = Color(0xFF818CF8)      // Vibrant indigo
private val DarkSecondaryContainer = Color(0xFF1E1B4B)
private val DarkOnSecondaryContainer = Color(0xFFC7D2FE)
private val DarkOutline = Color(0xFF334155)
private val DarkOutlineVariant = Color(0xFF1E293B)
private val DarkTextPrimary = Color(0xFFF8FAFC)
private val DarkTextSecondary = Color(0xFF94A3B8)

val DidbanDarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = Color(0xFF38BDF8),
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF334155),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

// ═════════════════════════════════════════════════════════════════════════════
// 3. MAIN COMPOSABLE APP CONTAINER
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLanguage(ctx)) }
    var themeMode by remember { mutableStateOf(Prefs.getThemeMode(ctx)) }
    var openServer by remember { mutableStateOf<ServerConfig?>(null) }
    var currentNav by remember { mutableStateOf(0) } // 0: Servers, 1: Tunnels, 2: Uptime, 3: Network, 4: Cloudflare, 5: Vault, 6: DevLab

    val t = if (lang == "fa") Locales.fa else Locales.en
    val isDarkMode = themeMode == "dark"
    val navScrollState = rememberScrollState()

    // Deep-link from notifications: open the specific server
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
        MaterialTheme(colorScheme = if (isDarkMode) DidbanDarkColors else DidbanLightColors) {
            val appBg = if (isDarkMode) DarkBg else LightBg

            if (openServer != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .background(appBg)
                ) {
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
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .background(appBg)
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

                    // ── Modern Bottom Navigation Bar (Vector Icons) ──
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .horizontalScroll(navScrollState)
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavItem(Icons.Rounded.Dns, t.navServers, currentNav == 0) { currentNav = 0 }
                            NavItem(Icons.Rounded.SwapHoriz, t.navTunnels, currentNav == 1) { currentNav = 1 }
                            NavItem(Icons.Rounded.Timer, t.navUptime, currentNav == 2) { currentNav = 2 }
                            NavItem(Icons.Rounded.Public, t.navNetwork, currentNav == 3) { currentNav = 3 }
                            NavItem(Icons.Rounded.Cloud, t.navCloudflare, currentNav == 4) { currentNav = 4 }
                            NavItem(Icons.Rounded.Security, t.navVault, currentNav == 5) { currentNav = 5 }
                            NavItem(Icons.Rounded.Terminal, t.navTools, currentNav == 6) { currentNav = 6 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
