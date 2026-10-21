package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «Borrar caché de imágenes no funciona». The button used to `File.delete()` Coil's files behind
 * Coil's back, so `diskCache.size` (an in-memory counter) never dropped, the memory cache kept every cover and
 * nothing confirmed the action. These pin the logic and the shape of the fix.
 */
class ImageCacheCleanerTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `freed bytes are the real drop and never negative`() {
        assertEquals(300L, ImageCacheCleaner.freedBytes(1_000L, 700L))
        assertEquals(0L, ImageCacheCleaner.freedBytes(500L, 500L))
        // a loader wrote a cover while we were clearing: report 0, not a negative amount
        assertEquals(0L, ImageCacheCleaner.freedBytes(100L, 250L))
    }

    @Test
    fun `preserved covers are taken in order within the budget and an oversized one does not stop the rest`() {
        assertEquals(listOf(0, 1, 2), ImageCacheCleaner.indicesWithinBudget(listOf(10L, 20L, 30L), 60L))
        assertEquals(listOf(0, 2), ImageCacheCleaner.indicesWithinBudget(listOf(10L, 100L, 30L), 60L))
        assertEquals(emptyList<Int>(), ImageCacheCleaner.indicesWithinBudget(listOf(10L), 5L))
        assertEquals(emptyList<Int>(), ImageCacheCleaner.indicesWithinBudget(emptyList(), 5L))
    }

    @Test
    fun `the cleaner empties Coil through its own API, memory and disk, and the animated covers`() {
        val cleaner = src("app/src/main/kotlin/com/music/echo/utils/ImageCacheCleaner.kt")
        assertTrue(cleaner.contains("loader.memoryCache?.clear()"))
        assertTrue(cleaner.contains("disk.clear()"))
        assertTrue(cleaner.contains("cache.removeResource(key)"))
        // downloaded songs' covers live ONLY in this cache: they are read back after the clear
        assertTrue(cleaner.contains("disk.openSnapshot(url)"))
        assertTrue(cleaner.contains("disk.openEditor(entry.key)"))
        // never reach the player / download caches or the lyrics
        assertFalse(cleaner.contains("playerCache"))
        assertFalse(cleaner.contains("downloadCache"))
    }

    @Test
    fun `the storage screen no longer deletes Coil's files behind its back and confirms with the freed size`() {
        val screen = src("app/src/main/kotlin/com/music/echo/ui/screens/settings/StorageSettings.kt")
        val dialog = screen.substringAfter("if (clearImageCacheDialog) {").substringBefore("if (showCacheWarningDialog)")
        assertTrue(dialog.contains("ImageCacheCleaner.clearAll("))
        assertFalse("borrar archivos de Coil a mano deja `size` sin cambiar", dialog.contains("file.delete()"))
        assertFalse(dialog.contains("listFiles()"))
        // the label is refreshed right away and the owner gets a toast with the freed space
        assertTrue(dialog.contains("imageCacheSize = context.imageLoader.diskCache?.size"))
        assertTrue(dialog.contains("R.string.image_cache_cleared"))
    }
}
