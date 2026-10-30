package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 536 — the wiring that no pure test sees: YouTube's pulls stay out of the way, the engine hands the library its messages. */
class LibrarySyncGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun read(path: String) = File(root, path).readText()

    @Test
    fun `YouTube's PULL passes for likes and playlists are skipped while the devices own the library`() {
        val s = read("app/src/main/kotlin/com/music/echo/utils/SyncUtils.kt")
        for (pass in listOf("likedSongs", "savedPlaylists", "autoSyncPlaylists")) {
            assertTrue("falta el freno en $pass", s.contains("if (libraryOwnedByDevices(\"$pass\")) return@withContext"))
        }
        assertTrue(s.contains("if (libraryOwnedByDevices(\"playlist\")) return@launch"))
        assertTrue(s.contains("if (!libraryOwnedByDevices(\"playlist\")) executeSyncPlaylist(operation.browseId, operation.playlistId)"))
        // the user's own pushes to the YouTube account are NOT gated
        assertTrue(s.contains("is SyncOperation.LikeSong -> executeLikeSong(operation.song)"))
        assertTrue("el «sincronizar ahora» explícito de una lista sigue funcionando", !s.substringAfter("suspend fun syncPlaylistNow").substringBefore("fun syncAllAlbums").contains("libraryOwnedByDevices"))
    }

    @Test
    fun `the engine gives the library its messages first and stops the old ping when the library goes through the room`() {
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        assertTrue(e.contains("if (library?.handle(msg) == true) return"))
        assertTrue(e.contains("library?.onConnected()"))
        assertTrue(e.contains("if (libraryEnabled && lib != null)"))
        assertTrue("con la biblioteca por el servidor un aviso «library» no consulta a YouTube", e.contains("if (libraryEnabled) return"))
    }

    @Test
    fun `a pull is never sent before the room has answered, and registers go out parents first`() {
        val l = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue(l.contains("if (!connected || !isEnabled() || !pulledOnce) return"))
        assertTrue(l.contains("sortedBy { rank(it.key) }"))
        assertEquals("sg < pl < pm < lk < resto", 5, Regex("\"(sg|pl|pm|lk)\" -> \\d").findAll(l).count() + 1)
    }

    @Test
    fun `the worker README says what is stored`() {
        val r = read("docs/worker/sync/README.md")
        assertTrue(r.contains("lib_push"))
    }
}
