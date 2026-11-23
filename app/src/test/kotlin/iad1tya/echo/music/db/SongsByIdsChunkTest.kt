package iad1tya.echo.music.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 694: `WHERE id IN (:songIds)` binds one variable per id and Android 8-11 SQLite stops at 999 («too many SQL
 * variables», thrown inside a `stateIn` with no catch). The exported lists now hold up to 5000 ids; every caller goes
 * through the chunked helper.
 */
class SongsByIdsChunkTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    @Test
    fun `long lists are split under the old 999 limit and duplicates are dropped`() {
        val ids = (1..5000).map { "id$it" }
        val chunks = songIdQueryChunks(ids + ids.take(10))
        assertTrue(chunks.all { it.size <= SONG_ID_QUERY_CHUNK })
        assertTrue(SONG_ID_QUERY_CHUNK < 999)
        assertEquals(ids, chunks.flatten())
        assertEquals(listOf(listOf("a", "b")), songIdQueryChunks(listOf("a", "b", "a")))
        assertTrue(songIdQueryChunks(emptyList()).isEmpty())
    }

    @Test
    fun `no caller sends an unbounded id list to the single query`() {
        val offenders = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "DatabaseDao.kt" && it.name != "SongsByIds.kt" }
            .filter { "getSongsByIdsFlow(" in it.readText() }
            .map { it.name }
            .toList()
        assertTrue("use getSongsByIdsFlowChunked: $offenders", offenders.isEmpty())
    }
}
