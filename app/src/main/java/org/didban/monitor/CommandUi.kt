package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CommandPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CommandColors.canvas)
            .padding(horizontal = CommandSpacing.md),
        content = content
    )
}

@Composable
fun CommandSectionTitle(
    title: String,
    supporting: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = CommandColors.textPrimary
            )
            if (supporting != null) {
                Spacer(Modifier.height(CommandSpacing.xxs))
                Text(
                    text = supporting,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = CommandColors.textSecondary
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            CommandTextButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun CommandRule(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CommandColors.border)
    )
}

@Composable
fun CommandSurface(
    modifier: Modifier = Modifier,
    raised: Boolean = false,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = if (raised) CommandColors.surfaceRaised else CommandColors.surface,
        shape = RoundedCornerShape(8.dp),
        border = if (border) BorderStroke(1.dp, CommandColors.border) else null
    ) {
        Column(content = content)
    }
}

@Composable
fun CommandStatusMark(
    label: String,
    tone: CommandHealthTone,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    val color = tone.color()
    val background = tone.background()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column {
            Text(
                label,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    detail,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = CommandColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

enum class CommandHealthTone {
    HEALTHY,
    ATTENTION,
    OFFLINE,
    UNKNOWN,
    INFO
}

@Composable
private fun CommandHealthTone.color(): Color = when (this) {
    CommandHealthTone.HEALTHY -> CommandColors.success
    CommandHealthTone.ATTENTION -> CommandColors.warning
    CommandHealthTone.OFFLINE -> CommandColors.danger
    CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
    CommandHealthTone.INFO -> CommandColors.info
}

@Composable
private fun CommandHealthTone.background(): Color = when (this) {
    CommandHealthTone.HEALTHY -> CommandColors.successSurface
    CommandHealthTone.ATTENTION -> CommandColors.warningSurface
    CommandHealthTone.OFFLINE -> CommandColors.dangerSurface
    CommandHealthTone.UNKNOWN -> CommandColors.surfaceRaised
    CommandHealthTone.INFO -> CommandColors.infoSurface
}

@Composable
fun CommandPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CommandColors.accent,
            contentColor = CommandColors.onAccent,
            disabledContainerColor = CommandColors.border,
            disabledContentColor = CommandColors.textTertiary
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(CommandSpacing.xs))
        }
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CommandSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (enabled) CommandColors.borderStrong else CommandColors.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = CommandColors.textPrimary,
            disabledContentColor = CommandColors.textTertiary
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(CommandSpacing.xs))
        }
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CommandTextButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(17.dp))
        Text(
            text,
            color = CommandColors.accent,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun CommandIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(48.dp)
            .semantics { role = Role.Button }
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) CommandColors.textPrimary else CommandColors.textTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun CommandStateBlock(
    title: String,
    body: String,
    tone: CommandHealthTone = CommandHealthTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val icon = when (tone) {
        CommandHealthTone.HEALTHY -> Icons.Rounded.CheckCircle
        CommandHealthTone.ATTENTION -> Icons.Rounded.WarningAmber
        CommandHealthTone.OFFLINE -> Icons.Rounded.ErrorOutline
        CommandHealthTone.UNKNOWN -> Icons.Rounded.Info
        CommandHealthTone.INFO -> Icons.Rounded.Info
    }
    val color = tone.color()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.background(), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(CommandSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(CommandSpacing.sm))
            Text(
                title,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = CommandColors.textPrimary
            )
        }
        Spacer(Modifier.height(CommandSpacing.xs))
        Text(
            body,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = CommandColors.textSecondary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(CommandSpacing.sm))
            CommandSecondaryButton(actionLabel, onAction, modifier = Modifier.align(Alignment.Start))
        }
    }
}

@Composable
fun CommandEmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CommandSpacing.xxl, horizontal = CommandSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .border(1.dp, CommandColors.borderStrong, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = CommandColors.textSecondary)
        }
        Spacer(Modifier.height(CommandSpacing.md))
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = CommandColors.textPrimary
        )
        Spacer(Modifier.height(CommandSpacing.xs))
        Text(
            body,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = CommandColors.textSecondary
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(CommandSpacing.md))
            CommandPrimaryButton(actionLabel, onAction, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
fun CommandMetricLine(
    label: String,
    value: String,
    tone: CommandHealthTone = CommandHealthTone.INFO,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = CommandColors.textSecondary)
        Text(
            value,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontFamily = Telemetry),
            color = when (tone) {
                CommandHealthTone.HEALTHY -> CommandColors.success
                CommandHealthTone.ATTENTION -> CommandColors.warning
                CommandHealthTone.OFFLINE -> CommandColors.danger
                else -> CommandColors.textPrimary
            }
        )
    }
}

@Composable
fun CommandBackButton(text: String, onClick: () -> Unit) {
    CommandTextButton(text = text, onClick = onClick, icon = if (LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl) Icons.Rounded.ArrowForward else Icons.Rounded.ArrowBack)
}

@Composable
fun CommandCloseButton(text: String, onClick: () -> Unit) {
    CommandTextButton(text = text, onClick = onClick, icon = Icons.Rounded.Close)
}

@Composable
fun CommandSectionRule() {
    Spacer(Modifier.height(CommandSpacing.lg))
    CommandRule()
    Spacer(Modifier.height(CommandSpacing.lg))
}
