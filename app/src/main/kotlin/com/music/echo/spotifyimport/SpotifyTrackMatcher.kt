package iad1tya.echo.music.spotifyimport

import com.aura.migration.model.SourceTrack
import com.aura.migration.model.YtmCandidate
import com.aura.migration.resolver.Scorer
import com.aura.migration.resolver.Similarity
import com.aura.migration.resolver.TextNormalizer
import kotlin.math.abs

/**
 * Registry row 832 (owner 2026-11-24: «quiero que la sincronización sea exacta»). The Spotify import used to accept any candidate whose
 * SpotifyMapper.matchScore reached 0.5, so another song by the same artist with a ±2 s duration (0.55) or the same title by any artist
 * (0.65) was imported, liked and pushed as if it were the user's song.
 *
 * Spotify tracks now go through the migration module's strict [Scorer] (ACCEPT 75 + MARGIN 10 over the runner-up), with three gates
 * that Scorer alone does not enforce: an empty normalized title or artist is never a match, the artist must match near-exactly, and
 * every word of the shorter title must be in the other one (Scorer only weighs the title at 20 points, so same artist + same duration
 * would pass without it; a similarity bar is not enough either: «love me» / «love you» scores 0.87). Pure, for JVM tests.
 */
object SpotifyTrackMatcher {
    /** The «casan casi perfecto» bar Scorer itself uses for the artist before trusting a match without duration. */
    const val NEAR_EXACT = 0.85f

    /** Scorer's top duration band: two uploads this close, with the same title and artist, are the same recording. */
    private const val SAME_RECORDING_MS = 2_000L

    private class Scored<T>(
        val item: T,
        val score: Float,
        val title: String,
        val artist: String,
        val durationMs: Long?,
    )

    /** The candidate [source] really is, or null when none is (or two different songs are too close to tell apart). */
    fun <T> pick(source: SourceTrack, candidates: List<Pair<T, YtmCandidate>>): T? {
        val title = TextNormalizer.normalize(source.title)
        if (title.isEmpty()) return null
        val artists = source.artists.filter { artistKey(it).isNotEmpty() }
        if (artists.isEmpty()) return null

        val ranked = candidates.mapNotNull { (item, candidate) -> evaluate(source, title, artists, item, candidate) }
            .sortedByDescending { it.score }
        val best = ranked.firstOrNull() ?: return null
        if (best.score < Scorer.ACCEPT) return null
        // The same recording uploaded twice (album + single) is not ambiguity: either one is the user's song.
        val runnerUp = ranked.drop(1).firstOrNull { !sameRecording(best, it) }
        if (runnerUp != null && best.score - runnerUp.score < Scorer.MARGIN) return null
        return best.item
    }

    private fun <T> evaluate(
        source: SourceTrack,
        title: String,
        artists: List<String>,
        item: T,
        candidate: YtmCandidate,
    ): Scored<T>? {
        val candidateTitle = TextNormalizer.normalize(candidate.title)
        if (candidateTitle.isEmpty()) return null
        // Each credited artist against each one (Spotify and YouTube Music do not credit the same featured artists, in the same
        // order): the best-matching pair is the one scored.
        var bestPair: Triple<String, String, Float>? = null
        for (s in artists) {
            val ns = artistKey(s)
            for (c in candidate.artists) {
                val nc = artistKey(c)
                if (nc.isEmpty()) continue
                val sim = Similarity.best(ns, nc)
                if (bestPair == null || sim > bestPair.third) bestPair = Triple(s, c, sim)
            }
        }
        val (sourceArtist, candidateArtist, artistSim) = bestPair ?: return null
        if (artistSim < NEAR_EXACT) return null
        if (!sameWords(title, candidateTitle)) return null
        val score = Scorer.score(
            source.copy(artists = listOf(sourceArtist)),
            candidate.copy(artists = listOf(candidateArtist)),
        )
        return Scored(item, score, candidateTitle, artistKey(candidateArtist), candidate.durationMs)
    }

    /**
     * normalizeArtist cuts from a standalone «x», «y», «and», «&» or «,» to the end, a LEADING one included: «X Ambassadors» and
     * «X Japan» come out empty, so none of their songs would ever match. Such a name is compared whole instead (row 832).
     */
    private fun artistKey(name: String): String =
        TextNormalizer.normalizeArtist(name).ifEmpty { TextNormalizer.normalize(name) }

    /** Every word of the shorter title is in the longer one («se le ve» / «se le ve with x»), never a word swapped for another. */
    private fun sameWords(a: String, b: String): Boolean {
        val wa = a.split(' ').toSet()
        val wb = b.split(' ').toSet()
        return wa.containsAll(wb) || wb.containsAll(wa)
    }

    private fun sameRecording(a: Scored<*>, b: Scored<*>): Boolean {
        val da = a.durationMs ?: return false
        val db = b.durationMs ?: return false
        return a.title == b.title && a.artist == b.artist && abs(da - db) <= SAME_RECORDING_MS
    }
}
