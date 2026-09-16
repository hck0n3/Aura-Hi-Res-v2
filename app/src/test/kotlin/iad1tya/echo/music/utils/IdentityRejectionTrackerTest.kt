package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El umbral que separa "esta canción no está disponible" de "YouTube no te quiere a TI".
 *
 * Los casos están calcados del registro real del dueño (2026-09-16): 36 vídeos distintos, cero éxitos.
 */
class IdentityRejectionTrackerTest {
    @Test
    fun `one unavailable song is content, not identity`() {
        val tracker = IdentityRejectionTracker()
        tracker.recordRejection("abc")
        assertFalse(
            "a single UNPLAYABLE is the normal case — a removed or region-locked video. Retrying it " +
                "anonymously is guaranteed to fail and only doubles the wait before the skip.",
            tracker.isIdentityShaped,
        )
    }

    @Test
    fun `retrying the same song never adds up to identity`() {
        val tracker = IdentityRejectionTracker()
        repeat(10) { tracker.recordRejection("abc") }
        assertEquals("the signal is DISTINCT videos, not attempts", 1, tracker.distinctRejections)
        assertFalse(tracker.isIdentityShaped)
    }

    @Test
    fun `three different songs in a row is identity`() {
        val tracker = IdentityRejectionTracker()
        tracker.recordRejection("one")
        tracker.recordRejection("two")
        assertFalse(tracker.isIdentityShaped)
        tracker.recordRejection("three")
        assertTrue(
            "the owner's log was 36 distinct videos with zero successes; this crosses on the third " +
                "instead of the thirty-sixth",
            tracker.isIdentityShaped,
        )
    }

    @Test
    fun `any success clears the suspicion`() {
        val tracker = IdentityRejectionTracker()
        tracker.recordRejection("one")
        tracker.recordRejection("two")
        tracker.recordSuccess()
        tracker.recordRejection("three")
        assertFalse(
            "a few genuinely unavailable songs spread across an afternoon of normal listening must " +
                "never add up to an anonymous retry nobody needed",
            tracker.isIdentityShaped,
        )
    }

    @Test
    fun `a blank id is not a song`() {
        val tracker = IdentityRejectionTracker()
        repeat(5) { tracker.recordRejection("") }
        assertEquals(0, tracker.distinctRejections)
        assertFalse(tracker.isIdentityShaped)
    }

    @Test
    fun `the set cannot grow without bound on a long broken session`() {
        val tracker = IdentityRejectionTracker()
        repeat(500) { tracker.recordRejection("video$it") }
        assertTrue(tracker.isIdentityShaped)
        assertTrue(
            "past the threshold one more id changes no decision, so it must not be kept",
            tracker.distinctRejections <= IdentityRejectionTracker.MAX_TRACKED,
        )
    }
}
