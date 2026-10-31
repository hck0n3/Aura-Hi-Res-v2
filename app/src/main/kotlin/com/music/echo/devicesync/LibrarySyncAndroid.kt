package iad1tya.echo.music.devicesync

import android.content.Context
import androidx.room.InvalidationTracker
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.dislike.DislikeStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import iad1tya.echo.music.constants.LibraryPinsKey
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Row 536 — builds the [LibrarySync] of the app: the real database, the dislike store, a file ledger and a preferences number. */
object LibrarySyncAndroid {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun create(
        context: Context,
        database: MusicDatabase,
        dislikes: DislikeStore,
        dislikeFlow: Flow<*>,
        scope: CoroutineScope,
        sendMessage: (JsonObject) -> Boolean,
        isEnabled: () -> Boolean,
    ): LibrarySync {
        val prefs = context.getSharedPreferences("device_sync_state", Context.MODE_PRIVATE)
        return LibrarySync(
            adapter = LibrarySyncAdapter(database, dislikes, LibraryPinsBackend(context, database)),
            store = LibraryLedgerStore(File(context.filesDir, "device_sync/library_ledger.tsv")),
            sinceStore = object : SinceStore {
                override fun get() = prefs.getLong("libSince", 0L)
                override fun put(value: Long) {
                    prefs.edit().putLong("libSince", value).apply()
                }
            },
            scope = scope,
            sendMessage = sendMessage,
            isEnabled = isEnabled,
            watchChanges = { kick ->
                // Likes, playlists and their songs live in the database; whoever changes them, this fires.
                val observer = object : InvalidationTracker.Observer("song", "playlist", "playlist_song_map", "speed_dial_item") {
                    override fun onInvalidated(tables: Set<String>) {
                        kick(false)
                    }
                }
                runCatching { database.invalidationTracker.addObserver(observer) }
                // The dislike sets live in a preferences file, not in the database: watch them separately.
                val job = scope.launch { dislikeFlow.drop(1).debounce(1_000L).collect { kick(true) } }
                // Biblioteca ▸ Fijados is one preference too (row 584).
                val pinsJob = scope.launch {
                    context.dataStore.data.map { it[LibraryPinsKey] }.distinctUntilChanged().drop(1).debounce(1_000L).collect { kick(true) }
                }
                val stop: () -> Unit = {
                    runCatching { database.invalidationTracker.removeObserver(observer) }
                    job.cancel()
                    pinsJob.cancel()
                }
                stop
            },
        )
    }
}
