

package iad1tya.echo.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val playlistId: String? = null,
    val title: String,
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val themeColor: Int? = null,
    val songCount: Int,
    val duration: Int,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
    val bookmarkedAt: LocalDateTime? = null,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val description: String? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = false.toString())
    val isUploaded: Boolean = false,
) {
    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleUploaded() = copy(
        isUploaded = !isUploaded
    )

    fun toggleLibrary() = copy(
        inLibrary = if (inLibrary != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        // Durable + immediate (see AccountPush): the save/unsave reaches the account at once, is retried
        // when the network is back, and the liked-albums sync no longer re-saves what was just removed.
        if (playlistId != null) {
            iad1tya.echo.music.utils.AccountPush.submit(
                if (bookmarkedAt == null) iad1tya.echo.music.utils.AccountPushPolicy.Op.PLAYLIST_SAVE
                else iad1tya.echo.music.utils.AccountPushPolicy.Op.PLAYLIST_UNSAVE,
                playlistId,
            )
        }
    }
}
