package iad1tya.echo.music.playback

/**
 * Qué hacer cuando el MODO VIDEO falla, sin tocar el audio.
 *
 * ## El fallo que arregla (registro del dueño, 2026-09-16, `f6f0dqs4Ax4`)
 * ```
 * 20:29:26.970 PipePipe adaptive video picked itag=136 (720p, cap=720p)
 * 20:29:27.378 Source error <- ... sniff failures: [NoDeclaredBrand, NoDeclaredBrand]   ← 3003 #1
 * 20:29:27.572 Player error ... errorCode=3003                                          ← 3003 #2
 * 20:29:27.572 CONTAINER_3003 id=f6f0dqs4Ax4 attempts=1 noBrand=true
 * 20:29:33.434 ... errorCode=2000                                                       ← lo que VE él
 * 20:29:38.812 Song f6f0dqs4Ax4 has exceeded retry limit, skipping
 * ```
 * Dos defectos encadenados, y ninguno es del audio:
 *
 * 1. **La carrera.** El 3003 llega DOS veces (la fuente de video y la de audio del `MergingMediaSource`
 *    reportan por separado). El primero sí se reconoce como fallo de video y `exitVideoMode()` limpia
 *    `_videoMode`, `videoModeMediaId` y `videoModeItems`. Doscientos milisegundos después llega el
 *    segundo — y como la limpieza ya pasó, [VideoModePlanning.isVideoFailure] dice "no es video" y el
 *    error entra por la rama de CONTENEDOR CORRUPTO: `performAggressiveCacheClear` **borra los bytes
 *    buenos del audio**, borra el `FormatEntity`, y a partir de ahí la canción se cae sola hasta que se
 *    salta. Por eso el error que aparece en pantalla es `io_unspecified (2000)` y no el 3003 real:
 *    el 2000 es el último eslabón de una cascada que empezó en el video.
 *    → [stillVideoFailure] mantiene el id marcado como "fallo de video" durante una ventana corta,
 *    así el segundo error no puede disfrazarse de audio corrupto.
 *
 * 2. **No se cambia de formato.** Cada reintento volvía a pedir el MISMO itag 136 (video-only, sin
 *    `ftyp` sniffable en esa respuesta), así que los tres intentos eran idénticos y fallaban igual.
 *    → [sourceForAttempt] baja por una escalera: otro itag adaptativo → InnerTube → muxed (22/18).
 *    Cuatro fuentes distintas antes de rendirse, y el audio sigue sonando durante todas.
 *
 * El dueño lo pidió sin margen: *"con ninguna quiero ese error"* y *"sí o sí los videos permitan
 * ponerse en modo canción sin excepciones"*. Rendirse aquí devuelve a AUDIO con la canción intacta
 * (nunca un salto, nunca `markSongAsFailed`), que es el peor caso aceptable.
 */
object VideoFormatFallback {

    /**
     * Cuánto sigue contando un fallo de video como fallo de video después de limpiar el estado.
     *
     * El eco medido fue de 194 ms (27.378 → 27.572), pero un `MergingMediaSource` que ya empezó a
     * rebuferear puede tardar más en soltar el suyo, y equivocarse hacia el lado largo solo cuesta
     * clasificar como "video" un fallo de audio que habría ocurrido igual dentro de esos segundos;
     * equivocarse hacia el lado corto cuesta el caché de audio y la canción.
     */
    const val STICKY_FAILURE_WINDOW_MS = 6_000L

    /** Fuentes de la escalera, en el orden en que se prueban. */
    enum class Source {
        /** El camino rápido normal: PipePipe/ANDROID_VR, mejor video-only H.264 bajo el tope. */
        PIPEPIPE_ADAPTIVE,

        /** Igual, pero prohibiendo los itags que ya reventaron para este id. */
        PIPEPIPE_ADAPTIVE_EXCLUDING,

        /** InnerTube: otro cliente, otra URL, otro host — no comparte la causa con PipePipe. */
        INNERTUBE,

        /** Progresivo 22/18: lleva audio dentro, es lo que más veces sobrevive a un sniff. */
        PIPEPIPE_MUXED,
    }

    /** Itags progresivos (llevan imagen y sonido en el mismo fichero), de mejor a peor. */
    val MUXED_ITAGS = listOf(22, 18)

    /**
     * Qué fuente toca en el intento [attempt] (0 = la primera resolución, la de siempre).
     * `null` = ya no queda escalera; el llamante vuelve a audio.
     */
    fun sourceForAttempt(attempt: Int): Source? = when (attempt) {
        0 -> Source.PIPEPIPE_ADAPTIVE
        1 -> Source.PIPEPIPE_ADAPTIVE_EXCLUDING
        2 -> Source.INNERTUBE
        3 -> Source.PIPEPIPE_MUXED
        else -> null
    }

    /** Intentos totales de la escalera, incluida la primera resolución. */
    val MAX_ATTEMPTS: Int = generateSequence(0) { it + 1 }.first { sourceForAttempt(it) == null }

    /** Queda algo por probar después de [attemptsSoFar] intentos fallidos. */
    fun hasNextSource(attemptsSoFar: Int): Boolean = sourceForAttempt(attemptsSoFar) != null

    /**
     * El itag que acaba de fallar se añade a los prohibidos. [failedItag] `null` (una URL de caché o
     * de export, sin itag conocido) deja el conjunto igual en vez de inventarse una exclusión.
     */
    fun exclusionsAfter(alreadyExcluded: Set<Int>, failedItag: Int?): Set<Int> =
        if (failedItag == null) alreadyExcluded else alreadyExcluded + failedItag

    /**
     * ¿Este error sigue perteneciendo al video, aunque el estado de modo video ya se haya limpiado?
     *
     * Solo para el MISMO id y dentro de [STICKY_FAILURE_WINDOW_MS]. Un id distinto, o el mismo id
     * mucho después, es un fallo de audio de verdad y tiene que seguir yendo por su rama de siempre
     * (borrar bytes corruptos es lo correcto cuando los bytes SON los corruptos).
     */
    fun stillVideoFailure(
        mediaId: String?,
        lastVideoFailureId: String?,
        lastVideoFailureAtMs: Long,
        nowMs: Long,
    ): Boolean =
        mediaId != null &&
            mediaId == lastVideoFailureId &&
            nowMs - lastVideoFailureAtMs in 0..STICKY_FAILURE_WINDOW_MS
}
