package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry 765: Listen Together durations stored in milliseconds are repaired once; real lengths are never touched. */
class RoomDurationRepairTest {

    private fun needs(seconds: Int, local: Boolean = false, uploaded: Boolean = false) =
        RoomDurationRepair.needsRepair(seconds, isLocal = local, isUploaded = uploaded)

    @Test
    fun `a duration stored in milliseconds is repaired to seconds`() {
        assertTrue(needs(215_000))
        assertEquals(215, RoomDurationRepair.repaired(215_000))
        assertTrue("exactly one day is the bar", needs(86_400))
        assertEquals(86, RoomDurationRepair.repaired(86_400))
    }

    @Test
    fun `real lengths are left alone`() {
        assertFalse("a real 3-hour item", needs(3 * 60 * 60))
        assertFalse("just under a day", needs(86_399))
        assertFalse("an ordinary song", needs(215))
        assertFalse("unknown", needs(-1))
        assertFalse("a long local audiobook", needs(30 * 60 * 60, local = true))
        assertFalse("an upload", needs(200_000, uploaded = true))
    }

    @Test
    fun `the SQL, the flag and the startup call match the predicate`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()
        assertEquals(86_400, RoomDurationRepair.MIN_SECONDS_STORED_AS_MILLIS)
        assertTrue(main("db/DatabaseDao.kt").contains(
            "@Query(\"UPDATE song SET duration = duration / 1000 WHERE duration >= :minSeconds AND isLocal = 0 AND isUploaded = 0\")",
        ))
        val repair = main("listentogether/RoomDurationRepair.kt")
        val flagRead = repair.indexOf("if (store.data.first()[RoomDurationRepairDoneKey] == true) return")
        val update = repair.indexOf("database.repairMillisecondDurations(MIN_SECONDS_STORED_AS_MILLIS)")
        val flagWrite = repair.indexOf("store.edit { it[RoomDurationRepairDoneKey] = true }")
        assertTrue("flag read, then update, then flag write", flagRead in 0 until update && update < flagWrite)
        assertTrue(main("constants/PreferenceKeys.kt").contains("val RoomDurationRepairDoneKey = booleanPreferencesKey(\"room_duration_repair_done\")"))
        assertTrue(main("playback/MusicService.kt").contains(
            "scope.launch(Dispatchers.IO) { runCatching { iad1tya.echo.music.listentogether.RoomDurationRepair.runOnce(this@MusicService, database) } }",
        ))
    }
}
