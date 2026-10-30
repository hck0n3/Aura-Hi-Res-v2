package iad1tya.echo.music.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
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
import iad1tya.echo.music.reco.ArtistLanguageMemory
import iad1tya.echo.music.reco.ArtistStyleMemory
import iad1tya.echo.music.reco.ArtistTagStyles
import iad1tya.echo.music.reco.ContextLane
import iad1tya.echo.music.reco.ContextProfile
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
import java.util.concurrent.ConcurrentLinkedQueue

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

    /** Same for [ArtistLanguageMemory] (row 459). */
    val learnedLangIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    // Row 459 — songs of the context's own artists and their neighbourhood, fetched in a BOUNDED number of artist
    // pages per queue ([LANE_FILL_REQUEST_BUDGET]) and drawn from one batch after another.
    val laneFillPool = ConcurrentLinkedQueue<MediaItem>()
    val laneFillFetched: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var laneFillRequests = 0

    // Fila 356, punto 3 — what the user skipped quickly in THIS queue, and what the gate let through.
    val gatedIds: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val skippedArtists: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    val skippedStyles: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    val confirmedStyles: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    @Volatile var currentItem: iad1tya.echo.music.models.MediaMetadata? = null
    @Volatile var currentItemStartedAt = 0L

    /** Artists skipped within seconds in THIS queue, any song (not only the gated ones): the ordering demotes them. */
    val quickSkips = iad1tya.echo.music.reco.SessionSkipMemory()

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
        quickSkips.clear()
        laneFillPool.clear()
        laneFillFetched.clear()
        laneFillRequests = 0
    }
}

private const val LEARNED_IDS_CAP = 20_000
private const val GATED_IDS_CAP = 5_000
private const val FILL_PAGES_PER_CALL = 2

/**
 * Row 459 — the artist-page budget of the lane fill (the context's own artists and the artists YouTube lists next
 * to them). Per queue and per call, so a long session can never turn the gate into a request loop (AGENTS rule 7).
 */
private const val LANE_FILL_REQUEST_BUDGET = 6
private const val LANE_FILL_REQUESTS_PER_CALL = 3
private const val LANE_FILL_SEED_ARTISTS = 2
private const val LANE_FILL_RELATED_ARTISTS = 2
private const val LANE_FILL_SONGS_PER_ARTIST = 6
private const val LANE_FILL_TAKE = 14

/** Last.fm lookups for this batch's artists may delay the batch by at most this much (silent: early seed). */
private const val TAG_ENRICH_WAIT_MS = 1_500L

/** Row 435: the longest the continuation gate waits for album years before judging a batch (bounded; the rest is unknown). */
private const val ERA_PREFETCH_MS = 3_500L

/** A user skip within this time of a song's start says "this does not fit". */
private const val QUICK_SKIP_MS = 30_000L

/** A style skipped this many times (and never listened through) in a queue is out for that queue. */
private const val STYLE_SKIPS_TO_REJECT = 2

// Row 527: how much of the batch's head is read for a family when the context names none, and how clear the lead must be.
private const val CONSENSUS_WINDOW = 15
private const val CONSENSUS_MIN_KNOWN = 4
private const val CONSENSUS_SHARE = 0.6

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

/** One judged candidate: the style verdict and everything the lane ([ContextLane]) needs to rank it. */
private class Judged(val verdict: StyleContinuity.Verdict, val candidate: ContextLane.Candidate)

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
 * Row 459 (owner 2026-10-20: *«rap cristiano en español → reggaetón → hip hop en inglés»*): the verdicts are now
 * ranked by the context's LANE ([ContextLane]) — language is a gate (strong evidence of another language never
 * plays, unless the context itself mixes them), the genre family is a gate when the concrete style is unknown,
 * and the unknowns are admitted in small steps (same language and faith first) instead of all at once. When the
 * exact steps are short, the context's own artists and their neighbourhood are fetched ([laneFillPage]) BEFORE the
 * weaker steps are accepted. [languageOnly] is the engine's second-to-last relaxation: style and family stand
 * down, the language still holds.
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
    languageOnly: Boolean = false,
    /**
     * Row 503: the LAST relaxation level — the genre-family fence of [languageOnly] comes down too. The language and,
     * in a Christian context, the faith still hold: the gate is never switched off while the context says anything.
     */
    dropFamilies: Boolean = false,
): List<MediaItem>? = withContext(Dispatchers.IO) {
    if (!keepGenreLaneHint || exactStyle.chipSteered) return@withContext null
    val service = this@exactStyleFilter
    val st = exactStyle
    val pool = if (collection) radioSeedPool else emptyList()
    if (!collection && anchor == null) return@withContext null
    val genres = runCatching { GenreCache.snapshot(service) }.getOrDefault(emptyMap())
    // Row 525: artists the user skipped quickly in the songs a smart queue added, in earlier queues too (30 days).
    val persistedRejected = runCatching { iad1tya.echo.music.reco.ContinuationSkipMemory.rejectedArtists(service) }.getOrDefault(emptySet())
    if (persistedRejected.isNotEmpty()) Timber.tag(TAG).i("STYLE_GATE remembered skips apply: artists=%d", persistedRejected.size)

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
        // Row 459 — and the artist's LANGUAGE, from the track's own words (a title's language never changes, so
        // an undecided title is remembered as seen too and is not re-read on every batch).
        if (st.learnedLangIds.size > LEARNED_IDS_CAP) st.learnedLangIds.clear()
        val languageObservations = tracks.mapNotNull { t ->
            val id = t.id ?: return@mapNotNull null
            val artist = t.primary ?: return@mapNotNull null
            if (!st.learnedLangIds.add(id)) return@mapNotNull null
            val language = ContextLane.languageOfText(t.text) ?: return@mapNotNull null
            artist to language
        }
        runCatching { ArtistLanguageMemory.learn(service, languageObservations) }
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
    // Row 459 — what else is known about the artists: the language they sing in (learned), the family / language
    // hint of their Last.fm tags, and whether those tags call them Christian.
    val langMemory = runCatching { ArtistLanguageMemory.snapshot(service) }.getOrDefault(emptyMap())
    val tagLanes = runCatching { ArtistTagStyles.laneSnapshot(service) }.getOrDefault(emptyMap())
    val christianTags = runCatching { ArtistTagStyles.christianSnapshot(service) }.getOrDefault(emptyMap())

    fun artistStyle(artist: String?): String? =
        ArtistStyleMemory.key(artist)?.let { tags[it] } ?: ArtistStyleMemory.styleOf(memory, artist)

    fun styleOf(t: TrackText): String? =
        StyleContinuity.trackStyle(t.text, artistStyle(t.primary), GenreLane.lookupGenre(genres, t.primary))

    // The language we can SAY about a track: its own words, else what is learned of its artist (both strong), else
    // a Spanish hint (Last.fm tags, an iTunes «Latin»/«Urbano» genre) that ranks but never excludes.
    fun languageEvidence(t: TrackText): ContextLane.LangEvidence {
        val key = ArtistStyleMemory.key(t.primary)
        val genre = GenreLane.lookupGenre(genres, t.primary)
        val spanishHint = (key != null && tagLanes[key]?.language == TitleLanguage.ES) ||
            (genre != null && ContextProfile.isSpanishGenre(genre))
        return ContextLane.candidateLanguage(
            titleLanguage = ContextLane.languageOfText(t.text),
            artistLanguage = ArtistLanguageMemory.languageOf(langMemory, t.primary),
            spanishHint = spanishHint,
        )
    }

    // The genre FAMILY of a track (urbano, rock, tropical…): its [style]'s family, else the iTunes family, else Last.fm's.
    fun familyFor(t: TrackText, style: String?): String? =
        MusicStyle.familyOf(style) ?: MusicStyle.fromGenre(GenreLane.lookupGenre(genres, t.primary)).family
            ?: ArtistStyleMemory.key(t.primary)?.let { tagLanes[it]?.family }

    // POSITIVE evidence that a track is Christian (its words, an iTunes «Christian & Gospel», Last.fm tags).
    fun christianEvidence(t: TrackText): Boolean =
        GenreLane.laneOfTrack(genres, t.primary, t.text, null) == GenreLane.CHRISTIAN ||
            ArtistStyleMemory.key(t.primary)?.let { christianTags[it] } == true

    val detected: StyleContinuity.Target
    val ownArtists: Set<String>
    val trackFamilies: List<String?>
    val seedArtists: List<Pair<String, String?>>
    var laneMixed = false
    if (collection && pool.isNotEmpty()) {
        val nameStyle = MusicStyle.fromText(radioSeedTitle)
        val christianTracks = pool.count { mm ->
            GenreLane.laneOfTrack(genres, mm.artists.firstOrNull()?.name, mm.title, mm.album?.title) == GenreLane.CHRISTIAN
        }
        val trackStyles = poolTexts.map { styleOf(it) }
        // Row 459: a SHORT collection (a single, an EP) has fewer than the 3 known styles / 4 decided titles the
        // collection rules need, so its target used to be empty; the language also reads what is learned of the artists.
        val votes = poolTexts.map { languageEvidence(it) } +
            ContextLane.candidateLanguage(ContextLane.languageOfText(radioSeedTitle), null, spanishHint = false)
        val contextLanguage = ContextLane.contextLanguage(votes, ContextLane.MIN_DECIDED_COLLECTION)
        val titleLanguage = collectionLanguage()
        detected = StyleContinuity.Target(
            styles = StyleContinuity.collectionStyles(trackStyles, nameStyle) ?: ContextLane.fewKnownStyles(trackStyles),
            language = titleLanguage ?: contextLanguage.code,
            christian = christianTracks * 2 >= pool.size || GenreLane.laneOf(radioSeedTitle) == GenreLane.CHRISTIAN,
        )
        laneMixed = titleLanguage == null && contextLanguage.mixed
        trackFamilies = poolTexts.mapIndexed { i, t -> familyFor(t, trackStyles[i]) }
        ownArtists = poolTexts.flatMap { it.artists }.map { it.trim().lowercase() }.toHashSet()
        seedArtists = pool.mapNotNull { it.artists.firstOrNull() }
            .filter { !it.id.isNullOrBlank() }
            .groupBy { it.name.trim().lowercase() }
            .entries.sortedByDescending { it.value.size }
            .take(LANE_FILL_SEED_ARTISTS)
            .map { it.value.first().name to it.value.first().id }
    } else if (anchor != null) {
        val t = anchor.trackText()
        val contextLanguage = ContextLane.contextLanguage(listOf(languageEvidence(t)), minDecided = 1)
        detected = StyleContinuity.Target(
            styles = styleOf(t)?.let { setOf(it) },
            language = contextLanguage.code,
            christian = GenreLane.laneOfTrack(genres, t.primary, anchor.title, anchor.album?.title) == GenreLane.CHRISTIAN,
        )
        trackFamilies = listOf(familyFor(t, styleOf(t)))
        ownArtists = t.artists.map { it.trim().lowercase() }.toHashSet()
        seedArtists = anchor.artists.filter { !it.id.isNullOrBlank() }.take(LANE_FILL_SEED_ARTISTS).map { it.name to it.id }
    } else {
        return@withContext null
    }
    // Fila 361: a "Pedir música" queue continues what was ASKED (styles, language, faith), not a guess.
    val requested = iad1tya.echo.music.reco.RequestStyleTargets.get(radioOriginContextId)
    val base = requested ?: detected
    val override = st.override
    val target = StyleContinuity.applyOverride(base, override)
    st.follow.value = StyleContinuity.Follow(
        detected = base.styles.orEmpty().sorted(),
        applied = target.styles.orEmpty().sorted(),
        christian = target.christian,
        language = target.language,
        override = override,
    )
    // Row 459 — relaxation level 2 of the engine: the style stands down, the LANGUAGE still holds.
    // Row 503: ...unless the context is Christian — then the faith alone is still worth enforcing.
    if (languageOnly && target.language == null && !target.christian) return@withContext null
    val gateTarget = if (languageOnly) StyleContinuity.Target(null, target.language, target.christian) else target
    // Row 459 — the lane: the language (null = unknown or MIXED: nothing judged), the faith and, when the concrete
    // style is unknown, the genre FAMILY (a user's «cualquier estilo» or a request's own target is never narrowed).
    // Row 503 (owner 2026-10-29: «terminó Luis Miguel, siguió con cantantes de la misma época… y de la nada cumbia»):
    // the relaxation levels used to drop the style AND the family, leaving only the language — and every Spanish song
    // (a cumbia included) is Spanish. The family fence now survives the first relaxation level (the families of the
    // styles and their neighbours), and the normal levels fence a context whose styles are known too, with what is
    // KNOWN of the candidate (its iTunes genre or Last.fm tags); a candidate nothing is known about stays eligible.
    val baseFamilies = when {
        override != null -> emptySet<String>()
        languageOnly -> if (dropFamilies) emptySet() else {
            target.neighbourFamilies.ifEmpty { ContextLane.familyHint(null, trackFamilies) }
        }
        requested != null -> emptySet<String>()
        else -> ContextLane.familyHint(target.styles, trackFamilies).ifEmpty { target.neighbourFamilies }
    }
    // Row 527 (owner 2026-10-29: «después de la salsa me puso rap»): a Christian salsa album has NO style and NO family the
    // tags can name (iTunes says «Christian & Gospel»), so the lane asked only for Spanish + faith and every Spanish Christian
    // song — a rap included — was a match. When the context itself says nothing, what the queue's OWN first candidates
    // say is the best hint left: if most of the batch's known songs belong to one family, that family leads. It only ever
    // narrows (a candidate nothing is known about stays eligible) and never overrides what the context or the user decided.
    fun consensusFamilies(batch: List<MediaItem>): Set<String> {
        val known = batch.take(CONSENSUS_WINDOW).mapNotNull { mi ->
            val t = mi.trackText()
            familyFor(t, styleOf(t))
        }
        return ContextLane.consensusFamilies(known, CONSENSUS_MIN_KNOWN, CONSENSUS_SHARE)
    }
    val inferredFamilies = if (baseFamilies.isEmpty() && override == null && requested == null && !languageOnly &&
        target.styles.isNullOrEmpty()
    ) consensusFamilies(items) else emptySet()
    if (inferredFamilies.isNotEmpty()) {
        Timber.tag(TAG).i("STYLE_GATE %s: no style known — the batch's own family leads: families=%s", site, inferredFamilies.sorted().joinToString("+"))
    }
    val families = baseFamilies.ifEmpty { inferredFamilies }
    val lane = ContextLane.Lane(
        language = gateTarget.language,
        mixed = laneMixed && requested == null,
        christian = gateTarget.christian,
        families = families,
    )
    if (!gateTarget.active && lane.families.isEmpty() && !lane.christian) {
        Timber.tag(TAG).i("STYLE_GATE %s: style and language unknown, no filter applied", site)
        return@withContext null
    }

    // Row 435: a request for YEARS ("del 2004 a 2010") is kept by the queue that continues it — the album years of this
    // batch are asked once, bounded, before judging (an album that never answers simply has no year).
    if (gateTarget.era != null) {
        iad1tya.echo.music.playlistimport.MusicRequestEraYears.prefetch(
            items.mapNotNull { it.metadata?.album?.id }, ERA_PREFETCH_MS,
        )
    }

    val basisById = HashMap<String, String>()

    fun judge(mi: MediaItem, fromSearch: Boolean, faithSource: Boolean = false): Judged {
        val t = mi.trackText()
        val textStyle = MusicStyle.fromText(t.text)
        val learned = artistStyle(t.primary)
        val genre = MusicStyle.fromGenre(GenreLane.lookupGenre(genres, t.primary))
        val known = textStyle ?: learned ?: genre.style
        val rejected = ArtistStyleMemory.key(t.primary)?.let { it in st.skippedArtists } == true ||
            (ArtistStyleMemory.key(t.primary)?.let { it in persistedRejected } == true &&
                !t.artists.any { it.trim().lowercase() in ownArtists }) ||
            (!languageOnly && known != null && known !in target.styles.orEmpty() && known !in st.confirmedStyles &&
                (st.skippedStyles[known] ?: 0) >= STYLE_SKIPS_TO_REJECT)
        val evidence = languageEvidence(t)
        val verdict = when {
            rejected -> StyleContinuity.Verdict.OFF
            // Only the family lane is active (no style, no language): nothing to say about the style itself.
            !gateTarget.active -> StyleContinuity.Verdict.UNKNOWN
            else -> StyleContinuity.verdict(
                gateTarget,
                StyleContinuity.Candidate(
                    textStyle = textStyle,
                    learnedStyle = learned,
                    genre = genre,
                    // Only STRONG evidence may call a candidate «another language»; a hint never does.
                    language = evidence.code.takeIf { evidence.strong },
                    ownArtist = t.artists.any { it.trim().lowercase() in ownArtists },
                    fromSearch = fromSearch,
                    rejected = rejected,
                    year = if (gateTarget.era == null) null else iad1tya.echo.music.playlistimport.MusicRequestEraYears.yearOf(
                        mi.mediaId, mi.metadata?.album?.id, mi.metadata?.album?.title,
                    ),
                ),
            )
        }
        // Row 503 — faith is a GATE in a Christian lane, so what PROVES it must be generous where it is sure: the context's
        // own artists, and the songs a Christian search ("rap cristiano en español", "música cristiana") brought.
        val ownArtist = t.artists.any { it.trim().lowercase() in ownArtists }
        val christian = christianEvidence(t) || (lane.christian && (ownArtist || faithSource))
        // Row 524: remember what proved this candidate, for the summary line (counts only).
        basisById[mi.mediaId] = StyleContinuity.basis(
            gateTarget,
            StyleContinuity.Candidate(
                textStyle = textStyle,
                learnedStyle = learned,
                genre = genre,
                language = null,
                ownArtist = ownArtist,
                fromSearch = fromSearch,
                rejected = rejected,
            ),
        )
        return Judged(verdict, ContextLane.Candidate(verdict, evidence, familyFor(t, known), christian))
    }

    // Fits the fill may add to: a related artist the context already knows to be elsewhere is not even asked for.
    fun relatedOk(name: String): Boolean {
        val key = ArtistStyleMemory.key(name) ?: return false
        if (key in st.skippedArtists || key in persistedRejected) return false
        val language = ArtistLanguageMemory.languageOf(langMemory, name)
        if (lane.language != null && language != null && language != lane.language) return false
        val style = artistStyle(name)
        val family = tagLanes[key]?.family ?: MusicStyle.familyOf(style)
        if (lane.families.isNotEmpty() && family != null && family !in lane.families) return false
        val styles = gateTarget.styles
        if (!styles.isNullOrEmpty() && style != null && style !in styles) return false
        return true
    }

    val all = ArrayList(items)
    val judged = ArrayList(items.map { judge(it, fromSearch = false) })
    var fetched = 0
    // Step 0 = the style (or family) AND the language proven. A candidate whose language is merely unproven (step 1)
    // is eligible but does not stop the lane fill: it is what an English song with a bare title looks like too.
    fun exactCount(): Int = judged.count { j -> ContextLane.tier(lane, j.candidate) == 0 }
    // Row 503: a Christian context may still SEARCH at the relaxed levels (it asks for Christian songs, never for
    // anything else), so the faith gate never starves the queue.
    if (allowSearch && (!languageOnly || lane.christian) && exactCount() < minMatches) {
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
        // Same filters for every source of extra songs: the user's own settings, then the smart queue's no-repeat
        // (this session, the persistent «already served after this list» memory, unavailable songs).
        suspend fun admit(
            found: List<MediaItem>,
            fromSearch: Boolean,
            impliedStyle: String?,
            faithSource: Boolean = fromSearch && gateTarget.christian,
        ): Int {
            val playable = found
                .filterExplicit(hideExplicit)
                .filterVideoSongs(hideVideos)
                .filterNonMusicForAutoQueue()
            if (gateTarget.era != null) {
                iad1tya.echo.music.playlistimport.MusicRequestEraYears.prefetch(
                    playable.mapNotNull { it.metadata?.album?.id }, ERA_PREFETCH_MS,
                )
            }
            learnFrom(playable.map { it.trackText() }, impliedStyle = impliedStyle)
            val fresh = playable.filter { mi ->
                seen.add(mi.mediaId) &&
                    mi.mediaId !in sessionPlayedIds && mi.dedupKeyOrNull() !in sessionPlayedDedupKeys &&
                    mi.mediaId !in playedBefore &&
                    !iad1tya.echo.music.utils.UnavailableSongs.isUnavailable(mi.mediaId)
            }
            fresh.forEach { mi ->
                all.add(mi)
                judged.add(judge(mi, fromSearch, faithSource))
            }
            return fresh.size
        }

        val styles = gateTarget.styles
        val style = when {
            styles.isNullOrEmpty() -> null
            styles.size == 1 -> styles.first()
            else -> styles.random(randomSeedSource)
        }
        val query = StyleContinuity.searchQuery(style, gateTarget.language, gateTarget.christian)
            ?: if (gateTarget.christian) StyleContinuity.christianQuery(gateTarget.language) else null
        if (query != null) {
            repeat(FILL_PAGES_PER_CALL) {
                if (exactCount() >= minMatches) return@repeat
                fetched += admit(styleFillPage(query), fromSearch = true, impliedStyle = style)
            }
        }
        // Row 459 — still short (and, when the style is unknown, this is the ONLY source: «sin estilo no hay consulta
        // de relleno», row 440 b): the context's own artists and the artists next to them, before any weaker step.
        if (exactCount() < minMatches && lane.extra) {
            fetched += admit(laneFillPage(seedArtists) { relatedOk(it) }, fromSearch = false, impliedStyle = null)
        }
    }
    val tiers = judged.map { ContextLane.tier(lane, it.candidate) }
    // With the second-to-last relaxation (languageOnly) every non-excluded candidate is welcome, best step first.
    val enough = if (languageOnly) Int.MAX_VALUE else minOf(minMatches, StyleContinuity.ENOUGH_TO_DROP_UNKNOWN)
    val kept = ContextLane.choose(all, tiers, judged.map { lane.christian && it.candidate.christian }, enough)
    if (st.gatedIds.size > GATED_IDS_CAP) st.gatedIds.clear()
    kept.forEach { st.gatedIds.add(it.mediaId) }
    // Counts and style/family ids only — never a title, artist or id (AGENTS.md rule 4).
    Timber.tag(TAG).i("STYLE_GATE_BASIS %s: kept by %s", site, kept.groupingBy { basisById[it.mediaId] ?: "?" }.eachCount().entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }.ifEmpty { "nothing" })
    Timber.tag(TAG).i(
        "STYLE_GATE %s: styles=%s lang=%s christian=%b override=%s match=%d unknown=%d off=%d fetched=%d kept=%d " +
            "mixed=%b families=%s steps=%s never=%d only=%b",
        site,
        gateTarget.styles?.sorted()?.joinToString("+") ?: "unknown",
        gateTarget.language ?: "unknown",
        gateTarget.christian,
        override?.let { it::class.simpleName } ?: "none",
        judged.count { it.verdict == StyleContinuity.Verdict.MATCH },
        judged.count { it.verdict == StyleContinuity.Verdict.UNKNOWN },
        judged.count { it.verdict == StyleContinuity.Verdict.OFF },
        fetched,
        kept.size,
        lane.mixed,
        lane.families.sorted().joinToString("+").ifEmpty { "none" },
        (0..ContextLane.MAX_TIER).joinToString("/") { step -> tiers.count { it == step }.toString() },
        tiers.count { it == null },
        languageOnly,
    )
    kept
}

/**
 * Row 459 — songs of the context's OWN artists and of the artists YouTube lists next to them (the «Fans might also
 * like» shelf of their page), for the lane when the exact steps are short and the style gives no search to run.
 * Bounded twice: at most [LANE_FILL_REQUESTS_PER_CALL] artist pages per call and [LANE_FILL_REQUEST_BUDGET] per queue
 * ([ExactStyleState.laneFillRequests]); what a page brings is kept in [ExactStyleState.laneFillPool] and drawn from
 * one batch after another, so a long session re-reads nothing. The songs are judged like any other candidate
 * afterwards (language, family, no-repeat); [relatedOk] only spares a request for an artist the app already knows
 * to be in another language / style.
 */
private suspend fun MusicService.laneFillPage(
    seedArtists: List<Pair<String, String?>>,
    relatedOk: (String) -> Boolean,
): List<MediaItem> {
    val st = exactStyle
    if (st.laneFillPool.isEmpty()) {
        var spent = 0
        suspend fun page(browseId: String): com.music.innertube.pages.ArtistPage? {
            if (st.laneFillRequests >= LANE_FILL_REQUEST_BUDGET || spent >= LANE_FILL_REQUESTS_PER_CALL) return null
            if (!st.laneFillFetched.add(browseId)) return null
            st.laneFillRequests++
            spent++
            return YouTube.artist(browseId).getOrNull()
        }
        fun com.music.innertube.pages.ArtistPage.songs(): List<SongItem> =
            sections.firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is SongItem } }
                ?.items?.filterIsInstance<SongItem>()?.take(LANE_FILL_SONGS_PER_ARTIST).orEmpty()
        val related = ArrayList<ArtistItem>()
        for ((_, id) in seedArtists) {
            val browseId = id?.takeIf { it.startsWith("UC") } ?: continue
            val artistPage = page(browseId) ?: continue
            artistPage.songs().forEach { st.laneFillPool.add(it.toMediaItem()) }
            artistPage.sections.firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is ArtistItem } }
                ?.items?.filterIsInstance<ArtistItem>()?.let { related.addAll(it) }
        }
        val neighbours = related
            .filter { it.id.startsWith("UC") && relatedOk(it.title) }
            .distinctBy { it.id }
            .take(LANE_FILL_RELATED_ARTISTS)
        for (artist in neighbours) {
            val artistPage = page(artist.id) ?: break
            artistPage.songs().forEach { st.laneFillPool.add(it.toMediaItem()) }
        }
    }
    val out = ArrayList<MediaItem>()
    while (out.size < LANE_FILL_TAKE) out.add(st.laneFillPool.poll() ?: break)
    return out
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
        if (songs.isNotEmpty()) {
            return StyleContinuity.contiguousRun(songs, StyleContinuity.FILL_RUN, randomSeedSource.nextInt())
                .map { it.toMediaItem() }
        }
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
    // The smart queue's ordering memory ([SessionSkipMemory]) learns from EVERY song, not only the gated ones: a quick
    // skip counts against its artist, a song heard to the end forgives it. No I/O.
    if (previous != null && previous.id != newItem?.mediaId) {
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> if ((now - startedAt) in 0L until QUICK_SKIP_MS) {
                st.quickSkips.record(previous.artists.firstOrNull()?.name)
            }
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> st.quickSkips.forgive(previous.artists.firstOrNull()?.name)
        }
    }
    if (previous == null || previous.id == newItem?.mediaId || previous.id !in st.gatedIds) return
    val t = previous.trackText()
    val ownStyle = MusicStyle.fromText(t.text)
    when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> if ((now - startedAt) in 0L until QUICK_SKIP_MS) {
            ArtistStyleMemory.key(t.primary)?.let { st.skippedArtists.add(it) }
            // Row 525: and for the next queues too (only songs the smart queue added reach this branch).
            val skippedKey = ArtistStyleMemory.key(t.primary)
            scope.launch(Dispatchers.IO) { iad1tya.echo.music.reco.ContinuationSkipMemory.recordSkip(this@exactStyleOnTransition, skippedKey) }
            ownStyle?.let { st.skippedStyles.merge(it, 1, Int::plus) }
            Timber.tag(TAG).i(
                "STYLE_GATE quick skip learned: artists=%d styles=%d",
                st.skippedArtists.size, st.skippedStyles.size,
            )
        }
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
            ownStyle?.let { st.confirmedStyles.add(it) }
            val heardKey = ArtistStyleMemory.key(t.primary)
            scope.launch(Dispatchers.IO) { iad1tya.echo.music.reco.ContinuationSkipMemory.forgive(this@exactStyleOnTransition, heardKey) }
        }
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
