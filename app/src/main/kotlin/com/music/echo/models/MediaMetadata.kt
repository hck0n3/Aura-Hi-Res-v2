

package iad1tya.echo.music.models

import androidx.compose.runtime.Immutable
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import iad1tya.echo.music.db.entities.Song
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.playback.VideoCompatibility
import iad1tya.echo.music.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val musicVideoType: String? = null,
    /** Direct video stream URL for a VIDEO PODCAST episode (played as-is, not resolved via YouTube). */
    val podcastVideoUrl: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    val suggestedBy: String? = null,
    /**
     * Purely structural — never read for display. Set to a fresh value when a MediaItem's tag needs to
     * change to force data-class inequality (so media3 treats a video/audio URI swap as a distinct
     * MediaItem and rebuilds its source) while keeping every real field intact for the UI.
     */
    val videoSwapNonce: Long? = null,
    /**
     * True once the video fallback ladder already exhausted every source for THIS id (persisted DB
     * flag when known locally, otherwise the in-session [VideoCompatibility] cache) — the "switch to
     * video" control stops being offered so tapping it can't error again on a song already proven
     * incompatible. Never true for a song never yet attempted: there is no cheap way to know that in
     * advance without resolving the stream for real (see VideoModeCoordinator).
     */
    val videoFormatIncompatible: Boolean = false,
) : Serializable {
    val isVideoSong: Boolean
        get() = musicVideoType != null && musicVideoType != MUSIC_VIDEO_TYPE_ATV

    /** The single source of truth the UI should check instead of raw [isVideoSong] (Player.kt et al). */
    val hasCompatibleVideo: Boolean
        get() = isVideoSong && !videoFormatIncompatible && !VideoCompatibility.isKnownIncompatible(id)

    data class Artist(
        val id: String?,
        val name: String,
    ) : Serializable {
        companion object {
            // See MediaMetadata's own companion for why this is pinned.
            private const val serialVersionUID: Long = 1L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    companion object {
        // Owner report (ronda 3, 2026-09-23): "después de las actualizaciones del reproductor no
        // continúa donde se quedó". Root cause — MediaMetadata travels inside PersistQueue.items,
        // the object graph MusicService serializes to disk to resume playback across a restart. Kotlin/
        // Java compute a class's serialVersionUID from its structure when none is declared, so adding
        // [videoFormatIncompatible] last round silently changed it — a queue saved by the OLD app
        // version threw InvalidClassException reading it back on the NEW one, which
        // MusicService.onCreate's runCatching swallows and treats as "nothing to restore"
        // (clearPersistedQueueFiles). PersistQueue.kt already pins its OWN UID for exactly this reason
        // ("future additive fields deserialize old files with defaults instead of throwing"); this class
        // sat one level deeper in the same graph without the same protection. Pinning it now means any
        // FUTURE additive field (with a default value, same as this one) no longer breaks the resume —
        // a file saved before this fix still fails to load once (same acceptable, one-time cost
        // PersistQueue's own fix already accepted), never again after.
        private const val serialVersionUID: Long = 1L
    }

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
            libraryAddToken = libraryAddToken,
            libraryRemoveToken = libraryRemoveToken,
            isVideo = isVideoSong
        )
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
        explicit = song.explicit,

        musicVideoType = if (song.isVideo) "MUSIC_VIDEO_TYPE_OMV" else null,
        suggestedBy = null,
        videoFormatIncompatible = song.videoFormatIncompatible,
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
            )
        },
        duration = duration ?: -1,
        thumbnailUrl = thumbnail.resize(1200, 1200),
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        explicit = explicit,
        setVideoId = setVideoId,
        musicVideoType = musicVideoType,
        libraryAddToken = libraryAddToken,
        libraryRemoveToken = libraryRemoveToken,
        suggestedBy = null
    )
