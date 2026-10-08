package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.models.MediaMetadata
import java.text.Normalizer

/**
 * Fila 359 — "Pedir música" no pone instrumentales ni pistas de karaoke, salvo que se pidan.
 *
 * 🔴 Dueño (2026-10-08): *"que en pedir música no salgan instrumentales, a menos que el usuario pida una
 * canción en karaoke"*.
 *
 * YouTube mezcla en sus resultados versiones sin voz de la misma canción: "(Instrumental)", "Karaoke
 * Version", "Pista con letra", "In the Style of…", canales como "Sing King". Para quien pide música son
 * canciones rotas. Se reconocen por el TÍTULO, el ÁLBUM y el ARTISTA (los canales de karaoke llevan la
 * palabra en el nombre), siempre por palabra o frase completa — "Pistas de baile" o "Beat It" no caen.
 *
 * Si la petición SÍ pide karaoke o instrumental ("karaoke de…", "pista de…", "instrumental", "sin voz",
 * "para cantar"), no se filtra nada: eso es justo lo que quiere. Puro, sin red.
 */
object MusicRequestInstrumental {

    /** Words/phrases that mark a vocal-less version, folded (lowercase, no accents, single spaces). */
    private val MARKERS = listOf(
        "instrumental", "instrumentales", "karaoke", "karaokes", "backing track", "backing tracks",
        "minus one", "sin voz", "sin letra", "pista musical", "pistas musicales", "pista con letra",
        "pista sin voz", "pista para cantar", "pistas para cantar", "playback",
        "multitrack", "multitracks", "type beat", "beat instrumental", "acompanamiento",
        "in the style of", "originally performed by", "made famous by", "originalmente interpretada por",
        "sing king", "karafun", "piano version", "version piano", "piano cover", "orchestral version",
        "8 bit version", "8bit version", "lullaby version", "version de cuna",
    )

    /** "(Pista)" / "[Pista]" / "- Pista": a lone "pista" only counts in those positions ("La Pista" is a song). */
    private val LONE_PISTA = Regex("""[(\[]\s*pistas?\s*[)\]]|\s-\s*pistas?\b""")

    /** What a request says when it DOES want them. */
    private val WANTS = listOf(
        "karaoke", "karaokes", "instrumental", "instrumentales", "sin voz", "sin letra", "para cantar",
        "backing track", "backing tracks", "playback", "multitrack", "multitracks", "pista musical",
        "pistas musicales", "pista con letra", "pista para", "pistas para", "pista de la cancion",
        "piano cover", "type beat",
    )

    fun wantsInstrumental(prompt: String): Boolean {
        val p = " ${fold(prompt)} "
        return WANTS.any { p.contains(" $it ") }
    }

    fun isInstrumental(title: String?, album: String? = null, artists: List<String> = emptyList()): Boolean {
        val raw = listOfNotNull(title, album).joinToString(" | ").lowercase()
        if (LONE_PISTA.containsMatchIn(Normalizer.normalize(raw, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), ""))) {
            return true
        }
        val text = " ${fold(listOfNotNull(title, album).joinToString(" "))} "
        val by = " ${fold(artists.joinToString(" "))} "
        return MARKERS.any { m -> text.contains(" $m ") || by.contains(" $m ") }
    }

    fun isInstrumental(mm: MediaMetadata): Boolean =
        isInstrumental(mm.title, mm.album?.title, mm.artists.map { it.name })

    /** [songs] without vocal-less versions, unless [prompt] asks for them. Order kept. */
    fun filter(prompt: String, songs: List<MediaMetadata>): List<MediaMetadata> =
        if (wantsInstrumental(prompt)) songs else songs.filterNot { isInstrumental(it) }

    private fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
}
