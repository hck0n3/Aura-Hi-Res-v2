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

class CapUnprovenTest {
    private val tier = mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 2, "e" to 3, "f" to 3)
    private fun cap(items: List<String>, enough: Int) = ContextLane.capUnproven(items, { tier[it] }, enough)

    @org.junit.Test
    fun `proven songs all stay and the unproven ones only fill what is missing`() {
        val kept = listOf("a", "b", "c", "d", "e", "f")
        org.junit.Assert.assertEquals(listOf("a", "b", "c", "d"), cap(kept, enough = 4))
        org.junit.Assert.assertEquals(listOf("a", "b", "c"), cap(kept, enough = 3))
        org.junit.Assert.assertEquals(listOf("a", "b"), cap(kept, enough = 2))
    }

    @org.junit.Test
    fun `with no proven song the unproven ones still bring the queue up to enough`() {
        org.junit.Assert.assertEquals(listOf("c", "d", "e"), cap(listOf("c", "d", "e", "f"), enough = 3))
    }

    @org.junit.Test
    fun `an unlimited need keeps everything (the relaxed levels)`() {
        val all = listOf("a", "c", "e", "f")
        org.junit.Assert.assertEquals(all, cap(all, enough = Int.MAX_VALUE))
    }
}

class StableStylesTest {
    @org.junit.Test
    fun `a decided style is kept even when a later batch detects another`() {
        val first = ContextLane.stableStyles(null, setOf("worship"), emptySet())
        org.junit.Assert.assertEquals(setOf("worship"), first)
        org.junit.Assert.assertEquals(setOf("worship"), ContextLane.stableStyles(first, setOf("rap"), setOf("pop")))
        org.junit.Assert.assertEquals(setOf("worship"), ContextLane.stableStyles(first, null, emptySet()))
    }

    @org.junit.Test
    fun `unknown becomes the style the songs heard to the end agree on, never a guess between several`() {
        org.junit.Assert.assertEquals(setOf("rap"), ContextLane.stableStyles(null, null, setOf("rap")))
        org.junit.Assert.assertNull(ContextLane.stableStyles(null, null, setOf("rap", "pop")))
        org.junit.Assert.assertNull(ContextLane.stableStyles(null, emptySet(), emptySet()))
    }
}
