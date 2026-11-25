package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 865: ffmpeg-kit-full 6.0-2 (abandoned, aliyun-only) replaced by the maintained audio build. */
class FfmpegKitAudioGuardTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `the app depends on the maintained audio build, not the abandoned one`() {
        val catalog = text("gradle/libs.versions.toml")
        assertTrue(catalog.contains("ffmpeg-kit-audio = { module = \"com.antonkarpenko:ffmpeg-kit-audio\", version.ref = \"ffmpegKitAudio\" }"))
        assertFalse(catalog.contains("com.arthenica"))
        assertTrue(text("app/build.gradle.kts").contains("implementation(libs.ffmpeg.kit.audio)"))
    }

    @Test
    fun `every call site uses the new package`() {
        val export = text("app/src/main/kotlin/com/music/echo/playback/AudioExportService.kt")
        val scanner = text("app/src/main/kotlin/com/music/echo/localmedia/LocalSongScanner.kt")
        assertTrue(export.contains("import com.antonkarpenko.ffmpegkit.FFmpegKit"))
        assertTrue(export.contains("import com.antonkarpenko.ffmpegkit.ReturnCode"))
        assertTrue(scanner.contains("import com.antonkarpenko.ffmpegkit.FFmpegKitConfig"))
        assertTrue(scanner.contains("import com.antonkarpenko.ffmpegkit.FFprobeKit"))
        val sources = File(root, "app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
        assertFalse(sources.any { it.readText().contains("com.arthenica") })
    }

    @Test
    fun `MP3 export keeps its encoder and the video export only stream-copies`() {
        val export = text("app/src/main/kotlin/com/music/echo/playback/AudioExportService.kt")
        assertTrue("MP3 export still encodes with LAME", export.contains("-c:a libmp3lame -b:a 320k"))
        assertTrue("MP3 export still embeds the cover", export.contains("-c:v mjpeg -disposition:v attached_pic"))
        assertTrue(export.contains("\"-c:v copy -c:a aac -b:a 192k \""))
        assertFalse("no build of ffmpeg-kit the app can use ships libx264", export.contains("libx264"))
    }

    @Test
    fun `release keeps the JNI callbacks the replacement AAR does not protect itself`() {
        val rules = text("app/proguard-rules.pro")
        assertTrue(rules.contains("-keep class com.antonkarpenko.ffmpegkit.FFmpegKitConfig {"))
        assertTrue(rules.contains("void log(long, int, byte[]);"))
        assertTrue(rules.contains("void statistics(long, int, float, float, long, double, double, double);"))
        assertTrue(rules.contains("int safOpen(int);"))
        assertTrue(rules.contains("int safClose(int);"))
        assertTrue(rules.contains("-keep class com.antonkarpenko.ffmpegkit.AbiDetect {"))
    }
}
