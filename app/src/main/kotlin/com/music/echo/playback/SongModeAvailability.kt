package iad1tya.echo.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner 2026-10-20: *«cuando me meto al apartado "ver videos" dentro de los artistas, si esos videos no
 * se pueden poner en modo canción, que no salga modo canción, porque esto genera un error en la app»*.
 *
 * ## Qué pasa de verdad (su log `share_all_diagnostics_4.txt`, 10-19 23:41–23:44)
 * "Modo canción" de un vídeo = el audio sale del resolutor de canciones (ANDROID_VR + IOS contra
 * `music.youtube.com`). Muchos vídeos de la lista de un artista (subidas de fans, entrevistas, directos…)
 * NO están en el catálogo de YouTube Music: ese endpoint contesta `UNPLAYABLE: Este video no se encuentra
 * disponible` (y HTTP 400 en IOS) aunque PipePipe SÍ extrae el vídeo (se ve `fell back to muxed itag=18`
 * para los mismos ids). Consecuencias medidas en el log:
 *  1. tocar uno en modo canción = toast «Canción no disponible» + salto, cinco veces seguidas (la cola
 *     entera de 100 vídeos); y la sexta se queda en pausa sin decir nada;
 *  2. el MODO VÍDEO de esos ids también revienta: la fuente unida es vídeo-only + el audio NORMAL
 *     (`createMediaSource`), y ese audio es justo lo que falla → el error `Source error` se clasifica
 *     como fallo de VÍDEO y la escalera de formatos gira en vacío ("ladder exhausted", reentrada cada 5 s);
 *  3. al pulsar «Canción» desde ese vídeo vuelve el error del punto 1.
 *
 * ## Qué hace este archivo
 * Recuerda, por sesión, los ids cuyo audio de canción ya fue rechazado por el CONTENIDO
 * ([SongModeAvailability.markBlocked], solo desde el resolutor y solo con veredicto de contenido), y
 * concentra las decisiones puras que dependen de eso ([SongModePlanning]) para poder fijarlas en un test:
 *  - la opción «Canción» no se ofrece mientras ese vídeo está en modo vídeo;
 *  - un vídeo cuya canción falla arranca en modo vídeo en vez de saltarse (una sola vez por id);
 *  - para esos ids la escalera de formatos solo usa el peldaño PROGRESIVO (lleva el audio dentro, no pide
 *    el audio roto);
 *  - la cadena de saltos se corta con UN aviso claro.
 *
 * En memoria a propósito (como [VideoCompatibility]): un falso positivo solo cuesta ocultar «Canción» en
 * un vídeo hasta reiniciar, y un acierto del audio del mismo id lo desmarca ([clear]). NO oculta nada de
 * listas ni de colas (eso es [iad1tya.echo.music.utils.UnavailableSongs], con otras garantías).
 */
class SongModeBlockedRegistry(private val maxEntries: Int = SongModePlanning.MAX_TRACKED) {
    private val ids = LinkedHashSet<String>()

    /** True si el set cambió. */
    @Synchronized
    fun add(id: String): Boolean {
        if (id.isBlank()) return false
        if (!ids.add(id)) return false
        while (ids.size > maxEntries) ids.remove(ids.first())
        return true
    }

    /** True si el set cambió. */
    @Synchronized
    fun remove(id: String): Boolean = ids.remove(id)

    @Synchronized
    fun contains(id: String): Boolean = id in ids

    @Synchronized
    fun snapshot(): Set<String> = LinkedHashSet(ids)
}

object SongModeAvailability {
    private val registry = SongModeBlockedRegistry()
    private val _blocked = MutableStateFlow<Set<String>>(emptySet())

    /** Para que la interfaz se recomponga cuando se aprende un id (el aprendizaje llega con el reproductor ya abierto). */
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    fun isBlocked(id: String?): Boolean = id != null && registry.contains(id)

    /** El audio de canción de [id] fue rechazado por el contenido. Sin ids en el log (AGENTS.md regla 4). */
    fun markBlocked(id: String?) {
        if (id == null) return
        if (registry.add(id)) _blocked.value = registry.snapshot()
    }

    /** El audio de [id] se resolvió: la canción sí funciona. */
    fun clear(id: String?) {
        if (id == null) return
        if (registry.remove(id)) _blocked.value = registry.snapshot()
    }
}

/** Decisiones puras de «canción vs vídeo» para ids con audio de canción rechazado. Fijadas en SongModePlanningTest. */
object SongModePlanning {

    /** Ids recordados por sesión como máximo. */
    const val MAX_TRACKED = 512

    /**
     * ¿Se ofrece «Canción» (el botón que saca del modo vídeo)? No, cuando el vídeo está en modo vídeo y su
     * audio de canción ya se sabe rechazado: pulsarlo solo traería el error. Fuera del modo vídeo el botón
     * que existe es «Vídeo», que nunca se oculta aquí.
     */
    fun offerSongOption(videoModeOn: Boolean, isVideoSong: Boolean, songBlocked: Boolean): Boolean =
        !(videoModeOn && isVideoSong && songBlocked)

    /**
     * Un vídeo cuya canción acaba de fallar por el CONTENIDO arranca en modo vídeo en vez de saltarse.
     * Una sola vez por id ([alreadyTried]): si el vídeo tampoco se deja, el fallo vuelve a su camino de
     * siempre (aviso + salto) y no hay bucle canción→vídeo→canción.
     */
    fun shouldFallBackToVideo(
        isVideoSong: Boolean,
        contentLevel: Boolean,
        videoModeOn: Boolean,
        alreadyTried: Boolean,
    ): Boolean = isVideoSong && contentLevel && !videoModeOn && !alreadyTried

    /**
     * Con el audio de canción rechazado, solo sirve el peldaño PROGRESIVO (imagen y sonido en el mismo
     * fichero): los demás son vídeo-only y la fuente unida pediría el audio roto → el mismo error otra vez.
     */
    fun ladderSourceAllowed(source: VideoFormatFallback.Source, songBlocked: Boolean): Boolean =
        !songBlocked || source == VideoFormatFallback.Source.PIPEPIPE_MUXED

    /**
     * Una URL de vídeo ya en caché solo vale para un id con la canción rechazada si es progresiva: una
     * vídeo-only (la de la precarga especulativa) arrastraría el audio roto.
     */
    fun cachedVideoUsable(songBlocked: Boolean, cachedIsMuxed: Boolean): Boolean = !songBlocked || cachedIsMuxed

    /**
     * ¿Se acabó la paciencia con la racha de canciones sin resolver? [consecutiveFailures] cuenta los saltos
     * seguidos sin que nada suene; pasado [max] se para con un solo aviso en vez de seguir.
     */
    fun skipChainExhausted(consecutiveFailures: Int, max: Int): Boolean = consecutiveFailures > max
}
