package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Liquid glass without a backdrop-blur dependency: milky cards with a
 * specular crown, diagonal sheen and inner shade float over a static orb
 * canvas, identical on every API level.
 *
 * Per the owner-approved spec (docs/design/glass-refinement.md) data surfaces
 * are OPAQUE and chrome stays near-opaque. The white frost stops are therefore
 * pre-composited over the palette canvas: the card keeps its milky gradient
 * look but never lets the orb canvas, elevation shadows or scrolled content
 * leak through. Real translucency rendered differently on every GPU (hard
 * inner rectangles / seams on physical devices), which is exactly what the
 * pre-composite removes (see CommandGlassTest).
 */
internal data class CommandGlassMaterial(
    /** Opaque card fill stops, top (specular band) to bottom. */
    val fill: List<Color>,
    /** Border stops, bright crown to soft base. */
    val rim: List<Color>
)

internal fun commandGlassMaterial(palette: CommandPalette, chrome: Boolean = false, raised: Boolean = false): CommandGlassMaterial {
    val dark = palette.canvas.luminance() < .5f
    val lift = if (chrome || raised) (if (dark) .02f else .04f) else 0f
    // Frost = white at the given alpha, flattened over the canvas once, so the
    // gradient below interpolates between solid colors (alpha stays 1f).
    fun frost(alpha: Float): Color =
        Color.White.copy(alpha = (alpha + lift).coerceIn(0f, 1f)).compositeOver(palette.canvas)
    return if (dark) CommandGlassMaterial(
        fill = listOf(frost(.38f), frost(.26f), frost(.16f)),
        rim = listOf(
            Color.White.copy(alpha = .90f),
            Color.White.copy(alpha = .35f),
            Color.White.copy(alpha = .25f)
        )
    ) else CommandGlassMaterial(
        fill = listOf(frost(.92f), frost(.88f), frost(.84f)),
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
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = CommandColors.textPrimary,
        shape = RoundedCornerShape(if (chrome) 28.dp else 24.dp),
        border = if (border) BorderStroke(
            1.dp,
            Brush.verticalGradient(0f to material.rim[0], .3f to material.rim[1], 1f to material.rim[2])
        ) else null,
        tonalElevation = 0.dp,
        shadowElevation = if (chrome) 8.dp else if (raised) 5.dp else 3.dp
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(0f to material.fill[0], .12f to material.fill[1], 1f to material.fill[2]))
                .background(
                    if (dark) Brush.linearGradient(
                        0f to Color.White.copy(alpha = .10f),
                        .45f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = .05f)
                    ) else Brush.linearGradient(
                        0f to Color.White.copy(alpha = .08f),
                        .45f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = .05f)
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (dark) .16f else .06f)
                    )
                ),
            content = content
        )
    }
}

/** Static glass-orb canvas: deep gradient base plus cached radial glows under a
 * dim veil. Brushes are built once per size; nothing animates behind data. */
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
        // Restrained edge-light: glows stay decorative in the page gaps and
        // never compete with operational text (approved spec).
        val orbs = if (dark) listOf(
            orb(Color(0xFFFF4ECD).copy(alpha = .38f), 1.02f, -.06f, w * .78f),
            orb(Color(0xFFFF9A3D).copy(alpha = .34f), -.12f, .30f, w * .72f),
            orb(Color(0xFF38E1FF).copy(alpha = .28f), 1.06f, .62f, w * .66f),
            orb(Color(0xFF7C5CFF).copy(alpha = .38f), .12f, 1.04f, w * .72f),
            orb(Color(0xFF3B82F6).copy(alpha = .25f), .55f, .46f, w * .95f)
        ) else listOf(
            orb(Color(0xFFFFC9A3).copy(alpha = .45f), 1.0f, -.08f, w * .80f),
            orb(Color(0xFFBFE3D0).copy(alpha = .40f), -.12f, .34f, w * .72f),
            orb(Color(0xFFC3D9F5).copy(alpha = .40f), 1.06f, .68f, w * .76f),
            orb(Color(0xFFE7C8F2).copy(alpha = .35f), .18f, 1.06f, w * .72f)
        )
        val veil = if (dark) Color.Black.copy(alpha = .32f) else Color.White.copy(alpha = .18f)
        onDrawBehind {
            drawRect(base)
            orbs.forEach { drawRect(it) }
            drawRect(veil)
        }
    }
}
