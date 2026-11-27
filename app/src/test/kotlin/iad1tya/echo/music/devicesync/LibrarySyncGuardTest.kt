package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * Row 754: every table the library sync reads must wake it. Saving an album only writes the `album` table (no libraryChanged
     * ping), so without it a save made offline was never stamped and one made online waited for an unrelated change.
     */
    @Test
    fun `the database watcher covers every table the library sync reads, saved albums included`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAndroid.kt")
        assertTrue(a.contains("InvalidationTracker.Observer(\"song\", \"playlist\", \"playlist_song_map\", \"speed_dial_item\", \"album\")"))
        val adapter = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAdapter.kt")
        assertTrue("the adapter reads the saved albums", adapter.contains("database.syncSavedAlbumRows()"))
        val dao = read("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue("from the album table", dao.substringBefore("suspend fun syncSavedAlbumRows()").substringAfterLast("@Query(").contains(" FROM album "))
    }

    @Test
    fun `a restored backup forgets every room's ledger and starts the next pull fresh`() {
        val v = read("app/src/main/kotlin/com/music/echo/viewmodels/BackupRestoreViewModel.kt")
        val replaced = v.substringAfter("bakFile.delete() // success").substringBefore("} catch (copyError: Exception) {")
        assertTrue("en cuanto la copia de la base de datos sale bien, antes de cualquier otra escritura", replaced.contains("LibrarySyncAndroid.forgetAfterRestore(context)"))
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAndroid.kt")
        assertTrue(a.contains("override fun freshStart() = fresh.isFile"))
        val l = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue("el freno sigue activo tras restaurar", l.contains("if (LibrarySyncCore.brakeTrips(plan.removalCount, known)) {") && !l.contains("!freshStart && LibrarySyncCore.brakeTrips("))
    }

    /**
     * Row 835 replaces row 542 (owner 2026-11-24, «haz K1, K2 y K3»: «que no sincronice otras cosas que no tengan nada que ver con
     * lo que estoy pidiendo»): what arrives through the room is written HERE only. The device where the user tapped sends it to its
     * own account; this device's account may be another one. A list made on another device and never put on YouTube stays local.
     */
    @Test
    fun `what arrives through the room is never written to this device's YouTube account`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAdapter.kt")
        assertFalse(a.contains("AccountPush"))
        assertFalse(a.contains("mirrorLikesToAccount"))
        assertFalse(a.contains("mirrorAlbumsToAccount"))
        assertFalse(a.contains("YouTube."))
        assertTrue(a.contains("database.syncSetLiked(id, liked, if (liked) now else null)"))
        assertTrue(a.contains("database.syncSetAlbumSaved(id, if (saved) now else null)"))
        assertTrue("a list with its own key (no browseId) is created local", a.contains("isLocal = ownId,"))
        assertTrue(a.contains("browseId = if (ownId) null else key,"))
    }

    /**
     * Row 740: the database side gives a playlist the key the core picks ([LibrarySyncCore.PlaylistIds.keyOf]), for the list, its songs
     * and its pins, and places what arrives by the same rules. «Its browseId when it has one» is what moved a list to a new key the
     * moment it was linked to YouTube: deleted and recreated on the other devices, or left twice by the mass-removal brake.
     */
    @Test
    fun `a playlist linked to YouTube keeps its sync key in the adapter and in the pins`() {
        val adapter = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAdapter.kt")
        assertTrue(adapter.contains("val key = playlists.keyOf(ref)"))
        assertTrue(adapter.contains("playlists.linkFact(ref)?.let { (k, v) -> facts[k] = v }"))
        assertTrue("row 741: one row per key says the list's name and songs", adapter.contains("if (sources[row.id] != key) continue"))
        assertTrue(adapter.contains("it.readLocal(keyOfRow)"))
        assertTrue(adapter.contains("val targets = LibrarySyncCore.PlaylistTargets("))
        assertTrue(adapter.contains("database.syncLinkPlaylist(row, browseId)"))
        assertFalse(adapter.contains("row.browseId ?: row.id"))
        val pins = read("app/src/main/kotlin/com/music/echo/devicesync/LibraryPinsBackend.kt")
        assertFalse(pins.contains("it.browseId ?: it.id"))
        assertFalse("a pin reaches a row by the adapter's rules, not by a lookup of its own", pins.contains("syncPlaylistIdFor"))
        val dao = read("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue(
            "never a list that is linked, never a browseId another list has",
            dao.contains("WHERE id = :id AND browseId IS NULL AND NOT EXISTS (SELECT 1 FROM playlist WHERE browseId = :browseId)"),
        )
        val sync = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue(sync.contains("adapter.readLocal(LibrarySyncCore.PlaylistIds.of(ledger))"))
        assertTrue(sync.contains("LibrarySyncCore.plan(accepted, before, LibrarySyncCore.PlaylistIds.of(ledger, accepted))"))
    }

    @Test
    fun `the worker README says what is stored`() {
        val r = read("docs/worker/sync/README.md")
        assertTrue(r.contains("lib_push"))
    }

    @Test
    fun `row 900 - the app's journal lives next to the room's ledger and dates changes by the boot and the time since boot`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAndroid.kt")
        assertTrue(a.contains("journal = LibraryJournalStore(ledgerFile.parentFile"))
        assertTrue(a.contains("android.provider.Settings.Global.BOOT_COUNT"))
        assertTrue(a.contains("val now: () -> Mono = { Mono(boot, android.os.SystemClock.elapsedRealtime()) }"))
        assertTrue("a restore still forgets every file of the rooms, the journal included", a.contains("File(context.filesDir, \"device_sync\").walkBottomUp()"))
        val s = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue("the journal is dated before a page of the room is weighed against it", s.contains("// Row 900: what was journaled before this page competes with it by when it was made, not by when it arrives.\n        convertJournalLocked()"))
        assertTrue("an op carries its id", s.contains("add(JsonPrimitive(LibrarySyncCore.opId(r)))"))
        assertTrue("only acknowledged ops stop being pending", s.contains("if (done != null && LibrarySyncCore.opId(sent) !in done) continue"))
    }

    @Test
    fun `row 900 - a playlist's order arriving from another device is written here only, by plain SQL`() {
        val a = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySyncAdapter.kt")
        assertTrue(a.contains("database.syncSetMapPosition(entry.id, index)"))
        assertTrue(a.contains("runCatching { LibrarySyncCore.PlaylistOrder.facts(key, songs, playlists.orderOf(key)) }.onSuccess(facts::putAll)"))
        assertFalse(a.contains("moveSongPlaylist"))
        val dao = read("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        assertTrue(dao.contains("@Query(\"UPDATE playlist_song_map SET position = :position WHERE id = :id\")\n    suspend fun syncSetMapPosition(id: Int, position: Int)"))
        assertTrue("the member rows come in each list's order", dao.substringBefore("suspend fun syncMemberRows()").substringAfterLast("@Query(").contains("ORDER BY m.playlistId, m.position, m.id"))
    }

    @Test
    fun `row 901 - removals of things the room never heard from this device are held, told in their own words`() {
        val s = read("app/src/main/kotlin/com/music/echo/devicesync/LibrarySync.kt")
        assertTrue(s.contains("} else if (!freshStart) {\n"))
        assertTrue(s.contains("held = removalsNeverSeenHereLocked(accepted, ledger)"))
        assertTrue(s.contains("DeviceSyncHub.HeldTrip((DeviceSyncHub.heldTrip.value?.token ?: 0) + 1, held.size, newHere = true)"))
        val activity = read("app/src/main/kotlin/com/music/echo/MainActivity.kt")
        assertTrue(activity.contains("message = (if (trip.newHere) heldNoticeNewHere else heldNotice).format(trip.count),"))
        for (dir in listOf("values", "values-es")) {
            val xml = read("app/src/main/res/$dir/device_sync_held_strings.xml")
            assertTrue(dir, xml.contains("name=\"device_sync_held_notice_new_here\">") && xml.substringAfter("device_sync_held_notice_new_here").contains("%1\$d"))
        }
    }
}
