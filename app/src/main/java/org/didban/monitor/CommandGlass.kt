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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Frosted appearance without backdrop capture, live blur, animation or an API-31 dependency.
 * Content cards remain fully opaque. Only navigation/header chrome transmits a little canvas.
 */
internal data class CommandGlassMaterial(
    val top: Color, val bottom: Color, val rimTop: Color, val rimBottom: Color
)

internal fun commandGlassMaterial(palette: CommandPalette, chrome: Boolean = false, raised: Boolean = false): CommandGlassMaterial {
    val dark = palette.canvas.luminance() < .5f
    return CommandGlassMaterial(
        top = (if (raised || chrome) palette.surfaceRaised else lerp(palette.surface, palette.surfaceRaised, .45f)).copy(alpha = if (chrome) .97f else 1f),
        bottom = palette.surface.copy(alpha = if (chrome) .96f else 1f),
        rimTop = if (dark) Color(0xFF526571) else Color(0xFFFFFFFF),
        rimBottom = palette.border
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
    val material = commandGlassMaterial(LocalCommandPalette.current, chrome, raised)
    Surface(
        modifier = modifier,
        color = Color.Transparent,
        contentColor = CommandColors.textPrimary,
        shape = RoundedCornerShape(if (chrome) 24.dp else 20.dp),
        border = if (border) BorderStroke(1.dp, Brush.verticalGradient(listOf(material.rimTop, material.rimBottom))) else null,
        tonalElevation = 0.dp,
        shadowElevation = if (chrome) 3.dp else if (raised) 2.dp else 1.dp
    ) {
        Column(Modifier.background(Brush.verticalGradient(listOf(material.top, material.bottom))), content = content)
    }
}

/** Static edge lighting; cached brushes, no decorative frame loop behind monitoring data. */
@Composable
internal fun Modifier.commandAtmosphere(): Modifier {
    val palette = LocalCommandPalette.current
    return drawWithCache {
        val radius = size.width.coerceAtLeast(1f) * .95f
        val cool = Brush.radialGradient(
            listOf(palette.accent.copy(alpha = .085f), Color.Transparent),
            center = Offset(size.width * .95f, 0f), radius = radius)
        val soft = Brush.radialGradient(
            listOf(palette.info.copy(alpha = .035f), Color.Transparent),
            center = Offset(0f, size.height * .65f), radius = radius)
        onDrawBehind {
            drawRect(palette.canvas)
            drawRect(cool)
            drawRect(soft)
        }
    }
}
