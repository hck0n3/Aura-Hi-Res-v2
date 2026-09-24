/*
 * EchoMusic (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.spotify

import iad1tya.echo.music.spotify.models.SpotifyPlaylist
import iad1tya.echo.music.spotify.models.SpotifyTrack

/**
 * Utility object for creating search queries from Spotify track data.
 * The actual mapping to Metrolist MediaMetadata is done in the app module
 * where MediaMetadata class is available.
 */
object SpotifyMapper {

    // Pre-compiled regex patterns for title normalization (avoids re-creation on each call)
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)")
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)")
    private val BRACKET_PATTERN = Regex("\\[.*?]")
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    // Release-variant noise that differs between Spotify and YTM titles for the SAME song and used to
    // sink the Dice score below MIN_MATCH_SCORE (owner report: a real link read "no encontrado" while
    // a manual search found the exact song). "- Live"/"- Single Version" are dash suffixes, so they
    // must run before NON_ALNUM_PATTERN strips the dash.
    private val LIVE_PATTERN = Regex("\\(.*?\\blive\\b.*?\\)", RegexOption.IGNORE_CASE)
    private val RADIO_EDIT_PATTERN = Regex("\\(.*?radio edit.*?\\)", RegexOption.IGNORE_CASE)
    private val SPED_UP_PATTERN = Regex("\\(.*?sped up.*?\\)", RegexOption.IGNORE_CASE)
    private val DASH_SUFFIX_PATTERN = Regex(
        "\\s*-\\s*(single|radio|album|mono|stereo)?\\s*(version|edit|live)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val NON_ALNUM_PATTERN = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_PATTERN = Regex("\\s+")

    private const val NORM_CACHE_MAX_SIZE = 256
    private const val EARLY_EXIT_THRESHOLD = 0.95

    /**
     * LRU cache for normalized strings. Avoids re-running 7 regex replacements
     * on the same Spotify title/artist across multiple candidate comparisons.
     * Bounded to [NORM_CACHE_MAX_SIZE] entries to limit memory usage.
     */
    // Wrapped in a synchronized map: matching now runs on up to MAX_CONCURRENT_MATCHES threads in parallel,
    // and an access-order LinkedHashMap relinks its internal list on every get, so unsynchronized concurrent
    // access could corrupt it (infinite loop / CME). synchronizedMap serializes each get/put.
    private val normalizeCache: MutableMap<String, String> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(NORM_CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
                size > NORM_CACHE_MAX_SIZE
        }
    )

    /**
     * LRU cache for pre-computed bigram sets. Avoids re-creating Set<String>
     * on every stringSimilarity call for the same normalized string.
     */
    private val bigramCache: MutableMap<String, Set<String>> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Set<String>>(NORM_CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?): Boolean =
                size > NORM_CACHE_MAX_SIZE
        }
    )

    /**
     * Builds a YouTube search query from a Spotify track.
     * The query is optimized for finding the matching song on YouTube Music.
     */
    fun buildSearchQuery(track: SpotifyTrack): String {
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val title = track.name
        return if (artist.isEmpty()) title else "$artist $title"
    }

    /**
     * Alternate query used when the primary artist-first search yields no usable match:
     * "title - artist" (title-first). Distinct from [buildSearchQuery] whenever an artist is present.
     */
    fun buildAlternateSearchQuery(track: SpotifyTrack): String {
        val artist = track.artists.firstOrNull()?.name.orEmpty()
        val title = track.name
        return if (artist.isEmpty()) title else "$title - $artist"
    }

    /**
     * Returns the best thumbnail URL from a Spotify playlist, preferring medium resolution.
     */
    fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? {
        return playlist.images.let { images ->
            // Prefer 300x300 or similar medium size, fallback to first
            images.firstOrNull { it.width in 200..400 }?.url
                ?: images.firstOrNull()?.url
        }
    }

    /**
     * Returns the best thumbnail URL from a Spotify track's album art.
     */
    fun getTrackThumbnail(track: SpotifyTrack): String? {
        return track.album?.images?.let { images ->
            images.firstOrNull { it.width in 200..400 }?.url
                ?: images.firstOrNull()?.url
        }
    }

    /**
     * Computes a match confidence score (0.0 - 1.0) between a Spotify track and
     * a candidate result based on title, artist, and duration similarity.
     */
    fun matchScore(
        spotifyTitle: String,
        spotifyArtists: List<String>,
        spotifyDurationMs: Int,
        candidateTitle: String,
        candidateArtists: List<String>,
        candidateDurationSec: Int?,
    ): Double {
        val normSpotifyTitle = cachedNormalize(spotifyTitle)
        val normCandidateTitle = cachedNormalize(candidateTitle)

        val titleScore = bigramSimilarity(
            normSpotifyTitle, cachedBigrams(normSpotifyTitle),
            normCandidateTitle, cachedBigrams(normCandidateTitle),
        )
        val artistScore = bestArtistSimilarity(spotifyArtists, candidateArtists)

        val durationScore = durationScore(spotifyDurationMs, candidateDurationSec)
        return titleScore * 0.45 + artistScore * 0.35 + durationScore * 0.20
    }

    /**
     * Ronda 9 (dueño, tercer reporte de "no encontrado" en links externos): comparar TODOS los
     * artistas acreditados como una sola cadena unida castigaba cualquier canción con artista
     * invitado ("feat.") — muy común en reggaetón/bachata — porque Spotify y YouTube Music no
     * siempre acreditan exactamente los mismos nombres, en el mismo orden, o el mismo número de
     * ellos, y el Dice de la cadena larga se diluye con cada nombre de más en cualquiera de los dos
     * lados: "Bad Bunny" (candidato) contra "Bad Bunny Chencho Corleone" (Spotify) puntuaba bajo
     * pese a que el artista principal coincide del todo. Ahora se compara CADA PAR de nombres (uno
     * de cada lado) y se toma la MEJOR coincidencia — basta con que un artista coincida bien para
     * que un featuring que el otro lado no acreditó igual no hunda esta parte del puntaje.
     */
    private fun bestArtistSimilarity(spotifyArtists: List<String>, candidateArtists: List<String>): Double {
        val spotifyNames = spotifyArtists.filter { it.isNotBlank() }
        val candidateNames = candidateArtists.filter { it.isNotBlank() }
        if (spotifyNames.isEmpty() || candidateNames.isEmpty()) return 0.0
        var best = 0.0
        for (s in spotifyNames) {
            val normS = cachedNormalize(s)
            val bigramsS = cachedBigrams(normS)
            for (c in candidateNames) {
                val normC = cachedNormalize(c)
                val score = bigramSimilarity(normS, bigramsS, normC, cachedBigrams(normC))
                if (score > best) best = score
            }
        }
        return best
    }

    /** Threshold above which we consider a match good enough to skip remaining candidates. */
    fun earlyExitThreshold(): Double = EARLY_EXIT_THRESHOLD

    private fun durationScore(spotifyDurationMs: Int, candidateDurationSec: Int?): Double {
        if (candidateDurationSec == null || spotifyDurationMs <= 0) return 0.5
        val diff = kotlin.math.abs(spotifyDurationMs / 1000 - candidateDurationSec)
        return when {
            diff <= 2 -> 1.0
            diff <= 5 -> 0.8
            diff <= 10 -> 0.5
            diff <= 30 -> 0.2
            else -> 0.0
        }
    }

    /**
     * Normalizes a title for comparison, with LRU caching.
     */
    private fun cachedNormalize(title: String): String {
        normalizeCache[title]?.let { return it }
        val normalized = normalizeTitle(title)
        normalizeCache[title] = normalized
        return normalized
    }

    /**
     * Returns cached bigrams for a normalized string.
     */
    private fun cachedBigrams(normalized: String): Set<String> {
        bigramCache[normalized]?.let { return it }
        val bigrams = if (normalized.length < 2) emptySet() else normalized.windowed(2).toSet()
        bigramCache[normalized] = bigrams
        return bigrams
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(LIVE_PATTERN, "")
            .replace(RADIO_EDIT_PATTERN, "")
            .replace(SPED_UP_PATTERN, "")
            .replace(DASH_SUFFIX_PATTERN, "")
            .replace(NON_ALNUM_PATTERN, "")
            .replace(MULTI_SPACE_PATTERN, " ")
            .trim()
    }

    /**
     * Dice coefficient using pre-computed bigram sets.
     */
    private fun bigramSimilarity(
        a: String, bigramsA: Set<String>,
        b: String, bigramsB: Set<String>,
    ): Double {
        if (a == b) return 1.0
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0.0
        val intersection = bigramsA.count { it in bigramsB }
        return (2.0 * intersection) / (bigramsA.size + bigramsB.size)
    }
}
