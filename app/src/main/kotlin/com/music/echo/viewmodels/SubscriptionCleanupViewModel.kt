package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.ArtistItem
import com.music.innertube.utils.completed
import com.music.innertube.utils.parseCookieString
import dagger.hilt.android.lifecycle.HiltViewModel
import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.utils.ArtistSyncPolicy
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.BatchResult
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Candidate
import iad1tya.echo.music.utils.SubscriptionCleanupEngine
import iad1tya.echo.music.utils.SubscriptionCleanupRunner
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Registry row 813 — «Revisar suscripciones» (owner 2026-11-24). Lists the account's artist subscriptions, marks the
 * ones that look like the old auto-subscribe bug ([PhantomSubscriptionPolicy.verdict], row 814) and hands ONLY what
 * the user selected and confirmed to [SubscriptionCleanupRunner], which removes them one at a time through the manual
 * unfollow tap. Row 838: the run belongs to the runner (app-scoped), not to this ViewModel, so it goes on when the
 * screen is left; this ViewModel only observes it. Nothing runs on its own: the list is read once when the screen
 * opens, a batch starts only from the confirmation dialog. Logs carry counts only (regla 4).
 */
@HiltViewModel
class SubscriptionCleanupViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val runner: SubscriptionCleanupRunner,
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
        /** Row 838: rows of the current (or last) run the account confirmed / that failed; the screen marks them. */
        val removedIds: Set<String> = emptySet(),
        val failedIds: Set<String> = emptySet(),
        /** Row 838: where the list read is, also while [phase] says RUNNING. */
        val listPhase: Phase = Phase.LOADING,
        /** Row 838: a new run was asked for while one was active; refused, and the screen says so. */
        val refused: Boolean = false,
        /** Row 839: a run of this account a process death cut short: «Se detuvo: X de N; toca Continuar». */
        val interrupted: SubscriptionCleanupEngine.SavedRun? = null,
    )

    /** The list as read from the account (and the selection); the run's part comes from [runner]. */
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = combine(_state, runner.state) { list, run -> merge(list, run) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, merge(_state.value, runner.state.value))

    private var job: Job? = null

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
        val s = state.value
        if (refuseWhileRunning(s) || s.phase != Phase.READY) return
        val plan = PhantomSubscriptionPolicy.planBatch(
            ordered = s.candidates.map { it.id },
            selected = s.selected,
            blocked = s.candidates.filter { it.blocked }.map { it.id }.toSet(),
        )
        if (plan.isEmpty()) return
        val byId = s.candidates.associateBy { it.id }
        absorbLastRun()
        refuseUnlessStarted(runner.start(plan.mapNotNull { byId[it] }))
    }

    /** «Reintentar las fallidas»: see [SubscriptionCleanupRunner.retryFailed]. */
    fun retryFailed() {
        val s = state.value
        if (refuseWhileRunning(s)) return
        val failed = s.lastResult?.failed.orEmpty()
        if (s.phase != Phase.READY || failed.isEmpty()) return
        absorbLastRun()
        refuseUnlessStarted(runner.retryFailed(failed, s.candidates.associateBy { it.id }))
    }

    /** «Detener»: the item in flight finishes its (bounded) wait for the account, the rest is not attempted. */
    fun stop() = runner.stop()

    /**
     * Row 839: «Continuar» on the «Se detuvo» notice. The ids the cut-short run still had pending (the user's own
     * confirmed selection), re-planned against the list just read: a channel already removed or pending is blocked
     * there and skipped, one the account no longer lists is not in it.
     */
    fun continueInterrupted() {
        val s = state.value
        val saved = s.interrupted ?: return
        if (refuseWhileRunning(s) || s.phase != Phase.READY) return
        val plan = PhantomSubscriptionPolicy.planBatch(
            ordered = s.candidates.map { it.id },
            selected = saved.pending.toSet(),
            blocked = s.candidates.filter { it.blocked }.map { it.id }.toSet(),
        )
        Timber.i("SUB_CLEANUP_CONTINUE pending=%d planned=%d", saved.pending.size, plan.size)
        if (plan.isEmpty()) {
            runner.discardInterrupted()
            return
        }
        val byId = s.candidates.associateBy { it.id }
        absorbLastRun()
        refuseUnlessStarted(runner.start(plan.mapNotNull { byId[it] }))
    }

    /** Row 839: «Descartar» on the «Se detuvo» notice. */
    fun discardInterrupted() = runner.discardInterrupted()

    private fun refuseWhileRunning(s: UiState): Boolean {
        if (s.phase != Phase.RUNNING) return false
        _state.update { it.copy(refused = true) }
        return true
    }

    private fun refuseUnlessStarted(started: Boolean) = _state.update { it.copy(refused = !started) }

    /**
     * Row 838: what the account confirmed in the run that ended leaves this list (and the selection) for good, right
     * before another run starts and wipes that run's summary. [merge] only hides the last run's ids.
     */
    private fun absorbLastRun() {
        val gone = SubscriptionCleanupEngine.removedBy(runner.state.value)
        if (gone.isEmpty()) return
        _state.update { s -> s.copy(candidates = s.candidates.filterNot { it.id in gone }, selected = s.selected - gone) }
    }

    /**
     * The list joined with the run: RUNNING while the runner is active, its counters and marks, and — once it ended —
     * its summary, with what the account confirmed taken out of the list (and of the selection).
     */
    private fun merge(list: UiState, run: SubscriptionCleanupEngine.State): UiState {
        val gone = SubscriptionCleanupEngine.removedBy(run)
        return list.copy(
            phase = if (run.active) Phase.RUNNING else list.phase,
            candidates = list.candidates.filterNot { it.id in gone },
            selected = list.selected - gone,
            total = run.total,
            done = run.done,
            removed = run.removed,
            failed = run.failed,
            stopping = run.stopping,
            lastResult = run.lastResult,
            removedIds = run.removedIds,
            failedIds = run.failedIds,
            listPhase = list.phase,
            refused = list.refused && run.active,
            interrupted = run.interrupted?.takeIf { !run.active && runner.isCurrentAccount(it) },
        )
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
