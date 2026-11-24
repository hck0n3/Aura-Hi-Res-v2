package iad1tya.echo.music.spotify

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 9 (dueño, tercer reporte): "no encontrado" para links externos aunque la canción sí está en
 * YouTube Music. Comparar TODOS los artistas acreditados como una sola cadena unida castigaba
 * cualquier canción con un featuring que el otro lado no acreditó igual — muy común en
 * reggaetón/bachata — porque el Dice de la cadena larga se diluye con cada nombre de más en
 * cualquiera de los dos lados. Estos tests fijan el contrato de [SpotifyMapper.matchScore] sin red.
 */
class SpotifyMapperTest {

    @Test
    fun `an exact title, artist and duration match scores near 1`() {
        val score = SpotifyMapper.matchScore(
            spotifyTitle = "Blinding Lights",
            spotifyArtists = listOf("The Weeknd"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Blinding Lights",
            candidateArtists = listOf("The Weeknd"),
            candidateDurationSec = 200,
        )
        assertTrue("expected near-perfect score, got $score", score > 0.95)
    }

    @Test
    fun `a featured artist Spotify credits but the candidate does not still scores high`() {
        // Spotify lists both credited artists; the YouTube Music candidate only credits the primary.
        val score = SpotifyMapper.matchScore(
            spotifyTitle = "Se Le Ve",
            spotifyArtists = listOf("Bad Bunny", "Chencho Corleone"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Se Le Ve",
            candidateArtists = listOf("Bad Bunny"),
            candidateDurationSec = 200,
        )
        assertTrue("expected a high score despite the missing featured artist, got $score", score > 0.8)
    }

    @Test
    fun `a featured artist the candidate credits but Spotify does not still scores high`() {
        // The reverse: YouTube Music's candidate lists extra collaborators Spotify's track does not.
        val score = SpotifyMapper.matchScore(
            spotifyTitle = "Se Le Ve",
            spotifyArtists = listOf("Bad Bunny"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Se Le Ve",
            candidateArtists = listOf("Bad Bunny", "Chencho Corleone", "Rauw Alejandro"),
            candidateDurationSec = 200,
        )
        assertTrue("expected a high score despite the extra credited artists, got $score", score > 0.8)
    }

    @Test
    fun `a genuinely different artist scores lower than a matching one`() {
        // Same title and duration on both sides, only the artist changes — isolates the artist
        // dimension instead of asserting an absolute number that title+duration alone can satisfy.
        val matching = SpotifyMapper.matchScore(
            spotifyTitle = "Some Song",
            spotifyArtists = listOf("Bad Bunny"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Some Song",
            candidateArtists = listOf("Bad Bunny"),
            candidateDurationSec = 200,
        )
        val mismatched = SpotifyMapper.matchScore(
            spotifyTitle = "Some Song",
            spotifyArtists = listOf("Bad Bunny"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Some Song",
            candidateArtists = listOf("Karol G"),
            candidateDurationSec = 200,
        )
        assertTrue("expected $mismatched < $matching", mismatched < matching)
    }

    @Test
    fun `blank artist entries are ignored rather than crashing or matching anything`() {
        val score = SpotifyMapper.matchScore(
            spotifyTitle = "Some Song",
            spotifyArtists = listOf("", "Bad Bunny"),
            spotifyDurationMs = 200_000,
            candidateTitle = "Some Song",
            candidateArtists = listOf("Bad Bunny", ""),
            candidateDurationSec = 200,
        )
        assertTrue("expected the real artist pair to still match well, got $score", score > 0.8)
    }
    /** Row 832: the normalizer kept only a-z0-9, so a Korean title became "" and any two empty titles compared as identical. */
    @Test
    fun `a non-Latin title is compared on its own letters`() {
        val same = SpotifyMapper.matchScore(
            spotifyTitle = "좋은 날",
            spotifyArtists = listOf("아이유"),
            spotifyDurationMs = 234_000,
            candidateTitle = "좋은 날",
            candidateArtists = listOf("아이유"),
            candidateDurationSec = 234,
        )
        val other = SpotifyMapper.matchScore(
            spotifyTitle = "좋은 날",
            spotifyArtists = listOf("아이유"),
            spotifyDurationMs = 234_000,
            candidateTitle = "밤편지",
            candidateArtists = listOf("Someone Else"),
            candidateDurationSec = 300,
        )
        assertTrue("expected an exact non-Latin match to score near 1, got $same", same > 0.95)
        assertTrue("expected a different non-Latin song by another artist to score low, got $other", other < 0.5)
    }

    @Test
    fun `nothing left after normalizing never counts as a title match`() {
        val score = SpotifyMapper.matchScore(
            spotifyTitle = "!!!",
            spotifyArtists = listOf("Artist"),
            spotifyDurationMs = 200_000,
            candidateTitle = "???",
            candidateArtists = listOf("Other"),
            candidateDurationSec = 200,
        )
        // Only the duration can count (0.20); the empty titles used to add 0.45 between them.
        assertTrue("expected empty titles not to match, got $score", score < 0.45)
    }
}
