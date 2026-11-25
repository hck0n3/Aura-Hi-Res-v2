package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 864 — Android 17 background audio hardening. With targetSdk 37 the system silences playback and refuses
 * audio focus when audio starts in the background without a while-in-use foreground service; nothing throws. Aura keeps
 * to the documented mitigation (media3 MediaLibraryService with a mediaPlayback foreground service, and the existing pause
 * when the foreground start is refused); a refused focus request is logged so a silent «playing» state can be told apart
 * in the shared log. Source-text checks: focus and foreground services only exist on a device.
 */
class BackgroundAudioHardeningGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    private val service by lazy { read("app/src/main/kotlin/com/music/echo/playback/MusicService.kt") }

    @Test
    fun `a refused focus request on Android 17 is logged`() {
        val start = service.indexOf("private fun requestAudioFocus(): Boolean {")
        assertTrue("requestAudioFocus not found", start >= 0)
        val end = service.indexOf("\n    }\n", start)
        val body = service.substring(start, end)
        assertTrue(body.contains("result == AudioManager.AUDIOFOCUS_REQUEST_FAILED && Build.VERSION.SDK_INT >= 37"))
        assertTrue(body.contains("\"AUDIO_FOCUS_REFUSED sdk=%d mode=%d\""))
    }

    @Test
    fun `playback still runs in a media3 service with a mediaPlayback foreground service`() {
        assertTrue(service.contains("class MusicService :\n    MediaLibraryService(),"))
        val manifest = read("app/src/main/AndroidManifest.xml")
        val declared = Regex(
            """android:name="\.playback\.MusicService"\s+android:exported="true"\s+android:foregroundServiceType="mediaPlayback"""",
        )
        assertTrue("MusicService lost its mediaPlayback foreground service type", declared.containsMatchIn(manifest))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test
    fun `a refused foreground start still pauses instead of playing unmanaged`() {
        assertTrue(service.contains("override fun onForegroundServiceStartNotAllowedException() {"))
        assertTrue(service.contains("pausedByForegroundRefusal = true"))
    }
}
