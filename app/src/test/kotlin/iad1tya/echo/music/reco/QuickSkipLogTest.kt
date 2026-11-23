package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Registry row 720 — the bounded log of the quick skips the listen history never recorded. */
class QuickSkipLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val userSkip = QuickSkipLog.SeekFacts(
        toAnotherSong = true,
        toNextInOrder = true,
        listenedMs = 8_000,
        durationMs = 200_000,
        historyThresholdMs = 30_000,
        appMovedOn = false,
        historyPaused = false,
        listenTogetherGuest = false,
        castingOrMirror = false,
        musicRequestQueue = false,
    )

    private fun entry(i: Int) = QuickSkipLog.Entry(
        songId = "song$i",
        artistId = "UC$i",
        artistName = "Artist $i",
        atMs = 1_000_000L + i,
        listenedMs = 5_000,
        durationMs = 200_000,
    )

    @Test
    fun `the user's skip to the next song before the history threshold counts`() {
        assertTrue(QuickSkipLog.isUserQuickSkip(userSkip))
    }

    @Test
    fun `a song heard up to the history threshold belongs to the history, whatever the threshold is`() {
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 30_000)))
        assertTrue(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 29_999)))
        assertTrue(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 50_000, historyThresholdMs = 60_000)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 8_000, historyThresholdMs = 5_000)))
    }

    @Test
    fun `nothing heard, a song heard to its end or the same song are not skips`() {
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 0)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenedMs = 20_000, durationMs = 20_000)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(toAnotherSong = false)))
        assertTrue("an unknown length still counts", QuickSkipLog.isUserQuickSkip(userSkip.copy(durationMs = 0)))
    }

    @Test
    fun `going back or jumping elsewhere in the queue is not a skip`() {
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(toNextInOrder = false)))
    }

    @Test
    fun `the app's own moves and the excluded situations never count`() {
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(appMovedOn = true)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(historyPaused = true)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(listenTogetherGuest = true)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(castingOrMirror = true)))
        assertFalse(QuickSkipLog.isUserQuickSkip(userSkip.copy(musicRequestQueue = true)))
    }

    @Test
    fun `a music request queue is recognised by the context id the request really gives it`() {
        assertTrue(QuickSkipLog.isMusicRequestContext("MR:123e4567"))
        assertTrue(QuickSkipLog.isMusicRequestContext("MR:123e4567:alt"))
        assertFalse(QuickSkipLog.isMusicRequestContext("PL:abc"))
        assertFalse(QuickSkipLog.isMusicRequestContext(null))
        val vm = main("viewmodels/MusicRequestViewModel.kt")
        assertTrue(vm.contains("val contextId = \"MR:\" + UUID.randomUUID()"))
        assertTrue(vm.contains("val altId = current.contextId + \":alt\""))
    }

    @Test
    fun `entries survive the round trip and odd characters cannot break a line`() {
        val e = QuickSkipLog.Entry("abc", null, "Tab\tand\nnew line", 42L, 3_000L, -1_000L)
        val line = QuickSkipLog.encode(e)
        assertFalse(line.contains('\n'))
        val back = QuickSkipLog.decode(line)!!
        assertEquals("abc", back.songId)
        assertNull(back.artistId)
        assertEquals("Tab and new line", back.artistName)
        assertEquals(42L, back.atMs)
        assertEquals(3_000L, back.listenedMs)
        assertEquals(-1_000L, back.durationMs)
        assertEquals(entry(7), QuickSkipLog.decode(QuickSkipLog.encode(entry(7))))
    }

    @Test
    fun `malformed lines are skipped`() {
        assertNull(QuickSkipLog.decode(""))
        assertNull(QuickSkipLog.decode("only\tthree\tfields"))
        assertNull(QuickSkipLog.decode("\tUC1\tA\t1\t2\t3"))
        assertNull(QuickSkipLog.decode("s\tUC1\tA\tnot-a-number\t2\t3"))
    }

    @Test
    fun `the log keeps only the newest entries, oldest dropped first`() {
        var list = emptyList<QuickSkipLog.Entry>()
        for (i in 0 until QuickSkipLog.CAP + 10) list = QuickSkipLog.appended(list, entry(i))
        assertEquals(QuickSkipLog.CAP, list.size)
        assertEquals("song10", list.first().songId)
        assertEquals("song${QuickSkipLog.CAP + 9}", list.last().songId)
    }

    @Test
    fun `the file keeps the cap, skips a corrupt line and leaves no temporary file behind`() {
        val file = File(tmp.root, QuickSkipLog.FILE_NAME)
        assertTrue(QuickSkipLog.read(file).isEmpty())
        for (i in 0 until 5) QuickSkipLog.append(file, entry(i), cap = 3)
        assertEquals(listOf("song2", "song3", "song4"), QuickSkipLog.read(file).map { it.songId })
        file.appendText("\ngarbage line")
        assertEquals(3, QuickSkipLog.read(file).size)
        QuickSkipLog.append(file, entry(5), cap = 3)
        assertEquals(listOf("song3", "song4", "song5"), QuickSkipLog.read(file).map { it.songId })
        assertFalse(File(tmp.root, QuickSkipLog.FILE_NAME + ".tmp").exists())
    }

    private fun main(path: String): String {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        return File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()
    }
}
