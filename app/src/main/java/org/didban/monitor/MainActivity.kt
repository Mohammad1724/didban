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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
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

            val currentCrashTrace = crashTrace
            if (currentCrashTrace != null) {
                CrashRecoveryScreen(
                    trace = currentCrashTrace,
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
        setIntent(intent)
        try {
            handleServerIntent(intent)
        } catch (_: Throwable) {}
    }

    private fun handleServerIntent(intent: Intent?) {
        // MainActivity is exported for the launcher. Never trust navigation
        // extras supplied by another app; only our immutable notification
        // PendingIntent carries the process-scoped capability.
        pendingServerId.value = InternalNavigation.trustedServerId(intent)
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
    val context = LocalContext.current
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
                            SensitiveClipboard.copy(context, "Didban crash diagnostic", trace)
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

// ═════════════════════════════════════════════════════════════════════════
// APP ENTRY — Blank-canvas command shell
//
// Crash recovery stays in this activity boundary. The product shell, route
// state, RTL direction and new visual language live in CommandCenterApp.
// Existing engines and feature screens remain reachable through the explicit
// migration bridge until each workspace is rebuilt.
// ═════════════════════════════════════════════════════════════════════════

@Composable
fun DidbanApp(pendingServerId: androidx.compose.runtime.MutableState<Long?>) {
    CommandCenterApp(pendingServerId)
}
