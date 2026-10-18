

package iad1tya.echo.music.models

import java.io.Serializable

data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
    // Enhanced Shuffle context of this queue (e.g. "PL:<id>", "LIBRARY"); null = no persistent-shuffle
    // memory for this queue. Optional with a default so it survives across the explicit serialVersionUID.
    val contextId: String? = null,
) : Serializable {
    companion object {
        // Pinned so future additive fields deserialize old files with defaults instead of throwing
        // InvalidClassException. (Files written before this UID existed still fail once and are cleared
        // by clearPersistedQueueFiles — acceptable, and it never happens again after this build.)
        private const val serialVersionUID: Long = 1L
    }
}

// The saved queue's type and data used to carry NO pinned serialVersionUID (only PersistQueue did), so Java computed
// one from each class's structure — and a release build's structure (R8, Kotlin synthetics) is not stable from one
// version to the next. The device log of 2026-10-18 shows it: «Failed to read persisted queue, clearing data —
// InvalidClassException: QueueType … local class incompatible» on the first launch of beta13, and the same for
// QueueData on beta14. Every update therefore threw the saved queue away. Pinned now, with the nested classes kept by
// name in proguard-rules.pro (row 444). The first launch after THIS update still cannot read the old file once.
sealed class QueueType : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    object LIST : QueueType() {
        private const val serialVersionUID: Long = 1L
    }

    object YOUTUBE : QueueType() {
        private const val serialVersionUID: Long = 1L
    }

    object YOUTUBE_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID: Long = 1L
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private const val serialVersionUID: Long = 1L
    }
}

sealed class QueueData : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    data class YouTubeData(
        val endpoint: String,
        val continuation: String? = null
    ) : QueueData() {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class YouTubeAlbumRadioData(
        val playlistId: String,
        val albumSongCount: Int = 0,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData() {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData() {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
