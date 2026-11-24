package iad1tya.echo.music.utils

import iad1tya.echo.music.utils.SubscriptionCleanupEngine.SavedRun
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 838 — the «Revisar suscripciones» run lives in [SubscriptionCleanupEngine] (app-scoped through
 * [SubscriptionCleanupRunner]), not in the screen. Its state machine: start, progress and marks, refusal while active,
 * «Detener» (the item in flight still counts), the account guard and the retry's carried ids.
 * Row 839: the pending ids survive a process death as a [SavedRun] and come back as «Se detuvo», never as a run.
 */
class SubscriptionCleanupEngineTest {

    private class Fixture(var account: String? = "acc-1") {
        val pauses = ArrayList<Long>()
        val logs = ArrayList<String>()
        val saves = ArrayList<SavedRun?>()
        fun engine(scope: CoroutineScope) = SubscriptionCleanupEngine(
            scope = scope,
            accountTag = { account },
            pause = { pauses.add(it) },
            log = { logs.add(it) },
            persist = { saves.add(it) },
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
    fun `what a run removed leaves its list for good, also across a retry that wipes the summary`() = runBlocking {
        val engine = Fixture().engine(this)
        // The screen's list and selection, folded the way SubscriptionCleanupViewModel.absorbLastRun does before a run.
        var candidates = listOf("a", "b", "c")
        var selected = setOf("a", "b", "c")
        fun absorb() {
            val gone = SubscriptionCleanupEngine.removedBy(engine.state.value)
            candidates = candidates.filterNot { it in gone }
            selected = selected - gone
        }
        assertTrue(engine.start(listOf("a", "b", "c"), unsubscribe = { id -> id != "c" }))
        assertTrue("nothing counts as removed while the run is active", SubscriptionCleanupEngine.removedBy(engine.state.value).isEmpty())
        assertEquals(setOf("a", "b"), SubscriptionCleanupEngine.removedBy(engine.finished()))
        absorb()
        assertTrue(engine.start(listOf("c"), unsubscribe = { false }))
        assertTrue("the retry wiped the summary", SubscriptionCleanupEngine.removedBy(engine.state.value).isEmpty())
        engine.finished()
        absorb()
        assertEquals(listOf("c"), candidates)
        assertEquals(setOf("c"), selected)
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
    fun `the pending ids are saved when the run starts and after every item, and cleared when it ends`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        engine.start(listOf("a", "b", "c"), unsubscribe = { id -> id != "b" })
        engine.finished()
        val key = SubscriptionCleanupEngine.accountKey("acc-1")
        assertEquals(
            listOf(
                SavedRun(key, 3, 0, 0, 0, listOf("a", "b", "c")),
                SavedRun(key, 3, 0, 0, 0, listOf("a", "b", "c")),
                SavedRun(key, 3, 1, 1, 0, listOf("b", "c")),
                SavedRun(key, 3, 2, 1, 1, listOf("c")),
                SavedRun(key, 3, 3, 2, 1, emptyList()),
                null,
            ),
            f.saves,
        )
    }

    @Test
    fun `stop and another account end with a summary, so nothing is left for the notice`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        engine.start(listOf("a", "b"), unsubscribe = { f.account = "acc-2"; true })
        assertEquals(listOf("b"), engine.finished().lastResult!!.notAttempted)
        assertNull(f.saves.last())
    }

    @Test
    fun `a run the process lost keeps its record, and the next process shows it without resuming`() = runBlocking {
        val f = Fixture()
        val processJob = Job()
        val engine = f.engine(CoroutineScope(coroutineContext + processJob))
        val inFlight = CompletableDeferred<Unit>()
        val calls = ArrayList<String>()
        engine.start(listOf("a", "b", "c"), unsubscribe = { id ->
            calls.add(id)
            if (id == "b") {
                inFlight.complete(Unit)
                CompletableDeferred<Boolean>().await()
            } else {
                true
            }
        })
        inFlight.await()
        processJob.cancelAndJoin() // the process dies while "b" waits for the account
        val left = f.saves.last()
        assertNotNull("the record survives", left)
        assertEquals(listOf("b", "c"), left!!.pending)
        assertEquals(1, left.done)
        assertEquals(1, left.removed)

        val stored = SubscriptionCleanupEngine.decode(SubscriptionCleanupEngine.encode(left))
        val next = Fixture()
        val restarted = next.engine(this)
        restarted.restore(stored)
        assertEquals(left, restarted.state.value.interrupted)
        assertFalse("nothing resumes on its own", restarted.state.value.active)
        yield()
        assertEquals(listOf("a", "b"), calls)
        assertTrue(next.logs.single().startsWith("SUB_CLEANUP_INTERRUPTED done=1 total=3 pending=2"))
    }

    @Test
    fun `a new run replaces the notice, and a record read after a run started here is ignored`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        val saved = SavedRun(SubscriptionCleanupEngine.accountKey("acc-1"), 5, 2, 2, 0, listOf("x", "y", "z"))
        engine.restore(saved)
        assertEquals(saved, engine.state.value.interrupted)
        engine.start(listOf("x"), unsubscribe = { true })
        assertNull(engine.state.value.interrupted)
        engine.finished()
        engine.restore(saved)
        assertNull("the old record lost the race against this process's run", engine.state.value.interrupted)
    }

    @Test
    fun `discarding the notice clears it and its record, never a run's`() = runBlocking {
        val f = Fixture()
        val engine = f.engine(this)
        engine.restore(SavedRun(SubscriptionCleanupEngine.accountKey("acc-1"), 2, 1, 1, 0, listOf("y")))
        engine.discardInterrupted()
        assertNull(engine.state.value.interrupted)
        yield()
        assertEquals(listOf<SavedRun?>(null), f.saves)
    }

    @Test
    fun `the record holds ids and a hash of the account, and a bad record is no notice`() {
        val key = SubscriptionCleanupEngine.accountKey("SAPISID-secret")
        assertEquals(64, key.length)
        assertFalse(key.contains("secret"))
        assertEquals(key, SubscriptionCleanupEngine.accountKey("SAPISID-secret"))
        val run = SavedRun(key, 4, 1, 1, 0, listOf("UCaaa", "MPLAUCbbb"))
        val text = SubscriptionCleanupEngine.encode(run)
        assertFalse(text.contains("SAPISID-secret"))
        assertEquals(run, SubscriptionCleanupEngine.decode(text))
        assertEquals(run.copy(pending = emptyList()), SubscriptionCleanupEngine.decode(SubscriptionCleanupEngine.encode(run.copy(pending = emptyList()))))
        listOf(null, "", "v0|$key|4|1|1|0|UCaaa", "v1||4|1|1|0|UCaaa", "v1|$key|4|x|1|0|UCaaa", "v1|$key|4|-1|1|0|UCaaa", "v1|$key|4|1|1|0").forEach {
            assertNull(it, SubscriptionCleanupEngine.decode(it))
        }
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
