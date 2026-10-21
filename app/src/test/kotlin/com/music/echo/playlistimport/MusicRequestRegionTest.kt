package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.reco.MusicStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fila 462 (dueño, 2026-10-21): *«pedí cumbia villera cristiana argentina, cumbia colombiana cristiana y cumbia mexicana
 * cristiana y las tres veces me dio lo mismo, y no son cristianas»*. Sus tres peticiones exactas son los primeros casos.
 */
class MusicRequestRegionTest {

    private fun qualifiersOf(prompt: String): List<String> {
        val intent = MusicRequestIntent.parse(prompt)
        return MusicRequestQuery.residualWords(MusicRequestQuery.residualBeyondCategory(intent.cleanPrompt))
    }

    private fun queryOf(prompt: String): String {
        val intent = MusicRequestIntent.parse(prompt)
        return MusicRequestIntent.styleQuery(intent, intent.styles.first(), qualifiersOf(prompt))!!
    }

    private fun matches(prompt: String, title: String): Boolean {
        val intent = MusicRequestIntent.parse(prompt)
        val groups = qualifiersOf(prompt).map { MusicRequestRegion.group(it) }
        return MusicRequestIntent.playlistMatches(title, intent.styles.first(), intent.christian, groups)
    }

    @Test
    fun `the three owner requests search three different things`() {
        val villera = queryOf("cumbia villera cristiana argentina")
        val colombiana = queryOf("cumbia colombiana cristiana")
        val mexicana = queryOf("cumbia mexicana cristiana")
        assertEquals(3, setOf(villera, colombiana, mexicana).size)
        assertTrue(villera, villera.contains("villera") && villera.contains("argentina") && villera.contains("cristiana"))
        assertTrue(colombiana, colombiana.contains("colombiana"))
        assertTrue(mexicana, mexicana.contains("mexicana"))
    }

    @Test
    fun `mexicana stays a country next to a concrete style and a family without one`() {
        assertEquals(listOf("mexicana"), qualifiersOf("cumbia mexicana cristiana"))
        // Sin estilo concreto sigue siendo la familia «regional mexicano»: nada que demostrar por título.
        assertEquals(emptyList<String>(), qualifiersOf("música mexicana"))
    }

    @Test
    fun `a list only counts when its title proves the sub-style and the country`() {
        val p = "cumbia villera cristiana argentina"
        assertTrue(matches(p, "Cumbia Villera Cristiana 🔥"))      // villera ⇒ Argentina
        assertTrue(matches(p, "Cumbia Argentina Cristiana Villera"))
        assertFalse(matches(p, "Cumbia Cristiana"))               // the generic list that answered all three
        assertFalse(matches(p, "Cumbia Colombiana Cristiana"))
        assertFalse(matches(p, "Cumbia Villera"))                 // not Christian
    }

    @Test
    fun `a country is named by any of its forms`() {
        assertTrue(matches("cumbia colombiana cristiana", "Cumbia Cristiana de Colombia"))
        assertTrue(matches("cumbia colombiana cristiana", "Cumbia colombiano cristiano"))
        assertFalse(matches("cumbia colombiana cristiana", "Cumbia Mexicana Cristiana"))
        assertTrue(matches("cumbia mexicana cristiana", "Cumbia Sonidera Cristiana"))   // sonidera ⇒ México
        assertTrue(matches("rock argentino", "Rock Argentina Clásico"))
        assertFalse(matches("rock argentino", "Rock en Español"))
    }

    @Test
    fun `a country or a sub-style is never a song or an artist`() {
        assertTrue(MusicRequestRegion.isRegionWord("colombiana"))
        assertTrue(MusicRequestRegion.isRegionWord("rico"))
        assertTrue(MusicStyle.isSubStyleWord("villera"))
        assertFalse(MusicRequestRegion.isRegionWord("flipando"))
        assertFalse(MusicStyle.isSubStyleWord("espanol"))
        assertTrue(MusicRequestQuery.isThemeOnly("cumbia colombiana"))
        assertTrue(MusicRequestQuery.isThemeOnly("música de Argentina"))
    }

    @Test
    fun `the year and the faith still travel with the country`() {
        val q = queryOf("bachata dominicana cristiana del 2015")
        assertTrue(q, q.contains("dominicana") && q.contains("cristiana") && q.contains("2015"))
        assertNotEquals(queryOf("bachata dominicana cristiana del 2015"), queryOf("bachata cristiana del 2015"))
    }

    @Test
    fun `the plain request is unchanged`() {
        assertEquals("cumbia cristiana", queryOf("cumbia cristiana"))
        assertEquals("merengue cristiano 2026", queryOf("merengue cristiano 2026"))
    }

    @Test
    fun `genres the catalogue has are understood`() {
        for (g in listOf("carranga", "tropipop", "marimba", "pasodoble", "broadway")) {
            assertTrue("«$g»", MusicStyle.namesAStyle(g))
        }
    }
}
