package iad1tya.echo.music.utils

import iad1tya.echo.music.constants.ShareUseSongLinkKey

/**
 * Builds the links the app shares. IDs are interpolated as-is so a null ID behaves exactly like the
 * previous inline string templates.
 */
object ShareLinks {
    private const val BASE = "https://music.youtube.com"

    /** song.link page for a YouTube video id: opens the song in Spotify, Apple Music, Deezer, Tidal… */
    private const val SONG_LINK_BASE = "https://song.link/y/"

    fun song(videoId: String?) = "$BASE/watch?v=$videoId"

    fun playlist(playlistId: String?) = "$BASE/playlist?list=$playlistId"

    fun channel(channelId: String?) = "$BASE/channel/$channelId"

    /**
     * The universal song link. song.link resolves `/y/<videoId>` itself — no API call, no key and no
     * rate limit on our side (its public API was retired: `PUBLIC_API_ACCESS_DEPRECATED`).
     */
    fun songLink(videoId: String?) = "$SONG_LINK_BASE$videoId"

    /** True unless the user turned the song.link sharing off in settings. */
    fun useSongLink(): Boolean = PrefsBridge.peek(ShareUseSongLinkKey) ?: true

    /**
     * What a song share sends: "Title — Artist" plus the link (song.link by default, the YouTube Music
     * link when the user prefers it). Falls back to the bare link when there is no title.
     */
    fun songShareText(videoId: String?, title: String?, artists: List<String>): String {
        val link = if (useSongLink()) songLink(videoId) else song(videoId)
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return link
        val artistLine = artists.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        val heading = if (artistLine.isEmpty()) cleanTitle else "$cleanTitle — $artistLine"
        return "$heading\n$link"
    }
}
