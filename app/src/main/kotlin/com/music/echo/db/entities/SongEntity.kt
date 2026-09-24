

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"]
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, 
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, 
    val dateModified: LocalDateTime? = null, 
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, 
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @ColumnInfo(defaultValue = "0")
    val lyricsOffset: Int = 0,
    @ColumnInfo(defaultValue = true.toString())
    val romanizeLyrics: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false,
    @ColumnInfo(name = "isVideo", defaultValue = false.toString())
    val isVideo: Boolean = false,
    // Set once the video fallback ladder (VideoModeCoordinator) has exhausted every source for this
    // song's video stream — persisted so the "switch to video" toggle stops being offered for it on
    // future encounters (queue rebuilds, re-opening the song, app restart), not just for the rest of
    // the current playback session. See MediaMetadata.hasCompatibleVideo.
    @ColumnInfo(defaultValue = "0")
    val videoFormatIncompatible: Boolean = false
) {
    fun localToggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
    )

    fun toggleLike() = copy(
        liked = !liked,
        likedDate = if (!liked) LocalDateTime.now() else null,
        inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary
    ).also {
        // Owner report 2026-09-22 (unconfirmed by static reading — this endpoint is `like/like`,
        // never `subscription/subscribe`): liking/disliking a song appears to also subscribe the
        // artist on the real YouTube account. Timestamp only, no ids/titles (regla 4 de AGENTS.md):
        // if it happens again, this line lets the next app.log show whether a subscribeChannel call
        // (ArtistEntity.toggleLike, LibraryUploadSync, SpotifyImportRepository — the only 3 real
        // call sites) landed at the same moment, which static reading alone could not confirm.
        timber.log.Timber.i("SONG_LIKE_TOGGLE liked=%b", !liked)
        CoroutineScope(Dispatchers.IO).launch {
            YouTube.likeVideo(id, !liked)
        }
    }

    fun toggleLibrary(syncToYouTube: Boolean = true) = copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        likedDate = if (inLibrary == null) likedDate else null
    ).also {
        if (syncToYouTube) {
            CoroutineScope(Dispatchers.IO).launch {
                
                val addToLibrary = inLibrary == null
                YouTube.toggleSongLibrary(id, addToLibrary)
            }
        }
    }

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )
}
