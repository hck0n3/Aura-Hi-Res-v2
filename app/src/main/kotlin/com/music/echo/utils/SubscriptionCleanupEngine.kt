package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.BatchResult
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registry row 838 — the «Revisar suscripciones» removal run (row 813), owned by the app instead of the screen: the
 * queue of selected ids, the counters, «Detener» and the summary live here, in [scope] (app-wide), so leaving the
 * screen or sending the app to the background no longer ends the run. The screen only observes [state].
 *
 * Pure (no Android): [SubscriptionCleanupRunner] gives it the account, the unfollow tap, the log and [persist]. Nothing
 * here polls: the run is one coroutine that exists only between [start] and its summary.
 *
 * Row 839: a process death (the system killing the app, a force stop) ends the run without a summary. So the run
 * keeps a [SavedRun] — the ids still pending (ids only), the counters and a hash of the account — written when it
 * starts and after every item, and cleared when it ends in any other way (finished, «Detener», another account).
 * A [SavedRun] found at the next start of the app is [State.interrupted]: the screen says «Se detuvo: X de N; toca
 * Continuar». Nothing resumes on its own; «Continuar» re-plans those ids against the list just read.
 */
class SubscriptionCleanupEngine(
    private val scope: CoroutineScope,
    private val accountTag: () -> String?,
    private val pause: suspend (Long) -> Unit,
    private val log: (String) -> Unit = {},
    private val persist: suspend (SavedRun?) -> Unit = {},
) {
    /** Row 839: what survives a process death. [account] is [accountKey] of the account, never the account itself. */
    data class SavedRun(
        val account: String,
        val total: Int,
        val done: Int,
        val removed: Int,
        val failed: Int,
        val pending: List<String>,
    )

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
        /** Row 839: a run a process death cut short (restored at start-up, see [restore]); null once handled. */
        val interrupted: SavedRun? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val stopRequested = MutableStateFlow(false)
    private val lock = Any()
    private val persistLock = Mutex()

    /** A run started in this process: a [SavedRun] read afterwards is that run's, not an interrupted one. */
    private var started = false

    /** Row 839: the [SavedRun] the last process left, read once at start-up. Never starts anything. */
    fun restore(saved: SavedRun?) {
        if (saved == null) return
        synchronized(lock) {
            if (started || _state.value.active) return
            _state.update { it.copy(interrupted = saved) }
        }
        log(String.format(Locale.ROOT, "SUB_CLEANUP_INTERRUPTED done=%d total=%d pending=%d", saved.done, saved.total, saved.pending.size))
    }

    /** Row 839: «Descartar» on the «Se detuvo» notice. */
    fun discardInterrupted() {
        synchronized(lock) {
            if (_state.value.active || _state.value.interrupted == null) return
            _state.update { it.copy(interrupted = null) }
        }
        scope.launch { save(null, onlyWhileIdle = true) }
    }

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
            started = true
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
        val key = account?.let(::accountKey)
        fun saved(done: Int, removed: Int, failed: Int, pending: List<String>) =
            key?.let { SavedRun(it, plan.size + carried.size, done, removed, failed, pending) }
        save(saved(0, 0, 0, plan + carried))
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
        save(saved(carried.size, alreadyResolved.size, stillFailed.size, plan))
        var attempted = 0
        var removedCount = 0
        var failedCount = 0
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
                attempted++
                if (ok) removedCount++ else failedCount++
                save(
                    saved(
                        carried.size + attempted,
                        alreadyResolved.size + removedCount,
                        stillFailed.size + failedCount,
                        plan.drop(attempted),
                    ),
                )
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
        // Ended with a summary (finished, «Detener», another account): nothing left for «Se detuvo».
        save(null)
        _state.update { it.copy(active = false, stopping = false, lastResult = result) }
    }

    private suspend fun save(run: SavedRun?, onlyWhileIdle: Boolean = false) = persistLock.withLock {
        if (onlyWhileIdle && _state.value.active) return@withLock
        persist(run)
    }

    companion object {
        private const val SAVED_VERSION = "v1"

        /** A one-way hash of the account tag (SAPISID): enough to tell «same account», useless as a credential. */
        fun accountKey(tag: String): String =
            MessageDigest.getInstance("SHA-256").digest(tag.toByteArray()).joinToString("") { String.format(Locale.ROOT, "%02x", it) }

        fun encode(run: SavedRun): String =
            listOf(SAVED_VERSION, run.account, run.total, run.done, run.removed, run.failed, run.pending.joinToString(","))
                .joinToString("|")

        /** Null for nothing saved or anything malformed: a bad record never becomes a notice. */
        fun decode(text: String?): SavedRun? {
            val parts = text?.split('|') ?: return null
            if (parts.size != 7 || parts[0] != SAVED_VERSION || parts[1].isBlank()) return null
            val numbers = parts.subList(2, 6).map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
            val pending = if (parts[6].isEmpty()) emptyList() else parts[6].split(',')
            return SavedRun(parts[1], numbers[0], numbers[1], numbers[2], numbers[3], pending)
        }
    }
}
