package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 421 (owner 2026-10-18): «el primer cambio de música a modo vídeo hay un corte; después ya no».
 *
 * The first entry into video used to rebuild the playing item whenever the speculative pre-player was not ready (the audio
 * halts while the merged source refills). It now publishes an ON-DEMAND pre-player while the audio keeps playing. These pin
 * the wiring a refactor could silently undo: the entry goes through the seamless path first, ONLY for an item that still
 * plays plain audio, any doubt falls back to the rebuild, and leaving video / changing track cancels the waiting job.
 */
class SeamlessVideoEntryTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun playback(name: String): String {
        val f = File(repoRoot, "app/src/main/kotlin/com/music/echo/playback/$name")
        assertTrue("$name se movió — actualiza este test", f.isFile)
        return f.readText()
    }

    @Test
    fun `swapToVideo tries the seamless entry first, only for an item that still plays audio, and falls back to the rebuild`() {
        val src = playback("VideoModeCoordinator.kt")
        val wrapper = src.substringAfter("private fun swapToVideo(").substringBefore("private fun swapToVideoRebuild(")
        assertTrue("el cambio debe intentar la entrada sin corte", wrapper.contains("service.beginSeamlessVideoSwap("))
        assertTrue(
            "solo un ítem que aún suena como audio (un reintento / re-cambio reconstruye)",
            wrapper.contains("!videoModeItems.containsKey(id)"),
        )
        assertTrue("sin plan B no hay entrada: debe reconstruir", wrapper.contains("swapToVideoRebuild("))
        assertTrue("la reconstrucción debe seguir existiendo", src.contains("private fun swapToVideoRebuild("))
    }

    @Test
    fun `the fallback is guarded against a track change, a newer swap and leaving video`() {
        val wrapper = playback("VideoModeCoordinator.kt")
            .substringAfter("private fun swapToVideo(").substringBefore("private fun swapToVideoRebuild(")
        assertTrue(wrapper.contains("videoSwapGeneration.get() == gen"))
        assertTrue(wrapper.contains("service._videoMode.value"))
        assertTrue(wrapper.contains("service.player.currentMediaItem?.mediaId == id"))
    }

    @Test
    fun `every doubt keeps the rebuild and the waiting job dies with the teardown`() {
        val src = playback("MusicService.kt")
        val begin = src.substringAfter("internal fun beginSeamlessVideoSwap(").substringBefore("    // ── SOFT VIDEO EXIT")
        listOf(
            "shuffleModeEnabled", "isCrossfading", "castConnectionHandler?.isCasting", "playbackState != Player.STATE_READY",
            "SEAMLESS_VIDEO_ENTRY_ENABLED", "SEAMLESS_VIDEO_READY_TIMEOUT_MS", "tryInstantVideoSwap()", "onFallback()",
        ).forEach { assertTrue("falta «$it» en la entrada sin corte", begin.contains(it)) }
        val teardown = src.substringAfter("internal fun teardownInstantVideoSwap(").substringBefore("    // ── SEAMLESS FIRST ENTRY")
        assertTrue("el teardown debe cancelar la espera", teardown.contains("seamlessEntryJob?.cancel()"))
    }
}
