package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.models.MediaMetadata

/**
 * El «Conocido / Descubrir» de «Pedir música» (YouTube Music lo ofrece en sus colas de radio como Familiar /
 * Discover; dueño, 2026-10-18: «integra las tres opciones»).
 *
 *  · [All]: lo de siempre, sin tocar nada.
 *  · [Familiar]: lo que ya conoce de artistas que escucha; si hay pocas, lo conocido va PRIMERO y el resto detrás.
 *  · [Discover]: canciones que nunca ha reproducido y de artistas que su perfil no conoce; si hay pocas, esas van
 *    primero y el resto detrás.
 *
 * Es un filtro/orden sobre lo que la petición YA produjo: nunca deja una petición vacía (la regla de «nunca
 * silencio» del resto de la app) y no cambia qué se pidió, solo qué parte de eso se prefiere. Función pura, sin
 * tipos de Android: probable en la JVM.
 */
enum class MusicRequestFocus { All, Familiar, Discover }

object MusicRequestFocusFilter {

    /** Con menos canciones que esto el filtro estricto no se aplica «a secas»: lo preferido va delante. */
    const val MIN_STRICT = 4

    /**
     * Cuántos candidatos hay que pedir para poder filtrar y aún llenar el primer lote. Sin filtro, el lote tal cual.
     */
    fun candidatesFor(focus: MusicRequestFocus, batch: Int): Int =
        if (focus == MusicRequestFocus.All) batch else batch * 2

    fun apply(
        focus: MusicRequestFocus,
        songs: List<MediaMetadata>,
        isFamiliar: (MediaMetadata) -> Boolean,
        playedIds: Set<String>,
    ): List<MediaMetadata> = when (focus) {
        MusicRequestFocus.All -> songs
        MusicRequestFocus.Familiar -> {
            val (familiar, rest) = songs.partition(isFamiliar)
            if (familiar.size >= MIN_STRICT) familiar else familiar + rest
        }
        MusicRequestFocus.Discover -> {
            val fresh = songs.filter { it.id !in playedIds && !isFamiliar(it) }
            if (fresh.size >= MIN_STRICT) {
                fresh
            } else {
                val freshIds = fresh.mapTo(HashSet()) { it.id }
                fresh + songs.filter { it.id !in freshIds }
            }
        }
    }
}
