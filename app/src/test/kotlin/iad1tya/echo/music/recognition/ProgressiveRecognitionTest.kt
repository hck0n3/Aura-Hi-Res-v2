package iad1tya.echo.music.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bloquea la identificación progresiva (dueño, 2026-09-17: *"la manera en que YouTube Music
 * identifica una canción es más rápida […] y se siente más efectiva"*, y *"mejóralo bajo tus
 * recomendaciones, sin que sufra daños pero que acelere la detección"*).
 *
 * La condición "sin que sufra daños" es la que gobierna la mitad de estas pruebas: la ventana más
 * larga tiene que seguir siendo la que ya funcionaba, y ninguna consulta puede salir con menos audio
 * del que la huella necesita.
 */
class ProgressiveRecognitionTest {

    private val sampleRate = 44_100

    /**
     * Lo de antes era UNA consulta a los 10 s (y una segunda captura de 12 s desde cero). Lo que hace
     * que esto sea más rápido Y más certero es que haya VARIAS ventanas, la primera pronto.
     */
    @Test
    fun `se pregunta varias veces y la primera es pronto`() {
        val checkpoints = ProgressiveRecognition.CHECKPOINTS_MS
        assertTrue("con una sola ventana no se gana nada", checkpoints.size >= 3)
        assertTrue("la primera consulta tiene que salir mucho antes que los 10 s de antes", checkpoints.first() <= 3_500L)
    }

    /** Creciente y sin repetidos: dos ventanas iguales gastarían un viaje de red en la misma huella. */
    @Test
    fun `las ventanas crecen y ninguna se repite`() {
        val checkpoints = ProgressiveRecognition.CHECKPOINTS_MS
        assertEquals(checkpoints.size, checkpoints.toSet().size)
        assertEquals(checkpoints.sorted(), checkpoints)
    }

    /**
     * El limitador del cliente Shazam exige 1 s mínimo entre consultas. Si dos puntos quedaran más
     * juntos, la segunda se dormiría esperando — y el retraso saldría justo del tiempo que esto
     * pretende ahorrar.
     */
    @Test
    fun `ninguna ventana choca con el limitador de un segundo`() {
        ProgressiveRecognition.CHECKPOINTS_MS.zipWithNext { a, b ->
            assertTrue("$a y $b están a menos de 1 s", b - a >= 1_000L)
        }
    }

    /**
     * "Sin que sufra daños": el último punto es la ventana de 12 s que la captura de reintento ya
     * usaba, o sea la más larga que se sabe que funcionaba. Y no más, porque si cuatro ventanas
     * distintas no acertaron el problema no es la cantidad de audio.
     */
    @Test
    fun `la ultima ventana es la que ya funcionaba y no se alarga`() {
        assertEquals(12_000L, ProgressiveRecognition.MAX_RECORDING_MS)
        assertEquals(ProgressiveRecognition.CHECKPOINTS_MS.last(), ProgressiveRecognition.MAX_RECORDING_MS)
        assertTrue(
            "grabar más que antes sería empeorar la espera, no mejorarla",
            ProgressiveRecognition.MAX_RECORDING_MS <= 12_000L,
        )
    }

    /** Solo el último "no encontrado" es definitivo; los intermedios tienen que dejar seguir. */
    @Test
    fun `solo la ultima ventana cierra la sesion`() {
        val checkpoints = ProgressiveRecognition.CHECKPOINTS_MS
        checkpoints.dropLast(1).forEach { assertFalse("$it no puede ser final", ProgressiveRecognition.isFinal(it)) }
        assertTrue(ProgressiveRecognition.isFinal(checkpoints.last()))
    }

    /**
     * El recorte es lo que mantiene las ventanas separadas: sin él, una consulta que sale tarde
     * mandaría más audio del que le toca y la siguiente sería casi idéntica.
     */
    @Test
    fun `los bytes de cada ventana salen del reloj, no de lo que haya grabado`() {
        // 1 s de PCM 16-bit mono a 44.1 kHz = 44100 muestras x 2 bytes.
        assertEquals(88_200, ProgressiveRecognition.bytesFor(1_000, sampleRate))
        assertEquals(264_600, ProgressiveRecognition.bytesFor(3_000, sampleRate))
        assertEquals(0, ProgressiveRecognition.bytesFor(0, sampleRate))
    }

    /** Impar rompe el generador de huellas, que exige `size % 2 == 0`. */
    @Test
    fun `el recorte nunca parte una muestra por la mitad`() {
        for (ms in listOf(1L, 7L, 13L, 999L, 3_001L, 12_000L)) {
            assertEquals("ms=$ms", 0, ProgressiveRecognition.bytesFor(ms, sampleRate) % 2)
        }
        // Y con un reloj absurdo tampoco desborda a negativo.
        assertTrue(ProgressiveRecognition.bytesFor(Long.MAX_VALUE, sampleRate) >= 0)
    }

    @Test
    fun `no se consulta hasta tener audio de sobra para esa ventana`() {
        val needed = ProgressiveRecognition.bytesFor(3_000, sampleRate)
        assertFalse(ProgressiveRecognition.readyToQuery(needed - 1, 3_000, sampleRate, recordingFinished = false))
        assertTrue(ProgressiveRecognition.readyToQuery(needed, 3_000, sampleRate, recordingFinished = false))
    }

    /**
     * Pero si la grabación YA TERMINÓ se acepta lo que haya: un micrófono que devolvió menos de lo
     * pedido no puede dejar la última consulta sin hacerse. Convertir un fallo de captura en un "no se
     * encontró la canción" sería mentirle y encima le haría culpar a la música.
     */
    @Test
    fun `una grabacion corta no deja la ultima consulta sin hacerse`() {
        val needed = ProgressiveRecognition.bytesFor(12_000, sampleRate)
        assertTrue(
            ProgressiveRecognition.readyToQuery(needed / 2, 12_000, sampleRate, recordingFinished = true),
        )
    }

    /** Aun así, por debajo del mínimo no se gasta un viaje de red en una huella condenada. */
    @Test
    fun `sin audio utilizable no se gasta una consulta`() {
        assertFalse(ProgressiveRecognition.readyToQuery(0, 3_000, sampleRate, recordingFinished = true))
        assertFalse(
            ProgressiveRecognition.readyToQuery(
                ProgressiveRecognition.MIN_QUERYABLE_BYTES - 1,
                12_000,
                sampleRate,
                recordingFinished = true,
            ),
        )
    }
}
