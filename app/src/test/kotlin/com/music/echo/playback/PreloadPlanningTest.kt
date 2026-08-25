package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the upcoming-track preload decisions extracted verbatim from
 * MusicService.preloadUpcomingItems into PreloadPlanning (HALLAZGO-021 split, phase B).
 *
 * Pinned facts: the lookahead is capped at 10 and clamps to zero at the queue's end; battery saver
 * forces limit 1; url-only mode is powerSave OR performanceMode; `take(limit)` happens BEFORE
 * `distinct()` (limit 2 over [a, a, b] preloads ONLY [a]); a fully-downloaded id drops out entirely
 * (no URL AND no lyrics); local ids and already-cached URLs never resolve but still preload lyrics;
 * and the loudness merge keeps the resolved value first, fills gaps from the existing row, takes the
 * measured value ONLY from the database, and persists only while the database has no loudness yet.
 */
class PreloadPlanningTest {

    // ------------------------------------------------------------------ lookahead

    @Test
    fun lookaheadIsCappedAtTen() {
        assertEquals(10, PreloadPlanning.upcomingLookahead(mediaItemCount = 50, currentIndex = 0))
    }

    @Test
    fun lookaheadShrinksNearQueueEnd() {
        assertEquals(2, PreloadPlanning.upcomingLookahead(mediaItemCount = 4, currentIndex = 1))
        assertEquals(0, PreloadPlanning.upcomingLookahead(mediaItemCount = 1, currentIndex = 0))
        assertEquals(0, PreloadPlanning.upcomingLookahead(mediaItemCount = 5, currentIndex = 4))
    }

    // ------------------------------------------------------------------ gates

    @Test
    fun batterySaverForcesLimitOne() {
        assertEquals(1, PreloadPlanning.effectiveLimit(powerSave = true, configuredLimit = 7))
    }

    @Test
    fun normalModeKeepsConfiguredLimit() {
        assertEquals(7, PreloadPlanning.effectiveLimit(powerSave = false, configuredLimit = 7))
        assertEquals(2, PreloadPlanning.effectiveLimit(powerSave = false, configuredLimit = 2))
    }

    @Test
    fun urlOnlyModeTruthTable() {
        assertTrue(PreloadPlanning.isUrlOnlyPreload(powerSave = true, performanceMode = true))
        assertTrue(PreloadPlanning.isUrlOnlyPreload(powerSave = true, performanceMode = false))
        assertTrue(PreloadPlanning.isUrlOnlyPreload(powerSave = false, performanceMode = true))
        assertFalse(PreloadPlanning.isUrlOnlyPreload(powerSave = false, performanceMode = false))
    }

    // ------------------------------------------------------------------ planItems

    private fun plan(
        upcoming: List<String>,
        limit: Int = 10,
        lyricsEnabled: Boolean = true,
        urlOnly: Boolean = false,
        local: Set<String> = emptySet(),
        downloaded: Set<String> = emptySet(),
        cachedUrl: Set<String> = emptySet(),
    ): List<PreloadPlanning.ItemPlan> = PreloadPlanning.planItems(
        upcoming = upcoming,
        limit = limit,
        lyricsEnabled = lyricsEnabled,
        urlOnly = urlOnly,
        isLocalMediaId = { it in local },
        isFullyDownloaded = { it in downloaded },
        hasCachedUrl = { it in cachedUrl },
    )

    @Test
    fun takeHappensBeforeDistinct() {
        // limit 2 over [a, a, b]: take(2) = [a, a], distinct = [a]. b is NOT reached — pinned.
        val planned = plan(upcoming = listOf("a", "a", "b"), limit = 2)
        assertEquals(listOf("a"), planned.map { it.mediaId })
    }

    @Test
    fun distinctKeepsFirstOccurrenceOrder() {
        val planned = plan(upcoming = listOf("b", "a", "b", "c"), limit = 10)
        assertEquals(listOf("b", "a", "c"), planned.map { it.mediaId })
    }

    @Test
    fun zeroLimitPlansNothing() {
        // The slider's range is 1..10 (PlayerSettings) and battery saver forces 1, so a negative
        // limit is unreachable in the service; 0 is the function's natural boundary.
        assertEquals(emptyList<PreloadPlanning.ItemPlan>(), plan(upcoming = listOf("a", "b"), limit = 0))
    }

    @Test
    fun downloadedIdDropsOutEntirely() {
        val planned = plan(upcoming = listOf("dl", "next"), downloaded = setOf("dl"))
        assertEquals(listOf("next"), planned.map { it.mediaId })
    }

    @Test
    fun localIdNeverResolvesButStillPreloadsLyrics() {
        val planned = plan(upcoming = listOf("local1"), local = setOf("local1"))
        assertEquals(1, planned.size)
        assertFalse(planned[0].resolveUrl)
        assertTrue(planned[0].preloadLyrics)
    }

    @Test
    fun cachedUrlSkipsResolveButKeepsLyrics() {
        val planned = plan(upcoming = listOf("cached"), cachedUrl = setOf("cached"))
        assertEquals(1, planned.size)
        assertFalse(planned[0].resolveUrl)
        assertTrue(planned[0].preloadLyrics)
    }

    @Test
    fun plainUpcomingIdResolvesAndPreloadsLyrics() {
        val planned = plan(upcoming = listOf("next"))
        assertEquals(
            PreloadPlanning.ItemPlan(mediaId = "next", resolveUrl = true, preloadLyrics = true),
            planned[0],
        )
    }

    @Test
    fun lyricsToggleAndUrlOnlyModeGateLyrics() {
        assertFalse(plan(upcoming = listOf("a"), lyricsEnabled = false)[0].preloadLyrics)
        assertFalse(plan(upcoming = listOf("a"), urlOnly = true)[0].preloadLyrics)
        // url-only never stops the URL resolve itself.
        assertTrue(plan(upcoming = listOf("a"), urlOnly = true)[0].resolveUrl)
    }

    // ------------------------------------------------------------------ mergeLoudness

    @Test
    fun resolvedLoudnessWinsOverExisting() {
        val merge = PreloadPlanning.mergeLoudness(
            resolvedLoudnessDb = -7.0,
            resolvedPerceptualLoudnessDb = -8.0,
            existingLoudnessDb = -12.0,
            existingPerceptualLoudnessDb = -13.0,
            existingMeasuredLoudnessDb = -9.5,
        )
        assertEquals(-7.0, merge.loudnessDb!!, 0.0)
        assertEquals(-8.0, merge.perceptualLoudnessDb!!, 0.0)
    }

    @Test
    fun existingRowFillsGapsLeftByResolve() {
        val merge = PreloadPlanning.mergeLoudness(
            resolvedLoudnessDb = null,
            resolvedPerceptualLoudnessDb = -8.0,
            existingLoudnessDb = -12.0,
            existingPerceptualLoudnessDb = null,
            existingMeasuredLoudnessDb = null,
        )
        assertEquals(-12.0, merge.loudnessDb!!, 0.0)
        assertEquals(-8.0, merge.perceptualLoudnessDb!!, 0.0)
    }

    @Test
    fun measuredLoudnessOnlyEverComesFromDatabase() {
        val merge = PreloadPlanning.mergeLoudness(
            resolvedLoudnessDb = -7.0,
            resolvedPerceptualLoudnessDb = null,
            existingLoudnessDb = null,
            existingPerceptualLoudnessDb = null,
            existingMeasuredLoudnessDb = -9.5,
        )
        assertEquals(-9.5, merge.measuredLoudnessDb!!, 0.0)

        val none = PreloadPlanning.mergeLoudness(
            resolvedLoudnessDb = -7.0,
            resolvedPerceptualLoudnessDb = -8.0,
            existingLoudnessDb = null,
            existingPerceptualLoudnessDb = null,
            existingMeasuredLoudnessDb = null,
        )
        assertEquals(null, none.measuredLoudnessDb)
    }

    @Test
    fun cacheHintSetWhenAnyMergedValuePresent() {
        assertTrue(
            PreloadPlanning.mergeLoudness(-7.0, null, null, null, null).cacheHint,
        )
        assertTrue(
            PreloadPlanning.mergeLoudness(null, null, null, null, -9.5).cacheHint,
        )
        assertFalse(
            PreloadPlanning.mergeLoudness(null, null, null, null, null).cacheHint,
        )
    }

    @Test
    fun persistOnlyWhileDatabaseHasNoLoudness() {
        // No row yet (all null) -> persist, even when the resolve itself brought no loudness: the row
        // still carries the format columns + playback URL. Pinned.
        assertTrue(PreloadPlanning.mergeLoudness(null, null, null, null, null).persistRow)
        assertTrue(PreloadPlanning.mergeLoudness(-7.0, null, null, null, null).persistRow)
        // Existing loudness (either column) -> never overwrite.
        assertFalse(PreloadPlanning.mergeLoudness(-7.0, null, -12.0, null, null).persistRow)
        assertFalse(PreloadPlanning.mergeLoudness(null, -8.0, null, -13.0, null).persistRow)
    }
}
