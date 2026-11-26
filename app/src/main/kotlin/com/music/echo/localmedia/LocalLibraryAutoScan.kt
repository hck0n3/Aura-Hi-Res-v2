package iad1tya.echo.music.localmedia

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.LocalArtistNameExceptionsKey
import iad1tya.echo.music.constants.LocalArtistSeparatorsKey
import iad1tya.echo.music.constants.LocalSongsIncludedFoldersKey
import iad1tya.echo.music.constants.LocalSongsIncludedTreeUrisKey
import iad1tya.echo.music.constants.LocalSongsMinDurationSecondsKey
import iad1tya.echo.music.di.ApplicationScope
import iad1tya.echo.music.utils.dataStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Row 881 (plan, phase 4: «reescaneo que actualiza… escaneo incremental»): the local library follows the phone without
 * the user opening the scan sheet. Event-driven only (AGENTS rule 7): one scan when the app starts and one after
 * MediaStore reports a change to audio or video, both [DebounceMs] after the last event; never a timer or a poll.
 *
 * Only for someone who already uses local music (they scanned once or have local songs) and has the audio permission.
 * The automatic scan never deletes a song it does not see (see [LocalSongScanner.scanDevice] `automatic`); removing is
 * still the user's scan. On Android 11+ it does not even query MediaStore when its generation has not moved.
 */
@Singleton
class LocalLibraryAutoScan
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val scanner: LocalSongScanner,
    @ApplicationScope private val scope: CoroutineScope,
    private val cloudIndexer: iad1tya.echo.music.cloud.CloudLibraryIndexer,
) {
    private val started = AtomicBoolean(false)
    // replay = 1: the app-start request is not lost if it is emitted before the collector subscribes.
    private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** App start (default process) and after the user's own successful scan. Idempotent. */
    fun ensureStarted() {
        // Row 891: the cloud library's one automatic index per app start (only when stale, with folders chosen, on an
        // unmetered network: CloudLibraryIndexer.indexIfStale), independent of the phone's library.
        scope.launch(Dispatchers.IO) { cloudIndexer.indexIfStale() }
        if (started.get()) return
        scope.launch(Dispatchers.IO) {
            if (!runCatching { scanner.usesLocalLibrary() }.getOrDefault(false)) return@launch
            if (!started.compareAndSet(false, true)) return@launch
            startCollecting()
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    requests.tryEmit(Unit)
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
            }
            requests.tryEmit(Unit)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startCollecting() {
        scope.launch(Dispatchers.IO) {
            requests.debounce(DebounceMs).collect { scanOnce() }
        }
        // Row 880: a change to the artist separators or to the names never split rebuilds the local artists; an event
        // of the setting itself, not a poll (the remembered files are reused, none is reopened).
        scope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { prefs -> prefs[LocalArtistSeparatorsKey] to prefs[LocalArtistNameExceptionsKey] }
                .distinctUntilChanged()
                .drop(1)
                .collect { requests.tryEmit(Unit) }
        }
    }

    private suspend fun scanOnce() {
        try {
            val prefs = context.dataStore.data.first()
            val config = LocalSongScanConfig(
                minimumDurationSeconds = prefs[LocalSongsMinDurationSecondsKey] ?: 0,
                includedFolders = prefs[LocalSongsIncludedFoldersKey].orEmpty(),
                includedTreeUris = prefs[LocalSongsIncludedTreeUrisKey].orEmpty(),
                artistSplit = LocalArtistSplitRules.fromPreferences(prefs[LocalArtistSeparatorsKey], prefs[LocalArtistNameExceptionsKey]),
            )
            val summary = scanner.scanAutomatically(config) ?: return
            // Counts only (AGENTS rule 4): never a title, an artist, a file name or a path.
            Timber.i(
                "LOCAL_SCAN_AUTO songs=%d videos=%d removed=%d kept=%d",
                summary.scannedSongs, summary.scannedVideos, summary.removedSongs, summary.keptMissing,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A refused or failed scan deleted nothing (row 694); the class name says which without any user data.
            Timber.w("LOCAL_SCAN_AUTO not applied: %s", e.javaClass.simpleName)
        }
    }

    private companion object {
        /** Design value, not measured: long enough to fold a copy of a whole album into one scan. */
        const val DebounceMs = 30_000L
    }
}
