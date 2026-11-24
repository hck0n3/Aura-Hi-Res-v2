package iad1tya.echo.music.utils

import com.music.innertube.utils.walkContinuations
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 837 — `LibraryPage.completed()` stopped at 50 pages or at the first failed page and reported
 * `continuation = null`, so a cut-short read of the account's subscriptions looked complete. «Revisar suscripciones»
 * showed only part of a big account and nothing said so. The walk now leaves the unfinished token in place, the
 * cleanup tool pages to the end (bounded) and says when it could not, and no consumer removes with a partial list.
 */
class LibraryReadCompletenessTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    /** A remote list of [pages] pages of [perPage] items; tokens "t1".."t(n-1)". */
    private fun pagedList(pages: Int, perPage: Int, failAt: Int? = null): suspend (String) -> Pair<List<Int>, String?>? = { token ->
        val n = token.removePrefix("t").toInt()
        if (n == failAt) {
            null
        } else {
            List(perPage) { n * perPage + it } to (if (n + 1 < pages) "t${n + 1}" else null)
        }
    }

    @Test
    fun `a walk that reaches the end is complete and keeps every item`() = runBlocking {
        val walk = walkContinuations(List(3) { it }, "t1", maxRequests = 50, fetch = pagedList(pages = 10, perPage = 3))
        assertTrue(walk.complete)
        assertNull(walk.continuation)
        assertEquals((0 until 30).toList(), walk.items)
    }

    @Test
    fun `the request cap leaves the next token, so the read says it is incomplete`() = runBlocking {
        // 60 pages, cap 50: the old completed() returned 51 pages and continuation = null.
        val walk = walkContinuations(List(25) { it }, "t1", maxRequests = 50, fetch = pagedList(pages = 60, perPage = 25))
        assertFalse(walk.complete)
        assertEquals("t51", walk.continuation)
        assertEquals(51 * 25, walk.items.size)
    }

    @Test
    fun `a higher cap reads a big account to the end`() = runBlocking {
        val walk = walkContinuations(List(25) { it }, "t1", maxRequests = 400, fetch = pagedList(pages = 60, perPage = 25))
        assertTrue(walk.complete)
        assertEquals(60 * 25, walk.items.size)
    }

    @Test
    fun `a failed page stops the walk and keeps its token`() = runBlocking {
        val walk = walkContinuations(List(2) { it }, "t1", maxRequests = 400, fetch = pagedList(pages = 10, perPage = 2, failAt = 4))
        assertFalse(walk.complete)
        assertEquals("t4", walk.continuation)
        assertEquals(4 * 2, walk.items.size)
    }

    @Test
    fun `a looping token and two empty pages in a row are incomplete too`() = runBlocking {
        val loop = walkContinuations(listOf(0), "t1", maxRequests = 400) { listOf(1) to "t1" }
        assertFalse(loop.complete)
        assertEquals(listOf(0, 1), loop.items)
        val empty = walkContinuations(listOf(0), "t1", maxRequests = 400) { token ->
            emptyList<Int>() to "t${token.removePrefix("t").toInt() + 1}"
        }
        assertFalse(empty.complete)
        assertEquals(listOf(0), empty.items)
    }

    @Test
    fun `one empty page followed by the end is still complete`() = runBlocking {
        val walk = walkContinuations(listOf(0), "t1", maxRequests = 400) { token ->
            if (token == "t1") emptyList<Int>() to "t2" else listOf(2) to null
        }
        assertTrue(walk.complete)
        assertEquals(listOf(0, 2), walk.items)
    }

    @Test
    fun `no continuation at all is a complete one-page list`() = runBlocking {
        val walk = walkContinuations(listOf(7, 8), null, maxRequests = 50) { error("must not fetch") }
        assertTrue(walk.complete)
        assertEquals(listOf(7, 8), walk.items)
    }

    @Test
    fun `completed() for library pages reports the walk's unfinished token instead of null`() {
        val utils = src("innertube/src/main/kotlin/com/music/innertube/utils/Utils.kt")
        val library = utils.substringAfter("@JvmName(\"completedPlaylist\")").substringBefore("/** What [walkContinuations] read")
        assertTrue(library.contains("suspend fun Result<LibraryPage>.completed(maxRequests: Int = 50): Result<LibraryPage>"))
        assertTrue(library.contains("walkContinuations(page.items, page.continuation, maxRequests)"))
        assertTrue(library.contains("continuation = walk.continuation"))
        assertFalse(library.contains("continuation = null"))
        // The streaming twin the account mirror reads keeps the same contract.
        val streaming = utils.substringAfter("@JvmName(\"completedStreamingPlaylist\")").substringBefore("fun ByteArray.toHex()")
        assertTrue(streaming.contains("continuation = continuation"))
    }

    @Test
    fun `the cleanup tool pages to the end, bounded, and tells the user when it could not`() {
        val vm = src("app/src/main/kotlin/com/music/echo/viewmodels/SubscriptionCleanupViewModel.kt")
        assertTrue(vm.contains("YouTube.library(\"FEmusic_library_corpus_artists\").completed(maxRequests = MAX_PAGES).getOrThrow()"))
        assertTrue(vm.contains("const val MAX_PAGES = 400"))
        assertTrue(vm.contains("val incomplete = page.continuation != null"))
        assertTrue(vm.contains("incomplete = incomplete,\n                    readCount = candidates.size,"))
        val screen = src("app/src/main/kotlin/com/music/echo/ui/screens/settings/SubscriptionCleanupScreen.kt")
        assertTrue(screen.contains("if (state.incomplete && !running) {"))
        assertTrue(screen.contains("stringResource(R.string.sub_cleanup_incomplete, state.readCount),"))
        val notice = screen.substringAfter("if (state.incomplete && !running) {").substringBefore("if (running) {")
        assertTrue("the notice carries its reload", notice.contains("viewModel.load()"))
        for (path in listOf(
            "app/src/main/res/values/subscription_cleanup_strings.xml",
            "app/src/main/res/values-es/subscription_cleanup_strings.xml",
        )) {
            val line = src(path).lines().single { it.contains("name=\"sub_cleanup_incomplete\"") }
            assertTrue("$path: the count placeholder", line.contains("%1\$d"))
        }
    }

    @Test
    fun `the account mirror and the uploads un-flag never remove using a cut-short read`() {
        val sync = src("app/src/main/kotlin/com/music/echo/utils/SyncUtils.kt")
        val mirror = sync.substringAfter("ArtistSyncPolicy.followsGoneFromAccount(").substringBefore("}.onFailure")
        assertTrue(mirror.contains("readWasComplete = page.continuation == null"))
        assertTrue(sync.contains("YouTube.library(\"FEmusic_library_corpus_artists\").completedStreaming { items ->"))
        assertTrue(sync.contains("if (remoteSongs.isNotEmpty() && page.continuation == null) {"))
        assertTrue(sync.contains("val readComplete = page.continuation == null"))
        assertTrue(sync.contains("(if (remoteAlbums.isEmpty() || !readComplete) emptyList() else localAlbums.filterNot { it.id in remoteIds })"))
    }
}
