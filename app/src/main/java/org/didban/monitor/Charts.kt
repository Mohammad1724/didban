package org.didban.monitor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Nightwatch sparkline — a smooth telemetry curve with a soft gradient fill
 * beneath it and a luminous endpoint. Zero chart libraries.
 */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Ds.accent,
    fillAlpha: Float = 0.16f
) {
    val cleanValues = values.filter { !it.isNaN() && !it.isInfinite() }
    val reveal by animateFloatAsState(
        targetValue = if (cleanValues.size >= 2) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "sparkReveal"
    )
    Canvas(modifier = modifier) {
        if (cleanValues.size < 2 || size.width <= 0f || size.height <= 0f) {
            // No telemetry yet — a quiet dashed baseline
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))
            )
            return@Canvas
        }
        val maxV = (cleanValues.maxOrNull() ?: 1f).coerceAtLeast(0.001f)
        val w = size.width
        val h = size.height
        val pad = h * 0.10f
        val step = w / (cleanValues.size - 1).coerceAtLeast(1)

        fun yFor(v: Float): Float = (h - pad - (v / maxV) * (h - pad * 2f)).coerceIn(0f, h)

        // Smooth path via monotone-ish cubic segments
        val line = Path()
        val points = cleanValues.mapIndexed { i, v -> Offset(i * step, yFor(v)) }
        line.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val dx = (cur.x - prev.x) * 0.42f
            line.cubicTo(prev.x + dx, prev.y, cur.x - dx, cur.y, cur.x, cur.y)
        }

        // Reveal clip (left → right)
        clipRect(right = w * reveal) {
            // Gradient fill under the curve
            val fill = Path().apply {
                addPath(line)
                lineTo(w * reveal, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h
                )
            )
            // The curve itself
            drawPath(
                line,
                color,
                style = Stroke(width = 4.2f, cap = StrokeCap.Round)
            )
        }

        // Luminous endpoint
        if (reveal >= 0.999f) {
            val last = points.last()
            drawCircle(color = color.copy(alpha = 0.28f), radius = 11f, center = last)
            drawCircle(color = color, radius = 4.6f, center = last)
        }
    }
}
