

package iad1tya.echo.music.db.entities

import iad1tya.echo.music.utils.ShareLinks

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.music.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isAutoSync", defaultValue = false.toString())
    val isAutoSync: Boolean = false,
    /**
     * Descarga automática de TODA la lista, presente y futura.
     *
     * 🔴 Punto 3 del dueño (2026-09-17): *"agregar un interruptor (toggle) en el menú de cada lista de
     * reproducción. Si está activo, todo el contenido actual y cualquier canción que se agregue en el
     * futuro a esa lista se descargará automáticamente"*.
     *
     * Vive en la LISTA y no en un ajuste global porque eso es exactamente lo que pidió: por lista.
     * Con `defaultValue` en SQL para que la migración la genere Room sola — ver la nota de la
     * migración 40→41 en `MusicDatabase`: una `Migration` a mano hay que registrarla en los DOS
     * builders, y olvidar el de Hilt es lo que rompió todas las instalaciones en 0.6.117.
     */
    @ColumnInfo(name = "autoDownload", defaultValue = false.toString())
    val autoDownload: Boolean = false,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + RandomStringUtils.insecure().next(8, true, false)
    }

    val shareLink: String?
        get() {
            return if (browseId != null)
                ShareLinks.playlist(browseId)
            else null
        }

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike().also {
        CoroutineScope(Dispatchers.IO).launch {
            if (browseId != null)
                YouTube.likePlaylist(browseId, bookmarkedAt == null)
        }
    }
}
