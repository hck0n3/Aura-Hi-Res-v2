package iad1tya.echo.music.reco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart-queue ordering signals that need no Android: the queue-scoped quick-skip memory and the "processed
 * re-upload" detector. Both only ever DEMOTE (the caller adds the number to the sort key) — nothing is dropped.
 */
class QueueSignalsTest {

    @Test
    fun `one quick skip is noise, two demote the artist for the rest of the queue`() {
        val m = SessionSkipMemory()
        m.record("Redimi2")
        assertEquals(0.0, m.penalty(listOf("Redimi2")), 0.0)
        m.record("  redimi2 ")
        assertEquals(SessionSkipMemory.DEMOTION, m.penalty(listOf("REDIMI2")), 0.0)
        assertEquals(1, m.demotedCount())
        // a collaboration is demoted when ANY credited artist was skipped
        assertEquals(SessionSkipMemory.DEMOTION, m.penalty(listOf("Someone", "Redimi2")), 0.0)
        assertEquals(0.0, m.penalty(listOf("Someone Else")), 0.0)
    }

    @Test
    fun `hearing a song of the artist to the end forgives the skips`() {
        val m = SessionSkipMemory()
        m.record("A"); m.record("A")
        m.forgive("a")
        assertEquals(0.0, m.penalty(listOf("A")), 0.0)
        m.record("A")
        assertEquals("forgiving resets the count, it does not leave a one-skip debt", 0.0, m.penalty(listOf("A")), 0.0)
    }

    @Test
    fun `a new queue starts clean and blank artists are ignored`() {
        val m = SessionSkipMemory()
        m.record(null); m.record(""); m.record("   ")
        assertEquals(0, m.demotedCount())
        m.record("A"); m.record("A")
        m.clear()
        assertEquals(0.0, m.penalty(listOf("A")), 0.0)
    }

    @Test
    fun `the memory is bounded`() {
        val m = SessionSkipMemory()
        repeat(SessionSkipMemory.CAP + 50) { m.record("artist$it"); m.record("artist$it") }
        assertTrue(m.demotedCount() <= SessionSkipMemory.CAP)
        // an artist already tracked can still be counted when the memory is full
        m.record("artist0")
        assertEquals(SessionSkipMemory.DEMOTION, m.penalty(listOf("artist0")), 0.0)
    }

    @Test
    fun `processed re-uploads are recognised, real recordings are not`() {
        for (t in listOf(
            "Flipando (Karaoke Version)", "Blindao - Instrumental", "Song (Sped Up)", "Song speed up", "Song (Slowed + Reverb)",
            "Song - Nightcore", "Song (8D Audio)", "Song 8D", "Song [Bass Boosted]", "Song (Backing Track)",
            "Canción pista instrumental", "Canción (Sin Voz)", "KARAOKE: Canción", "Canción (Versión Karaoké)",
        )) assertTrue(t, VersionNoise.isDerivative(t))
        for (t in listOf(
            "Flipando", "Song (Live)", "Song - Acoustic", "Song (Remix)", "Song (Remastered 2011)", "Song (feat. X)",
            "Instrumentalista", "Karaokeros", "Slowedown", "Canción en Vivo", "Song 18D", "", "Nightcoreless",
        )) assertFalse(t, VersionNoise.isDerivative(t))
        assertFalse(VersionNoise.isDerivative(null, ""))
    }

    @Test
    fun `the album title counts too`() {
        assertTrue(VersionNoise.isDerivative("Hit song", "Karaoke Hits 2020"))
        assertFalse(VersionNoise.isDerivative("Hit song", "Greatest Hits"))
    }
}
