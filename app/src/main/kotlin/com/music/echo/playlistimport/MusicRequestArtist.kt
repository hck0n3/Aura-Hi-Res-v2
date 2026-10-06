package iad1tya.echo.music.playlistimport

import java.text.Normalizer

/**
 * 🔴 "HIP HOP DE EMINEM" ES UNA PETICIÓN DE EMINEM (dueño, 2026-10-05: *"pedí hip hop de Eminem y
 * solo la primera canción fue hip hop de Eminem y lo demás solo era de otro cantante"*).
 *
 * Lo que sobra de la petición tras quitar el género ([MusicRequestQuery.residualBeyondCategory]) era
 * siempre tratado como el NOMBRE DE UNA CANCIÓN: se buscaba un título que contuviera esa palabra, se
 * fijaba en el primer puesto y detrás iba la radio de esa canción — o sea, otros artistas. Su log lo
 * confirma (`MUSIC_REQUEST specific=true pool=44`: una canción fijada y su radio).
 *
 * Aquí se decide si ese resto es en realidad un ARTISTA, y se decide con una prueba, no con una
 * corazonada: el resto tiene que ser EXACTAMENTE (sin acentos, mayúsculas ni signos) el artista
 * principal de al menos [MIN_CONFIRMATIONS] de las canciones que el propio buscador devolvió para su
 * petición. "eminem" contra una lista llena de canciones de Eminem pasa; "alternativo" (de "rock
 * alternativo") no pasa aunque exista algún grupo con ese nombre, porque el buscador no lo devuelve
 * dos veces como artista principal de una búsqueda de rock.
 *
 * Puro y probado (MusicRequestArtistTest).
 */
object MusicRequestArtist {

    /** Cuántas canciones del resultado tienen que ser de ese artista para creer que lo pidió. */
    const val MIN_CONFIRMATIONS = 2

    /**
     * El nombre del artista tal y como lo escribe el catálogo si [residual] lo nombra, o null.
     *
     * @param primaryArtists el artista principal de cada canción que devolvió la búsqueda, en su
     *   orden; null/vacío = desconocido (no cuenta ni a favor ni en contra).
     */
    fun confirm(residual: String, primaryArtists: List<String?>): String? {
        val wanted = normalizeName(residual)
        if (wanted.length < 2) return null
        var hits = 0
        var display: String? = null
        for (name in primaryArtists) {
            if (name.isNullOrBlank()) continue
            if (normalizeName(name) == wanted) {
                hits++
                if (display == null) display = name.trim()
            }
        }
        return display?.takeIf { hits >= MIN_CONFIRMATIONS }
    }

    /** true cuando [a] y [b] nombran al mismo artista una vez normalizados ("Bad Bunny" = "bad  bunny"). */
    fun sameName(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val na = normalizeName(a)
        return na.isNotEmpty() && na == normalizeName(b)
    }

    /**
     * Sin acentos, minúsculas, sin signos (A$AP → aap, "Guns N' Roses" → "guns n roses") y sin un "the"
     * inicial ("The Weeknd" = "weeknd"), espacios colapsados.
     */
    internal fun normalizeName(value: String): String {
        val folded = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return folded.removePrefix("the ").trim()
    }
}
