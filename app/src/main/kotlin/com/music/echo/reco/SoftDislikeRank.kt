package iad1tya.echo.music.reco

import iad1tya.echo.music.dislike.DislikeStore

/**
 * Row 721 — «Menos de esto» («Se recomendará menos parecido a esto») as the menu promises it.
 *
 * The menu used to store only the song id, and only the radio's ordering read it (`MusicService.orderedByTaste`), so the
 * same artist kept coming back everywhere else. The menu now also stores the song's primary artist ([artistKey]: its id, or
 * its lower-cased name when YouTube gave none — the radio matches both), and the Home shelves read both soft sets
 * ([TasteProfile.softPenalty]) as a bounded demotion. Never a filter (rows 39/116): a marked song can still appear.
 */
object SoftDislikeRank {
    /**
     * What a «Menos de esto» takes from a Home candidate's taste score: half the strongest normalised artist affinity
     * ([TasteProfile] scores an artist in -1..1), so a favourite marked once still ranks above an unknown song, while
     * an ordinary one falls below it.
     */
    const val HOME_PENALTY = 0.5

    /** The key the soft-artist set keeps for an artist: its id, or its lower-cased name when it has none. */
    fun artistKey(id: String?, name: String?): String? =
        id?.takeIf { it.isNotBlank() } ?: name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /** Pure: the song, or one of its artists (by id or by name), was marked «Menos de esto». */
    fun matches(disliked: DislikeStore.Disliked, songId: String?, artistIds: List<String?>, artistNames: List<String>): Boolean {
        if (songId != null && songId in disliked.softSongs) return true
        if (disliked.softArtists.isEmpty()) return false
        return artistIds.any { it != null && it in disliked.softArtists } ||
            artistNames.any { it.trim().lowercase() in disliked.softArtists }
    }
}
