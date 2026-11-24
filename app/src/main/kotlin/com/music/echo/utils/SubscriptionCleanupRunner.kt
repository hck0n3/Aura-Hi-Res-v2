package iad1tya.echo.music.utils

import android.content.Context
import com.music.innertube.YouTube
import com.music.innertube.utils.parseCookieString
import dagger.hilt.android.qualifiers.ApplicationContext
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Candidate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Registry row 838 — the app-scoped owner of the «Revisar suscripciones» removal (row 813). The owner (2026-11-24): «quiero
 * que continúe y si uno vuelve a esa pantalla que muestre su progreso actual». The run used to live in the screen's
 * ViewModel, so leaving the screen ended it. Here it runs in its own [SupervisorJob] scope ([SubscriptionCleanupEngine]),
 * kept alive in the background by [SubscriptionCleanupWorker] (a foreground job that exists only while a run is active),
 * and the screen only observes [state].
 *
 * Every removal goes through the manual unfollow tap ([toggleArtistFollow]: the per-channel queue of rows 711/716, the
 * `unfollowedByUserAt` marker, `confirmArtistUnsubscribed`, the retry); it never calls the account itself. Counted as
 * removed only once the account confirmed it (row 798). Logs carry counts only (regla 4).
 */
@Singleton
class SubscriptionCleanupRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val libraryUploadSync: LibraryUploadSync,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val engine = SubscriptionCleanupEngine(
        scope = scope,
        accountTag = ::accountTag,
        pause = { delay(it) },
        log = { Timber.i(it) },
    )

    val state: StateFlow<SubscriptionCleanupEngine.State> = engine.state

    /** Unfollows written but not confirmed by the account: the existing retry owns them (see [retryFailed]). */
    private val awaitingAccount = ConcurrentHashMap.newKeySet<String>()

    /** The confirmed selection, in order. False (nothing starts) while another run is active. */
    fun start(items: List<Candidate>): Boolean {
        val byId = items.associateBy { it.id }
        val started = engine.start(plan = items.map { it.id }, unsubscribe = { id -> unsubscribeOne(byId.getValue(id)) })
        if (started) SubscriptionCleanupWorker.enqueue(context)
        return started
    }

    /**
     * «Reintentar las fallidas». An unfollow already written but not confirmed is retried by the pass a failed manual
     * unfollow already relies on (`AccountPush.runArtistPass` → [LibraryUploadSync.runUploadPass], whose
     * `flushPendingUnsubscribes` runs with or without the upload switch); one that was never written goes through the
     * manual tap again.
     */
    fun retryFailed(failed: List<String>, byId: Map<String, Candidate>): Boolean {
        val written = failed.filter { it in awaitingAccount }
        val notWritten = failed.filterNot { it in awaitingAccount }.mapNotNull { byId[it] }
        val rewrite = notWritten.associateBy { it.id }
        val started = engine.start(
            plan = notWritten.map { it.id },
            unsubscribe = { id -> unsubscribeOne(rewrite.getValue(id)) },
            carried = written,
            settleCarried = { carried -> settleWritten(carried, notWritten.size) },
        )
        if (started) SubscriptionCleanupWorker.enqueue(context)
        return started
    }

    /** «Detener», from the screen or from the notification. */
    fun stop() = engine.stop()

    private suspend fun settleWritten(written: List<String>, rewrite: Int): List<String> {
        var confirmed = emptyList<String>()
        try {
            if (written.isNotEmpty()) {
                libraryUploadSync.runUploadPass()
                confirmed = written.filter { id ->
                    database.getArtistEntitiesByIds(ArtistSyncPolicy.aliasIds(id)).none(ArtistSyncPolicy::hasPendingUnfollow)
                }
                awaitingAccount.removeAll(confirmed.toSet())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("SUB_CLEANUP_RETRY pass failed: %s", e.javaClass.simpleName)
        }
        Timber.i("SUB_CLEANUP_RETRY written=%d confirmed=%d rewrite=%d", written.size, confirmed.size, rewrite)
        return confirmed
    }

    /**
     * One selected subscription through the manual unfollow tap. A subscription the account lists but this device has
     * no row for gets the row the artist down-sync would give it (`SyncUtils.insertArtistsPage`), and the account's
     * list is stamped with the read-back every sync runs (`markArtistsSubscribedOnYtm`), so the tap reads it as
     * «Suscrito» and unfollows it. True only once the account confirmed it (`confirmArtistUnsubscribed` cleared the
     * pending shape of every spelling).
     */
    private suspend fun unsubscribeOne(item: Candidate): Boolean {
        val now = LocalDateTime.now()
        if (database.getArtistEntitiesByIds(listOf(item.id)).isEmpty()) {
            database.insert(
                ArtistEntity(
                    id = item.id,
                    name = item.name,
                    thumbnailUrl = item.thumbnailUrl,
                    channelId = item.channelId,
                    bookmarkedAt = now,
                    followedByUserAt = now,
                ),
            )
        }
        // Row 829: for a resend the read-back takes the listing as a follow again, so the tap below sends the unfollow again.
        database.markArtistsSubscribedOnYtm(listOf(item.id), now, PhantomSubscriptionPolicy.readBackHoldSince(item, now))
        if (!database.toggleArtistFollow(item.id, fallback = null, shownFollowed = true)) {
            // No row reads «Suscrito» because the user's own unfollow of this channel is still owed to the account
            // (the read-back above re-armed it): the uploader sends it, so «Reintentar» waits for that pass.
            val owed = database.getArtistEntitiesByIds(ArtistSyncPolicy.aliasIds(item.id)).any { ArtistSyncPolicy.mayUnsubscribe(it) }
            if (owed) awaitingAccount.add(item.id)
            return false
        }
        awaitingAccount.add(item.id)
        // «Detener» does not cut this wait short: the unfollow is already committed and queued for the account, so
        // ending here would report a removal that goes through a moment later as failed.
        val confirmed = withTimeoutOrNull(PhantomSubscriptionPolicy.CONFIRM_TIMEOUT_MS) {
            database.artistEntitiesByIdsFlow(ArtistSyncPolicy.aliasIds(item.id))
                .first { rows -> rows.none(ArtistSyncPolicy::hasPendingUnfollow) }
            true
        } ?: false
        if (confirmed) awaitingAccount.remove(item.id)
        return confirmed
    }

    /** The signed-in account (SAPISID), or null when signed out. Compared, never logged. */
    private fun accountTag(): String? =
        runCatching { parseCookieString(YouTube.cookie.orEmpty())["SAPISID"] }.getOrNull()?.takeIf { it.isNotBlank() }
}
