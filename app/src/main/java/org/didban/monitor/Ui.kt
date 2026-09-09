@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.didban.monitor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ═════════════════════════════════════════════════════════════════════════════
//  DIDBAN · NIGHTWATCH COMPONENT LIBRARY
//  Flat surfaces, hairline structure, one luminous accent, quiet motion.
// ═════════════════════════════════════════════════════════════════════════════

/** App background: deep canvas + a whisper of accent glow near the top. */
@Composable
fun DidbanBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glow = Ds.accent
    val canvasColor = Ds.canvas
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(canvasColor)
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(glow.copy(alpha = 0.055f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.5f
                    ),
                    topLeft = Offset(-size.width * 0.25f, -size.height * 0.15f),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.5f, size.height * 0.7f)
                )
            }
    ) { content() }
}

// ── Radar mark — the signature of the sentinel ───────────────────────────────

@Composable
fun RadarMark(
    diameter: Dp = 32.dp,
    modifier: Modifier = Modifier,
    sweep: Boolean = true,
    tint: Color = Ds.accent
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "radarAngle"
    )
    val blip by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(1700, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "radarBlip"
    )
    Canvas(modifier.size(diameter)) {
        val c = center
        val r = min(size.width, size.height) / 2f - 2.dp.toPx()
        // concentric rings
        drawCircle(color = tint.copy(alpha = 0.42f), radius = r, center = c, style = Stroke(1.1.dp.toPx()))
        drawCircle(color = tint.copy(alpha = 0.20f), radius = r * 0.64f, center = c, style = Stroke(1.dp.toPx()))
        drawCircle(color = tint.copy(alpha = 0.11f), radius = r * 0.32f, center = c, style = Stroke(1.dp.toPx()))
        // compass ticks
        val tick = 3.2.dp.toPx()
        val inner = r + 1.2.dp.toPx()
        listOf(0f, 90f, 180f, 270f).forEach { deg ->
            rotate(deg, pivot = c) {
                drawLine(
                    color = tint.copy(alpha = 0.38f),
                    start = Offset(c.x, c.y - inner),
                    end = Offset(c.x, c.y - inner - tick),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        // rotating sweep
        if (sweep) {
            rotate(angle, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.66f to Color.Transparent,
                        0.90f to tint.copy(alpha = 0.20f),
                        1f to Color.Transparent
                    ),
                    radius = r, center = c
                )
            }
        }
        // core + echoes
        drawCircle(color = tint.copy(alpha = 0.16f), radius = 5.4.dp.toPx(), center = c)
        drawCircle(color = tint, radius = 2.1.dp.toPx(), center = c)
        drawCircle(color = tint.copy(alpha = blip * 0.85f), radius = 1.8.dp.toPx(), center = Offset(c.x + r * 0.52f, c.y - r * 0.30f))
        drawCircle(color = tint.copy(alpha = blip * 0.55f), radius = 1.4.dp.toPx(), center = Offset(c.x - r * 0.36f, c.y + r * 0.44f))
    }
}

// ── Surfaces ─────────────────────────────────────────────────────────────────

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
            Column(Modifier.clickable(onClick = onClick).padding(padding), content = content)
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
        shape = RoundedCornerShape(11.dp),
        color = background,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ds.hairline))
}

// ── Banners & guides ─────────────────────────────────────────────────────────

enum class BannerTone { Info, Warn, Danger, Ok }

@Composable
private fun toneColor(tone: BannerTone): Color = when (tone) {
    BannerTone.Info -> Ds.info
    BannerTone.Warn -> Ds.warn
    BannerTone.Danger -> Ds.danger
    BannerTone.Ok -> Ds.ok
}

@Composable
private fun toneDim(tone: BannerTone): Color = when (tone) {
    BannerTone.Info -> Ds.infoDim
    BannerTone.Warn -> Ds.warnDim
    BannerTone.Danger -> Ds.dangerDim
    BannerTone.Ok -> Ds.okDim
}

@Composable
fun Banner(
    tone: BannerTone,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val c = toneColor(tone)
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = toneDim(tone),
        border = BorderStroke(1.dp, c.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = c, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = Ds.textPrimary.copy(alpha = 0.92f),
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    actionLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = c,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            if (onDismiss != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "✕",
                    fontSize = 13.sp,
                    color = Ds.textTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Lightbulb,
    onDismiss: (() -> Unit)? = null
) {
    Banner(BannerTone.Info, text, modifier, icon = icon, onDismiss = onDismiss)
}

@Composable
fun FeatureGuideCard(
    title: String,
    description: String,
    bullets: List<String> = emptyList(),
    icon: ImageVector = Icons.Rounded.Lightbulb,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Ds.accent)
            }
            Text(
                description,
                fontSize = 11.sp,
                color = Ds.textSecondary,
                lineHeight = 17.sp
            )
            if (bullets.isNotEmpty()) {
                bullets.forEach { b ->
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .padding(top = 5.dp)
                                .size(4.dp)
                                .background(Ds.accent.copy(alpha = 0.7f), CircleShape)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(b, fontSize = 10.5.sp, color = Ds.textSecondary, lineHeight = 15.5.sp)
                    }
                }
            }
        }
    }
}

// ── Buttons & chips ──────────────────────────────────────────────────────────

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = Color.Unspecified
) {
    val useGradient = containerColor == Color.Unspecified
    val shape = RoundedCornerShape(14.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        colors = if (useGradient) {
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Ds.onAccent,
                disabledContainerColor = Ds.surfaceHigh,
                disabledContentColor = Ds.textTertiary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = Color.White,
                disabledContainerColor = Ds.surfaceHigh,
                disabledContentColor = Ds.textTertiary
            )
        },
        modifier = if (useGradient) {
            modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    brush = Brush.verticalGradient(listOf(Ds.accent, Ds.accentDeep)),
                    shape = shape
                )
        } else {
            modifier.fillMaxWidth().height(50.dp)
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = if (useGradient) Ds.onAccent else Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
fun SecondaryActionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Ds.accent
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Ds.hairlineStrong),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable { onClick() }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Quiet pill button for secondary contexts (empty states, banners, headers). */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Color = Ds.accent,
    toneDim: Color? = null
) {
    val shape = RoundedCornerShape(11.dp)
    Surface(
        shape = shape,
        color = toneDim ?: tone.copy(alpha = 0.12f),
        modifier = modifier
            .clip(shape)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tone, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1)
        }
    }
}

@Composable
fun TagChip(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Ds.accentDim else Color.Transparent,
        animationSpec = tween(200), label = "chipBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) Ds.accent.copy(alpha = 0.30f) else Ds.hairline,
        animationSpec = tween(200), label = "chipBorder"
    )
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(RoundedCornerShape(999.dp)).clickable { onClick() } else Modifier
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Ds.accent else Ds.textSecondary,
            maxLines = 1
        )
    }
}

/** Tiny mono pill for protocol / state tags (TCP, LISTEN, RUNNING…). */
@Composable
fun ValuePill(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
    dim: Color? = null
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = dim ?: tone.copy(alpha = 0.13f),
        modifier = modifier
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Telemetry,
            color = tone,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
    }
}

// ── Tabs ─────────────────────────────────────────────────────────────────────

data class TabSpec(val label: String, val icon: ImageVector? = null, val badge: String? = null)

/**
 * Segmented control — the app's primary tab pattern.
 * A sunken well holding raised "keys"; the selected key lifts to the surface.
 */
@Composable
fun SegmentedTabs(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = Ds.surfaceLow,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier.fillMaxWidth()
    ) {
        if (scrollable) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { i, _ -> SegKey(tabs, i, selected == i, onSelect, weightless = true) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { i, _ -> SegKey(tabs, i, selected == i, onSelect, weightless = false) }
            }
        }
    }
}

@Composable
private fun RowScope.SegKey(
    tabs: List<TabSpec>,
    index: Int,
    selected: Boolean,
    onSelect: (Int) -> Unit,
    weightless: Boolean
) {
    val tab = tabs[index]
    val bg by animateColorAsState(
        targetValue = if (selected) Ds.surface else Color.Transparent,
        animationSpec = tween(220), label = "segBg"
    )
    val shape = RoundedCornerShape(10.dp)
    Surface(
        shape = shape,
        color = bg,
        border = if (selected) BorderStroke(1.dp, Ds.hairline) else null,
        modifier = Modifier
            .then(if (weightless) Modifier else Modifier.weight(1f))
            .padding(2.dp)
            .clip(shape)
            .clickable { onSelect(index) }
    ) {
        Row(
            Modifier.padding(horizontal = if (weightless) 14.dp else 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tab.icon?.let {
                Icon(
                    imageVector = it, contentDescription = null,
                    tint = if (selected) Ds.accent else Ds.textTertiary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                tab.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Ds.textPrimary else Ds.textSecondary,
                maxLines = 1
            )
            if (tab.badge != null) {
                Spacer(Modifier.width(6.dp))
                ValuePill(tab.badge, if (selected) Ds.accent else Ds.neutral)
            }
        }
    }
}

// ── Inputs ───────────────────────────────────────────────────────────────────

@Composable
fun DTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    secret: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (focused) Ds.accent.copy(alpha = 0.55f) else Ds.hairline,
        animationSpec = tween(180), label = "tfBorder"
    )
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ds.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Ds.surfaceLow,
            border = BorderStroke(1.dp, borderColor)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                interactionSource = interaction,
                keyboardOptions = keyboardOptions,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Ds.textPrimary,
                    fontFamily = if (mono) Telemetry else Inter,
                    fontFeatureSettings = "tnum"
                ),
                cursorBrush = SolidColor(Ds.accent),
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Ds.textTertiary
                                )
                            }
                            inner()
                        }
                        if (trailing != null) {
                            Spacer(Modifier.width(8.dp))
                            trailing()
                        }
                    }
                }
            )
        }
    }
}

/** Terminal-styled code block with traffic lights and a copy affordance. */
@Composable
fun TerminalBox(
    text: String,
    modifier: Modifier = Modifier,
    title: String = "bash",
    copyLabel: String? = null,
    onCopy: (() -> Unit)? = null
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Ds.surfaceLow,
            border = BorderStroke(1.dp, Ds.hairline),
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(Ds.danger, Ds.warn, Ds.ok).forEach { c ->
                        Box(Modifier.size(5.dp).background(c.copy(alpha = 0.65f), CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(title, fontSize = 9.5.sp, fontFamily = Telemetry, color = Ds.textTertiary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    if (onCopy != null && copyLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Ds.accentDim,
                            modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onCopy)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(11.dp))
                                Text(copyLabel, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Ds.accent)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Ds.hairline))
                Text(
                    text,
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState()),
                    fontSize = 10.5.sp,
                    fontFamily = Telemetry,
                    fontWeight = FontWeight.Medium,
                    color = Ds.textPrimary.copy(alpha = 0.90f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ── Metrics & gauges ─────────────────────────────────────────────────────────

@Composable
fun CircularGauge(
    percentage: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    activeColor: Color = Ds.accent,
    trackColor: Color = Ds.track
) {
    val target = (percentage / 100f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(850, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            if (progress > 0.004f) {
                drawArc(color = activeColor, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = stroke)
                // luminous endpoint
                val angleRad = Math.toRadians((-90.0 + progress * 360.0))
                val radius = this.size.minDimension / 2f - strokeWidth.toPx() / 2f
                val p = Offset(
                    center.x + radius * cos(angleRad).toFloat(),
                    center.y + radius * sin(angleRad).toFloat()
                )
                drawCircle(color = activeColor.copy(alpha = 0.30f), radius = strokeWidth.toPx() * 0.95f, center = p)
                drawCircle(color = activeColor, radius = strokeWidth.toPx() * 0.42f, center = p)
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "${percentage.toInt()}%",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Telemetry,
                color = Ds.textPrimary
            )
            Text(
                label.uppercase(),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ds.textTertiary,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
fun ProgressMetricBar(
    title: String,
    percentage: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = Ds.violet,
    trackColor: Color = Ds.track
) {
    val target = (percentage / 100f).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Ds.textSecondary)
            Text(
                "${percentage.toInt()}%",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Telemetry,
                color = progressColor
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(progressColor, progressColor.copy(alpha = 0.62f))
                        )
                    )
            )
        }
    }
}

@Composable
fun MetricStatCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = Ds.accent,
    valueColor: Color = Ds.textPrimary
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Ds.surface,
        border = BorderStroke(1.dp, Ds.hairline),
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(13.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ds.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Icon(imageVector = icon, contentDescription = null, tint = iconTint.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(9.dp))
            Text(
                value,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Telemetry,
                color = valueColor,
                lineHeight = 19.sp
            )
        }
    }
}

// ── Status ───────────────────────────────────────────────────────────────────

@Composable
fun PulseDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val color = if (isOnline) Ds.ok else Ds.danger
    Box(modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        // breathing halo
        if (isOnline) {
            Box(
                Modifier
                    .size(size * 2.4f)
                    .background(color.copy(alpha = alpha * 0.18f), CircleShape)
            )
        }
        Box(
            Modifier
                .size(size)
                .background(color.copy(alpha = if (isOnline) 1f else 0.85f), CircleShape)
        )
        if (!isOnline) {
            Box(
                Modifier
                    .size(size + 3.dp)
                    .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
) {
    val tone = if (isOnline) Ds.ok else Ds.danger
    val dim = if (isOnline) Ds.okDim else Ds.dangerDim
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = dim,
        border = BorderStroke(1.dp, tone.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(5.dp).background(tone, CircleShape))
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tone, maxLines = 1)
        }
    }
}

// ── Headers ──────────────────────────────────────────────────────────────────

/** Micro section label — quiet, tracked, tertiary. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(13.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ds.textTertiary, letterSpacing = 0.9.sp)
    }
}

@Composable
fun SleekSectionHeader(
    title: String,
    icon: ImageVector? = null,
    badgeText: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = Ds.accent, modifier = Modifier.size(16.dp))
            }
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary)
            if (badgeText != null) {
                ValuePill(badgeText, Ds.accent)
            }
        }
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Ds.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

/** Consistent page header for secondary screens. */
@Composable
fun PageHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon = icon, tint = Ds.accent, background = Ds.accentDim, size = 38.dp, iconSize = 19.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = Ds.textTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actionLabel != null && onAction != null) {
            SoftButton(text = actionLabel, onClick = onAction, icon = Icons.Rounded.Bolt)
        }
    }
}

/** Brand header for the home screen — radar mark, wordmark, quick controls. */
@Composable
fun ModernTopBar(
    title: String,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onToggleLang: () -> Unit,
    langLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadarMark(diameter = 34.dp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, letterSpacing = (-0.3).sp)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = Ds.textTertiary)
            }
        }
        CircleIconButton(
            icon = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
            contentDescription = "Theme",
            tint = Ds.warn,
            onClick = onToggleTheme
        )
        Spacer(Modifier.width(8.dp))
        CircleIconButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = "Refresh",
            tint = Ds.textSecondary,
            onClick = onRefresh
        )
        Spacer(Modifier.width(8.dp))
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Ds.accent
            )
        }
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ds.textSecondary,
    size: Dp = 34.dp
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
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────

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
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (radar) {
            RadarMark(diameter = 84.dp, tint = Ds.accent.copy(alpha = 0.8f))
        } else if (icon != null) {
            IconBadge(icon = icon, tint = Ds.accent, background = Ds.accentDim, size = 56.dp, iconSize = 26.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                fontSize = 12.sp,
                color = Ds.textSecondary,
                lineHeight = 17.5.sp,
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
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
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

// ── Uptime heartbeat ─────────────────────────────────────────────────────────

@Composable
fun HeartbeatBar(
    statuses: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 15.dp
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
                    .background(color, RoundedCornerShape(2.5.dp))
            )
        }
    }
}
