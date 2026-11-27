package iad1tya.echo.music.stats

import iad1tya.echo.music.constants.statToPeriod
import iad1tya.echo.music.ui.screens.OptionStats
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Registry row 909 — the week, month and year chips of Estadísticas start at local midnight (the stored timestamp is the
 * local wall-clock time read as UTC), not at «the same time of day as now».
 */
class StatsPeriodBoundaryTest {
    private fun stored(t: LocalDateTime) = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = LocalDateTime.of(2026, 3, 1, 15, 42, 7)

    @Test
    fun `this month starts on the 1st at midnight, so the morning of the 1st is not last month`() {
        assertEquals(stored(LocalDateTime.of(2026, 3, 1, 0, 0)), statToPeriod(OptionStats.MONTHS, 0, now))
        assertEquals(stored(LocalDateTime.of(2026, 2, 1, 0, 0)), statToPeriod(OptionStats.MONTHS, 1, now))
        // Across a year boundary.
        assertEquals(stored(LocalDateTime.of(2025, 12, 1, 0, 0)), statToPeriod(OptionStats.MONTHS, 3, now))
        val morningOfThe1st = stored(LocalDateTime.of(2026, 3, 1, 8, 0))
        assertEquals(true, morningOfThe1st > statToPeriod(OptionStats.MONTHS, 0, now))
    }

    @Test
    fun `a year starts on January 1st at midnight`() {
        assertEquals(stored(LocalDateTime.of(2026, 1, 1, 0, 0)), statToPeriod(OptionStats.YEARS, 0, now))
        assertEquals(stored(LocalDateTime.of(2025, 1, 1, 0, 0)), statToPeriod(OptionStats.YEARS, 1, now))
    }

    @Test
    fun `week chips are the whole days their labels name, contiguous`() {
        // Chip 0 is «today» (label «1 - 1 mar»): from midnight.
        assertEquals(stored(LocalDateTime.of(2026, 3, 1, 0, 0)), statToPeriod(OptionStats.WEEKS, 0, now))
        // Chip 1 (label «22 - 28 feb») runs from 22 Feb 00:00 to where chip 0 starts.
        assertEquals(stored(LocalDateTime.of(2026, 2, 22, 0, 0)), statToPeriod(OptionStats.WEEKS, 1, now))
        assertEquals(stored(LocalDateTime.of(2026, 2, 15, 0, 0)), statToPeriod(OptionStats.WEEKS, 2, now))
    }

    @Test
    fun `the continuous ranges stay rolling windows ending now`() {
        // StatPeriod reads the clock itself; only check it is not snapped to midnight of the injected day.
        val all = statToPeriod(OptionStats.CONTINUOUS, 5, now)
        assertEquals(0L, all)
    }
}
