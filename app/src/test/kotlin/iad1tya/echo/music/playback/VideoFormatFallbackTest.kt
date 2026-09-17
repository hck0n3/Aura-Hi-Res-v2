package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea el arreglo de "cambio a video y me da error" (registro del dueño 2026-09-16, `f6f0dqs4Ax4`).
 *
 * Los dos defectos que estas pruebas impiden que vuelvan:
 *  - el ECO del 3003 colándose por la rama de audio corrupto y llevándose el caché bueno por delante;
 *  - los tres reintentos pidiendo el MISMO itag 136, o sea un intento repetido tres veces.
 */
class VideoFormatFallbackTest {

    @Test
    fun `la escalera baja por cuatro fuentes distintas y luego se acaba`() {
        assertEquals(VideoFormatFallback.Source.PIPEPIPE_ADAPTIVE, VideoFormatFallback.sourceForAttempt(0))
        assertEquals(VideoFormatFallback.Source.PIPEPIPE_ADAPTIVE_EXCLUDING, VideoFormatFallback.sourceForAttempt(1))
        assertEquals(VideoFormatFallback.Source.INNERTUBE, VideoFormatFallback.sourceForAttempt(2))
        assertEquals(VideoFormatFallback.Source.PIPEPIPE_MUXED, VideoFormatFallback.sourceForAttempt(3))
        assertNull(VideoFormatFallback.sourceForAttempt(4))
        assertEquals(4, VideoFormatFallback.MAX_ATTEMPTS)
    }

    /** Ningún peldaño repite fuente: si dos coincidieran, la escalera gastaría un intento en nada. */
    @Test
    fun `ningun peldano repite el intento anterior`() {
        val used = (0 until VideoFormatFallback.MAX_ATTEMPTS).map { VideoFormatFallback.sourceForAttempt(it) }
        assertEquals(used.size, used.toSet().size)
        assertTrue(used.all { it != null })
    }

    @Test
    fun `hay siguiente fuente hasta agotar la escalera`() {
        assertTrue(VideoFormatFallback.hasNextSource(1))
        assertTrue(VideoFormatFallback.hasNextSource(3))
        assertFalse(VideoFormatFallback.hasNextSource(VideoFormatFallback.MAX_ATTEMPTS))
        assertFalse(VideoFormatFallback.hasNextSource(99))
    }

    /**
     * EL defecto original: el itag que reventó tiene que quedar prohibido, porque volver a elegirlo es
     * repetir el intento. Un itag desconocido (URL de caché o de export) no inventa una exclusión.
     */
    @Test
    fun `el itag que revento queda prohibido y uno desconocido no inventa nada`() {
        assertEquals(setOf(136), VideoFormatFallback.exclusionsAfter(emptySet(), 136))
        assertEquals(setOf(136, 137), VideoFormatFallback.exclusionsAfter(setOf(136), 137))
        // Idempotente: el eco del mismo fallo no ensucia el conjunto.
        assertEquals(setOf(136), VideoFormatFallback.exclusionsAfter(setOf(136), 136))
        assertEquals(setOf(136), VideoFormatFallback.exclusionsAfter(setOf(136), null))
        assertEquals(emptySet<Int>(), VideoFormatFallback.exclusionsAfter(emptySet(), null))
    }

    /**
     * La carrera medida: 27.378 → 27.572, 194 ms. El segundo error llega con el estado de video ya
     * limpiado y tiene que seguir contando como fallo de VIDEO, o `performAggressiveCacheClear` borra
     * los bytes buenos del audio y la canción acaba saltada.
     */
    @Test
    fun `el eco del mismo fallo sigue siendo fallo de video`() {
        val t0 = 1_000_000L
        assertTrue(VideoFormatFallback.stillVideoFailure("f6f0dqs4Ax4", "f6f0dqs4Ax4", t0, t0 + 194))
        assertTrue(VideoFormatFallback.stillVideoFailure("f6f0dqs4Ax4", "f6f0dqs4Ax4", t0, t0))
        assertTrue(
            VideoFormatFallback.stillVideoFailure(
                "f6f0dqs4Ax4",
                "f6f0dqs4Ax4",
                t0,
                t0 + VideoFormatFallback.STICKY_FAILURE_WINDOW_MS,
            ),
        )
    }

    /**
     * Y la ventana no puede tragarse fallos que SÍ son de audio: otra canción, o la misma mucho
     * después, vuelve a su rama de siempre — borrar bytes corruptos es lo correcto cuando los bytes
     * son los corruptos.
     */
    @Test
    fun `la ventana no se traga los fallos de audio de verdad`() {
        val t0 = 1_000_000L
        assertFalse(VideoFormatFallback.stillVideoFailure("otra", "f6f0dqs4Ax4", t0, t0 + 100))
        assertFalse(
            VideoFormatFallback.stillVideoFailure(
                "f6f0dqs4Ax4",
                "f6f0dqs4Ax4",
                t0,
                t0 + VideoFormatFallback.STICKY_FAILURE_WINDOW_MS + 1,
            ),
        )
        assertFalse(VideoFormatFallback.stillVideoFailure(null, "f6f0dqs4Ax4", t0, t0 + 100))
        assertFalse(VideoFormatFallback.stillVideoFailure("f6f0dqs4Ax4", null, t0, t0 + 100))
        // Reloj hacia atrás (cambio de hora / elapsedRealtime reiniciado): no cuenta como eco.
        assertFalse(VideoFormatFallback.stillVideoFailure("f6f0dqs4Ax4", "f6f0dqs4Ax4", t0, t0 - 1))
    }

    /** El último peldaño existe de verdad: sin itags muxed no habría a dónde caer. */
    @Test
    fun `el peldano muxed tiene formatos que ofrecer`() {
        assertEquals(listOf(22, 18), VideoFormatFallback.MUXED_ITAGS)
    }
}
