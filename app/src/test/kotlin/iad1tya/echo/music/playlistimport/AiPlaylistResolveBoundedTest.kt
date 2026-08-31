package iad1tya.echo.music.playlistimport

import iad1tya.echo.music.api.TrackQuery
import iad1tya.echo.music.models.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks [AiPlaylistGenerator.resolveBoundedOrdered] — the parallel-resolve contract behind the
 * owner's 2026-08-31 directive ("AI answers are VERY slow"): faster resolve must never change
 * WHAT resolves or in WHICH ORDER the playlist ends up.
 *
 * The old serial loop resolved one track at a time (~1s each on a local-match miss). This test
 * pins the properties that made the serial loop "precise" so the parallel version cannot silently
 * trade them for speed:
 *  - final order follows the AI's PROPOSAL order, regardless of completion order;
 *  - the resolve set is IDENTICAL to what the serial order would resolve;
 *  - concurrency is actually bounded (battery/heat rule);
 *  - the soft short-circuit stops the network work once target distinct ids are in;
 *  - duplicate ids across proposals are deduplicated the same way the serial loop did.
 */
class AiPlaylistResolveBoundedTest {

    private fun song(id: String) = MediaMetadata(
        id = id,
        title = "title-$id",
        artists = listOf(MediaMetadata.Artist(id = "a-$id", name = "artist-$id")),
        duration = 200,
    )

    private fun tracks(vararg titles: String) = titles.map { TrackQuery(title = it, artist = "X") }

    @Test
    fun orderFollowsProposalOrderNotCompletionOrder() = runBlocking {
        // Track 0 resolves SLOW (500ms), track 1 FAST (10ms): completion order is 1, 0.
        val out = AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("slow", "fast"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ ->
                when (title) {
                    "slow" -> { delay(500); song("a") }
                    else -> { delay(10); song("b") }
                }
            },
            accept = { true },
            target = 2,
        )
        assertEquals(listOf("a", "b"), out.map { it.id })
    }

    @Test
    fun duplicateIdsResolveToDistinctCountOnly() = runBlocking {
        // Two different proposals resolving to the SAME video: both enter the list (the serial loop
        // appended both too — dedupe happens later at distinctBy), but the short-circuit's distinct
        // count must NOT be fooled into thinking target was reached early by a duplicate.
        val out = AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("one", "two", "three"),
            resolveArtistFor = { it.artist },
            resolveOne = { _, _ -> song("same") },
            accept = { true },
            target = 3,
        )
        assertEquals(listOf("same", "same", "same"), out.map { it.id })
    }

    @Test
    fun shortCircuitSkipsNetworkOnceTargetReached() = runBlocking {
        // Target 2, three proposals: once two distinct ids resolve, the third lane must SKIP its
        // resolve call entirely (zero extra network hits — the serial loop's early break).
        var resolves = 0
        AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("a", "b", "c"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ ->
                resolves++
                song(title)
            },
            accept = { true },
            target = 2,
            concurrency = 1, // serial lanes: deterministic — the third lane must see the short-circuit
        )
        // Lanes a and b resolve; lane c skips. (Concurrency 1 makes the check deterministic.)
        assertEquals(2, resolves)
    }

    @Test
    fun rejectedResultsDoNotCountTowardsTarget() = runBlocking {
        // accept=false for everything: nothing counts, no early skip, and the list comes back empty
        // exactly like the serial loop would produce.
        var resolves = 0
        val out = AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("a", "b"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ ->
                resolves++
                song(title)
            },
            accept = { false },
            target = 1,
        )
        assertTrue(out.isEmpty())
        assertEquals(2, resolves)
    }

    @Test
    fun concurrencyIsBounded() = runBlocking {
        // Six slow resolves with concurrency 2: at no moment may more than 2 be in flight.
        val inFlight = java.util.concurrent.atomic.AtomicInteger()
        var maxInFlight = 0
        AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("a", "b", "c", "d", "e", "f"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ ->
                val now = inFlight.incrementAndGet()
                maxInFlight = maxOf(maxInFlight, now)
                delay(100)
                inFlight.decrementAndGet()
                song(title)
            },
            accept = { true },
            target = 6,
            concurrency = 2,
        )
        assertTrue("max in flight was $maxInFlight", maxInFlight <= 2)
    }

    @Test
    fun emptyProposalsResolveToNothing() = runBlocking {
        val out = AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = emptyList(),
            resolveArtistFor = { it.artist },
            resolveOne = { _, _ -> song("x") },
            accept = { true },
            target = 5,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun resolveFailuresLeaveGapsNotReordering() = runBlocking {
        // Middle track fails to resolve: the survivors keep their RELATIVE proposal order; the
        // serial loop behaved exactly this way (misses were silently skipped).
        val out = AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("a", "b", "c"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ -> if (title == "b") null else song(title) },
            accept = { true },
            target = 3,
        )
        assertEquals(listOf("a", "c"), out.map { it.id })
    }

    @Test
    fun progressCallbackNeverExceedsTarget() = runBlocking {
        val seen = java.util.concurrent.ConcurrentLinkedDeque<Int>()
        AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks("a", "b", "c"),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ -> song(title) },
            accept = { true },
            target = 2,
            onResolveProgress = { done, total -> seen.add(done); assertEquals(2, total) },
        )
        assertTrue(seen.all { it in 0..2 })
    }

    @Test
    fun parallelIsActuallyFasterThanSerialForSlowResolves() = runBlocking {
        // The directive's point, measured: 8 resolves × 100ms each. Serial = ~800ms; bounded
        // concurrency 4 must finish in well under that (we assert < 500ms to keep the test robust
        // on slow CI while still proving the wall-time win).
        val titles = (0 until 8).map { "t$it" }
        val start = System.currentTimeMillis()
        AiPlaylistGenerator.resolveBoundedOrdered(
            proposed = tracks(*titles.toTypedArray()),
            resolveArtistFor = { it.artist },
            resolveOne = { title, _ ->
                delay(100)
                song(title)
            },
            accept = { true },
            target = 8,
            concurrency = 4,
        )
        val elapsed = System.currentTimeMillis() - start
        assertTrue("parallel resolve took ${elapsed}ms (expected < 500ms)", elapsed < 500)
    }
}
