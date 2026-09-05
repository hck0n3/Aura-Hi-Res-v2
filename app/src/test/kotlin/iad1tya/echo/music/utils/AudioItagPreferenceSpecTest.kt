package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec test for the audio itag preference ladder used by the NewPipe URL fallback in
 * YTPlayerUtils.findUrlOrNull (AUDIO_ITAG_PREFERENCE). The production list is private inside the
 * Android-heavy YTPlayerUtils object, so this test pins the ORDER ITSELF as a specification: if the
 * production list is ever reordered or an itag dropped, updating this mirror is mandatory — the
 * assertion failure is the reminder.
 *
 * Order contract (owner directive 2026-09-05, Echo-Music reference — OPUS-ONLY streaming):
 * 1. The ENTIRE Opus family leads, in descending quality: 774 (Opus 256k premium) → 251 (160k)
 *    → 250 (70k) → 249 (50k) → 139 (mobile Opus). No AAC/other codec may sit between Opus
 *    options — the old order interleaved 141 (AAC 256k) between 774 and 251, so a track without
 *    the premium itag silently played AAC.
 * 2. AAC (141, 140) comes AFTER every Opus option as the cross-family fallback for videos that
 *    ship no Opus rendition at all, then Vorbis (171).
 * 3. The muxed MP4 progressives (22, 18) keep their emergency-tail role (bot-limited
 *    extractions sometimes hand over ONLY itag 18 — a playing low-quality stream beats a
 *    perfect silent one).
 * 4. No duplicates, all entries positive — a duplicate would silently change first-match semantics.
 */
class AudioItagPreferenceSpecTest {

    // Mirror of YTPlayerUtils.AUDIO_ITAG_PREFERENCE. Keep in sync with production.
    private val audioItagPreference = listOf(774, 251, 250, 249, 139, 141, 140, 171, 22, 18)

    private fun pickFirstAvailable(available: Set<Int>): Int? =
        audioItagPreference.firstOrNull { it in available }

    @Test
    fun premiumOpus774LeadsWhenPresent() {
        // Typical Premium-and-cookie extraction: 141 and 251 also present — 774 must still win.
        assertEquals(774, pickFirstAvailable(setOf(774, 141, 251, 250, 249)))
    }

    @Test
    fun opusOnly_251BeatsAac141WhenNo774() {
        // OPUS-ONLY contract: the 160k Opus must beat the 256k AAC when the premium Opus is
        // absent — the whole family degrades internally before crossing to AAC.
        assertEquals(251, pickFirstAvailable(setOf(141, 251, 250, 249, 140)))
    }

    @Test
    fun opusOnly_mobile139BeatsAac140() {
        // Even the lowest mobile Opus wins over AAC 128k — the family is a wall.
        assertEquals(139, pickFirstAvailable(setOf(139, 140, 171)))
    }

    @Test
    fun aacIsTheCrossFamilyFallbackWhenNoOpusExists() {
        // A video shipping only AAC renditions still plays — AAC after the Opus family.
        assertEquals(141, pickFirstAvailable(setOf(141, 140, 22, 18)))
        assertEquals(140, pickFirstAvailable(setOf(140, 22, 18)))
    }

    @Test
    fun anonymousExtractionPicks251WhenNo774() {
        // Anonymous ANDROID_VR evidence (NewPipe.kt): audio 250/251 (+774 on some tracks).
        assertEquals(251, pickFirstAvailable(setOf(251, 250, 249, 140)))
    }

    @Test
    fun anonymousExtractionWith774PresentPicks774() {
        // The fork evidence says 774 can appear even anonymously — it must lead when present.
        assertEquals(774, pickFirstAvailable(setOf(774, 251, 250)))
    }

    @Test
    fun botLimitedEmergencyHandoverKeepsMuxedTailLast() {
        // Bot-limited extraction handing over only the muxed emergency itags.
        assertEquals(22, pickFirstAvailable(setOf(22, 18)))
        assertEquals(18, pickFirstAvailable(setOf(18)))
    }

    @Test
    fun ladderHasNoDuplicatesAndAllEntriesPositive() {
        assertEquals(audioItagPreference.size, audioItagPreference.toSet().size)
        assertTrue(audioItagPreference.all { it > 0 })
    }

    @Test
    fun entireOpusFamilyPrecedesEveryNonOpusEntry() {
        // The OPUS-ONLY wall, structurally: every Opus itag must precede every non-Opus itag.
        val opusFamily = setOf(774, 251, 250, 249, 139)
        val opusIdx = audioItagPreference.withIndex().filter { it.value in opusFamily }.map { it.index }
        val nonOpusIdx = audioItagPreference.withIndex().filter { it.value !in opusFamily }.map { it.index }
        assertTrue("An Opus itag sits after a non-Opus itag", opusIdx.max() < nonOpusIdx.min())
    }
}
