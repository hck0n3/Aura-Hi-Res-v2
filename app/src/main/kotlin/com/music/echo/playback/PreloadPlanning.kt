package iad1tya.echo.music.playback

/**
 * Pure decision core for the upcoming-track preload extracted verbatim from
 * [MusicService.preloadUpcomingItems] (HALLAZGO-021 split, phase B).
 *
 * The preload resolves the stream URLs (and optionally the loudness/format row and the lyrics) of the
 * next few queue items BEFORE they start playing, so a transition never pays a network resolve. What
 * gets preloaded is gated by the user's slider, Data Saver (skip everything), battery saver and
 * High-Performance Mode (both degrade to a url-only next-track prefetch — crossfade still needs the
 * URL armed or an Android Auto hard cut goes silent, owner share_log-3). These rules used to live
 * inline in the service; they are the part of the preload that can be characterized without a device.
 */
object PreloadPlanning {

    /** Hard cap on how far ahead the lookahead collects ids, above the user's slider limit. */
    const val MAX_LOOKAHEAD = 10

    /**
     * One planned preload action. [resolveUrl] and [preloadLyrics] are independent: a song whose URL is
     * already cached this session still gets its lyrics preloaded, and a local song never resolves a
     * URL but can still preload lyrics. A fully-downloaded song is not planned at all — it plays
     * straight from the download cache, and resolving it could poison the URL cache with a
     * wrong-container URL (the #57 mechanism).
     */
    data class ItemPlan(
        val mediaId: String,
        val resolveUrl: Boolean,
        val preloadLyrics: Boolean,
    )

    /**
     * How many items AFTER [currentIndex] the lookahead collects: capped at [MAX_LOOKAHEAD] and never
     * negative at the end of the queue. The real limit (the user's slider, or 1 under battery saver)
     * is applied later by [planItems].
     */
    fun upcomingLookahead(mediaItemCount: Int, currentIndex: Int): Int =
        kotlin.math.min(MAX_LOOKAHEAD, mediaItemCount - currentIndex - 1)

    /** Battery saver preloads only the single next URL; otherwise the user's configured limit stands. */
    fun effectiveLimit(powerSave: Boolean, configuredLimit: Int): Int =
        if (powerSave) 1 else configuredLimit

    /** Battery saver and High-Performance Mode both degrade the preload to url-only (no extras). */
    fun isUrlOnlyPreload(powerSave: Boolean, performanceMode: Boolean): Boolean =
        powerSave || performanceMode

    /**
     * The per-item plan. Mirrors the original pipeline EXACTLY: `take(limit)` happens BEFORE
     * `distinct()` — with limit 2 and ids [a, a, b] only [a] is preloaded, never [a, b] — and a
     * fully-downloaded id drops out entirely (no URL resolve AND no lyrics).
     *
     * NOTE: do NOT also skip on a player-cache hit here. The URL cache is in-memory (empty on a fresh
     * process), so skipping a player-cache-cached song would leave the resolver later with a cached
     * flag but no URL, taking the "Ghost cache entry" path that DELETES the cached bytes and
     * re-downloads — destroying cross-session cache.
     */
    fun planItems(
        upcoming: List<String>,
        limit: Int,
        lyricsEnabled: Boolean,
        urlOnly: Boolean,
        isLocalMediaId: (String) -> Boolean,
        isFullyDownloaded: (String) -> Boolean,
        hasCachedUrl: (String) -> Boolean,
    ): List<ItemPlan> = upcoming
        .take(limit)
        .distinct()
        .mapNotNull { mediaId ->
            if (isFullyDownloaded(mediaId)) return@mapNotNull null
            ItemPlan(
                mediaId = mediaId,
                resolveUrl = !isLocalMediaId(mediaId) && !hasCachedUrl(mediaId),
                preloadLyrics = lyricsEnabled && !urlOnly,
            )
        }

    /**
     * How a resolved track's loudness merges with the format row already in the database, extracted
     * from the preload's FIX A block: the resolved value wins, the existing row fills whatever the
     * resolve did not report, and the measured value is ONLY ever the persisted one (the preload never
     * measures). [cacheHint] says whether the merged result is worth mirroring into the in-memory
     * loudness hint cache (so a crossfade INTO this track can pre-level it with no disk read);
     * [persistRow] says whether the row must be written — only when the database has NO loudness for
     * this song yet, so a known loudness is never overwritten with null.
     */
    data class LoudnessMerge(
        val loudnessDb: Double?,
        val perceptualLoudnessDb: Double?,
        val measuredLoudnessDb: Double?,
        val cacheHint: Boolean,
        val persistRow: Boolean,
    )

    fun mergeLoudness(
        resolvedLoudnessDb: Double?,
        resolvedPerceptualLoudnessDb: Double?,
        existingLoudnessDb: Double?,
        existingPerceptualLoudnessDb: Double?,
        existingMeasuredLoudnessDb: Double?,
    ): LoudnessMerge {
        val loudnessDb = resolvedLoudnessDb ?: existingLoudnessDb
        val perceptualLoudnessDb = resolvedPerceptualLoudnessDb ?: existingPerceptualLoudnessDb
        val measuredLoudnessDb = existingMeasuredLoudnessDb
        return LoudnessMerge(
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb,
            measuredLoudnessDb = measuredLoudnessDb,
            cacheHint = loudnessDb != null || perceptualLoudnessDb != null || measuredLoudnessDb != null,
            persistRow = existingLoudnessDb == null && existingPerceptualLoudnessDb == null,
        )
    }
}
