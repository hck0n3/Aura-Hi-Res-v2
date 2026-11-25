package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 862 (owner test #34, Galaxy Z Fold7 / One UI): the launcher showed only four shortcuts and «Descargado»,
 * published 5th with no explicit rank, was the one left out. The four that launchers like that keep must be «Seguir
 * escuchando», «Aleatorio de Me gusta», «Descargado» and «Buscar»; «Biblioteca» stays published as the 5th.
 */
class LauncherShortcutsOrderTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val source: String by lazy {
        File(root, "app/src/main/kotlin/com/music/echo/utils/LauncherShortcuts.kt")
            .also { assertTrue("LauncherShortcuts.kt moved", it.isFile) }.readText()
    }

    @Test
    fun `the four a four-slot launcher keeps are resume, shuffle liked, downloaded and search`() {
        assertEquals(
            listOf("resume_queue", "shuffle_liked", "downloaded", "search_v2"),
            LauncherShortcuts.LAUNCHER_ORDER.take(4),
        )
    }

    @Test
    fun `library stays published as the fifth and the ids are the stable pinned ones`() {
        assertEquals(
            listOf("resume_queue", "shuffle_liked", "downloaded", "search_v2", "library_v2"),
            LauncherShortcuts.LAUNCHER_ORDER,
        )
    }

    @Test
    fun `launcherOrder sorts whatever order the shortcuts were built in`() {
        assertEquals(
            LauncherShortcuts.LAUNCHER_ORDER,
            LauncherShortcuts.launcherOrder(listOf("search_v2", "library_v2", "resume_queue", "shuffle_liked", "downloaded")),
        )
        assertEquals(listOf("downloaded", "library_v2"), LauncherShortcuts.launcherOrder(setOf("library_v2", "downloaded")))
        // An id with no place in the order is dropped instead of reaching setRank with -1 (which throws).
        assertEquals(listOf("search_v2"), LauncherShortcuts.launcherOrder(listOf("unknown", "search_v2")))
    }

    @Test
    fun `every published id has a rank and the published list follows the rank`() {
        val published = Regex("shortcut\\(context, \"([a-z_0-9]+)\"").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(LauncherShortcuts.LAUNCHER_ORDER.toSet(), published)
        assertTrue(source.contains("val all = launcherOrder(builders.keys).map { id -> builders.getValue(id).setRank(LAUNCHER_ORDER.indexOf(id)).build() }"))
        // The launcher-limit cut still happens on the ranked list, so it drops «Biblioteca» first.
        assertTrue(source.indexOf("val shortcuts = all.take(allowed)") > source.indexOf("val all = launcherOrder("))
        assertTrue(source.contains("manager.setDynamicShortcuts(shortcuts)"))
    }
}
