package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 762: leftovers of the import follow path. Since row 646 the Spotify import follows an artist
 * in Aura only and never subscribes the account, yet its INFO line still said `ARTIST_SUBSCRIBE_IMPORT` —
 * exactly the word the owner greps when he asks «who subscribed me». The line now says what happens.
 */
class ImportLogLeftoversGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun read(path: String): String {
        val f = File(root, path)
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the Spotify import logs a local follow, never a subscribe`() {
        val src = read("app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportRepository.kt")
        assertFalse(src.contains("ARTIST_SUBSCRIBE_IMPORT"))
        assertTrue(src.contains("Timber.i(\"ARTIST_IMPORT_LOCAL_FOLLOW heldForConfirm=\${existing?.followedByUserAt == null}\")"))
    }
}
