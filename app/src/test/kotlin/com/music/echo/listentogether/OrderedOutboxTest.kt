package iad1tya.echo.music.listentogether

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

/**
 * P2-5: the host's commands leave in the order they were issued. Each used to be its own `launch` on Dispatchers.Default, so the PLAY
 * that follows a CHANGE_TRACK (or an older queue) could reach the room first.
 */
class OrderedOutboxTest {
    @Test
    fun commandsAreDeliveredInTheOrderTheyWereIssuedEvenWhenOneIsSlow() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val got = Collections.synchronizedList(mutableListOf<Int>())
            val done = CompletableDeferred<Unit>()
            val outbox =
                OrderedOutbox<Int>(scope) { i ->
                    // A slow send (a big queue, a congested socket) must not let the next command overtake it.
                    if (i % 10 == 0) delay(2)
                    got += i
                    if (i == 299) done.complete(Unit)
                }
            repeat(300) { outbox.offer(it) }
            withTimeout(10_000) { done.await() }
            assertEquals((0 until 300).toList(), got.toList())
            scope.cancel()
        }

    @Test
    fun oneFailedDeliveryDoesNotSilenceTheOnesAfterIt() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val got = Collections.synchronizedList(mutableListOf<String>())
            val done = CompletableDeferred<Unit>()
            val outbox =
                OrderedOutbox<String>(scope) { s ->
                    if (s == PlaybackActions.SEEK) error("send failed")
                    got += s
                    if (s == PlaybackActions.PAUSE) done.complete(Unit)
                }
            outbox.offer(PlaybackActions.CHANGE_TRACK)
            outbox.offer(PlaybackActions.SEEK)
            outbox.offer(PlaybackActions.PLAY)
            outbox.offer(PlaybackActions.PAUSE)
            withTimeout(10_000) { done.await() }
            assertEquals(listOf(PlaybackActions.CHANGE_TRACK, PlaybackActions.PLAY, PlaybackActions.PAUSE), got.toList())
            scope.cancel()
        }

    @Test
    fun theSessionSendsEveryPlaybackActionThroughOneOutbox() {
        // The guard: sendPlaybackAction and sendQueue no longer launch a coroutine each (they returned a Job); they hand the payload
        // to the session's single OrderedOutbox.
        val session = ListenTogetherSession::class.java
        assertEquals(1, session.declaredFields.count { it.type == OrderedOutbox::class.java })
        for (name in listOf("sendPlaybackAction", "sendQueue")) {
            val methods = session.declaredMethods.filter { it.name == name }
            assertEquals("$name exists", true, methods.isNotEmpty())
            methods.forEach { assertEquals("$name returns nothing to launch", Void.TYPE, it.returnType) }
        }
    }
}
