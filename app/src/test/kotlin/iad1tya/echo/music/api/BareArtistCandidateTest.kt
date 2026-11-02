package iad1tya.echo.music.api

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 641 (owner 2026-11-02: «pido Luis Miguel y me salen también Cristian Castro y Ricardo Montaner»). A request that is JUST an
 * artist's name has no «solo…» trigger, so it never locked to that artist. The candidate is only a name; the generator still has to
 * confirm it with an exact artist-card match before locking.
 */
class BareArtistCandidateTest {

    @Test
    fun aBareNameIsACandidate() {
        assertEquals("Luis Miguel", AiPlaylistConstraints.bareArtistCandidate("Luis Miguel"))
        assertEquals("Bad Bunny", AiPlaylistConstraints.bareArtistCandidate("  Bad Bunny  "))
        assertEquals("A\$AP Rocky", AiPlaylistConstraints.bareArtistCandidate("A\$AP Rocky"))
        assertEquals("A Great Big World", AiPlaylistConstraints.bareArtistCandidate("A Great Big World"))
    }

    @Test
    fun theWaysPeopleAskForAnArtistAllGiveTheSameName() {
        for (phrase in listOf(
            "pon Luis Miguel", "ponme a Luis Miguel", "quiero escuchar a Luis Miguel", "quiero Luis Miguel", "reproduce Luis Miguel",
            "dame Luis Miguel", "lo mejor de Luis Miguel", "los mejores éxitos de Luis Miguel", "grandes éxitos de Luis Miguel",
            "pon lo mejor de Luis Miguel", "Éxitos de Luis Miguel",
        )) {
            assertEquals(phrase, "Luis Miguel", AiPlaylistConstraints.bareArtistCandidate(phrase))
        }
    }

    @Test
    fun genresMoodsDecadesAndThemesAreNeverAnArtist() {
        for (phrase in listOf(
            "merengue", "pon merengue", "reproduce salsa", "salsa alegre", "música alegre", "quiero música para estudiar",
            "rock para correr", "música de los 80s", "reggaeton cristiano", "baladas románticas",
        )) {
            assertNull(phrase, AiPlaylistConstraints.bareArtistCandidate(phrase))
        }
    }

    @Test
    fun aSentenceIsNotAName() {
        assertNull(AiPlaylistConstraints.bareArtistCandidate("Luis Miguel, Cristian Castro"))
        assertNull(AiPlaylistConstraints.bareArtistCandidate("una canción para bailar toda la noche con mis amigos"))
        assertNull(AiPlaylistConstraints.bareArtistCandidate("¿tienes algo de Luis Miguel?"))
        assertNull(AiPlaylistConstraints.bareArtistCandidate(""))
    }

    @Test
    fun aPhraseTheSoloRuleAlreadyHandlesIsNotDoubled() {
        assertEquals("Luis Miguel", AiPlaylistConstraints.extractSoloArtist("solo Luis Miguel"))
        assertNull(AiPlaylistConstraints.bareArtistCandidate("solo Luis Miguel"))
        assertNull(AiPlaylistConstraints.bareArtistCandidate("canciones de Luis Miguel"))
    }

    @Test
    fun bothEntryPointsConfirmTheCandidateBeforeLocking() {
        val src = File("src/main/kotlin/com/music/echo/playlistimport/AiPlaylistGenerator.kt").readText()
        assertEquals("produce() and generate() both use it", 2, Regex("""\?: confirmedBareArtist\(""").findAll(src).count())
        assertTrue("the confirmation is an exact artist-card match", src.contains("MusicRequestArtist.sameName(it.title, candidate)"))
    }
}
