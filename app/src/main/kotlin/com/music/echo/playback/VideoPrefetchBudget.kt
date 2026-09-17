package iad1tya.echo.music.playback

/**
 * # Cuántos vídeos se pueden resolver por adelantado
 *
 * 🔴 OWNER REPORT (2026-09-16): *"siento que tarda entre cambiar música y vídeo y viceversa, ¿hay alguna
 * manera de hacerlo más rápido?"*.
 *
 * El cambio es instantáneo cuando la URL del vídeo ya está resuelta, y lento cuando hay que resolverla en
 * el momento (cifrado + PoToken, segundos). Lo que decide entre una cosa y otra es este presupuesto.
 *
 * ## Qué estaba mal
 * Era un contador de **por vida**: `if (speculativeVideoPrefetches >= 8) return`, sin recarga. Los ocho
 * primeros cambios de canción con vídeo gastaban el presupuesto entero del proceso y, a partir de ahí,
 * **ningún** vídeo volvía a pre-resolverse mientras la app siguiera viva. Escuchando un rato, o sea justo
 * como se usa la app, todo cambio a vídeo pagaba el resolve completo. No era una sensación suya.
 *
 * ## Por qué no se quita el tope y ya está
 * El tope no es capricho: cada resolve especulativo compite por los mutex del WebView con el resolve del
 * audio que está sonando, y eso corta el audio. Es la regla de calor/batería del repo. Un presupuesto
 * **sin** límite cambiaría un fallo por otro peor.
 *
 * ## Lo que hace ahora
 * Un cubo de fichas: como mucho [capacity] resolves seguidos, y una ficha nueva cada
 * [refillIntervalMs]. Acotado igual en ráfaga (nunca más de 8 a la vez, que es lo que protegía al audio)
 * y ya no se agota para siempre: tras un par de minutos de música el cambio vuelve a ser instantáneo. El
 * gasto máximo sostenido queda en una resolución cada dos minutos, mucho menos que una por canción.
 *
 * Las salvaguardas de calor, de modo de ahorro de datos y de contención con el resolve de audio siguen
 * ANTES que esto en `prefetchCurrentVideoUrl` y no las toca: este objeto solo decide el ritmo, nunca
 * anula un dispositivo caliente.
 *
 * Es una clase pura, con el reloj como parámetro, para que su comportamiento se pueda comprobar en un
 * test en vez de a ojo.
 */
internal class VideoPrefetchBudget(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val refillIntervalMs: Long = DEFAULT_REFILL_INTERVAL_MS,
) {
    private var tokens = capacity

    /**
     * Cuándo se contó la última recarga. Arranca sin fijar para que el reloj del cubo empiece con el
     * primer uso real: si se fijara en la construcción, un proceso que pasa media hora en segundo plano
     * llegaría al primer cambio con el cubo lleno por el mero paso del tiempo, que no es lo que se está
     * limitando.
     */
    private var lastRefillMs: Long? = null

    /** Fichas disponibles ahora mismo. Solo para tests y registro; no decide nada por sí sola. */
    @get:Synchronized
    internal val available: Int get() = tokens

    /**
     * Gasta una ficha si hay. Se llama en el punto donde el resolve va a empezar DE VERDAD (después de
     * todas las demás guardas), no antes: así una ficha nunca se pierde en una llamada que iba a salirse
     * de todas formas, que es lo que hacía el contador viejo.
     */
    @Synchronized
    fun tryConsume(nowMs: Long): Boolean {
        val last = lastRefillMs
        if (last == null) {
            lastRefillMs = nowMs
        } else {
            val elapsed = nowMs - last
            if (elapsed >= refillIntervalMs) {
                val refills = elapsed / refillIntervalMs
                // coerceAtMost: el cubo no acumula crédito por estar parado, solo se llena.
                tokens = (tokens + refills).coerceAtMost(capacity.toLong()).toInt()
                lastRefillMs = last + refills * refillIntervalMs
            }
        }
        if (tokens <= 0) return false
        tokens--
        return true
    }

    companion object {
        /** La ráfaga que ya protegía al audio. Se conserva tal cual: no es esta parte la que fallaba. */
        const val DEFAULT_CAPACITY = 8

        /** Una ficha cada dos minutos: más lento que una canción, así que nunca resuelve todas. */
        const val DEFAULT_REFILL_INTERVAL_MS = 2 * 60 * 1000L
    }
}
