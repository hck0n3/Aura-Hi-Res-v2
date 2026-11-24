package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 810 (owner H4, 2026-11-24): «Seguir escuchando», «Aleatorio de Me gusta» and «Descargado» join the row-785
 * shortcuts. No Compose/Android runtime in the unit job, so the wiring is pinned as source text.
 */
class LauncherShortcutsDynamicGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path missing", it.isFile) }.readText()

    @Test
    fun `the three new shortcuts are published with the row-785 ones and never exceed the launcher limit`() {
        val s = main("utils/LauncherShortcuts.kt")
        assertTrue(s.contains("ACTION_RESUME, R.string.shortcut_resume, R.drawable.shortcut_resume"))
        assertTrue(s.contains("ACTION_SHUFFLE_LIKED, R.string.shortcut_shuffle_liked, R.drawable.shortcut_shuffle"))
        assertTrue(s.contains("ACTION_DOWNLOADED, R.string.shortcut_downloaded, R.drawable.shortcut_downloaded"))
        // Too many would make setDynamicShortcuts throw and lose all five, Buscar and Biblioteca included.
        assertTrue(s.contains("manager.maxShortcutCountPerActivity"))
        assertTrue(s.contains("val shortcuts = all.take(allowed)"))
    }

    @Test
    fun `playing waits on events with a bound, never on a timer`() {
        val s = main("utils/LauncherShortcuts.kt")
        assertTrue(s.contains("val restored = withTimeoutOrNull(RESUME_WAIT_MS) {"))
        assertTrue(s.contains("connection.mediaMetadata.first { it != null }"))
        // Play only after the saved index/position are applied, or it starts the queue file's (stale) song first.
        assertTrue(s.contains("connection.service.playerStateRestoreDone.first { it }"))
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("val playerStateRestoreDone = MutableStateFlow(false)"))
        assertTrue(service.contains("}.invokeOnCompletion { playerStateRestoreDone.value = true }"))
        assertTrue(service.contains("if (!playerStateRestoreLaunched) playerStateRestoreDone.value = true"))
        assertFalse("rule 7: no polling", s.contains("delay("))
        assertFalse("rule 7: no polling", s.contains("while ("))
        // Same queue as the «Me gusta» screen's shuffle button.
        assertTrue(s.contains("private const val LIKED_CONTEXT_ID = \"AP:liked\""))
        assertTrue(s.contains("startShuffled = true,"))
        assertTrue(s.contains("database.playedSongIdsForContextFlow(LIKED_CONTEXT_ID)"))
        // Rule 4: counts and fixed texts only.
        assertFalse(s.contains("Timber.tag(TAG).i("))
    }

    @Test
    fun `MainActivity routes the three actions`() {
        val a = main("MainActivity.kt")
        assertTrue(a.contains("shortcutAction in iad1tya.echo.music.utils.LauncherShortcuts.ACTIONS"))
        assertTrue(a.contains("navController.navigateToReentryTarget(\"auto_playlist/downloaded\")"))
        // Not .immediate: the resume must land after the queue restore's own playWhenReady = false.
        assertTrue(a.contains("lifecycleScope.launch(Dispatchers.Main) {"))
        assertTrue(a.contains("iad1tya.echo.music.utils.LauncherShortcuts.play(shortcutAction, connection, database, this@MainActivity)"))
        // Reopening from Recents re-delivers the shortcut that started the task: it must not replace the queue or play.
        val historyGate = a.indexOf("if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return")
        assertTrue(historyGate > a.indexOf("navController.navigateToReentryTarget(\"auto_playlist/downloaded\")"))
        assertTrue(historyGate < a.indexOf("iad1tya.echo.music.utils.LauncherShortcuts.play(shortcutAction, connection, database, this@MainActivity)"))
    }

    @Test
    fun `labels exist in English and Spanish and the icons are drawn dark like the row-785 ones`() {
        val en = res("values/launcher_shortcuts_strings.xml")
        val es = res("values-es/launcher_shortcuts_strings.xml")
        listOf("shortcut_resume", "shortcut_shuffle_liked", "shortcut_downloaded").forEach {
            assertTrue(en.contains("name=\"$it\""))
            assertTrue(es.contains("name=\"$it\""))
        }
        assertTrue(es.contains(">Seguir escuchando<"))
        assertTrue(es.contains(">Aleatorio de Me gusta<"))
        assertTrue(es.contains(">Descargado<"))
        listOf("shortcut_resume", "shortcut_shuffle", "shortcut_downloaded").forEach {
            assertTrue("$it must not be white on a white launcher sheet", res("drawable/$it.xml").contains("android:fillColor=\"#000000\""))
        }
    }
}
