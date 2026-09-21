package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * purpose — the frost cards are meant to read as glass over it. */
@Composable
internal fun Modifier.commandAtmosphere(): Modifier {
    val palette = LocalCommandPalette.current
    val dark = palette.canvas.luminance() < .5f
    return drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val base = Brush.verticalGradient(
            if (dark) listOf(Color(0xFF1B1338), Color(0xFF101B40))
            else listOf(Color(0xFFF0EADD), Color(0xFFDCE3E6))
        )
        fun orb(color: Color, cx: Float, cy: Float, radius: Float): Brush =
            Brush.radialGradient(
                listOf(color, Color.Transparent),
                center = Offset(w * cx, h * cy), radius = radius
            )
        val orbs = if (dark) listOf(
            orb(Color(0xFFFF4ECD).copy(alpha = .60f), 1.02f, -.06f, w * .78f),
            orb(Color(0xFFFF9A3D).copy(alpha = .55f), -.12f, .30f, w * .72f),
            orb(Color(0xFF38E1FF).copy(alpha = .45f), 1.06f, .62f, w * .66f),
            orb(Color(0xFF7C5CFF).copy(alpha = .60f), .12f, 1.04f, w * .72f),
            orb(Color(0xFF3B82F6).copy(alpha = .40f), .55f, .46f, w * .95f)
        ) else listOf(
            orb(Color(0xFFFFC9A3).copy(alpha = .60f), 1.0f, -.08f, w * .80f),
            orb(Color(0xFFBFE3D0).copy(alpha = .55f), -.12f, .34f, w * .72f),
            orb(Color(0xFFC3D9F5).copy(alpha = .55f), 1.06f, .68f, w * .76f),
            orb(Color(0xFFE7C8F2).copy(alpha = .50f), .18f, 1.06f, w * .72f)
        )
        val veil = if (dark) Color.Black.copy(alpha = .28f) else Color.White.copy(alpha = .12f)
        onDrawBehind {
            drawRect(base)
            orbs.forEach { drawRect(it) }
            drawRect(veil)
        }
    }
}
