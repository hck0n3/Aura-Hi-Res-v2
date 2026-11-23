package iad1tya.echo.music.playback

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.playlistimport.RequestArtistTargets

/** Row 651: how many continuation pages of the artist's song list are read per re-seed (bounded: network and battery). */
private const val CATALOGUE_CONTINUATIONS = 2

/** Row 651: songs of that artist appended per re-seed (the size of a request's own list), and how many such batches are tried. */
internal const val ARTIST_CATALOGUE_BATCH = 25
internal const val ARTIST_CATALOGUE_TRIES = 3

/**
 * Row 651 — the songs of the artist a «Pedir música» request named, from that artist's OWN catalogue: the song list of
 * their channel page (with [lock]'s channel id), else a song search for their name. Only songs [RequestArtistTargets.admits]
 * keeps; never the «Fans might also like» shelf or a radio. Network: call off the main thread.
 * Row 656: null = YouTube did not answer (nothing found, or the channel's song list failed, and the song search failed), so
 * the caller asks again later instead of taking a network failure for an exhausted catalogue; an empty list is a real
 * «nothing of that artist».
 */
internal suspend fun requestArtistCatalogue(lock: RequestArtistTargets.Lock): List<MediaItem>? {
    val songs = ArrayList<SongItem>()
    val browseId = lock.id?.takeIf { it.startsWith("UC") }
    var listFailed = false
    if (browseId != null) {
        val section = YouTube.artist(browseId).getOrNull()?.sections
            ?.firstOrNull { s -> s.items.isNotEmpty() && s.items.all { it is SongItem } }
        section?.items?.filterIsInstance<SongItem>()?.let { songs += it }
        section?.moreEndpoint?.let { more ->
            val page = YouTube.artistItems(more).getOrNull()
            listFailed = page == null
            page?.let { first ->
                songs += first.items.filterIsInstance<SongItem>()
                var continuation = first.continuation
                var pages = 0
                while (continuation != null && pages < CATALOGUE_CONTINUATIONS) {
                    val next = YouTube.artistItemsContinuation(continuation).getOrNull() ?: break
                    songs += next.items.filterIsInstance<SongItem>()
                    continuation = next.continuation
                    pages++
                }
            }
        }
    }
    if (songs.isEmpty() || listFailed) {
        val result = YouTube.search(lock.name, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: return null
        songs += result.items.filterIsInstance<SongItem>()
        result.continuation?.let { c ->
            YouTube.searchContinuation(c).getOrNull()?.items?.filterIsInstance<SongItem>()?.let { songs += it }
        }
    }
    return songs
        .distinctBy { it.id }
        .filter { song -> !song.unavailable && RequestArtistTargets.admits(lock, song.artists.map { it.name to it.id }) }
        .map { it.toMediaItem() }
}
