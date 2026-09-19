package org.didban.monitor

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

internal fun monitoringNotificationsEnabled(context: android.content.Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val nm = context.getSystemService(NotificationManager::class.java)
    return listOf("didban_uptime", MonitorService.CHANNEL_ALERT, MonitorService.CHANNEL_STATUS).all {
        nm.getNotificationChannel(it)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}

@Composable
internal fun CommandMonitoringControl(copy: CommandCopy, activeTargets: Int) {
    val context = LocalContext.current
    val state by MonitoringControl.status.collectAsState()
    val owner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(monitoringNotificationsEnabled(context)) }
    var permissionPending by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = monitoringNotificationsEnabled(context)
        if (permissionPending) {
            permissionPending = false
            // Notification permission is not permission to monitor: Android permits
            // foreground checks even when alerts are denied. Keep the warning visible.
            MonitoringControl.start(context)
        }
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = monitoringNotificationsEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    CommandMonitoringCard(copy, state, activeTargets, notificationsAllowed, permissionPending,
        onStart = {
            settingsError = false
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionPending = true
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else MonitoringControl.start(context)
        },
        onStop = { MonitoringControl.stop(context) },
        onNotificationSettings = {
            settingsError = runCatching {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }.isFailure
        }
    )
    if (settingsError) Text(copy.monitorSettingsError, color = CommandColors.warning)
}

/** Render-only component; opening it never requests permission or starts a service. */
@Composable
internal fun CommandMonitoringCard(
    copy: CommandCopy,
    state: MonitoringStatus,
    activeTargets: Int,
    notificationsAllowed: Boolean,
    permissionPending: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNotificationSettings: () -> Unit
) {
    var detailsVisible by rememberSaveable { mutableStateOf(false) }
    val busy = permissionPending || state.phase in setOf(MonitoringPhase.STARTING, MonitoringPhase.STOPPING)
    val label = when {
        permissionPending -> copy.monitorPermissionPending
        state.phase == MonitoringPhase.STARTING -> copy.monitorStarting
        state.phase == MonitoringPhase.STOPPING -> copy.monitorStopping
        state.phase == MonitoringPhase.FAILED -> copy.monitorFailed
        state.running -> copy.monitorOn
        else -> copy.monitorOff
    }
    val tone = when {
        state.failure != null -> CommandHealthTone.ATTENTION
        state.running -> CommandHealthTone.HEALTHY
        busy -> CommandHealthTone.INFO
        else -> CommandHealthTone.UNKNOWN
    }
    CommandSurface(Modifier.fillMaxWidth(), raised = true) {
        Column(Modifier.padding(CommandSpacing.md), verticalArrangement = Arrangement.spacedBy(CommandSpacing.sm)) {
            Text(copy.monitorTitle, color = CommandColors.textPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            CommandStatusMark(label, tone)
            Text(copy.monitorBody, color = CommandColors.textSecondary)
            Text(copy.monitorTargets.replace("%1", activeTargets.toString()), color = CommandColors.textSecondary)
            if (activeTargets == 0) Text(copy.monitorNoTargets, color = CommandColors.textSecondary)
            if (state.running) CommandSecondaryButton(copy.monitorStop, onStop, icon = Icons.Rounded.Stop, enabled = !busy)
            else CommandPrimaryButton(if (busy) label else copy.monitorStart, onStart, icon = Icons.Rounded.PlayArrow, enabled = !busy)
            state.failure?.let { failure ->
                Text(when (failure) {
                    MonitoringFailure.START -> copy.monitorStartError
                    MonitoringFailure.STOP -> copy.monitorStopError
                    MonitoringFailure.ENGINE -> copy.monitorEngineError
                }, color = CommandColors.warning)
            }
            if (!notificationsAllowed) {
                Text(copy.monitorPermission, color = CommandColors.warning)
                CommandTextButton(copy.monitorPermissionSettings, onNotificationSettings)
            }
            CommandTextButton(if (detailsVisible) copy.hide else copy.monitorDetails, { detailsVisible = !detailsVisible })
            if (detailsVisible) Text(copy.monitorLimits, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}
