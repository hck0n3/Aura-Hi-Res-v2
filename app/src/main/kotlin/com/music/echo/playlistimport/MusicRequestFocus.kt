package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.models.MediaMetadata

/**
 * El «Conocido / Descubrir» de «Pedir música» (YouTube Music lo ofrece en sus colas de radio como Familiar /
 * Discover; dueño, 2026-10-18: «integra las tres opciones»; y el mismo día: «el algoritmo de Conocido no es lógico
 * y no es preciso»).
 *
 *  · [All]: lo de siempre, sin tocar nada.
 *  · [Familiar]: SOLO canciones de artistas que el usuario ya escucha con gusto, las más afines primero. Antes
 *    «conocido» era «su puntuación de gusto es > 0», y esa puntuación suma bonos de género y de estilo: un artista
 *    que nunca había oído pasaba por «conocido» solo por ser del mismo género. Ahora el criterio es el artista.
 *  · [Discover]: canciones que nunca ha reproducido Y de artistas que su perfil no conoce.
 *
 * Nada se mezcla en silencio: si hay pocas, se devuelven solo esas y se marca [FocusResult.relaxed] para que la
 * pantalla lo diga; solo cuando NO hay ninguna se devuelve lo pedido tal cual (también marcado), porque una petición
 * vacía es peor que una petición parcial. No cambia qué se pidió, solo qué parte se prefiere. Función pura, sin
 * tipos de Android: probable en la JVM.
 */
enum class MusicRequestFocus { All, Familiar, Discover }

/** [relaxed] = el filtro no pudo cumplirse del todo (pocas o ninguna cumplen); la pantalla debe decirlo. */
data class FocusResult(val songs: List<MediaMetadata>, val relaxed: Boolean)

object MusicRequestFocusFilter {

    /** Por debajo de esto el resultado se marca como corto ([FocusResult.relaxed]). */
    const val MIN_STRICT = 4

    /**
     * Cuántos candidatos hay que pedir para poder filtrar y aún llenar el primer lote. «Conocido» pide más porque
     * los artistas que ya escucha son un subconjunto pequeño de lo que la petición trae. Sin filtro, el lote tal cual.
     */
    fun candidatesFor(focus: MusicRequestFocus, batch: Int): Int = when (focus) {
        MusicRequestFocus.All -> batch
        MusicRequestFocus.Familiar -> batch * 3
        MusicRequestFocus.Discover -> batch * 2
    }

    /**
     * @param isFamiliar el artista de la canción es uno que el usuario escucha con gusto (NO «le suena el género»).
     * @param familiarity cuánto: ordena lo conocido, lo más afín primero (orden estable ante empates).
     */
    fun select(
        focus: MusicRequestFocus,
        songs: List<MediaMetadata>,
        isFamiliar: (MediaMetadata) -> Boolean,
        playedIds: Set<String>,
        familiarity: (MediaMetadata) -> Double = { 0.0 },
    ): FocusResult = when (focus) {
        MusicRequestFocus.All -> FocusResult(songs, relaxed = false)
        MusicRequestFocus.Familiar -> {
            val known = songs.filter(isFamiliar).sortedByDescending(familiarity)
            if (known.isEmpty()) FocusResult(songs, relaxed = true)
            else FocusResult(known, relaxed = known.size < MIN_STRICT)
        }
        MusicRequestFocus.Discover -> {
            val fresh = songs.filter { it.id !in playedIds && !isFamiliar(it) }
            if (fresh.isEmpty()) FocusResult(songs, relaxed = true)
            else FocusResult(fresh, relaxed = fresh.size < MIN_STRICT)
        }
    }
}
