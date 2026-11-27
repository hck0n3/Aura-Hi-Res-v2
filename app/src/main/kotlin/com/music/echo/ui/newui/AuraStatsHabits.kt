package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.R
import iad1tya.echo.music.stats.ListeningStats
import iad1tya.echo.music.stats.StatsFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Registry row 909 — «Tus hábitos» on Estadísticas: when you listen (hour of the day, day of the week), your streaks,
 * the artists you found and your genres, for the range the chips above select. Everything comes from
 * [ListeningStats.Summary], computed on the phone. The charts are drawn once per summary: no animation, no clock.
 */
@Composable
internal fun AuraStatsHabits(
    summary: ListeningStats.Summary,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AuraSectionHeader(title = stringResource(R.string.stats_habits_title))
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AuraSpacing.Gutter)
                .padding(top = AuraSpacing.SectionGap),
        ) {
            if (summary.plays == 0) {
                AuraHabitsLine(stringResource(R.string.stats_habits_empty))
                return@Column
            }
            AuraHabitsLine(
                pluralStringResource(R.plurals.stats_listening_days, summary.listeningDays, summary.listeningDays),
            )
            if (summary.currentStreak > 0) {
                AuraHabitsLine(
                    pluralStringResource(R.plurals.stats_streak_current, summary.currentStreak, summary.currentStreak),
                )
            }
            summary.longestStreak?.let { streak ->
                AuraHabitsLine(pluralStringResource(R.plurals.stats_streak_longest, streak.days, streak.days))
            }
            if (summary.newArtists.isNotEmpty()) {
                AuraHabitsLine(
                    pluralStringResource(R.plurals.stats_new_artists, summary.newArtists.size, summary.newArtists.size),
                )
            }
            summary.topSongsByPlays.firstOrNull()?.let { song ->
                AuraHabitsLine(
                    stringResource(
                        R.string.stats_habits_most_times,
                        song.name,
                        pluralStringResource(R.plurals.n_time, song.plays, song.plays),
                    ),
                )
            }

            AuraHabitsLabel(stringResource(R.string.stats_habits_by_hour))
            AuraStatsBars(values = summary.playTimeByHour, height = 56.dp)
            Row(Modifier.fillMaxWidth()) {
                // One label per six hours, each over its own quarter of the bars.
                listOf("0", "6", "12", "18").forEach { hour ->
                    Text(
                        text = hour,
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundFaint,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AuraHabitsLabel(stringResource(R.string.stats_habits_by_weekday))
            AuraStatsBars(values = summary.playTimeByDayOfWeek, height = 56.dp)
            val locale = Locale.getDefault()
            val dayNames = remember(locale) { DayOfWeek.values().map { it.getDisplayName(TextStyle.NARROW, locale) } }
            Row(Modifier.fillMaxWidth()) {
                dayNames.forEach { name ->
                    Text(
                        text = name,
                        style = AuraType.RowSubtitle,
                        color = AuraPalette.OnGroundFaint,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = AuraDefaultOverflow,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (summary.topGenres.isNotEmpty()) {
                AuraHabitsLabel(stringResource(R.string.stats_habits_top_genres))
                summary.topGenres.forEachIndexed { index, genre ->
                    AuraHabitsLine("${index + 1}. ${genre.name} · ${StatsFormat.hoursMinutes(genre.playTimeMs)}")
                }
            }
        }
    }
}

@Composable
private fun AuraHabitsLabel(text: String) {
    Text(
        text = text,
        style = AuraType.SectionLabel,
        color = AuraPalette.OnGroundMuted,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun AuraHabitsLine(text: String) {
    Text(text = text, style = AuraType.RowSubtitle, color = AuraPalette.OnGround)
}

/** One rounded bar per value, the tallest filling the height. Static: drawn when the summary changes, never per frame. */
@Composable
private fun AuraStatsBars(values: List<Long>, height: Dp) {
    val fractions = remember(values) { StatsFormat.fractions(values) }
    val bar = AuraPalette.Teal
    val track = AuraPalette.TrackEmpty
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (fractions.isEmpty()) return@Canvas
        val slot = size.width / fractions.size
        val width = slot * 0.6f
        val corner = CornerRadius(width / 2f, width / 2f)
        fractions.forEachIndexed { i, f ->
            val x = i * slot + (slot - width) / 2f
            drawRoundRect(color = track, topLeft = Offset(x, 0f), size = Size(width, size.height), cornerRadius = corner)
            if (f > 0f) {
                val h = (size.height * f).coerceAtLeast(width)
                drawRoundRect(color = bar, topLeft = Offset(x, size.height - h), size = Size(width, h), cornerRadius = corner)
            }
        }
    }
}
