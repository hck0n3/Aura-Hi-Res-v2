

package iad1tya.echo.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.completedStreaming
import com.music.innertube.utils.parseCookieString
import iad1tya.echo.music.constants.InnerTubeCookieKey
import iad1tya.echo.music.constants.LastFMUseSendLikes
import iad1tya.echo.music.constants.LastFullSyncKey
import iad1tya.echo.music.constants.SuppressedPlaylistIdsKey
import iad1tya.echo.music.constants.YtmLastSyncKey
import iad1tya.echo.music.constants.SYNC_COOLDOWN
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.Playlist
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.extensions.collectLatest
import iad1tya.echo.music.extensions.isInternetConnected
import iad1tya.echo.music.extensions.isSyncEnabled
import iad1tya.echo.music.models.MediaMetadata
import iad1tya.echo.music.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOperation {
    /**
     * [includeUpload] gates the Aura -> YouTube upload pass. It is FALSE for the passive
     * [SyncUtils.tryAutoSync] path, which runs whenever Home/Library is opened (cooldown aside) — the
     * owner's standing battery/thermal gate means a whole-library upload must never ride along with an
     * app start. It is TRUE only for deliberate actions: the manual "Sincronizar todo", the
     * user-configured scheduled sync, and the explicit upload button.
     */
    data class FullSync(val includeUpload: Boolean = true) : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistsSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object AutoSyncPlaylists : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: String) : SyncOperation()
    data class LikeSong(val song: SongEntity) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()
    data object ClearAllSynced : SyncOperation()
    data object MirrorLikedSongs : SyncOperation()
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Completed : SyncStatus()
}

data class SyncState(
    val overallStatus: SyncStatus = SyncStatus.Idle,
    val likedSongs: SyncStatus = SyncStatus.Idle,
    val librarySongs: SyncStatus = SyncStatus.Idle,
    val uploadedSongs: SyncStatus = SyncStatus.Idle,
    val likedAlbums: SyncStatus = SyncStatus.Idle,
    val uploadedAlbums: SyncStatus = SyncStatus.Idle,
    val artists: SyncStatus = SyncStatus.Idle,
    val playlists: SyncStatus = SyncStatus.Idle,
    val currentOperation: String = ""
)

/**
 * HALLAZGO-056: decides whether a sync pull op may start. The BETA-012 log showed FOUR parallel
 * `FEmusic_library_privately_owned_tracks` browses landing within 31 ms — YouTube answered 429
 * (rateLimitExceeded) and the whole library rendered as an error. The root cause was
 * [SyncUtils.performFullSyncSuspend] bypassing the serial sync channel and running CONCURRENTLY
 * with a queued FullSync plus individual queued ops, so the same pull pass hit YouTube several
 * times at once. Rules:
 *  - two full syncs never overlap (the second coalesces into the running one);
 *  - an individual pull op never starts while a full sync is running (the full sync already
 *    covers every individual pass);
 *  - an individual op never overlaps another run of the SAME op;
 *  - otherwise the op starts.
 * Write/targeted ops (likes, single playlist, mirror, clear) are never gated by this.
 */
fun shouldStartSyncOp(isFullSync: Boolean, fullSyncRunning: Boolean, sameOpRunning: Boolean): Boolean {
    if (sameOpRunning) return false
    if (!isFullSync && fullSyncRunning) return false
    return true
}

/**
 * HALLAZGO-056 (full-sync composite): a down-only request (auto-sync) coalesces into ANY running
 * full sync, but an upload-carrying request (manual "Sincronizar todo", pull-to-refresh) only
 * coalesces when an upload pass is already in flight — a down-only run does not cover the upload,
 * and silently dropping it would change what the deliberate action does. When an upload run starts
 * next to a down-only run, the per-step single-flight guards keep every YouTube request unique.
 */
fun shouldStartFullSync(requestIncludeUpload: Boolean, fullSyncRunning: Boolean, uploadInFlight: Boolean): Boolean {
    if (!fullSyncRunning) return true
    if (!requestIncludeUpload) return false
    return !uploadInFlight
}

/**
 * HALLAZGO-028: true while ANY sync pass is in flight. `overallStatus` only transitions for the
 * FullSync composite and ClearAllSynced — individual ops (the four login pulls, single playlist,
 * likes...) only move their own per-op field — so "is anything syncing" must be derived from the
 * per-op statuses, not from `overallStatus` alone. Drives the Home "Sincronizando tu biblioteca…"
 * indicator: after login the UI used to look dead for minutes while the pulls ran with zero
 * feedback (owner read that as the app being broken/slow).
 */
fun anySyncActive(state: SyncState): Boolean =
    state.overallStatus == SyncStatus.Syncing ||
        state.likedSongs == SyncStatus.Syncing ||
        state.librarySongs == SyncStatus.Syncing ||
        state.uploadedSongs == SyncStatus.Syncing ||
        state.likedAlbums == SyncStatus.Syncing ||
        state.uploadedAlbums == SyncStatus.Syncing ||
        state.artists == SyncStatus.Syncing ||
        state.playlists == SyncStatus.Syncing

@Singleton
class SyncUtils @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val libraryUploadSync: LibraryUploadSync,
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Timber.e(throwable, "Sync coroutine exception")
        }
    }

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(Dispatchers.IO + syncJob + exceptionHandler)

    private val syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
    private var processingJob: Job? = null

    /**
     * HALLAZGO-056: keys of the pull passes running RIGHT NOW, across every entry point (the serial
     * channel, the `*Suspend()` direct calls, [performFullSyncSuspend]). The channel alone cannot
     * dedupe — the direct suspend paths bypass it — so this set is what keeps one pull pass from
     * hitting YouTube twice at once (the 429 storm that broke the library on BETA-012).
     */
    private val inFlightOps = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var lastfmSendLikes = false

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L

        // HALLAZGO-056: single-flight keys for the coalescing guard below. One key per pull pass.
        // The full-sync composite has TWO keys: a down-only run and an upload-carrying run, because
        // a down-only run does not cover a deliberate upload request (see shouldStartFullSync).
        private const val OP_FULL_SYNC_DOWN = "FullSyncDown"
        private const val OP_FULL_SYNC_UPLOAD = "FullSyncUpload"
        private const val OP_LIKED_SONGS = "LikedSongs"
        private const val OP_LIBRARY_SONGS = "LibrarySongs"
        private const val OP_UPLOADED_SONGS = "UploadedSongs"
        private const val OP_LIKED_ALBUMS = "LikedAlbums"
        private const val OP_UPLOADED_ALBUMS = "UploadedAlbums"
        private const val OP_ARTISTS = "ArtistsSubscriptions"
        private const val OP_SAVED_PLAYLISTS = "SavedPlaylists"
        private const val OP_AUTO_SYNC_PLAYLISTS = "AutoSyncPlaylists"
        // How many rows we write per DB transaction during a sync. Whole blocks are written inside a
        // single withTransaction {} so Room's invalidation tracker emits ONCE per block (not once per
        // row) — this is what kills the Library "flicker" during a sync while still updating in near
        // real time (the list grows a block at a time). It also lets us drop the old per-row throttle
        // (the former DB_OPERATION_DELAY_MS) that made syncing 4000+ likes crawl.
        private const val SYNC_BATCH_SIZE = 300
        // SQLite caps the number of bound host parameters (historically 999). Chunk `id IN (...)`
        // lookups so a 4000+ liked-songs library never blows past that limit.
        private const val SQL_IN_CHUNK = 900
        // Max artist cover photos to fetch per sync run (bounded so it never hammers the network/API).
        private const val MAX_ARTIST_IMAGE_FETCH = 250
        // Both artist-sync network loops below (channelId backfill + missing-cover fetch) used to run
        // fully SERIAL — one artist, wait for the response, next artist — which is what made "your
        // artists" sit blank for a long stretch on an account following many artists (reported by the
        // owner: "cuesta que los artistas carguen su portada"). Bounded concurrency instead of either
        // extreme: unbounded parallel requests risk looking like automated traffic to YouTube's bot
        // detection (the exact failure mode this app spent all day fighting elsewhere), so this stays a
        // small, fixed-size batch rather than firing everything at once.
        private const val ARTIST_NETWORK_LOOKUP_CONCURRENCY = 5
        private val LastLikedSyncTimeKey = longPreferencesKey("last_liked_sync_time")
    }

    init {
        context.dataStore.data
            .map { it[LastFMUseSendLikes] ?: false }
            .distinctUntilChanged()
            .collectLatest(syncScope) {
                lastfmSendLikes = it
            }

        startProcessingQueue()
    }

    private fun startProcessingQueue() {
        processingJob = syncScope.launch {
            for (operation in syncChannel) {
                try {
                    processOperation(operation)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing sync operation: $operation")
                }
            }
        }
    }

    /**
     * HALLAZGO-056: runs a pull pass single-flight. If an overlapping run already covers it (same
     * pass in flight, or a full sync in flight for an individual pass) the call coalesces into a
     * no-op instead of firing duplicate YouTube requests — the duplicate browses were what earned
     * the 429 rateLimitExceeded storm on BETA-012. The [inFlightOps] add/remove bracket is what
     * makes the guard visible to EVERY entry point (channel processor, `*Suspend()`, full sync).
     */
    private suspend fun singleFlightOp(key: String, isFullSync: Boolean = false, block: suspend () -> Unit) {
        val fullSyncRunning = inFlightOps.contains(OP_FULL_SYNC_DOWN) || inFlightOps.contains(OP_FULL_SYNC_UPLOAD)
        if (!shouldStartSyncOp(isFullSync, fullSyncRunning, inFlightOps.contains(key))) {
            Timber.d("Sync op '$key' coalesced — an overlapping run already covers it (HALLAZGO-056)")
            return
        }
        if (!inFlightOps.add(key)) return // lost the add-race against a concurrent start of the same op
        try {
            block()
        } finally {
            inFlightOps.remove(key)
        }
    }

    /**
     * HALLAZGO-056: single-flight for the full-sync COMPOSITE. Down-only requests coalesce into any
     * running full sync; upload-carrying requests only coalesce into another upload run (a down-only
     * run does not cover the upload pass). Two overlapping composites can therefore only be one
     * down-only + one upload run, and their inner steps are individually single-flighted inside
     * [executeFullSync], so every YouTube request stays unique.
     */
    private suspend fun runFullSyncCoalesced(includeUpload: Boolean) {
        val uploadInFlight = inFlightOps.contains(OP_FULL_SYNC_UPLOAD)
        val fullSyncRunning = inFlightOps.contains(OP_FULL_SYNC_DOWN) || uploadInFlight
        if (!shouldStartFullSync(includeUpload, fullSyncRunning, uploadInFlight)) {
            Timber.d("Full sync coalesced — an overlapping run already covers it (HALLAZGO-056)")
            return
        }
        val key = if (includeUpload) OP_FULL_SYNC_UPLOAD else OP_FULL_SYNC_DOWN
        if (!inFlightOps.add(key)) return // lost the add-race against a concurrent start of the same kind
        try {
            executeFullSync(includeUpload)
        } finally {
            inFlightOps.remove(key)
        }
    }

    private suspend fun processOperation(operation: SyncOperation) {
        when (operation) {
            // PULL ops run single-flight (HALLAZGO-056): the channel is serial with ITSELF, but the
            // direct suspend entry points bypass it, so the same pass could otherwise run twice at once.
            is SyncOperation.FullSync -> runFullSyncCoalesced(operation.includeUpload)
            is SyncOperation.LikedSongs -> singleFlightOp(OP_LIKED_SONGS) { executeSyncLikedSongs() }
            is SyncOperation.LibrarySongs -> singleFlightOp(OP_LIBRARY_SONGS) { executeSyncLibrarySongs() }
            is SyncOperation.UploadedSongs -> singleFlightOp(OP_UPLOADED_SONGS) { executeSyncUploadedSongs() }
            is SyncOperation.LikedAlbums -> singleFlightOp(OP_LIKED_ALBUMS) { executeSyncLikedAlbums() }
            is SyncOperation.UploadedAlbums -> singleFlightOp(OP_UPLOADED_ALBUMS) { executeSyncUploadedAlbums() }
            is SyncOperation.ArtistsSubscriptions -> singleFlightOp(OP_ARTISTS) { executeSyncArtistsSubscriptions() }
            is SyncOperation.SavedPlaylists -> singleFlightOp(OP_SAVED_PLAYLISTS) { executeSyncSavedPlaylists() }
            is SyncOperation.AutoSyncPlaylists -> singleFlightOp(OP_AUTO_SYNC_PLAYLISTS) { executeSyncAutoSyncPlaylists() }
            // WRITE / targeted ops are NEVER coalesced: they carry deliberate user actions (a like,
            // one playlist, a mirror, a wipe) that must run exactly as requested.
            is SyncOperation.SinglePlaylist -> executeSyncPlaylist(operation.browseId, operation.playlistId)
            is SyncOperation.LikeSong -> executeLikeSong(operation.song)
            is SyncOperation.CleanupDuplicates -> executeCleanupDuplicatePlaylists()
            is SyncOperation.ClearAllSynced -> executeClearAllSyncedContent()
            is SyncOperation.MirrorLikedSongs -> executeMirrorLikedSongs()
        }
    }

    private suspend fun isLoggedIn(): Boolean {
        return try {
            val cookie = context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .first()
            cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error checking login status")
            false
        }
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = MAX_RETRIES,
        initialDelay: Long = INITIAL_RETRY_DELAY_MS,
        block: suspend () -> T
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                Timber.w(e, "Attempt ${attempt + 1}/$maxRetries failed")
                if (attempt == maxRetries - 1) {
                    return Result.failure(e)
                }
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return Result.failure(Exception("Max retries exceeded"))
    }

    private fun updateState(update: SyncState.() -> SyncState) {
        _syncState.value = _syncState.value.update()
    }

    // ---- Batched DB helpers (speed + real-time-without-flicker) ------------------------------------
    // The sync loops used to read each row back with `database.song(id).firstOrNull()` (N+1), then write
    // each row in its OWN `database.transaction {}` with a per-row `delay(50ms)`. That made Room re-emit
    // the FULL list per row (flicker) and dragged a 4000+ liked library out for minutes. Instead we now
    // load the existing rows once, decide in memory, and write in blocks — one transaction per block.

    /**
     * Load the existing [SongEntity] rows for [ids] in ONE (chunked) query instead of N+1
     * `database.song(id).firstOrNull()` reads. Chunked to stay under SQLite's bound-parameter cap.
     */
    private suspend fun loadExistingSongs(ids: List<String>): HashMap<String, SongEntity> {
        val map = HashMap<String, SongEntity>(ids.size.coerceAtLeast(0))
        if (ids.isEmpty()) return map
        ids.distinct().chunked(SQL_IN_CHUNK).forEach { chunk ->
            database.getSongsByIds(chunk).forEach { map[it.id] = it.song }
        }
        return map
    }

    /** Same as [loadExistingSongs] but for followed/synced artists. */
    private suspend fun loadExistingArtists(ids: List<String>): HashMap<String, ArtistEntity> {
        val map = HashMap<String, ArtistEntity>(ids.size.coerceAtLeast(0))
        if (ids.isEmpty()) return map
        ids.distinct().chunked(SQL_IN_CHUNK).forEach { chunk ->
            database.getArtistEntitiesByIds(chunk).forEach { map[it.id] = it }
        }
        return map
    }

    /**
     * Insert brand-new synced songs (+ their artists/maps) in blocks of [SYNC_BATCH_SIZE], each block in
     * its own transaction so Room emits once per block. [entities] and [mediaList] are index-aligned.
     */
    private suspend fun batchInsertSongs(entities: List<SongEntity>, mediaList: List<MediaMetadata>) {
        if (entities.isEmpty()) return
        entities.indices.chunked(SYNC_BATCH_SIZE).forEach { block ->
            val songBlock = block.map { entities[it] }
            val metaBlock = block.map { mediaList[it] }
            database.withTransaction {
                insertSongsWithArtists(songBlock, metaBlock)
            }
        }
    }

    /** Update existing songs in blocks of [SYNC_BATCH_SIZE], one transaction per block. */
    private suspend fun batchUpdateSongs(entities: List<SongEntity>) {
        if (entities.isEmpty()) return
        entities.chunked(SYNC_BATCH_SIZE).forEach { block ->
            database.withTransaction {
                updateSongs(block)
            }
        }
    }

    // ---- Per-page streaming inserts (HALLAZGO-028) --------------------------------------------------
    // After Google sign-in the four library pulls fetched up to 50 continuation pages BEFORE the
    // first row reached Room, so Home looked dead for minutes (owner read it as a broken restart).
    // These helpers run once per page, as pages arrive from completedStreaming: same differential
    // logic and same batched transactions as the old end-of-fetch pass, just sliced per page. A
    // page re-run after a fetch retry is harmless — the diff sees already-inserted rows as
    // existing and skips them. Passes that need the FULL remote list (reconciles, ytm stamping)
    // still run once after the last page, in each executor below.

    /**
     * One liked-songs page → Room. [startIndex] is the position of the page's first song in the
     * FULL remote list: new likes are stamped `likedDate = now - position` so the remote order
     * (newest first) survives across pages, exactly like the old single-pass stamp. KNOWN songs
     * keep their ORIGINAL likedDate (never rewritten on sync → preserves local order, no
     * accidental re-uploads).
     */
    private suspend fun insertLikedSongsPage(songs: List<SongItem>, now: LocalDateTime, startIndex: Int) {
        if (songs.isEmpty()) return
        val existing = loadExistingSongs(songs.map { it.id })
        val insertEntities = ArrayList<SongEntity>()
        val insertMeta = ArrayList<MediaMetadata>()
        val updateEntities = ArrayList<SongEntity>()
        songs.forEachIndexed { offset, song ->
            try {
                val dbSong = existing[song.id]
                val isVideoSong = song.isVideoSong
                if (dbSong == null) {
                    val meta = song.toMediaMetadata()
                    insertMeta.add(meta)
                    insertEntities.add(
                        meta.toSongEntity().copy(
                            liked = true,
                            likedDate = now.minusSeconds((startIndex + offset).toLong()),
                            isVideo = isVideoSong,
                        )
                    )
                } else if (!dbSong.liked || dbSong.isVideo != isVideoSong) {
                    // Keep the original likedDate — never overwrite it with the sync timestamp.
                    updateEntities.add(dbSong.copy(liked = true, isVideo = isVideoSong))
                }
            } catch (e: Exception) {
                // Never swallow coroutine cancellation: doing so let the sync loop keep
                // running after its job was cancelled, blasting through every song and
                // flooding logs / pegging the CPU (made playback fail right after a restore).
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to process song: ${song.id}")
            }
        }
        batchInsertSongs(insertEntities, insertMeta)
        batchUpdateSongs(updateEntities)
        Timber.d("Liked songs sync: page inserted ${insertEntities.size} new, updated ${updateEntities.size} (of ${songs.size})")
    }

    /**
     * One library-songs page → Room. toggleLibrary() keeps its existing behaviour: new songs get
     * inLibrary set (and are pushed to the account), known songs are only touched when they aren't
     * in the library yet. Pages are inserted in arrival order — the old pass reversed the FULL
     * accumulated list first, which only affected the order of the inLibrary timestamps inside one
     * batch, never which rows end up in the library.
     */
    private suspend fun insertLibrarySongsPage(items: List<YTItem>) {
        val pageSongs = items.filterIsInstance<SongItem>()
        if (pageSongs.isEmpty()) return
        val existing = loadExistingSongs(pageSongs.map { it.id })
        val insertEntities = ArrayList<SongEntity>()
        val insertMeta = ArrayList<MediaMetadata>()
        val updateEntities = ArrayList<SongEntity>()
        pageSongs.forEach { song ->
            try {
                val dbSong = existing[song.id]
                if (dbSong == null) {
                    val meta = song.toMediaMetadata()
                    insertMeta.add(meta)
                    insertEntities.add(meta.toSongEntity().toggleLibrary())
                } else if (dbSong.inLibrary == null) {
                    updateEntities.add(dbSong.toggleLibrary())
                }
            } catch (e: Exception) {
                // Never swallow coroutine cancellation: doing so let the sync loop keep
                // running after its job was cancelled, blasting through every song and
                // flooding logs / pegging the CPU (made playback fail right after a restore).
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to process song: ${song.id}")
            }
        }
        batchInsertSongs(insertEntities, insertMeta)
        batchUpdateSongs(updateEntities)
        Timber.d("Library songs sync: page inserted ${insertEntities.size} new, updated ${updateEntities.size} (of ${pageSongs.size})")
    }

    /**
     * One artist-subscriptions page → Room. channelId lookups for the page run concurrently
     * (bounded), exactly like the old whole-list pass but scoped to the page, so the page's insert
     * is only gated on its own lookups.
     */
    private suspend fun insertArtistsPage(items: List<YTItem>, now: LocalDateTime) {
        val pageArtists = items.filterIsInstance<ArtistItem>()
        if (pageArtists.isEmpty()) return
        val existingArtists = loadExistingArtists(pageArtists.map { it.id })

        // Resolve missing channelIds CONCURRENTLY (bounded), not one getChannelId() network
        // round-trip per artist serially inside the loop below — that N+1 pattern gated the
        // WHOLE batched insert/update at the end of this function on every single lookup
        // finishing. thumbnailUrl comes from the SAME already-fetched library page below, not
        // from this lookup, so covers were sitting ready in memory but stuck waiting behind
        // it — reported by the owner as "cuesta que los artistas carguen su portada".
        val resolvedChannelIds: Map<String, String> = coroutineScope {
            val lookupSemaphore = Semaphore(ARTIST_NETWORK_LOOKUP_CONCURRENCY)
            pageArtists
                .filter { it.channelId == null && it.id.startsWith("UC") }
                .map { artist ->
                    async {
                        lookupSemaphore.withPermit {
                            val channelId = try {
                                YouTube.getChannelId(artist.id).takeIf { it.isNotEmpty() }
                            } catch (e: Exception) {
                                // Never swallow coroutine cancellation: doing so let the sync
                                // loop keep running after its job was cancelled, blasting
                                // through every song and flooding logs / pegging the CPU
                                // (made playback fail right after a restore).
                                if (e is CancellationException) throw e
                                null
                            }
                            artist.id to channelId
                        }
                    }
                }
                .awaitAll()
                .mapNotNull { (id, channelId) -> channelId?.let { id to it } }
                .toMap()
        }

        val artistsToInsert = ArrayList<ArtistEntity>()
        val artistsToUpdate = ArrayList<ArtistEntity>()
        pageArtists.forEach { artist ->
            try {
                val dbArtist = existingArtists[artist.id]
                val channelId = artist.channelId ?: resolvedChannelIds[artist.id]

                if (dbArtist == null) {
                    artistsToInsert.add(
                        ArtistEntity(
                            id = artist.id,
                            name = artist.title,
                            thumbnailUrl = artist.thumbnail,
                            channelId = channelId,
                            // This id came from the account's REAL subscription list, so the
                            // row is a deliberate follow by definition — stamp the follow
                            // column directly instead of relying on the backfill below
                            // (which is best-effort; registry #154).
                            bookmarkedAt = now,
                            followedByUserAt = now,
                        )
                    )
                } else {
                    // The user UNFOLLOWED this artist in Aura and the unsubscribe has not
                    // reached YouTube yet. Re-bookmarking it here would undo their unfollow
                    // on every sync — the same trap the saved-playlists sync already guards
                    // against with its tombstone check. Refresh the metadata, but leave the
                    // follow alone; LibraryUploadSync will finish the unsubscribe.
                    //
                    // Keyed off the POSITIVE intent marker, not off "bookmarkedAt is gone".
                    // A missing bookmark is also what a logout, a reset, a restore from an
                    // old backup or an artist row created incidentally by a song looks
                    // like, and none of those is a request to unsubscribe.
                    val pendingUnfollow = dbArtist.unfollowedByUserAt != null
                    val needsChannelIdUpdate = dbArtist.channelId == null && channelId != null
                    if (dbArtist.bookmarkedAt == null || needsChannelIdUpdate ||
                        dbArtist.name != artist.title || dbArtist.thumbnailUrl != artist.thumbnail) {
                        artistsToUpdate.add(
                            dbArtist.copy(
                                name = artist.title,
                                thumbnailUrl = artist.thumbnail,
                                channelId = channelId ?: dbArtist.channelId,
                                bookmarkedAt = if (pendingUnfollow) null
                                else dbArtist.bookmarkedAt ?: now,
                                lastUpdateTime = now
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Never swallow coroutine cancellation: doing so let the sync loop keep
                // running after its job was cancelled, blasting through every song and
                // flooding logs / pegging the CPU (made playback fail right after a restore).
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to process artist: ${artist.id}")
            }
        }
        artistsToInsert.chunked(SYNC_BATCH_SIZE).forEach { block ->
            database.withTransaction { insertArtists(block) }
        }
        artistsToUpdate.chunked(SYNC_BATCH_SIZE).forEach { block ->
            database.withTransaction { updateArtists(block) }
        }
        Timber.d("Artist subscriptions sync: page inserted ${artistsToInsert.size} new, updated ${artistsToUpdate.size} (of ${pageArtists.size})")
    }

    /**
     * One saved-playlists page → Room (+ each playlist's own content sync). Playlists are handled
     * in arrival order — the old pass reversed the FULL list first, which only affected the order
     * of the bookmarkedAt timestamps inside one batch, never which playlists end up bookmarked.
     */
    private suspend fun syncSavedPlaylistsPage(items: List<YTItem>, suppressedIds: Set<String>) {
        val pagePlaylists = items.filterIsInstance<PlaylistItem>()
            .filterNot { it.id == "LM" || it.id == "SE" }
        if (pagePlaylists.isEmpty()) return

        for (playlist in pagePlaylists) {
            try {
                if (suppressedIds.contains(playlist.id)) {
                    Timber.d("syncSavedPlaylists: skipping ${playlist.title} (${playlist.id}) — suppressed by user")
                    continue
                }

                // Look at EVERY local row for this browseId, not just the bookmarked ones.
                // localPlaylists comes from a Library query (WHERE bookmarkedAt IS NOT NULL),
                // so a playlist the user REMOVED from the app read as "not present here" and
                // was re-inserted as a SECOND row — it reappeared in Library and the removal
                // undid itself on every sync (owner report).
                val existingRows = database.playlistsByBrowseIdBlocking(playlist.id)
                val bookmarkedRow = existingRows.firstOrNull { it.playlist.bookmarkedAt != null }

                // The user removed it on purpose (rows exist, none bookmarked): respect that.
                // Saving it again from the online playlist screen re-bookmarks the same row.
                if (existingRows.isNotEmpty() && bookmarkedRow == null) {
                    Timber.d("syncSavedPlaylists: skipping ${playlist.title} (${playlist.id}) — removed by the user")
                    continue
                }

                var playlistEntity = bookmarkedRow?.playlist

                if (playlistEntity == null) {
                    playlistEntity = PlaylistEntity(
                        name = playlist.title,
                        browseId = playlist.id,
                        thumbnailUrl = playlist.thumbnail,
                        isEditable = playlist.isEditable,
                        bookmarkedAt = LocalDateTime.now(),
                        remoteSongCount = playlist.songCountText?.let {
                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                        },
                        playEndpointParams = playlist.playEndpoint?.params,
                        shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                        radioEndpointParams = playlist.radioEndpoint?.params
                    )
                    database.insert(playlistEntity)
                    Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                } else {
                    database.update(playlistEntity, playlist)
                    Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                }

                executeSyncPlaylist(playlist.id, playlistEntity.id)
            } catch (e: Exception) {
                // Never swallow coroutine cancellation: doing so let the sync loop keep
                // running after its job was cancelled, blasting through every song and
                // flooding logs / pegging the CPU (made playback fail right after a restore).
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to sync playlist ${playlist.title}")
            }
        }
    }

    /** Deliberate "Sincronizar todo": pulls everything down AND pushes the library up. */
    fun performFullSync() {
        syncScope.launch {
            syncChannel.send(SyncOperation.FullSync(includeUpload = true))
        }
    }

    suspend fun performFullSyncSuspend() {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return
        }
        // HALLAZGO-056: this entry point BYPASSES the serial channel (workers, "Sincronizar todo",
        // pull-to-refresh) — without the single-flight guard it ran concurrently with a queued FullSync
        // and fired the same pull passes at YouTube several times at once (the 429 storm).
        runFullSyncCoalesced(includeUpload = true)
    }

    fun tryAutoSync() {
        syncScope.launch {
            if (!isLoggedIn()) {
                Timber.d("Skipping auto sync - user not logged in")
                return@launch
            }

            if (!context.isSyncEnabled() || !context.isInternetConnected()) {
                return@launch
            }

            val lastSync = context.dataStore.get(LastFullSyncKey, 0L)
            val currentTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            // A NEGATIVE elapsed means the wall clock was rolled back past the stored lastSync;
            // without the lower bound that would keep auto-sync blocked indefinitely (elapsed stays
            // < cooldown until the clock catches up). Treat clock drift as an expired cooldown.
            val elapsed = currentTime - lastSync
            if (lastSync > 0 && elapsed in 0 until SYNC_COOLDOWN) {
                return@launch
            }

            // DOWN-ONLY. This fires from HomeViewModel/LibraryViewModels, i.e. effectively on app
            // start (30-min cooldown aside). Uploading a whole library from here would put hundreds of
            // network writes behind opening the app — exactly what the battery/thermal gate forbids.
            // The upload runs on deliberate actions only (manual "Sincronizar todo", the scheduled
            // sync, the explicit button, or right after an import).
            syncChannel.send(SyncOperation.FullSync(includeUpload = false))

            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }
        }
    }

    fun runAllSyncs() {
        performFullSync()
    }

    fun likeSong(s: SongEntity) {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikeSong(s))
        }
    }

    fun syncLikedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedSongs)
        }
    }

    /**
     * Manual, user-initiated "mirror favorites from my account": makes the local liked songs match
     * the YouTube account EXACTLY — adds the ones on the account and removes the local likes that are
     * no longer on it. Unlike the automatic sync (which is purely additive on purpose), this can remove
     * local likes, so it is ONLY ever run when the user explicitly taps the button and confirms.
     */
    fun mirrorLikedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.MirrorLikedSongs)
        }
    }

    suspend fun mirrorLikedSongsSuspend() = executeMirrorLikedSongs()

    fun syncLibrarySongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LibrarySongs)
        }
    }

    fun syncUploadedSongs() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedSongs)
        }
    }

    fun syncLikedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
        }
    }

    fun syncUploadedAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncArtistsSubscriptions() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun syncSavedPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.SavedPlaylists)
        }
    }

    /**
     * HALLAZGO-061: the four library pulls enqueued together once a login is confirmed. Every
     * recovery path (login completion, the App cookie watcher, the cold-start check) goes through
     * this single entry point; the HALLAZGO-056 single-flight guard coalesces any overlap, so the
     * redundant enqueues from two paths firing close together never duplicate YouTube requests.
     */
    fun syncLibraryAfterLogin() {
        syncLikedSongs()
        syncLibrarySongs()
        syncArtistsSubscriptions()
        syncSavedPlaylists()
    }

    /**
     * HALLAZGO-061: epoch-millis stamp of the last COMPLETED liked-songs sync, 0 when none ever
     * finished. The cold-start recovery uses it to detect the stuck state left behind when the
     * login completion was cancelled before it could enqueue the library syncs.
     */
    suspend fun lastLikedSyncTimeMs(): Long =
        context.dataStore.data.first()[LastLikedSyncTimeKey] ?: 0L

    fun syncAutoSyncPlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.AutoSyncPlaylists)
        }
    }

    fun syncPlaylist(browseId: String, playlistId: String) {
        syncScope.launch {
            executeSyncPlaylist(browseId, playlistId)
        }
    }

    /**
     * Manual, user-initiated "Sincronizar ahora" for one YouTube-linked playlist. Runs the SAME guarded
     * single-playlist sync as the background path (an empty or materially-truncated remote page never
     * wipes the local copy), and — unlike the fire-and-forget [syncPlaylist] above — returns whether it
     * actually reached YouTube and applied, so the caller can show a "Listo" / "Falló" toast.
     * Returns false (never throws) when the user is not signed into YouTube Music, so a signed-out tap
     * can never crash. Callers should still gate on their own cookie check to show the "sign in" hint.
     */
    suspend fun syncPlaylistNow(browseId: String, playlistId: String): Boolean {
        if (!isLoggedIn()) return false
        // executeSyncPlaylist returns the nested Result from withRetry { YouTube.playlist(..).completed() }:
        // outer getOrNull() is null only if it threw, inner getOrNull() is null if the fetch failed after
        // retries, and isSuccess reflects the completed() page fetch itself.
        return runCatching { executeSyncPlaylist(browseId, playlistId) }
            .getOrNull()
            ?.getOrNull()
            ?.isSuccess == true
    }

    fun syncAllAlbums() {
        syncScope.launch {
            syncChannel.send(SyncOperation.LikedAlbums)
            syncChannel.send(SyncOperation.UploadedAlbums)
        }
    }

    fun syncAllArtists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ArtistsSubscriptions)
        }
    }

    fun cleanupDuplicatePlaylists() {
        syncScope.launch {
            syncChannel.send(SyncOperation.CleanupDuplicates)
        }
    }

    fun clearAllSyncedContent() {
        syncScope.launch {
            syncChannel.send(SyncOperation.ClearAllSynced)
        }
    }

    

    // HALLAZGO-056: the `*Suspend()` entry points bypass the serial channel (YtmSyncWorker,
    // account settings), so they share the same single-flight guard as the queued ops.
    suspend fun syncLikedSongsSuspend() = singleFlightOp(OP_LIKED_SONGS) { executeSyncLikedSongs() }
    suspend fun syncLibrarySongsSuspend() = singleFlightOp(OP_LIBRARY_SONGS) { executeSyncLibrarySongs() }
    suspend fun syncUploadedSongsSuspend() = singleFlightOp(OP_UPLOADED_SONGS) { executeSyncUploadedSongs() }
    suspend fun syncLikedAlbumsSuspend() = singleFlightOp(OP_LIKED_ALBUMS) { executeSyncLikedAlbums() }
    suspend fun syncUploadedAlbumsSuspend() = singleFlightOp(OP_UPLOADED_ALBUMS) { executeSyncUploadedAlbums() }
    suspend fun syncArtistsSubscriptionsSuspend() = singleFlightOp(OP_ARTISTS) { executeSyncArtistsSubscriptions() }
    suspend fun syncSavedPlaylistsSuspend() = singleFlightOp(OP_SAVED_PLAYLISTS) { executeSyncSavedPlaylists() }
    suspend fun syncAutoSyncPlaylistsSuspend() = singleFlightOp(OP_AUTO_SYNC_PLAYLISTS) { executeSyncAutoSyncPlaylists() }
    suspend fun cleanupDuplicatePlaylistsSuspend() = executeCleanupDuplicatePlaylists()
    suspend fun clearAllSyncedContentSuspend() = executeClearAllSyncedContent()

    suspend fun syncAllAlbumsSuspend() {
        syncLikedAlbumsSuspend()
        syncUploadedAlbumsSuspend()
    }

    suspend fun syncAllArtistsSuspend() {
        syncArtistsSubscriptionsSuspend()
    }

    

    private suspend fun executeFullSync(includeUpload: Boolean) = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping full sync - user not logged in")
            return@withContext
        }

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Starting full sync") }

        try {

            // HALLAZGO-056: every pull step runs single-flighted even INSIDE the composite, so an
            // overlapping down-only + upload full-sync pair can never fire the same browse twice.
            // isFullSync = true on purpose: these steps ARE the full sync, so they must not be
            // blocked by the "a full sync is running" rule — only by the same step running elsewhere.
            singleFlightOp(OP_LIKED_SONGS, isFullSync = true) { executeSyncLikedSongs() }

            singleFlightOp(OP_LIBRARY_SONGS, isFullSync = true) { executeSyncLibrarySongs() }

            // PLAYLISTS moved up — BEFORE the expensive album re-fetch + artist-image steps. They used to
            // run LAST, and on real-sized accounts the album/artist loops burned the whole ~10-min worker
            // budget → the worker was stopped and retried from the top → saved playlists were NEVER reached
            // ("no me pone nunca las playlists"). Running them early guarantees they complete.
            singleFlightOp(OP_SAVED_PLAYLISTS, isFullSync = true) { executeSyncSavedPlaylists() }

            // Repair rows created by the pre-0.6.137 bug: because the sync looked the playlist up through
            // a Library-filtered query, removing a synced playlist left an invisible row and the next sync
            // inserted a SECOND one for the same browseId. This collapses those pairs (keeping a saved,
            // fullest row and never collapsing onto a tombstone). Cheap: it only touches browseIds that
            // actually have more than one row. Until now this function had NO caller at all.
            executeCleanupDuplicatePlaylists()

            singleFlightOp(OP_AUTO_SYNC_PLAYLISTS, isFullSync = true) { executeSyncAutoSyncPlaylists() }

            singleFlightOp(OP_UPLOADED_SONGS, isFullSync = true) { executeSyncUploadedSongs() }

            singleFlightOp(OP_LIKED_ALBUMS, isFullSync = true) { executeSyncLikedAlbums() }

            singleFlightOp(OP_UPLOADED_ALBUMS, isFullSync = true) { executeSyncUploadedAlbums() }

            singleFlightOp(OP_ARTISTS, isFullSync = true) { executeSyncArtistsSubscriptions() }

            // UPLOAD last: every step above has just refreshed what the account already has, so the
            // uploader writes the smallest possible difference. It is bounded (see
            // LibraryUploadSync.MAX_REQUESTS_PER_RUN), no-ops when the user turned it off, and never
            // throws out of here — a failed upload must not mark the whole sync as failed.
            if (includeUpload) {
                runCatching { libraryUploadSync.runUploadPass() }
                    .onFailure {
                        if (it is CancellationException) throw it
                        Timber.w(it, "Library upload pass failed during full sync")
                    }
            }

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            // C3: stamp the REAL completion time (single source of truth for "last synced X ago"). Reached only
            // after a genuine full sync ran — we returned early above when not logged in — so the sync screen
            // shows a truthful timestamp instead of a placebo. Both the manual "sync all" and the scheduled
            // worker funnel through here.
            runCatching { context.dataStore.edit { it[YtmLastSyncKey] = System.currentTimeMillis() } }
            Timber.d("Full sync completed successfully")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error during full sync")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    private suspend fun executeLikeSong(s: SongEntity) = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping likeSong - user not logged in")
            return@withContext
        }

        withRetry {
            YouTube.likeVideo(s.id, s.liked)
        }.onFailure { e ->
            Timber.e(e, "Failed to like song on YouTube: ${s.id}")
        }


    }

    private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Syncing liked songs") }

        // HALLAZGO-028: stream each page into Room AS IT ARRIVES instead of downloading up to 50
        // pages before the first insert — after login, Home looked dead for minutes while this
        // pass fetched everything first. [insertLikedSongsPage] decides new/changed per page with
        // the same differential + batched-transaction logic as the old end-of-fetch pass; the
        // reconcile below needs the FULL remote list, so it still runs once after the last page.
        val now = LocalDateTime.now()
        withRetry {
            var streamedIndex = 0
            YouTube.playlist("LM").completedStreaming { songs ->
                insertLikedSongsPage(songs, now, streamedIndex)
                streamedIndex += songs.size
            }
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.songs
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.likedSongsByNameAsc().first()
                    val nowEpochMs = System.currentTimeMillis()
                    val lastLikedSyncMs = context.dataStore.data.first()[LastLikedSyncTimeKey] ?: 0L

                    localSongs.filterNot { it.id in remoteIds || it.song.isLocal }.forEach { song ->
                        try {
                            val likedEpochMs = AccountLibraryReconcile.toEpochMilli(song.song.likedDate)
                            val shouldPush = AccountLibraryReconcile.shouldPushLocalMissing(
                                likedAtEpochMs = likedEpochMs,
                                lastSuccessfulSyncEpochMs = lastLikedSyncMs,
                                nowEpochMs = nowEpochMs,
                            )
                            if (shouldPush) {
                                withRetry {
                                    YouTube.likeVideo(song.id, true)
                                }.onFailure { e ->
                                    Timber.e(e, "Failed to like song on YouTube: ${song.id}")
                                }
                            } else if (AccountLibraryReconcile.remoteListSafeToDropMissing(remoteSongs.size, localSongs.size)) {
                                // Reconcile: User deliberately unliked this song on YouTube; update local state
                                database.update(song.song.copy(liked = false))
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to update song: ${song.id}")
                        }
                    }

                    // C4 — DIFFERENTIAL + BATCHED: the new/changed pass now runs PER PAGE while
                    // the pages are still downloading (insertLikedSongsPage above); only this
                    // full-list reconcile is left for the end.

                    // NOTE: the removed bulk follow (`followArtistsWithContent`) used to run here and
                    // stamp every artist with content as followed — importing a liked song silently
                    // subscribed its artist everywhere. Artists only become followed through
                    // deliberate sources now; see the tombstone in DatabaseDao.kt (registry #154).
                    context.dataStore.edit { it[LastLikedSyncTimeKey] = nowEpochMs }
                    updateState { copy(likedSongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} liked songs")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing liked songs")
                    updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked songs from YouTube")
                updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked songs after retries")
            updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeMirrorLikedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping mirrorLikedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(likedSongs = SyncStatus.Syncing, currentOperation = "Mirroring liked songs from account") }

        withRetry {
            YouTube.playlist("LM").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.songs
                    val remoteIds = remoteSongs.map { it.id }.toSet()

                    // SAFETY GUARD: never wipe the local favorites if the account came back empty (a
                    // failed/anomalous fetch). The canonical "LM" liked playlist via .completed() is the
                    // reliable source for likes (unlike the capped library views), but an empty result is
                    // always treated as an error, not "you unliked everything".
                    if (remoteIds.isEmpty()) {
                        Timber.w("mirrorLikedSongs: remote returned empty; aborting to avoid wiping favorites")
                        updateState { copy(likedSongs = SyncStatus.Error("Empty remote — aborted to protect favorites")) }
                        return@onSuccess
                    }

                    // 1) Additive: make sure everything on the account is liked locally. Decided in memory
                    // against one pre-loaded snapshot (no N+1) and written in batched transactions.
                    val now = LocalDateTime.now()
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEachIndexed { index, song ->
                        try {
                            val dbSong = existing[song.id]
                            val timestamp = now.minusSeconds(index.toLong())
                            val isVideoSong = song.isVideoSong
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(
                                    meta.toSongEntity().copy(liked = true, likedDate = timestamp, isVideo = isVideoSong)
                                )
                            } else if (!dbSong.liked) {
                                updateEntities.add(dbSong.copy(liked = true, likedDate = timestamp, isVideo = isVideoSong))
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "mirrorLikedSongs: failed to add song ${song.id}")
                        }
                    }
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)

                    // 2) Mirror: remove local likes that are no longer on the account. Local files are left
                    // alone. Done locally only — the account is the source of truth, so there's nothing to
                    // push up (the song is already not liked on the account).
                    val localLiked = database.likedSongsByNameAsc().first()
                    val toUnlike = localLiked
                        .filterNot { it.id in remoteIds || it.song.isLocal }
                        .map { it.song.copy(liked = false, likedDate = null) }
                    batchUpdateSongs(toUnlike)

                    updateState { copy(likedSongs = SyncStatus.Completed) }
                    Timber.d("mirrorLikedSongs: mirrored ${remoteSongs.size} liked songs from account")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error mirroring liked songs")
                    updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked songs from YouTube (mirror)")
                updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to mirror liked songs after retries")
            updateState { copy(likedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncLibrarySongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLibrarySongs - user not logged in")
            return@withContext
        }

        updateState { copy(librarySongs = SyncStatus.Syncing, currentOperation = "Syncing library songs") }

        // HALLAZGO-028: stream each page into Room as it arrives (insertLibrarySongsPage) — the
        // old pass fetched up to 50 pages before the first insert. The push-up reconcile below
        // needs the FULL remote id set, so it still runs once after the last page.
        withRetry {
            YouTube.library("FEmusic_liked_videos").completedStreaming { items ->
                insertLibrarySongsPage(items)
            }
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.items.filterIsInstance<SongItem>()
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.songsByNameAsc().first()

                    // Additive only: NEVER drop a song from the local library just because it isn't in
                    // this remote "liked videos" page. That page is paginated/capped and routinely returns
                    // fewer items than the user actually has, and toggleLibrary() ALSO clears `liked` /
                    // `likedDate` — so reconciling against it silently un-liked thousands of favorites
                    // (4000 -> 1700). Push local-only library adds UP to the account instead, exactly like
                    // the liked-songs and liked-albums syncs already do.
                    localSongs.filterNot { it.id in remoteIds || it.song.isLocal }.forEach { song ->
                        try {
                            if (song.song.inLibrary != null) {
                                withRetry {
                                    YouTube.toggleSongLibrary(song.id, true)
                                }.onFailure { e ->
                                    Timber.e(e, "Failed to add song to YouTube library: ${song.id}")
                                }
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to push library song: ${song.id}")
                        }
                    }

                    // The new/changed pass now runs PER PAGE while pages are still downloading
                    // (insertLibrarySongsPage above).

                    updateState { copy(librarySongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} library songs")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing library songs")
                    updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch library songs from YouTube")
                updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync library songs after retries")
            updateState { copy(librarySongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedSongs() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncUploadedSongs - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedSongs = SyncStatus.Syncing, currentOperation = "Syncing uploaded songs") }

        withRetry {
            YouTube.library("FEmusic_library_privately_owned_tracks", tabIndex = 1).completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                    val remoteIds = remoteSongs.map { it.id }.toSet()
                    val localSongs = database.uploadedSongsByNameAsc().first()

                    // NEVER un-flag from an EMPTY remote page while we still hold local uploads. "You have
                    // no uploads" and "we could not read your uploads" are indistinguishable at this layer,
                    // and getting it wrong strips the uploaded flag off the user's ENTIRE library — with
                    // backups covering library data only, that is unrecoverable. Requiring a non-empty
                    // remote list makes an unreadable response a no-op instead of a wipe; a user who really
                    // did delete everything upstream just keeps stale flags until a page comes back with
                    // content, which is the harmless direction to be wrong in.
                    if (remoteSongs.isNotEmpty()) {
                        val songsToRemove = localSongs.filterNot { it.id in remoteIds }
                        batchUpdateSongs(songsToRemove.map { it.song.toggleUploaded() })
                    } else if (localSongs.isNotEmpty()) {
                        Timber.w("Uploads sync: remote page empty but ${localSongs.size} local uploads — skipping the un-flag pass")
                    }

                    // Decide new/changed in memory (no N+1) and write in batched transactions.
                    val existing = loadExistingSongs(remoteSongs.map { it.id })
                    val insertEntities = ArrayList<SongEntity>()
                    val insertMeta = ArrayList<MediaMetadata>()
                    val updateEntities = ArrayList<SongEntity>()
                    remoteSongs.forEach { song ->
                        try {
                            val dbSong = existing[song.id]
                            if (dbSong == null) {
                                val meta = song.toMediaMetadata()
                                insertMeta.add(meta)
                                insertEntities.add(meta.toSongEntity().toggleUploaded())
                            } else if (!dbSong.isUploaded) {
                                updateEntities.add(dbSong.toggleUploaded())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process song: ${song.id}")
                        }
                    }
                    batchInsertSongs(insertEntities, insertMeta)
                    batchUpdateSongs(updateEntities)

                    updateState { copy(uploadedSongs = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteSongs.size} uploaded songs (${insertEntities.size} new, ${updateEntities.size} updated)")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing uploaded songs")
                    updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                // Not an error path worth a stack trace: an account with nothing uploaded now comes
                // back as an empty page, so this only fires when the response shape was unusable.
                Timber.w("Could not read uploaded songs from YouTube: ${e.message}")
                updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.w("Failed to sync uploaded songs after retries: ${e.message}")
            updateState { copy(uploadedSongs = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncLikedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncLikedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(likedAlbums = SyncStatus.Syncing, currentOperation = "Syncing liked albums") }

        withRetry {
            YouTube.library("FEmusic_liked_albums").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsLikedByNameAsc().first()

                    // Additive only: do NOT un-favorite local albums that aren't in the remote account.
                    // We don't push app-favorites to the YouTube account, so removing them here made the
                    // user's favorite albums "disappear" on their own a few minutes after adding them.

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            if (dbAlbum == null) {
                                // NEW album only: fetch the full page + insert. The old code called
                                // YouTube.album() for EVERY album every run (the dbAlbum==null check gated
                                // only the write, not the network) → hundreds of round-trips that burned the
                                // worker budget and starved later steps (notably playlists). Now only the
                                // missing albums hit the network.
                                YouTube.album(album.browseId).onSuccess { albumPage ->
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.localToggleLike())
                                    }
                                }
                            } else if (dbAlbum.album.bookmarkedAt == null) {
                                // Already in the DB — just mark it favorited locally, no network call.
                                database.update(dbAlbum.album.localToggleLike())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process album: ${album.id}")
                        }
                    }

                    // NOTE: the removed bulk follow (`followArtistsWithContent`) used to run here and
                    // subscribe every album artist just because an album was liked/imported. That is
                    // not user intent; follows only come from deliberate sources now (registry #154).
                    updateState { copy(likedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} liked albums")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing liked albums")
                    updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch liked albums from YouTube")
                updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync liked albums after retries")
            updateState { copy(likedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncUploadedAlbums() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncUploadedAlbums - user not logged in")
            return@withContext
        }

        updateState { copy(uploadedAlbums = SyncStatus.Syncing, currentOperation = "Syncing uploaded albums") }

        withRetry {
            YouTube.library("FEmusic_library_privately_owned_releases").completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                    val remoteIds = remoteAlbums.map { it.id }.toSet()
                    val localAlbums = database.albumsUploadedByNameAsc().first()

                    // Same guard as the uploaded-songs pass: an empty remote page must never un-flag a
                    // non-empty local set — an unreadable response would otherwise wipe the whole library.
                    if (remoteAlbums.isEmpty() && localAlbums.isNotEmpty()) {
                        Timber.w("Uploads sync: remote albums empty but ${localAlbums.size} local — skipping the un-flag pass")
                    }
                    (if (remoteAlbums.isEmpty()) emptyList() else localAlbums.filterNot { it.id in remoteIds }).forEach { album ->
                        try {
                            database.update(album.album.toggleUploaded())
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to update album: ${album.id}")
                        }
                    }

                    remoteAlbums.forEach { album ->
                        try {
                            val dbAlbum = database.album(album.id).firstOrNull()
                            if (dbAlbum == null) {
                                // Only NEW albums hit the network (was: every album, every run → starvation).
                                YouTube.album(album.browseId).onSuccess { albumPage ->
                                    database.insert(albumPage)
                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                        database.update(newDbAlbum.album.toggleUploaded())
                                    }
                                }.onFailure { reportException(it) }
                            } else if (!dbAlbum.album.isUploaded) {
                                database.update(dbAlbum.album.toggleUploaded())
                            }
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to process album: ${album.id}")
                        }
                    }

                    updateState { copy(uploadedAlbums = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteAlbums.size} uploaded albums")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing uploaded albums")
                    updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.w("Could not read uploaded albums from YouTube: ${e.message}")
                updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.w("Failed to sync uploaded albums after retries: ${e.message}")
            updateState { copy(uploadedAlbums = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncArtistsSubscriptions() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
            return@withContext
        }

        updateState { copy(artists = SyncStatus.Syncing, currentOperation = "Syncing artist subscriptions") }

        // HALLAZGO-028: stream each page into Room as it arrives (insertArtistsPage) — the old
        // pass fetched every page, resolved ALL missing channelIds, and only then wrote the first
        // row. The markArtistsSubscribedOnYtm stamp below needs the FULL remote id set, so it
        // still runs once after the last page.
        val now = LocalDateTime.now()
        withRetry {
            YouTube.library("FEmusic_library_corpus_artists").completedStreaming { items ->
                insertArtistsPage(items, now)
            }
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remoteArtists = page.items.filterIsInstance<ArtistItem>()
                    val remoteIds = remoteArtists.map { it.id }.toSet()

                    // REMOVED — this pushed local follows UP as real YouTube subscriptions, and it was
                    // subscribing the user to artists they never chose.
                    //
                    // The intent was narrow (the handful of artists picked during onboarding), but the
                    // source it read is not: `artistsBookmarkedByNameAsc()` returns every artist with a
                    // bookmarkedAt, and `followArtistsWithContent()` sets that flag on EVERY artist that
                    // has so much as one song or album in the library — it runs after a liked-songs sync,
                    // after a Spotify import, after a library migration. So the chain was:
                    //   one song lands in the library -> its artist is auto-"followed" locally
                    //   -> the next sync SUBSCRIBES that artist on the user's real YouTube account.
                    // The owner reported exactly that: "me aparecen muchas suscripciones de cantantes que
                    // no sigo". Writing to someone's account is not a sync detail — it needs an explicit
                    // action, and there is no UI that asks for one.
                    //
                    // Syncing DOWN is untouched below: real YouTube subscriptions still populate "tus
                    // artistas". Local-only follows simply stay local, which is the safe direction.
                    // A deliberate "subscribe on YouTube" action can be added later as an explicit button.

                    // The new/changed pass (channelId lookups included) now runs PER PAGE while
                    // pages are still downloading (insertArtistsPage above).

                    // BACKFILL for the v39->v40 follow markers, and the thing that keeps the upload
                    // idempotent. These ids come from FEmusic_library_corpus_artists — the account's
                    // REAL channel subscriptions — so each one is, by definition, a follow the user
                    // made: stamp it followedByUserAt (if it had none) AND ytmSyncedAt. Consequences:
                    // LibraryUploadSync never re-subscribes them, and unfollowing one in Aura now
                    // correctly unsubscribes it upstream. Artists that only carry an incidental
                    // `bookmarkedAt` (legacy bulk-follow leftovers, registry #154) are NOT in this
                    // list and stay untouched — that is the whole point of the split
                    // ("suscripciones de cantantes que no sigo").
                    //
                    // This page can be PARTIAL (a paginated read that timed out returns fewer ids).
                    // That is now harmless: the statement only ever stamps rows towards "in sync", and
                    // an id that never arrives simply keeps its previous state. Nothing here can put a
                    // row into the pending-UNSUBSCRIBE state.
                    if (remoteIds.isNotEmpty()) {
                        runCatching {
                            remoteIds.toList().chunked(SQL_IN_CHUNK).forEach { chunk ->
                                database.markArtistsSubscribedOnYtm(chunk, now)
                            }
                        }.onFailure { Timber.w(it, "Could not stamp subscribed artists") }
                    }

                    // NOTE: the removed bulk follow (`followArtistsWithContent`) used to run here and
                    // subscribe every artist with ANY content in the library — playing or liking a song
                    // was enough to make its artist appear under "tus artistas". Real channel
                    // subscriptions still show: `markArtistsSubscribedOnYtm` above stamps
                    // `followedByUserAt` for the ids read back from the account (registry #154).
                    updateState { copy(artists = SyncStatus.Completed) }
                    Timber.d("Synced ${remoteArtists.size} artist subscriptions")
                    // Fill in missing artist cover photos (e.g. artists that came in only via songs) —
                    // bounded + throttled so it doesn't hammer the network/API.
                    runCatching { fillMissingArtistImages() }
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing artist subscriptions")
                    updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to fetch artist subscriptions from YouTube")
                updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync artist subscriptions after retries")
            updateState { copy(artists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    /**
     * Fill missing artist cover photos. Many followed artists (especially those that came in only via
     * synced songs) have no image, so "your artists" shows blanks. Fetch each one's picture from its
     * artist page and store it. BOUNDED (max [MAX_ARTIST_IMAGE_FETCH] per run) and throttled so it never
     * hammers the network/API; re-running the sync fills more. Runs inside the background sync worker.
     */
    private suspend fun fillMissingArtistImages() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) return@withContext
        val missing = runCatching { database.bookmarkedArtistsMissingImage(MAX_ARTIST_IMAGE_FETCH) }
            .getOrNull().orEmpty()
        if (missing.isEmpty()) return@withContext
        // Was a plain serial `for` loop — one full artist-page fetch, THEN a 150ms sleep, THEN the
        // next artist — so an account with many followed-but-imageless artists (common: artists that
        // only entered the library via a synced song, per the class doc above) could sit blank for a
        // long stretch. Same bounded-concurrency treatment as the channelId backfill above, and for
        // the same reason: full unbounded parallelism risks looking like automated traffic to
        // YouTube's bot detection, so this stays a small fixed-size batch rather than one at a time OR
        // all at once.
        var filled = 0
        coroutineScope {
            val fetchSemaphore = Semaphore(ARTIST_NETWORK_LOOKUP_CONCURRENCY)
            missing.filterNot { it.isLocal || it.id.isBlank() }
                .map { a ->
                    async {
                        fetchSemaphore.withPermit {
                            try {
                                val thumb = YouTube.artist(a.id).getOrNull()?.artist?.thumbnail
                                if (!thumb.isNullOrBlank() && thumb != a.thumbnailUrl) {
                                    database.update(a.copy(thumbnailUrl = thumb))
                                    true
                                } else false
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.e(e, "Failed to fetch artist image: ${a.id}")
                                false
                            }
                        }
                    }
                }
                .awaitAll()
                .let { results -> filled = results.count { it } }
        }
        Timber.d("Filled $filled missing artist images (of ${missing.size} checked)")
    }

    private suspend fun executeSyncSavedPlaylists() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncSavedPlaylists - user not logged in")
            return@withContext
        }

        updateState { copy(playlists = SyncStatus.Syncing, currentOperation = "Syncing saved playlists") }

        // HALLAZGO-028: stream each page as it arrives — every playlist row is created/refreshed
        // and its own content sync starts while the rest of the playlist list is still
        // downloading (syncSavedPlaylistsPage).
        // Additive only: don't un-bookmark local saved playlists that are missing from this
        // remote page (it can be incomplete). Same reasoning as the liked songs/albums syncs.
        // (The old whole-library read that used to live here is gone: rows are now looked up
        // per browseId inside the loop, which is also what makes a removed playlist stay
        // removed. Keeping the unused query cost a COUNT subquery + thumbnail relation over
        // EVERY playlist on every sync.)
        val suppressedIds = runCatching {
            context.dataStore.get(SuppressedPlaylistIdsKey, "").split(',').filter { it.isNotBlank() }.toSet()
        }.getOrDefault(emptySet())

        withRetry {
            YouTube.library("FEmusic_liked_playlists").completedStreaming { items ->
                syncSavedPlaylistsPage(items, suppressedIds)
            }
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val remotePlaylists = page.items.filterIsInstance<PlaylistItem>()
                        .filterNot { it.id == "LM" || it.id == "SE" }

                    updateState { copy(playlists = SyncStatus.Completed) }
                    Timber.d("Synced ${remotePlaylists.size} saved playlists")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing saved playlists")
                    updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
                }
            }.onFailure { e ->
                Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
            }
        }.onFailure { e ->
            Timber.e(e, "Failed to sync saved playlists after retries")
            updateState { copy(playlists = SyncStatus.Error(e.message ?: "Unknown error")) }
        }
    }

    private suspend fun executeSyncAutoSyncPlaylists() = withContext(Dispatchers.IO) {
        if (!isLoggedIn()) {
            Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
            return@withContext
        }

        try {
            val autoSyncPlaylists = database.playlistsByNameAsc().first()
                .filter { it.playlist.isAutoSync && it.playlist.browseId != null }

            Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

            autoSyncPlaylists.forEach { playlist ->
                try {
                    executeSyncPlaylist(playlist.playlist.browseId!!, playlist.playlist.id)
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                }
            }
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error syncing auto-sync playlists")
        }
    }

    private suspend fun executeSyncPlaylist(browseId: String, playlistId: String) = withContext(Dispatchers.IO) {
        // Auto / scheduled sync never calls executeClearAllSyncedContent — only explicit logout reset does.
        Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

        withRetry {
            YouTube.playlist(browseId).completed()
        }.onSuccess { result ->
            result.onSuccess { page ->
                try {
                    val songs = page.songs.map(SongItem::toMediaMetadata)
                    Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

                    if (songs.isEmpty()) {
                        // Do NOT clear the local playlist here. A successful-but-empty response is almost
                        // always a transient/anomalous fetch, and wiping the local copy permanently loses
                        // the user's songs. Leave the local playlist untouched.
                        Timber.w("syncPlaylist: Remote playlist returned empty; leaving local playlist intact")
                        return@onSuccess
                    }

                    val remoteIds = songs.map { it.id }
                    val localIds = database.playlistSongs(playlistId).first()
                        .sortedBy { it.map.position }
                        .map { it.song.id }

                    if (remoteIds == localIds) {
                        Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                        return@onSuccess
                    }

                    // Guard against a truncated/partial remote page wiping the playlist: if the remote came back
                    // MATERIALLY smaller than the local copy, treat it as a transient bad fetch and skip the
                    // destructive clear-and-replace (same spirit as the empty-response guard above). Real
                    // shrinks of <50% still sync on a later run when the full page returns.
                    if (localIds.isNotEmpty() && remoteIds.size < localIds.size / 2) {
                        Timber.w("syncPlaylist: remote (${remoteIds.size}) << local (${localIds.size}); skipping to avoid truncating the playlist")
                        return@onSuccess
                    }

                    Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

                    val libraryNow = LocalDateTime.now()
                    // Load the songs that already exist ONCE (no per-row `song(id)` + `getSongByIdBlocking`
                    // reads inside the transaction). This whole playlist rebuild is already one transaction,
                    // so Room still emits a single time — the win here is dropping the N+1 reads.
                    val existing = loadExistingSongs(remoteIds)
                    database.withTransaction {
                        database.clearPlaylist(playlistId)
                        songs.forEachIndexed { idx, song ->
                            val dbSong = existing[song.id]
                            if (dbSong == null) {
                                // New song → insert and drop it into the library so it surfaces in
                                // Library → Songs (WHERE inLibrary IS NOT NULL).
                                database.insert(song) { it.copy(inLibrary = libraryNow) }
                            } else if (dbSong.inLibrary == null) {
                                // Set inLibrary on existing songs that don't have it yet.
                                database.update(dbSong.copy(inLibrary = libraryNow))
                            }
                            database.insert(
                                PlaylistSongMap(
                                    songId = song.id,
                                    playlistId = playlistId,
                                    position = idx,
                                    setVideoId = song.setVideoId
                                )
                            )
                        }
                    }
                    Timber.d("syncPlaylist: Successfully synced playlist")
                } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                    Timber.e(e, "Error processing playlist sync")
                }
            }.onFailure { e ->
                Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
            }
        }.onFailure { e ->
            Timber.e(e, "syncPlaylist: Failed after retries")
        }
    }

    private suspend fun executeCleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
        try {
            // Read EVERY row per browseId, not the Library-filtered list: the duplicates this repairs
            // were created precisely because an un-bookmarked row was invisible to that query, so the
            // old version of this cleanup could not see the pair it was meant to merge.
            val bookmarked = database.playlistsByNameAsc().first()
            val browseIds = bookmarked.mapNotNull { it.playlist.browseId }.distinct()
            val browseIdGroups = browseIds.associateWith { database.playlistsByBrowseIdBlocking(it) }

            for ((browseId, playlists) in browseIdGroups) {
                if (playlists.size > 1) {
                    Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                    // Keep the row the REST OF THE APP resolves — same order as playlistByBrowseId
                    // (saved first, then oldest). Picking by song count instead would delete the row the
                    // user actually sees, and its id is referenced elsewhere as "PL:<id>" (Speed Dial
                    // pins, widget targets, the enhanced-shuffle memory), leaving those dangling.
                    // The survivor's songs are refilled by the sync; a tombstone's are expendable.
                    // EXACTLY what playlistByBrowseId resolves: bookmarked first, then lowest rowId.
                    // playlistsByBrowseIdBlocking is already ORDER BY rowId, so "first bookmarked, else
                    // first" reproduces it. Tie-breaking on the TEXT id instead would disagree with rowId
                    // about half the time and delete the row the app actually shows.
                    val toKeep = playlists.firstOrNull { it.playlist.bookmarkedAt != null }
                        ?: playlists.first()

                    playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                        try {
                            Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                            // clearPlaylist is redundant (playlist_song_map has onDelete = CASCADE) but
                            // harmless; the delete is what matters and it is a single statement, so no
                            // half-state is possible here.
                            database.delete(duplicate.playlist)
                        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
                            Timber.e(e, "Failed to remove duplicate playlist: ${duplicate.id}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "Error cleaning up duplicate playlists")
        }
    }

    private suspend fun executeClearAllSyncedContent() = withContext(Dispatchers.IO) {
        Timber.d("clearAllSyncedContent: Starting cleanup")

        updateState { copy(overallStatus = SyncStatus.Syncing, currentOperation = "Clearing synced content") }

        try {
            database.withTransaction {
                // Bulk SQL UPDATEs — no longer load the entire library into memory and update row-by-row
                // (that was an OOM/multi-second-ANR risk on a 15-20k-song restored library).
                database.clearAllLikedSongs()
                database.clearAllLibrarySongs()
                database.clearAllLikedAlbums()
                database.clearAllBookmarkedArtists()
                database.clearAllUploadedSongs()
                database.clearAllUploadedAlbums()

                // Playlists are few; clear each synced playlist's song-map then delete the playlist row.
                // Includes TOMBSTONES (rows kept with bookmarkedAt = null so the sync won't resurrect a
                // playlist the user removed): a Library-filtered read would leave them behind, holding
                // their whole song map forever AND making the sync skip those playlists for good — after
                // a full reset the account's playlists must be able to come back.
                // Reads EVERY row with a browseId, tombstones included. Seeding this from a Library query
                // could not see a browseId whose only row is a tombstone — the normal state after the
                // user removes a synced playlist — so those rows survived the reset with their whole
                // song map AND kept the sync skipping those playlists for good.
                database.playlistsWithBrowseIdBlocking().forEach {
                    database.delete(it.playlist)  // playlist_song_map cascades
                }
            }

            
            context.dataStore.edit { settings ->
                settings[LastFullSyncKey] = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            }

            updateState { copy(overallStatus = SyncStatus.Completed, currentOperation = "") }
            Timber.d("clearAllSyncedContent: Cleanup completed successfully")
        } catch (e: Exception) {
                            // Never swallow coroutine cancellation: doing so let the sync loop keep
                            // running after its job was cancelled, blasting through every song and
                            // flooding logs / pegging the CPU (made playback fail right after a restore).
                            if (e is CancellationException) throw e
            Timber.e(e, "clearAllSyncedContent: Error during cleanup")
            updateState { copy(overallStatus = SyncStatus.Error(e.message ?: "Unknown error"), currentOperation = "") }
        }
    }

    fun cancelAllSyncs() {
        processingJob?.cancel()
        startProcessingQueue()
        updateState { SyncState() }
    }
}
