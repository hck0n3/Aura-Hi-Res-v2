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
}
