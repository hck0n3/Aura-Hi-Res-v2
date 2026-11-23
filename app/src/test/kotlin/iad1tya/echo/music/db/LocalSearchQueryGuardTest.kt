package iad1tya.echo.music.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 768: the Library search song query (3-row preview on every keystroke) runs the cheap library filter
 * before the title LIKE / correlated artist EXISTS, and splits title matches and artist-only matches into two
 * rowid-ordered arms with their own LIMIT, so the preview stops scanning early instead of sorting every match.
 * Same rows in the same order as 09a983d: title matches first, then artist-only matches, each by rowid.
 */
class LocalSearchQueryGuardTest {
    private val sql: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt").readText()
            .substringBefore("fun searchSongs(")
            .substringAfterLast("@Query(")
            .substringAfter("\"")
            .substringBefore("\",")
    }

    private val libraryFilter = "(inLibrary IS NOT NULL OR liked = 1 OR isLocal = 1)"
    private val titleMatch = "title LIKE '%' || :query || '%'"

    @Test
    fun `the library filter comes first in both arms`() {
        val arms = sql.split(" UNION ALL ")
        assertEquals(2, arms.size)
        val titleArm = arms[0].substringAfter("WHERE ")
        val artistArm = arms[1].substringAfter("WHERE ")
        assertTrue(titleArm.startsWith("$libraryFilter AND $titleMatch ORDER BY song.rowid LIMIT :previewSize)"))
        assertTrue(artistArm.startsWith("$libraryFilter AND NOT ($titleMatch) AND EXISTS("))
        assertTrue(artistArm.contains("artist.name LIKE '%' || :query || '%') ORDER BY song.rowid LIMIT :previewSize)"))
    }

    @Test
    fun `title matches rank before artist-only matches, each by rowid`() {
        assertTrue(sql.contains("0 AS searchRank, song.rowid AS searchRowId"))
        assertTrue(sql.contains("1 AS searchRank, song.rowid AS searchRowId"))
        assertTrue(sql.endsWith(") ORDER BY searchRank, searchRowId LIMIT :previewSize"))
        assertFalse("no sort of every match", sql.contains("ORDER BY (title LIKE"))
    }
}
