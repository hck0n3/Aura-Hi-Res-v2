package iad1tya.echo.music.utils

import iad1tya.echo.music.constants.ShareUseSongLinkKey

/**
 * Builds the links the app shares. The raw URL builders ([song], [playlist], [channel], [album])
 * interpolate IDs as-is; the `*ShareText` functions are what every Share action sends (owner
 * 2026-10-20: "siempre a través de link de song.link").
 *
 * Guaranteed vs best-effort on song.link (Odesli): songs (`/y/<videoId>`) and albums/EPs/singles
 * resolve; Odesli has no artist or playlist entity, so those two are the same wrapper of the YouTube
 * Music URL — Aura opens them natively, other people's apps get whatever song.link does with it.
 */
object ShareLinks {
    private const val BASE = "https://music.youtube.com"

    /** song.link page for a YouTube video id: opens the song in Spotify, Apple Music, Deezer, Tidal… */
    private const val SONG_LINK_BASE = "https://song.link/y/"

    /** Odesli's wrapper: `song.link/<url of the item on any streaming service>` (see [viaSongLink]). */
    private const val SONG_LINK_WRAP = "https://song.link/"

    fun song(videoId: String?) = "$BASE/watch?v=$videoId"

    fun playlist(playlistId: String?) = "$BASE/playlist?list=$playlistId"

    fun channel(channelId: String?) = "$BASE/channel/$channelId"

    /** An album by its browseId (`MPREb…`), for the rare album that carries no playlistId. */
    fun album(browseId: String?) = "$BASE/browse/$browseId"

    /**
     * The universal song link. song.link resolves `/y/<videoId>` itself — no API call, no key and no
     * rate limit on our side (its public API was retired: `PUBLIC_API_ACCESS_DEPRECATED`).
     */
    fun songLink(videoId: String?) = "$SONG_LINK_BASE$videoId"

    /**
     * song.link page for ANY item URL: `https://song.link/<url>`. This is the form Odesli documents for
     * a link from a streaming service. Aura itself unwraps it when it opens one of its own shares
     * ([ExternalMusicLinks.songLinkWrappedYouTubeUrl]), so Aura-to-Aura shares keep opening the exact
     * artist / playlist / album inside the app.
     */
    fun viaSongLink(url: String) = "$SONG_LINK_WRAP$url"

    /** True unless the user turned the song.link sharing off in settings. */
    fun useSongLink(): Boolean = PrefsBridge.peek(ShareUseSongLinkKey) ?: true

    // EVERY share in the app (song, album, EP/single, playlist, artist) is built by one of the
    // `*ShareText` functions below, so the song.link rule lives in exactly one place. `songLinkEnabled`
    // is the user's setting ([useSongLink]); tests pass it explicitly.

    /**
     * What a song share sends: "Title — Artist" plus the link (song.link by default, the YouTube Music
     * link when the user prefers it). Falls back to the bare link when there is no title.
     */
    fun songShareText(
        videoId: String?,
        title: String?,
        artists: List<String>,
        songLinkEnabled: Boolean = useSongLink(),
    ): String = withHeading(
        heading = heading(title, artists),
        link = if (songLinkEnabled) songLink(videoId) else song(videoId),
    )

    /**
     * Album, EP or single. Odesli resolves albums from a YouTube Music album URL — the
     * `playlist?list=OLAK5uy…` form is the one the album itself carries. An album without a playlistId
     * falls back to its `browse/MPREb…` URL instead of the broken `list=null`.
     */
    fun albumShareText(
        playlistId: String?,
        browseId: String?,
        title: String?,
        artists: List<String>,
        songLinkEnabled: Boolean = useSongLink(),
    ): String {
        val direct = when {
            !playlistId.isNullOrBlank() -> playlist(playlistId)
            !browseId.isNullOrBlank() -> album(browseId)
            else -> null
        }
        return itemShareText(direct, heading(title, artists), songLinkEnabled)
    }

    /** Playlist. Best-effort on song.link: Odesli has no playlist entity (see the KDoc of [viaSongLink]). */
    fun playlistShareText(playlistId: String?, title: String?, songLinkEnabled: Boolean = useSongLink()): String =
        itemShareText(playlistId?.takeIf { it.isNotBlank() }?.let { playlist(it) }, heading(title, emptyList()), songLinkEnabled)

    /** Artist. Best-effort on song.link: Odesli has no artist entity (see the KDoc of [viaSongLink]). */
    fun artistShareText(channelId: String?, name: String?, songLinkEnabled: Boolean = useSongLink()): String =
        itemShareText(channelId?.takeIf { it.isNotBlank() }?.let { channel(it) }, heading(name, emptyList()), songLinkEnabled)

    private fun itemShareText(directUrl: String?, heading: String, songLinkEnabled: Boolean): String {
        // No id means no link: never fabricate one (the old code interpolated the literal "null").
        if (directUrl == null) return heading
        return withHeading(heading, if (songLinkEnabled) viaSongLink(directUrl) else directUrl)
    }

    private fun heading(title: String?, artists: List<String>): String {
        val cleanTitle = title?.trim().orEmpty()
        if (cleanTitle.isEmpty()) return ""
        val artistLine = artists.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        return if (artistLine.isEmpty()) cleanTitle else "$cleanTitle — $artistLine"
    }

    private fun withHeading(heading: String, link: String) = if (heading.isEmpty()) link else "$heading\n$link"
}
