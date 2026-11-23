package iad1tya.echo.music.releaseradar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class ReleaseRadarMatchingTest {
    private fun item(title: String, artist: String, date: LocalDate, source: String = "yt") =
        ReleaseCandidate(title = title, artist = artist, date = date, source = source, artworkUri = null, playId = title)

    @Test fun dedupeKeyIgnoresCaseAndPunctuation() {
        assertEquals(
            ReleaseRadarMatching.dedupeKey(item("Hello!", "Adele", LocalDate.of(2026, 6, 12))),
            ReleaseRadarMatching.dedupeKey(item("hello", "ADELE", LocalDate.of(2026, 6, 12)))
        )
    }
    @Test fun dedupeKeepsOnePerKeyPreferringYt() {
        val spotify = item("Song", "X", LocalDate.of(2026, 6, 12), source = "spotify")
        val yt = item("song", "x", LocalDate.of(2026, 6, 12), source = "yt")
        val result = ReleaseRadarMatching.dedupe(listOf(spotify, yt))
        assertEquals(1, result.size)
        assertEquals("yt", result.first().source)
    }
    @Test fun releaseTypeUsesSpotifyTypeAndTrackCount() {
        val m = ReleaseRadarMatching
        assertEquals(m.TYPE_SINGLE, m.releaseType("single", 1, "Song"))
        assertEquals(m.TYPE_SINGLE, m.releaseType("single", 3, "Song"))
        assertEquals(m.TYPE_EP, m.releaseType("single", 4, "Song"))
        assertEquals(m.TYPE_EP, m.releaseType("single", 6, "Song"))
        assertEquals(m.TYPE_SINGLE, m.releaseType("single", 7, "Song"))
        assertEquals(m.TYPE_EP, m.releaseType("single", 0, "Song EP"))
        assertEquals(m.TYPE_EP, m.releaseType("ep", 0, "Song"))
        assertEquals(m.TYPE_ALBUM, m.releaseType("album", 12, "Record"))
        assertEquals(m.TYPE_COMPILATION, m.releaseType("compilation", 20, "Hits"))
        // YouTube-only path (no type): title heuristic, else unknown.
        assertEquals(m.TYPE_SINGLE, m.releaseType(null, 0, "Song - Single"))
        assertEquals(m.TYPE_EP, m.releaseType(null, 0, "Song - EP"))
        assertEquals(m.TYPE_UNKNOWN, m.releaseType(null, 0, "Record"))
        // "Step" is not an EP.
        assertEquals(m.TYPE_UNKNOWN, m.releaseType(null, 0, "Step"))
    }
    @Test fun oneTrackReleaseIsASingleOnCardAndPage() {
        val m = ReleaseRadarMatching
        // Card (Spotify type): one track is never an album or an EP.
        assertEquals(m.TYPE_SINGLE, m.releaseType("album", 1, "Record"))
        assertEquals(m.TYPE_SINGLE, m.releaseType("ep", 1, "Song"))
        assertEquals(m.TYPE_SINGLE, m.releaseType(null, 1, "Record"))
        // Page: one track wins over any stored type.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(null, 1, "Record"))
        assertEquals(m.TYPE_SINGLE, m.albumPageType(m.TYPE_ALBUM, 1, "Record"))
        assertEquals(m.TYPE_SINGLE, m.albumPageType(m.TYPE_UNKNOWN, 1, "Record"))
        // Page agrees with the card's stored type when there is more than one track.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(m.releaseType("single", 3, "Song"), 3, "Song"))
        assertEquals(m.TYPE_EP, m.albumPageType(m.releaseType("single", 5, "Song"), 5, "Song"))
        assertEquals(m.TYPE_ALBUM, m.albumPageType(m.releaseType("album", 12, "Record"), 12, "Record"))
        assertEquals(m.TYPE_ALBUM, m.albumPageType(m.TYPE_COMPILATION, 20, "Hits"))
        // Not in the radar / unknown count: the title, like the card.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(null, 0, "Song - Single"))
        assertEquals(m.TYPE_EP, m.albumPageType(null, 4, "Song - EP"))
        assertEquals(m.TYPE_ALBUM, m.albumPageType(null, 0, "Step"))
        // Card order (auraContentKind): a «Single» title beats a stored EP.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(m.TYPE_EP, 4, "Song (Single)"))
    }
    @Test fun youTubeHeaderTypeReadsKnownEnglishAndSpanishWordsOnly() {
        val m = ReleaseRadarMatching
        // Header subtitle first run (AlbumPage.typeText), as sent for hl=en / hl=es.
        assertEquals(m.TYPE_SINGLE, m.youTubeHeaderType("Single"))
        assertEquals(m.TYPE_SINGLE, m.youTubeHeaderType("Sencillo"))
        assertEquals(m.TYPE_SINGLE, m.youTubeHeaderType(" single "))
        assertEquals(m.TYPE_EP, m.youTubeHeaderType("EP"))
        // An album, another language, a bare year or nothing: no type, today's logic decides.
        assertEquals(null, m.youTubeHeaderType("Album"))
        assertEquals(null, m.youTubeHeaderType("Álbum"))
        assertEquals(null, m.youTubeHeaderType("Singolo"))
        assertEquals(null, m.youTubeHeaderType("2024"))
        assertEquals(null, m.youTubeHeaderType(null))
    }
    @Test fun youTubeHeaderTypeFillsOnlyTheRadarGapOnThePage() {
        val m = ReleaseRadarMatching
        val single = m.youTubeHeaderType("Single")
        val ep = m.youTubeHeaderType("EP")
        // YouTube-only release (no type, or the old «Release»), 2-6 tracks, no word in the title.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(null, 3, "Record", single))
        assertEquals(m.TYPE_SINGLE, m.albumPageType(m.TYPE_UNKNOWN, 2, "Record", single))
        assertEquals(m.TYPE_EP, m.albumPageType(null, 5, "Record", ep))
        assertEquals(m.TYPE_EP, m.albumPageType(m.TYPE_UNKNOWN, 6, "Record", ep))
        assertEquals(m.TYPE_ALBUM, m.albumPageType(null, 5, "Record", m.youTubeHeaderType("Album")))
        // The radar's own type (Spotify) and the title words still win.
        assertEquals(m.TYPE_ALBUM, m.albumPageType(m.TYPE_ALBUM, 3, "Record", single))
        assertEquals(m.TYPE_ALBUM, m.albumPageType(m.TYPE_COMPILATION, 3, "Record", ep))
        assertEquals(m.TYPE_EP, m.albumPageType(m.TYPE_EP, 4, "Record", single))
        assertEquals(m.TYPE_SINGLE, m.albumPageType(null, 4, "Song (Single)", ep))
        // One track is a single whatever YouTube says.
        assertEquals(m.TYPE_SINGLE, m.albumPageType(null, 1, "Record", ep))
    }
    @Test fun windowFiltersOld() {
        val ref = LocalDate.of(2026, 6, 12)
        assertTrue(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 6, 10), ref, days = 7))
        assertFalse(ReleaseRadarMatching.isWithinWindow(LocalDate.of(2026, 1, 1), ref, days = 7))
    }
}
