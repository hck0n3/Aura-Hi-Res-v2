package iad1tya.echo.music.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Row 711: the account calls for ONE key (an artist's channel) go out one at a time, in tap order, and the
 * account ends with the user's LAST intent.
 *
 * Before, every follow tap launched its own unscoped call. Two opposite taps in quick succession sent two
 * requests with no order between them: if the unsubscribe landed first, the account stayed subscribed while
 * Aura showed «Suscribirse».
 *
 * [submit] is synchronous, so the order of submits is the order of taps. While a call for the key is in
 * flight, newer intents wait; when it ends, only the newest of them is sent — and not even that one when the
 * account already has its effect from a call this run confirmed. [settle] then runs for every waiting intent
 * with that effect (each row's own write-back); intents with the other effect were overruled and are dropped.
 * A failed call settles nothing, so whatever marker the caller keeps for a retry stays in place.
 *
 * @param effectOf what an intent does to the account (for a follow: subscribe or not).
 * @param send makes the call; true only when the account confirmed it.
 * @param settle runs for an intent the account is confirmed to hold.
 */
class LatestIntentQueue<T>(
    private val scope: CoroutineScope,
    private val effectOf: (T) -> Any,
    private val send: suspend (T) -> Boolean,
    private val settle: suspend (T) -> Unit,
) {
    private val lock = Any()
    private val waiting = HashMap<String, MutableList<T>>()
    private val draining = HashSet<String>()

    fun submit(key: String, intent: T) {
        val start = synchronized(lock) {
            waiting.getOrPut(key) { mutableListOf() }.add(intent)
            draining.add(key)
        }
        if (start) scope.launch { drain(key) }
    }

    /** The intents waiting for [key], or null — and the key no longer draining — when there are none. */
    private fun takeOrFinish(key: String): List<T>? = synchronized(lock) {
        waiting.remove(key) ?: run {
            draining.remove(key)
            null
        }
    }

    private suspend fun drain(key: String) {
        var confirmedEffect: Any? = null
        try {
            while (true) {
                val batch = takeOrFinish(key) ?: return
                val effect = effectOf(batch.last())
                val confirmed = effect == confirmedEffect || guarded { send(batch.last()) } == true
                confirmedEffect = if (confirmed) effect else null
                if (confirmed) batch.filter { effectOf(it) == effect }.forEach { guarded { settle(it) } }
            }
        } catch (e: Throwable) {
            // Only cancellation gets here. Release the key so the next submit drains what is left.
            synchronized(lock) { draining.remove(key) }
            throw e
        }
    }

    /** No exception from a call escapes into this unsupervised coroutine; cancellation still does. */
    private suspend fun <R> guarded(block: suspend () -> R): R? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
