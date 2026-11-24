package iad1tya.echo.music.edge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 606: the smart queue's AI second opinion — what travels, how the answer is read, and that it can only remove doubtful songs. */
class EdgeJudgeTest {
    private val ctx = listOf(EdgeJudge.Song("Ave Maria", "Bach"))
    private val cand = listOf(EdgeJudge.Song("A", "x"), EdgeJudge.Song("B", "y"))

    @Test
    fun theBodyCarriesTitlesArtistsAndPositionsOnly() {
        val b = EdgeJudge.body(ctx, cand, listOf("classical"), "es", true)
        assertTrue(b.contains("\"i\":1"))
        assertTrue(b.contains("\"faith\":true"))
        assertFalse(b.contains("mediaId"))
        assertFalse(b.contains("videoId"))
    }

    @Test
    fun theBodyIsBounded() {
        val many = (1..40).map { EdgeJudge.Song("S$it", "A") }
        val b = EdgeJudge.body(many, many, emptyList(), null, false)
        assertEquals(EdgeJudge.MAX_CANDIDATES, Regex("\"i\":").findAll(b).count())
    }

    @Test
    fun theAnswerIsReadAsPositionsOrIgnored() {
        assertEquals(setOf(1), EdgeJudge.parse("{\"keep\":[1,7,-2]}", 2))
        assertEquals(emptySet<Int>(), EdgeJudge.parse("{\"keep\":[]}", 2))
        assertNull(EdgeJudge.parse("junk", 2))
        assertNull(EdgeJudge.parse("{\"keep\":\"all\"}", 2))
    }

    @Test
    fun anAnswerThatRejectsEverythingIsNotBelieved() {
        assertNull(EdgeJudge.believable(emptySet(), 3))
        assertEquals(setOf(1), EdgeJudge.believable(setOf(1), 3))
        assertEquals(emptySet<Int>(), EdgeJudge.believable(emptySet(), 1)) // a single doubtful song may truly be out
        assertNull(EdgeJudge.believable(null, 3))
    }

    @Test
    fun theGateOnlyRemovesDoubtfulSongsAndLogsCountsOnly() {
        val src = File("src/main/kotlin/com/music/echo/playback/ExactStyleGate.kt").readText()
        assertTrue(src.contains("EdgeClient.judgeQueue("))
        assertTrue("must only remove: a null answer changes nothing", src.contains(") ?: return@run"))
        assertTrue(src.contains("AI second opinion asked=%d dropped=%d"))
        val client = File("src/main/kotlin/com/music/echo/edge/EdgeClient.kt").readText()
        // Audit F-008 (owner 2026-11-22): off until the user turns it on — an absent key means OFF.
        assertTrue("honours its own switch, off by default", client.contains("QueueAiJudgeKey] != true"))
        val settings = File("src/main/kotlin/com/music/echo/ui/screens/settings/integrations/AuraServicesSettings.kt").readText()
        assertTrue("the settings switch shows it off by default", settings.contains("QueueAiJudgeKey, false)"))
    }

    @Test
    fun auraRoomsAreOnByDefaultEverywhere() {
        listOf(
            "src/main/kotlin/com/music/echo/ui/menu/PlayerMenu.kt",
            "src/main/kotlin/com/music/echo/ui/screens/ListenTogetherScreen.kt",
        ).forEach { path ->
            val src = File(path).readText()
            assertTrue("$path default", src.contains("ListenTogetherAuraRoomsKey, true)"))
            assertFalse("$path still off by default", src.contains("ListenTogetherAuraRoomsKey, false)"))
        }
        // Row 818 (owner decision B1, 2026-11-24): rooms are only Aura's. The settings switch is gone and the client never reads
        // the stored choice (this guard used to pin the switch's default and AppModule's `?: true`).
        val settings = File("src/main/kotlin/com/music/echo/ui/screens/settings/integrations/ListenTogetherSettings.kt").readText()
        assertFalse("the «Salas de Aura» switch is gone", settings.contains("ListenTogetherAuraRoomsKey"))
        assertFalse("and so is the shared server's URL", settings.contains("ListenTogetherServerUrlKey"))
        val module = File("src/main/kotlin/com/music/echo/di/AppModule.kt").readText()
        assertTrue(module.contains("auraRooms = { true },"))
        assertFalse(module.contains("ListenTogetherServerUrlKey"))
    }
}
