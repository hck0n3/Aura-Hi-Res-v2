package iad1tya.echo.music.cloud

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LocalArtistNameExceptionsKey
import iad1tya.echo.music.constants.LocalArtistSeparatorsKey
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.AlbumArtistMap
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongAlbumMap
import iad1tya.echo.music.db.entities.SongArtistMap
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.extensions.isInternetConnected
import iad1tya.echo.music.localmedia.LocalArtistSplitRules
import iad1tya.echo.music.localmedia.matchMovedLocalSongs
import iad1tya.echo.music.utils.dataStore
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Registry row 891 — indexes the folders the user chose in each connected cloud into the library.
 *
 * WHEN: only when the user asks («Actualizar» on the cloud page), right after choosing folders, and at most once per app
 * start when the last index is older than [CloudIndexPolicy.STALE_AFTER_MS] — and that automatic one only on an
 * unmetered network (the automatic-downloads rule, HALLAZGO-037). Never on a timer, never while nobody asked.
 *
 * HOW: a bounded, cancellable walk ([CloudFolderWalk]); a file whose size and date did not change keeps the tags read
 * last time, the others are read through the serve with Range requests ([CloudTagReader]), at most
 * [CloudIndexPolicy.MAX_TAG_READS_PER_RUN] per run (the rest keep their file name until the next run).
 *
 * DATA SAFETY (rows 694, 881-882): a cloud that is disconnected or cannot be listed changes nothing; a folder that could
 * not be listed completely keeps every song it had; a file moved or renamed in the cloud (same size, title, album and
 * duration, one-to-one) takes its likes, playlist entries, history and play counts to its new id; a song that went away
 * is deleted only when nothing of the user's hangs on it (no like, no play time, no playlist, no history, no download).
 *
 * Logs: counts only (rule 4) — never a path, a file name, a title or an artist.
 */
@Singleton
class CloudLibraryIndexer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    enum class Trigger { USER, FOLDERS_CHOSEN, APP_START }

    data class Summary(
        val songs: Int,
        val tagsRead: Int,
        val tagsPending: Int,
        val removed: Int,
        val kept: Int,
        val moved: Int,
        val complete: Boolean,
    )

    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Volatile
    private var autoChecked = false

    /** Once per app process: indexes when something is chosen, the index is stale and the network is unmetered. */
    suspend fun indexIfStale() {
        if (autoChecked) return
        autoChecked = true
        val state = CloudLibraryStore.read(context)
        if (state.folders.values.all { it.isEmpty() }) return
        if (!CloudIndexPolicy.isStale(state.lastIndexAtMs, System.currentTimeMillis())) return
        if (RcloneBinary.path(context) == null || !context.isInternetConnected()) return
        val metered = runCatching { context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered }.getOrNull()
        if (metered != false) return
        try {
            index(Trigger.APP_START)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w("cloud: automatic index not applied (%s)", e.javaClass.simpleName)
        }
    }

    suspend fun index(trigger: Trigger): Summary = mutex.withLock {
        _running.value = true
        try {
            withContext(Dispatchers.IO) { run(trigger) }
        } finally {
            _running.value = false
        }
    }

    private suspend fun run(trigger: Trigger): Summary {
        val ctx = currentCoroutineContext()
        val state = CloudLibraryStore.read(context)
        var records = state.files
        val seenAll = ArrayList<CloudFileRecord>()
        // Clouds listed completely this run (or with nothing chosen any more): only their vanished songs may go.
        val settled = HashSet<String>()
        var tagReads = 0
        var pending = 0
        var complete = true
        RcloneDaemon.use(context) { client ->
            val configured = RcResponses.remotes(client.call(RcRequests.listRemotes())).toSet()
            val remotes = (state.folders.keys + records.map { it.remote }).distinct()
            for (remote in remotes) {
                // A disconnected cloud is never touched: its songs, likes and lists wait for it to be connected again.
                if (remote !in configured) {
                    if (state.folders[remote].orEmpty().isNotEmpty()) complete = false
                    continue
                }
                val chosen = CloudIndexPolicy.outermost(state.folders[remote].orEmpty())
                val walk = CloudFolderWalk.walk(
                    chosen = chosen,
                    list = { dir -> RcResponses.entries(client.call(RcRequests.list(remote, dir))) },
                    checkActive = { ctx.ensureActive() },
                )
                if (walk.completeFolders.size < chosen.size) complete = false else settled += remote
                val previousById = records.associateBy { it.id }
                var serve: CloudStreams.Serve? = null
                // New and changed files first, so the bounded reads go where a title is missing.
                val ordered = walk.files.sortedBy { entry -> if (previousById[CloudSongIds.of(remote, entry.path)] == null) 0 else 1 }
                val seen = ordered.map { entry ->
                    val id = CloudSongIds.of(remote, entry.path)
                    val previous = previousById[id]
                    val base = CloudFileRecord(id, remote, entry.path, entry.size, entry.modTimeMs, previous?.tags, previous?.tagsFor)
                    when {
                        !CloudIndexPolicy.needsTagRead(previous, entry.size, entry.modTimeMs) -> base
                        tagReads >= CloudIndexPolicy.MAX_TAG_READS_PER_RUN -> base.also { pending++ }
                        else -> {
                            ctx.ensureActive()
                            tagReads++
                            val live = serve ?: CloudStreams.ensureServe(client, remote).also { serve = it }
                            val tags = CloudTagReader.read(CloudStreams.url(live, entry.path), live.authorization)
                            if (tags == null) {
                                pending++
                                base
                            } else {
                                base.copy(tags = tags, tagsFor = CloudIndexPolicy.fingerprint(entry.size, entry.modTimeMs))
                            }
                        }
                    }
                }
                records = CloudIndexPolicy.merge(records, remote, chosen, walk.completeFolders, seen).records
                seenAll += seen
            }
        }
        ctx.ensureActive()
        val outcome = apply(seenAll, records, state.files, settled, mayDelete = trigger != Trigger.APP_START)
        CloudLibraryStore.update(context) { it.copy(files = records, lastIndexAtMs = System.currentTimeMillis()) }
        val summary = Summary(
            songs = records.size,
            tagsRead = tagReads,
            tagsPending = pending,
            removed = outcome.removed,
            kept = outcome.kept,
            moved = outcome.moved,
            complete = complete,
        )
        Timber.tag(TAG).i(
            "cloud: index %s songs=%d read=%d pending=%d removed=%d kept=%d moved=%d complete=%b",
            trigger.name, summary.songs, summary.tagsRead, summary.tagsPending, summary.removed, summary.kept, summary.moved,
            summary.complete,
        )
        return summary
    }

    private data class Outcome(val removed: Int, val kept: Int, val moved: Int)

    /**
     * One transaction. [seen] = every file listed this run; only those new to the database or changed since [previous]
     * are written. [all] = the whole index after this run (album counts and order come from it). A cloud song row whose
     * file is not in the index any more (an ORPHAN) is looked at only for the [settled] clouds: moved to its new file when
     * the match is one-to-one (row 882), deleted when nothing of the user's hangs on it and [mayDelete] (a run nobody
     * asked for never deletes, row 881), otherwise kept.
     */
    private suspend fun apply(
        seen: List<CloudFileRecord>,
        all: List<CloudFileRecord>,
        previous: List<CloudFileRecord>,
        settled: Set<String>,
        mayDelete: Boolean,
    ): Outcome {
        val prefs = context.dataStore.data.first()
        val rules = LocalArtistSplitRules.fromPreferences(prefs[LocalArtistSeparatorsKey], prefs[LocalArtistNameExceptionsKey])
        val unknownArtist = context.getString(R.string.unknown_artist)
        val allTracks = all.map { CloudLibraryTracks.track(it, rules, unknownArtist) }
        val trackById = allTracks.associateBy(CloudTrack::id)
        val albumIndexes = CloudLibraryTracks.albumIndexes(allTracks)
        val albums = CloudLibraryTracks.albums(allTracks).associateBy(CloudAlbum::id)
        val previousById = previous.associateBy(CloudFileRecord::id)
        val seenIds = seen.map(CloudFileRecord::id)
        val existing = loadSongs(seenIds)
        val changedIds = seen.filter { it.id !in existing || previousById[it.id] != it }.mapTo(LinkedHashSet(), CloudFileRecord::id)
        val orphanIds = database.localSongIds().filter { id ->
            id.isCloudSongId() && id !in trackById && CloudSongIds.remoteOf(id) in settled
        }
        val orphans = loadSongs(orphanIds)
        var removed = 0
        var kept = 0
        var moved = 0
        database.withTransaction {
            // Row 882: an orphan whose file reappeared under another path (same size, title, album, duration; one-to-one)
            // hands its likes, play time, playlist entries, history, play counts and lyrics to the new row. A row that
            // already exists only receives when nothing of the user's hangs on it yet.
            val missing = orphans.values.map { old ->
                iad1tya.echo.music.localmedia.LocalSongIdentity(
                    id = old.song.id,
                    isVideo = false,
                    sizeBytes = old.format?.contentLength ?: 0L,
                    durationSeconds = old.song.duration,
                    title = old.song.title,
                    albumName = old.song.albumName,
                )
            }
            val appeared = seenIds.mapNotNull(trackById::get).map(CloudLibraryTracks::identity)
            val candidates = iad1tya.echo.music.localmedia.movedSongCandidateIds(missing, appeared)
            val takenRows = loadSongs(candidates.filter { it in existing })
            val unusedTaken = unusedIds(takenRows.values)
            val verdict = matchMovedLocalSongs(missing, appeared) { id -> id !in takenRows || id in unusedTaken }
            verdict.moves.forEach { (oldId, newId) ->
                val old = orphans.getValue(oldId).song
                val receiving = takenRows[newId]?.song
                if (receiving == null) {
                    insert(old.copy(id = newId))
                } else {
                    update(
                        receiving.copy(
                            liked = old.liked,
                            likedDate = old.likedDate,
                            totalPlayTime = old.totalPlayTime,
                            date = old.date ?: receiving.date,
                            lyricsOffset = old.lyricsOffset,
                            romanizeLyrics = old.romanizeLyrics,
                        ),
                    )
                }
                moveLocalSongPlaylistEntries(oldId, newId)
                moveLocalSongEvents(oldId, newId)
                moveLocalSongPlayCounts(oldId, newId)
                moveLocalSongLyrics(oldId, newId)
                changedIds += newId
            }
            moved = verdict.moves.size
            val unmoved = orphans.values.filterNot { it.song.id in verdict.moves }
            val deletable = if (mayDelete) {
                val unused = unusedIds(unmoved)
                unmoved.map { it.song.id }.filter { it in unused && it !in verdict.ambiguous }
            } else {
                emptyList()
            }
            kept = unmoved.size - deletable.size
            removed = deletable.size
            (verdict.moves.keys + deletable).chunked(SQL_BATCH).forEach(::deleteSongsByIds)

            val writeTracks = changedIds.mapNotNull(trackById::get)
            val artistRefs = writeTracks.flatMap { it.artists + it.albumArtists }.distinctBy(CloudArtistRef::id)
            val existingArtists = artistRefs.map(CloudArtistRef::id).chunked(SQL_BATCH)
                .flatMap { artistEntitiesByIdsInTransaction(it) }.associateBy(ArtistEntity::id)
            artistRefs.forEach { ref ->
                val old = existingArtists[ref.id]
                if (old == null) {
                    insert(ArtistEntity(id = ref.id, name = ref.name, isLocal = true))
                } else if (old.isLocal && old.name != ref.name) {
                    update(old.copy(name = ref.name))
                }
            }
            val writeAlbums = writeTracks.mapNotNullTo(LinkedHashSet()) { it.albumId }.mapNotNull(albums::get)
            val existingAlbums = writeAlbums.map(CloudAlbum::id).chunked(SQL_BATCH)
                .flatMap { getAlbumEntitiesByIds(it) }.associateBy(AlbumEntity::id)
            writeAlbums.forEach { album ->
                val old = existingAlbums[album.id]
                if (old == null) {
                    insert(
                        AlbumEntity(
                            id = album.id,
                            title = album.title,
                            year = album.year,
                            songCount = album.songCount,
                            duration = album.durationSeconds,
                            isLocal = true,
                        ),
                    )
                } else if (old.isLocal) {
                    // The phone's scanner may share this album (same id): neither side shrinks the other's count.
                    update(
                        old.copy(
                            title = album.title,
                            year = album.year ?: old.year,
                            songCount = maxOf(old.songCount, album.songCount),
                            duration = maxOf(old.duration, album.durationSeconds),
                        ),
                    )
                }
                album.artistIds.forEachIndexed { index, artistId -> insert(AlbumArtistMap(albumId = album.id, artistId = artistId, order = index)) }
            }
            writeTracks.forEach { track ->
                val modified = track.modTimeMs?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
                val old = existing[track.id]?.song ?: getSongByIdBlocking(track.id)?.song
                if (old != null) {
                    update(
                        old.copy(
                            title = track.title,
                            duration = track.durationSeconds,
                            albumId = track.albumId,
                            albumName = track.albumName,
                            year = track.year ?: old.year,
                            dateModified = modified ?: old.dateModified,
                            isLocal = true,
                        ),
                    )
                } else {
                    insert(
                        SongEntity(
                            id = track.id,
                            title = track.title,
                            duration = track.durationSeconds,
                            albumId = track.albumId,
                            albumName = track.albumName,
                            year = track.year,
                            dateModified = modified,
                            isLocal = true,
                        ),
                    )
                }
                upsert(
                    FormatEntity(
                        id = track.id,
                        itag = -1,
                        mimeType = track.mimeType,
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = track.sizeBytes,
                        loudnessDb = null,
                        measuredLoudnessDb = measuredLoudnessDbForId(track.id),
                        playbackUrl = null,
                    ),
                )
                deleteSongArtistMaps(track.id)
                track.artists.forEachIndexed { index, artist -> insert(SongArtistMap(songId = track.id, artistId = artist.id, position = index)) }
                deleteSongAlbumMaps(track.id)
                track.albumId?.let { albumId ->
                    insert(SongAlbumMap(songId = track.id, albumId = albumId, index = albumIndexes[track.id] ?: 0))
                }
            }
            if (verdict.moves.isNotEmpty() || deletable.isNotEmpty() || writeTracks.isNotEmpty()) {
                pruneLocalAlbums()
                pruneLocalArtists()
                pruneFormats()
                prunePlayCounts()
            }
        }
        return Outcome(removed = removed, kept = kept, moved = moved)
    }

    /** Rows 881-882: the gone songs nothing of the user's hangs on (no like, play time, download, playlist, history, count). */
    private fun MusicDatabase.unusedIds(rows: Collection<Song>): Set<String> {
        val candidates = rows.filter {
            !it.song.liked && it.song.likedDate == null && it.song.totalPlayTime == 0L && !it.song.isDownloaded && it.song.dateDownload == null
        }.map { it.song.id }
        if (candidates.isEmpty()) return emptySet()
        val used = candidates.chunked(SQL_BATCH).flatMapTo(HashSet()) { chunk ->
            localSongIdsInPlaylists(chunk) + localSongIdsWithEvents(chunk) + localSongIdsWithPlayCounts(chunk)
        }
        return candidates.filterNotTo(HashSet(), used::contains)
    }

    private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SQL_BATCH).flatMap { database.getSongsByIds(it) }.associateBy { it.song.id }

    private companion object {
        const val TAG = "Cloud"
        const val SQL_BATCH = 900
    }
}
