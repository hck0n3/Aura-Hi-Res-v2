package iad1tya.echo.music.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 838 — the «Revisar suscripciones» run lives in [SubscriptionCleanupEngine] (app-scoped through
 * [SubscriptionCleanupRunner]), not in the screen. Its state machine: start, progress and marks, refusal while active,
 * «Detener» (the item in flight still counts), the account guard and the retry's carried ids.
 */
class SubscriptionCleanupEngineTest {

    private class Fixture(var account: String? = "acc-1") {
        val pauses = ArrayList<Long>()
        val logs = ArrayList<String>()
        fun engine(scope: kotlinx.coroutines.CoroutineScope) = SubscriptionCleanupEngine(
            scope = scope,
            accountTag = { account },
            pause = { pauses.add(it) },
            log = { logs.add(it) },
        )
    }

    private suspend fun SubscriptionCleanupEngine.finished() = state.first { !it.active && it.lastResult != null }

    @Test
    fun `a run removes in order, counts, marks rows and ends with its summary`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        val calls = ArrayList<String>()
        assertTrue(engine.start(listOf("a", "b", "c"), unsubscribe = { id -> calls.add(id); id != "b" }))
        assertTrue("active as soon as it starts", engine.state.value.active)
        assertEquals(3, engine.state.value.total)
        val end = engine.finished()
        assertEquals(listOf("a", "b", "c"), calls)
        assertEquals(listOf(PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS, PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS), f.pauses)
        assertEquals(3, end.done)
        assertEquals(2, end.removed)
        assertEquals(1, end.failed)
        assertEquals(setOf("a", "c"), end.removedIds)
        assertEquals(setOf("b"), end.failedIds)
        assertEquals(listOf("a", "c"), end.lastResult!!.removed)
        assertEquals(listOf("b"), end.lastResult!!.failed)
        assertFalse(end.lastResult!!.cancelled)
    }

    @Test
    fun `a second run is refused while one is active, and allowed after`() = runBlocking {
        val engine = Fixture().engine(this)
        val gate = CompletableDeferred<Boolean>()
        assertTrue(engine.start(listOf("a"), unsubscribe = { gate.await() }))
        assertFalse(engine.start(listOf("x", "y"), unsubscribe = { true }))
        assertEquals("the refused run changed nothing", 1, engine.state.value.total)
        gate.complete(true)
        engine.finished()
        assertTrue(engine.start(listOf("x"), unsubscribe = { true }))
        assertEquals("a new run starts from a clean state", null, engine.state.value.lastResult)
        assertEquals(listOf("x"), engine.finished().lastResult!!.removed)
    }

    @Test
    fun `nothing to do starts nothing`() = runBlocking {
        val engine = Fixture().engine(this)
        assertFalse(engine.start(emptyList(), unsubscribe = { true }))
        assertFalse(engine.state.value.active)
    }

    @Test
    fun `stop lets the item in flight finish and counts it, the rest is not attempted`() = runBlocking {
        val engine = Fixture().engine(this)
        val inFlight = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Boolean>()
        val calls = ArrayList<String>()
        engine.start(listOf("a", "b", "c"), unsubscribe = { id -> calls.add(id); inFlight.complete(Unit); gate.await() })
        inFlight.await()
        engine.stop()
        assertTrue(engine.state.value.stopping)
        gate.complete(true)
        val end = engine.finished()
        assertEquals(listOf("a"), calls)
        assertEquals(listOf("a"), end.lastResult!!.removed)
        assertEquals(listOf("b", "c"), end.lastResult!!.notAttempted)
        assertTrue(end.lastResult!!.cancelled)
        assertFalse(end.stopping)
    }

    @Test
    fun `another account or a sign-out stops the run before the next item`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        val calls = ArrayList<String>()
        engine.start(listOf("a", "b", "c"), unsubscribe = { id -> calls.add(id); f.account = "acc-2"; true })
        val end = engine.finished()
        assertEquals(listOf("a"), calls)
        assertEquals(listOf("b", "c"), end.lastResult!!.notAttempted)

        f.account = null
        val signedOut = f.engine(this)
        signedOut.start(listOf("a"), unsubscribe = { error("must not run signed out") })
        assertEquals(listOf("a"), signedOut.finished().lastResult!!.notAttempted)
    }

    @Test
    fun `an exception from the tap is a failure of that id, the run goes on`() = runBlocking {
        val engine = Fixture().engine(this)
        engine.start(listOf("a", "b"), unsubscribe = { id -> if (id == "a") throw IllegalStateException("x") else true })
        val end = engine.finished()
        assertEquals(listOf("a"), end.lastResult!!.failed)
        assertEquals(listOf("b"), end.lastResult!!.removed)
    }

    @Test
    fun `the retry settles carried ids first, then taps the rest`() = runBlocking {
        val engine = Fixture().engine(this)
        engine.start(
            plan = listOf("n"),
            unsubscribe = { true },
            carried = listOf("w1", "w2"),
            settleCarried = { carried -> carried.filter { it == "w1" } },
        )
        assertEquals(3, engine.state.value.total)
        val end = engine.finished()
        assertEquals(3, end.done)
        assertEquals(listOf("w1", "n"), end.lastResult!!.removed)
        assertEquals(listOf("w2"), end.lastResult!!.failed)
        assertEquals(setOf("w1", "n"), end.removedIds)
        assertEquals(setOf("w2"), end.failedIds)
    }

    @Test
    fun `logs carry counts only`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        engine.start(listOf("UCsecretchannel"), unsubscribe = { true })
        engine.finished()
        assertEquals(listOf("SUB_CLEANUP_START count=1", "SUB_CLEANUP_DONE removed=1 failed=0 not_attempted=0 stopped=false"), f.logs)
    }
}
