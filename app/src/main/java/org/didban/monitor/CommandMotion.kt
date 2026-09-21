package org.didban.monitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * بودجهٔ حرکت (۲۰۲۶-۰۹-۲۱).
 *
 * قواعد:
 *  - هر انیمیشن پایان‌پذیر است (هیچ حلقهٔ تزئینی روی داده وجود ندارد؛
 *    فقط دریفت آرام `CommandAurora` در پس‌زمینه که آن هم با
 *    «کاهش حرکت» خاموش می‌شود).
 *  - با روشن‌بودن «کاهش حرکت» سیستمی همهٔ مدت‌ها صفر می‌شوند
 *    ([scaledMs])، پس فریم‌های میانی حذف و مقدار نهایی مستقیم رسم می‌شود.
 *  - انیمیشن‌ها صرفاً تزئینی‌اند: هیچ‌کدام مالک دادهٔ نمایش‌داده‌شده نیستند و
 *    از کار افتادنشان فقط ظاهر را ساده می‌کند.
 */
object CommandMotion {
    /** ورود هر کارت: محو + جابه‌جایی کوچک. */
    const val entranceMs = 240

    /** فاصلهٔ ورود کارت‌های پشت‌سرهم. */
    const val staggerMs = 45

    /** حداکثر تأخیر پله‌ای که اجازه داریم به آخرین کارت بدهیم. */
    const val staggerMaxMs = 180

    /** پر شدن حلقه/نوار سنجه. */
    const val fillMs = 700

    /** شمارش عددی شاخص‌ها. */
    const val countUpMs = 620

    /** جابه‌جایی ورودی کارت، در dp. */
    const val entranceOffsetDp = 12f

    /** مقیاس هنگام لمس کارت. */
    const val pressScale = 0.975f

    /** تأخیر پله‌ای برای کارت شمارهٔ [index] (سقف‌دار). */
    fun entranceDelayMs(index: Int): Int =
        (index.coerceAtLeast(0) * staggerMs).coerceAtMost(staggerMaxMs)

    /** کل بودجهٔ زمانی ورود کارت شمارهٔ [index] — برای تست سقف زمان. */
    fun entranceBudgetMs(index: Int): Int = entranceMs + entranceDelayMs(index)

    /** مدت مؤثر پس از اعمال «کاهش حرکت». */
    fun scaledMs(reduceMotion: Boolean, ms: Int): Int = if (reduceMotion) 0 else ms.coerceAtLeast(0)
}

/** Spec ورود کارت [index]. */
@Composable
private fun entranceSpec(index: Int): androidx.compose.animation.core.AnimationSpec<Float> =
    tween(
        durationMillis = CommandMotion.scaledMs(useReduceMotion(), CommandMotion.entranceMs),
        delayMillis = CommandMotion.scaledMs(useReduceMotion(), CommandMotion.entranceDelayMs(index))
    )

/**
 * مودیفایر ورود پله‌ای کارت: یک‌بار در هر ورود به صفحه اجرا می‌شود
 * (وابسته به state نیست)، پس با تازه‌شدن داده دوباره پخش نمی‌شود.
 */
@Composable
fun Modifier.commandEntrance(index: Int): Modifier {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = entranceSpec(index),
        label = "entrance-$index"
    )
    val offset = (1f - progress) * CommandMotion.entranceOffsetDp
    return this.graphicsLayer {
        alpha = progress
        translationY = offset * density
    }
}

/** پیشرفت ۰..۱ برای پر شدن سنجه/نوار؛ با «کاهش حرکت» مستقیم مقدار نهایی. */
@Composable
fun rememberCommandFill(target: Float): Float {
    val clamped = target.coerceIn(0f, 1f)
    if (useReduceMotion()) return clamped
    val value by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(CommandMotion.fillMs),
        label = "fill"
    )
    return value
}

/** شمارش عددی یک شاخص از صفر تا [target]؛ با «کاهش حرکت» مستقیم مقدار نهایی. */
@Composable
fun rememberCommandCountUp(target: Float): Float {
    if (useReduceMotion()) return target
    val value by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(CommandMotion.countUpMs),
        label = "count-up"
    )
    return value
}

/** کلیک با بازخورد فشردن سبک (بدون تغییر اندازهٔ چیدمان). */
@Composable
fun Modifier.commandPressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) CommandMotion.pressScale else 1f,
        animationSpec = spring(),
        label = "press"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick
        )
}
