@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

// ═════════════════════════════════════════════════════════════════════════════
//  DIDBAN · OBSIDIAN ZENITH COMPONENT LIBRARY
//  Hyper-clean, noise-free, high-information-density UI primitives.
// ═════════════════════════════════════════════════════════════════════════════

/** Root app background: Deep Obsidian canvas with dynamic aurora glow at the top. */
@Composable
fun DidbanBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glow = Ds.accent
    val canvasColor = Ds.canvas
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(canvasColor)
                // Ambient top glow
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(glow.copy(alpha = 0.05f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.45f
                    ),
                    topLeft = Offset(-size.width * 0.2f, -size.height * 0.12f),
                    size = Size(size.width * 1.4f, size.height * 0.6f)
                )
            }
    ) { content() }
}

// ── Sentinel Radar Mark ──────────────────────────────────────────────────────

@Composable
fun RadarMark(
    diameter: Dp = 34.dp,
    modifier: Modifier = Modifier,
    sweep: Boolean = true,
    tint: Color = Ds.accent
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "radarAngle"
    )
    val blip by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "radarBlip"
    )

    Canvas(modifier.size(diameter)) {
        val c = center
        val r = min(size.width, size.height) / 2f - 2.dp.toPx()

        // Concentric telemetry rings
        drawCircle(color = tint.copy(alpha = 0.38f), radius = r, center = c, style = Stroke(1.1.dp.toPx()))
        drawCircle(color = tint.copy(alpha = 0.18f), radius = r * 0.66f, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(color = tint.copy(alpha = 0.10f), radius = r * 0.33f, center = c, style = Stroke(1.dp.toPx()))

        // Compass crosshairs
        val tick = 3.dp.toPx()
        val inner = r + 1.dp.toPx()
        listOf(0f, 90f, 180f, 270f).forEach { deg ->
            rotate(deg, pivot = c) {
                drawLine(
                    color = tint.copy(alpha = 0.35f),
                    start = Offset(c.x, c.y - inner),
                    end = Offset(c.x, c.y - inner - tick),
                    strokeWidth = 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Rotating radar beam
        if (sweep) {
            rotate(angle, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.70f to Color.Transparent,
                        0.92f to tint.copy(alpha = 0.22f),
                        1f to Color.Transparent
                    ),
                    radius = r, center = c
                )
            }
        }

        // Core beacon + echoes
        drawCircle(color = tint.copy(alpha = 0.20f), radius = 5.dp.toPx(), center = c)
        drawCircle(color = tint, radius = 2.2.dp.toPx(), center = c)
        drawCircle(color = tint.copy(alpha = blip * 0.9f), radius = 1.8.dp.toPx(), center = Offset(c.x + r * 0.52f, c.y - r * 0.28f))
        drawCircle(color = tint.copy(alpha = blip * 0.6f), radius = 1.4.dp.toPx(), center = Offset(c.x - r * 0.38f, c.y + r * 0.42f))
    }
}

// ── Live Pulse Beacon Dot ────────────────────────────────────────────────────

@Composable
fun PulseDot(
    modifier: Modifier = Modifier,
    color: Color = Ds.ok,
    size: Dp = 8.dp,
    pulsing: Boolean = true
) {
    if (!pulsing) {
        Box(modifier.size(size).background(color, CircleShape))
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.9f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.85f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseScale"
    )
    Box(modifier.size(size * 1.9f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * scale)
                .background(color.copy(alpha = alpha * 0.4f), CircleShape)
        )
        Box(
            Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

// ── Surfaces & Bento Cards ───────────────────────────────────────────────────

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    containerColor: Color = Ds.surface,
    borderColor: Color = Ds.hairline,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        if (onClick != null) {
            Column(
                Modifier
                    .clickable(onClick = onClick)
                    .padding(padding),
                content = content
            )
        } else {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModernCard(modifier = modifier, content = content)
}

/** Luxury Bento Metric Card with Icon, Title, Tabular Value, and Progress/Trend. */
@Composable
fun BentoMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    tone: Color = Ds.accent,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    ModernCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        padding = 14.dp,
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Ds.textSecondary,
                maxLines = 1
            )
            IconBadge(icon = icon, tint = tone, background = tone.copy(alpha = 0.12f), size = 30.dp, iconSize = 16.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Telemetry,
            color = Ds.textPrimary,
            maxLines = 1
        )
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ds.track)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                        .background(tone, RoundedCornerShape(2.dp))
                )
            }
        }
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = Ds.textTertiary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = Ds.accent,
    background: Color = Ds.accentDim,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = background,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Ds.hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

// ── Status Pills & Badges ───────────────────────────────────────────────────

enum class StatusLevel { Ok, Warn, Danger, Info, Neutral }

@Composable
fun StatusPill(
    text: String,
    level: StatusLevel = StatusLevel.Ok,
    pulse: Boolean = true,
    modifier: Modifier = Modifier
) {
    val (tone, toneDim) = when (level) {
        StatusLevel.Ok -> Ds.ok to Ds.okDim
        StatusLevel.Warn -> Ds.warn to Ds.warnDim
        StatusLevel.Danger -> Ds.danger to Ds.dangerDim
        StatusLevel.Info -> Ds.info to Ds.infoDim
        StatusLevel.Neutral -> Ds.neutral to Ds.surfaceHighlight
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = toneDim,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PulseDot(color = tone, size = 6.dp, pulsing = pulse && level != StatusLevel.Neutral)
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                maxLines = 1
            )
        }
    }
}

// ── Banners & Notices ───────────────────────────────────────────────────────

enum class BannerTone { Info, Warn, Danger, Ok }

@Composable
fun BannerCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    val (c, bg) = when (tone) {
        BannerTone.Info -> Ds.info to Ds.infoDim
        BannerTone.Warn -> Ds.warn to Ds.warnDim
        BannerTone.Danger -> Ds.danger to Ds.dangerDim
        BannerTone.Ok -> Ds.ok to Ds.okDim
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, c.copy(alpha = 0.28f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = c, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                fontSize = 12.5.sp,
                color = Ds.textPrimary,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                action()
            }
        }
    }
}

// ── Top Navigation Bars ─────────────────────────────────────────────────────

@Composable
fun ModernTopBar(
    title: String,
    subtitle: String? = null,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onToggleLang: () -> Unit,
    langLabel: String = "FA / EN",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadarMark(diameter = 32.dp, tint = Ds.accent)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.textPrimary,
                    letterSpacing = (-0.2).sp
                )
                Spacer(Modifier.width(7.dp))
                PulseDot(color = Ds.ok, size = 6.dp)
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = Ds.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleIconButton(
                icon = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                contentDescription = "Theme",
                tint = Ds.warn,
                onClick = onToggleTheme
            )
            CircleIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                tint = Ds.textSecondary,
                onClick = onRefresh
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Ds.surface,
                border = BorderStroke(1.dp, Ds.hairline),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onToggleLang() }
            ) {
                Text(
                    langLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ds.accent
                )
            }
        }
    }
}

@Composable
fun HeaderWithBack(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.rounded.ArrowBack,
            contentDescription = "Back",
            tint = Ds.textPrimary,
            onClick = onBack
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ds.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    fontFamily = Telemetry,
                    color = Ds.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badge: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Ds.textPrimary
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Ds.surfaceHighlight,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    badge,
                    fontSize = 10.sp,
                    fontFamily = Telemetry,
                    fontWeight = FontWeight.Bold,
                    color = Ds.accent
                )
            }
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

// ── Buttons ─────────────────────────────────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Ds.accent,
            contentColor = Ds.onAccent,
            disabledContainerColor = Ds.surfaceHighlight,
            disabledContentColor = Ds.textTertiary
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        modifier = modifier.height(46.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Ds.onAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tone: Color = Ds.accent
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = tone.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.24f)),
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = tone)
        }
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SoftButton(text = text, onClick = onClick, modifier = modifier, icon = icon, enabled = enabled, tone = Ds.danger)
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ds.textSecondary,
    size: Dp = 36.dp
) {
    Surface(
        shape = CircleShape,
        color = Ds.surface,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(17.dp))
        }
    }
}

// ── Segmented Control & Tabs ────────────────────────────────────────────────

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) Ds.surfaceHighlight else Color.Transparent,
                    animationSpec = tween(200), label = "tabBg"
                )
                val txtColor by animateColorAsState(
                    targetValue = if (selected) Ds.accent else Ds.textTertiary,
                    animationSpec = tween(200), label = "tabTxt"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onSelect(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = txtColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FilterChipRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) Ds.accentDim else Ds.surfaceLow,
                border = BorderStroke(1.dp, if (selected) Ds.accent.copy(alpha = 0.4f) else Ds.hairline),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onSelect(index) }
            ) {
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Ds.accent else Ds.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

// ── Terminal & Code Display ─────────────────────────────────────────────────

@Composable
fun TerminalBox(
    command: String,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(0xFFFF5F56), CircleShape))
                    Box(Modifier.size(8.dp).background(Color(0xFFFFBD2E), CircleShape))
                    Box(Modifier.size(8.dp).background(Color(0xFF27C93F), CircleShape))
                    if (title != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(title, fontSize = 10.5.sp, color = Ds.textTertiary, fontFamily = Telemetry)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (copied) Ds.okDim else Ds.surfaceHighlight,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            clipboard.setText(AnnotatedString(command))
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        }
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (copied) Ds.ok else Ds.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            if (copied) "Copied" else "Copy",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (copied) Ds.ok else Ds.accent
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                command,
                fontSize = 12.sp,
                fontFamily = Telemetry,
                color = Ds.textPrimary,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Text Fields ─────────────────────────────────────────────────────────────

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var passVisible by remember { mutableStateOf(!isPassword) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ds.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = Ds.surfaceLow,
            border = BorderStroke(1.dp, Ds.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 13.sp,
                            color = Ds.textTertiary,
                            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        keyboardOptions = keyboardOptions,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontFamily = AppFontFamily,
                            color = Ds.textPrimary,
                            textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                        ),
                        cursorBrush = SolidColor(Ds.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isPassword) {
                    IconButton(
                        onClick = { passVisible = !passVisible },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (passVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Toggle password",
                            tint = Ds.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }
    }
}

@Composable
fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ds.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = Ds.surfaceLow,
            border = BorderStroke(1.dp, Ds.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, fontSize = 12.5.sp, fontFamily = Telemetry, color = Ds.textTertiary)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, fontFamily = Telemetry, color = Ds.textPrimary),
                        cursorBrush = SolidColor(Ds.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = Ds.textTertiary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 12.5.sp,
                        color = Ds.textTertiary,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontFamily = AppFontFamily,
                        color = Ds.textPrimary,
                        textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
                    ),
                    cursorBrush = SolidColor(Ds.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Clear",
                    tint = Ds.textTertiary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onValueChange("") }
                )
            }
        }
    }
}

// ── Gauges, Meters & Heartbeats ─────────────────────────────────────────────

@Composable
fun RingGauge(
    value: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.5.dp,
    tone: Color = Ds.accent,
    track: Color = Ds.track,
    content: (@Composable () -> Unit)? = null
) {
    val target = (value / 100f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "ring"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            if (progress > 0.003f) {
                drawArc(color = tone, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = stroke)
            }
        }
        content?.invoke()
    }
}

@Composable
fun MeterBar(
    value01: Float,
    modifier: Modifier = Modifier,
    width: Dp = 4.dp,
    height: Dp = 32.dp,
    tone: Color = Ds.accent
) {
    Box(
        modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(2.dp))
            .background(Ds.track)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(value01.coerceIn(0.02f, 1f))
                .background(tone, RoundedCornerShape(2.dp))
        )
    }
}

data class StatItem(
    val label: String,
    val value: String,
    val tone: Color = Color.Unspecified,
    val bar: Float? = null
)

@Composable
fun StatBand(
    stats: List<StatItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            stats.forEachIndexed { i, s ->
                if (i > 0) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(Ds.hairline)
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        s.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ds.textTertiary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        s.value,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = Telemetry,
                        color = if (s.tone == Color.Unspecified) Ds.textPrimary else s.tone,
                        maxLines = 1
                    )
                    if (s.bar != null) {
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Ds.surfaceHighlight)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(s.bar.coerceIn(0.02f, 1f))
                                    .background(if (s.tone == Color.Unspecified) Ds.accent else s.tone, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeartbeatBar(
    statuses: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        repeat(30) { i ->
            val st = statuses.getOrNull(statuses.size - 30 + i)
            val color = when (st) {
                1 -> Ds.ok
                0 -> Ds.danger
                else -> Ds.track
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

// ── State Screens ───────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    radar: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (radar) {
            RadarMark(diameter = 80.dp, tint = Ds.accent.copy(alpha = 0.85f))
        } else if (icon != null) {
            IconBadge(icon = icon, tint = Ds.accent, background = Ds.accentDim, size = 56.dp, iconSize = 26.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                fontSize = 12.5.sp,
                color = Ds.textSecondary,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            SoftButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LoadingState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = Ds.accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 12.sp, color = Ds.textTertiary)
    }
}
