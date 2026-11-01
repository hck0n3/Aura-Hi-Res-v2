package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 626: «Luis Miguel» is the singer, not every channel with that name. */
class SoloArtistIdentityTest {
    private val real = "UC_REAL"

    @Test
    fun theRealArtistsSongPasses() {
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to real), "Luis Miguel", real))
    }

    @Test
    fun aNamesakeWithAnotherChannelIsTurnedAway() {
        assertFalse(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to "UC_OTHER"), "Luis Miguel", real))
    }

    @Test
    fun aDuetKeepsTheRealOneWhenHeIsCredited() {
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to real, "Alejandro Sanz" to "UC_X"), "Luis Miguel", real))
    }

    @Test
    fun creditsWithoutAnIdOrWithoutKnownIdCannotTellAndPass() {
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to null), "Luis Miguel", real))
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to "UC_OTHER"), "Luis Miguel", null))
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Luis Miguel" to "UC_OTHER"), null, real))
    }

    @Test
    fun otherArtistsAreNotThisFiltersBusiness() {
        // the name gate (soloPrimaryMatch) already handles them; this one only separates namesakes
        assertTrue(AiPlaylistGenerator.soloIdMatch(listOf("Cristian Castro" to "UC_CC"), "Luis Miguel", real))
    }

    @Test
    fun theGeneratorResolvesTheRealChannelBeforeAbsorbing() {
        val src = java.io.File("src/main/kotlin/com/music/echo/playlistimport/AiPlaylistGenerator.kt").readText()
        org.junit.Assert.assertTrue(src.contains("soloIdMatch(item.artists.map { it.name to it.id }, effectiveSolo, effectiveSoloId)"))
        org.junit.Assert.assertTrue(src.contains("runCatching { resolveSoloId(effectiveSolo) }"))
    }
}
