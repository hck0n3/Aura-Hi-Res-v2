package iad1tya.echo.music.playback

/**
 * Row 903 (owner decision H17, «sin fundido en álbum»): two queue neighbours are «an album played in
 * order» when they are consecutive tracks of the same album — same album id, the next one's position in
 * that album is exactly the current one's plus one — and the queue is advancing in order (shuffle off, not
 * repeating the same song). Then the crossfade is skipped and the transition stays gapless, so a live
 * album or a concept album keeps its segues.
 *
 * Positions come from `song_album_map.index` (the album's own order: track order for online albums, disc
 * then track for local files, row 879). Unknown on either side = not proven = the crossfade stays as it is:
 * this only ever removes a fade where the album order is known for certain.
 */
object AlbumInOrder {

    /** Identity of a (current → next) pair, so each pair is looked up once. Null when either id is missing. */
    fun pairKey(currentMediaId: String?, nextMediaId: String?): String? =
        if (currentMediaId == null || nextMediaId == null) null else "$currentMediaId>$nextMediaId"

    fun isInOrderPair(
        currentAlbumId: String?,
        currentAlbumIndex: Int?,
        nextAlbumId: String?,
        nextAlbumIndex: Int?,
        shuffle: Boolean,
        repeatOne: Boolean,
    ): Boolean {
        if (shuffle || repeatOne) return false
        if (currentAlbumId.isNullOrEmpty() || currentAlbumId != nextAlbumId) return false
        if (currentAlbumIndex == null || nextAlbumIndex == null) return false
        return nextAlbumIndex == currentAlbumIndex + 1
    }
}
