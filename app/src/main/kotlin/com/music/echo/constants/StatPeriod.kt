

package iad1tya.echo.music.constants

import iad1tya.echo.music.ui.screens.OptionStats
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class StatPeriod {
    WEEK_1,
    MONTH_1,
    MONTH_3,
    MONTH_6,
    YEAR_1,
    ALL,
    ;

    fun toTimeMillis(): Long =
        when (this) {
            WEEK_1 ->
                LocalDateTime
                    .now()
                    .minusWeeks(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_1 ->
                LocalDateTime
                    .now()
                    .minusMonths(1)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_3 ->
                LocalDateTime
                    .now()
                    .minusMonths(3)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            MONTH_6 ->
                LocalDateTime
                    .now()
                    .minusMonths(6)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            YEAR_1 ->
                LocalDateTime
                    .now()
                    .minusMonths(12)
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()

            ALL -> 0
        }
}

/**
 * Start of the range a Estadísticas chip names, as the stored event timestamp (local wall-clock time read as UTC, the
 * way `Converters` writes `Event.timestamp`). Registry row 909: a week, month or year chip starts at local MIDNIGHT —
 * it used to keep the current time of day, so on the 1st of a month everything played before «now o'clock» was
 * counted in the previous month, and the week chips ran one day early. The window of chip `t` ends where chip `t - 1`
 * starts (chip 0 ends now), so the chips stay contiguous with no gap and no overlap. [OptionStats.CONTINUOUS] is a
 * rolling window ending now («last month»), unchanged.
 */
fun statToPeriod(
    selection: OptionStats,
    test: Int,
    now: LocalDateTime = LocalDateTime.now(),
): Long =
    when (selection) {
        // Chip 0 is «today» (its label reads «27 - 27 nov»), chip t ≥ 1 the seven days its label names.
        OptionStats.WEEKS -> {
            now
                .toLocalDate()
                .atStartOfDay()
                .minusWeeks(test.toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.MONTHS -> {
            now
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay()
                .minusMonths(test.toLong())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }

        OptionStats.YEARS -> {
            now
                .toLocalDate()
                .withDayOfYear(1)
                .atStartOfDay()
                .minusYears(test.toLong())
                .toInstant(
                    ZoneOffset.UTC,
                ).toEpochMilli()
        }

        OptionStats.CONTINUOUS -> {
            val index = if (test >= StatPeriod.entries.size) StatPeriod.entries.size - 1 else test
            StatPeriod.entries[index].toTimeMillis()
        }
    }
