

package iad1tya.echo.music.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.extensions.toMediaItem
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
    override val startShuffled: Boolean = false,
    // Followed / YouTube playlists: "OL:<id>" so Aleatorio mejorado is the same bucket as a saved
    // copy of this list. Null would leave shuffle as a one-shot scramble with no memory.
    override val contextId: String? = iad1tya.echo.music.playback.ShuffleContexts.onlinePlaylist(playlistId),
    override val seedPlayedIds: Set<String> = emptySet(),
) : Queue {
    /** Read-only exposure for the send-playback-metrics ping (SimpMusic queueData.playlistId). */
    val metricsPlaylistId: String get() = playlistId
    private var continuation: String? = initialContinuation
    private var retryCount = 0
    private val maxRetries = 3

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            // Owner 2026-10-06: songs YouTube Music greys out (or that already failed for good) never
            // enter the queue — the tapped one excepted (see UnavailableSongs.keepPlayable).
            if (initialSongs.isNotEmpty()) {
                val (songs, index) = iad1tya.echo.music.utils.UnavailableSongs.keepPlayable(initialSongs, startIndex)
                Queue.Status(
                    title = playlistTitle,
                    items = songs.map { it.toMediaItem() },
                    mediaItemIndex = index,
                )
            } else {
                val playlistPage = YouTube.playlist(playlistId).getOrThrow()
                continuation = playlistPage.songsContinuation
                val (songs, index) = iad1tya.echo.music.utils.UnavailableSongs.keepPlayable(playlistPage.songs, startIndex)
                Queue.Status(
                    title = playlistPage.playlist.title,
                    items = songs.map { it.toMediaItem() },
                    mediaItemIndex = index,
                )
            }
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            var lastException: Throwable? = null
            
            for (attempt in 0..maxRetries) {
                try {
                    val continuationPage = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                    continuation = continuationPage.continuation
                    retryCount = 0
                    return@withContext iad1tya.echo.music.utils.UnavailableSongs
                        .keepPlayable(continuationPage.songs, startIndex = -1).first
                        .map { it.toMediaItem() }
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }
}
