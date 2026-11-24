package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.BatchResult
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Registry row 838 — the «Revisar suscripciones» removal run (row 813), owned by the app instead of the screen: the
 * queue of selected ids, the counters, «Detener» and the summary live here, in [scope] (app-wide), so leaving the
 * screen or sending the app to the background no longer ends the run. The screen only observes [state].
 *
 * Pure (no Android): [SubscriptionCleanupRunner] gives it the account, the unfollow tap and the log. Nothing here
 * polls: the run is one coroutine that exists only between [start] and its summary.
 */
class SubscriptionCleanupEngine(
    private val scope: CoroutineScope,
    private val accountTag: () -> String?,
    private val pause: suspend (Long) -> Unit,
    private val log: (String) -> Unit = {},
) {
    data class State(
        val active: Boolean = false,
        val total: Int = 0,
        val done: Int = 0,
        val removed: Int = 0,
        val failed: Int = 0,
        val stopping: Boolean = false,
        /** Ids of the current (or last) run the account confirmed / that failed: the screen marks those rows. */
        val removedIds: Set<String> = emptySet(),
        val failedIds: Set<String> = emptySet(),
        val lastResult: BatchResult? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val stopRequested = MutableStateFlow(false)
    private val lock = Any()

    /**
     * Starts a run over [plan] (the ids to unfollow, in order). [carried] are ids handed straight to [settleCarried]
     * first (the retry's already-written unfollows); the ids it returns count as removed, the rest as failed.
     * False — and nothing starts — while another run is active (or when there is nothing to do).
     */
    fun start(
        plan: List<String>,
        unsubscribe: suspend (String) -> Boolean,
        carried: List<String> = emptyList(),
        settleCarried: suspend (List<String>) -> List<String> = { emptyList() },
    ): Boolean {
        val total = plan.size + carried.size
        synchronized(lock) {
            if (_state.value.active || total == 0) return false
            stopRequested.value = false
            _state.value = State(active = true, total = total)
        }
        scope.launch {
            try {
                run(plan, unsubscribe, carried, settleCarried)
            } finally {
                _state.update { if (it.active) it.copy(active = false, stopping = false) else it }
            }
        }
        return true
    }

    /** «Detener»: the item in flight finishes its (bounded) wait for the account, the rest is not attempted. */
    fun stop() {
        stopRequested.value = true
        _state.update { if (it.active) it.copy(stopping = true) else it }
    }

    private suspend fun run(
        plan: List<String>,
        unsubscribe: suspend (String) -> Boolean,
        carried: List<String>,
        settleCarried: suspend (List<String>) -> List<String>,
    ) {
        val account = accountTag()
        val alreadyResolved = try {
            settleCarried(carried).filter { it in carried }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val stillFailed = carried - alreadyResolved.toSet()
        _state.update {
            it.copy(
                done = carried.size,
                removed = alreadyResolved.size,
                failed = stillFailed.size,
                removedIds = alreadyResolved.toSet(),
                failedIds = stillFailed.toSet(),
            )
        }
        log(String.format(Locale.ROOT, "SUB_CLEANUP_START count=%d", plan.size))
        val batch = PhantomSubscriptionPolicy.runBatch(
            plan = plan,
            spacingMs = PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS,
            unsubscribe = { id ->
                val ok = try {
                    unsubscribe(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    false
                }
                _state.update { if (ok) it.copy(removedIds = it.removedIds + id) else it.copy(failedIds = it.failedIds + id) }
                ok
            },
            pause = pause,
            // A sign-out or another account stops the batch: the list belongs to the account it was read from.
            shouldStop = { stopRequested.value || account == null || accountTag() != account },
            onProgress = { done, removed, failed ->
                _state.update { it.copy(done = done + carried.size, removed = removed + alreadyResolved.size, failed = failed + stillFailed.size) }
            },
        )
        val result = BatchResult(
            removed = alreadyResolved + batch.removed,
            failed = stillFailed + batch.failed,
            notAttempted = batch.notAttempted,
            cancelled = batch.cancelled,
        )
        log(
            String.format(
                Locale.ROOT,
                "SUB_CLEANUP_DONE removed=%d failed=%d not_attempted=%d stopped=%b",
                result.removed.size, result.failed.size, result.notAttempted.size, result.cancelled,
            ),
        )
        _state.update { it.copy(active = false, stopping = false, lastResult = result) }
    }
}
