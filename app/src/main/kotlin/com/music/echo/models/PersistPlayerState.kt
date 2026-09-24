

package iad1tya.echo.music.models

import java.io.Serializable

data class PersistPlayerState(
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float,
    val currentPosition: Long,
    val currentMediaItemIndex: Int,
    val playbackState: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable {
    companion object {
        // Same reasoning/precedent as PersistQueue.serialVersionUID and MediaMetadata.serialVersionUID:
        // pinned so a future additive field doesn't silently break restoring playback position across
        // an app update.
        private const val serialVersionUID: Long = 1L
    }
}
