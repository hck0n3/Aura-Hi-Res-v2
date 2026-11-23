package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 785: the launcher shortcuts (long-press Buscar / Biblioteca) must open THIS build, whatever its applicationId
 * (`iad1tya.aura.music`, `.debug`, `.dev` with -Pnosub). The static `res/xml/shortcuts.xml` hardcoded the old frozen app's
 * package; the shortcuts are now built at runtime from the running app's own Context.
 */
class LauncherShortcutsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `no resource names a target package, so nothing can point at another app`() {
        File(root, "app/src").walkTopDown()
            .filter { it.isFile && it.extension == "xml" && "/res/" in it.invariantSeparatorsPath }
            .forEach { assertFalse("${it.path} hardcodes a targetPackage", it.readText().contains("android:targetPackage")) }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse("a static shortcuts.xml cannot follow the applicationId", manifest.contains("android.app.shortcuts"))
    }

    @Test
    fun `the shortcuts take the package from the running app and keep the MainActivity actions`() {
        val shortcuts = main("utils/LauncherShortcuts.kt")
        assertTrue(shortcuts.contains(".setIntent(Intent(action).setClassName(context, MainActivity::class.java.name))"))
        assertTrue(shortcuts.contains("manager.setDynamicShortcuts(shortcuts)"))
        assertFalse("no package literal in code", Regex("\"iad1tya\\.").containsMatchIn(shortcuts))
        assertTrue(shortcuts.contains("R.string.search, R.drawable.shortcut_search"))
        assertTrue(shortcuts.contains("R.string.filter_library, R.drawable.shortcut_library"))

        val activity = main("MainActivity.kt")
        assertTrue(activity.contains("LauncherShortcuts.publish(this, ACTION_SEARCH, ACTION_LIBRARY)"))
        assertTrue(activity.contains("private const val ACTION_SEARCH = \"iad1tya.echo.music.action.SEARCH\""))
        assertTrue(activity.contains("private const val ACTION_LIBRARY = \"iad1tya.echo.music.action.LIBRARY\""))
        assertTrue(activity.contains("ACTION_SEARCH -> NavigationTab.SEARCH"))
        assertTrue(activity.contains("ACTION_LIBRARY -> NavigationTab.LIBRARY"))
        assertTrue(activity.contains("if (intent.action == ACTION_SEARCH || intent.action == ACTION_LIBRARY) {"))
    }
}
