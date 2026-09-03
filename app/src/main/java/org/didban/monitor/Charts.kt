package org.didban.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A tiny sparkline chart drawn with the Compose Canvas — zero chart libraries.
 * Values are auto-scaled to the max; the last point gets a dot.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fillAlpha: Float = 0.12f
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val w = size.width
        val h = size.height
        val step = w / (values.size - 1)

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / maxV) * h * 0.92f - h * 0.04f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        val fill = Path()
        fill.addPath(line)
        fill.lineTo(w, h)
        fill.lineTo(0f, h)
        fill.close()
        drawPath(fill, color.copy(alpha = fillAlpha))

        drawPath(line, color, style = Stroke(width = 4f, cap = StrokeCap.Round))

        val lastV = values.last()
        drawCircle(
            color = color,
            radius = 8f,
            center = Offset(w, h - (lastV / maxV) * h * 0.92f - h * 0.04f)
        )
    }
}
