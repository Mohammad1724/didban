package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun CommandPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CommandColors.infoSurface.copy(alpha = 0.22f), CommandColors.canvas),
                    radius = 900f
                )
            )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(CommandColors.accent)
                )
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = CommandColors.textPrimary
                )
            }
            if (supporting != null) {
                Spacer(Modifier.height(CommandSpacing.xxs))
                Text(
                    text = supporting,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontFamily = Telemetry),
                    color = CommandColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
        shape = RoundedCornerShape(14.dp),
        border = if (border) BorderStroke(1.dp, CommandColors.border) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
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
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
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
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
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
            .clip(RoundedCornerShape(10.dp))
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
                .size(52.dp)
                .background(CommandColors.infoSurface, CircleShape)
                .border(1.dp, CommandColors.borderStrong, CircleShape),
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
fun CommandTelemetryPill(
    text: String,
    tone: CommandHealthTone = CommandHealthTone.INFO,
    modifier: Modifier = Modifier
) {
    val color = tone.color()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tone.background())
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(
            text,
            color = color,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CommandMetricTile(
    label: String,
    value: String,
    supporting: String,
    tone: CommandHealthTone = CommandHealthTone.INFO,
    modifier: Modifier = Modifier,
    valueColorOverride: Color? = null
) {
    val valueColor = valueColorOverride ?: when (tone) {
        CommandHealthTone.HEALTHY -> CommandColors.success
        CommandHealthTone.ATTENTION -> CommandColors.warning
        CommandHealthTone.OFFLINE -> CommandColors.danger
        CommandHealthTone.INFO -> CommandColors.accent
        CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
    }
    Column(
        modifier = modifier
            .background(CommandColors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, CommandColors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(label, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            color = valueColor,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontFamily = Telemetry, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(supporting, color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(valueColor.copy(alpha = 0.72f), RoundedCornerShape(99.dp)))
    }
}

@Composable
fun CommandRingGauge(
    score: Int?,
    label: String,
    modifier: Modifier = Modifier
) {
    val progress = score?.coerceIn(0, 100)?.div(100f) ?: 0f
    val ringColor = when {
        score == null -> CommandColors.textTertiary
        score < 50 -> CommandColors.danger
        score < 80 -> CommandColors.warning
        else -> CommandColors.accent
    }
    val trackColor = CommandColors.track
    Box(modifier = modifier.size(142.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (score != null) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score?.toString() ?: "—",
                color = ringColor,
                style = androidx.compose.material3.MaterialTheme.typography.displayLarge.copy(fontFamily = Telemetry, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            )
            Text(label, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun CommandTelemetryOrbit(
    tones: List<CommandHealthTone>,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val nodeColors = tones.take(8).map { it.color() }
    val orbitBorder = CommandColors.border
    val orbitBorderStrong = CommandColors.borderStrong
    val orbitCanvas = CommandColors.canvas
    Box(
        modifier = modifier
            .height(184.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(CommandColors.infoSurface.copy(alpha = 0.55f), orbitCanvas),
                    radius = 420f
                )
            )
            .border(1.dp, CommandColors.border, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.31f
            val secondaryRadius = radius * 0.62f
            drawCircle(orbitBorderStrong.copy(alpha = 0.62f), radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(orbitBorder.copy(alpha = 0.9f), secondaryRadius, center, style = Stroke(1.dp.toPx()))
            drawLine(orbitBorder.copy(alpha = 0.72f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 1.dp.toPx())
            drawLine(orbitBorder.copy(alpha = 0.72f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 1.dp.toPx())
            nodeColors.forEachIndexed { index, color ->
                val angle = (-Math.PI / 2.0) + (Math.PI * 2.0 * index / maxOf(nodeColors.size, 1))
                val point = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius
                )
                drawCircle(color.copy(alpha = 0.2f), 10.dp.toPx(), point)
                drawCircle(color, 4.dp.toPx(), point)
            }
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(CommandColors.accent.copy(alpha = 0.10f))
                .border(1.dp, CommandColors.accent.copy(alpha = 0.48f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = CommandColors.accent, modifier = Modifier.size(23.dp))
        }
        Text(
            caption ?: if (tones.isEmpty()) "NO TELEMETRY" else "${tones.size} NODES",
            color = CommandColors.textTertiary,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )
    }
}

@Composable
fun CommandTelemetryBar(
    label: String,
    value: Float?,
    tone: CommandHealthTone = CommandHealthTone.INFO,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null
) {
    val color = colorOverride ?: tone.color()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        Text(label, color = CommandColors.textTertiary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry), modifier = Modifier.width(31.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(99.dp)).background(CommandColors.track)) {
            if (value != null) {
                Box(Modifier.fillMaxWidth(value.coerceIn(0f, 100f) / 100f).fillMaxHeight().background(color, RoundedCornerShape(99.dp)))
            }
        }
        Text(value?.let(Fmt::pct) ?: "—", color = CommandColors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry), modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
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
