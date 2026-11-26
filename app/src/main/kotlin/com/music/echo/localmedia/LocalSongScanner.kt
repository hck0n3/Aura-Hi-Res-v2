/*
 * ArchiveTune (2026)
 * © Chartreux Westia — github.com/koiverse
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package iad1tya.echo.music.localmedia

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.antonkarpenko.ffmpegkit.FFprobeKit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import iad1tya.echo.music.R
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.AlbumArtistMap
import iad1tya.echo.music.db.entities.AlbumEntity
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.db.entities.FormatEntity
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongAlbumMap
import iad1tya.echo.music.db.entities.SongArtistMap
import iad1tya.echo.music.db.entities.SongEntity
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.constants.ExportedEntriesCap
import iad1tya.echo.music.constants.ExportedFileUrisKey
import iad1tya.echo.music.constants.ExportedVideoIdsKey
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.parseExportedFileUriMap
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 0,
    val excludedFolders: Set<String> = emptySet(),
    /**
     * When non-empty, ONLY these folders (and their subfolders) are imported. Empty means the whole
     * device (minus [excludedFolders]). This is what the owner means by "escanea solo la carpeta
     * que yo elijo" — the old UI saved picks as excludes, which did the opposite.
     */
    val includedFolders: Set<String> = emptySet(),
) {
    val sanitizedMinimumDurationSeconds: Int
        get() = minimumDurationSeconds.coerceAtLeast(0)

    val sanitizedExcludedFolders: Set<String>
        get() = deduplicateFolderEntries(excludedFolders)

    val sanitizedIncludedFolders: Set<String>
        get() = deduplicateFolderEntries(includedFolders)

    companion object {
        private val DuplicateSlashRegex = Regex("/+")

        fun normalizeFolderEntry(raw: String): String {
            return raw
                .trim()
                .replace('\\', '/')
                .replace(DuplicateSlashRegex, "/")
                .trim('/')
        }

        fun deduplicateFolderEntries(entries: Iterable<String>): Set<String> {
            val deduplicated = linkedMapOf<String, String>()
            entries.forEach { entry ->
                val normalized = normalizeFolderEntry(entry)
                if (normalized.isNotEmpty()) {
                    deduplicated.putIfAbsent(normalized.lowercase(Locale.ROOT), normalized)
                }
            }
            return deduplicated.values.toSet()
        }
    }
}

internal fun splitArtistNames(rawArtist: String): List<String> {
    return rawArtist
        .split(';')
        .flatMap { it.split(FeaturingSeparator) }
        .flatMap { it.split(CollabSeparator) }
        .flatMap(::splitCommaList)
        .map(::trimDanglingBracket)
        .filter(String::isNotBlank)
        .ifEmpty { listOf(rawArtist) }
}

/** The same artist name always maps to the same id, whatever its position in the tag or the number of artists. */
internal fun localArtistId(artistName: String): String {
    val normalized = artistName.trim().lowercase(Locale.ROOT)
    val hash = UUID.nameUUIDFromBytes(normalized.toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")
    return "LOCAL_ARTIST_$hash"
}

private val FeaturingSeparator = Regex("""(?:\s+|\s*[(\[]\s*)(?:feat\.?|ft\.|featuring)\s+""", RegexOption.IGNORE_CASE)
private val CollabSeparator = Regex("""\s+x\s+""")
private val BandJoiner = Regex("""&|\band\b""", RegexOption.IGNORE_CASE)

private fun splitCommaList(part: String): List<String> {
    val pieces = part.split(", ").map(String::trim)
    if (pieces.size < 2 || BandJoiner.containsMatchIn(part)) return listOf(part)
    if (pieces.any { it.startsWith("the ", ignoreCase = true) }) return listOf(part)
    return pieces
}

private fun trimDanglingBracket(part: String): String {
    val trimmed = part.trim()
    return when {
        trimmed.endsWith(")") && !trimmed.contains("(") -> trimmed.dropLast(1).trim()
        trimmed.endsWith("]") && !trimmed.contains("[") -> trimmed.dropLast(1).trim()
        else -> trimmed
    }
}

data class LocalSongScanSummary(
    /** Row 855: the AUDIO files the scan found — what Biblioteca ▸ Local lists (videos go elsewhere, row 583). */
    val scannedSongs: Int,
    val removedSongs: Int,
    /** Songs the scan no longer sees but kept because their storage volume is not mounted right now (row 694). */
    val keptMissing: Int = 0,
    /** Row 855: video files the scan registered; they are listed under «Vídeos exportados», not in Local. */
    val scannedVideos: Int = 0,
    /** Row 855: audio files MediaStore listed in the chosen folders that were not imported: a format Aura does not play. */
    val skippedUnsupported: Int = 0,
    /** Row 855: audio files in the chosen folders that MediaStore lists with no duration, or shorter than the minimum. */
    val skippedShort: Int = 0,
)

/**
 * Row 855 (owner test #36, 2026-11-24: «59 canciones encontradas» in the scan, 58 in Biblioteca ▸ Local). The scan's
 * summary counted every track it registered, videos included, while Local lists audio only (row 583). Counted here the way
 * Local lists them, with the rest said apart.
 */
internal fun localScanSummary(
    scannedIsVideo: List<Boolean>,
    removedSongs: Int,
    keptMissing: Int,
    skippedUnsupported: Int,
    skippedShort: Int,
): LocalSongScanSummary = LocalSongScanSummary(
    scannedSongs = scannedIsVideo.count { !it },
    removedSongs = removedSongs,
    keptMissing = keptMissing,
    scannedVideos = scannedIsVideo.count { it },
    skippedUnsupported = skippedUnsupported,
    skippedShort = skippedShort,
)

/**
 * A scan that could not be trusted to say what is on the device. Thrown BEFORE anything is deleted, so the library keeps
 * every local song together with its likes, playlist entries and history (a deleted song row takes them with it by
 * cascade). [keptSongs] > 0: the scan ran but found no audio at all, and kept that many local songs it did not see.
 */
class LocalScanFailedException(
    message: String,
    val keptSongs: Int = 0,
    cause: Throwable? = null,
    /** True when the scan stopped because Aura may not read the device's audio. */
    val missingPermission: Boolean = false,
) : IllegalStateException(message, cause)

/**
 * Row 694: a scan that finds NO audio while the library still lists local audio does not delete it. A volume that is not
 * readable right now, or a folder filter that matches nothing, looks exactly like "every file is gone"; any scan that
 * finds at least one audio file removes the missing ones as before.
 */
internal fun refusesEmptyScan(scannedAudio: Int, libraryLocalAudio: Int): Boolean =
    scannedAudio == 0 && libraryLocalAudio > 0

/**
 * Row 694: the storage root of an absolute path (`/storage/emulated/0`, `/storage/1A2B-3C4D`), the volume key used before
 * Android 10, where MediaStore has no volume-name column. Null when the path does not say.
 */
internal fun storageRootOf(absolutePath: String?): String? {
    val parts = absolutePath?.split('/')?.filter { it.isNotEmpty() } ?: return null
    if (parts.size < 3 || parts[0] != "storage") return null
    return if (parts[1] == "emulated") {
        if (parts.size >= 4) "/storage/emulated/${parts[2]}" else null
    } else {
        "/storage/${parts[1]}"
    }
}

/**
 * Row 694: which local songs the scan no longer sees it may delete (first) and which it keeps (second). A song recorded
 * on a storage volume that is not mounted right now (an SD card taken out, a USB drive) is kept: the volume is missing,
 * not its files. A song with no recorded volume (scanned before this rule) is removed as before.
 */
internal fun splitMissingByVolume(
    missingIds: List<String>,
    volumeOf: Map<String, String>,
    isVolumePresent: (String) -> Boolean,
): Pair<List<String>, List<String>> = missingIds.partition { id -> volumeOf[id]?.let(isVolumePresent) ?: true }

class LocalSongScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    /**
     * [removeMissing] = the user confirmed «remove from Aura the local songs that are no longer on the phone»: the two
     * row-694 guards (empty scan, absent volume) step aside and every song the scan does not see is removed.
     * Row 881: [fullRescan] reads every file again instead of reusing what an unchanged file gave last time.
     * [automatic] (only [scanAutomatically]): nobody asked for this scan, so it never deletes a song it does not see.
     */
    suspend fun scanDevice(
        scanConfig: LocalSongScanConfig = LocalSongScanConfig(),
        removeMissing: Boolean = false,
        fullRescan: Boolean = false,
        automatic: Boolean = false,
    ): LocalSongScanSummary = scanMutex.withLock { withContext(Dispatchers.IO) {
        // Row 694: no permission, a null cursor or a failing query is a FAILED scan, never "zero tracks".
        if (!hasAudioReadPermission()) {
            throw LocalScanFailedException("audio read permission not granted", missingPermission = true)
        }
        // Row 881: what the last successful scan built from each file, reused for the files MediaStore reports unchanged.
        val autoStamp = currentAutoScanStamp(scanConfig)
        val cacheHeader = currentScanCacheHeader()
        val scanCache = readScanCache()
        val remembered = if (
            localScanCacheUsable(scanCache?.header, cacheHeader, fullRescan, versionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ) {
            scanCache?.tracks.orEmpty().associateBy { it.record.id }
        } else {
            emptyMap()
        }
        val snapshot = try {
            queryTracks(scanConfig, remembered)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalScanFailedException) {
            throw e
        } catch (e: Exception) {
            throw LocalScanFailedException("MediaStore query failed", cause = e)
        }
        // Row 724: ReplayGain tags, read outside the transaction and only for files new or changed since the last read.
        val replayGain = readReplayGain(snapshot.tracks)
        var summary = LocalSongScanSummary(0, 0)
        val recordedVolumes = readSongVolumes()
        val volumePresent = volumePresence()
        var keptOnAbsentVolume = emptyList<String>()
        database.withTransaction {
            val existingLocalIds = localSongIds()
            val scannedIds = snapshot.tracks.map(LocalTrackRecord::id)
            val scannedIdSet = scannedIds.toSet()
            val removedIds = existingLocalIds.filterNot(scannedIdSet::contains)

            val videoIdPrefix = "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/"
            val libraryLocalAudio = existingLocalIds.count { !it.startsWith(videoIdPrefix) }
            if (!removeMissing && refusesEmptyScan(snapshot.tracks.count { !it.isVideo }, libraryLocalAudio)) {
                throw LocalScanFailedException("scan found no audio", keptSongs = removedIds.size)
            }
            val (missingOnMountedVolumes, keptOnVolume) = if (removeMissing) {
                removedIds to emptyList<String>()
            } else {
                splitMissingByVolume(removedIds, recordedVolumes, volumePresent)
            }
            // Row 882: a missing song matched one-to-one with a file seen for the first time was moved or renamed; its
            // new row takes its likes, playlist entries, history and play counts before the old row is deleted. A song
            // with several possible matches is never merged and, unless the user confirmed the removal, kept.
            val existingLocalIdSet = existingLocalIds.toSet()
            val moved = moveRenamedLocalSongs(missingOnMountedVolumes, snapshot.tracks.filterNot { it.id in existingLocalIdSet })
            val keptUnconfirmed = when {
                removeMissing -> emptyList()
                // Row 881: a scan nobody asked for (app start, a MediaStore change) never deletes a song; only the old
                // row of a moved song goes, after its data moved to the new one.
                automatic -> missingOnMountedVolumes.filterNot(moved.moves::containsKey)
                else -> missingOnMountedVolumes.filter(moved.ambiguous::contains)
            }
            val removable = missingOnMountedVolumes - keptUnconfirmed.toSet()
            val kept = keptOnVolume + keptUnconfirmed
            keptOnAbsentVolume = kept
            removable.chunked(SqlBatchSize).forEach(::deleteSongsByIds)

            val existingSongs = loadSongs(scannedIds)
            val existingArtists = loadArtists(snapshot.artists.map(LocalArtistRecord::id))
            val existingAlbums = loadAlbums(snapshot.albums.map(LocalAlbumRecord::id))

            // Row 881: a file reused unchanged whose row still says what it said is not written again; when that is every
            // file and nothing went, the library is already what the scan would write.
            val untouchedIds = snapshot.tracks.filter { track ->
                track.id in snapshot.reusedIds && existingSongs[track.id].isUnchangedLocalRow(
                    track,
                    cachedReplayGainLoudness(replayGain, track.id, track.sizeBytes, track.modifiedEpochSeconds),
                )
            }.mapTo(HashSet(), LocalTrackRecord::id)
            if (removable.isEmpty() && untouchedIds.size == snapshot.tracks.size) {
                summary = localScanSummary(
                    scannedIsVideo = snapshot.tracks.map(LocalTrackRecord::isVideo),
                    removedSongs = 0,
                    keptMissing = kept.size,
                    skippedUnsupported = snapshot.skippedUnsupported,
                    skippedShort = snapshot.skippedShort,
                )
                return@withTransaction
            }

            snapshot.artists.forEach { artist ->
                val existingArtist = existingArtists[artist.id]
                if (existingArtist != null && existingArtist.isLocal) {
                    update(existingArtist.copy(name = artist.name))
                } else insert(
                    ArtistEntity(
                        id = artist.id,
                        name = artist.name,
                        thumbnailUrl = existingArtist?.thumbnailUrl,
                        channelId = null,
                        lastUpdateTime = existingArtist?.lastUpdateTime ?: LocalDateTime.now(),
                        bookmarkedAt = existingArtist?.bookmarkedAt,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.forEach { album ->
                val existingAlbum = existingAlbums[album.id]
                if (existingAlbum != null && existingAlbum.isLocal) {
                    update(
                        existingAlbum.copy(
                            title = album.title,
                            year = album.year ?: existingAlbum.year,
                            thumbnailUrl = album.thumbnailUrl ?: existingAlbum.thumbnailUrl,
                            songCount = album.songCount,
                            duration = album.duration,
                        ),
                    )
                } else insert(
                    AlbumEntity(
                        id = album.id,
                        playlistId = null,
                        title = album.title,
                        year = album.year ?: existingAlbum?.year,
                        thumbnailUrl = album.thumbnailUrl ?: existingAlbum?.thumbnailUrl,
                        themeColor = existingAlbum?.themeColor,
                        songCount = album.songCount,
                        duration = album.duration,
                        explicit = false,
                        lastUpdateTime = LocalDateTime.now(),
                        bookmarkedAt = existingAlbum?.bookmarkedAt,
                        likedDate = existingAlbum?.likedDate,
                        inLibrary = existingAlbum?.inLibrary,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.map(LocalAlbumRecord::id).distinct().chunked(SqlBatchSize).forEach(::deleteAlbumArtistMapsByAlbumIds)
            snapshot.albums.forEach { album ->
                album.artistIds.forEachIndexed { index, artistId ->
                    insert(
                        AlbumArtistMap(
                            albumId = album.id,
                            artistId = artistId,
                            order = index,
                        ),
                    )
                }
            }

            snapshot.tracks.forEach { track ->
                if (track.id in untouchedIds) return@forEach
                val existingSong = existingSongs[track.id]?.song
                if (existingSong != null && existingSong.isLocal) {
                    update(
                        existingSong.copy(
                            title = track.title,
                            duration = track.durationSeconds,
                            thumbnailUrl = track.thumbnailUrl ?: existingSong.thumbnailUrl,
                            albumId = track.albumId,
                            albumName = track.albumName,
                            year = track.year ?: existingSong.year,
                            dateModified = track.dateModified ?: existingSong.dateModified,
                            isVideo = track.isVideo,
                        ),
                    )
                } else insert(
                    SongEntity(
                        id = track.id,
                        title = track.title,
                        duration = track.durationSeconds,
                        thumbnailUrl = track.thumbnailUrl ?: existingSong?.thumbnailUrl,
                        albumId = track.albumId,
                        albumName = track.albumName,
                        explicit = existingSong?.explicit ?: false,
                        year = track.year ?: existingSong?.year,
                        date = existingSong?.date,
                        dateModified = track.dateModified ?: existingSong?.dateModified,
                        liked = existingSong?.liked ?: false,
                        likedDate = existingSong?.likedDate,
                        totalPlayTime = existingSong?.totalPlayTime ?: 0L,
                        inLibrary = null,
                        dateDownload = existingSong?.dateDownload,
                        isLocal = true,
                        isVideo = track.isVideo,
                    ),
                )
                upsert(
                    FormatEntity(
                        id = track.id,
                        itag = -1,
                        mimeType = track.mimeType,
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = track.sizeBytes,
                        loudnessDb = cachedReplayGainLoudness(replayGain, track.id, track.sizeBytes, track.modifiedEpochSeconds),
                        perceptualLoudnessDb = null,
                        // Keep whatever the column already holds across rescans (don't wipe it to null). Nothing
                        // measures loudness any more (row 791): local files are levelled by loudnessDb above,
                        // their ReplayGain tag (rows 724-725), or by the unknown-loudness default.
                        measuredLoudnessDb = measuredLoudnessDbForId(track.id),
                        playbackUrl = null,
                    ),
                )
                deleteSongArtistMaps(track.id)
                track.artists.forEachIndexed { index, artist ->
                    insert(
                        SongArtistMap(
                            songId = track.id,
                            artistId = artist.id,
                            position = index,
                        ),
                    )
                }
                deleteSongAlbumMaps(track.id)
                track.albumId?.let { albumId ->
                    insert(
                        SongAlbumMap(
                            songId = track.id,
                            albumId = albumId,
                            index = 0,
                        ),
                    )
                }
            }

            pruneLocalAlbums()
            pruneLocalArtists()
            pruneFormats()
            prunePlayCounts()

            summary = localScanSummary(
                scannedIsVideo = snapshot.tracks.map(LocalTrackRecord::isVideo),
                removedSongs = removable.size - moved.moves.size,
                keptMissing = kept.size,
                skippedUnsupported = snapshot.skippedUnsupported,
                skippedShort = snapshot.skippedShort,
            )
        }
        // The kept songs keep their recorded volume, or the next scan would no longer know to keep them.
        writeSongVolumes(
            buildMap {
                keptOnAbsentVolume.forEach { id -> recordedVolumes[id]?.let { put(id, it) } }
                snapshot.tracks.forEach { track -> track.volume?.let { put(track.id, it) } }
            },
        )
        val replayGainIds = keptOnAbsentVolume.toSet() + snapshot.tracks.map(LocalTrackRecord::id)
        writeReplayGainCache(replayGain.filterKeys(replayGainIds::contains))
        // Row 881: remembered only after a successful transaction; a failed or refused scan keeps the previous memory.
        writeScanCache(
            LocalScanCacheFile(
                header = cacheHeader,
                autoStamp = autoStamp,
                tracks = snapshot.tracks.mapNotNull { track ->
                    snapshot.fingerprints[track.id]?.let { CachedLocalTrack(fingerprint = it, record = track) }
                },
            ),
        )

        val videoTracks = snapshot.tracks.filter { it.isVideo }
        if (videoTracks.isNotEmpty()) {
            context.dataStore.edit { prefs ->
                val currentVideoIds = withScannedIdsAppended(
                    existing = prefs[ExportedVideoIdsKey].orEmpty()
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    scanned = videoTracks.map { it.id },
                )
                val currentUrisMap = withScannedUrisAppended(
                    existing = parseExportedFileUriMap(prefs[ExportedFileUrisKey].orEmpty()),
                    scanned = videoTracks.associate { it.id to it.id },
                )
                prefs[ExportedVideoIdsKey] = currentVideoIds.take(ExportedEntriesCap).joinToString(",")
                prefs[ExportedFileUrisKey] = currentUrisMap.entries.take(ExportedEntriesCap).joinToString("\u001E") { "${it.key}\u001F${it.value}" }
            }
        }

        return@withContext summary
    } }

    /**
     * Row 881: the scan run on app start and after a MediaStore change (`LocalLibraryAutoScan`), never on a timer. Null
     * when it did not run: no permission, or on Android 11+ MediaStore reports nothing new since the last successful scan.
     */
    suspend fun scanAutomatically(scanConfig: LocalSongScanConfig): LocalSongScanSummary? {
        if (!hasAudioReadPermission()) return null
        val stored = withContext(Dispatchers.IO) { readScanCache()?.autoStamp }
        if (autoScanFindsNothingNew(stored, currentAutoScanStamp(scanConfig))) return null
        return scanDevice(scanConfig, automatic = true)
    }

    /** Row 881: only someone who already uses local music gets automatic scans (they scanned once, or have local songs). */
    suspend fun usesLocalLibrary(): Boolean =
        hasAudioReadPermission() && withContext(Dispatchers.IO) { scanCacheFile.isFile || database.localSongIds().isNotEmpty() }

    // Row 881: what the last successful scan built from each file. noBackupFilesDir: MediaStore ids belong to this phone.
    private val scanCacheFile: File get() = File(context.noBackupFilesDir, "local_scan_tracks.json")

    private fun readScanCache(): LocalScanCacheFile? = runCatching {
        if (scanCacheFile.isFile) ScanCacheJson.decodeFromString(LocalScanCacheFile.serializer(), scanCacheFile.readText()) else null
    }.getOrNull()

    private fun writeScanCache(cache: LocalScanCacheFile) {
        runCatching {
            val tmp = File(scanCacheFile.path + ".tmp")
            tmp.writeText(ScanCacheJson.encodeToString(LocalScanCacheFile.serializer(), cache))
            if (!tmp.renameTo(scanCacheFile)) {
                scanCacheFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    private fun currentScanCacheHeader() = LocalScanCacheHeader(
        rules = LocalScanRulesVersion,
        shape = LocalTrackRecord.serializer().descriptor.shape(),
        mediaStoreVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { MediaStore.getVersion(context) }.getOrNull()
        } else {
            null
        },
    )

    private fun currentAutoScanStamp(scanConfig: LocalSongScanConfig): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull() ?: return null
        val states = volumes.associateWith { volume ->
            runCatching { "${MediaStore.getVersion(context, volume)}:${MediaStore.getGeneration(context, volume)}" }.getOrNull()
                ?: return null
        }
        return localAutoScanStamp(states, scanConfig)
    }

    /** Is this row exactly what the remembered build of an unchanged file would write? */
    private fun Song?.isUnchangedLocalRow(track: LocalTrackRecord, loudnessDb: Double?): Boolean {
        if (this == null || !song.isLocal) return false
        val stored = format ?: return false
        return song.title == track.title &&
            song.duration == track.durationSeconds &&
            song.albumId == track.albumId &&
            song.albumName == track.albumName &&
            song.isVideo == track.isVideo &&
            song.dateModified == track.dateModified &&
            (track.year == null || song.year == track.year) &&
            (track.thumbnailUrl == null || song.thumbnailUrl == track.thumbnailUrl) &&
            stored.contentLength == track.sizeBytes &&
            stored.mimeType == track.mimeType &&
            stored.loudnessDb == loudnessDb &&
            artists.map { it.id }.toSet() == track.artists.map(LocalArtistRecord::id).toSet()
    }

    /** The same permission LocalSongScreen asks for. Without it MediaStore answers with only this app's own files. */
    private fun hasAudioReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
    ) == PackageManager.PERMISSION_GRANTED

    // Row 694: songId -> storage volume of the last successful scan. noBackupFilesDir: volume ids belong to this phone.
    private val songVolumesFile: File get() = File(context.noBackupFilesDir, "local_song_volumes.tsv")

    private fun readSongVolumes(): Map<String, String> = runCatching {
        if (!songVolumesFile.isFile) return@runCatching emptyMap()
        songVolumesFile.readLines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab > 0 && tab < line.length - 1) line.substring(tab + 1) to line.substring(0, tab) else null
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeSongVolumes(volumeOf: Map<String, String>) {
        runCatching {
            val tmp = File(songVolumesFile.path + ".tmp")
            tmp.writeText(volumeOf.entries.joinToString("\n") { (id, volume) -> "$volume\t$id" })
            if (!tmp.renameTo(songVolumesFile)) {
                songVolumesFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    // Row 724: songId -> size, mtime and ReplayGain loudness when its tags were last read. noBackupFilesDir like row 694.
    private val replayGainCacheFile: File get() = File(context.noBackupFilesDir, "local_replaygain.tsv")

    private fun writeReplayGainCache(entries: Map<String, ReplayGainCacheEntry>) {
        runCatching {
            val tmp = File(replayGainCacheFile.path + ".tmp")
            tmp.writeText(serializeReplayGainCache(entries))
            if (!tmp.renameTo(replayGainCacheFile)) {
                replayGainCacheFile.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    /**
     * Row 724: the read cache plus this scan's reads. Reads the tags only of audio files never read or changed since (size or
     * mtime), at most [MaxReplayGainReadsPerScan] files and [ReplayGainReadBudgetMs] per scan; the rest wait for the next scan.
     */
    private suspend fun readReplayGain(tracks: List<LocalTrackRecord>): Map<String, ReplayGainCacheEntry> {
        val cache = runCatching {
            if (replayGainCacheFile.isFile) parseReplayGainCache(replayGainCacheFile.readLines()) else emptyMap()
        }.getOrDefault(emptyMap())
        val audio = tracks.filterNot(LocalTrackRecord::isVideo).associateBy(LocalTrackRecord::id)
        val toRead = replayGainReadsNeeded(
            audio.values.map { Triple(it.id, it.sizeBytes, it.modifiedEpochSeconds) },
            cache,
            MaxReplayGainReadsPerScan,
        )
        if (toRead.isEmpty()) return cache
        val read = HashMap<String, ReplayGainCacheEntry>()
        val deadline = SystemClock.elapsedRealtime() + ReplayGainReadBudgetMs
        for (id in toRead) {
            currentCoroutineContext().ensureActive()
            if (SystemClock.elapsedRealtime() > deadline) break
            val track = audio[id] ?: continue
            val tags = readTagMaps(Uri.parse(id), track.dataPath) ?: continue
            read[id] = ReplayGainCacheEntry(
                sizeBytes = track.sizeBytes,
                modifiedEpochSeconds = track.modifiedEpochSeconds,
                loudnessDb = replayGainFromTags(tags)?.let(::replayGainToLoudnessDb),
            )
        }
        return cache + read
    }

    /** Container and per-stream tags of one file via ffprobe (ffmpeg-kit, already in the app). Null when it cannot be read. */
    private fun readTagMaps(contentUri: Uri, dataPath: String?): List<Map<String, String>>? = try {
        val input = dataPath?.takeIf { path -> File(path).let { it.isFile && it.canRead() } }
            ?: FFmpegKitConfig.getSafParameterForRead(context, contentUri)
        FFprobeKit.getMediaInformation(input).mediaInformation?.let { info ->
            listOf(info.tags.toStringMap()) + info.streams.orEmpty().map { it.tags.toStringMap() }
        }
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        // ffmpeg-kit's native libraries failed to load: the file keeps today's behaviour, the scan itself goes on.
        null
    }

    private fun org.json.JSONObject?.toStringMap(): Map<String, String> {
        val json = this ?: return emptyMap()
        return buildMap { json.keys().forEach { key -> put(key, json.optString(key)) } }
    }

    /** Is a recorded volume mounted now? When the answer cannot be read, nothing counts as mounted (keep, never delete). */
    @Suppress("DEPRECATION")
    private fun volumePresence(): (String) -> Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val attached = runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull() ?: return { false }
            return { volume -> volume in attached }
        }
        return { root ->
            runCatching {
                Environment.getExternalStorageState(File(root)) in
                    setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
            }.getOrDefault(false)
        }
    }

    /**
     * Row 882, inside the scan transaction: the new row is a copy of the old one (likes, play time, lyrics settings), then
     * the playlist entries, the history, the play counts and the lyrics are pointed at it. The old row is deleted after.
     * A file that already has a row of its own never takes another song's data.
     */
    private suspend fun moveRenamedLocalSongs(
        missingIds: List<String>,
        appearedTracks: List<LocalTrackRecord>,
    ): LocalSongMoves {
        if (missingIds.isEmpty() || appearedTracks.isEmpty()) return LocalSongMoves(emptyMap(), emptySet())
        val oldSongs = loadSongs(missingIds)
        val takenIds = loadSongs(appearedTracks.map(LocalTrackRecord::id)).keys
        val verdict = matchMovedLocalSongs(
            missing = oldSongs.values.map { old ->
                LocalSongIdentity(
                    id = old.song.id,
                    isVideo = old.song.isVideo,
                    sizeBytes = old.format?.contentLength ?: 0L,
                    durationSeconds = old.song.duration,
                    title = old.song.title,
                    albumName = old.song.albumName,
                )
            },
            appeared = appearedTracks.filterNot { it.id in takenIds }.map { track ->
                LocalSongIdentity(
                    id = track.id,
                    isVideo = track.isVideo,
                    sizeBytes = track.sizeBytes,
                    durationSeconds = track.durationSeconds,
                    title = track.title,
                    albumName = track.albumName,
                )
            },
        )
        verdict.moves.forEach { (oldId, newId) ->
            database.insert(oldSongs.getValue(oldId).song.copy(id = newId))
            database.moveLocalSongPlaylistEntries(oldId, newId)
            database.moveLocalSongEvents(oldId, newId)
            database.moveLocalSongPlayCounts(oldId, newId)
            database.moveLocalSongLyrics(oldId, newId)
        }
        return verdict
    }

        private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SqlBatchSize)
            .flatMap { chunk -> database.getSongsByIds(chunk) }
            .associateBy { item -> item.song.id }

        private suspend fun loadArtists(ids: List<String>): Map<String, ArtistEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getArtistEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

        private suspend fun loadAlbums(ids: List<String>): Map<String, AlbumEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getAlbumEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

    @Suppress("DEPRECATION")
    private fun queryTracks(
        scanConfig: LocalSongScanConfig,
        remembered: Map<String, CachedLocalTrack> = emptyMap(),
    ): LocalScanSnapshot {
        val sanitizedMinimumDurationMs = scanConfig.sanitizedMinimumDurationSeconds.toLong() * 1000L
        val sanitizedExcludedFolders = scanConfig.sanitizedExcludedFolders
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val sanitizedIncludedFolders = scanConfig.sanitizedIncludedFolders
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                add(MediaStore.MediaColumns.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }.toTypedArray()
        val durationFloor = if (sanitizedMinimumDurationMs > 0L) {
            "${MediaStore.Audio.Media.DURATION} >= $sanitizedMinimumDurationMs"
        } else {
            "${MediaStore.Audio.Media.DURATION} > 0"
        }
        val selectionWith = { durationClause: String ->
            buildList {
                add("${MediaStore.Audio.Media.SIZE} > 0")
                add(durationClause)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add("${MediaStore.MediaColumns.IS_PENDING} = 0")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add("is_trashed = 0")
                }
            }.joinToString(" AND ")
        }
        val selection = selectionWith(durationFloor)

        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownTitle = context.getString(R.string.unknown)
        val tracks = mutableListOf<LocalTrackRecord>()
        var skippedUnsupported = 0
        val fingerprints = HashMap<String, LocalFileFingerprint>()
        val reusedIds = HashSet<String>()
        // Row 881: a file MediaStore reports unchanged since the last scan is not opened again; its remembered build is reused.
        fun reuseIfUnchanged(id: String, fingerprint: LocalFileFingerprint): Boolean {
            fingerprints[id] = fingerprint
            val cached = remembered[id]?.takeIf { isUnchangedFile(it.fingerprint, fingerprint) } ?: return false
            tracks += cached.record
            reusedIds += id
            return true
        }
        (context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC, ${MediaStore.Audio.Media._ID} ASC",
        ) ?: throw LocalScanFailedException("MediaStore returned no audio cursor")).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val volumeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            val generationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                val normalizedFolderPath = resolveNormalizedFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                )
                // Include list wins: if the user picked folders, nothing outside them is imported.
                if (!shouldIncludeFolder(normalizedFolderPath, sanitizedIncludedFolders)) {
                    continue
                }
                if (shouldExcludeFolder(normalizedFolderPath, sanitizedExcludedFolders)) {
                    continue
                }
                val displayName = cursor.getString(displayNameIndex)
                val mimeType = cursor.getString(mimeTypeIndex)?.takeIf(String::isNotBlank) ?: "audio/*"
                if (!SupportedLocalAudio.isSupported(displayName, mimeType)) {
                    skippedUnsupported++
                    continue
                }
                if (reuseIfUnchanged(contentUri.toString(), cursor.fingerprint(sizeIndex, dateModifiedIndex, generationIndex))) continue

                val rawTitle = cursor.getString(titleIndex)
                val rawArtist = cursor.getString(artistIndex)
                val rawAlbum = cursor.getString(albumIndex)
                val rawYear = cursor.getIntOrNull(yearIndex)?.takeIf { it > 0 }
                val rawDuration = cursor.getLong(durationIndex).coerceAtLeast(0L)
                val dataPath = cursor.getStringOrNull(dataPathIndex)

                // If MediaStore lacks ID3 tags (e.g. newly downloaded files), read tags directly via MediaMetadataRetriever
                val needsId3Extraction = rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", ignoreCase = true) ||
                    rawAlbum.isNullOrBlank() || rawAlbum.equals("<unknown>", ignoreCase = true) ||
                    rawTitle.isNullOrBlank() || rawTitle == displayName || rawTitle.endsWith(".mp3", ignoreCase = true) ||
                    rawYear == null || rawDuration == 0L

                val id3 = if (needsId3Extraction) {
                    extractId3Metadata(contentUri, dataPath)
                } else null

                val effectiveTitle = id3?.title ?: rawTitle
                val effectiveArtist = id3?.artist ?: rawArtist
                val effectiveAlbum = id3?.album ?: rawAlbum
                val effectiveYear = id3?.year ?: rawYear
                val effectiveDurationMs = if (rawDuration > 0L) rawDuration else (id3?.durationMs ?: 0L)

                val artistValue = normalizeArtistName(effectiveArtist, unknownArtist)
                val splitArtists = splitArtistNames(artistValue).ifEmpty { listOf(unknownArtist) }
                val artists = splitArtists.map { name ->
                    LocalArtistRecord(
                        id = localArtistId(name),
                        name = name,
                    )
                }
                val mediaStoreAlbumId = cursor.getLongOrNull(albumIdIndex)
                val albumName = normalizeAlbumName(effectiveAlbum)
                val title = normalizeTitle(
                    title = effectiveTitle,
                    displayName = displayName,
                    fallback = unknownTitle,
                )
                tracks += LocalTrackRecord(
                    id = contentUri.toString(),
                    title = title,
                    artists = artists,
                    albumId = albumName?.let {
                        buildAlbumId(
                            mediaStoreAlbumId = mediaStoreAlbumId,
                            albumName = it,
                            primaryArtistId = artists.firstOrNull()?.id,
                        )
                    },
                    albumName = albumName,
                    durationSeconds = (effectiveDurationMs / 1000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    year = effectiveYear,
                    dateModified = cursor.getLong(dateModifiedIndex)
                        .takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                    mimeType = mimeType,
                    thumbnailUrl = iad1tya.echo.music.utils.coil.LocalAudioArtFetcher.uriFor(contentUri.toString()),
                    volume = cursor.getStringOrNull(volumeIndex) ?: storageRootOf(dataPath),
                    modifiedEpochSeconds = cursor.getLong(dateModifiedIndex).coerceAtLeast(0L),
                    dataPath = dataPath,
                )
            }
        }

        val videoProjection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.TITLE)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.ARTIST)
            add(MediaStore.Video.Media.ALBUM)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            } else {
                add(MediaStore.MediaColumns.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.MediaColumns.GENERATION_MODIFIED)
        }.toTypedArray()

        (context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            selection,
            null,
            "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC, ${MediaStore.Video.Media._ID} ASC",
        ) ?: throw LocalScanFailedException("MediaStore returned no video cursor")).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val volumeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            val generationIndex = cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                val normalizedFolderPath = resolveNormalizedFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                )
                if (!shouldIncludeFolder(normalizedFolderPath, sanitizedIncludedFolders)) continue
                if (shouldExcludeFolder(normalizedFolderPath, sanitizedExcludedFolders)) continue
                if (reuseIfUnchanged(contentUri.toString(), cursor.fingerprint(sizeIndex, dateModifiedIndex, generationIndex))) continue

                val displayName = cursor.getString(displayNameIndex)
                val mimeType = cursor.getString(mimeTypeIndex)?.takeIf(String::isNotBlank) ?: "video/*"
                val rawTitle = cursor.getString(titleIndex)
                val rawArtist = cursor.getString(artistIndex)
                val rawAlbum = cursor.getString(albumIndex)
                val rawDuration = cursor.getLong(durationIndex).coerceAtLeast(0L)

                val artistValue = normalizeArtistName(rawArtist, unknownArtist)
                val splitArtists = splitArtistNames(artistValue).ifEmpty { listOf(unknownArtist) }
                val artists = splitArtists.map { name ->
                    LocalArtistRecord(
                        id = localArtistId(name),
                        name = name,
                    )
                }
                val albumName = normalizeAlbumName(rawAlbum)
                val title = normalizeTitle(
                    title = rawTitle,
                    displayName = displayName,
                    fallback = unknownTitle,
                )
                tracks += LocalTrackRecord(
                    id = contentUri.toString(),
                    title = title,
                    artists = artists,
                    albumId = albumName?.let {
                        buildAlbumId(
                            mediaStoreAlbumId = null,
                            albumName = it,
                            primaryArtistId = artists.firstOrNull()?.id,
                        )
                    },
                    albumName = albumName,
                    durationSeconds = (rawDuration / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    year = null,
                    dateModified = cursor.getLong(dateModifiedIndex)
                        .takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                    mimeType = mimeType,
                    thumbnailUrl = null,
                    isVideo = true,
                    volume = cursor.getStringOrNull(volumeIndex) ?: storageRootOf(cursor.getStringOrNull(dataPathIndex)),
                )
            }
        }

        val albums = tracks
            .filter { !it.albumId.isNullOrBlank() && !it.albumName.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumId, albumTracks) ->
                LocalAlbumRecord(
                    id = albumId,
                    title = albumTracks.first().albumName.orEmpty(),
                    year = albumTracks.mapNotNull(LocalTrackRecord::year).maxOrNull(),
                    thumbnailUrl = albumTracks.mapNotNull(LocalTrackRecord::thumbnailUrl).firstOrNull(),
                    songCount = albumTracks.size,
                    duration = albumTracks.sumOf(LocalTrackRecord::durationSeconds),
                    artistIds = albumTracks.flatMap { track -> track.artists.map(LocalArtistRecord::id) }.distinct(),
                )
            }

        return LocalScanSnapshot(
            tracks = tracks,
            artists = tracks.flatMap(LocalTrackRecord::artists).distinctBy(LocalArtistRecord::id),
            albums = albums,
            skippedUnsupported = skippedUnsupported,
            skippedShort = countShortAudio(
                selection = selectionWith("(${MediaStore.Audio.Media.DURATION} IS NULL OR NOT ($durationFloor))"),
                includedFolders = sanitizedIncludedFolders,
                excludedFolders = sanitizedExcludedFolders,
            ),
            fingerprints = fingerprints,
            reusedIds = reusedIds,
        )
    }

    /**
     * Row 855: the audio files in the chosen folders that the duration clause leaves out of the scan, only counted (never
     * imported) so the summary can say why the device lists more audio than Local. Never fails the scan: 0 if unreadable.
     */
    @Suppress("DEPRECATION")
    private fun countShortAudio(selection: String, includedFolders: Set<String>, excludedFolders: Set<String>): Int = runCatching {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null)
            ?.use { cursor ->
                val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                var count = 0
                while (cursor.moveToNext()) {
                    val folder = resolveNormalizedFolderPath(
                        relativePath = cursor.getStringOrNull(relativePathIndex),
                        absolutePath = cursor.getStringOrNull(dataPathIndex),
                    )
                    if (shouldIncludeFolder(folder, includedFolders) && !shouldExcludeFolder(folder, excludedFolders)) count++
                }
                count
            } ?: 0
    }.getOrDefault(0)

    private fun normalizeTitle(title: String?, displayName: String?, fallback: String): String {
        return title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun normalizeArtistName(rawArtist: String?, fallback: String): String {
        val normalized = rawArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        return normalized ?: fallback
    }

    private fun normalizeAlbumName(rawAlbum: String?): String? {
        return rawAlbum?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
    }

    private fun buildAlbumId(
        mediaStoreAlbumId: Long?,
        albumName: String,
        primaryArtistId: String?,
    ): String {
        val stableId = mediaStoreAlbumId?.takeIf { it > 0L }
        return if (stableId != null) {
            "LOCAL_ALBUM_$stableId"
        } else {
            "LOCAL_ALBUM_${stableHash("$albumName|$primaryArtistId")}"
        }
    }

    private fun stableHash(source: String): String {
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
    }

    private fun resolveNormalizedFolderPath(relativePath: String?, absolutePath: String?): String? {
        val relativeFolder = LocalSongScanConfig.normalizeFolderEntry(relativePath.orEmpty())
        if (relativeFolder.isNotEmpty()) {
            return relativeFolder.lowercase(Locale.ROOT)
        }

        val absoluteFolder = absolutePath
            ?.replace('\\', '/')
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            .orEmpty()
        // Strip common Android volume prefixes so SAF picks (Music/Foo) match absolute DATA paths.
        val withoutVolume = absoluteFolder
            .replace(Regex("(?i)^/storage/emulated/\\d+/"), "")
            .replace(Regex("(?i)^/sdcard/"), "")
            .replace(Regex("(?i)^/mnt/sdcard/"), "")
            .replace(Regex("(?i)^/storage/[^/]+/"), "")
        val normalizedAbsoluteFolder = LocalSongScanConfig.normalizeFolderEntry(withoutVolume)
        return normalizedAbsoluteFolder.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)
    }

    private fun shouldExcludeFolder(folderPath: String?, excludedFolders: Set<String>): Boolean {
        if (folderPath.isNullOrEmpty() || excludedFolders.isEmpty()) return false
        return folderMatchesAny(folderPath, excludedFolders)
    }

    /**
     * Empty include list = whole device. Non-empty = only matching folders/subfolders.
     * Unknown path while filtering → skip (never import "mystery" tracks outside the allowlist).
     */
    private fun shouldIncludeFolder(folderPath: String?, includedFolders: Set<String>): Boolean {
        if (includedFolders.isEmpty()) return true
        if (folderPath.isNullOrEmpty()) return false
        return folderMatchesAny(folderPath, includedFolders)
    }

    private fun folderMatchesAny(folderPath: String, folders: Set<String>): Boolean {
        return folders.any { folder ->
            folderPath == folder ||
                folderPath.startsWith("$folder/") ||
                folderPath.endsWith("/$folder") ||
                folderPath.contains("/$folder/")
        }
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    }

    private fun android.database.Cursor.fingerprint(sizeIndex: Int, dateModifiedIndex: Int, generationIndex: Int) =
        LocalFileFingerprint(
            sizeBytes = getLong(sizeIndex).coerceAtLeast(0L),
            modifiedEpochSeconds = getLong(dateModifiedIndex).coerceAtLeast(0L),
            generation = getLongOrNull(generationIndex),
        )

    private data class ExtractedId3Metadata(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val durationMs: Long? = null,
    )

    private fun extractId3Metadata(contentUri: Uri, dataPath: String?): ExtractedId3Metadata? {
        val retriever = android.media.MediaMetadataRetriever()
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            var opened = false
            if (!dataPath.isNullOrBlank()) {
                val file = java.io.File(dataPath)
                if (file.exists() && file.canRead()) {
                    retriever.setDataSource(file.absolutePath)
                    opened = true
                }
            }
            if (!opened) {
                try {
                    retriever.setDataSource(context, contentUri)
                    opened = true
                } catch (_: Exception) {
                    pfd = context.contentResolver.openFileDescriptor(contentUri, "r")
                    if (pfd != null) {
                        retriever.setDataSource(pfd.fileDescriptor)
                        opened = true
                    }
                }
            }
            if (!opened) return null

            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf(String::isNotBlank)
            val artist = (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            val yearStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = yearStr?.filter(Char::isDigit)?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }
            val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0L }

            ExtractedId3Metadata(
                title = title,
                artist = artist,
                album = album,
                year = year,
                durationMs = durationMs,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { pfd?.close() }
            runCatching { retriever.release() }
        }
    }

    private data class LocalScanSnapshot(
        val tracks: List<LocalTrackRecord>,
        val artists: List<LocalArtistRecord>,
        val albums: List<LocalAlbumRecord>,
        val skippedUnsupported: Int = 0,
        val skippedShort: Int = 0,
        /** Row 881: what MediaStore reported for each scanned file, remembered with its build after the scan. */
        val fingerprints: Map<String, LocalFileFingerprint> = emptyMap(),
        /** Row 881: files MediaStore reports unchanged since the last scan, whose remembered build was reused. */
        val reusedIds: Set<String> = emptySet(),
    )

    /** Row 881: what the last successful scan built, file by file (`local_scan_tracks.json`). */
    @Serializable
    private data class LocalScanCacheFile(
        val header: LocalScanCacheHeader,
        val autoStamp: String? = null,
        val tracks: List<CachedLocalTrack> = emptyList(),
    )

    @Serializable
    private data class CachedLocalTrack(
        val fingerprint: LocalFileFingerprint,
        val record: LocalTrackRecord,
    )

    @Serializable
    private data class LocalTrackRecord(
        val id: String,
        val title: String,
        val artists: List<LocalArtistRecord>,
        val albumId: String?,
        val albumName: String?,
        val durationSeconds: Int,
        val year: Int?,
        @Serializable(with = IsoLocalDateTimeSerializer::class)
        val dateModified: LocalDateTime?,
        val sizeBytes: Long,
        val mimeType: String,
        val thumbnailUrl: String?,
        val isVideo: Boolean = false,
        /** MediaStore volume name (Android 10+) or storage root ([storageRootOf]); null when unknown. */
        val volume: String? = null,
        /** Raw MediaStore DATE_MODIFIED (seconds): with [sizeBytes], says whether the ReplayGain read is still valid (row 724). */
        val modifiedEpochSeconds: Long = 0L,
        /** Absolute path before Android 10 (DATA column); null otherwise. */
        val dataPath: String? = null,
    )

    @Serializable
    private data class LocalArtistRecord(
        val id: String,
        val name: String,
    )

    private data class LocalAlbumRecord(
        val id: String,
        val title: String,
        val year: Int?,
        val thumbnailUrl: String?,
        val songCount: Int,
        val duration: Int,
        val artistIds: List<String>,
    )

    private companion object {
        val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
        const val SqlBatchSize = 900
        val ScanCacheJson = Json { ignoreUnknownKeys = true }
        /** Row 881: one scan at a time in the whole process (the screen, app start and MediaStore changes all scan). */
        val scanMutex = Mutex()
        const val MaxReplayGainReadsPerScan = 200
        const val ReplayGainReadBudgetMs = 30_000L
    }
}
