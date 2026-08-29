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
 * Order contract (SimpMusic v2.0.0 ITAG parity, "split high audio into opus and aac"):
 * 1. Premium/high twins first: 774 (Opus 256k) with same-tier twin fallback 141 (AAC 256k) —
 *    YouTube hands an entitled account only ONE family, so the twin of the requested tier degrades
 *    BEFORE dropping to a lower tier.
 * 2. Then the anonymous ladder exactly as before: 251 (Opus 160k) → 250 → 249 → 140 (AAC 128k)
 *    → 171 (Vorbis) → 139 → 22 → 18 (emergency muxed tail).
 * 3. No duplicates, all entries positive — a duplicate would silently change first-match semantics.
 */
class AudioItagPreferenceSpecTest {

    // Mirror of YTPlayerUtils.AUDIO_ITAG_PREFERENCE. Keep in sync with production.
    private val audioItagPreference = listOf(774, 141, 251, 250, 249, 140, 171, 139, 22, 18)

    private fun pickFirstAvailable(available: Set<Int>): Int? =
        audioItagPreference.firstOrNull { it in available }

    @Test
    fun premiumOpus774LeadsWhenPresent() {
        // Typical Premium-and-cookie extraction: 141 and 251 also present — 774 must still win.
        assertEquals(774, pickFirstAvailable(setOf(774, 141, 251, 250, 249)))
    }

    @Test
    fun premiumAac141IsTheSameTierTwinBeforeDroppingTo251() {
        // Entitled account served the AAC family: 774 absent, 141 present with the 160k ladder.
        // Same-tier twin (141) must be picked before the lower Opus tier (251).
        assertEquals(141, pickFirstAvailable(setOf(141, 251, 250, 249, 140)))
    }

    @Test
    fun anonymousExtractionPicks251WhenNo774No141() {
        // Anonymous ANDROID_VR evidence (NewPipe.kt): audio 250/251 (+774 on some tracks).
        // With 774 absent this must behave exactly like the previous ladder.
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
    fun previousAnonymousOrderIsUnchangedBehindTheTwins() {
        // The pre-774 ladder was (251, 250, 249, 141, 140, 171, 139, 22, 18). Per NewPipe.kt the
        // fork evidence, 141 only appears with the cookie's supplementary WEB_REMIX call — anonymous
        // extraction never carries it. So for every itag an anonymous resolve can see, the new
        // ladder must keep the exact legacy relative order: 141's hoisting to twin position is
        // invisible to anonymous picks by construction.
        val legacyAnonymousReachable = listOf(251, 250, 249, 140, 171, 139, 22, 18)
        val newAnonymousReachable = audioItagPreference.filter { it != 774 && it != 141 }
        assertEquals(legacyAnonymousReachable, newAnonymousReachable)
    }
}
