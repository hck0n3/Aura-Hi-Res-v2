package iad1tya.echo.music.playback

import android.content.Context
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import iad1tya.echo.music.BuildConfig
import iad1tya.echo.music.db.MusicDatabase
import timber.log.Timber
import java.time.LocalDateTime

/** What the startup reconcile decided for the library's "downloaded" flag. */
internal data class OfflinePlan(
    /** Bytes complete on disk but the library does not list the song as downloaded: list it. */
    val markDownloaded: Set<String>,
    /** The library lists it as downloaded, yet there is NOTHING on disk and no download pending: stop claiming it. */
    val unmarkDownloaded: Set<String>,
)

/**
 * The library (Room `song.isDownloaded`) and the bytes (Media3's download cache) are two records of the same fact, kept in
 * step only by `onDownloadChanged` events. Nothing ever compared them afterwards, so any divergence — a failed re-add of a
 * song whose bytes were already complete, a cache that lost its index — stayed forever: songs listed as downloaded that do
 * not play offline, or songs that play offline but are missing from the "Descargadas" list (owner / users 2026-10-19: after
 * updating, the downloaded content "is lost"). This object compares them at startup and says honestly what it finds.
 *
 * Playback itself never reads the library flag (the resolver asks the cache), so reconciling is only about WHAT THE USER
 * IS TOLD — and about logging, with counts only (AGENTS.md rule 4), what an update did to the cache.
 */
internal object OfflineIntegrity {
    private const val TAG = "DOWNLOAD"
    private const val PREFS = "offline_audit"

    private val PENDING = setOf(
        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
        Download.STATE_REMOVING,
    )

    /**
     * True when every byte of [key] is on disk and its length is known — the SAME test the stream resolver uses to serve a
     * song with zero network, so "downloaded" here means "plays offline".
     */
    fun isFullyOnDisk(cache: SimpleCache, key: String): Boolean = runCatching {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        length > 0 && cache.isCached(key, 0, length)
    }.getOrDefault(false)

    /**
     * Pure decision. [anyBytes] = ids with at least one cached span (a partial download); [indexState] = Media3's download
     * state per id. A song is un-listed only when nothing at all is left AND Media3 has no record of it: a partial or a
     * pending download is still "trying", and a flag flip is reversible anyway ([markDownloaded] restores it next start).
     */
    fun plan(
        roomDownloaded: Set<String>,
        indexState: Map<String, Int>,
        bytesComplete: Set<String>,
        anyBytes: Set<String>,
    ): OfflinePlan {
        val mark = bytesComplete.filterTo(linkedSetOf()) { it !in roomDownloaded && indexState[it] !in PENDING }
        val unmark = roomDownloaded.filterTo(linkedSetOf()) { id ->
            id !in bytesComplete && id !in anyBytes && indexState[id] == null
        }
        return OfflinePlan(markDownloaded = mark, unmarkDownloaded = unmark)
    }

    /**
     * Runs the reconcile and the audit. Counts only in the log — no ids, titles or artists. Never throws.
     * `OFFLINE_AUDIT_DROP` is the line to look for in a shared log after an update: it says the cache had fewer complete
     * songs than before the update, which is exactly the "I lost my downloads" report.
     */
    fun run(
        context: Context,
        database: MusicDatabase,
        downloadCache: SimpleCache,
        playerCache: SimpleCache,
        downloads: Map<String, Download>,
    ) {
        runCatching {
            val audioKeys = downloadCache.keys.filterTo(HashSet()) { !isVideoDownloadId(it) }
            val bytesComplete = audioKeys.filterTo(HashSet()) { isFullyOnDisk(downloadCache, it) }
            val anyBytes = audioKeys.filterTo(HashSet()) { downloadCache.getCachedSpans(it).isNotEmpty() }
            val indexState = downloads.mapValues { it.value.state }
            val room = database.downloadedSongIdsOnce().toHashSet()
            val plan = plan(room, indexState, bytesComplete, anyBytes)

            val now = LocalDateTime.now()
            plan.markDownloaded.forEach { runCatching { database.markDownloadedKeepingDate(it, now) } }
            plan.unmarkDownloaded.forEach { runCatching { database.setDownloadedFlag(it, false) } }

            val indexCompleted = indexState.count { it.value == Download.STATE_COMPLETED }
            val indexMissingBytes = downloads.count { (id, d) ->
                d.state == Download.STATE_COMPLETED && !isVideoDownloadId(id) && id !in bytesComplete
            }
            val bytesMissingIndex = bytesComplete.count { indexState[it] != Download.STATE_COMPLETED }
            val playerComplete = playerCache.keys.count { key ->
                key.startsWith(StreamCacheKeys.PREFIX) && isFullyOnDisk(playerCache, key)
            }
            val mediaDb = context.getDatabasePath("exoplayer_internal.db")

            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val prevVersion = prefs.getInt("versionCode", -1)
            val prevComplete = prefs.getInt("cacheComplete", -1)
            val prevPlayer = prefs.getInt("playerComplete", -1)

            Timber.tag(TAG).i(
                "OFFLINE_AUDIT vc=%d prevVc=%d room=%d indexCompleted=%d cacheComplete=%d playerComplete=%d " +
                    "indexWithoutBytes=%d bytesWithoutIndex=%d listed(+%d,-%d) mediaDb=%b/%dB",
                BuildConfig.VERSION_CODE, prevVersion, room.size, indexCompleted, bytesComplete.size, playerComplete,
                indexMissingBytes, bytesMissingIndex, plan.markDownloaded.size, plan.unmarkDownloaded.size,
                mediaDb.exists(), mediaDb.length(),
            )
            val updated = prevVersion != -1 && prevVersion != BuildConfig.VERSION_CODE
            if (updated && (bytesComplete.size < prevComplete || playerComplete < prevPlayer)) {
                Timber.tag(TAG).w(
                    "OFFLINE_AUDIT_DROP after update %d->%d: cacheComplete %d->%d playerComplete %d->%d",
                    prevVersion, BuildConfig.VERSION_CODE, prevComplete, bytesComplete.size, prevPlayer, playerComplete,
                )
            }
            prefs.edit()
                .putInt("versionCode", BuildConfig.VERSION_CODE)
                .putInt("cacheComplete", bytesComplete.size)
                .putInt("playerComplete", playerComplete)
                .apply()
        }.onFailure { Timber.tag(TAG).w("OFFLINE_AUDIT failed: %s", iad1tya.echo.music.utils.privacySafeSummary(it)) }
    }
}
