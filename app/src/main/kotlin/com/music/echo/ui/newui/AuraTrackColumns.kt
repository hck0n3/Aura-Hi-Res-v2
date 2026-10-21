package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import iad1tya.echo.music.reco.ContextPattern
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
 * The trailing end of a column row is TWO fixed-width slots, reserved whether or not the row has anything to
 * show in them. A heart / download tick / «ya reproducida» check used to widen the trailing cluster on the rows
 * that had one, and the weighted columns (artist, genre, duration) then got a different width on every row —
 * a liked song's columns sat ~25 px left of its neighbours' (owner, 2026-10-20, the «Oxigeno» screenshot).
 *  · state slot  — heart, download state, library mark, played check, quality badge; right-aligned. Sized for
 *    the usual worst case (liked + downloaded + in library); a rarer longer cluster grows LEFT, over the empty
 *    part of the duration cell, never pushing a column.
 *  · action slot — the ⋯ menu or, in selection mode, the checkbox: both are 48 dp, so swapping them is free.
 */
internal val AuraTrackStateSlotWidth: Dp = 72.dp
internal val AuraTrackActionSlotWidth: Dp = AuraSpacing.MinTouchTarget

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

/** Most artists offered to one enrich call; [GenreCache.enrich] itself still caps a call at 15 lookups. */
internal const val AURA_TRACK_GENRE_STUDY_ARTISTS = 40

/**
 * Loads the genre snapshot ONCE per screen visit, off the main thread, and only when the pane is wide enough
 * to show the column (a folded phone never touches it). No per-row work.
 *
 * [primaryArtists] = the first artist of every song of the list. Once the list has songs, the artists the
 * cache does not know yet are looked up with ONE bounded [GenreCache.enrich] call per screen instance
 * (`onlyWifi = true` = honour the user's network preference, exactly like MusicService's own study call;
 * at most 15 lookups, known artists and definitive misses skipped), and the snapshot is re-read so the genre
 * column fills in without reopening the screen. Wide pane only. Nothing is logged.
 */
@Composable
internal fun rememberAuraTrackGenres(primaryArtists: List<String> = emptyList()): AuraTrackGenres {
    val columns = rememberAuraTrackColumns()
    val context = LocalContext.current.applicationContext
    var refresh by remember { mutableIntStateOf(0) }
    var enrichStarted by remember { mutableStateOf(false) }
    val byArtist by produceState(emptyMap<String, String>(), columns, refresh) {
        if (columns) {
            value = withContext(Dispatchers.IO) {
                runCatching { GenreCache.snapshot(context) }.getOrDefault(emptyMap())
            }
        }
    }
    val hasArtists = primaryArtists.isNotEmpty()
    LaunchedEffect(columns, hasArtists) {
        if (!columns || !hasArtists || enrichStarted) return@LaunchedEffect
        enrichStarted = true
        val study = ContextPattern.studyArtists(primaryArtists, AURA_TRACK_GENRE_STUDY_ARTISTS)
        if (study.isEmpty()) return@LaunchedEffect
        runCatching { GenreCache.enrich(context, study, onlyWifi = true) }
        refresh++
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


/**
 * The trailing end of a column row: [state] in its fixed slot, then [action] in its fixed slot. Always the same
 * total width ([AuraTrackStateSlotWidth] + [AuraTrackActionSlotWidth]), so every row of a list shares the same
 * column x-positions whatever it has to show (heart, download tick, played check, menu, checkbox, nothing).
 */
@Composable
internal fun AuraTrackTrailingSlots(
    state: @Composable RowScope.() -> Unit,
    action: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(AuraTrackStateSlotWidth), contentAlignment = Alignment.CenterEnd) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true),
                content = state,
            )
        }
        Box(Modifier.width(AuraTrackActionSlotWidth), contentAlignment = Alignment.Center) { action() }
    }
}
