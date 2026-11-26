package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 899 (plan phase 7): JankStats as aggregate counts per screen, debug only, nothing user-identifying. */
class JankReportTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `counts are per screen and start again after a report`() {
        val t = JankTally()
        repeat(3) { t.record("home", false) }
        t.record("home", true)
        t.record("album", true)
        assertEquals("screen=home frames=4 janky=1 (25.0%)", t.drain("home"))
        assertNull("drained", t.drain("home"))
        assertEquals("screen=album frames=1 janky=1 (100.0%)", t.drain("album"))
        assertNull("a screen that drew nothing reports nothing", t.drain("search"))
        assertEquals("screen=x frames=0 janky=0 (0.0%)", JankTally.line("x", 0, 0))
    }

    @Test
    fun `the screen name is the route template without its arguments or query`() {
        assertEquals("album", JankTally.screenOf("album/{albumId}"))
        assertEquals("settings/performance", JankTally.screenOf("settings/performance"))
        assertEquals("search", JankTally.screenOf("search/{query}"))
        assertEquals("online_playlist", JankTally.screenOf("online_playlist/{playlistId}?filter={filter}"))
        assertEquals(JankTally.OTHER, JankTally.screenOf(null))
        assertEquals(JankTally.OTHER, JankTally.screenOf("{id}"))
        assertEquals("a value that slipped into a route never reaches the log", JankTally.OTHER, JankTally.screenOf("search/Hello World"))
        assertEquals(JankTally.OTHER, JankTally.screenOf("a".repeat(49)))
    }

    @Test
    fun `it only starts in debug builds, logs below INFO, and the activity drives it by events`() {
        val report = src("utils/JankReport.kt")
        assertTrue(report.contains("if (!BuildConfig.DEBUG || stats != null) return"))
        assertTrue("DEBUG level: AppLogger persists INFO+ only", report.contains("Timber.tag(\"Jank\").d(it)"))
        assertFalse(Regex("""Timber(\.tag\([^)]*\))?\.[iwe]\(""").containsMatchIn(report))
        assertFalse("no timer", report.contains("while (") || report.contains("delay(") || report.contains("postDelayed"))
        val main = src("MainActivity.kt")
        assertTrue(main.contains("iad1tya.echo.music.utils.JankReport.attach(window)"))
        assertTrue(main.contains("LaunchedEffect(currentRoute) { iad1tya.echo.music.utils.JankReport.onRoute(currentRoute) }"))
        val pause = main.substringAfter("override fun onPause()").substringBefore("override fun onCreate(")
        assertTrue(pause.contains("JankReport.setTracking(false)") && pause.contains("JankReport.flush()"))
    }
}
