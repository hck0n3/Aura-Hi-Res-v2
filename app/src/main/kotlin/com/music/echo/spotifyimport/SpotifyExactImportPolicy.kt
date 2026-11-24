package iad1tya.echo.music.spotifyimport

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
}
