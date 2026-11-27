package iad1tya.echo.music.devicesync

import android.content.Context
import androidx.room.InvalidationTracker
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.dislike.DislikeStore
import java.io.File
import java.security.MessageDigest
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
        groupId: String,
    ): LibrarySync {
        val prefs = context.getSharedPreferences("device_sync_state", Context.MODE_PRIVATE)
        val room = MessageDigest.getInstance("SHA-256").digest(groupId.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        val ledgerFile = File(context.filesDir, "device_sync/$room/ledger.tsv")
        val sinceKey = "libSince_$room"
        val legacy = File(context.filesDir, "device_sync/library_ledger.tsv")
        if (!prefs.contains(sinceKey) && (legacy.isFile || prefs.contains("libSince"))) {
            if (legacy.isFile && !ledgerFile.exists()) {
                ledgerFile.parentFile?.mkdirs()
                legacy.renameTo(ledgerFile)
            }
            prefs.edit().putLong(sinceKey, prefs.getLong("libSince", 0L)).remove("libSince").commit()
        }
        val fresh = File(context.filesDir, FRESH_START)
        if (fresh.isFile) {
            ledgerFile.delete()
            prefs.edit().remove(sinceKey).commit()
        }
        return LibrarySync(
            adapter = LibrarySyncAdapter(database, dislikes, LibraryPinsBackend(context, database)),
            store = LibraryLedgerStore(ledgerFile),
            sinceStore = object : SinceStore {
                override fun get() = prefs.getLong(sinceKey, 0L)
                override fun put(value: Long) {
                    prefs.edit().putLong(sinceKey, value).apply()
                }
                override fun freshStart() = fresh.isFile
                override fun endFreshStart() {
                    fresh.delete()
                }
            },
            scope = scope,
            sendMessage = sendMessage,
            isEnabled = isEnabled,
            // Row 900: the journal of changes seen being made before they can be dated against the room, next to the ledger; the
            // boot count and the time since boot say WHEN, whatever the phone clock says (-1 = the boot count is not readable).
            journal = LibraryJournalStore(ledgerFile.parentFile ?: File(context.filesDir, "device_sync/$room")),
            mono = run {
                val boot = runCatching {
                    android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1).toLong()
                }.getOrDefault(-1L)
                ({ Mono(boot, android.os.SystemClock.elapsedRealtime()) })
            },
            watchChanges = { kick ->
                // Likes, playlists and their songs live in the database; whoever changes them, this fires.
                val observer = object : InvalidationTracker.Observer("song", "playlist", "playlist_song_map", "speed_dial_item", "album") {
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

    /** A restored backup replaced the library under every room's ledger: forget them all, and let the next pull start fresh. */
    fun forgetAfterRestore(context: Context) {
        File(context.filesDir, "device_sync").walkBottomUp().filter { it.isFile }.forEach { it.delete() }
        File(context.filesDir, FRESH_START).apply { parentFile?.mkdirs() }.createNewFile()
        val prefs = context.getSharedPreferences("device_sync_state", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("libSince") }.forEach { edit.remove(it) }
        edit.commit()
    }

    private const val FRESH_START = "device_sync/fresh_start"
}
