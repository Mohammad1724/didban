package org.didban.monitor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 3 · item 3-E — v3 first-launch onboarding.
 *
 * Shown once (Prefs.isOnboardingSeen) over the deck, on top of the home.
 * Four short pages that teach the structural change (map → cockpit →
 * quick actions → dual theme) using the same instrument language as the
 * product (no marketing imagery). Back or Skip dismisses and records.
 *
 * UI-only: verified by code review (no Android/Compose compile here).
 */

private const val ONB_PAGES = 4

@Composable
fun AeroOnboarding(
    t: Str,
    onDone: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }

    BackHandler { onDone() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ds.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            // 3-F: instant page switch under the system reduce-motion setting
            val fade = aeroTween(AeroMotion.fadeMs)
            AnimatedContent(
                targetState = page,
                label = "onboarding",
                transitionSpec = {
                    (fadeIn(fade) togetherWith fadeOut(fade))
                }
            ) { p ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    OnboardingGlyph(kind = p)
                    Spacer(Modifier.height(28.dp))
                    Text(
                        onbTitle(t, p),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ds.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        onbBody(t, p),
                        fontSize = 12.5.sp,
                        color = Ds.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        // progress dots
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(ONB_PAGES) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (i == page) 18.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i == page) Ds.accent else Ds.hairlineStrong)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, end = 34.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoftButton(text = t.onbSkip, onClick = onDone)
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = if (page == ONB_PAGES - 1) t.onbStart else t.onbNext,
                onClick = {
                    if (page == ONB_PAGES - 1) onDone() else page++
                },
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

private fun onbTitle(t: Str, p: Int): String = when (p) {
    1 -> t.onb2Title
    2 -> t.onb3Title
    3 -> t.onb4Title
    else -> t.onb1Title
}

private fun onbBody(t: Str, p: Int): String = when (p) {
    1 -> t.onb2Body
    2 -> t.onb3Body
    3 -> t.onb4Body
    else -> t.onb1Body
}

/** Minimal instrument-language glyph for each onboarding page. */
@Composable
private fun OnboardingGlyph(kind: Int) {
    // Capture palette colors before the draw closure (Ds.* is composable).
    val canvas = Ds.canvas
    val hairline = Ds.hairline
    val accent = Ds.accent
    val ok = Ds.ok
    val violet = Ds.violet
    val textTertiary = Ds.textTertiary
    val surface = Ds.surface
    val danger = Ds.danger

    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            when (kind) {
                // 0 — spatial map: dot grid, orbital ring, three nodes
                0 -> {
                    val step = w / 9f
                    var y = step / 2f
                    while (y < h) {
                        var x = step / 2f
                        while (x < w) {
                            drawCircle(color = hairline.copy(alpha = 0.55f), radius = 1.4f, center = Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                    val r = minOf(w, h) * 0.34f
                    drawCircle(
                        color = hairline,
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f))
                    )
                    // central node (accent, hollow core)
                    node(cx, cy, 9f, accent, canvas)
                    // two orbiting nodes
                    node(cx + r, cy, 6.5f, ok, canvas)
                    node(cx - r * 0.7f, cy - r * 0.7f, 6.5f, violet, canvas)
                }

                // 1 — one tap, full cockpit: node → gauge + tiles
                1 -> {
                    node(cx - w * 0.30f, cy, 8f, accent, canvas)
                    // arrow
                    val ax0 = cx - w * 0.30f + 14f
                    val ax1 = cx + w * 0.02f
                    drawLine(color = textTertiary, start = Offset(ax0, cy), end = Offset(ax1, cy), strokeWidth = 1.6f)
                    drawPath(
                        color = textTertiary,
                        path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(ax1, cy - 5f); lineTo(ax1 + 8f, cy); lineTo(ax1, cy + 5f)
                        }
                    )
                    // ring gauge
                    val gr = 26f
                    val gx = cx + w * 0.20f
                    drawCircle(color = hairline, radius = gr, center = Offset(gx, cy), style = Stroke(width = 6f))
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = Offset(gx - gr, cy - gr),
                        size = Size(gr * 2f, gr * 2f),
                        style = Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    // mini tiles
                    drawRoundRect(
                        color = surface,
                        topLeft = Offset(gx - 20f, cy + 34f),
                        size = Size(18f, 12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                        style = Stroke(width = 1.2f)
                    )
                    drawRoundRect(
                        color = surface,
                        topLeft = Offset(gx + 2f, cy + 34f),
                        size = Size(18f, 12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                        style = Stroke(width = 1.2f)
                    )
                }

                // 2 — radial quick actions: center + two satellites
                2 -> {
                    node(cx, cy + 6f, 11f, accent, canvas)
                    val sxL = cx - 34f
                    val sxR = cx + 34f
                    val sy = cy - 22f
                    // dashed spokes
                    drawLine(
                        color = hairline, start = Offset(cx, cy + 6f), end = Offset(sxL, sy), strokeWidth = 1.4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f)
                    )
                    drawLine(
                        color = hairline, start = Offset(cx, cy + 6f), end = Offset(sxR, sy), strokeWidth = 1.4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f), 0f)
                    )
                    node(sxL, sy, 7f, ok, canvas)
                    node(sxR, sy, 7f, violet, canvas)
                }

                // 3 — dual themes: light platinum + dark cockpit swatches
                else -> {
                    val sw = w * 0.36f
                    val sh = h * 0.42f
                    val l = Offset(cx - sw / 2f - 6f, cy - sh / 2f)
                    val r = Offset(cx + sw / 2f + 6f, cy - sh / 2f)
                    // light swatch
                    drawRoundRect(
                        color = surface,
                        topLeft = l, size = Size(sw, sh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = hairline, topLeft = l, size = Size(sw, sh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                        style = Stroke(width = 1.4f)
                    )
                    drawCircle(color = accent, radius = 4f, center = Offset(l.x + 14f, l.y + 14f))
                    // dark swatch
                    drawRoundRect(
                        color = Color(AeroCockpitTokens.canvas), // 3-F: the real cockpit canvas token, not a guessed hex
                        topLeft = r, size = Size(sw, sh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = hairline.copy(alpha = 0.6f), topLeft = r, size = Size(sw, sh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                        style = Stroke(width = 1.4f)
                    )
                    drawCircle(color = danger, radius = 4f, center = Offset(r.x + 14f, r.y + 14f))
                    // small gauge bars
                    drawRoundRect(
                        color = hairline, topLeft = Offset(l.x + 10f, l.y + sh - 16f),
                        size = Size(sw - 20f, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                    drawRoundRect(
                        color = accent.copy(alpha = 0.7f), topLeft = Offset(r.x + 10f, r.y + sh - 16f),
                        size = Size((sw - 20f) * 0.6f, 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
                    )
                }
            }
        }
    }
}

/** Hollow instrument node: filled disc, canvas punch, core dot. */
private fun DrawScope.node(x: Float, y: Float, r: Float, color: Color, punch: Color) {
    drawCircle(color = color, radius = r, center = Offset(x, y))
    drawCircle(color = punch, radius = (r - 3f).coerceAtLeast(0f), center = Offset(x, y))
    drawCircle(color = color, radius = (r * 0.4f).coerceAtLeast(1.5f), center = Offset(x, y))
}
