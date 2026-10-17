package iad1tya.echo.music.playback

import java.text.Normalizer

/**
 * "Is this the same SONG?" for the infinite / smart queue — the identity the no-repeat rule compares.
 *
 * A song reaches the queue under many mediaIds: the album track, the "Official Video", the lyric video,
 * the remaster, the single, a "feat." re-credit. Matching on mediaId alone let all of them through as
 * "new", and the older title key (`AiPlaylistGenerator.dedupKey`, kept as is for "Pedir música") only
 * stripped bracketed text, so "Song - Remastered 2011", "Song (feat. X)" with a different bracket style,
 * "Café" vs "Cafe" or "Artist - Topic" vs "Artist" still counted as different songs.
 *
 * What is deliberately KEPT as a difference: `live`, `acoustic`, `remix`, `instrumental`, `karaoke`,
 * `cover`, `sped up`, `slowed` — those are other recordings a listener may well want, and merging them
 * would hide real songs. What is dropped: pure packaging (remaster, version/edit labels, video/audio/lyrics
 * labels, quality tags, mono/stereo, deluxe/bonus, explicit/clean) and featured-artist credits.
 *
 * Pure function, no Android types: unit-testable on the JVM.
 */
object NoRepeatKey {

    private val bracketed = Regex("""[(\[{][^)\]}]*[)\]}]""")
    private val combiningMarks = Regex("""\p{M}+""")
    private val nonWord = Regex("""[^\p{L}\p{N}\s]""")
    private val spaces = Regex("""\s+""")

    // "feat. X", "ft X", "featuring X", "con X" is NOT touched (it is also an ordinary word in Spanish titles).
    private val featuredTail = Regex("""\b(feat|ft|featuring)\b.*$""")

    // " - Remastered 2011", " - Single Version", " - Radio Edit", " - Official Video" ... : a dash-separated
    // tail made ONLY of packaging words (plus years) is dropped; a tail with any other word is kept.
    private val packagingWords = setOf(
        "remaster", "remastered", "remasterizado", "remasterizada", "version", "versión", "versao", "single",
        "album", "álbum", "radio", "edit", "original", "mix", "mono", "stereo", "estéreo", "deluxe", "bonus",
        "track", "official", "oficial", "video", "vídeo", "audio", "lyric", "lyrics", "letra", "visualizer",
        "visualiser", "hd", "hq", "4k", "explicit", "clean", "music", "the", "de", "la", "extended", "full",
        "song", "canción", "cancion", "with", "new", "edition", "anniversary", "aniversario",
    )
    private val yearOrDigits = Regex("""^\d{2,4}$""")

    private val dashTail = Regex("""\s[-–—|]\s(.+)$""")

    private val topicSuffix = Regex("""\s*-\s*topic$""")
    private val vevoSuffix = Regex("""vevo$""")

    /** Normalised song identity: `title|primaryArtist`, both folded and stripped of packaging. */
    fun of(title: String, primaryArtist: String?): String =
        "${normalizeTitle(title)}|${normalizeArtist(primaryArtist)}"

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")

    internal fun normalizeTitle(raw: String): String {
        var t = fold(raw)
        // Brackets first: "(Official Video)", "[Remastered]", "(feat. X)", "(Live)" — see below for live.
        // A bracket that says live/acoustic/remix/... is a DIFFERENT recording: keep its word.
        t = bracketed.replace(t) { m ->
            val inner = m.value.trim('(', ')', '[', ']', '{', '}', ' ')
            if (distinguishing.any { inner.contains(it) }) " $inner " else " "
        }
        // Dash tail made only of packaging words.
        val tail = dashTail.find(t)
        if (tail != null) {
            val words = nonWord.replace(tail.groupValues[1], " ").split(spaces).filter { it.isNotBlank() }
            val onlyPackaging = words.isNotEmpty() && words.all { it in packagingWords || yearOrDigits.matches(it) }
            if (onlyPackaging) t = t.substring(0, tail.range.first)
        }
        t = featuredTail.replace(t, " ")
        t = nonWord.replace(t, " ")
        return spaces.replace(t, " ").trim()
    }

    internal fun normalizeArtist(raw: String?): String {
        var a = fold(raw.orEmpty()).trim()
        a = topicSuffix.replace(a, "")
        a = nonWord.replace(a, " ")
        a = spaces.replace(a, " ").trim()
        // "ArtistVEVO" is the same act as "Artist".
        if (a.length > 5 && vevoSuffix.containsMatchIn(a)) a = vevoSuffix.replace(a, "").trim()
        return a
    }

    private val distinguishing = listOf(
        "live", "en vivo", "acoustic", "acustic", "remix", "instrumental", "karaoke", "cover",
        "sped up", "slowed", "nightcore", "reverb", "8d", "unplugged",
    )
}
