package iad1tya.echo.music.ui.newui

import iad1tya.echo.music.db.entities.PlaylistEntity

/**
 * Where the player's «Ir a la lista» chip leads (owner 2026-11-22): it exists only while the playing song is in at least one of
 * YOUR playlists, wherever it is playing from. One list opens it; several ask which. The playing context no longer decides —
 * a YouTube list you only played (`OL:`, never saved) is not yours, and a song of yours playing from an album or a radio is.
 */
internal object PlayerPlaylistRoute {
    sealed interface Door {
        data object None : Door
        data class Open(val route: String) : Door
        data class Choose(val playlists: List<PlaylistEntity>) : Door
    }

    /** The special and generated lists, excluded like the library sync does (`DatabaseDao.syncPlaylistRows`). */
    private val generatedIds = setOf(
        PlaylistEntity.LIKED_PLAYLIST_ID,
        PlaylistEntity.DOWNLOADED_PLAYLIST_ID,
        iad1tya.echo.music.reco.AutoRecoPlaylistWorker.PLAYLIST_ID,
    )

    /**
     * Yours = exactly what the Library lists (every Library playlist query requires `bookmarkedAt IS NOT NULL`), minus the
     * generated ones. A YouTube list you removed stays as a tombstone with `bookmarkedAt = null`, a list only browsed is never
     * bookmarked, and an unbookmarked local row cannot be found in the Library — the chip must not lead to any of them.
     */
    fun isUsersPlaylist(playlist: PlaylistEntity): Boolean =
        playlist.bookmarkedAt != null && playlist.id !in generatedIds

    fun usersPlaylists(containing: List<PlaylistEntity>): List<PlaylistEntity> =
        containing.filter(::isUsersPlaylist).distinctBy { it.id }

    /** The song is IN this list, so the row has songs: the Library opens such a list on its local page too (`Library.kt`). */
    fun route(playlistId: String): String = "local_playlist/$playlistId"

    fun door(containing: List<PlaylistEntity>): Door {
        val mine = usersPlaylists(containing)
        return when (mine.size) {
            0 -> Door.None
            1 -> Door.Open(route(mine.single().id))
            else -> Door.Choose(mine)
        }
    }
}
