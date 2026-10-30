package iad1tya.echo.music.reco

/**
 * Row 526 (owner 2026-10-29: «necesito que mejores también las canciones sugeridas»). The «Canciones sugeridas» footer of a
 * playlist ranks YouTube's related songs by how many of the playlist's songs they are related to — it knew nothing about WHAT
 * the playlist is: a salsa list got a reggaeton «related» song, a Christian list a secular one, an English list a Spanish one,
 * and the last-resort backfill is simply «what you played lately».
 *
 * This reads the playlist like the smart queue reads a context (dominant style, language, faith) and says, for each candidate,
 * whether it FITS. The ranking is not replaced: candidates that fit keep their order and go first, the ones that plainly do not
 * go to the very end (never dropped — a section must not go empty — and a playlist too mixed to read is not judged at all).
 * Only POSITIVE evidence of a mismatch moves a song: an unknown style, language or artist is never held against it.
 */
object SuggestionFit {
    /** A song as the fit sees it: its own words and the artists credited. */
    data class Entry(val title: String, val artists: List<String>, val album: String? = null) {
        val primary: String? get() = artists.firstOrNull()
        val text: String get() = listOfNotNull(title, album).joinToString(" ")
    }

    class Profile(val target: StyleContinuity.Target, val ownArtists: Set<String>, val genres: Map<String, String>) {
        val active: Boolean get() = target.active || target.christian
    }

    const val MIN_SONGS = 4
    private const val STYLE_SHARE = 0.25
    private const val LANGUAGE_SHARE = 0.7
    private const val CHRISTIAN_SHARE = 0.6

    /** The playlist's dominant style(s), language and faith; inactive (judges nothing) when it is too small or too mixed. Pure. */
    fun profileOf(playlist: List<Entry>, genres: Map<String, String>): Profile {
        val own = playlist.flatMap { it.artists }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        if (playlist.size < MIN_SONGS) {
            return Profile(StyleContinuity.Target(null, null, christian = false), own, genres)
        }
        val styleCounts = HashMap<String, Int>()
        val languageCounts = HashMap<String, Int>()
        var christian = 0
        for (e in playlist) {
            val style = MusicStyle.fromText(e.text) ?: MusicStyle.fromGenre(GenreLane.lookupGenre(genres, e.primary)).style
            if (style != null) styleCounts.merge(style, 1, Int::plus)
            TitleLanguage.detect(e.text)?.let { languageCounts.merge(it, 1, Int::plus) }
            if (GenreLane.laneOfTrack(genres, e.primary, e.title, e.album) == GenreLane.CHRISTIAN) christian++
        }
        val knownStyles = styleCounts.values.sum()
        val styles = if (knownStyles >= MIN_SONGS) {
            styleCounts.filter { (_, n) -> n >= 2 && n.toDouble() / knownStyles >= STYLE_SHARE }.keys
        } else {
            emptySet()
        }
        val knownLanguages = languageCounts.values.sum()
        val language = languageCounts.maxByOrNull { it.value }
            ?.takeIf { knownLanguages >= MIN_SONGS && it.value.toDouble() / knownLanguages >= LANGUAGE_SHARE }?.key
        val isChristian = christian.toDouble() / playlist.size >= CHRISTIAN_SHARE
        return Profile(StyleContinuity.Target(styles.ifEmpty { null }, language, isChristian), own, genres)
    }

    /** True unless [candidate] plainly does not belong to what [profile] reads. Pure. */
    fun fits(profile: Profile, candidate: Entry): Boolean {
        if (!profile.active) return true
        val artist = candidate.primary?.trim()?.lowercase()
        val own = candidate.artists.any { it.trim().lowercase() in profile.ownArtists }
        if (own) return true
        val genre = MusicStyle.fromGenre(GenreLane.lookupGenre(profile.genres, candidate.primary))
        val verdict = StyleContinuity.verdict(
            profile.target,
            StyleContinuity.Candidate(
                textStyle = MusicStyle.fromText(candidate.text),
                learnedStyle = null,
                genre = genre,
                language = TitleLanguage.detect(candidate.text),
                ownArtist = false,
                fromSearch = false,
            ),
        )
        if (verdict == StyleContinuity.Verdict.OFF) return false
        // Faith: only a candidate KNOWN not to be Christian is held back from a Christian list.
        if (profile.target.christian && artist != null) {
            val lane = GenreLane.laneOfTrack(profile.genres, candidate.primary, candidate.title, candidate.album)
            if (lane != null && lane != GenreLane.CHRISTIAN) return false
        }
        return true
    }

    /** [ids] with the ones that fit first (their order kept) and the ones that do not at the very end. Pure. */
    fun order(profile: Profile, ids: List<String>, entryOf: (String) -> Entry?): List<String> {
        if (!profile.active) return ids
        val (fit, misfit) = ids.partition { id -> entryOf(id)?.let { fits(profile, it) } ?: true }
        return fit + misfit
    }
}
