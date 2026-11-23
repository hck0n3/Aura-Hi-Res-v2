package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 762 (same class as row 585): the settings line of the shared-log header read crossfade as ON
 * and safe volume as OFF for a device that never touched them, while the player service reads crossfade OFF
 * and safe volume ON. A log that contradicts what the device was doing sends the diagnosis the wrong way.
 * Every default the header reads must equal the one MusicService acts on.
 */
class DiagnosticHeaderDefaultsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val header by lazy {
        read("app/src/main/kotlin/com/music/echo/App.kt")
            .substringAfter("DiagnosticHeader.Settings(")
            .substringBefore("loggedIn =")
    }

    private val service by lazy { read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt") }

    private fun defaultsOf(source: String, key: String): Set<String> =
        Regex("""\[(?:[\w.]+\.)?$key\]\s*\?:\s*(true|false)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSet()

    private fun assertSameDefault(key: String) {
        val inService = defaultsOf(service, key)
        assertEquals("MusicService must read $key with one default: $inService", 1, inService.size)
        assertEquals("header default of $key", inService, defaultsOf(header, key))
    }

    @Test
    fun `crossfade default in the header equals the service`() = assertSameDefault("CrossfadeEnabledKey")

    @Test
    fun `safe volume default in the header equals the service`() = assertSameDefault("SafeVolumeEnabledKey")

    @Test
    fun `enhanced shuffle default in the header equals the service - row 585`() = assertSameDefault("EnhancedShuffleKey")

    @Test
    fun `audio offload default in the header equals the service`() = assertSameDefault("AudioOffload")
}
