package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el arreglo de *"a veces las canciones inician cortadas cuando cambio a mano, y en Android
 * Auto también"* (dueño, 2026-09-17).
 *
 * La asimetría que gobierna todas estas pruebas: quedarse sin fundido no se nota; quedarse sin el
 * principio de la canción, o con la canción entera a un tercio de volumen, se nota siempre.
 */
class ManualFadeInTest {

    /**
     * El corazón del fallo en el coche: `isPlaying` es falso mientras el foco está suprimido, pero el
     * audio YA está saliendo. Esperar a `isPlaying` con el volumen en 0 era regalar ese principio.
     */
    @Test
    fun `sonar no es isPlaying — basta con estar listo y con playWhenReady`() {
        assertTrue(ManualFadeIn.audible(ready = true, playWhenReady = true))
        assertFalse(ManualFadeIn.audible(ready = false, playWhenReady = true))
        assertFalse(ManualFadeIn.audible(ready = true, playWhenReady = false))
        assertFalse(ManualFadeIn.audible(ready = false, playWhenReady = false))
    }

    @Test
    fun `la espera muda se corta en cuanto suena`() {
        assertFalse(ManualFadeIn.shouldKeepWaiting(waitedMs = 0, audible = true))
        assertTrue(ManualFadeIn.shouldKeepWaiting(waitedMs = 0, audible = false))
        assertTrue(ManualFadeIn.shouldKeepWaiting(waitedMs = 500, audible = false))
    }

    /**
     * Y NUNCA dura ocho segundos. Ese era el tope viejo: hasta ocho segundos de canción sonando a
     * volumen cero, y al agotarse todavía rampaba desde cero.
     */
    @Test
    fun `la espera muda nunca se come el principio de la cancion`() {
        assertFalse(
            ManualFadeIn.shouldKeepWaiting(waitedMs = ManualFadeIn.MAX_SILENT_WAIT_MS, audible = false),
        )
        assertFalse(ManualFadeIn.shouldKeepWaiting(waitedMs = 8_000, audible = false))
        assertTrue(
            "esperar más que un par de fundidos no compra nada",
            ManualFadeIn.MAX_SILENT_WAIT_MS <= 4 * ManualFadeIn.RAMP_MS,
        )
    }

    /** El primer escalón tiene que oírse; el último tiene que ser el volumen entero del usuario. */
    @Test
    fun `la rampa empieza audible y termina exacta`() {
        assertEquals(ManualFadeIn.FLOOR, ManualFadeIn.stepGain(0), 1e-6f)
        assertTrue(ManualFadeIn.stepGain(1) >= ManualFadeIn.FLOOR)
        assertEquals(1f, ManualFadeIn.stepGain(ManualFadeIn.STEPS), 1e-6f)
    }

    @Test
    fun `la rampa es monotona y nunca se pasa`() {
        var previous = -1f
        for (i in 0..ManualFadeIn.STEPS) {
            val gain = ManualFadeIn.stepGain(i)
            assertTrue("el escalón $i baja", gain >= previous)
            assertTrue("el escalón $i se pasa: $gain", gain <= 1f)
            previous = gain
        }
        // Fuera de rango no revienta ni devuelve basura.
        assertEquals(1f, ManualFadeIn.stepGain(999), 1e-6f)
        assertEquals(ManualFadeIn.FLOOR, ManualFadeIn.stepGain(-5), 1e-6f)
    }

    /**
     * EL agujero: el bucle cortaba con `isCrossfading` y el `finally` restauraba solo `!isCrossfading`,
     * o sea que un crossfade encima dejaba el volumen clavado a media rampa PARA SIEMPRE. Restaurar
     * ahora no depende del crossfade — el crossfade fija su propio volumen en el primer tic.
     */
    @Test
    fun `el volumen siempre vuelve al del usuario`() {
        assertEquals(0.8f, ManualFadeIn.finalVolume(isCurrentJob = true, muted = false, userVolume = 0.8f))
        assertEquals(0f, ManualFadeIn.finalVolume(isCurrentJob = true, muted = true, userVolume = 0.8f))
    }

    /**
     * La única razón legítima para no tocar el volumen: una entrada MÁS NUEVA ya es su dueña (acaba de
     * ponerlo a 0 para su propia rampa) y pisarla mataría ese fundido en cada salto rápido.
     */
    @Test
    fun `una entrada mas nueva manda y la vieja no la pisa`() {
        assertNull(ManualFadeIn.finalVolume(isCurrentJob = false, muted = false, userVolume = 0.8f))
        assertNull(ManualFadeIn.finalVolume(isCurrentJob = false, muted = true, userVolume = 0.8f))
    }

    @Test
    fun `sin volumen que alcanzar no hay fundido que hacer`() {
        assertFalse(ManualFadeIn.worthFading(muted = true, userVolume = 1f))
        assertFalse(ManualFadeIn.worthFading(muted = false, userVolume = 0f))
        assertTrue(ManualFadeIn.worthFading(muted = false, userVolume = 0.01f))
    }
}
