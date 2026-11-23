package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 761 (continues row 77): `YTPlayerUtils.forceRefreshForVideo` only wrote a debug line, yet the
 * recovery paths called it and logged «Cleared decryption caches» — a claim nothing backed. It is deleted with
 * its calls, and so are the unused Qobuz/Saavn toast flags and the per-resolve read of a setting nobody used.
 * Nothing new invalidates a cache: whether the cipher caches need it stays an open question (S-003).
 */
class DeadResolverCodeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val ytp by lazy { read("app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt") }
    private val recovery by lazy { read("app/src/main/kotlin/com/music/echo/playback/PlaybackErrorRecovery.kt") }

    @Test
    fun `forceRefreshForVideo no longer exists and nothing calls it`() {
        assertFalse(ytp.contains("forceRefreshForVideo"))
        assertFalse(recovery.contains("forceRefreshForVideo"))
    }

    @Test
    fun `recovery no longer claims to clear decryption caches`() {
        assertFalse(recovery.contains("Cleared decryption caches"))
        assertFalse(recovery.contains("Failed to clear decryption caches"))
    }

    @Test
    fun `the unused toast flags and the fallback-toast read are gone`() {
        assertFalse(ytp.contains("hasShownLosslessToast"))
        assertFalse(ytp.contains("hasShownSaavnToast"))
        assertFalse(ytp.contains("showFallbackToast"))
    }

    @Test
    fun `the recovery still drops the cached URL and the player cache`() {
        assertTrue(recovery.contains("songUrlCache.remove(mediaId)"))
        assertTrue(recovery.contains("StreamCacheKeys.keysOf(playerCache.keys, mediaId)"))
    }
}
