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
