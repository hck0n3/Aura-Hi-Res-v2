package iad1tya.echo.music.stats

/** Registry rows 909-910 — how a listening time reads on Estadísticas and «Tu año en Aura» (pure). */
object StatsFormat {
    /** «3h 20m», «45m», «0m» — the same shape as the Resumen sheet (`ActivityHistory.formatDuration`). */
    fun hoursMinutes(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMinutes = ms / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    /** The bar heights of a chart, 0..1 against the largest value; all 0 when nothing was played. */
    fun fractions(values: List<Long>): List<Float> {
        val max = values.maxOrNull() ?: 0L
        if (max <= 0L) return values.map { 0f }
        return values.map { (it.coerceAtLeast(0L).toDouble() / max).toFloat() }
    }
}
