package org.didban.monitor

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قرارداد چیدمان و حرکت (۲۰۲۶-۰۹-۲۱).
 *
 * ریشهٔ گزارش مالک («بعضی کادرها هنوز مربع است») دو چیز بود:
 *  - `Shapes` تم اسلات‌های `extraSmall`/`extraLarge` را مقدار نمی‌داد، پس
 *    `OutlinedTextField`ها و منوها روی پیش‌فرض ۴dp می‌ماندند.
 *  - کارت‌ها/دکمه‌ها شعاع ۸–۱۲dp داشتند.
 *
 * این تست‌ها مقیاس [CommandRadii] و بودجهٔ حرکت [CommandMotion] را قفل
 * می‌کنند تا گوشه‌تیز شدن دوباره برنگردد.
 */
class CommandLayoutTest {

    // ── مقیاس گوشه‌ها ───────────────────────────────────────────────────────

    @Test
    fun `no surface radius is smaller than the comfortable floor`() {
        val radii = mapOf(
            "hero" to CommandRadii.hero, "card" to CommandRadii.card, "tile" to CommandRadii.tile,
            "control" to CommandRadii.control, "field" to CommandRadii.field, "icon" to CommandRadii.icon
        )
        radii.forEach { (name, value) ->
            assertTrue("$name = $value is too square", value >= 12.dp)
        }
    }

    @Test
    fun `the scale stays ordered from hero down to icon`() {
        assertTrue(CommandRadii.hero >= CommandRadii.card)
        assertTrue(CommandRadii.card > CommandRadii.tile)
        assertTrue(CommandRadii.tile > CommandRadii.control)
        assertTrue(CommandRadii.control > CommandRadii.field)
        assertTrue(CommandRadii.field > CommandRadii.icon)
    }

    @Test
    fun `responsive breakpoints keep a phone floor and one rail threshold`() {
        assertTrue(CommandBreakpoints.phoneMin >= 320.dp)
        assertTrue(CommandBreakpoints.formStack > CommandBreakpoints.phoneMin)
        assertTrue(CommandBreakpoints.rail > CommandBreakpoints.formStack)
    }

    @Test
    fun `responsive form stacks at 320 and 400 dp but keeps the wide rail`() {
        assertTrue(320.dp < CommandBreakpoints.formStack)
        assertTrue(400.dp < CommandBreakpoints.formStack)
        assertTrue(1000.dp >= CommandBreakpoints.formStack)
    }

    @Test
    fun `elevation tokens stay ordered and intentionally bounded`() {
        assertEquals(0.dp, CommandElevation.flat)
        assertTrue(CommandElevation.raised > CommandElevation.flat)
        assertTrue(CommandElevation.dialog >= CommandElevation.raised)
        assertTrue(CommandElevation.dialog <= 8.dp)
    }

    @Test
    fun `interaction metrics keep controls usable`() {
        assertTrue(CommandMetrics.touchTarget >= 48.dp)
        assertTrue(CommandMetrics.controlMinHeight >= CommandMetrics.touchTarget)
        assertTrue(CommandMetrics.compactRowMinHeight >= CommandMetrics.touchTarget)
        assertTrue(CommandMetrics.iconSmall < CommandMetrics.iconMedium)
        assertTrue(CommandMetrics.iconMedium < CommandMetrics.iconLarge)
        assertTrue(CommandMetrics.formAuxFieldWidth >= 96.dp)
        assertTrue(CommandMetrics.formAuxFieldWidth <= 144.dp)
        assertEquals(56.dp, CommandMetrics.launcherIcon)
        assertEquals(116.dp, CommandMetrics.launcherTileMinHeight)
        assertEquals(1.dp, CommandMetrics.borderWidth)
    }

    @Test
    fun `workbench launcher changes columns at the shared responsive breakpoints`() {
        assertEquals(2, workbenchLauncherColumns(320.dp))
        assertEquals(3, workbenchLauncherColumns(CommandBreakpoints.formStack))
        assertEquals(4, workbenchLauncherColumns(CommandBreakpoints.rail))
    }

    @Test
    fun `pills and bars are fully rounded`() {
        assertTrue(CommandRadii.pill >= 100.dp)
        assertTrue(CommandRadii.bar >= 100.dp)
    }

    // ── بودجهٔ حرکت ────────────────────────────────────────────────────────

    @Test
    fun `stagger delays grow and stay capped`() {
        assertEquals(0, CommandMotion.entranceDelayMs(0))
        assertTrue(CommandMotion.entranceDelayMs(1) > CommandMotion.entranceDelayMs(0))
        assertTrue(CommandMotion.entranceDelayMs(3) > CommandMotion.entranceDelayMs(2))
        // a long list must not delay the last card forever
        assertEquals(CommandMotion.staggerMaxMs, CommandMotion.entranceDelayMs(40))
        assertEquals(CommandMotion.staggerMaxMs, CommandMotion.entranceDelayMs(999))
        assertEquals(0, CommandMotion.entranceDelayMs(-5))
    }

    @Test
    fun `entrance stays inside a short budget`() {
        (0..10).forEach { index ->
            val budget = CommandMotion.entranceBudgetMs(index)
            assertTrue("card $index budget $budget ms is too slow", budget <= 500)
            assertTrue(budget >= CommandMotion.entranceMs)
        }
    }

    @Test
    fun `meter fills and count-ups are visible but brief`() {
        listOf(CommandMotion.fillMs to "fill", CommandMotion.countUpMs to "count-up").forEach { (ms, name) ->
            assertTrue("$name = $ms ms", ms in 300..900)
        }
        assertTrue(CommandMotion.pressScale in 0.95f..1f)
        assertTrue(CommandMotion.entranceOffsetDp in 4f..20f)
    }

    @Test
    fun `reduce motion collapses every duration to zero`() {
        assertTrue(CommandMotion.scaledMs(reduceMotion = false, ms = 240) == 240)
        assertTrue(CommandMotion.scaledMs(reduceMotion = true, ms = 240) == 0)
        assertTrue(CommandMotion.scaledMs(reduceMotion = true, ms = 0) == 0)
        assertTrue(CommandMotion.scaledMs(reduceMotion = false, ms = -10) == 0)
    }

    // ── ریاضی داشبورد (توابع خالص) ──────────────────────────────────────────

    @Test
    fun `status segments always fill the bar exactly`() {
        val cases = listOf(
            listOf(3, 0, 0, 0),
            listOf(1, 1, 1, 1),
            listOf(7, 5, 3, 1),
            listOf(0, 0, 0, 2),
            listOf(10, 0, 0, 1)
        )
        cases.forEach { counts ->
            val segments = commandStatusSegments(counts)
            assertEquals(counts.size, segments.size)
            assertEquals(1f, segments.sum(), 1e-4f)
            assertTrue(segments.all { it >= 0f && it <= 1f })
        }
    }

    @Test
    fun `status segments are empty without data and never divide by zero`() {
        assertTrue(commandStatusSegments(listOf(0, 0, 0, 0)).isEmpty())
        assertTrue(commandStatusSegments(emptyList()).isEmpty())
        assertTrue(commandStatusSegments(listOf(-4, -1)).isEmpty())
    }

    @Test
    fun `status segments keep the largest share at the largest bucket`() {
        val segments = commandStatusSegments(listOf(8, 1, 1, 0))
        assertEquals(0, segments.indices.maxByOrNull { segments[it] })
    }

    @Test
    fun `health fraction clamps and reports missing data as null`() {
        assertNull(commandHealthFraction(null))
        assertEquals(0f, commandHealthFraction(0)!!, 1e-4f)
        assertEquals(0.65f, commandHealthFraction(65)!!, 1e-4f)
        assertEquals(1f, commandHealthFraction(140)!!, 1e-4f)
        assertEquals(0f, commandHealthFraction(-20)!!, 1e-4f)
    }

    @Test
    fun `percent label rounds to whole numbers inside the range`() {
        assertEquals("0%", commandPercentLabel(0f))
        assertEquals("100%", commandPercentLabel(1f))
        assertEquals("38%", commandPercentLabel(0.384f))
        assertEquals("100%", commandPercentLabel(4f))
    }
}
