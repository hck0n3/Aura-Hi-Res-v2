package iad1tya.echo.music.localmedia

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 882 (plan, phase 4: «mover archivos sin perder me gusta/listas/historial»): a local file that is moved or renamed
 * gets a new MediaStore id, so the scan used to delete its old row (and with it, by cascade, its likes, playlist entries
 * and history) and import it as a new song. A one-to-one match now carries everything over; anything ambiguous is never
 * merged.
 */
class MovedLocalSongsTest {

    private fun song(
        id: String,
        size: Long = 4_000_000L,
        duration: Int = 215,
        title: String = "Song",
        album: String? = "Album",
        isVideo: Boolean = false,
    ) = LocalSongIdentity(id, isVideo, size, duration, title, album)

    @Test
    fun `a moved file is matched to its old row`() {
        val result = matchMovedLocalSongs(missing = listOf(song("old")), appeared = listOf(song("new")))
        assertEquals(mapOf("old" to "new"), result.moves)
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `the match ignores case and outer spaces of the tags and one second of rounding`() {
        val result = matchMovedLocalSongs(
            missing = listOf(song("old", title = "Song ", album = "ALBUM", duration = 215)),
            appeared = listOf(song("new", title = "song", album = "album", duration = 216)),
        )
        assertEquals(mapOf("old" to "new"), result.moves)
    }

    @Test
    fun `a different size, duration, title, album or kind is a different song`() {
        val old = song("old")
        for (other in listOf(
            song("new", size = 4_000_001L),
            song("new", duration = 217),
            song("new", title = "Other"),
            song("new", album = "Other"),
            song("new", album = null),
            song("new", isVideo = true),
        )) {
            val result = matchMovedLocalSongs(listOf(old), listOf(other))
            assertTrue("$other", result.moves.isEmpty())
            assertTrue("$other", result.ambiguous.isEmpty())
        }
    }

    @Test
    fun `two candidates for one missing song are never merged`() {
        val result = matchMovedLocalSongs(missing = listOf(song("old")), appeared = listOf(song("copy1"), song("copy2")))
        assertTrue(result.moves.isEmpty())
        assertEquals(setOf("old"), result.ambiguous)
    }

    @Test
    fun `two missing songs claiming one new file are never merged`() {
        val result = matchMovedLocalSongs(missing = listOf(song("old1"), song("old2")), appeared = listOf(song("new")))
        assertTrue(result.moves.isEmpty())
        assertEquals(setOf("old1", "old2"), result.ambiguous)
    }

    @Test
    fun `an unknown size or an empty title never matches`() {
        assertTrue(matchMovedLocalSongs(listOf(song("old", size = 0L)), listOf(song("new", size = 0L))).moves.isEmpty())
        assertTrue(matchMovedLocalSongs(listOf(song("old", title = " ")), listOf(song("new", title = " "))).moves.isEmpty())
    }

    @Test
    fun `a whole library whose MediaStore ids all changed keeps every song`() {
        val missing = (1..50).map { song("old$it", size = 1_000_000L + it, title = "T$it") }
        val appeared = (1..50).map { song("new$it", size = 1_000_000L + it, title = "T$it") }
        val result = matchMovedLocalSongs(missing, appeared)
        assertEquals((1..50).associate { "old$it" to "new$it" }, result.moves)
    }

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the scan moves the user data inside its transaction before it deletes the old rows`() {
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        val transaction = scanner.substringAfter("database.withTransaction {").substringBefore("pruneLocalAlbums()")
        val move = transaction.indexOf("val moved = moveRenamedLocalSongs(missingOnMountedVolumes,")
        assertTrue("moves are matched only among songs the volume guard lets go", move > transaction.indexOf("splitMissingByVolume("))
        assertTrue("moves run before the delete", move in 0 until transaction.indexOf("removable.chunked(SqlBatchSize).forEach(::deleteSongsByIds)"))
        assertTrue("ambiguous songs are kept unless the user confirmed the removal", transaction.contains("val ambiguousKept = if (removeMissing) emptyList() else missingOnMountedVolumes.filter(moved.ambiguous::contains)"))
        assertTrue(transaction.contains("val kept = keptOnVolume + ambiguousKept"))
        assertTrue("a moved song is not counted as removed", scanner.contains("removedSongs = removable.size - moved.moves.size,"))

        val helper = scanner.substringAfter("private suspend fun moveRenamedLocalSongs(").substringBefore("private suspend fun loadSongs(")
        assertTrue("a file with a row of its own never takes another song's data", helper.contains(".filterNot { it.id in takenIds }"))
        val insert = helper.indexOf("database.insert(oldSongs.getValue(oldId).song.copy(id = newId))")
        for (call in listOf("moveLocalSongPlaylistEntries", "moveLocalSongEvents", "moveLocalSongPlayCounts", "moveLocalSongLyrics")) {
            assertTrue("$call runs after the new row exists", helper.indexOf("database.$call(oldId, newId)") > insert)
        }
        assertTrue(insert >= 0)
    }

    @Test
    fun `the DAO moves every table that cascades from a song row`() {
        val dao = text("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue(dao.contains("@Query(\"UPDATE playlist_song_map SET songId = :newId WHERE songId = :oldId\")"))
        assertTrue(dao.contains("@Query(\"UPDATE event SET songId = :newId WHERE songId = :oldId\")"))
        assertTrue(dao.contains("\"UPDATE playCount SET song = :newId WHERE song = :oldId AND NOT EXISTS \" +"))
        assertTrue(dao.contains("@Query(\"UPDATE lyrics SET id = :newId WHERE id = :oldId AND NOT EXISTS (SELECT 1 FROM lyrics WHERE id = :newId)\")"))
    }
}
