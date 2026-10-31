package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 527 — with no style and no family from the context, the queue's own batch names the family. */
class ConsensusFamilyTest {
    @Test
    fun `a clear lead names the family, a mixed or tiny batch names none`() {
        assertEquals(setOf("tropical"), ContextLane.consensusFamilies(listOf("tropical", "tropical", "tropical", "urbano", "tropical"), 4, 0.6))
        assertTrue(ContextLane.consensusFamilies(listOf("tropical", "urbano", "tropical", "urbano"), 4, 0.6).isEmpty())
        assertTrue(ContextLane.consensusFamilies(listOf("tropical", "tropical", "tropical"), 4, 0.6).isEmpty())
        assertTrue(ContextLane.consensusFamilies(emptyList(), 4, 0.6).isEmpty())
    }

    @Test
    fun `the gate only infers a family when the context and the user named nothing`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val s = File(root, "app/src/main/kotlin/com/music/echo/playback/ExactStyleGate.kt").readText()
        assertTrue(s.contains("baseFamilies.isEmpty() && override == null && requested == null && !languageOnly &&"))
        assertTrue(s.contains("val families = baseFamilies.ifEmpty { inferredFamilies }"))
    }
}

class LockedConsensusTest {
    @org.junit.Test
    fun theFirstAnswerStandsForTheWholeContext() {
        val first = ContextLane.lockedConsensus(null, setOf("worship"))
        org.junit.Assert.assertEquals(setOf("worship"), first)
        // A later batch that votes for another family does not move it.
        org.junit.Assert.assertEquals(setOf("worship"), ContextLane.lockedConsensus(first, setOf("urbano")))
        // ...and a batch with no clear vote does not loosen it.
        org.junit.Assert.assertEquals(setOf("worship"), ContextLane.lockedConsensus(first, emptySet()))
    }

    @org.junit.Test
    fun aNewQueueStartsFree() {
        org.junit.Assert.assertEquals(setOf("pop"), ContextLane.lockedConsensus(null, setOf("pop")))
        org.junit.Assert.assertEquals(emptySet<String>(), ContextLane.lockedConsensus(null, emptySet()))
        org.junit.Assert.assertEquals(setOf("pop"), ContextLane.lockedConsensus(emptySet(), setOf("pop")))
    }
}
