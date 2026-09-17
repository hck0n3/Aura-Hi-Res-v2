package iad1tya.echo.music.playback.queues

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tap must always play something.
 *
 * 🔴 OWNER REPORT (2026-09-16): *"cuando doy clic sobre los video no hace nada"*, in all four places a
 * video can be tapped — search results, Tendencias, an artist's page, Vídeos exportados — with no player,
 * no error and no message at all.
 *
 * The cause was not a dead button. `MusicService.playQueue` runs every queue through the hide-videos
 * filter, which is ORed with Data Saver; a queue seeded from a video is videos, so the filter removed the
 * tapped item along with the rest, `initialStatus.items` came back empty, and playQueue returned without
 * touching the player. The lists kept showing videos (the search screen only consults `HideVideoSongsKey`,
 * not Data Saver), so they stayed visible, tappable and dead.
 *
 * These tests pin the rule that fixes it: with `protectAnchor`, whatever the user tapped survives, and a
 * non-empty queue can never be filtered down to nothing. They also pin the OTHER half — automatic radio
 * appends pass `protectAnchor = false` and must keep filtering exactly as before, or the preference
 * silently stops working.
 *
 * Expressed over `List<Boolean>` on purpose: a `MediaItem` cannot be built in a JVM test (its builder
 * parses an `android.net.Uri`), which is precisely why this filter shipped able to empty a queue with the
 * whole suite green.
 */
class QueueFilterAnchorTest {
    private fun keep(
        isVideo: List<Boolean>,
        anchor: Int,
        protect: Boolean,
    ) = QueueFilters.keepIndicesHidingVideos(isVideo, anchor, protect)

    @Test
    fun `a tapped video survives its own filter`() {
        // The exact shape of the bug: the user tapped a video and the radio behind it is videos too.
        val allVideos = List(5) { true }

        val unprotected = keep(allVideos, anchor = 0, protect = false)
        assertTrue(
            "without protection the whole queue is removed — this is what made the tap do nothing",
            unprotected.isEmpty(),
        )

        val protectedKeep = keep(allVideos, anchor = 0, protect = true)
        assertEquals("the tapped item must be the one survivor", listOf(0), protectedKeep)
    }

    @Test
    fun `protection saves the tapped item only, not every video`() {
        // Tapped item at index 2; indices 0, 2 and 4 are videos. The setting must still hide 0 and 4.
        val isVideo = listOf(true, false, true, false, true)

        assertEquals(
            "only the anchor is exempt; the other videos still go",
            listOf(1, 2, 3),
            keep(isVideo, anchor = 2, protect = true),
        )
    }

    @Test
    fun `a non-empty queue never filters down to nothing when protected`() {
        // The invariant the user actually feels: tapping something always plays something.
        for (size in 1..6) {
            for (anchor in 0 until size) {
                val kept = keep(List(size) { true }, anchor = anchor, protect = true)
                assertTrue(
                    "size=$size anchor=$anchor produced an empty queue — the tap would do nothing",
                    kept.isNotEmpty(),
                )
                assertTrue("the survivor must be the tapped item", kept.contains(anchor))
            }
        }
    }

    @Test
    fun `automatic radio keeps the old behaviour byte for byte`() {
        // Nothing may change for the radio/autoplay call sites, or "hide videos" stops working there.
        val isVideo = listOf(false, true, false, true, true, false)
        val expected = isVideo.indices.filter { !isVideo[it] }

        assertEquals(expected, keep(isVideo, anchor = 0, protect = false))
        assertEquals(
            "the anchor must NOT be special when protection is off",
            expected,
            keep(isVideo, anchor = 1, protect = false),
        )
    }

    @Test
    fun `an audio-only queue is untouched either way`() {
        val isVideo = List(4) { false }
        val all = listOf(0, 1, 2, 3)

        assertEquals(all, keep(isVideo, anchor = 2, protect = true))
        assertEquals(all, keep(isVideo, anchor = 2, protect = false))
    }

    @Test
    fun `an out-of-range anchor protects nothing instead of throwing`() {
        // A queue whose index does not point into its own items is already degenerate; the filter must
        // still return a sane list rather than crash the whole playQueue coroutine.
        val isVideo = listOf(true, false, true)

        assertEquals(listOf(1), keep(isVideo, anchor = 99, protect = true))
        assertEquals(listOf(1), keep(isVideo, anchor = -1, protect = true))
        assertTrue(keep(emptyList(), anchor = 0, protect = true).isEmpty())
    }
}
