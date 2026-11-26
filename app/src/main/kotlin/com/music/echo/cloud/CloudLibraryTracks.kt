package iad1tya.echo.music.cloud

import iad1tya.echo.music.localmedia.LocalAlbumOrderEntry
import iad1tya.echo.music.localmedia.LocalArtistSplitRules
import iad1tya.echo.music.localmedia.LocalSongIdentity
import iad1tya.echo.music.localmedia.localAlbumArtistIds
import iad1tya.echo.music.localmedia.localAlbumGroupArtistId
import iad1tya.echo.music.localmedia.localAlbumIndexes
import iad1tya.echo.music.localmedia.localArtistId
import iad1tya.echo.music.localmedia.splitArtistNames
import java.nio.charset.StandardCharsets
import java.util.UUID

/*
 * Registry row 891 — a cloud file as a library song, with the SAME rules the phone's scanner applies (rows 879-880):
 * the artist separators and exceptions of Ajustes ▸ Música local, the album artist, disc then track order, and the same
 * artist and album ids, so a phone copy and a cloud copy of an album land on the same artist and album. Pure.
 */

data class CloudArtistRef(val id: String, val name: String)

data class CloudTrack(
    val id: String,
    val title: String,
    val artists: List<CloudArtistRef>,
    val albumArtists: List<CloudArtistRef>,
    val albumId: String?,
    val albumName: String?,
    val year: Int?,
    val disc: Int?,
    val track: Int?,
    val durationSeconds: Int,
    val mimeType: String,
    val sizeBytes: Long,
    val modTimeMs: Long?,
)

data class CloudAlbum(val id: String, val title: String, val year: Int?, val artistIds: List<String>, val songCount: Int, val durationSeconds: Int)

object CloudLibraryTracks {
    fun track(record: CloudFileRecord, rules: LocalArtistSplitRules, unknownArtist: String): CloudTrack {
        val tags = record.tags
        val artistTag = tags?.artist?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) } ?: unknownArtist
        val artists = splitArtistNames(artistTag, rules).ifEmpty { listOf(unknownArtist) }.map { CloudArtistRef(localArtistId(it), it) }
        val albumArtists = tags?.albumArtist?.let { splitArtistNames(it, rules) }.orEmpty().map { CloudArtistRef(localArtistId(it), it) }
        val albumName = tags?.album?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
        val albumId = albumName?.let {
            albumIdOf(it, localAlbumGroupArtistId(albumArtists.map(CloudArtistRef::id), artists.map(CloudArtistRef::id)))
        }
        return CloudTrack(
            id = record.id,
            title = CloudTagFallback.title(tags, record.fileName),
            artists = artists,
            albumArtists = albumArtists,
            albumId = albumId,
            albumName = albumName,
            year = tags?.year,
            disc = tags?.disc,
            track = tags?.track,
            durationSeconds = CloudTagFallback.durationSeconds(tags),
            mimeType = CloudAudioFiles.mimeOf(record.fileName) ?: "audio/*",
            sizeBytes = record.size,
            modTimeMs = record.modTimeMs,
        )
    }

    /** The phone scanner's id for an album without a MediaStore id (LocalSongScanner.buildAlbumId), byte for byte. */
    fun albumIdOf(albumName: String, primaryArtistId: String?): String =
        "LOCAL_ALBUM_" + UUID.nameUUIDFromBytes("$albumName|$primaryArtistId".toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")

    /** Albums of [tracks]: title, year, artists (album artists when tagged, row 879), count and length. */
    fun albums(tracks: List<CloudTrack>): List<CloudAlbum> =
        tracks.filter { it.albumId != null }.groupBy { it.albumId!! }.map { (id, songs) ->
            CloudAlbum(
                id = id,
                title = songs.first().albumName.orEmpty(),
                year = songs.firstNotNullOfOrNull { it.year },
                artistIds = localAlbumArtistIds(
                    songs.map { s -> s.albumArtists.map(CloudArtistRef::id) },
                    songs.map { s -> s.artists.map(CloudArtistRef::id) },
                ),
                songCount = songs.size,
                durationSeconds = songs.sumOf { it.durationSeconds },
            )
        }

    /** Song id -> position in its album: disc, then track, then title (row 879). */
    fun albumIndexes(tracks: List<CloudTrack>): Map<String, Int> =
        localAlbumIndexes(tracks.map { LocalAlbumOrderEntry(it.id, it.albumId, it.disc, it.track, it.title) })

    /** What the moved-file match of row 882 compares (size, title, album, duration). */
    internal fun identity(track: CloudTrack): LocalSongIdentity =
        LocalSongIdentity(track.id, isVideo = false, sizeBytes = track.sizeBytes, durationSeconds = track.durationSeconds, title = track.title, albumName = track.albumName)
}
