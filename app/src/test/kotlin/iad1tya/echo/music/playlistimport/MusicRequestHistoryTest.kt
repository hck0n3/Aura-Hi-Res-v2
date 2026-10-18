package iad1tya.echo.music.playlistimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Dueño (2026-10-18): que de «Pedir música» se pueda quitar del historial una petición ya completada, por
 * separado. Lo que NO puede pasar: que quitar una arrastre las demás, o que la lista observable por la hoja
 * se quede con la entrada quitada.
 */
class MusicRequestHistoryTest {

    @Before
    fun fresh() = MusicRequestHistory.clear()

    @Test
    fun `quitar una entrada no toca las demas`() {
        MusicRequestHistory.record("para estudiar")
        MusicRequestHistory.record("salsa de los 80")
        MusicRequestHistory.record("parecido a Redimi2")

        MusicRequestHistory.remove("salsa de los 80")

        assertEquals(listOf("parecido a Redimi2", "para estudiar"), MusicRequestHistory.recentPrompts())
    }

    @Test
    fun `quitar ignora mayusculas y espacios`() {
        MusicRequestHistory.record("Para Estudiar")
        MusicRequestHistory.remove("  para estudiar ")
        assertTrue(MusicRequestHistory.recentPrompts().isEmpty())
    }

    @Test
    fun `la lista observable sigue a los cambios`() {
        MusicRequestHistory.record("uno")
        MusicRequestHistory.record("dos")
        assertEquals(listOf("dos", "uno"), MusicRequestHistory.prompts.value)

        MusicRequestHistory.remove("dos")
        assertEquals(listOf("uno"), MusicRequestHistory.prompts.value)

        MusicRequestHistory.clear()
        assertTrue(MusicRequestHistory.prompts.value.isEmpty())
    }

    @Test
    fun `quitar algo que no esta no rompe nada`() {
        MusicRequestHistory.record("uno")
        MusicRequestHistory.remove("otra")
        assertEquals(listOf("uno"), MusicRequestHistory.recentPrompts())
    }
}
