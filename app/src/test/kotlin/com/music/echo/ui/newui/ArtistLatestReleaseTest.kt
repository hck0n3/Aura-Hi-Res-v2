package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Owner (2026-10-04): "cuando miro el último lanzamiento tiene una canción que nada que ver con el último
 * lanzamiento real del artista" — the artist had just released a new album.
 */
class ArtistLatestReleaseTest {

    private fun album(title: String, year: Int?, pos: Int, date: String? = null) =
        ArtistLatestRelease.Candidate(title, year, shelfRank = 4, shelfPosition = pos, guestShelf = false, releaseDate = date)

    private fun single(title: String, year: Int?, pos: Int, date: String? = null) =
        ArtistLatestRelease.Candidate(title, year, shelfRank = 7, shelfPosition = pos, guestShelf = false, releaseDate = date)

    private fun guest(title: String, year: Int?, pos: Int) =
        ArtistLatestRelease.Candidate(title, year, shelfRank = 10, shelfPosition = pos, guestShelf = true)

    @Test
    fun sameYearTieIsNotBrokenAlphabetically() {
        // Old rule: max(year) then max(title) → "Zafiro" (a single) beat the brand-new album "Amanecer".
        val candidates = listOf(
            album("Amanecer", 2026, 0),
            album("Viejo disco", 2023, 1),
            single("Zafiro", 2026, 0),
            single("Otra", 2026, 1),
        )
        // Without dates: shelf order decides, albums before singles on a tie of position.
        assertEquals(0, ArtistLatestRelease.pick(candidates))
    }

    @Test
    fun guestAppearancesNeverWin() {
        val candidates = listOf(
            album("Mi disco", 2025, 0),
            guest("Colaboración ajena", 2026, 0),
        )
        assertEquals(0, ArtistLatestRelease.pick(candidates))
    }

    @Test
    fun newerYearWinsOverShelfOrder() {
        val candidates = listOf(
            album("Disco 2024", 2024, 0),
            single("Sencillo 2026", 2026, 0),
        )
        assertEquals(1, ArtistLatestRelease.pick(candidates))
    }

    @Test
    fun itunesDateBreaksTheTieBetweenAlbumAndSingle() {
        val candidates = listOf(
            album("Enero", 2026, 0, date = "2026-01-10T08:00:00Z"),
            single("Octubre", 2026, 0, date = "2026-10-03T07:00:00Z"),
        )
        assertEquals(1, ArtistLatestRelease.pick(candidates, itunesNewest = "2026-10-03T07:00:00Z"))
    }

    @Test
    fun undatedTopCardWinsWhenItunesKnowsSomethingNewerThanEveryMatch() {
        // The new album's YouTube title did not match iTunes' spelling, so it has no date; iTunes still says
        // its newest release is today — the dated older single must not win.
        val candidates = listOf(
            album("Nuevo Álbum (Deluxe Edition) [YT spelling]", 2026, 0),
            single("Sencillo de marzo", 2026, 0, date = "2026-03-01T08:00:00Z"),
        )
        assertEquals(0, ArtistLatestRelease.pick(candidates, itunesNewest = "2026-10-04T07:00:00Z"))
    }

    @Test
    fun datedReleaseWinsWhenItIsTheNewestItunesKnows() {
        val candidates = listOf(
            album("Disco de enero", 2026, 0),
            single("Sencillo de hoy", 2026, 0, date = "2026-10-04T07:00:00Z"),
        )
        assertEquals(1, ArtistLatestRelease.pick(candidates, itunesNewest = "2026-10-04T07:00:00Z"))
    }

    @Test
    fun onlyGuestShelvesMeansNoPin() {
        assertNull(ArtistLatestRelease.pick(listOf(guest("Ajeno", 2026, 0))))
    }

    @Test
    fun dateIndexKeepsSinglesApartFromAlbumsWithTheSameTitle() {
        // "X - Single" (March) and the album "X" (October) share one normalized key.
        val index = ArtistLatestRelease.DateIndex.build(
            listOf(
                Triple("x", true, "2026-03-01T08:00:00Z"),
                Triple("x", false, "2026-10-01T08:00:00Z"),
                Triple("x", false, "2026-10-05T08:00:00Z"), // a store that listed it late
            ),
        )
        assertEquals("2026-10-01T08:00:00Z", index.dateOf("x", fromSinglesShelf = false))
        assertEquals("2026-03-01T08:00:00Z", index.dateOf("x", fromSinglesShelf = true))
        // Newest comes from the EARLIEST date per release, not from the late re-listing.
        assertEquals("2026-10-01T08:00:00Z", index.newest)
    }
}
