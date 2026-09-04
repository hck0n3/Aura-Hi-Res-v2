package iad1tya.echo.music.api

import iad1tya.echo.music.playlistimport.SongResolver
import java.text.Normalizer

/**
 * Lightweight intent helpers for Lista IA. Pure / Android-free so unit tests can lock
 * "solo este artista" without mocking network.
 */
object AiPlaylistConstraints {

    /**
     * When the user clearly asks for ONE artist, return that name; otherwise null (genre/mood mixes).
     * Patterns cover Spanish/English phrasing owners actually type into the dialog.
     * Extended 2026-09-04 (owner: "la IA no se basa en lo que pido"): "puro X", "únicamente X",
     * "solo de X" and friends previously MISSED the solo lock → neither the prompt's solo-artist
     * emphasis nor the hard post-filter applied → adjacent artists crept in. Every pattern here
     * feeds BOTH the prompt lock and [AiPlaylistGenerator.soloPrimaryMatch] (row 198).
     */
    fun extractSoloArtist(prompt: String): String? {
        val raw = prompt.trim()
        if (raw.isBlank()) return null
        val patterns = listOf(
            // Order matters: specific patterns MUST precede the generic ones they specialize. E.g.
            // "solo de Bad Bunny" against the generic `solo (.+?)` captures "de Bad Bunny" as the
            // artist name → the hard filter then matches nothing → empty playlist.
            Regex("""(?iu)^\s*solo\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*solo\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*only\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*puro\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*pura\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*[uú]nicamente\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*canciones\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*temas\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*m[uú]sica\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*playlist\s+de\s+(.+?)\s*$"""),
            Regex("""(?iu)^\s*(.+?)\s+solamente\s*$"""),
            Regex("""(?iu)^\s*(.+?)\s+nada\s+m[aá]s\s*$"""),
            Regex("""(?iu)^\s*(.+?)\s+[uú]nicamente\s*$"""),
            Regex("""(?iu)\bsolo\s+([^,.]+?)(?:\s+canciones|\s+temas)?\s*$"""),
            Regex("""(?iu)\bcanciones\s+(?:solo\s+)?de\s+([^,.]+?)\s*$"""),
        )
        for (p in patterns) {
            val m = p.find(raw) ?: continue
            val name = cleanArtistCandidate(m.groupValues.getOrNull(1).orEmpty())
            if (name != null) return name
        }
        return null
    }

    fun artistAllowed(trackArtist: String, soloArtist: String?): Boolean {
        if (soloArtist.isNullOrBlank()) return true
        return SongResolver.artistMatches(trackArtist, soloArtist)
    }

    private fun cleanArtistCandidate(value: String): String? {
        var s = value.trim()
            .trim('"', '\'', '«', '»')
            .replace(Regex("""(?iu)\s+(canciones|temas|m[uú]sica|playlist)\s*$"""), "")
            .trim()
        if (s.length < 2 || s.length > 80) return null
        // Reject candidates that are clearly genres/moods, not people. NOTE: compare FOLDED
        // (accent-less) forms only — the old list mixed accented entries ("electrónica",
        // "romántica") that could NEVER match the folded value, i.e. they were dead entries.
        // Extended 2026-09-04 with the Latin genres the owner actually types ("pura bachata",
        // "puro perreo", "puro corridos"…): without them the new solo patterns captured the GENRE
        // as a pseudo-artist and the hard filter emptied the playlist — a total dead-end where
        // a genre mix used to be generated (adversarial audit finding #2).
        val folded = fold(s)
        val genreLike = listOf(
            "rock", "pop", "salsa", "reggaeton", "cumbia", "jazz", "blues", "metal", "punk",
            "hip hop", "rap", "trap", "electronica", "lofi", "lo-fi", "clasica", "romantica",
            "triste", "fiesta", "gym", "correr", "dormir", "estudio", "workout",
            "bachata", "merengue", "vallenato", "corridos", "corrido", "banda", "mariachi",
            "tango", "flamenco", "ranchera", "bolero", "balada", "baladas", "disco", "funk",
            "soul", "reggae", "ska", "house", "techno", "indie", "perreo", "dembow",
            "urbana", "urbano", "cristiana", "cristiano", "worship", "alabanza",
        )
        // Phrasing prefixes mean the capture grabbed "de rock de los 80"-style junk, not an
        // artist. Only rejected when what follows the prefix is itself genre/mood-like, so
        // "De La Ghetto" (a real artist) survives while "de rock de los 80" and
        // "música de los 90" do not (adversarial audit finding #2, second half).
        val phrasing = listOf("de ", "musica ", "canciones ", "temas ")
        val isGenreLike = genreLike.any { g -> folded == g || folded.startsWith("$g ") } ||
            phrasing.any { p ->
                genreLike.any { g -> folded.startsWith("$p$g") } ||
                    folded.startsWith("${p}musica ") || folded.startsWith("${p}canciones ") ||
                    folded.startsWith("${p}temas ")
            } ||
            folded == "musica" || folded.startsWith("musica ")
        if (isGenreLike) return null
        return s
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
