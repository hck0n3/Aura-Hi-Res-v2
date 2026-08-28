package iad1tya.echo.music.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import iad1tya.echo.music.R
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Full song downloads deferred while live video mode is active. Previously only the companion
 * VIDEO request was deferred while AUDIO still raced the live mux — that bandwidth fight is a
 * prime hitch source. Both are held here and flushed from [MusicService.exitVideoMode].
 */
internal object PendingDeferredDownloads {
    data class Entry(val title: String, val isVideoSong: Boolean, val wifiOnly: Boolean)

    private val pending = ConcurrentHashMap<String, Entry>()
    private val _pendingIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingIds: StateFlow<Set<String>> = _pendingIds

    fun mark(songId: String, title: String, isVideoSong: Boolean, wifiOnly: Boolean = false) {
        if (songId.isBlank()) return
        pending[songId] = Entry(title = title.ifBlank { songId }, isVideoSong = isVideoSong, wifiOnly = wifiOnly)
        _pendingIds.value = pending.keys.toSet()
    }

    fun take(songId: String): Entry? {
        val entry = pending.remove(songId)
        if (entry != null) _pendingIds.value = pending.keys.toSet()
        return entry
    }

    fun contains(songId: String): Boolean = pending.containsKey(songId)
}

/**
 * Enqueue audio (+ companion video when [isVideoSong]).
 *
 * When [deferWhileLiveVideo] is true, enqueue NOTHING yet — store for
 * [flushPendingSongDownload] after video mode ends (avoids ExoDownload fighting the live A/V mux).
 *
 * [wifiOnly] marks the AUTOMATIC download-on-like path (default ON): it used to burn mobile data
 * without ever asking — the owner's data-usage complaint (HALLAZGO-037 / cache audit). media3
 * 1.10.1 has no per-request network requirement on [DownloadRequest.Builder], so the gate is a
 * metered-network check at enqueue time: on mobile data the automatic download is skipped (the
 * song still plays from the stream cache); explicit user downloads keep the old behavior.
 */
fun enqueueSongDownloads(
    context: Context,
    songId: String,
    title: String,
    isVideoSong: Boolean,
    deferWhileLiveVideo: Boolean = false,
    wifiOnly: Boolean = false,
) {
    if (wifiOnly && !shouldAutoDownload(isActiveNetworkMetered(context))) {
        Timber.tag("Download").i("auto-download skipped on metered network (id omitted)")
        return
    }
    if (deferWhileLiveVideo) {
        PendingDeferredDownloads.mark(songId, title, isVideoSong, wifiOnly)
        return
    }
    val audio = DownloadRequest.Builder(songId, songId.toUri())
        .setCustomCacheKey(songId)
        .setData(title.toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, audio, false)
    if (isVideoSong) {
        enqueueVideoCompanionDownload(context, songId, title)
    }
}

/** Enqueue only the companion video offline download (`id::video`). */
fun enqueueVideoCompanionDownload(
    context: Context,
    songId: String,
    title: String,
) {
    val vidId = videoDownloadMediaId(songId)
    val video = DownloadRequest.Builder(vidId, vidId.toUri())
        .setCustomCacheKey(vidId)
        .setData(context.getString(R.string.downloading_video, title).toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, video, false)
}

/**
 * Automatic downloads must not burn mobile data (HALLAZGO-037): they enqueue only on an
 * unmetered network. Pure decision — the live metered reading stays at the call site.
 */
fun shouldAutoDownload(isActiveNetworkMetered: Boolean): Boolean = !isActiveNetworkMetered

private fun isActiveNetworkMetered(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    return cm?.isActiveNetworkMetered ?: false
}

/**
 * Flush a download deferred while watching video (audio + optional companion).
 * Safe to call when leaving video mode; no-op when nothing is pending.
 */
fun flushPendingSongDownload(context: Context, songId: String?) {
    if (songId.isNullOrBlank()) return
    val entry = PendingDeferredDownloads.take(songId) ?: return
    enqueueSongDownloads(
        context = context,
        songId = songId,
        title = entry.title,
        isVideoSong = entry.isVideoSong,
        deferWhileLiveVideo = false,
        wifiOnly = entry.wifiOnly,
    )
}

/**
 * Flush all pending deferred downloads (e.g. on media item transition or when video mux reaches steady state).
 */
fun flushAllPendingSongDownloads(context: Context) {
    val ids = PendingDeferredDownloads.pendingIds.value
    for (id in ids) {
        flushPendingSongDownload(context, id)
    }
}

/** @deprecated Use [flushPendingSongDownload]. Kept name as thin alias for call-site grep. */
fun flushPendingVideoCompanionDownload(context: Context, songId: String?) =
    flushPendingSongDownload(context, songId)

/** Remove audio and, for video songs, the companion video download. */
fun removeSongDownloads(
    context: Context,
    songId: String,
    isVideoSong: Boolean,
) {
    PendingDeferredDownloads.take(songId)
    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
    if (isVideoSong) {
        DownloadService.sendRemoveDownload(
            context,
            ExoDownloadService::class.java,
            videoDownloadMediaId(songId),
            false,
        )
    }
}
