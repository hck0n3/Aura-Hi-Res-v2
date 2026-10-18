package iad1tya.echo.music.ui.newui

/**
 * Where the player's «Ir a la lista» chip leads, from the live queue's Enhanced-Shuffle context id
 * ([iad1tya.echo.music.playback.ShuffleContexts]): `PL:<local id>` is one of YOUR playlists, `OL:<browse id>` a followed
 * YouTube one. Anything else (an album `AL:`, a request `MR:`, a radio, no context) has no playlist page, so no chip.
 */
internal object PlayerPlaylistRoute {
    fun of(contextId: String?): String? {
        val id = contextId ?: return null
        return when {
            id.startsWith("PL:") && id.length > 3 -> "local_playlist/${id.removePrefix("PL:")}"
            id.startsWith("OL:") && id.length > 3 -> "online_playlist/${id.removePrefix("OL:")}"
            else -> null
        }
    }
}
