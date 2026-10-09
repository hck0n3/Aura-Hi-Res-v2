package iad1tya.echo.music.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.extensions.SilentHandler
import iad1tya.echo.music.extensions.currentMetadata
import iad1tya.echo.music.extensions.metadata
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playback.MusicService.Companion.TAG
import iad1tya.echo.music.playback.queues.filterExplicit
import iad1tya.echo.music.playback.queues.filterNonMusicForAutoQueue
import iad1tya.echo.music.playback.queues.filterVideoSongs
import iad1tya.echo.music.reco.ArtistStyleMemory
import iad1tya.echo.music.reco.ArtistTagStyles
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    // Song-search cursor (the last resort of the style fill).
    @Volatile var searchQuery: String? = null
    @Volatile var searchContinuation: String? = null

    // Fila 356, punto 2 — playlists people made of the style ("Cumbias cristianas"…), used one after another.
    @Volatile var playlistQuery: String? = null
    @Volatile var playlistIds: List<String> = emptyList()
    @Volatile var playlistCursor = 0

    /** Track ids already counted in [ArtistStyleMemory] this process, so one track never counts twice. */
    val learnedIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    // Fila 356, punto 3 — what the user skipped quickly in THIS queue, and what the gate let through.
    val gatedIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val skippedArtists: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val skippedStyles: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    val confirmedStyles: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var currentItem: iad1tya.echo.music.models.MediaMetadata? = null
    @Volatile var currentItemStartedAt = 0L

    // Fila 356, punto 4 — what the queue shows ("Siguiendo: Cumbia · cristiana") and the user's correction.
    @Volatile var override: StyleContinuity.Override? = null
    val follow = MutableStateFlow<StyleContinuity.Follow?>(null)

    /** A new queue (tap, list, external queue) starts with a clean slate. Main thread. */
    fun resetForNewQueue() {
        chipSteered = false
        override = null
        follow.value = null
        gatedIds.clear()
        skippedArtists.clear()
        skippedStyles.clear()
        confirmedStyles.clear()
    }
}

private const val LEARNED_IDS_CAP = 20_000
private const val GATED_IDS_CAP = 5_000
private const val FILL_PAGES_PER_CALL = 2

/** Last.fm lookups for this batch's artists may delay the batch by at most this much (silent: early seed). */
private const val TAG_ENRICH_WAIT_MS = 1_500L

/** A user skip within this time of a song's start says "this does not fit". */
private const val QUICK_SKIP_MS = 30_000L

/** A style skipped this many times (and never listened through) in a queue is out for that queue. */
private const val STYLE_SKIPS_TO_REJECT = 2

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
 * candidates are confirmed in style, the style itself is fetched — first from playlists people made of it,
 * then from a song search ("cumbia cristiana", "corridos", "rock en español"…) — and those songs join the batch.
 *
 * An artist's style comes, in this order, from its Last.fm tags ([ArtistTagStyles], fila 356), what the app
 * learned from its titles ([ArtistStyleMemory]) and a concrete iTunes genre.
 *
 * Returns null when the gate does not apply (setting off, chip steer, "any style" with no language, nothing
 * known about the target) — the caller keeps its list exactly as before. An empty list means nothing fits:
 * the caller tries its next source rather than playing another style.
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
    val st = exactStyle
    val pool = if (collection) radioSeedPool else emptyList()
    if (!collection && anchor == null) return@withContext null
    val genres = runCatching { GenreCache.snapshot(service) }.getOrDefault(emptyMap())

    // LEARN first, from everything in view: the collection and this batch (own words only).
    fun learnFrom(tracks: List<TrackText>, impliedStyle: String? = null) {
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
    val poolTexts = pool.map { it.trackText() }
    learnFrom(poolTexts + items.map { it.trackText() })

    // Fila 356, punto 1 — Last.fm tags for the artists that decide: the anchor's / the collection's most
    // frequent ones first (their own failure budget), then this batch's. Bounded wait; the lookups that do
    // not make it in time still land in the cache for the next batch.
    val anchorArtists = if (collection) {
        poolTexts.mapNotNull { it.primary }.groupingBy { it.trim().lowercase() }.eachCount()
            .entries.sortedByDescending { it.value }.take(8).map { it.key }
    } else {
        listOfNotNull(anchor?.artists?.firstOrNull()?.name)
    }
    val batchArtists = items.mapNotNull { it.trackText().primary }
    val tagJob = scope.launch(Dispatchers.IO + SilentHandler) {
        runCatching { ArtistTagStyles.enrich(service, anchorArtists) }
        runCatching { ArtistTagStyles.enrich(service, batchArtists) }
    }
    withTimeoutOrNull(TAG_ENRICH_WAIT_MS) { tagJob.join() }
    val tags = runCatching { ArtistTagStyles.snapshot(service) }.getOrDefault(emptyMap())
    val memory = runCatching { ArtistStyleMemory.snapshot(service) }.getOrDefault(emptyMap())

    fun artistStyle(artist: String?): String? =
        ArtistStyleMemory.key(artist)?.let { tags[it] } ?: ArtistStyleMemory.styleOf(memory, artist)

    fun styleOf(t: TrackText): String? =
        StyleContinuity.trackStyle(t.text, artistStyle(t.primary), GenreLane.lookupGenre(genres, t.primary))

    val detected: StyleContinuity.Target
    val ownArtists: Set<String>
    if (collection && pool.isNotEmpty()) {
        val nameStyle = MusicStyle.fromText(radioSeedTitle)
        val christianTracks = pool.count { mm ->
            GenreLane.laneOfTrack(genres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title) == GenreLane.CHRISTIAN
        }
        detected = StyleContinuity.Target(
            styles = StyleContinuity.collectionStyles(poolTexts.map { styleOf(it) }, nameStyle),
            language = collectionLanguage(),
            christian = christianTracks * 2 >= pool.size || GenreLane.laneOf(radioSeedTitle) == GenreLane.CHRISTIAN,
        )
        ownArtists = poolTexts.flatMap { it.artists }.map { it.trim().lowercase() }.toHashSet()
    } else if (anchor != null) {
        val t = anchor.trackText()
        detected = StyleContinuity.Target(
            styles = styleOf(t)?.let { setOf(it) },
            language = TitleLanguage.detect(t.text),
            christian = GenreLane.laneOfTrack(genres, t.primary, anchor.title, anchor.album?.title) == GenreLane.CHRISTIAN,
        )
        ownArtists = t.artists.map { it.trim().lowercase() }.toHashSet()
    } else {
        return@withContext null
    }
    // Fila 361: a "Pedir música" queue continues what was ASKED (styles, language, faith), not a guess.
    val base = iad1tya.echo.music.reco.RequestStyleTargets.get(radioOriginContextId) ?: detected
    val override = st.override
    val target = StyleContinuity.applyOverride(base, override)
    st.follow.value = StyleContinuity.Follow(
        detected = base.styles.orEmpty().sorted(),
        applied = target.styles.orEmpty().sorted(),
        christian = target.christian,
        language = target.language,
        override = override,
    )
    if (!target.active) {
        Timber.tag(TAG).i("STYLE_GATE %s: style and language unknown, no filter applied", site)
        return@withContext null
    }

    fun judge(mi: MediaItem, fromSearch: Boolean): StyleContinuity.Verdict {
        val t = mi.trackText()
        val textStyle = MusicStyle.fromText(t.text)
        val learned = artistStyle(t.primary)
        val genre = MusicStyle.fromGenre(GenreLane.lookupGenre(genres, t.primary))
        val known = textStyle ?: learned ?: genre.style
        val rejected = ArtistStyleMemory.key(t.primary)?.let { it in st.skippedArtists } == true ||
            (known != null && known !in target.styles.orEmpty() && known !in st.confirmedStyles &&
                (st.skippedStyles[known] ?: 0) >= STYLE_SKIPS_TO_REJECT)
        return StyleContinuity.verdict(
            target,
            StyleContinuity.Candidate(
                textStyle = textStyle,
                learnedStyle = learned,
                genre = genre,
                language = TitleLanguage.detect(t.text),
                ownArtist = t.artists.any { it.trim().lowercase() in ownArtists },
                fromSearch = fromSearch,
                rejected = rejected,
            ),
        )
    }

    val all = ArrayList(items)
    val verdicts = ArrayList(items.map { judge(it, fromSearch = false) })
    var fetched = 0
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
            repeat(FILL_PAGES_PER_CALL) {
                if (verdicts.count { it == StyleContinuity.Verdict.MATCH } >= minMatches) return@repeat
                val found = styleFillPage(query)
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
                fetched += fresh.size
                fresh.forEach { mi ->
                    all.add(mi)
                    verdicts.add(judge(mi, fromSearch = true))
                }
            }
        }
    }
    val kept = StyleContinuity.select(all, verdicts, minMatches)
    if (st.gatedIds.size > GATED_IDS_CAP) st.gatedIds.clear()
    kept.forEach { st.gatedIds.add(it.mediaId) }
    // Counts and style ids only — never a title, artist or id (AGENTS.md rule 4).
    Timber.tag(TAG).i(
        "STYLE_GATE %s: styles=%s lang=%s christian=%b override=%s match=%d unknown=%d off=%d fetched=%d kept=%d",
        site,
        target.styles?.sorted()?.joinToString("+") ?: "unknown",
        target.language ?: "unknown",
        target.christian,
        override?.let { it::class.simpleName } ?: "none",
        verdicts.count { it == StyleContinuity.Verdict.MATCH },
        verdicts.count { it == StyleContinuity.Verdict.UNKNOWN },
        verdicts.count { it == StyleContinuity.Verdict.OFF },
        fetched,
        kept.size,
    )
    kept
}

/**
 * Fila 356, punto 2 — more songs of the style: from the playlists PEOPLE made of it (featured + community,
 * shuffled, one playlist per page so the variety is real and nothing repeats), and only once those run out
 * from YouTube's song search for the same words.
 */
private suspend fun MusicService.styleFillPage(query: String): List<MediaItem> {
    val st = exactStyle
    if (st.playlistQuery != query) {
        st.playlistQuery = query
        st.playlistCursor = 0
        st.playlistIds = try {
            val featured = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()?.items.orEmpty()
            val community = YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()?.items.orEmpty()
            (featured.take(4) + community.take(6)).filterIsInstance<PlaylistItem>()
                .map { it.id }.distinct().shuffled(randomSeedSource)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("STYLE_GATE playlist search failed: %s", privacySafeSummary(e))
            emptyList()
        }
    }
    while (st.playlistCursor < st.playlistIds.size) {
        val id = st.playlistIds[st.playlistCursor++]
        val songs = try {
            YouTube.playlist(id).getOrThrow().songs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("STYLE_GATE playlist load failed: %s", privacySafeSummary(e))
            emptyList()
        }
        if (songs.isNotEmpty()) return songs.shuffled(randomSeedSource).map { it.toMediaItem() }
    }
    return styleSearchPage(query)
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

/**
 * Fila 356, punto 3 — learn from what the user does with the songs the gate let through. Called on every
 * track change (main thread, no I/O): a SKIP within [QUICK_SKIP_MS] of a gated song's start puts its artist —
 * and, after [STYLE_SKIPS_TO_REJECT], its style — out for the rest of this queue; a song heard to its end
 * confirms its style so it is never rejected afterwards. Counts only in the log.
 */
internal fun MusicService.exactStyleOnTransition(newItem: MediaItem?, reason: Int) {
    val st = exactStyle
    val previous = st.currentItem
    val startedAt = st.currentItemStartedAt
    val now = SystemClock.elapsedRealtime()
    st.currentItem = newItem?.metadata
    st.currentItemStartedAt = now
    if (previous == null || previous.id == newItem?.mediaId || previous.id !in st.gatedIds) return
    val t = previous.trackText()
    val ownStyle = MusicStyle.fromText(t.text)
    when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> if ((now - startedAt) in 0L until QUICK_SKIP_MS) {
            ArtistStyleMemory.key(t.primary)?.let { st.skippedArtists.add(it) }
            ownStyle?.let { st.skippedStyles.merge(it, 1, Int::plus) }
            Timber.tag(TAG).i(
                "STYLE_GATE quick skip learned: artists=%d styles=%d",
                st.skippedArtists.size, st.skippedStyles.size,
            )
        }
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> ownStyle?.let { st.confirmedStyles.add(it) }
    }
}

/**
 * Fila 356, punto 4 — the user's choice in the queue ("Siguiendo: …"). An exact style also TEACHES: the song
 * the continuation comes from counts as that style for its artist, so the correction outlives this queue.
 * The songs the gate already queued after the current one are judged again at once and those that no longer
 * fit leave the queue; the user's own list is never touched (only ids the gate let in are judged).
 */
internal fun MusicService.setExactStyleOverride(override: StyleContinuity.Override?) {
    val st = exactStyle
    st.override = override
    st.follow.value = st.follow.value?.let { f ->
        val detected = StyleContinuity.Target(f.detected.toSet().ifEmpty { null }, f.language, f.christian)
        f.copy(applied = StyleContinuity.applyOverride(detected, override).styles.orEmpty().sorted(), override = override)
    }
    val anchor = radioAnchorMetadata ?: player.currentMetadata
    val exact = override as? StyleContinuity.Override.Exact
    val artist = anchor?.artists?.firstOrNull()?.name
    val collection = iad1tya.echo.music.reco.CollectionContinuation.isCollection(radioAnchorId != null, radioSeedPool.size)
    val current = player.currentMediaItemIndex
    val tail = (current + 1 until player.mediaItemCount)
        .map { player.getMediaItemAt(it) }
        .filter { it.mediaId in st.gatedIds }
    val generation = queueGeneration
    scope.launch(SilentHandler) {
        if (exact != null && artist != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    ArtistStyleMemory.learn(this@setExactStyleOverride, listOf(artist to exact.style, artist to exact.style))
                }
            }
        }
        if (tail.isEmpty()) return@launch
        // Int.MAX_VALUE: never "enough matches", so only what is now OFF goes — unknowns stay.
        val kept = exactStyleFilter(tail, anchor, collection, "override", Int.MAX_VALUE, allowSearch = false)
            ?: return@launch
        if (queueGeneration != generation) return@launch
        val keptIds = kept.mapTo(HashSet()) { it.mediaId }
        val drop = tail.map { it.mediaId }.filter { it !in keptIds }.toHashSet()
        var removed = 0
        for (i in player.mediaItemCount - 1 downTo player.currentMediaItemIndex + 1) {
            if (player.getMediaItemAt(i).mediaId in drop) {
                player.removeMediaItem(i)
                removed++
            }
        }
        Timber.tag(TAG).i("STYLE_GATE override re-judged the queued tail: removed=%d of %d", removed, tail.size)
        if (removed > 0 && !player.hasNextMediaItem()) startRadioSeamlessly()
    }
}
