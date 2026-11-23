package iad1tya.echo.music.utils

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 711: two opposite follow taps in quick succession used to send two unordered account calls; if the
 * unsubscribe landed first, the account stayed subscribed against the user's last tap. [LatestIntentQueue]
 * sends them one at a time per channel and the account ends with the LAST intent.
 */
class ArtistAccountCallOrderTest {

    private data class Tap(val name: String, val subscribing: Boolean)

    /** A fake account: every call waits until the test answers it. */
    private class Harness(scope: CoroutineScope) {
        val sent = mutableListOf<String>()
        val settled = mutableListOf<String>()
        val answers = ArrayDeque<CompletableDeferred<Boolean>>()
        val queue = LatestIntentQueue<Tap>(
            scope = scope,
            effectOf = { it.subscribing },
            send = { tap -> sent += tap.name; CompletableDeferred<Boolean>().also { answers.addLast(it) }.await() },
            settle = { tap -> settled += tap.name },
        )

        fun answer(confirmed: Boolean) = answers.removeFirst().complete(confirmed)
    }

    private suspend fun idle() = repeat(20) { yield() }

    @Test
    fun `an unfollow tapped while the follow is in flight goes out after it`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("follow", true)); idle()
        h.queue.submit("UC1", Tap("unfollow", false)); idle()
        assertEquals("nothing overtakes the call in flight", listOf("follow"), h.sent)
        h.answer(true); idle()
        assertEquals(listOf("follow", "unfollow"), h.sent)
        h.answer(true); idle()
        assertEquals("the last tap is what the account ends with", "unfollow", h.sent.last())
        assertEquals(listOf("unfollow"), h.settled.filter { it == "unfollow" })
    }

    @Test
    fun `taps that end where the account already is send nothing more and still retire their marker`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("unfollow1", false)); idle()
        h.queue.submit("UC1", Tap("follow", true))
        h.queue.submit("UC1", Tap("unfollow2", false)); idle()
        h.answer(true); idle()
        assertEquals("the account already holds the last intent", listOf("unfollow1"), h.sent)
        assertEquals("each confirmed unfollow is retired (row 107)", listOf("unfollow1", "unfollow2"), h.settled)
        assertTrue(h.answers.isEmpty())
    }

    @Test
    fun `a failed call settles nothing and the newer tap is still sent`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("unfollow1", false)); idle()
        h.queue.submit("UC1", Tap("unfollow2", false)); idle()
        h.answer(false); idle()
        assertEquals("a failure leaves the marker: it is the retry", emptyList<String>(), h.settled)
        assertEquals(listOf("unfollow1", "unfollow2"), h.sent)
        h.answer(true); idle()
        assertEquals(listOf("unfollow2"), h.settled)
    }

    @Test
    fun `both spellings of a channel unfollowed in one tap are one call and both are retired`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("UC1", false))
        h.queue.submit("UC1", Tap("MPLAUC1", false)); idle()
        assertEquals(listOf("MPLAUC1"), h.sent)
        h.answer(true); idle()
        assertEquals(listOf("UC1", "MPLAUC1"), h.settled)
    }

    @Test
    fun `different channels do not wait for each other`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("a", true))
        h.queue.submit("UC2", Tap("b", true)); idle()
        assertEquals(listOf("a", "b"), h.sent)
        h.answer(true); h.answer(true); idle()
        h.queue.submit("UC1", Tap("c", false)); idle()
        assertEquals("a drained key starts again on the next tap", listOf("a", "b", "c"), h.sent)
        h.answer(true); idle()
    }

    @Test
    fun `a crashing call does not wedge the channel`() = runBlocking {
        var fail = true
        val sent = mutableListOf<String>()
        val queue = LatestIntentQueue<Tap>(
            scope = this,
            effectOf = { it.subscribing },
            send = { tap -> sent += tap.name; if (fail) error("network") else true },
            settle = {},
        )
        queue.submit("UC1", Tap("a", true)); idle()
        fail = false
        queue.submit("UC1", Tap("b", false)); idle()
        assertEquals(listOf("a", "b"), sent)
    }

    @Test
    fun `submit reports how many taps wait for the channel`() = runBlocking {
        val h = Harness(this)
        assertEquals(1, h.queue.submit("UC1", Tap("a", true)))
        assertEquals(2, h.queue.submit("UC1", Tap("b", false)))
        idle()
        assertEquals("the call in flight is not counted", 1, h.queue.submit("UC1", Tap("c", true)))
        h.answer(true); idle()
        h.answer(true); idle()
    }

    @Test
    fun `a sign-out drops what waits and nothing of it reaches the next account`() = runBlocking {
        val h = Harness(this)
        h.queue.submit("UC1", Tap("a-follow", true)); idle()
        h.queue.submit("UC1", Tap("a-unfollow", false))
        h.queue.submit("UC2", Tap("a-other", true))
        h.queue.clear()
        // The next account taps while account A's call is still in flight.
        h.queue.submit("UC1", Tap("b-follow", true))
        h.answer(true); idle()
        assertEquals("only A's call already in flight went out, then B's own tap", listOf("a-follow", "b-follow"), h.sent)
        assertTrue("what A's call confirmed is not settled", h.settled.isEmpty())
        h.answer(true); idle()
        assertEquals(listOf("b-follow"), h.settled)
        assertTrue(h.answers.isEmpty())
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `every artist tap reaches the account through the ordered queue`() {
        val entity = main("db/entities/ArtistEntity.kt")
        val toggle = entity.substringAfter("    fun toggleLike(").substringBefore("\n    }\n")
        assertTrue("submitted at tap time, in tap order", toggle.contains("accountCalls.submit("))
        assertTrue("row 716: one queue per channel, all spellings", toggle.contains("ArtistSyncPolicy.accountCallKey(id, channelId)"))
        assertFalse("an unordered call per tap came back", toggle.contains("subscribeChannel("))
        assertEquals("one door to the account", 1, Regex("""YouTube\.subscribeChannel\(""").findAll(entity).count())
        val send = entity.substringAfter("private suspend fun sendToAccount(").substringBefore("\n        }\n")
        assertTrue(send.contains("YouTube.subscribeChannel(targetChannelId, subscribing)"))
        val settle = entity.substringAfter("private suspend fun retireHonouredUnfollow(").substringBefore("\n        }\n")
        assertTrue("row 107 write-back kept", settle.contains("liveCallHonouredTheUnfollow(call.subscribing, confirmedByAccount = true)"))
        assertTrue(settle.contains("call.onLiveUnsubscribeConfirmed(call.artist.id, queuedAt)"))
    }

    @Test
    fun `every tap is logged without names or ids and a sign-out empties the queue`() {
        val entity = main("db/entities/ArtistEntity.kt")
        assertTrue(
            entity.contains(
                "timber.log.Timber.i(\"ARTIST_FOLLOW_TAP intent=%s queued=%d\", if (subscribing) \"follow\" else \"unfollow\", queued)",
            ),
        )
        assertTrue(entity.contains("fun dropPendingAccountCalls() = accountCalls.clear()"))
        val forget = main("App.kt").substringAfter("suspend fun forgetAccount(context: Context) {")
        val drop = forget.indexOf("ArtistEntity.dropPendingAccountCalls()")
        assertTrue("dropped before the session goes", drop in 0 until forget.indexOf("YouTube.cookie = null"))
    }

    @Test
    fun `the search pane's subscribe tile is the artist page's tap`() {
        val pane = main("ui/newui/AuraSearchDetailPane.kt")
        val artist = pane.substringAfter("private fun PaneYtArtist(").substringBefore("\n}\n")
        assertTrue("alias-aware state", artist.contains("env.database.isAnyArtistFollowed(ArtistSyncPolicy.aliasIds(artist.id))"))
        assertTrue("one tap at a time", artist.contains("if (followInFlight.compareAndSet(false, true)) {"))
        assertTrue(
            "only against what the tile showed",
            artist.contains("shownFollowed = shownFollowed,"),
        )
        assertTrue(artist.contains("env.database.toggleArtistFollow("))
        assertFalse("a second, alias-blind toggle came back", artist.contains(".toggleLike("))
        assertFalse(artist.contains("libraryArtist?.artist?.followedByUserAt"))
    }
}
