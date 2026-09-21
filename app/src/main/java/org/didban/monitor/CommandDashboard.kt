package org.didban.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * اجزای داشبورد زمردی (۲۰۲۶-۰۹-۲۱).
 *
 * این‌ها «سیلوئت» دیدبان‌اند، نه کپی چیدمان زمرد: هیرو، نوار شاخص، نوار
 * مصرف، و ردیف چیپ سرورها — همه با دادهٔ واقعی، بدون تصویر یا موج تزئینی.
 * منطق خالص ([commandStatusSegments]، [commandHealthFraction]) جدا و
 * JVM-تست‌شدنی است.
 */

// ── منطق خالص ──────────────────────────────────────────────────────────────

/**
 * سهم هر وضعیت از کل ناوگان، برای نوار وضعیت هیرو. مجموع همیشه ۱ می‌شود
 * (یا لیست خالی وقتی داده‌ای نیست) و باقی‌ماندهٔ گردکردن به بزرگ‌ترین سهم
 * داده می‌شود تا نوار دقیقاً پر شود.
 */
fun commandStatusSegments(counts: List<Int>): List<Float> {
    val safe = counts.map { it.coerceAtLeast(0) }
    val total = safe.sum()
    if (total <= 0) return emptyList()
    val fractions = safe.map { it.toFloat() / total.toFloat() }.toMutableList()
    val drift = 1f - fractions.sum()
    val biggest = fractions.indices.maxByOrNull { fractions[it] }
    if (biggest != null) fractions[biggest] = (fractions[biggest] + drift).coerceIn(0f, 1f)
    return fractions
}

/** کسر سلامت (۰..۱) از امتیاز ۰..۱۰۰؛ `null` یعنی داده‌ای نداریم. */
fun commandHealthFraction(score: Int?): Float? = score?.let { (it.coerceIn(0, 100)) / 100f }

/** برچسب درصدی برای نوار مصرف (بدون اعشار اضافه). */
fun commandPercentLabel(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%"

// ── کارت هیرو ──────────────────────────────────────────────────────────────

@Composable
fun CommandHeroCard(
    eyebrow: String,
    title: String,
    body: String?,
    score: Int?,
    gaugeLabel: String,
    statusLabel: String,
    statusTone: CommandHealthTone,
    segments: List<Float>,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(CommandRadii.hero)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(CommandColors.heroTop, CommandColors.heroBottom)
                )
            )
            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    eyebrow.uppercase(),
                    color = CommandHeroInk.eyebrow,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(fontFamily = Telemetry)
                )
                Spacer(Modifier.height(CommandSpacing.xxs))
                Text(
                    title,
                    color = CommandHeroInk.strong,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (body != null) {
                    Spacer(Modifier.height(CommandSpacing.xs))
                    Text(
                        body,
                        color = CommandHeroInk.body,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badgeIcon != null) {
                Spacer(Modifier.width(CommandSpacing.sm))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(CommandRadii.icon))
                        .background(CommandHeroInk.badgeFill)
                        .border(1.dp, CommandHeroInk.badgeRim, RoundedCornerShape(CommandRadii.icon)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(badgeIcon, contentDescription = null, tint = CommandHeroInk.strong, modifier = Modifier.size(21.dp))
                }
            }
        }

        Spacer(Modifier.height(CommandSpacing.md))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CommandHeroGauge(score = score, label = gaugeLabel)
            Spacer(Modifier.width(CommandSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CommandSpacing.xs)) {
                CommandHeroPill(statusLabel, statusTone)
                if (actionLabel != null && onAction != null) {
                    CommandHeroButton(actionLabel, onAction)
                }
            }
        }

        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(CommandSpacing.md))
            CommandStatusSegments(segments)
        }

        if (footer != null) {
            Spacer(Modifier.height(CommandSpacing.sm))
            footer()
        }
    }
}

/** رنگ‌های ثابت روی گرادیان زمردی (مستقل از تم، چون پس‌زمینه همیشه زمرد است). */
internal object CommandHeroInk {
    val strong = Color(0xFFF2FFF8)
    val body = Color(0xCCE3F6EC)
    val muted = Color(0xB3CBEBDA)
    val eyebrow = Color(0xFFBEEBD4)
    val track = Color(0x33FFFFFF)
    val ring = Color(0xFFEBD08A)
    val ringTip = Color(0xFFFFFFFF)
    val badgeFill = Color(0x26FFFFFF)
    val badgeRim = Color(0x40FFFFFF)
    val barTrack = Color(0x2EFFFFFF)
}

@Composable
private fun CommandHeroPill(label: String, tone: CommandHealthTone) {
    Row(
        Modifier
            .clip(RoundedCornerShape(CommandRadii.pill))
            .background(CommandHeroInk.badgeFill)
            .border(1.dp, CommandHeroInk.badgeRim, RoundedCornerShape(CommandRadii.pill))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when (tone) {
                        CommandHealthTone.HEALTHY -> Color(0xFF8FF0C4)
                        CommandHealthTone.ATTENTION -> Color(0xFFFFD98A)
                        CommandHealthTone.OFFLINE -> Color(0xFFFFB0AB)
                        CommandHealthTone.INFO -> Color(0xFFA9E8FF)
                        CommandHealthTone.UNKNOWN -> Color(0xFFD5E3DB)
                    }
                )
        )
        Text(
            label,
            color = CommandHeroInk.strong,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CommandHeroButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(CommandRadii.pill))
            .background(
                Brush.verticalGradient(
                    listOf(CommandColors.gold.copy(alpha = .98f), CommandColors.gold)
                )
            )
            .commandPressable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        Text(
            label,
            color = CommandColors.onGold,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text("→", color = CommandColors.onGold, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
    }
}

/** سنجهٔ حلقه‌ای روی گرادیان زمردی: طلایی، با پر شدن انیمیشنی. */
@Composable
fun CommandHeroGauge(score: Int?, label: String, modifier: Modifier = Modifier, size: Dp = 118.dp) {
    val fraction = commandHealthFraction(score) ?: 0f
    val animated = rememberCommandFill(fraction)
    val shown = rememberCommandCountUp(score?.toFloat() ?: 0f).roundToInt()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f + 1f
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = CommandHeroInk.track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (score != null) {
                drawArc(
                    color = CommandHeroInk.ring,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                // سرِ کمان: نقطهٔ روشن، مثل اعلان وضعیت
                val angle = Math.toRadians((-90f + 360f * animated).toDouble())
                val radius = (arcSize.width / 2f)
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val tip = Offset(
                    (center.x + (radius * kotlin.math.cos(angle)).toFloat()),
                    (center.y + (radius * kotlin.math.sin(angle)).toFloat())
                )
                drawCircle(CommandHeroInk.ringTip, radius = 3.4.dp.toPx(), center = tip)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (score == null) "—" else "$shown%",
                color = CommandHeroInk.strong,
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall.copy(
                    fontFamily = Telemetry,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
            )
            Text(
                label,
                color = CommandHeroInk.body,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** نوار وضعیت ناوگان: هر بخش یک وضعیت، با سهم واقعی. */
@Composable
private fun CommandStatusSegments(fractions: List<Float>) {
    val colors = listOf(
        Color(0xFF8FF0C4), // healthy
        Color(0xFFFFD98A), // attention
        Color(0xFFFFB0AB), // offline
        Color(0xFFD5E3DB)  // unknown
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(CommandRadii.bar))
            .background(CommandHeroInk.barTrack)
    ) {
        fractions.forEachIndexed { index, fraction ->
            if (fraction <= 0f) return@forEachIndexed
            Spacer(
                Modifier
                    .weight(fraction.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .background(colors[index % colors.size])
            )
            if (index != fractions.lastIndex) Spacer(Modifier.width(2.dp))
        }
    }
}

// ── نوار شاخص (یک سطح پیوسته، نه چند کارت) ─────────────────────────────────

data class CommandStatCell(
    val label: String,
    val value: String,
    val unit: String? = null,
    val tone: CommandHealthTone = CommandHealthTone.INFO,
    val animatedValue: Float? = null
)

@Composable
fun CommandStatStrip(cells: List<CommandStatCell>, modifier: Modifier = Modifier) {
    if (cells.isEmpty()) return
    val shape = RoundedCornerShape(CommandRadii.tile)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CommandColors.surface)
            .border(1.dp, CommandColors.border, shape)
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .align(Alignment.CenterVertically)
                        .background(CommandColors.border)
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = CommandSpacing.sm, horizontal = CommandSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    cell.label,
                    color = CommandColors.textTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    val text = if (cell.animatedValue != null) {
                        // شمارش نرم برای شاخص‌هایی که مقدار عددی زنده دارند.
                        val animated = rememberCommandCountUp(cell.animatedValue)
                        formatStatValue(animated)
                    } else {
                        cell.value
                    }
                    Text(
                        text,
                        color = statToneColor(cell.tone),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                            fontFamily = Telemetry,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (cell.unit != null) {
                        Text(
                            cell.unit,
                            color = CommandColors.textTertiary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatStatValue(value: Float): String =
    if (value >= 100f) value.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

@Composable
private fun statToneColor(tone: CommandHealthTone): Color = when (tone) {
    CommandHealthTone.HEALTHY -> CommandColors.success
    CommandHealthTone.ATTENTION -> CommandColors.warning
    CommandHealthTone.OFFLINE -> CommandColors.danger
    CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
    CommandHealthTone.INFO -> CommandColors.accent
}

// ── نوار مصرف: یک سطح، متن روی نوار ────────────────────────────────────────

@Composable
fun CommandConsumeBar(
    title: String,
    valueLabel: String,
    fraction: Float?,
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(CommandRadii.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CommandColors.surface)
            .border(1.dp, CommandColors.border, shape)
            .padding(CommandSpacing.md)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f),
                color = CommandColors.textPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(CommandRadii.icon))
                    .background(CommandColors.accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("◧", color = CommandColors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(CommandSpacing.xxs))
            Text(
                caption,
                color = CommandColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(CommandSpacing.sm))
        val animated = rememberCommandFill(fraction ?: 0f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(CommandRadii.tile))
                .background(CommandColors.track)
        ) {
            if (fraction != null && fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(animated.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(CommandColors.success, CommandColors.accent)
                            ),
                            RoundedCornerShape(CommandRadii.tile)
                        )
                )
            }
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    valueLabel,
                    color = if (fraction != null && fraction > 0.35f) Color.White else CommandColors.textPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                        fontFamily = Telemetry,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    caption ?: title,
                    color = if (fraction != null && fraction > 0.35f) Color(0xE6FFFFFF) else CommandColors.textTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── ردیف چیپ سرورها (سیلوئت خود دیدبان) ────────────────────────────────────

data class CommandChipItem(
    val id: Long,
    val name: String,
    val status: String,
    val tone: CommandHealthTone
)

@Composable
fun CommandChipRow(
    items: List<CommandChipItem>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
    ) {
        items.forEach { item ->
            val color = when (item.tone) {
                CommandHealthTone.HEALTHY -> CommandColors.success
                CommandHealthTone.ATTENTION -> CommandColors.warning
                CommandHealthTone.OFFLINE -> CommandColors.danger
                CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
                CommandHealthTone.INFO -> CommandColors.info
            }
            Row(
                Modifier
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(CommandRadii.pill))
                    .background(CommandColors.surface)
                    .border(1.dp, CommandColors.border, RoundedCornerShape(CommandRadii.pill))
                    .commandPressable { onSelect(item.id) }
                    .padding(horizontal = CommandSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CommandSpacing.xs)
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Column {
                    Text(
                        item.name,
                        color = CommandColors.textPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.status,
                        color = CommandColors.textTertiary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** بلوک رخداد/اعلان با لهجهٔ طلایی. */
@Composable
fun CommandNoticeRow(
    title: String,
    body: String,
    tone: CommandHealthTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    val accent = when (tone) {
        CommandHealthTone.ATTENTION -> CommandColors.warning
        CommandHealthTone.OFFLINE -> CommandColors.danger
        CommandHealthTone.HEALTHY -> CommandColors.success
        CommandHealthTone.UNKNOWN -> CommandColors.textTertiary
        CommandHealthTone.INFO -> CommandColors.info
    }
    val shape = RoundedCornerShape(CommandRadii.tile)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(CommandColors.surface)
            .border(1.dp, CommandColors.border, shape)
            .commandPressable(onClick = onClick)
            .padding(horizontal = CommandSpacing.md, vertical = CommandSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CommandSpacing.sm)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(CommandRadii.icon))
                .background(accent.copy(alpha = .14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "!",
                color = accent,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = CommandColors.textPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                color = CommandColors.textSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            trailing ?: "›",
            color = CommandColors.textTertiary,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )
    }
}
