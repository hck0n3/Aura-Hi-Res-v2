package iad1tya.echo.music.playback

/**
 * La entrada suave al cambiar de canción A MANO — y las tres formas en que se comía el principio.
 *
 * ## El síntoma (dueño, 2026-09-17)
 * *"a veces, no siempre, siento que las canciones inician cortadas cuando estoy cambiando canciones
 * de manera manual, y en Android Auto también"*.
 *
 * El fundido existe desde *"cuando cambio una canción cae de golpe"*: al saltar a otra canción el
 * volumen sube de 0 al del usuario en ~400 ms. Correcto cuando funciona. Lo que fallaba:
 *
 * 1. **Se esperaba a `isPlaying` con el volumen ya en 0.** `isPlaying` es falso mientras el foco de
 *    audio está SUPRIMIDO (una indicación del navegador, un aviso del coche) aunque el reproductor
 *    siga sacando muestras, y falso en cada rebufer. En el coche eso pasa constantemente: el
 *    principio de la canción sonaba, pero a volumen cero. El tope era de OCHO segundos, y al
 *    agotarse todavía se rampaba desde cero — o sea, hasta ocho segundos de canción perdidos.
 *    → [audible] mira el estado real de reproducción, no `isPlaying`, y [shouldKeepWaiting] corta
 *    la espera muda en poco más de un segundo: perder el fundido es infinitamente mejor que perder
 *    el principio de la canción.
 *
 * 2. **La rampa se abandonaba a medias dejando el volumen bajo.** El bucle cortaba con
 *    `if (isCrossfading) break` y el `finally` solo restauraba `if (!isCrossfading)`: justo la misma
 *    condición, así que cuando empezaba un crossfade encima el volumen se quedaba clavado en el
 *    escalón que tocara (0.3 del usuario, por ejemplo) para siempre.
 *    → [finalVolume] decide la restauración SIN mirar el crossfade: el crossfade fija el volumen él
 *      mismo en su primer tic, así que restaurar no le quita nada, y en cambio cierra el agujero.
 *
 * 3. **Ese volumen a medias contaminaba el crossfade siguiente.** El blend tomaba su nivel base de
 *    `fadingPlayer.volume`, o sea del valor que la rampa abandonada hubiera dejado, y escalaba las
 *    DOS rampas por él: la canción entrante se quedaba a ese porcentaje toda la mezcla. Por eso el
 *    corte se "pegaba" de una canción a la siguiente. El arreglo de ese punto vive en MusicService
 *    (el nivel base pasa a ser el del usuario), pero la causa es esta.
 */
object ManualFadeIn {

    /**
     * Cuánto se puede esperar EN SILENCIO a que la canción empiece a sonar de verdad.
     *
     * Eran 8 s. El fundido dura 400 ms: si arrancar cuesta más de algo más de un segundo, el fundido
     * ya no aporta nada y lo único que hace la espera es tapar el principio. Al agotarse NO se rampa
     * desde cero — se devuelve el volumen del usuario de golpe (ver [finalVolume]).
     */
    const val MAX_SILENT_WAIT_MS = 1_200L

    /** Paso del sondeo de la espera. */
    const val WAIT_POLL_MS = 40L

    /** Duración de la rampa y su resolución. Sin cambios: esto ya sonaba bien. */
    const val RAMP_MS = 400L
    const val STEPS = 16

    /** Primer escalón audible (~−12 dB): nada de arrancar en silencio absoluto. */
    const val FLOOR = 0.25f

    /**
     * ¿Está saliendo audio ya?
     *
     * A propósito NO es `player.isPlaying`: esa propiedad es falsa mientras el foco está suprimido
     * (ducking, aviso de navegación) aunque el reproductor esté renderizando. Con el volumen pinchado
     * a 0 esperando, eso son segundos de canción que el usuario no oye nunca.
     */
    fun audible(ready: Boolean, playWhenReady: Boolean): Boolean = ready && playWhenReady

    /** Seguir esperando en silencio solo si aún no suena y no se ha agotado el poco margen que hay. */
    fun shouldKeepWaiting(waitedMs: Long, audible: Boolean): Boolean =
        !audible && waitedMs < MAX_SILENT_WAIT_MS

    /**
     * Ganancia del escalón [step] (1..[STEPS]) como fracción del volumen del usuario. Seno de
     * potencia constante desde [FLOOR] hasta 1.
     */
    fun stepGain(step: Int): Float {
        val p = step.coerceIn(0, STEPS) / STEPS.toFloat()
        return FLOOR + (1f - FLOOR) * kotlin.math.sin(p * (Math.PI / 2.0).toFloat())
    }

    /**
     * Volumen con el que hay que terminar SIEMPRE, pase lo que pase con la rampa.
     *
     * `null` = no tocar nada, y solo por una razón: otra entrada más nueva ya es la dueña del volumen
     * (acaba de ponerlo a 0 para su propia rampa) y pisarla mataría ese fundido. El crossfade ya NO
     * es motivo para no restaurar — era el agujero por el que se escapaba el volumen a medias.
     */
    fun finalVolume(isCurrentJob: Boolean, muted: Boolean, userVolume: Float): Float? = when {
        !isCurrentJob -> null
        muted -> 0f
        else -> userVolume
    }

    /** No tiene sentido rampar hacia el silencio: sin volumen que alcanzar, no hay fundido. */
    fun worthFading(muted: Boolean, userVolume: Float): Boolean = !muted && userVolume > 0f
}
