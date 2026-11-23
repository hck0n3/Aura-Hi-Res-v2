package iad1tya.echo.music.listentogether

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Delivers what it is given one at a time, in the order it was given (P2-5).
 *
 * The host's commands used to be sent from one `launch` each on Dispatchers.Default, and two launches have no order between them:
 * the PLAY that follows a CHANGE_TRACK could reach the room first, or an older queue after the new track's. [offer] never suspends
 * and never reorders; a single consumer drains the queue. One failed delivery does not stop the ones after it.
 */
internal class OrderedOutbox<T>(
    scope: CoroutineScope,
    private val deliver: suspend (T) -> Unit,
) {
    private val queue = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in queue) {
                try {
                    deliver(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Not sent, exactly as a failed send already was; the next command still goes out.
                }
            }
        }
    }

    fun offer(item: T) {
        queue.trySend(item)
    }
}
