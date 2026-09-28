package iad1tya.echo.music.reco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ronda 11 (dueño, 2026-09-27): "si el artista que estoy escuchando es cristiano, que también se
 * mantenga el género más su religión; y si no tiene religión, que se mantenga con música de acuerdo a
 * lo que se escucha". Geovanni Rios ("El culto está bueno", merengue cristiano) → Marcos Witt.
 */
class GenreLaneStyleTest {

    private val genres = mapOf(
        "geovanni rios" to "Tropical",
        "marcos witt" to "Christian & Gospel",
        "elvis crespo" to "Tropical",
        "alex zurdo" to "Urbano latino",
        "banda cristiana x" to "Tropical",
    )

    @Test
    fun `a christian tropical anchor keeps faith and style`() {
        val anchor = GenreLane.anchorOf(genres, "Geovanni Rios", "El Culto Está Bueno")
        assertEquals(GenreLane.CHRISTIAN, anchor.lane)
        assertEquals(GenreLane.STYLE_TROPICAL, anchor.christianStyle)
        // Worship-only artist: same faith, different style -> out.
        assertFalse(GenreLane.keeps(anchor, genres, "Marcos Witt", "Tu Fidelidad"))
        // Secular tropical: same style, no faith -> out (the anchor is strict: keyword Christian).
        assertFalse(GenreLane.keeps(anchor, genres, "Elvis Crespo", "Suavemente"))
        // Christian tropical: both -> in.
        assertTrue(GenreLane.keeps(anchor, genres, "Banda Cristiana X", "Merengue de Adoración a Cristo"))
    }

    @Test
    fun `a secular anchor behaves exactly as before`() {
        val anchor = GenreLane.anchorOf(genres, "Elvis Crespo", "Suavemente")
        assertEquals("tropical", anchor.lane)
        assertNull(anchor.christianStyle)
        assertTrue(GenreLane.keeps(anchor, genres, "Elvis Crespo", "Tu Sonrisa"))
        assertFalse(GenreLane.keeps(anchor, genres, "Marcos Witt", "Tu Fidelidad"))
        // Unknown artist stays eligible (#39/#41).
        assertTrue(GenreLane.keeps(anchor, genres, "Nadie Conocido", "Canción"))
    }

    @Test
    fun `a christian anchor with no known style does not filter by style`() {
        val anchor = GenreLane.anchorOf(genres, "Marcos Witt", "Tu Fidelidad")
        assertEquals(GenreLane.CHRISTIAN, anchor.lane)
        assertNull(anchor.christianStyle)
    }

    @Test
    fun `title style words beat the artist label`() {
        assertEquals(GenreLane.STYLE_URBAN, GenreLane.styleOfTrack(genres, "Marcos Witt", "Rap Cristiano"))
        assertEquals(GenreLane.STYLE_WORSHIP, GenreLane.styleOfTrack(genres, "Marcos Witt", "Tu Fidelidad"))
        assertEquals(GenreLane.STYLE_URBAN, GenreLane.styleFamily("Urbano latino"))
        // "rap" is matched as a whole word, never inside "rápido".
        assertNull(GenreLane.styleOfTrack(emptyMap(), "x", "Corre Rápido"))
    }
}
