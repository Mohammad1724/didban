package org.didban.monitor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═════════════════════════════════════════════════════════════════════════════
// 1. MODERN SOFT-CARD CONTAINER (Matching Modern SaaS Dashboard Design)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

// Backward-compatible alias
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModernCard(modifier = modifier, content = content)
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. NOTICE BANNER (Purple/Lavender Lightbulb Banner matching Screenshot)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: String = "💡",
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
                lineHeight = 18.sp
            )
            if (onDismiss != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onDismiss() }
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. FEATURE GUIDE & CLARIFICATION CARD (Detailed Explanations for User)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun FeatureGuideCard(
    title: String,
    description: String,
    bullets: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ℹ️", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                description,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
            if (bullets.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                bullets.forEach { b ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text("• ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(b, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. CIRCULAR GAUGE RING (Matching the 0% Gauge in the Screenshot)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CircularGauge(
    percentage: Float, // 0..100
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 105.dp,
    strokeWidth: Dp = 9.dp,
    activeColor: Color = Color(0xFF10B981),
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
) {
    val progress = (percentage / 100f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            // Background track circle
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            // Active progress arc
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = stroke
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "${percentage.toInt()}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 5. PRIMARY ACTION BUTTON (Vibrant Emerald Teal matching "اتصال مستقیم")
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFF0D9488) // Vibrant Emerald Teal
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 6. SECONDARY ACTION CARD BUTTON (Matching the 2x2 Grid in Screenshot)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun SecondaryActionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(48.dp)
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
                Text(icon, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 7. LINEAR PROGRESS BAR WITH LABEL & PERCENTAGE
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ProgressMetricBar(
    title: String,
    percentage: Float, // 0..100
    modifier: Modifier = Modifier,
    progressColor: Color = Color(0xFF6366F1), // Soft Violet/Indigo
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${percentage.toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((percentage / 100f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(progressColor)
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 8. METRIC STAT CARD (2x2 Grid Cards with Icon + Value + Subtitle)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun MetricStatCard(
    icon: String,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(icon, fontSize = 18.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                lineHeight = 18.sp
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 9. STATUS PILL BADGE (• فعال / • آنلاین / • قطع)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    isOnline: Boolean = true
) {
    val bgColor = if (isOnline) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
    val textColor = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(textColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 10. MODERN TOP HEADER BAR (Logo + Refresh + Theme Toggle + Language)
// ═════════════════════════════════════════════════════════════════════════════

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
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Theme & Refresh Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onToggleTheme() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(if (isDarkMode) "🌙" else "☀️", fontSize = 16.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onRefresh() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text("🔄", fontSize = 15.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp,
                modifier = Modifier.clickable { onToggleLang() }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(langLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Brand Title
        Column(horizontalAlignment = Alignment.End) {
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
