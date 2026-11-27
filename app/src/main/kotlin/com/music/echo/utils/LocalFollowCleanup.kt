package iad1tya.echo.music.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.constants.ImportFollowsAwaitingConfirmKey
import iad1tya.echo.music.constants.ImportFollowsLocalOnlyKey
import iad1tya.echo.music.constants.ImportFollowsWrittenByImportKey
import iad1tya.echo.music.db.MusicDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Registry row 905 — the Android half of [LocalFollowCleanupPolicy]: reads the import lists and the rows, clears the
 * selected local-only follows and keeps what «Deshacer» needs. App-scoped, so the undo survives leaving the screen
 * (not a process death: it is a convenience of the moment, the removal itself is a plain local edit).
 *
 * Nothing here talks to the account, and nothing runs on its own: [read] when the screen opens, [remove] and [undo]
 * from their buttons. Logs carry counts only (AGENTS rule 4).
 */
@Singleton
class LocalFollowCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val libraryUploadSync: LibraryUploadSync,
) {
    data class Lists(val awaiting: Set<String>, val localOnly: Set<String>)

    /** What «Deshacer» puts back: the [channels] the last removal cleared and their [rows] as they were. */
    data class Undo(val channels: Int = 0, val rows: List<LocalFollowCleanupPolicy.Snapshot> = emptyList())

    private val lock = Mutex()

    private val _undo = MutableStateFlow(Undo())

    /** The last removal (empty once undone, or when nothing was removed). Kept here, with the count, so both outlive the screen. */
    val undo: StateFlow<Undo> = _undo.asStateFlow()

    /**
     * The two import lists of the account signed in now. First through [LibraryUploadSync.importFollowsHeldBack], the
     * same call the upload pass makes: it adopts the lists of the current account (row 808) and, the first time after an
     * install or a restore, files the pending follows as awaiting (row 646 (a)). `awaiting` keeps only the ids an import
     * itself wrote ([LocalFollowCleanupPolicy.importedAwaiting]). Null when the preferences cannot be read: the screen then
     * offers nothing.
     */
    suspend fun lists(): Lists? = try {
        libraryUploadSync.importFollowsHeldBack()
        val prefs = context.dataStore.data.first()
        Lists(
            LocalFollowCleanupPolicy.importedAwaiting(
                prefs[ImportFollowsAwaitingConfirmKey].orEmpty(),
                prefs[ImportFollowsWrittenByImportKey].orEmpty(),
            ),
            prefs[ImportFollowsLocalOnlyKey].orEmpty(),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("LOCAL_FOLLOW_CLEANUP lists failed: %s", e.javaClass.simpleName)
        null
    }

    /** The screen's list (see [LocalFollowCleanupPolicy.candidates]). Null on any read failure. */
    suspend fun candidates(): List<LocalFollowCleanupPolicy.Candidate>? {
        val lists = lists() ?: return null
        return try {
            val ids = lists.awaiting.flatMap { ArtistSyncPolicy.aliasIds(it) }.distinct()
            val rows = ids.chunked(SQL_IN_CHUNK).flatMap { database.getArtistEntitiesByIds(it) }
            val found = LocalFollowCleanupPolicy.candidates(rows, lists.awaiting, lists.localOnly, database.artistInterestRows())
            Timber.i(
                "LOCAL_FOLLOW_CLEANUP review awaiting=%d candidates=%d suggested=%d",
                lists.awaiting.size, found.size, found.count { it.verdict.suggested },
            )
            found
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("LOCAL_FOLLOW_CLEANUP review failed: %s", e.javaClass.simpleName)
            null
        }
    }

    /**
     * Clears the follows of [plan] (the user's confirmed selection) in ONE transaction, re-checking each channel on the
     * rows and lists read right before the write. Returns how many channels were cleared; they become [undo]. Null when
     * nothing could be written (the lists or the transaction failed): the previous [undo] stays as it was.
     */
    suspend fun remove(plan: List<LocalFollowCleanupPolicy.Candidate>): Int? = lock.withLock {
        if (plan.isEmpty()) return@withLock 0
        val lists = lists() ?: return@withLock null
        val snapshots = ArrayList<LocalFollowCleanupPolicy.Snapshot>()
        var cleared = 0
        try {
            database.withTransaction {
                for (candidate in plan) {
                    val spellings = artistEntitiesByIdsInTransaction(ArtistSyncPolicy.aliasIds(candidate.id))
                    val changed = spellings.mapNotNull { row ->
                        LocalFollowCleanupPolicy.cleared(row, spellings, lists.awaiting, lists.localOnly)?.let { row to it }
                    }
                    if (changed.isEmpty()) continue
                    changed.forEach { (before, after) ->
                        snapshots.add(LocalFollowCleanupPolicy.snapshotOf(before))
                        update(after)
                    }
                    cleared++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("LOCAL_FOLLOW_CLEANUP remove failed: %s", e.javaClass.simpleName)
            return@withLock null
        }
        // A removal that changed nothing keeps the previous «Deshacer».
        if (snapshots.isNotEmpty()) _undo.value = Undo(cleared, snapshots)
        Timber.i("LOCAL_FOLLOW_CLEANUP removed=%d skipped=%d rows=%d", cleared, plan.size - cleared, snapshots.size)
        cleared
    }

    /** «Deshacer»: puts back the rows of the last removal that are still as it left them. Returns how many rows. */
    suspend fun undo(): Int = lock.withLock {
        val snapshots = _undo.value.rows
        if (snapshots.isEmpty()) return@withLock 0
        var restored = 0
        try {
            database.withTransaction {
                val current = snapshots.map { it.id }.chunked(SQL_IN_CHUNK)
                    .flatMap { artistEntitiesByIdsInTransaction(it) }.associateBy { it.id }
                for (snapshot in snapshots) {
                    val row = current[snapshot.id] ?: continue
                    val back = LocalFollowCleanupPolicy.restored(row, snapshot) ?: continue
                    update(back)
                    restored++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("LOCAL_FOLLOW_CLEANUP undo failed: %s", e.javaClass.simpleName)
            return@withLock 0
        }
        _undo.value = Undo()
        Timber.i("LOCAL_FOLLOW_CLEANUP undone rows=%d of=%d", restored, snapshots.size)
        restored
    }

    private companion object {
        const val SQL_IN_CHUNK = 500
    }
}
