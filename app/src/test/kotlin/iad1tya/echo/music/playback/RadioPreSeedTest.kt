package iad1tya.echo.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el arreglo de *"estoy en un álbum […] cambiando las canciones de manera manual […] de la nada
 * me reproduce música de otro artista o otra canción que no corresponde a la cola actual"* (dueño,
 * 2026-09-17, y ya reportado antes).
 *
 * El adelanto de la radio infinita se disparaba con cualquier motivo de transición menos REPEAT —
 * incluido un SEEK manual — así que saltar a mano hasta la última canción le metía ahí mismo canciones
 * ajenas. Estas pruebas fijan que **solo** un avance natural (o una cola que nace en su último
 * elemento) puede sembrar por adelantado.
 */
class RadioPreSeedTest {

    /** Un avance natural SÍ: el álbum llegó de verdad a su final y el empalme no debe tener hueco. */
    @Test
    fun `un avance natural siembra por adelantado`() {
        assertTrue(RadioQueueShaping.mayPreSeedRadio(isAuto = true, isPlaylistChanged = false))
    }

    /**
     * Una cola NUEVA que nace en su último elemento también: una cola de un solo tema no tiene nada
     * detrás, y sin esto la música se pararía al acabarlo.
     */
    @Test
    fun `una cola que nace en su ultimo elemento siembra`() {
        assertTrue(RadioQueueShaping.mayPreSeedRadio(isAuto = false, isPlaylistChanged = true))
    }

    /**
     * EL FALLO: cualquier otro motivo — y el que importa es el SEEK manual — NO puede sembrar. Saltar
     * a mano no es una cola terminada, y meterle canciones ajenas en ese momento es exactamente lo que
     * él reportó dos veces.
     *
     * No se pierde nada: el salto manual a la última pista ya lo cubre la red de `STATE_ENDED`, que
     * está siempre activa y que existe declaradamente por este caso.
     */
    @Test
    fun `un salto manual no le mete canciones ajenas al album`() {
        assertFalse(
            "un SEEK manual no es una cola terminada",
            RadioQueueShaping.mayPreSeedRadio(isAuto = false, isPlaylistChanged = false),
        )
    }

    /**
     * Y la invariante, que es lo que de verdad hay que impedir que vuelva: si alguien "simplifica" esto
     * a `reason != REPEAT`, los dos casos falsos de arriba volverían a ser verdad a la vez.
     */
    @Test
    fun `los dos motivos son necesarios, ninguno basta por defecto`() {
        assertFalse(RadioQueueShaping.mayPreSeedRadio(isAuto = false, isPlaylistChanged = false))
        assertTrue(RadioQueueShaping.mayPreSeedRadio(isAuto = true, isPlaylistChanged = true))
    }
}

/**
 * Row 503 (owner 2026-10-29: «la cola inteligente debería ya estar preparada cuando voy a cambiar la última canción»): the same
 * seed one song EARLIER, with every restriction of the head-start above (natural advance only) plus the ones that keep the album
 * an album: in order, repeat off, a collection with no next page, and only when the NEXT item is the last.
 */
class RadioEarlyPrimeTest {
    private fun may(
        isAuto: Boolean = true,
        shuffle: Boolean = false,
        repeatOff: Boolean = true,
        isCollection: Boolean = true,
        hasNextPage: Boolean = false,
        itemCount: Int = 10,
        nextIndex: Int = 9,
    ) = RadioQueueShaping.mayPrimeEarly(isAuto, shuffle, repeatOff, isCollection, hasNextPage, itemCount, nextIndex)

    @Test
    fun `the song before the last of a finished album primes the continuation`() {
        assertTrue(may())
        assertTrue(may(itemCount = 2, nextIndex = 1))
    }

    @Test
    fun `a manual jump, shuffle, repeat, a single song or an album with more pages never primes early`() {
        assertFalse("un salto manual no es una cola terminada", may(isAuto = false))
        assertFalse(may(shuffle = true))
        assertFalse(may(repeatOff = false))
        assertFalse("una canción suelta no es una colección", may(isCollection = false))
        assertFalse("una lista con más páginas sigue paginando", may(hasNextPage = true))
    }

    @Test
    fun `only the song right before the last one primes, never earlier and never the last itself`() {
        assertFalse(may(nextIndex = 8))
        assertFalse("el siguiente aún no es el último", may(nextIndex = 5))
        assertFalse("la última canción ya la cubre el adelanto de siempre", may(nextIndex = -1))
        assertFalse(may(itemCount = 1, nextIndex = 0))
    }
}
