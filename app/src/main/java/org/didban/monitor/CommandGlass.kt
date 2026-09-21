package org.didban.monitor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Liquid glass, owner direction 2026-09-20: translucent frosted cards over a
 * vivid static orb canvas, like the supplied references — orbs stay visible
 * through every card, a bright crown rim and inner bottom shade give depth.
 *
 * Device-safe rendering: the whole frost (fill + diagonal sheen + inner shade)
 * is drawn in ONE cached pass as round-rect fills inside an explicit clip, and
 * no elevation shadow is attached to translucent surfaces. The previous
 * artifact on physical GPUs (hard inner rectangles / seams) came from stacked
 * translucent Compose backgrounds plus `Surface(shadowElevation)` leaking the
 * shadow through alpha layers; a single draw pass with `drawRoundRect` and no
 * RenderNode shadow is the least renderer-dependent path (same look on every
 * API level, no RenderEffect / backdrop blur).
 */
internal data class CommandGlassMaterial(
    /** Translucent white frost stops, crown to base. */
    val fill: List<Color>,
    /** Border stops, bright crown to soft base. */
    val rim: List<Color>
)

internal fun commandGlassMaterial(palette: CommandPalette, chrome: Boolean = false, raised: Boolean = false): CommandGlassMaterial {
    val dark = palette.canvas.luminance() < .5f
    val lift = if (chrome || raised) (if (dark) .02f else .04f) else 0f
    return if (dark) CommandGlassMaterial(
        fill = listOf(
            Color.White.copy(alpha = .38f + lift),
            Color.White.copy(alpha = .26f + lift),
            Color.White.copy(alpha = .16f + lift)
        ),
        rim = listOf(
            Color.White.copy(alpha = .90f),
            Color.White.copy(alpha = .35f),
            Color.White.copy(alpha = .25f)
        )
    ) else CommandGlassMaterial(
        fill = listOf(
            Color.White.copy(alpha = .84f + lift),
            Color.White.copy(alpha = .66f + lift),
            Color.White.copy(alpha = .50f + lift)
        ),
        rim = listOf(
            Color.White.copy(alpha = 1f),
            Color.White.copy(alpha = .70f),
            Color.White.copy(alpha = .50f)
        )
    )
}

/**
 * Pure motion math for the ambient emerald canvas (JVM-testable, no Compose
 * state): one period is [periodMs], offsets stay inside ±1 unit and the loop
 * closes exactly at the period so there is no visible jump.
 */
internal object CommandAurora {
    const val periodMs = 30_000

    /** Longest visual travel of the orb layer, in dp. */
    const val driftDp = 9f

    /** Offset in units of [driftDp]; `harmonic` must be a whole number so
     *  every axis returns to its start at phase 1f (loop closure). */
    fun driftOffset(phase: Float, harmonic: Float): Float {
        val a = (phase * 2.0 * Math.PI * harmonic).toFloat()
        return kotlin.math.sin(a) * (0.55f + 0.45f * kotlin.math.cos(a * 0.5f))
    }
}

/** Orb tints for the emerald atmosphere, one set per theme. */
internal object CommandEmeraldAurora {
    val darkAccent = Color(0xFF2FCB93)
    val darkGold = Color(0xFFD6AE4A)
    val darkJade = Color(0xFF1E9C8A)
    val lightAccent = Color(0xFF3FA97F)
    val lightGold = Color(0xFFE8C46A)
    val lightJade = Color(0xFF63BFA7)
}

/**
 * Drift phase as an observable value. Zero (a still canvas) when the system
 * "reduce motion" setting is on, so the ambient layer is never a forced
 * animation.
 */
@Composable
internal fun rememberCommandAuroraDrift(): State<Float> {
    val reduceMotion = useReduceMotion()
    if (reduceMotion) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "emerald-atmosphere")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CommandAurora.periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emerald-drift"
    )
}

@Composable
internal fun CommandLayerSurface(
    modifier: Modifier = Modifier,
    chrome: Boolean = false,
    raised: Boolean = false,
    border: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalCommandPalette.current
    val dark = palette.canvas.luminance() < .5f
    val material = commandGlassMaterial(palette, chrome, raised)
    val radius = if (chrome) 28.dp else 24.dp
    val shape = RoundedCornerShape(radius)
    CompositionLocalProvider(LocalContentColor provides CommandColors.textPrimary) {
        Column(
            modifier
                .clip(shape)
                .drawWithCache {
                    val r = CornerRadius(radius.toPx(), radius.toPx())
                    val frost = Brush.verticalGradient(
                        0f to material.fill[0], .12f to material.fill[1], 1f to material.fill[2]
                    )
                    val sheen = Brush.linearGradient(
                        0f to Color.White.copy(alpha = if (dark) .10f else .08f),
                        .45f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = .05f)
                    )
                    val shade = Brush.verticalGradient(
                        0f to Color.Transparent,
                        .62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) .16f else .06f)
                    )
                    onDrawBehind {
                        // One pass, rounded fills only: no stacked translucent
                        // layers, no square corners, no elevation shadow.
                        drawRoundRect(frost, cornerRadius = r)
                        drawRoundRect(sheen, cornerRadius = r)
                        drawRoundRect(shade, cornerRadius = r)
                    }
                }
                .then(
                    if (border) Modifier.border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                0f to material.rim[0], .3f to material.rim[1], 1f to material.rim[2]
                            )
                        ),
                        shape
                    ) else Modifier
                ),
            content = content
        )
    }
}

/** Static glass-orb canvas: deep gradient base plus cached radial glows.
 * Brushes are built once per size; nothing animates behind data. Vivid on
 * purpose — the frost cards are meant to read as glass over it.
 *
 * 2026-09-21 (emerald identity): the orbs are re-tinted to the emerald/gold
 * family and drift very slowly. The drift is a single float animation that
 * translates the orb layer by a few dp — the base and every orb brush stay
 * cached (`drawWithCache`), only the draw phase re-runs, and the whole effect
 * collapses to a still canvas when "reduce motion" is enabled. No surface
 * translucency, elevation or blur is involved, so the device-safety rules in
 * glass-refinement.md are untouched. */
@Composable
internal fun Modifier.commandAtmosphere(): Modifier {
    val palette = LocalCommandPalette.current
    val dark = palette.canvas.luminance() < .5f
    val driftState = rememberCommandAuroraDrift()
    return drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val base = Brush.verticalGradient(
            if (dark) listOf(Color(0xFF07211A), Color(0xFF04120F))
            else listOf(Color(0xFFF1F6F1), Color(0xFFDCE9E1))
        )
        fun orb(color: Color, cx: Float, cy: Float, radius: Float): Brush =
            Brush.radialGradient(
                listOf(color, Color.Transparent),
                center = Offset(w * cx, h * cy), radius = radius
            )
        val orbs = if (dark) listOf(
            orb(CommandEmeraldAurora.darkAccent.copy(alpha = .62f), 1.02f, -.06f, w * .78f),
            orb(CommandEmeraldAurora.darkGold.copy(alpha = .46f), -.12f, .30f, w * .72f),
            orb(CommandEmeraldAurora.darkJade.copy(alpha = .50f), 1.06f, .62f, w * .66f),
            orb(CommandEmeraldAurora.darkGold.copy(alpha = .34f), .12f, 1.04f, w * .72f),
            orb(CommandEmeraldAurora.darkAccent.copy(alpha = .30f), .55f, .46f, w * .95f)
        ) else listOf(
            orb(CommandEmeraldAurora.lightGold.copy(alpha = .46f), 1.0f, -.08f, w * .80f),
            orb(CommandEmeraldAurora.lightAccent.copy(alpha = .40f), -.12f, .34f, w * .72f),
            orb(CommandEmeraldAurora.lightJade.copy(alpha = .44f), 1.06f, .68f, w * .76f),
            orb(CommandEmeraldAurora.lightGold.copy(alpha = .32f), .18f, 1.06f, w * .72f)
        )
        val veil = if (dark) Color.Black.copy(alpha = .26f) else Color.White.copy(alpha = .10f)
        val driftRange = CommandAurora.driftDp * density
        onDrawBehind {
            drawRect(base)
            // Read the drift here (not in the cache block) so only the draw
            // phase invalidates and every brush above stays cached.
            val phase = driftState.value
            withTransform({
                // DrawTransform.translate() names its axes left/top.
                translate(
                    left = CommandAurora.driftOffset(phase, 1f) * driftRange,
                    top = CommandAurora.driftOffset(phase, 2f) * driftRange * .7f
                )
            }) {
                orbs.forEach { drawRect(it) }
            }
            drawRect(veil)
        }
    }
}


