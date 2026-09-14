package org.didban.monitor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 3 · item 3-A — Aerospatial Compose layer.
 *
 * Renders the pure [AeroTokens] into the existing [DidbanPalette] pipeline,
 * so every `Ds.*` accessor keeps working while v3 screens are built (3-B..3-F).
 *
 * UI-only file: cannot be JVM-compiled in this environment (no Android SDK).
 * Verified by code review against Theme.kt (DidbanPalette field order/names).
 * The token values and resolution logic live in AeroTokens.kt and ARE tested.
 */

fun AeroTokens.toDidbanPalette(): DidbanPalette = DidbanPalette(
    canvas = Color(canvas),
    surface = Color(surface),
    surfaceElevated = Color(surfaceElevated),
    surfaceLow = Color(surfaceLow),
    surfaceHighlight = Color(surfaceHighlight),
    hairline = Color(hairline),
    hairlineStrong = Color(hairlineStrong),
    hairlineSubtle = Color(hairlineSubtle),
    textPrimary = Color(textPrimary),
    textSecondary = Color(textSecondary),
    textTertiary = Color(textTertiary),
    accent = Color(accent),
    accentDeep = Color(accentDeep),
    accentGlow = Color(accentGlow),
    accentDim = Color(accentDim),
    onAccent = Color(onAccent),
    violet = Color(violet),
    violetDeep = Color(violetDeep),
    violetDim = Color(violetDim),
    ok = Color(ok),
    okDim = Color(okDim),
    warn = Color(warn),
    warnDim = Color(warnDim),
    danger = Color(danger),
    dangerDim = Color(dangerDim),
    info = Color(info),
    infoDim = Color(infoDim),
    neutral = Color(neutral),
    track = Color(track),
    glassBorder = Color(glassBorder)
)

/** v3 light theme — the product default. */
val AeroPlatinum: DidbanPalette by lazy { AeroPlatinumTokens.toDidbanPalette() }
/** v3 dark theme — optional "cockpit" identity. */
val AeroCockpit: DidbanPalette by lazy { AeroCockpitTokens.toDidbanPalette() }

/**
 * v3 theme entry point. Resolves the mode (pure logic in AeroTokens.kt) and
 * feeds the existing MaterialTheme pipeline via [DidbanTheme]'s palette override.
 */
@Composable
fun AeroTheme(
    mode: AeroThemeMode = AeroThemeMode.LIGHT,
    systemDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val resolved = remember(mode, systemDark) { resolveAeroTheme(mode, systemDark) }
    val palette = remember(resolved) { resolved.tokens.toDidbanPalette() }
    DidbanTheme(dark = resolved.isDark, palette = palette) { content() }
}

// ── Structural tokens (geometry & motion) ──────────────────────────────────

/** v3 geometry: full-bleed spatial system. */
object AeroRadii {
    val sheet: Dp = 30.dp          // context sheet top corners
    val hud: Dp = 500.dp           // HUD pill
    val band: Dp = 18.dp           // instrument band
    val table: Dp = 16.dp          // dense tables / charts
    val tile: Dp = 14.dp           // small tiles / action buttons
    val chip: Dp = 10.dp           // service chips
    val pill: Dp = 500.dp          // buttons & badges
    val grabber: Dp = 3.dp         // sheet grabber bar
}

/** v3 motion budget: snappy cockpit, never decorative. */
object AeroMotion {
    const val sheetMs: Int = 220
    const val radialMs: Int = 200
    const val fadeMs: Int = 150
    const val listMs: Int = 160
    const val arcFlowMs: Int = 1600   // map dash-flow loop
    const val skeletonMs: Int = 700   // shimmer half-cycle
}

/** Respect the system "reduce motion" accessibility setting. */
@Composable
fun useReduceMotion(): Boolean = remember {
    try {
        val ctx = LocalContext.current.applicationContext
        android.provider.Settings.Secure.getInt(
            ctx.contentResolver, "accessibility_reduce_motion", 0
        ) == 1
    } catch (_: Throwable) {
        false
    }
}

// ── Instrument band: one continuous cluster (NOT four cards) ───────────────

enum class DeltaTone { NEUTRAL, GOOD, BAD }

data class InstrumentCellData(
    val label: String,
    val value: String,
    val delta: String? = null,
    val deltaTone: DeltaTone = DeltaTone.NEUTRAL,
    val ringPercent: Float? = null, // 0f..1f — draws a mini gauge ring
    val ringColor: Color? = null
)

@Composable
fun deltaColor(tone: DeltaTone): Color = when (tone) {
    DeltaTone.NEUTRAL -> Ds.textTertiary
    DeltaTone.GOOD -> Ds.ok
    DeltaTone.BAD -> Ds.warn
}

@Composable
fun InstrumentBand(modifier: Modifier = Modifier, cells: List<InstrumentCellData>) {
    val shape = RoundedCornerShape(AeroRadii.band)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Ds.surfaceLow)
            .border(BorderStroke(1.dp, Ds.hairline), shape)
    ) {
        cells.forEachIndexed { i, cell ->
            if (i > 0) {
                Spacer(Modifier.width(1.dp).fillMaxHeight().background(Ds.hairline))
            }
            InstrumentCell(cell, Modifier.weight(1f).padding(vertical = 11.dp, horizontal = 6.dp))
        }
    }
}

@Composable
private fun InstrumentCell(cell: InstrumentCellData, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val pct = cell.ringPercent
        if (pct != null) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                RingGauge(percent = pct.coerceIn(0f, 1f), color = cell.ringColor ?: Ds.accent)
                Text(
                    text = cell.value,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ds.textPrimary,
                    style = TabularNums
                )
            }
            Spacer(Modifier.height(4.dp))
        } else {
            Text(
                text = cell.value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ds.textPrimary,
                style = TabularNums
            )
        }
        Text(text = cell.label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Ds.textSecondary)
        cell.delta?.let {
            Text(
                text = it,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = deltaColor(cell.deltaTone),
                style = TabularNums
            )
        }
    }
}

/** Mini gauge ring (44dp) — track from palette, sweep in cell color. */
@Composable
fun RingGauge(percent: Float, color: Color, trackColor: Color = Ds.track, strokeWidth: Dp = 4.5.dp) {
    val sw = with(LocalDensity.current) { strokeWidth.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val s = size.minDimension - sw
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            brush = androidx.compose.ui.graphics.SolidColor(trackColor),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
            size = Size(s, s),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawArc(
            brush = androidx.compose.ui.graphics.SolidColor(color),
            startAngle = -90f, sweepAngle = 360f * percent, useCenter = false,
            topLeft = Offset((size.width - s) / 2f, (size.height - s) / 2f),
            size = Size(s, s),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
    }
}

// ── Dense table: one surface + hairlines (NOT a card per row) ──────────────

data class DenseCell(
    val text: String,
    val mono: Boolean = false,
    val ltr: Boolean = false,
    val bold: Boolean = false,
    val color: Color? = null,
    val align: TextAlign = TextAlign.Start,
    val width: Dp? = null
)

@Composable
fun DenseTable(
    modifier: Modifier = Modifier,
    header: List<DenseCell>,
    rows: List<List<DenseCell>>
) {
    val shape = RoundedCornerShape(AeroRadii.table)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Ds.surface)
            .border(BorderStroke(1.dp, Ds.hairline), shape)
    ) {
        Row(modifier = Modifier.fillMaxWidth().background(Ds.surfaceLow).padding(horizontal = 12.dp, vertical = 7.5.dp)) {
            header.forEach { cell ->
                DenseCellText(cell, Modifier.weight(1f), headerStyle = true)
            }
        }
        rows.forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(Ds.hairline))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
                row.forEach { cell ->
                    DenseCellText(cell, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DenseCellText(cell: DenseCell, modifier: Modifier, headerStyle: Boolean = false) {
    val direction = if (cell.ltr) LayoutDirection.Ltr else LayoutDirection.Rtl
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides direction
    ) {
        Text(
            text = cell.text,
            modifier = modifier,
            fontSize = if (headerStyle) 9.sp else 11.sp,
            fontWeight = when {
                headerStyle -> FontWeight.Bold
                cell.bold -> FontWeight.Bold
                else -> FontWeight.Normal
            },
            fontFamily = if (cell.mono) Telemetry else AppFontFamily,
            color = cell.color ?: (if (headerStyle) Ds.textTertiary else Ds.textPrimary),
            textAlign = if (cell.ltr) TextAlign.End else cell.align,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = TabularNums
        )
    }
}

// ── 4-state system components (empty / skeleton / error / stale) ───────────

@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ds.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text = body, fontSize = 12.sp, color = Ds.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(AeroRadii.pill),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(text = actionLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Ds.danger)
        Spacer(Modifier.height(4.dp))
        Text(text = body, fontSize = 11.5.sp, color = Ds.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(AeroRadii.pill),
            border = BorderStroke(1.dp, Ds.danger.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ds.danger)
        ) {
            Text(text = actionLabel, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Shimmer skeleton with the exact final shape (no layout jump). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    val reduceMotion = useReduceMotion()
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            tween(AeroMotion.skeletonMs),
            RepeatMode.Reverse
        )
    )
    Box(
        modifier = modifier.background(
            Ds.surfaceHighlight.copy(alpha = if (reduceMotion) 0.5f else phase),
            RoundedCornerShape(radius)
        )
    )
}

/**
 * Honest offline state — never present stale numbers as live.
 * Caller supplies the age label (e.g. "۸ دقیقه پیش").
 */
@Composable
fun StaleBadge(ageLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AeroRadii.pill))
            .background(Ds.warnDim)
            .border(BorderStroke(1.dp, Ds.warn.copy(alpha = 0.35f)), RoundedCornerShape(AeroRadii.pill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ageLabel,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = Ds.warn,
            style = TabularNums
        )
    }
}

// ── Shared v3 list/surface helpers (used by cockpit, deck, fleet) ──────────

/** v3 surface container: one surface + hairline (no shadow stacks). */
fun Modifier.v3Surface(radius: Dp = AeroRadii.table): Modifier =
    this
        .clip(RoundedCornerShape(radius))
        .background(Ds.surface)
        .border(BorderStroke(1.dp, Ds.hairline), RoundedCornerShape(radius))

/** v3 surface for LISTS: applied to the LazyColumn itself so an unbounded
 *  list keeps one continuous surface + hairline while staying lazy. */
fun Modifier.v3ListSurface(): Modifier = v3Surface()

/** Dense list header row (v3 language; pairs of label to ltr). */
@Composable
fun v3ListHeaderRow(cells: List<Pair<String, Boolean>>) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ds.surfaceLow)
            .padding(horizontal = 12.dp, vertical = 7.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEach { (label, ltr) ->
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Ds.textTertiary,
                modifier = Modifier.weight(1f),
                textAlign = if (ltr) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start
            )
        }
    }
}

/** Hairline row divider inside a v3 list. */
@Composable
fun v3RowDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Ds.hairline))
}
