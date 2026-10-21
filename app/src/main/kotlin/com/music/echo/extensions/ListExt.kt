

package iad1tya.echo.music.extensions

import iad1tya.echo.music.db.entities.Album
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
    fromIndex: Int,
    toIndex: Int,
): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}

fun <T : Any> List<T>.mergeNearbyElements(
    key: (T) -> Any = { it },
    merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
    if (isEmpty()) return emptyList()

    val mergedList = mutableListOf<T>()
    var currentItem = this[0]

    for (i in 1 until size) {
        val nextItem = this[i]
        if (key(currentItem) == key(nextItem)) {
            currentItem = merge(currentItem, nextItem)
        } else {
            mergedList.add(currentItem)
            currentItem = nextItem
        }
    }
    mergedList.add(currentItem)

    return mergedList
}


// Explicit content is never hidden (owner 2026-10-20): no-op on purpose, [enabled] ignored.
@Suppress("UNUSED_PARAMETER")
fun List<Song>.filterExplicit(enabled: Boolean = true): List<Song> = this


fun List<Song>.filterVideoSongs(enabled: Boolean = true) =
    if (enabled) {
        filter { !it.song.isVideo }
    } else {
        this
    }


// Explicit content is never hidden (owner 2026-10-20): no-op on purpose, [enabled] ignored.
@Suppress("UNUSED_PARAMETER")
fun List<Album>.filterExplicitAlbums(enabled: Boolean = true): List<Album> = this


fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false) =
    if (enabled) {
        filterNot { it.playlist.browseId?.startsWith("SS") == true }
    } else {
        this
    }
