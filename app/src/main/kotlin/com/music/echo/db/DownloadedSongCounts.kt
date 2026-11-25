package iad1tya.echo.music.db

import androidx.compose.runtime.Immutable

/**
 * Registry row 855 (owner test #18, 2026-11-24: Inicio said «Descargado · 21 canciones», the list showed 20). The song
 * rows the «Descargado» list reads (`song.isDownloaded = 1`, [DatabaseDao.downloadedSongs]), and how many of them are
 * videos. The quick-access tile used to count Media3's download index instead, which also holds the companion video
 * downloads (`<id>::video`) and COMPLETED records the library no longer lists — rows the list never shows.
 */
@Immutable
data class DownloadedSongCounts(
    val total: Int,
    val videos: Int,
) {
    /** Exactly what the list shows: `AutoPlaylistViewModel` drops `isVideo` rows when «Ocultar vídeos» is on. */
    fun listed(hideVideoSongs: Boolean): Int = if (hideVideoSongs) (total - videos).coerceAtLeast(0) else total

    companion object {
        val EMPTY = DownloadedSongCounts(total = 0, videos = 0)
    }
}
