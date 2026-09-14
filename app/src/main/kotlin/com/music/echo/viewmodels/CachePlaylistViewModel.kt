

package iad1tya.echo.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import iad1tya.echo.music.constants.HideExplicitKey
import iad1tya.echo.music.constants.HideVideoSongsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.di.DownloadCache
import iad1tya.echo.music.di.PlayerCache
import iad1tya.echo.music.extensions.filterExplicit
import iad1tya.echo.music.extensions.filterVideoSongs
import iad1tya.echo.music.playback.StreamCacheKeys
import iad1tya.echo.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache
) : ViewModel() {

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    /**
     * Songs whose listen-cache copy is COMPLETE — exactly what offline mode can play (owner directive
     * 2026-09-14: "el modo sin conexión puede reproducir lo que está en caché"). Same completeness test
     * as MusicService.fullyCachedListenUri: the cache's own content length, all bytes present.
     * [cachedSongs] also lists partial copies, which would fail with no network.
     */
    private val _fullyCachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val fullyCachedSongs: StateFlow<List<Song>> = _fullyCachedSongs

    private fun isListenCopyComplete(keys: Set<String>, songId: String): Boolean =
        keys.any { key ->
            (StreamCacheKeys.belongsTo(key, songId) || key == songId) &&
                androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(playerCache.getContentMetadata(key))
                    .let { length -> length > 0 && playerCache.isCached(key, 0, length) }
        }

    init {
        // Run off the main thread: the DataStore reads and SimpleCache key-scans below
        // must not block the UI thread. Tied to viewModelScope so it is cancelled when the
        // ViewModel is cleared; delay() is cancellable, so the loop stops.
        //
        // SUBSCRIBER-GATED (thermal audit's #1 finding): this ViewModel is ALSO obtained by the song
        // ⋯-menu via hiltViewModel(), whose host sits OUTSIDE the NavHost — its owner is the ACTIVITY,
        // so onCleared never fires until the app dies. Opening any song menu ONCE therefore left this
        // loop issuing a Room relation query + two full cache-key-set copies + N SimpleCache lock scans
        // EVERY SECOND for the rest of the session, screen off included (~86k queries/day). The loop now
        // PARKS (suspends, zero work) whenever nobody is collecting [cachedSongs] — collectAsState
        // subscribes only while the cache screen/menu is actually composed — and resumes on demand.
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val subscribers = kotlinx.coroutines.flow.combine(
                    _cachedSongs.subscriptionCount,
                    _fullyCachedSongs.subscriptionCount,
                ) { cached, full -> cached + full }
                if (subscribers.first() == 0) {
                    subscribers.first { it > 0 }
                }
                // Non-blocking suspend read of the latest preferences (no runBlocking on any thread).
                val prefs = context.dataStore.data.first()
                val hideExplicit = prefs[HideExplicitKey] ?: false
                val hideVideoSongs = prefs[HideVideoSongsKey] ?: false
                // LISTEN-CACHE KEYS (2026-08-29 audit): streamed bytes land under stable
                // yt-stream-<videoId>-<itag> keys (StreamCacheKeys), NOT mediaId — looking up
                // raw cache keys against Room song ids returned nothing, so "En caché" showed
                // EMPTY while the listen-cache held gigabytes. Map every key to its song id:
                // yt-stream-* parse to their videoId; non-googlevideo keys (Qobux/podcasts) ARE
                // the mediaId already, so they pass through unchanged.
                val cachedIds = playerCache.keys
                    .mapNotNull { StreamCacheKeys.songIdOf(it) ?: it }
                    .toSet()
                val downloadedIds = downloadCache.keys.toSet()
                val pureCacheIds = cachedIds.subtract(downloadedIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                // OWNER RULE (2026-08-31): "En caché" must list EVERY song whose listen-bytes are
                // on disk. The ids in [songs] already come from the key set above (yt-stream-*
                // keys parsed to their videoId; non-googlevideo keys ARE the mediaId), so presence
                // is established. The OLD filter re-demanded format.contentLength, which is NULL
                // for most YouTube streams, excluding the exact songs the owner listened to
                // (bytes under yt-stream-*) while the list showed empty — "I don't see my app
                // caching anything". Completeness (isCached over 0..contentLength) now only
                // SORTS — fully cached first — and only when contentLength is known; it is NEVER
                // an exclusion gate. One keys-snapshot per pass instead of one per song (the old
                // filter re-copied the full key set for every song).
                val playerKeys = playerCache.keys
                val cachedEntries = songs.map { song ->
                    val fullyCached = song.format?.contentLength?.let { length ->
                        playerKeys.any { key ->
                            (StreamCacheKeys.belongsTo(key, song.song.id) || key == song.song.id) &&
                                playerCache.isCached(key, 0, length)
                        }
                    } == true
                    song to fullyCached
                }

                _fullyCachedSongs.value = songs
                    .filter { song -> isListenCopyComplete(playerKeys, song.song.id) }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)

                val cachedSongsNow = cachedEntries.map { it.first }
                if (cachedSongsNow.isNotEmpty()) {
                    database.query {
                        cachedSongsNow.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = cachedEntries
                    .filter { it.first.song.dateDownload != null }
                    .sortedWith(
                        compareByDescending<Pair<Song, Boolean>> { it.second }
                            .thenByDescending { it.first.song.dateDownload }
                    )
                    .map { it.first }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)

                delay(1000)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        // Purge BOTH key families: the stable yt-stream-<videoId>-<itag> listen keys (any cached
        // quality of the song) and the legacy mediaId resource. Missing only the first family was
        // the 2026-08-29 audit's placebo twin: the song vanished from the list but its bytes stayed
        // on disk (registry row #36's lesson — remove the BYTES, not just the visibility).
        playerCache.keys
            .filter { StreamCacheKeys.belongsTo(it, songId) || it == songId }
            .forEach { playerCache.removeResource(it) }
    }
}
