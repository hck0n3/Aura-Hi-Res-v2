package iad1tya.echo.music.playback

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.MusicService.Companion.TAG
import iad1tya.echo.music.playback.queues.filterExplicit
import iad1tya.echo.music.playback.queues.filterNonMusicForAutoQueue
import iad1tya.echo.music.playback.queues.filterVideoSongs
import iad1tya.echo.music.reco.ArtistStyleMemory
import iad1tya.echo.music.reco.GenreCache
import iad1tya.echo.music.reco.GenreLane
import iad1tya.echo.music.reco.MusicStyle
import iad1tya.echo.music.reco.StyleContinuity
import iad1tya.echo.music.reco.TitleLanguage
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import iad1tya.echo.music.utils.privacySafeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Fila 354 — the EXACT-STYLE gate of the smart queue (rules in [StyleContinuity], vocabulary in [MusicStyle],
 * learned artist styles in [ArtistStyleMemory]).
 *
 * 🔴 Dueño (2026-10-08): *"estaba escuchando una cumbia cristiana y cuando terminó la cola inteligente continuó
 * con salsa y merengue… no solo me refiero al ámbito cristiano… repara todo eso en general de los géneros"*.
 *
 * Runs LAST on every automatic continuation, after the lane/profile/language passes that already existed
 * (they stay as they were): the first radio batch and every re-seed ([startRadioSeamlessly]'s appendSeed),
 * each radio page ([MusicService.maybeLoadMoreQueuePages]) and the tapped song's own list (playQueue). It
 * never runs on the user's own lists, on a mood, or after an autoplay chip (an explicit choice of direction).
 */
internal class ExactStyleState {
    /** An autoplay chip is an explicit steer: the gate stands down until a new queue starts. */
    @Volatile var chipSteered = false
    @Volatile var searchQuery: String? = null
    @Volatile var searchContinuation: String? = null

    /** Track ids already counted in [ArtistStyleMemory] this process, so one track never counts twice. */
    val learnedIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
}

private const val LEARNED_IDS_CAP = 20_000
private const val SEARCH_PAGES_PER_CALL = 2

private class TrackText(val id: String?, val primary: String?, val artists: List<String>, val text: String)

private val ARTIST_SPLIT = Regex(",|;| & | feat\\.? | ft\\.? | x | con | y ", RegexOption.IGNORE_CASE)

private fun primaryOf(byline: String?): String? =
    byline?.split(ARTIST_SPLIT)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

private fun MediaItem.trackText(): TrackText {
    val m = metadata
    val title = m?.title ?: mediaMetadata.title?.toString()
    val album = m?.album?.title ?: mediaMetadata.albumTitle?.toString()
    val artists = m?.artists?.map { it.name }?.filter { it.isNotBlank() }
        ?: listOfNotNull(mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() })
    val primary = m?.artists?.firstOrNull()?.name ?: primaryOf(mediaMetadata.artist?.toString())
    return TrackText(mediaId, primary, artists, listOfNotNull(title, album).joinToString(" "))
}

private fun iad1tya.echo.music.models.MediaMetadata.trackText(): TrackText =
    TrackText(id, artists.firstOrNull()?.name, artists.map { it.name }, listOfNotNull(title, album?.title).joinToString(" "))

/**
 * Filters [items] to the continuation's exact style and language. [anchor] is the song the continuation
 * comes from (single-song case); [collection] says the continuation is a finished album/playlist's
 * ([MusicService.radioSeedPool]), whose styles then rule. With [allowSearch], when fewer than [minMatches]
 * candidates are confirmed in style, YouTube is searched for the style itself ("cumbia cristiana",
 * "corridos", "rock en español"…) and those songs join the batch.
 *
 * Returns null when the gate does not apply (setting off, chip steer, nothing known about the target) —
 * the caller keeps its list exactly as before. An empty list means nothing fits: the caller tries its next
 * source rather than playing another style.
 */
internal suspend fun MusicService.exactStyleFilter(
    items: List<MediaItem>,
    anchor: iad1tya.echo.music.models.MediaMetadata?,
    collection: Boolean,
    site: String,
    minMatches: Int,
    allowSearch: Boolean,
): List<MediaItem>? = withContext(Dispatchers.IO) {
    if (!keepGenreLaneHint || exactStyle.chipSteered) return@withContext null
    val service = this@exactStyleFilter
    val pool = if (collection) radioSeedPool else emptyList()
    val genres = runCatching { GenreCache.snapshot(service) }.getOrDefault(emptyMap())

    // LEARN first, from everything in view: the collection and this batch (own words only).
    fun learnFrom(tracks: List<TrackText>, impliedStyle: String? = null) {
        val st = exactStyle
        if (st.learnedIds.size > LEARNED_IDS_CAP) st.learnedIds.clear()
        val observations = tracks.mapNotNull { t ->
            val id = t.id ?: return@mapNotNull null
            val artist = t.primary ?: return@mapNotNull null
            val style = MusicStyle.fromText(t.text) ?: impliedStyle ?: return@mapNotNull null
            if (!st.learnedIds.add(id)) return@mapNotNull null
            artist to style
        }
        runCatching { ArtistStyleMemory.learn(service, observations) }
    }
    learnFrom(pool.map { it.trackText() } + items.map { it.trackText() })
    val memory = runCatching { ArtistStyleMemory.snapshot(service) }.getOrDefault(emptyMap())

    fun styleOf(t: TrackText): String? = StyleContinuity.trackStyle(
        t.text, ArtistStyleMemory.styleOf(memory, t.primary), GenreLane.lookupGenre(genres, t.primary),
    )

    val target: StyleContinuity.Target
    val ownArtists: Set<String>
    if (collection && pool.isNotEmpty()) {
        val tracks = pool.map { it.trackText() }
        val nameStyle = MusicStyle.fromText(radioSeedTitle)
        val christianTracks = pool.count { mm ->
            GenreLane.laneOfTrack(genres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title) == GenreLane.CHRISTIAN
        }
        target = StyleContinuity.Target(
            styles = StyleContinuity.collectionStyles(tracks.map { styleOf(it) }, nameStyle),
            language = collectionLanguage(),
            christian = christianTracks * 2 >= pool.size || GenreLane.laneOf(radioSeedTitle) == GenreLane.CHRISTIAN,
        )
        ownArtists = tracks.flatMap { it.artists }.map { it.trim().lowercase() }.toHashSet()
    } else if (anchor != null) {
        val t = anchor.trackText()
        target = StyleContinuity.Target(
            styles = styleOf(t)?.let { setOf(it) },
            language = TitleLanguage.detect(t.text),
            christian = GenreLane.laneOfTrack(genres, t.primary, anchor.title, anchor.album?.title) == GenreLane.CHRISTIAN,
        )
        ownArtists = t.artists.map { it.trim().lowercase() }.toHashSet()
    } else {
        return@withContext null
    }
    if (!target.active) {
        Timber.tag(TAG).i("STYLE_GATE %s: style and language unknown, no filter applied", site)
        return@withContext null
    }

    fun judge(mi: MediaItem, fromSearch: Boolean): StyleContinuity.Verdict {
        val t = mi.trackText()
        return StyleContinuity.verdict(
            target,
            StyleContinuity.Candidate(
                textStyle = MusicStyle.fromText(t.text),
                learnedStyle = ArtistStyleMemory.styleOf(memory, t.primary),
                genre = MusicStyle.fromGenre(GenreLane.lookupGenre(genres, t.primary)),
                language = TitleLanguage.detect(t.text),
                ownArtist = t.artists.any { it.trim().lowercase() in ownArtists },
                fromSearch = fromSearch,
            ),
        )
    }

    val all = ArrayList(items)
    val verdicts = ArrayList(items.map { judge(it, fromSearch = false) })
    var searched = 0
    if (allowSearch && verdicts.count { it == StyleContinuity.Verdict.MATCH } < minMatches) {
        val styles = target.styles
        val style = when {
            styles.isNullOrEmpty() -> null
            styles.size == 1 -> styles.first()
            else -> styles.random(randomSeedSource)
        }
        val query = StyleContinuity.searchQuery(style, target.language, target.christian)
        if (query != null) {
            val originContextId = radioOriginContextId
            val playedBefore = if (originContextId != null) {
                runCatching { database.radioContinuationPlayedIds(originContextId) }.getOrDefault(emptyList()).toHashSet()
            } else {
                emptySet()
            }
            val seen = HashSet(items.map { it.mediaId })
            val hideExplicit = dataStore.get(HideExplicitKey, false)
            val hideVideos = dataStore.get(HideVideoSongsKey, false) ||
                dataStore.get(iad1tya.echo.music.constants.DataSaverEnabledKey, false)
            repeat(SEARCH_PAGES_PER_CALL) {
                if (verdicts.count { it == StyleContinuity.Verdict.MATCH } >= minMatches) return@repeat
                val found = styleSearchPage(query)
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideos)
                    .filterNonMusicForAutoQueue()
                learnFrom(found.map { it.trackText() }, impliedStyle = style)
                val fresh = found.filter { mi ->
                    seen.add(mi.mediaId) &&
                        mi.mediaId !in sessionPlayedIds && mi.dedupKeyOrNull() !in sessionPlayedDedupKeys &&
                        mi.mediaId !in playedBefore &&
                        !iad1tya.echo.music.utils.UnavailableSongs.isUnavailable(mi.mediaId)
                }
                searched += fresh.size
                fresh.forEach { mi ->
                    all.add(mi)
                    verdicts.add(judge(mi, fromSearch = true))
                }
            }
        }
    }
    val kept = StyleContinuity.select(all, verdicts, minMatches)
    // Counts and style ids only — never a title, artist or id (AGENTS.md rule 4).
    Timber.tag(TAG).i(
        "STYLE_GATE %s: styles=%s lang=%s christian=%b match=%d unknown=%d off=%d searched=%d kept=%d",
        site,
        target.styles?.sorted()?.joinToString("+") ?: "unknown",
        target.language ?: "unknown",
        target.christian,
        verdicts.count { it == StyleContinuity.Verdict.MATCH },
        verdicts.count { it == StyleContinuity.Verdict.UNKNOWN },
        verdicts.count { it == StyleContinuity.Verdict.OFF },
        searched,
        kept.size,
    )
    kept
}

/** One page of YouTube song results for [query], continuing the previous page of the same query. */
private suspend fun MusicService.styleSearchPage(query: String): List<MediaItem> {
    val st = exactStyle
    val continuation = st.searchContinuation.takeIf { st.searchQuery == query }
    val result = try {
        if (continuation != null) {
            YouTube.searchContinuation(continuation).getOrThrow()
        } else {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w("STYLE_GATE search failed: %s", privacySafeSummary(e))
        return emptyList()
    }
    st.searchQuery = query
    st.searchContinuation = result.continuation
    return result.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
}
