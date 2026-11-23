package iad1tya.echo.music.ui.newui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 766-767: list rows must not rebuild flows on every recomposition.
 */
class ListPerformanceGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `AuraDownloadTick remembers its download flow per song`() {
        val tick = main("ui/newui/AuraContent.kt")
            .substringAfter("fun AuraDownloadTick(")
            .substringBefore("when (download?.state) {")
        assertTrue(tick.contains("val downloadFlow = remember(downloadUtil, songId) { downloadUtil.getDownload(songId) }"))
        assertTrue(tick.contains("val download by downloadFlow.collectAsState(initial = null)"))
        assertFalse(tick.contains("getDownload(songId).collectAsState("))
    }

    @Test
    fun `home load-more is single-flight and appends a continuation only once`() {
        val loadMore = main("viewmodels/HomeViewModel.kt")
            .substringAfter("fun loadMoreYouTubeItems(continuation: String?) {")
            .substringBefore("fun toggleChip(")
        // The flag is taken on the caller thread, before the coroutine starts.
        val claim = loadMore.indexOf("if (!_isLoadingMore.compareAndSet(expect = false, update = true)) return")
        assertTrue(claim >= 0)
        assertTrue(claim < loadMore.indexOf("viewModelScope.launch(Dispatchers.IO)"))
        // A stale continuation (already appended, or a page that was replaced) is ignored before and after the fetch.
        assertTrue(loadMore.contains("if (continuation == null || homePage.value?.continuation != continuation) return"))
        assertTrue(loadMore.contains("if (homePage.value?.continuation != continuation) return@launch"))
        assertTrue(loadMore.substringAfter("} finally {").trimStart().startsWith("_isLoadingMore.value = false"))
        assertFalse(loadMore.contains("_isLoadingMore.value = true"))
    }
}
