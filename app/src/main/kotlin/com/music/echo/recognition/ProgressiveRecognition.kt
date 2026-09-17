package iad1tya.echo.music.recognition

/**
 * La agenda de la identificación PROGRESIVA: cuándo preguntar mientras el micrófono sigue grabando.
 *
 * ## Por qué existe (dueño, 2026-09-17)
 * *"la manera en que YouTube Music identifica una canción es más rápida que la función que ofrezco
 * yo, y no solo es más rápida: se siente más efectiva"*.
 *
 * Tenía razón en las dos mitades, y las dos salen de lo mismo. Lo de antes:
 *
 * 1. grabar **10 segundos fijos** sin mandar nada;
 * 2. generar la huella y hacer **UNA** consulta;
 * 3. si no había coincidencia, volver a grabar **12 segundos desde cero** y consultar otra vez.
 *
 * O sea 10 s en el mejor caso, 22 s de grabación y dos viajes en el peor — y **un solo intento por
 * captura**. Si el primer segundo pilló a alguien hablando encima, esa huella nacía mala y no había
 * vuelta atrás; por eso además de lenta se sentía menos certera.
 *
 * YouTube Music y Shazam mandan la huella a los ~3 s y **siguen mandando ventanas cada vez más
 * largas** hasta que el servidor acierta. No es otra tecnología ni mejor micrófono: es la agenda.
 *
 * ## Lo que esto cambia
 * Una sola grabación que crece, y consultas a los [CHECKPOINTS_MS] con lo acumulado hasta ese
 * momento. La primera que acierte corta todo. El micrófono **no se detiene** mientras una consulta
 * viaja, que es lo que hace que la siguiente ventana ya esté lista cuando vuelve.
 *
 * - Acierto típico: 3-5 s en vez de 10-12.
 * - Aciertos donde antes fallaba: cuatro ventanas distintas, y las largas ya incluyen audio limpio.
 * - Peor caso: 12 s en vez de 22.
 *
 * Nada de esto toca el proveedor ni el generador de huellas: `Shazam.recognize(firma, duraciónMs)`
 * no guarda estado entre llamadas, así que una huella de 3 s es tan válida como una de 10.
 */
object ProgressiveRecognition {

    /**
     * Los momentos, en ms desde que arranca el micrófono, en que se consulta con lo grabado hasta ahí.
     *
     * **3 s** es el primero porque por debajo de eso la huella casi nunca tiene picos suficientes y la
     * consulta se gasta en un fallo seguro; es también donde arrancan Shazam y YouTube Music.
     *
     * Después **casi se dobla cada vez** (3 → 5 → 8 → 12) en vez de ir de segundo en segundo: cada
     * ventana tiene que aportar bastante audio NUEVO para merecer su viaje de red, y una progresión
     * plana gastaría consultas en huellas casi idénticas a la anterior. Cuatro intentos en 12 s,
     * separados 2-4 s — muy por encima del mínimo de 1 s que impone el limitador del cliente Shazam,
     * así que la agenda no puede chocar con él.
     *
     * **12 s es el final** y no más: pasado eso, si cuatro ventanas distintas no acertaron, el problema
     * no es la cantidad de audio (demasiado ruido, música que no está en el catálogo, una versión en
     * vivo) y seguir grabando solo gasta batería delante de alguien que ya está esperando.
     */
    val CHECKPOINTS_MS: List<Long> = listOf(3_000L, 5_000L, 8_000L, 12_000L)

    /** Cuánto graba como mucho una sesión: el último punto de consulta. */
    val MAX_RECORDING_MS: Long get() = CHECKPOINTS_MS.last()

    /** El último punto — el único cuyo "no encontrado" es definitivo. */
    fun isFinal(checkpointMs: Long): Boolean = checkpointMs == CHECKPOINTS_MS.last()

    /**
     * Cuántos BYTES de PCM 16-bit mono a [sampleRate] representan [ms] de audio.
     *
     * Se usa para recortar la instantánea del búfer al punto exacto: sin esto, una consulta que sale
     * tarde mandaría una ventana más larga de la que le toca y las cuatro ventanas dejarían de estar
     * separadas como dice [CHECKPOINTS_MS]. Par siempre — media muestra partida por la mitad rompe el
     * generador de huellas, que exige `size % 2 == 0`.
     */
    fun bytesFor(ms: Long, sampleRate: Int): Int {
        val bytes = ms * sampleRate * 2 / 1000
        val capped = bytes.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return capped - (capped % 2)
    }

    /**
     * ¿Hay suficiente audio para consultar en [checkpointMs]?
     *
     * Se acepta lo que haya cuando la grabación YA TERMINÓ ([recordingFinished]) aunque se quede corto:
     * un micrófono que devolvió menos de lo pedido (el sistema cortó la sesión, el aparato es lento)
     * no puede dejar la última consulta sin hacerse — eso convertiría un fallo de captura en un "no se
     * encontró", que es mentira y además es el peor mensaje posible.
     */
    fun readyToQuery(bufferedBytes: Int, checkpointMs: Long, sampleRate: Int, recordingFinished: Boolean): Boolean {
        if (bufferedBytes < MIN_QUERYABLE_BYTES) return false
        return recordingFinished || bufferedBytes >= bytesFor(checkpointMs, sampleRate)
    }

    /** Por debajo de esto no hay huella que valga; evita mandar un viaje de red condenado. */
    const val MIN_QUERYABLE_BYTES = 8_000
}
