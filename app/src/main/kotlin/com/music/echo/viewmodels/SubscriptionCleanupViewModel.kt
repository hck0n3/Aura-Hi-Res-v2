package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import iad1tya.echo.music.utils.ArtistSyncPolicy
import iad1tya.echo.music.utils.LibraryUploadSync
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.BatchResult
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Candidate
import iad1tya.echo.music.utils.toggleArtistFollow
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Registry row 813 — «Revisar suscripciones» (owner 2026-11-24). Lists the account's artist subscriptions, marks the
 * ones that look like the old auto-subscribe bug ([PhantomSubscriptionPolicy.verdict], row 814) and removes ONLY what
 * the user selected and confirmed, one at a time, through the manual unfollow tap ([toggleArtistFollow]: the per-channel
 * account-call queue of rows 711/716, the `unfollowedByUserAt` marker, `confirmArtistUnsubscribed`, the retry). It never
 * calls the account itself. Nothing runs on its own: the list is read once when the screen opens, the batch starts only
 * from the confirmation dialog. Logs carry counts only (regla 4).
 */
@HiltViewModel
class SubscriptionCleanupViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val libraryUploadSync: LibraryUploadSync,
) : ViewModel() {

    enum class Phase { LOADING, SIGNED_OUT, LOAD_FAILED, READY, RUNNING }

    data class UiState(
        val phase: Phase = Phase.LOADING,
        val candidates: List<Candidate> = emptyList(),
        val selected: Set<String> = emptySet(),
        val total: Int = 0,
        val done: Int = 0,
        val removed: Int = 0,
        val failed: Int = 0,
        val stopping: Boolean = false,
        val lastResult: BatchResult? = null,
        /** Row 837: the read stopped before the end of the account's list (failed page or [MAX_PAGES]); [readCount] it got. */
        val incomplete: Boolean = false,
        val readCount: Int = 0,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private val stopRequested = MutableStateFlow(false)

    /** Unfollows written but not confirmed by the account: the existing retry owns them (see [retryFailed]). */
    private val awaitingAccount = ConcurrentHashMap.newKeySet<String>()

    init {
        load()
    }

    fun load() {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.value = UiState(phase = Phase.LOADING)
            if (accountTag() == null) {
                _state.value = UiState(phase = Phase.SIGNED_OUT)
                return@launch
            }
            try {
                // The same read LibraryUploadSync.fetchRemoteSubscriptionIds and the artist down-sync use, paged to the end
                // (row 837: no 50-page cap here; the screen says so when the read stops early).
                val page = YouTube.library("FEmusic_library_corpus_artists").completed(maxRequests = MAX_PAGES).getOrThrow()
                val incomplete = page.continuation != null
                val remote = page.items.filterIsInstance<ArtistItem>().distinctBy { ArtistSyncPolicy.channelKey(it.id) }
                val interest = database.artistInterestRows()
                val local = remote.flatMap { ArtistSyncPolicy.aliasIds(it.id) }.distinct()
                    .chunked(SQL_IN_CHUNK).flatMap { database.getArtistEntitiesByIds(it) }
                val now = LocalDateTime.now()
                val candidates = remote.map { item ->
                    val spellings = ArtistSyncPolicy.aliasIds(item.id).toSet()
                    Candidate(
                        id = item.id,
                        name = item.title,
                        thumbnailUrl = item.thumbnail,
                        channelId = item.channelId,
                        verdict = PhantomSubscriptionPolicy.verdict(
                            PhantomSubscriptionPolicy.interestFor(item.id, item.title, interest),
                        ),
                        removalPending = PhantomSubscriptionPolicy.removalPending(local.filter { it.id in spellings }),
                        removalConfirmed = PhantomSubscriptionPolicy.removalConfirmed(
                            item.id,
                            local.filter { it.id in spellings },
                            now,
                        ),
                        resendAllowed = PhantomSubscriptionPolicy.resendAllowed(item.id, local.filter { it.id in spellings }, now),
                    )
                }
                Timber.i(
                    "SUB_CLEANUP_REVIEW total=%d suggested=%d pending=%d confirmed=%d resend=%d incomplete=%b",
                    candidates.size,
                    candidates.count { it.verdict.suggested },
                    candidates.count { it.removalPending },
                    candidates.count { it.removalConfirmed },
                    candidates.count { it.resendAllowed },
                    incomplete,
                )
                _state.value = UiState(
                    phase = Phase.READY,
                    candidates = PhantomSubscriptionPolicy.screenOrder(candidates),
                    incomplete = incomplete,
                    readCount = candidates.size,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("SUB_CLEANUP_REVIEW failed: %s", e.javaClass.simpleName)
                _state.value = UiState(phase = Phase.LOAD_FAILED)
            }
        }
    }

    fun toggleSelected(id: String) = _state.update { s ->
        if (s.phase != Phase.READY || s.candidates.none { it.id == id && !it.blocked }) return@update s
        s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
    }

    fun selectSuggested() = _state.update { s ->
        if (s.phase != Phase.READY) s else s.copy(selected = PhantomSubscriptionPolicy.suggestedSelection(s.candidates))
    }

    fun clearSelection() = _state.update { s -> if (s.phase != Phase.READY) s else s.copy(selected = emptySet()) }

    /** The confirmation dialog's «Quitar». The ONLY way a batch starts. */
    fun removeSelected() {
        val s = _state.value
        if (s.phase != Phase.READY || job?.isActive == true) return
        val plan = PhantomSubscriptionPolicy.planBatch(
            ordered = s.candidates.map { it.id },
            selected = s.selected,
            blocked = s.candidates.filter { it.blocked }.map { it.id }.toSet(),
        )
        if (plan.isEmpty()) return
        val byId = s.candidates.associateBy { it.id }
        runCleanup(plan.mapNotNull { byId[it] }, alreadyResolved = emptyList(), stillFailed = emptyList())
    }

    /**
     * «Reintentar las fallidas». An unfollow already written but not confirmed is retried by the pass a failed manual
     * unfollow already relies on (`AccountPush.runArtistPass` → [LibraryUploadSync.runUploadPass], whose
     * `flushPendingUnsubscribes` runs with or without the upload switch); one that was never written goes through the
     * manual tap again.
     */
    fun retryFailed() {
        val s = _state.value
        val failed = s.lastResult?.failed.orEmpty()
        if (s.phase != Phase.READY || failed.isEmpty() || job?.isActive == true) return
        val byId = s.candidates.associateBy { it.id }
        val written = failed.filter { it in awaitingAccount }
        val notWritten = failed.filterNot { it in awaitingAccount }.mapNotNull { byId[it] }
        stopRequested.value = false
        _state.update { it.copy(phase = Phase.RUNNING, total = failed.size, done = 0, removed = 0, failed = 0, stopping = false) }
        job = viewModelScope.launch(Dispatchers.IO) {
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
            Timber.i("SUB_CLEANUP_RETRY written=%d confirmed=%d rewrite=%d", written.size, confirmed.size, notWritten.size)
            runCleanupInline(notWritten, alreadyResolved = confirmed, stillFailed = written - confirmed.toSet())
        }
    }

    /** «Detener»: the item in flight finishes its (bounded) wait for the account, the rest is not attempted. */
    fun stop() {
        stopRequested.value = true
        _state.update { if (it.phase == Phase.RUNNING) it.copy(stopping = true) else it }
    }

    private fun runCleanup(items: List<Candidate>, alreadyResolved: List<String>, stillFailed: List<String>) {
        stopRequested.value = false
        _state.update { it.copy(phase = Phase.RUNNING, total = items.size, done = 0, removed = 0, failed = 0, stopping = false) }
        job = viewModelScope.launch(Dispatchers.IO) { runCleanupInline(items, alreadyResolved, stillFailed) }
    }

    private suspend fun runCleanupInline(items: List<Candidate>, alreadyResolved: List<String>, stillFailed: List<String>) {
        val account = accountTag()
        val byId = items.associateBy { it.id }
        Timber.i("SUB_CLEANUP_START count=%d", items.size)
        val batch = PhantomSubscriptionPolicy.runBatch(
            plan = items.map { it.id },
            spacingMs = PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS,
            unsubscribe = { id -> unsubscribeOne(byId.getValue(id)) },
            pause = { delay(it) },
            // A sign-out or another account stops the batch: the list belongs to the account it was read from.
            shouldStop = { stopRequested.value || account == null || accountTag() != account },
            onProgress = { done, removed, failed ->
                _state.update { it.copy(done = done + alreadyResolved.size + stillFailed.size, removed = removed + alreadyResolved.size, failed = failed + stillFailed.size) }
            },
        )
        val result = BatchResult(
            removed = alreadyResolved + batch.removed,
            failed = stillFailed + batch.failed,
            notAttempted = batch.notAttempted,
            cancelled = batch.cancelled,
        )
        Timber.i(
            "SUB_CLEANUP_DONE removed=%d failed=%d not_attempted=%d stopped=%b",
            result.removed.size, result.failed.size, result.notAttempted.size, result.cancelled,
        )
        val gone = result.removed.toSet()
        _state.update { s ->
            s.copy(
                phase = Phase.READY,
                candidates = s.candidates.filterNot { it.id in gone },
                selected = s.selected - gone,
                stopping = false,
                lastResult = result,
            )
        }
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

    private companion object {
        const val SQL_IN_CHUNK = 500

        /** Row 837: upper bound of continuation pages for one read; reaching it shows the «incomplete» notice. */
        const val MAX_PAGES = 400
    }
}
