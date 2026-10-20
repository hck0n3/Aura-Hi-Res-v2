package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iad1tya.echo.music.R
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.ui.utils.LocalContentPaneWidth
import iad1tya.echo.music.ui.utils.WIDE_LAYOUT_BREAKPOINT_DP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Apple-Music-style track rows for álbum / EP / single / playlist.
 *
 *  · FOLDED (pane narrower than WIDE_LAYOUT_BREAKPOINT_DP): title with the artist underneath, the «E» badge
 *    right after the title.
 *  · UNFOLDED / WIDE: the row spreads over the whole pane as columns — title (+E) | artist | genre | duration —
 *    and keeps its trailing actions (heart, download, menu / checkbox).
 *
 * The width that decides is the width of the CONTENT PANE (LocalContentPaneWidth when the split player takes
 * the right half), not the window: a list squeezed next to the player stays stacked.
 */

/** Pane width (dp) from which a track row lays its columns out across the row. Same line as the app's wide layouts. */
internal val AURA_TRACK_COLUMNS_MIN_WIDTH: Dp = WIDE_LAYOUT_BREAKPOINT_DP.dp

/** Pure: does a content pane [paneWidth] wide have room for the column layout? */
internal fun auraTrackColumnsFit(paneWidth: Dp): Boolean = paneWidth >= AURA_TRACK_COLUMNS_MIN_WIDTH

/** Relative widths of the flexible columns (title, artist, genre); the duration column has a fixed width. */
internal const val AURA_TRACK_TITLE_WEIGHT = 1.7f
internal const val AURA_TRACK_ARTIST_WEIGHT = 1.0f
internal const val AURA_TRACK_GENRE_WEIGHT = 0.8f
internal val AuraTrackDurationWidth: Dp = 52.dp

/**
 * Does the screen's content pane have room for the column layout right now? Reads the window size and the
 * split-player override once per composition of the caller — nothing per frame.
 */
@Composable
internal fun rememberAuraTrackColumns(): Boolean {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val pane = LocalContentPaneWidth.current ?: with(density) { widthPx.toDp() }
    return auraTrackColumnsFit(pane)
}

/**
 * artist → genre lookup over the on-device [GenreCache] snapshot (iTunes primary genre of the ARTIST, keyed by
 * lowercased name). It is the only genre data the app has: songs and albums carry no genre of their own.
 */
internal class AuraTrackGenres(private val byArtist: Map<String, String>) {
    val isEmpty: Boolean get() = byArtist.isEmpty()

    /** The genre of the FIRST (main) artist, or null when unknown. Never guessed from another artist. */
    fun of(artistNames: List<String>): String? = auraArtistGenre(artistNames, byArtist)

    /** Is there at least one song in [songs] whose genre is known (decides whether the column is drawn at all)? */
    fun <T> anyKnown(songs: List<T>, artistNamesOf: (T) -> List<String>): Boolean =
        !isEmpty && songs.any { of(artistNamesOf(it)) != null }
}

/** Pure: genre of the first artist in [artistNames] from [genres] (lowercased-name keys), blank = unknown = null. */
internal fun auraArtistGenre(artistNames: List<String>, genres: Map<String, String>): String? {
    val first = artistNames.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return genres[first.lowercase()]?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Loads the genre snapshot ONCE per screen visit, off the main thread, and only when the pane is wide enough
 * to show the column (a folded phone never touches it). No network: this is a SharedPreferences read.
 */
@Composable
internal fun rememberAuraTrackGenres(): AuraTrackGenres {
    val columns = rememberAuraTrackColumns()
    val context = LocalContext.current.applicationContext
    val byArtist by produceState(emptyMap<String, String>(), columns) {
        if (columns) {
            value = withContext(Dispatchers.IO) {
                runCatching { GenreCache.snapshot(context) }.getOrDefault(emptyMap())
            }
        }
    }
    return remember(byArtist) { AuraTrackGenres(byArtist) }
}

/** The small grey rounded «E» Apple Music draws after an explicit title. Palette tokens only. */
@Composable
internal fun AuraExplicitBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.explicit)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(15.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AuraPalette.OnGround.copy(alpha = 0.20f))
            .semantics { contentDescription = description },
    ) {
        Text(
            text = "E",
            style = AuraType.QualityBadge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            ),
            color = AuraPalette.OnGroundMuted,
            maxLines = 1,
        )
    }
}

/**
 * The columns of a wide track row, as direct children of [AuraRow]'s own Row (so they get its inner spacing):
 * title (+ badge) | artist | genre | duration. An unknown artist / genre leaves its cell EMPTY — never filled in.
 * [showGenreColumn] reserves the genre cell for every row of a list that has at least one known genre, so the
 * columns line up; with no known genre in the list the cell is dropped and the artist gets the room.
 */
@Composable
internal fun RowScope.AuraTrackColumns(
    title: String,
    titleColor: Color,
    subtitleColor: Color,
    titleMaxLines: Int,
    titleBadge: (@Composable () -> Unit)?,
    artist: String?,
    genre: String?,
    showGenreColumn: Boolean,
    durationLabel: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(AURA_TRACK_TITLE_WEIGHT),
    ) {
        Text(
            text = title,
            style = AuraType.RowTitle,
            color = titleColor,
            maxLines = titleMaxLines,
            overflow = AuraDefaultOverflow,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (titleBadge != null) {
            Spacer(Modifier.width(6.dp))
            titleBadge()
        }
    }
    Box(Modifier.weight(AURA_TRACK_ARTIST_WEIGHT), contentAlignment = Alignment.CenterStart) {
        if (!artist.isNullOrBlank()) {
            Text(
                text = artist,
                style = AuraType.RowSubtitle,
                color = subtitleColor,
                maxLines = 1,
                overflow = AuraDefaultOverflow,
            )
        }
    }
    if (showGenreColumn) {
        Box(Modifier.weight(AURA_TRACK_GENRE_WEIGHT), contentAlignment = Alignment.CenterStart) {
            if (!genre.isNullOrBlank()) {
                Text(
                    text = genre,
                    style = AuraType.RowSubtitle,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = AuraDefaultOverflow,
                )
            }
        }
    }
    Box(Modifier.width(AuraTrackDurationWidth), contentAlignment = Alignment.CenterEnd) {
        if (!durationLabel.isNullOrBlank()) {
            AuraTechnicalText(
                text = durationLabel,
                color = AuraPalette.OnGroundFaint,
                style = AuraType.RowSubtitle,
            )
        }
    }
}
