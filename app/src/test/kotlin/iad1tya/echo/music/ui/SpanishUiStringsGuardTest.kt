package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX audit P22C (registry row 734): the Spanish UI still showed English — «Added to Liked songs», «Song removed from
 * playlist» (keys with no `values-es` entry), «Lyrics from …», «… copied», the ringtone trimmer and its progress
 * messages, the recognition history's empty states and group headers (texts hardcoded in Kotlin).
 */
class SpanishUiStringsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path se movió — actualiza este test", it.isFile) }.readText()

    private fun main(path: String) = src("app/src/main/kotlin/com/music/echo/$path")

    @Test
    fun `user-visible keys that had no Spanish entry now have one`() {
        val es = src("app/src/main/res/values-es/ux_i18n_strings.xml")
        for (key in listOf("song_liked_toast", "song_removed_from_playlist", "home_syncing_library", "on_latest_version")) {
            assertTrue(key, es.contains("<string name=\"$key\">"))
        }
    }

    @Test
    fun `the English literals are gone from the screens that showed them`() {
        val literals = mapOf(
            "ui/component/Lyrics.kt" to listOf("\"Lyrics from "),
            "ui/utils/ShowMediaInfo.kt" to listOf("\"\$label copied\""),
            "ui/utils/ShowOffsetDialog.kt" to listOf("\"Reset\"", "\"Decrease\"", "\"Increase\""),
            "ui/screens/recognition/RecognitionHistoryScreen.kt" to
                listOf("\"Today\"", "\"Yesterday\"", "\"This Week\"", "\"No recognition history\"", "\"No results for "),
            "ui/screens/ambient/AmbientModeScreen.kt" to listOf("\"Album Art\""),
            "ui/component/RingtoneTrimmerDialog.kt" to
                listOf("\"Trim Ringtone\"", "\"Select the part of ", "Preview\"", "\"Selected duration: "),
            "ui/component/RingtoneProgressDialog.kt" to listOf("\"Success!\"", "\"Failed\"", "\"Setting Ringtone...\"", "\"Open Settings\""),
            "utils/RingtoneHelper.kt" to listOf(
                "\"Getting audio stream...\"", "\"Fetching audio...\"", "\"Processing audio...\"", "\"Saving ringtone...\"",
                "\"Song saved to Ringtones...\"", "\"Done!\"", "\"Error: ", "\"Failed to get audio stream\"",
                "\"Failed to prepare source file\"", "\"Failed to process audio",
            ),
        )
        for ((file, banned) in literals) {
            val text = main(file)
            for (literal in banned) assertFalse("$file: $literal", text.contains(literal))
        }
    }

    @Test
    fun `the ringtone helper localises the progress of the cache copy and the download too`() {
        val helper = main("utils/RingtoneHelper.kt")
        assertTrue(helper.contains("onProgress(progress, context.getString(R.string.ringtone_progress_cached))"))
        assertTrue(helper.contains("onProgress(progress, context.getString(R.string.ringtone_progress_downloading, (progress * 100).toInt()))"))
    }

    @Test
    fun `every new text exists in English and Spanish`() {
        val en = src("app/src/main/res/values/ux_i18n_strings.xml")
        val es = src("app/src/main/res/values-es/ux_i18n_strings.xml")
        val keys = Regex("""<string name="([a-z_]+)">""").findAll(en).map { it.groupValues[1] }.toList()
        assertTrue(keys.size >= 20)
        for (key in keys) assertTrue("$key (es)", es.contains("<string name=\"$key\">"))
    }
}
