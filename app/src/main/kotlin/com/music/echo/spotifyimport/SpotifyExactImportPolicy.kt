package iad1tya.echo.music.spotifyimport

import iad1tya.echo.music.utils.ArtistSyncPolicy

/**
 * Registry row 830 (owner 2026-11-24, K1: «quiero que la sincronización sea exacta y no que sincronice otras cosas que no
 * tengan nada que ver con lo que estoy pidiendo»). The pure decisions of the Spotify import, so a JVM test can hold them.
 */
object SpotifyExactImportPolicy {
    /** [SpotifyImportSource.FollowedArtists.id]. */
    const val FOLLOWED_ARTISTS_SOURCE_ID = "followed_artists"

    /** [SpotifyImportSource.SavedAlbums.id]. */
    const val SAVED_ALBUMS_SOURCE_ID = "saved_albums"

    /**
     * The selection after the sources are (re)loaded: nothing is preselected. Only what the user already ticked on this screen and
     * is still offered stays ticked, so a reload (session change, «Actualizar») does not drop his picks nor add any.
     */
    fun initialSelection(loadedIds: List<String>, current: Set<String>): Set<String> =
        loadedIds.filterTo(LinkedHashSet()) { it in current }

    /** The scheduled sync keeps playlists (and the liked songs) up to date; it never follows artists nor saves albums by itself. */
    fun isSchedulable(type: SpotifyImportSourceType): Boolean =
        type == SpotifyImportSourceType.PLAYLIST || type == SpotifyImportSourceType.LIKED_SONGS

    /**
     * The scheduled source ids as stored (comma separated). A schedule saved by an older build that named «Artistas que sigues» or
     * «Álbumes guardados» drops them here, on every read; the rest is kept as it was.
     */
    fun scheduledIds(csv: String): Set<String> =
        csv.split(",")
            .map { it.trim() }
            .filterTo(LinkedHashSet()) {
                it.isNotEmpty() && it != FOLLOWED_ARTISTS_SOURCE_ID && it != SAVED_ALBUMS_SOURCE_ID
            }

    /**
     * True when the YouTube results whose normalized name is exactly the Spotify artist's ([exactMatchIds]) are more than one
     * channel: namesakes, and nothing says which one the user follows, so none is followed. One channel listed under its `UC…` and
     * `MPLAUC…` spellings is one channel ([ArtistSyncPolicy.channelKey]).
     */
    fun isAmbiguousArtist(exactMatchIds: List<String>): Boolean =
        exactMatchIds.mapTo(HashSet()) { ArtistSyncPolicy.channelKey(it) }.size > 1

    /**
     * Every id under which the matched channel can be stored: the spellings ([ArtistSyncPolicy.aliasIds]) of the result's id and of
     * its resolved [channelId]. An unfollow on ANY of them means the user removed this artist, and the import must not follow it again.
     */
    fun storedSpellings(matchId: String, channelId: String?): List<String> =
        (ArtistSyncPolicy.aliasIds(matchId) + channelId?.takeIf { it.isNotBlank() }?.let(ArtistSyncPolicy::aliasIds).orEmpty())
            .distinct()

    /** The tag the «already imported» set is kept under: the Google account's (`ImportFollowsAccounts.tagOf`), or one for no account. */
    const val SIGNED_OUT_TAG = "signed_out"

    fun accountTag(googleAccountTag: String?): String = googleAccountTag ?: SIGNED_OUT_TAG

    /**
     * True for a Spotify artist no earlier pass handled for this account: it is matched and followed. One handled before is followed
     * again only when no row for it is stored (a backup from before the import was restored); one since removed on YouTube (row kept,
     * its follow cleared by the account read-back without an unfollow marker) is therefore never followed again.
     */
    fun isNewSinceLastPass(spotifyArtistId: String, alreadyImported: Set<String>): Boolean = spotifyArtistId !in alreadyImported

    /**
     * The set after a pass: what Spotify still lists and an earlier pass handled, plus what this pass handled. An artist the user
     * stopped following ON SPOTIFY leaves it, so following it there again later is new again. Blank ids are never kept.
     */
    fun importedAfterPass(previous: Set<String>, listedNow: Collection<String>, handledNow: Collection<String>): Set<String> {
        val listed = listedNow.toHashSet()
        return (previous + handledNow).filterTo(LinkedHashSet()) { it.isNotBlank() && it in listed }
    }

    /**
     * Comparison key: [ArtistNameMatching.normalize]; when that leaves nothing (a title or name written only in a non-Latin script,
     * which it strips), the trimmed lower-case text, still compared for equality.
     */
    fun matchKey(raw: String): String = ArtistNameMatching.normalize(raw).ifEmpty { raw.trim().lowercase() }

    /**
     * A YouTube album is the Spotify one only when its normalized title is EQUAL and one of its artists is one of the Spotify
     * album's artists. Before, the first album whose title contained (or was contained in) Spotify's was saved, whoever made it.
     */
    fun isSameAlbum(
        spotifyTitle: String,
        spotifyArtists: List<String>,
        youTubeTitle: String,
        youTubeArtists: List<String>,
    ): Boolean {
        val title = matchKey(spotifyTitle)
        if (title.isEmpty() || title != matchKey(youTubeTitle)) return false
        val wanted = spotifyArtists.map(::matchKey).filterTo(HashSet()) { it.isNotEmpty() }
        return youTubeArtists.any { matchKey(it) in wanted }
    }
}
