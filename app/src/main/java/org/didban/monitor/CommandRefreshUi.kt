package org.didban.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import java.text.DateFormat
import java.util.Date

@Composable
fun CommandRefreshButton(copy: CommandCopy, running: Boolean, onClick: () -> Unit, enabled: Boolean = true, compact: Boolean = false) {
    if (compact) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled && !running) {
            if (running) CircularProgressIndicator(
                modifier = Modifier.size(CommandMetrics.iconMedium).semantics { contentDescription = copy.refreshing },
                color = CommandColors.accent, strokeWidth = CommandMetrics.progressStroke
            ) else androidx.compose.material3.Icon(Icons.Rounded.Refresh, contentDescription = copy.refreshMetrics, tint = CommandColors.textPrimary)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (running) CircularProgressIndicator(
            modifier = Modifier.size(CommandMetrics.iconSmall), color = CommandColors.accent, strokeWidth = CommandMetrics.progressStroke
        )
        CommandTextButton(
            if (running) copy.refreshing else copy.refresh,
            onClick,
            icon = if (running) null else Icons.Rounded.Refresh,
            enabled = enabled && !running
        )
    }
}

@Composable
fun CommandRefreshFeedback(copy: CommandCopy, state: RefreshState, scopeLabel: String? = null) {
    if (state.phase == RefreshPhase.IDLE) return
    val title = when (state.phase) {
        RefreshPhase.RUNNING -> copy.refreshing
        RefreshPhase.EMPTY -> copy.refreshEmpty
        RefreshPhase.TIMED_OUT -> copy.refreshTimedOut
        RefreshPhase.INTERRUPTED -> copy.refreshInterrupted
        RefreshPhase.COMPLETE -> when {
            state.failed == 0 -> copy.refreshComplete
            state.succeeded == 0 -> copy.refreshFailed
            else -> copy.refreshPartial
        }
        RefreshPhase.IDLE -> return
    }
    val color = when {
        state.running -> CommandColors.accent
        state.phase == RefreshPhase.TIMED_OUT || state.phase == RefreshPhase.INTERRUPTED || state.failed > 0 -> CommandColors.warning
        else -> CommandColors.textSecondary
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)
    ) {
        if (state.running) CircularProgressIndicator(Modifier.size(CommandMetrics.iconSmall), color = color, strokeWidth = CommandMetrics.progressStroke)
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(scopeLabel, title).joinToString(" · "), color = color,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            if (state.targets.isNotEmpty()) {
                val progress = if (state.running) "${state.results.size}/${state.targets.size}" else buildString {
                    append("${state.succeeded}/${state.targets.size} ${copy.refreshComplete}")
                    if (state.failed > 0) append(" · ${state.failed} ${copy.operationFailed}")
                    if (state.pending > 0) append(" · ${state.pending} ${copy.refreshPending}")
                }
                val time = if (state.finishedAt > 0) " · ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.finishedAt))}" else ""
                Text(progress + time, color = CommandColors.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** State for single-resource screen loaders; old data never implies a new success. */
internal fun commandLoadState(loading: Boolean, error: String?, finishedAt: Long, id: Long?): RefreshState =
    RefreshState(
        phase = when {
            loading -> RefreshPhase.RUNNING
            finishedAt > 0 -> RefreshPhase.COMPLETE
            else -> RefreshPhase.IDLE
        },
        targets = listOfNotNull(id),
        results = if (!loading && finishedAt > 0 && id != null) mapOf(id to (error == null)) else emptyMap(),
        finishedAt = if (loading) 0L else finishedAt
    )
