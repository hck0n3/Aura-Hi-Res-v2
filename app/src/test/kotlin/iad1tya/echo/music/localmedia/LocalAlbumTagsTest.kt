package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 879 (plan, phase 4: «orden de disco/pista, artista del álbum»): a local album plays disc 1 then disc 2, each by track
 * number, and a compilation stays one album under its album artist.
 */
class LocalAlbumTagsTest {

    @Test
    fun `tag numbers keep the leading number of the usual spellings`() {
        assertEquals(3, parseTagNumber("3"))
        assertEquals(3, parseTagNumber("03"))
        assertEquals(3, parseTagNumber("3/12"))
        assertEquals(2, parseTagNumber(" 2 of 2"))
        assertNull("0 is an unset tag", parseTagNumber("0"))
        assertNull(parseTagNumber("0/0"))
        assertNull(parseTagNumber(""))
        assertNull(parseTagNumber(null))
        assertNull("vinyl side letters are not numbers", parseTagNumber("A1"))
    }

    @Test
    fun `MediaStore TRACK carries the disc in its thousands`() {
        assertEquals(LocalTrackPosition(disc = 2, track = 5), localTrackPosition(null, null, 2005))
        assertEquals(LocalTrackPosition(disc = 1, track = 12), localTrackPosition(null, null, 1012))
        assertEquals(LocalTrackPosition(disc = null, track = 7), localTrackPosition(null, null, 7))
        assertEquals(LocalTrackPosition(disc = null, track = null), localTrackPosition(null, null, 0))
        assertEquals(LocalTrackPosition(disc = null, track = null), localTrackPosition(null, null, null))
    }

    @Test
    fun `the disc and track tags win over the encoded TRACK`() {
        assertEquals(LocalTrackPosition(disc = 2, track = 3), localTrackPosition("3/14", "2/2", 1003))
        assertEquals(LocalTrackPosition(disc = 2, track = 3), localTrackPosition("03", null, 2003))
        assertEquals(LocalTrackPosition(disc = 1, track = 9), localTrackPosition(null, "1/2", 9))
    }

    private fun entry(id: String, disc: Int?, track: Int?, title: String = id, album: String? = "A") =
        LocalAlbumOrderEntry(id = id, albumId = album, disc = disc, track = track, title = title)

    @Test
    fun `a two-disc album plays disc 1 then disc 2, each by track number`() {
        // Listed by title, the way MediaStore hands them over.
        val listed = listOf(
            entry("content://1", disc = 2, track = 1, title = "Abbey"),
            entry("content://2", disc = 1, track = 10, title = "Bravo"),
            entry("content://3", disc = 1, track = 2, title = "Charlie"),
            entry("content://4", disc = 2, track = 11, title = "Delta"),
            entry("content://5", disc = 1, track = 1, title = "Echo"),
        )
        val indexes = localAlbumIndexes(listed)
        val order = listed.sortedBy { indexes.getValue(it.id) }.map { it.title }
        assertEquals(listOf("Echo", "Charlie", "Bravo", "Abbey", "Delta"), order)
        assertEquals(listOf(0, 1, 2, 3, 4), listed.map { indexes.getValue(it.id) }.sorted())
    }

    @Test
    fun `untagged songs follow the numbered ones, by title, and a missing disc counts as disc 1`() {
        val listed = listOf(
            entry("a", disc = null, track = null, title = "Zulu"),
            entry("b", disc = null, track = null, title = "alpha"),
            entry("c", disc = 2, track = 1, title = "Second disc"),
            entry("d", disc = null, track = 2, title = "Two"),
            entry("e", disc = 1, track = 1, title = "One"),
        )
        val indexes = localAlbumIndexes(listed)
        assertEquals(listOf("One", "Two", "alpha", "Zulu", "Second disc"), listed.sortedBy { indexes.getValue(it.id) }.map { it.title })
    }

    @Test
    fun `each album is numbered on its own and songs without an album get no position`() {
        val indexes = localAlbumIndexes(
            listOf(
                entry("x2", disc = 1, track = 2, album = "X"),
                entry("y1", disc = 1, track = 1, album = "Y"),
                entry("x1", disc = 1, track = 1, album = "X"),
                entry("loose", disc = null, track = 1, album = null),
            ),
        )
        assertEquals(mapOf("x1" to 0, "x2" to 1, "y1" to 0), indexes)
    }

    @Test
    fun `a compilation is grouped and shown by its album artist, an untagged album by its first artist as before`() {
        val various = localArtistId("Various Artists")
        val queen = localArtistId("Queen")
        val bowie = localArtistId("David Bowie")
        assertEquals(various, localAlbumGroupArtistId(listOf(various), listOf(queen)))
        assertEquals(various, localAlbumGroupArtistId(listOf(various), listOf(bowie)))
        assertEquals("no album artist tag: the old grouping, same ids", queen, localAlbumGroupArtistId(emptyList(), listOf(queen, bowie)))
        assertNull(localAlbumGroupArtistId(emptyList(), emptyList()))

        assertEquals(
            listOf(various),
            localAlbumArtistIds(listOf(listOf(various), emptyList(), listOf(various)), listOf(listOf(queen), listOf(bowie), listOf(queen))),
        )
        assertEquals(listOf(queen, bowie), localAlbumArtistIds(listOf(emptyList(), emptyList()), listOf(listOf(queen), listOf(bowie, queen))))
    }

    @Test
    fun `an artist or album that got another id hands its marks to the new one only when the old one goes`() {
        val heirs = inheritedLocalIds(
            listOf(
                listOf("A / B") to listOf("A", "B"),
                listOf("A / B") to listOf("A", "B"),
                listOf("C") to listOf("C"),
                listOf("D & E") to listOf("D", "E"),
            ),
            stillUsed = setOf("A", "B", "C", "D", "E"),
        )
        assertEquals(mapOf("A" to "A / B", "D" to "D & E"), heirs)
        assertTrue("an old id still in use keeps its own marks", inheritedLocalIds(listOf(listOf("A") to listOf("B")), setOf("A", "B")).isEmpty())
        assertTrue(inheritedLocalIds(listOf(listOf("A") to emptyList()), emptySet()).isEmpty())
        assertEquals("a new id inherits from one old id at most", mapOf("X" to "old1"), inheritedLocalIds(listOf(listOf("old1", "old2") to listOf("X")), setOf("X")))
    }

    @Test
    fun `an artist or album that already existed never takes the marks of one that was dropped`() {
        assertTrue(
            "«A feat. B» retagged «A»: A was already there, so B's follow does not move to A",
            inheritedLocalIds(listOf(listOf("A", "B") to listOf("A")), stillUsed = setOf("A"), alreadyThere = setOf("A")).isEmpty(),
        )
        assertTrue(
            "the only song of album P retagged into album Q that exists: Q keeps only its own like",
            inheritedLocalIds(listOf(listOf("P") to listOf("Q")), stillUsed = setOf("Q"), alreadyThere = setOf("Q")).isEmpty(),
        )
        assertEquals(
            "the first NEW id of the song inherits, not an artist that was already there",
            mapOf("Simon & Garfunkel" to "Simon"),
            inheritedLocalIds(
                listOf(listOf("A", "Simon", "Garfunkel") to listOf("A", "Simon & Garfunkel")),
                stillUsed = setOf("A", "Simon & Garfunkel"),
                alreadyThere = setOf("A"),
            ),
        )
        assertEquals(
            "an album now grouped by its album artist is new and inherits",
            mapOf("new" to "old"),
            inheritedLocalIds(listOf(listOf("old") to listOf("new")), stillUsed = setOf("new"), alreadyThere = emptySet()),
        )
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(scanner.contains("alreadyThere = existingArtists.keys,"))
        assertTrue(scanner.contains("alreadyThere = existingAlbums.keys,"))
    }

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")

    private fun text(path: String) = File(root, path).readText()

    @Test
    fun `the scan writes the album order and the album artist, and the album lists read them`() {
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(scanner.contains("add(MediaStore.Audio.Media.TRACK)"))
        assertTrue(scanner.contains("add(MediaStore.MediaColumns.ALBUM_ARTIST)"))
        assertTrue(scanner.contains("METADATA_KEY_CD_TRACK_NUMBER"))
        assertTrue(scanner.contains("index = snapshot.albumIndexes[track.id] ?: 0,"))
        assertTrue("an unchanged song of a rewritten album gets its position again", scanner.contains("albumId !in rewrittenAlbums"))
        assertTrue(
            "a remembered build is rebuilt from its tags, and counts as reused only if nothing changed",
            scanner.contains("tracks += cached.record.finished(unknownArtist, scanConfig.artistSplit).also { record -> if (record == cached.record) reusedIds += id }"),
        )
        assertTrue(scanner.contains("artists = tracks.flatMap { it.artists + it.albumArtists }"))
        assertTrue(scanner.contains("bookmarkedAt = existingArtist?.bookmarkedAt ?: inherited?.bookmarkedAt,"))
        assertTrue(scanner.contains("bookmarkedAt = existingAlbum?.bookmarkedAt ?: inherited?.bookmarkedAt,"))
        assertTrue("new tag rules discard the remembered builds once", LocalScanRulesVersion >= 2)

        val dao = text("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue(dao.contains("WHERE song_album_map.albumId = :albumId ORDER BY song_album_map.`index`"))
        assertTrue("an album artist with no song of its own is not pruned", dao.contains("AND id NOT IN (SELECT DISTINCT album_artist_map.artistId FROM album_artist_map JOIN album"))
        val view = text("app/src/main/kotlin/com/music/echo/db/entities/SortedSongAlbumMap.kt")
        assertTrue("the album screen's junction sorts by the same index", view.contains("SELECT * FROM song_album_map ORDER BY `index`"))

        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/library/LocalSongScreen.kt")
        assertTrue(screen.contains(".thenBy { song -> albumPositions[song.song.id] ?: Int.MAX_VALUE }"))
        assertTrue(screen.contains("if (sortDescending && sortType != LocalSongSortType.ALBUM) sortedSongs.asReversed() else sortedSongs"))
    }
}
