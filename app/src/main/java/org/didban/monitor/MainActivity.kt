@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

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

        // Ask for notification permission when the user starts monitoring,
        // not before they know why the app needs it.

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
    val language = remember { Prefs.getLanguage(context) }
    val themeMode = remember { Prefs.getThemeMode(context) }
    val copy = remember(language) { CommandCopy.forLanguage(language) }
    var copied by remember { mutableStateOf(false) }

    // The recovery path is part of the product shell too: it must not switch
    // to a second visual language while the rest of the app uses CommandTheme.
    CommandTheme(themeMode = themeMode, language = language) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .commandAtmosphere()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(CommandSpacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CommandStateBlock(
                title = copy.crashTitle,
                body = copy.crashBody,
                tone = CommandHealthTone.OFFLINE,
                modifier = Modifier.fillMaxWidth()
            )

            // H8: make a detected cannot-start loop explicit to the user.
            if (consecutiveCount >= CrashPolicy.CRASH_LOOP_THRESHOLD) {
                Spacer(Modifier.height(CommandSpacing.sm))
                CommandStateBlock(
                    title = copy.crashLoopTitle,
                    body = copy.crashLoopBody.replace("%1", consecutiveCount.toString()),
                    tone = CommandHealthTone.ATTENTION,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(CommandSpacing.md))

            CommandSurface(
                raised = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    trace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(CommandSpacing.md),
                    color = CommandColors.textTertiary,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Spacer(Modifier.height(CommandSpacing.md))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)
            ) {
                CommandSecondaryButton(
                    text = if (copied) copy.crashCopied else copy.crashCopyLog,
                    onClick = {
                        SensitiveClipboard.copy(context, copy.crashTitle, trace)
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.ContentCopy
                )
                CommandPrimaryButton(
                    text = copy.crashResetLaunch,
                    onClick = onReset,
                    modifier = Modifier.weight(1.3f),
                    icon = Icons.Rounded.Refresh
                )
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
