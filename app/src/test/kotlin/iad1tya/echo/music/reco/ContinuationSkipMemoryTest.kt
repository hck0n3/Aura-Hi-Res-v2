package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 525 — the artists a user skipped quickly in smart-queue songs stay out of the next queues for 30 days. */
class ContinuationSkipMemoryTest {
    private val day = 24L * 60 * 60 * 1000
    private val t0 = 1_000_000_000L

    @Test
    fun `one skip is not enough, two inside thirty days reject the artist`() {
        val one = ContinuationSkipMemory.withSkip(emptyMap(), "tercer cielo", t0)
        assertTrue(ContinuationSkipMemory.rejectedFrom(one, t0).isEmpty())
        val two = ContinuationSkipMemory.withSkip(one, "tercer cielo", t0 + 3 * day)
        assertEquals(setOf("tercer cielo"), ContinuationSkipMemory.rejectedFrom(two, t0 + 4 * day))
    }

    @Test
    fun `the rejection expires and a skip after the window starts the count again`() {
        val two = ContinuationSkipMemory.withSkip(ContinuationSkipMemory.withSkip(emptyMap(), "a", t0), "a", t0 + day)
        assertTrue(ContinuationSkipMemory.rejectedFrom(two, t0 + 40 * day).isEmpty())
        val again = ContinuationSkipMemory.withSkip(two, "a", t0 + 60 * day)
        assertTrue(ContinuationSkipMemory.rejectedFrom(again, t0 + 60 * day).isEmpty())
        assertEquals("1,${t0 + 60 * day}", again["a"])
    }

    @Test
    fun `the store never grows past its cap, dropping the oldest artists`() {
        var m: Map<String, String> = emptyMap()
        for (i in 0 until 450) m = ContinuationSkipMemory.withSkip(m, "artist$i", t0 + i)
        assertEquals(400, m.size)
        assertTrue("artist449" in m && "artist0" !in m)
    }

    @Test
    fun `the gate applies the memory to continuation songs only and never to the context's own artists`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val s = File(root, "app/src/main/kotlin/com/music/echo/playback/ExactStyleGate.kt").readText()
        assertTrue(s.contains("it in persistedRejected") && s.contains("!t.artists.any { it.trim().lowercase() in ownArtists }"))
        // recorded only where the song was one the gate let through (the quick-skip branch after the gatedIds guard)
        val guard = s.indexOf("previous.id !in st.gatedIds) return")
        assertTrue(guard > 0 && s.indexOf("ContinuationSkipMemory.recordSkip(") > guard)
        assertTrue("sin nombres en el registro", !Regex("""STYLE_GATE remembered[^\n]*(title|artist\b)""").containsMatchIn(s.replace("artists=%d", "")))
    }
}
