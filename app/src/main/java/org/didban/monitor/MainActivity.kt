package org.didban.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var lang by remember { mutableStateOf(Prefs.getLanguage(ctx)) }
    var openServer by remember { mutableStateOf<ServerConfig?>(null) }

    // Deep link from notifications: open the specific server
    LaunchedEffect(pendingServerId.value) {
        val id = pendingServerId.value
        if (id != null) {
            val s = Prefs.loadServers(ctx).firstOrNull { it.id == id }
            if (s != null) openServer = s
            pendingServerId.value = null
        }
    }

    val t = if (lang == "fa") Locales.fa else Locales.en

    val colors = darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF0B1220),
        background = Color(0xFF0B1220),
        surface = Color(0xFF111A2E),
        surfaceVariant = Color(0xFF1B2740),
        onSurface = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF94A3B8),
        secondary = Color(0xFFA78BFA)
    )

    CompositionLocalProvider(
        LocalLayoutDirection provides if (lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(colorScheme = colors) {
            if (openServer == null) {
                ServersScreen(
                    t = t,
                    onLanguage = { new ->
                        lang = new
                        Prefs.setLanguage(ctx, new)
                    },
                    onOpen = { openServer = it }
                )
            } else {
                DashboardScreen(t = t, server = openServer!!, onBack = { openServer = null })
            }
        }
    }
}
