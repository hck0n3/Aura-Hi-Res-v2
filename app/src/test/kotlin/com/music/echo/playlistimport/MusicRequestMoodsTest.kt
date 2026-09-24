package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dueño (2026-09-17): usar el catálogo propio de YouTube Music, donde la categoría ES la prueba.
 * Lo que hay que resolver: sus palabras no coinciden con los nombres de las categorías.
 */
class MusicRequestMoodsTest {

    // Nombres reales del catálogo, en español, como le llegan a él.
    private val catalogo = listOf(
        "Años 50", "Años 60", "Años 70", "Años 80", "Años 90",
        "Concentración", "Entrenamiento", "Fiesta", "Relajación", "Romance", "Viaje", "Sentirse bien",
    )

    private fun pick(prompt: String): Int? =
        MusicRequestMoods.pickCategory(catalogo, prompt, MusicRequestQuery.build(prompt))

    @Test
    fun `a decade request lands on its decade category`() {
        assertEquals(catalogo.indexOf("Años 80"), pick("música de los 80s"))
        assertEquals(catalogo.indexOf("Años 90"), pick("lo mejor de los noventa"))
    }

    @Test
    fun `studying lands on Concentracion even though the words do not match`() {
        assertEquals(catalogo.indexOf("Concentración"), pick("música para estudiar"))
        assertEquals(catalogo.indexOf("Concentración"), pick("algo para concentrarme"))
    }

    @Test
    fun `the gym lands on Entrenamiento`() {
        assertEquals(catalogo.indexOf("Entrenamiento"), pick("música para el gimnasio"))
        assertEquals(catalogo.indexOf("Entrenamiento"), pick("algo para correr"))
    }

    @Test
    fun `a party lands on Fiesta and sleep on Relajacion`() {
        assertEquals(catalogo.indexOf("Fiesta"), pick("música para la fiesta"))
        assertEquals(catalogo.indexOf("Relajación"), pick("música para dormir"))
    }

    @Test
    fun `a decade wins over the moment`() {
        // Comprobable manda sobre interpretable.
        assertEquals(catalogo.indexOf("Años 80"), pick("música de los 80 para el gimnasio"))
    }

    @Test
    fun `a decade the catalogue does not have picks nothing`() {
        // No se cae a otra categoría: daría una lista de otra cosa. Sin categoría, se busca.
        val sinDecadas = listOf("Concentración", "Fiesta")
        val prompt = "música de los 80s"
        assertNull(MusicRequestMoods.pickCategory(sinDecadas, prompt, MusicRequestQuery.build(prompt)))
    }

    @Test
    fun `a request with no clear family picks nothing`() {
        assertNull(pick("salsa de Puerto Rico"))
        assertNull(pick("canciones de Bad Bunny"))
    }

    @Test
    fun `an empty catalogue is handled`() {
        val prompt = "música para estudiar"
        assertNull(MusicRequestMoods.pickCategory(emptyList(), prompt, MusicRequestQuery.build(prompt)))
    }

    /**
     * Ronda 7 (dueño): "si pongo reggaetón me pone canciones que llevan el nombre de reggaetón en el
     * título — es una búsqueda estúpida". Un género suelto ahora también cae en su propia categoría.
     */
    @Test
    fun `a genre lands on its own category`() {
        val withGenres = listOf("Reggaetón", "Salsa", "Bossa Nova", "Rock")
        val reggaeton = "reggaeton"
        assertEquals(
            withGenres.indexOf("Reggaetón"),
            MusicRequestMoods.pickCategory(withGenres, reggaeton, MusicRequestQuery.build(reggaeton)),
        )
        val bossanova = "bossanova"
        assertEquals(
            withGenres.indexOf("Bossa Nova"),
            MusicRequestMoods.pickCategory(withGenres, bossanova, MusicRequestQuery.build(bossanova)),
        )
    }

    @Test
    fun `english catalogue names work too`() {
        val english = listOf("80s", "Focus", "Workout", "Party", "Sleep")
        val prompt = "música para estudiar"
        assertEquals(1, MusicRequestMoods.pickCategory(english, prompt, MusicRequestQuery.build(prompt)))
        assertTrue(MusicRequestMoods.conceptsFor(prompt, MusicRequestQuery.build(prompt)).isNotEmpty())
    }
}
