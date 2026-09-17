package iad1tya.echo.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlaylistConstraintsTest {

    @Test fun extractsSoloArtistFromSoloPrefix() {
        assertEquals("Bad Bunny", AiPlaylistConstraints.extractSoloArtist("solo Bad Bunny"))
    }

    @Test fun extractsSoloArtistFromCancionesDe() {
        assertEquals("Rosalía", AiPlaylistConstraints.extractSoloArtist("canciones de Rosalía"))
    }

    @Test fun extractsSoloArtistFromNadaMas() {
        assertEquals("Feid", AiPlaylistConstraints.extractSoloArtist("Feid nada más"))
    }

    @Test fun rejectsGenreOnlyPrompts() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("rock para correr"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("salsa romántica"))
    }

    @Test fun artistAllowedAcceptsMatchingCredit() {
        assertTrue(AiPlaylistConstraints.artistAllowed("Bad Bunny", "Bad Bunny"))
        assertTrue(AiPlaylistConstraints.artistAllowed("Bad Bunny & Jhayco", "Bad Bunny"))
    }

    @Test fun artistAllowedRejectsOtherAct() {
        assertTrue(!AiPlaylistConstraints.artistAllowed("J Balvin", "Bad Bunny"))
    }

    // --- Extension 2026-09-04 (owner: "cuando las crea no se basa en lo que pido") — real
    // phrasings that previously missed the solo lock, letting adjacent artists creep in. ---

    @Test fun extractsSoloArtistFromPuro() {
        assertEquals("Bad Bunny", AiPlaylistConstraints.extractSoloArtist("puro Bad Bunny"))
    }

    @Test fun extractsSoloArtistFromPura() {
        assertEquals("Celia Cruz", AiPlaylistConstraints.extractSoloArtist("pura Celia Cruz"))
    }

    @Test fun extractsSoloArtistFromUnicamente() {
        assertEquals("Romeo Santos", AiPlaylistConstraints.extractSoloArtist("únicamente Romeo Santos"))
    }

    @Test fun extractsSoloArtistFromSoloDe() {
        // The specific "solo de X" pattern MUST run before the generic "solo X" — otherwise the
        // captured "artist" is "de Bad Bunny" and the hard filter matches nothing (empty playlist).
        assertEquals("Bad Bunny", AiPlaylistConstraints.extractSoloArtist("solo de Bad Bunny"))
    }

    @Test fun extractsSoloArtistFromXUnicamente() {
        assertEquals("Frank Reyes", AiPlaylistConstraints.extractSoloArtist("Frank Reyes únicamente"))
    }

    @Test fun stillRejectsGenreAfterExtension() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("únicamente rock de los 80"))
    }

    // --- Adversarial audit findings #2: the new solo patterns must NOT capture Latin genres or
    // phrasing junk as a pseudo-artist — that activated the hard filter against a non-artist and
    // emptied the playlist (a total dead-end where a genre mix used to generate). ---

    @Test fun rejectsLatinGenresFromNewPatterns() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("pura bachata"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("puro perreo"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("puro corridos"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("pura salsa de los 90"))
    }

    @Test fun rejectsPhrasingJunkNotArtists() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("solo de rock de los 80"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("pura música de los 90"))
    }

    @Test fun realArtistWithDePrefixSurvives() {
        // "De La Ghetto" is a REAL artist whose name starts with "De" — the phrasing filter must
        // not eat him (only genre-like captures after the prefix are rejected).
        assertEquals("De La Ghetto", AiPlaylistConstraints.extractSoloArtist("puro De La Ghetto"))
    }

    // --- Dueño (2026-09-17): "le pedí música de los 80s y nunca funcionó, luego le pedí música de
    // los 80s en inglés y no funcionó". El patrón "música de (.+)" capturaba "los 80s" como ARTISTA y
    // el filtro duro vaciaba el resultado entero: cero canciones. Una década no es un artista. ---

    @Test fun rejectsDecadesAsArtists() {
        assertNull(AiPlaylistConstraints.extractSoloArtist("música de los 80s"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("música de los 80s en inglés"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("canciones de los 90"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("música de los ochentas"))
        assertNull(AiPlaylistConstraints.extractSoloArtist("temas de los 2000 en español"))
    }

    @Test fun artistWithANumberInTheNameSurvives() {
        // El guardián mira si queda ALGO al quitar época e idioma: "cent" queda, así que es un nombre.
        assertEquals("50 Cent", AiPlaylistConstraints.extractSoloArtist("canciones de 50 Cent"))
    }
}
