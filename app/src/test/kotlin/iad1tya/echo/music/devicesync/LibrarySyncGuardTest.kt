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
        assertTrue(e.contains("library?.onConnected(helloLib)"))
        assertTrue(e.contains("if (libraryEnabled && lib != null)"))
        assertTrue("con la biblioteca por el servidor un aviso «library» no consulta a YouTube", e.contains("if (libraryEnabled) return"))
    }

    @Test
    fun `a pull is never sent before the room has answered, and registers go out parents first`() {
        val l = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue(l.contains("if (!connected || !isEnabled() || !pulledOnce) {"))
        assertTrue(l.contains("sortedBy { rank(it.key) }"))
        assertTrue("una sola descarga a la vez (tormenta de la beta 4)", l.contains("if (pullInFlight)"))
        for (rank in listOf("\"sg\", \"ag\" -> 0", "\"pl\" -> 1", "\"pm\", \"ab\" -> 2", "\"lk\" -> 3")) assertTrue(rank, l.contains(rank))
    }

    @Test
    fun `each room keeps its own ledger and since, and the library sync follows the room`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAndroid.kt")
        assertTrue(a.contains("File(context.filesDir, \"device_sync/\$room/ledger.tsv\")"))
        assertTrue(a.contains("val sinceKey = \"libSince_\$room\""))
        assertTrue("los archivos de antes pasan a la sala actual", a.contains("legacy.renameTo(ledgerFile)"))
        val e = read("app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt")
        val swap = e.substringAfter("if (groupId != null && groupId != libraryGroup) {").substringBefore("deviceId = ensureDeviceId()")
        assertTrue("al cambiar de sala se para la sincronización anterior", swap.contains("library?.stop()"))
        assertTrue("y se crea una nueva para la sala nueva", swap.contains("LibrarySyncAndroid.create(") && swap.contains("groupId)"))
    }

    @Test
    fun `a restored backup forgets every room's ledger and starts the next pull fresh`() {
        val v = read("app/src/main/kotlin/com/music/echo/viewmodels/BackupRestoreViewModel.kt")
        val replaced = v.substringAfter("YtmUploadSyncKey] =").substringBefore("restartApp(context)")
        assertTrue("tras reemplazar la base de datos y antes de reiniciar", replaced.contains("LibrarySyncAndroid.forgetAfterRestore(context)"))
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAndroid.kt")
        assertTrue(a.contains("override fun freshStart() = fresh.isFile"))
        val l = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue("el freno sigue activo tras restaurar", l.contains("if (LibrarySyncCore.brakeTrips(plan.removalCount, known)) {") && !l.contains("!freshStart && LibrarySyncCore.brakeTrips("))
    }

    @Test
    fun `likes that arrive through the room are also sent to the YouTube account, a few at a time`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAdapter.kt")
        assertTrue(a.contains("mirrorLikesToAccount(changedLikes)"))
        assertTrue(a.contains("AccountPushPolicy.Op.SONG_LIKE"))
        assertTrue("una sincronización inicial grande no se reenvía una a una a YouTube", a.contains("changed.size > MIRROR_MAX"))
    }

    @Test
    fun `the worker README says what is stored`() {
        val r = read("docs/worker/sync/README.md")
        assertTrue(r.contains("lib_push"))
    }
}
