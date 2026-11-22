package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22: «que Aura detecte que no hay internet por cualquier motivo y pase el reproductor y su cola a
 * modo sin conexión, para que la siguiente canción nunca se quede cargando». Source-text checks on purpose: the
 * routing needs a real player, a real loader thread and a real loss of network, none of which runs in a JVM test.
 */
class OfflineFallbackGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val service by lazy { main("playback/MusicService.kt") }
    private val resolver by lazy { main("playback/StreamResolution.kt") }

    private val onPlayerError by lazy {
        val start = service.indexOf("override fun onPlayerError(error: PlaybackException) {\n        super.onPlayerError(error)")
        assertTrue("onPlayerError not found", start >= 0)
        val end = service.indexOf("\n    }\n", start)
        service.substring(start, end)
    }

    /** Both refusals of the resolver carry the marker cause, so onPlayerError can recognise them through media3's wrapping. */
    @Test
    fun `every offline refusal of the resolver carries its own marker`() {
        assertFalse(
            "a refusal without the marker is classified as a generic I/O error again",
            resolver.contains("getString(R.string.error_offline_not_downloaded),\n                    null,"),
        )
        assertFalse(resolver.contains("getString(R.string.error_offline_not_downloaded),\n                null,"))
        assertTrue(resolver.contains("OfflineRejectionException(directRejection)"))
        assertTrue(resolver.contains("OfflineRejectionException(offlineRejection)"))
    }

    /**
     * An offline refusal is routed BEFORE the generic I/O fallthrough (handleGenericIOError → performAggressiveCacheClear),
     * and its branch never purges, never retries the same song.
     */
    @Test
    fun `an offline refusal goes to the offline skip and never reaches the purge`() {
        val branchStart = onPlayerError.indexOf("PlaybackErrorClassifier.isOfflineRejection(error) -> {")
        val generic = onPlayerError.indexOf("handleGenericIOError(mediaId)")
        assertTrue(branchStart > 0)
        assertTrue("the offline branch must come before the generic I/O handler", branchStart < generic)
        val branch = onPlayerError.substring(branchStart, onPlayerError.indexOf("!isNetworkConnected.value -> {", branchStart))
        assertTrue(branch.contains("findNextOfflinePlayableIndex()"))
        assertTrue(branch.contains("skipToOfflinePlayableSong(offlineTargetIndex)"))
        assertFalse(branch.contains("performAggressiveCacheClear"))
        assertFalse(branch.contains("incrementRetryCount"))
        assertFalse(branch.contains("handleGenericIOError"))
    }

    /** The bounded network retry keeps the bytes (a dead link says nothing about audio already on disk). */
    @Test
    fun `the network branches never purge the song bytes`() {
        val start = onPlayerError.indexOf("!isNetworkConnected.value -> {")
        val end = onPlayerError.indexOf("handleGenericIOError(mediaId)")
        assertTrue(start in 1 until end)
        assertFalse(onPlayerError.substring(start, end).contains("performAggressiveCacheClear"))
    }
}
