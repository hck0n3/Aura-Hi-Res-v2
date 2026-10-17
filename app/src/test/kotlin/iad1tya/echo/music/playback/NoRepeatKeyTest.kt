package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Bloquea la regla de la cola inteligente del dueño (2026-10-17): *"no debe repetir ninguna canción en la
 * misma cola ni en el listado álbum o EP o cualquier canción que se haya escuchado antes"*. La misma
 * canción llega con ids distintos (álbum, vídeo oficial, remasterizada, "feat."); la clave tiene que
 * verlas como una sola — y NO fundir grabaciones realmente distintas (en vivo, remix, acústica).
 */
class NoRepeatKeyTest {

    private fun key(title: String, artist: String? = "Artist") = NoRepeatKey.of(title, artist)

    @Test
    fun `el empaquetado no crea otra cancion`() {
        val base = key("Hello")
        assertEquals(base, key("Hello (Official Video)"))
        assertEquals(base, key("Hello [Remastered 2011]"))
        assertEquals(base, key("Hello - Remastered 2011"))
        assertEquals(base, key("Hello - Single Version"))
        assertEquals(base, key("Hello - Radio Edit"))
        assertEquals(base, key("Hello (feat. Someone)"))
        assertEquals(base, key("Hello feat. Someone"))
        assertEquals(base, key("HELLO"))
    }

    @Test
    fun `acentos y artista Topic o VEVO son lo mismo`() {
        assertEquals(key("Café", "Beyoncé"), key("Cafe", "Beyonce"))
        assertEquals(key("Hello", "Adele"), key("Hello", "Adele - Topic"))
        assertEquals(key("Hello", "Adele"), key("Hello", "AdeleVEVO"))
    }

    @Test
    fun `grabaciones distintas siguen siendo distintas`() {
        val base = key("Hello")
        assertNotEquals(base, key("Hello (Live)"))
        assertNotEquals(base, key("Hello - Live at Wembley"))
        assertNotEquals(base, key("Hello (Acoustic)"))
        assertNotEquals(base, key("Hello (Remix)"))
        assertNotEquals(base, key("Hello - Instrumental"))
    }

    @Test
    fun `otra cancion u otro artista no se funde`() {
        assertNotEquals(key("Hello"), key("Hello Again"))
        assertNotEquals(key("Hello", "Adele"), key("Hello", "Lionel Richie"))
        assertNotEquals(key("Un - Dos"), key("Un"))
    }
}
