package iad1tya.echo.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el arreglo de *"las playlist que solo contienen video, cuando intento cambiar al siguiente no
 * me cambia al siguiente video que sigue dentro de la playlist, como si la cola estuviera mala […] veo
 * que me los agarra como canción independiente como si no detectara las colas"* (dueño, 2026-09-17).
 *
 * La cola nunca estuvo mala: **cada** cambio de pista salía de modo vídeo, así que en una lista donde
 * todo es vídeo había que volver a tocar Vídeo en cada elemento — y eso se siente exactamente como que
 * la cola no existe.
 */
class VideoQueueCarryTest {

    /** Lo que él pidió: vídeo → vídeo se queda en vídeo. */
    @Test
    fun `en una lista de solo video el siguiente sigue en video`() {
        assertTrue(
            VideoModePlanning.keepVideoOnTrackChange(incomingIsVideoSong = true, sameTrack = false),
        )
    }

    /**
     * Y lo que NO se toca: la directiva de "música primero" del 2026-09-14 existía para que una canción
     * suelta puesta en vídeo no secuestrara la siguiente. Si la pista entrante no tiene vídeo, se baja a
     * audio igual que antes — en una lista mixta el comportamiento es el de siempre.
     */
    @Test
    fun `una pista sin video sigue bajando a audio`() {
        assertFalse(
            "la música primero se conserva para el caso que la motivó",
            VideoModePlanning.keepVideoOnTrackChange(incomingIsVideoSong = false, sameTrack = false),
        )
    }

    /**
     * Un re-prepare de la MISMA pista no es un cambio de pista y no puede sacar de vídeo: `exitVideoMode`
     * hace un `seekTo` internamente que vuelve a disparar este callback, y salir ahí haría que el botón
     * de Vídeo pareciera roto — el mismo agujero que ya está documentado con `userExplicitlyExitedVideo`.
     */
    @Test
    fun `un re-prepare de la misma pista nunca sale de video`() {
        assertTrue(VideoModePlanning.keepVideoOnTrackChange(incomingIsVideoSong = false, sameTrack = true))
        assertTrue(VideoModePlanning.keepVideoOnTrackChange(incomingIsVideoSong = true, sameTrack = true))
    }
}
