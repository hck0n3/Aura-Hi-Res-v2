package iad1tya.echo.music.utils

import android.content.Context
import androidx.media3.common.util.UnstableApi
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.imageLoader
import iad1tya.echo.music.extensions.tryOrNull
import iad1tya.echo.music.ui.player.CanvasVideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Settings ▸ Almacenamiento ▸ «Borrar caché de imágenes».
 *
 * Owner 2026-10-20: «Borrar caché de imágenes no funciona». The old button `File.delete()`d the files of
 * Coil's disk cache directory BEHIND Coil's back. Coil's DiskLruCache keeps its byte count and its entry
 * table in memory (the journal), so after those deletes `diskCache.size` still returned the same number:
 * the bar/label next to the button never moved, and nothing else was cleared (the Coil MEMORY cache — up to
 * 40% of the app's RAM — kept every cover on screen, the animated-cover videos stayed, and no confirmation
 * was shown). To the owner it looked like a button that does nothing.
 *
 * What this does instead, in order, all off the main thread:
 *  1. Coil memory cache: `clear()`.
 *  2. Coil disk cache: through ITS OWN API (`clear()`, which updates the size and the journal). The covers of
 *     DOWNLOADED songs/albums are the one thing worth keeping — the download only prefetches them into this
 *     cache ([iad1tya.echo.music.playback.DownloadUtil]) and offline there is no way to fetch them again —
 *     so they are read through [DiskCache.openSnapshot] first and written back with [DiskCache.openEditor]
 *     afterwards (bounded by [PRESERVE_BUDGET_BYTES]).
 *  3. Animated covers ([CanvasVideoCache], `filesDir/canvas_video`): re-downloaded on demand.
 *  4. Rendered share cards (`cacheDir/images`, [iad1tya.echo.music.utils.ComposeToImage]).
 *
 * It never touches songs, downloads, the player cache, lyrics, playlist covers the user picked
 * (`filesDir`) or the colour cache ([iad1tya.echo.music.ui.newui.AuraBloomCache]: a few bytes in RAM, and
 * clearing it would make the playing track's tint fall back to the brand colour until the next track).
 */
@OptIn(ExperimentalCoilApi::class, UnstableApi::class)
object ImageCacheCleaner {

    /** Covers of downloaded songs kept across a clear: more than this is skipped, never loaded into RAM. */
    internal const val PRESERVE_BUDGET_BYTES = 48L * 1024 * 1024

    private const val CANVAS_DIR = "canvas_video"
    private const val SHARE_IMAGES_DIR = "images"

    /** Bytes the clear released: never negative even if a loader wrote while we were clearing. */
    internal fun freedBytes(before: Long, after: Long): Long = (before - after).coerceAtLeast(0L)

    /**
     * How many of [sizes] (in priority order) fit in [budget] when taken from the front; an entry that does not
     * fit is skipped, not a reason to stop (a later small cover can still fit). Returns the kept indices.
     */
    internal fun indicesWithinBudget(sizes: List<Long>, budget: Long): List<Int> {
        var used = 0L
        val kept = ArrayList<Int>()
        sizes.forEachIndexed { i, size ->
            if (size >= 0 && used + size <= budget) {
                used += size
                kept += i
            }
        }
        return kept
    }

    /** Disk bytes the image caches hold right now (what the Storage screen can honestly show as freed). */
    fun diskBytes(context: Context): Long =
        (context.imageLoader.diskCache?.size ?: 0L) + canvasBytes(context) + shareImagesBytes(context)

    /**
     * Clears every image cache and returns the bytes released from disk. [preserveUrls] are the image URLs
     * (== Coil disk-cache keys) that must survive, i.e. the covers of downloaded music.
     */
    suspend fun clearAll(context: Context, preserveUrls: Collection<String>): Long =
        withContext(Dispatchers.IO) {
            val loader = context.imageLoader
            val before = diskBytes(context)

            tryOrNull { loader.memoryCache?.clear() }

            loader.diskCache?.let { disk ->
                val kept = tryOrNull { snapshotPreserved(disk, preserveUrls) }.orEmpty()
                // The supported API: it drops the entries, rewrites the journal and zeroes `size`.
                disk.clear()
                tryOrNull { restorePreserved(disk, kept) }
            }

            tryOrNull { clearCanvas(context) }
            tryOrNull { clearShareImages(context) }

            freedBytes(before, diskBytes(context))
        }

    private class Kept(val key: String, val metadata: ByteArray, val data: ByteArray)

    private fun snapshotPreserved(disk: DiskCache, urls: Collection<String>): List<Kept> {
        val fs = disk.fileSystem
        val candidates = ArrayList<Kept>()
        val sizes = ArrayList<Long>()
        for (url in urls.distinct()) {
            val snapshot = disk.openSnapshot(url) ?: continue
            try {
                val dataSize = fs.metadata(snapshot.data).size ?: continue
                if (dataSize > PRESERVE_BUDGET_BYTES) continue
                val metadata = fs.read(snapshot.metadata) { readByteArray() }
                val data = fs.read(snapshot.data) { readByteArray() }
                candidates += Kept(url, metadata, data)
                sizes += (metadata.size + data.size).toLong()
            } catch (_: Throwable) {
                // An unreadable entry is simply not preserved; it is cleared with the rest.
            } finally {
                snapshot.close()
            }
        }
        return indicesWithinBudget(sizes, PRESERVE_BUDGET_BYTES).map { candidates[it] }
    }

    private fun restorePreserved(disk: DiskCache, kept: List<Kept>) {
        val fs = disk.fileSystem
        for (entry in kept) {
            val editor = disk.openEditor(entry.key) ?: continue
            try {
                fs.write(editor.metadata) { write(entry.metadata) }
                fs.write(editor.data) { write(entry.data) }
                editor.commit()
            } catch (_: Throwable) {
                tryOrNull { editor.abort() }
            }
        }
    }

    private fun canvasDir(context: Context) = File(context.filesDir, CANVAS_DIR)

    private fun canvasBytes(context: Context): Long {
        // Do not create the SimpleCache just to measure it.
        if (!canvasDir(context).exists()) return 0L
        return tryOrNull { CanvasVideoCache.get(context).cacheSpace } ?: 0L
    }

    private fun clearCanvas(context: Context) {
        if (!canvasDir(context).exists()) return
        val cache = CanvasVideoCache.get(context)
        // Same removal the song-cache button uses: through the cache, so its index and size stay true.
        cache.keys.toList().forEach { key -> tryOrNull { cache.removeResource(key) } }
    }

    private fun shareImagesDir(context: Context) = File(context.cacheDir, SHARE_IMAGES_DIR)

    private fun shareImagesBytes(context: Context): Long =
        tryOrNull { shareImagesDir(context).listFiles()?.sumOf { if (it.isFile) it.length() else 0L } } ?: 0L

    private fun clearShareImages(context: Context) {
        shareImagesDir(context).listFiles()?.forEach { if (it.isFile) it.delete() }
    }
}
