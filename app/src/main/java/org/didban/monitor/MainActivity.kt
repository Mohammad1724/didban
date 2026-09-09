package org.didban.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

// ── "Obsidian" premium dark palette ─────────────────────────────────────────

private val Bg0 = Color(0xFF090D16)        // deepest background
private val Bg1 = Color(0xFF0D1320)        // surface
private val Bg2 = Color(0xFF121A2C)        // low container
private val Bg3 = Color(0xFF161F34)        // container
private val Bg4 = Color(0xFF1B253E)        // high container
private val Bg5 = Color(0xFF202C48)        // highest container

private val Ink0 = Color(0xFFF1F5F9)       // primary text — near white
private val Ink1 = Color(0xFFA9B6CE)       // secondary text — light slate
private val Accent = Color(0xFF4CC2FF)     // vivid sky
private val OnAccent = Color(0xFF002E44)   // dark navy on accent
private val Indigo = Color(0xFFA5B4FC)
private val Teal = Color(0xFF5EEAD4)

private val DidbanColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Color(0xFF1E4976),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Indigo,
    onSecondary = Color(0xFF1B1E4B),
    secondaryContainer = Color(0xFF2E3A6E),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Teal,
    onTertiary = Color(0xFF07332E),
    tertiaryContainer = Color(0xFF0F4A44),
    onTertiaryContainer = Color(0xFFB8FFF2),
    background = Bg0,
    onBackground = Ink0,
    surface = Bg1,
    onSurface = Ink0,
    surfaceVariant = Color(0xFF18213A),
    onSurfaceVariant = Ink1,
    surfaceDim = Color(0xFF060A12),
    surfaceBright = Color(0xFF2C3A57),
    surfaceContainerLowest = Bg0,
    surfaceContainerLow = Bg2,
    surfaceContainer = Bg3,
    surfaceContainerHigh = Bg4,
    surfaceContainerHighest = Bg5,
    outline = Color(0xFF3B4B6B),
    outlineVariant = Color(0xFF24304D),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1515),
    onErrorContainer = Color(0xFFFFD5D5),
    inverseSurface = Color(0xFFE2E8F0),
    inversePrimary = Color(0xFF00558A),
    scrim = Color(0xFF000000)
)

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLanguage(ctx)) }
    var openServer by remember { mutableStateOf<ServerConfig?>(null) }
    var currentNav by remember { mutableStateOf(0) } // 0: Servers, 1: Uptime, 2: Network, 3: Cloudflare, 4: Vault, 5: DevLab

    val t = if (lang == "fa") Locales.fa else Locales.en

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
        MaterialTheme(colorScheme = DidbanColors) {
            if (openServer != null) {
                DashboardScreen(t = t, server = openServer!!, onBack = { openServer = null })
            } else {
                Column(Modifier.fillMaxSize().background(Bg0)) {
                    Box(Modifier.weight(1f)) {
                        when (currentNav) {
                            0 -> ServersScreen(
                                t = t,
                                onLanguage = { new ->
                                    lang = new
                                    Prefs.setLanguage(ctx, new)
                                },
                                onOpen = { openServer = it }
                            )
                            1 -> UptimeScreen(t = t)
                            2 -> NetworkHubScreen(t = t)
                            3 -> CloudflareScreen(t = t)
                            4 -> VaultScreen(t = t)
                            5 -> DevLabScreen(t = t)
                        }
                    }

                    // ── Bottom Navigation Bar ──
                    Surface(
                        color = Bg1,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavItem("👁️", t.navServers, currentNav == 0) { currentNav = 0 }
                            NavItem("⏱️", t.navUptime, currentNav == 1) { currentNav = 1 }
                            NavItem("🛰️", t.navNetwork, currentNav == 2) { currentNav = 2 }
                            NavItem("☁️", t.navCloudflare, currentNav == 3) { currentNav = 3 }
                            NavItem("🔐", t.navVault, currentNav == 4) { currentNav = 4 }
                            NavItem("🛠️", t.navTools, currentNav == 5) { currentNav = 5 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            label,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
