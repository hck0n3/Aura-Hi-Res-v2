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

    /**
     * Yours: a list made in Aura (no `browseId`; deleting it deletes the row) or a YouTube list still saved in your library. A
     * YouTube list you removed stays as a tombstone with `bookmarkedAt = null`, and a list only browsed is never bookmarked.
     */
    fun isUsersPlaylist(playlist: PlaylistEntity): Boolean = playlist.browseId == null || playlist.bookmarkedAt != null

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
