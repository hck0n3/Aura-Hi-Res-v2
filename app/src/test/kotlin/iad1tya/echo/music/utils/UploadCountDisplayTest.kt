package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 841 (owner 2026-11-24: «quiero que esos números sean reales»): a count built on an account list that was not
 * read to its end is a minimum. The sync screen says «Al menos N sincronizadas · lista incompleta» for it, never an
 * exact-looking «N de M», never «Listo», and never «todo sincronizado» while any list is in that state.
 */
class UploadCountDisplayTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    @Test
    fun `a complete read shows the exact count, an incomplete one only a minimum`() {
        assertEquals(UploadCountLine.Exact(1234, 1300), UploadCountDisplay.line(1234, 1300, readIncomplete = false))
        assertEquals(UploadCountLine.AtLeast(1000), UploadCountDisplay.line(1000, 1000, readIncomplete = true))
        assertEquals(UploadCountLine.AtLeast(0), UploadCountDisplay.line(0, 0, readIncomplete = true))
        assertEquals(UploadCountLine.Hidden, UploadCountDisplay.line(0, 0, readIncomplete = false))
    }

    @Test
    fun `an incomplete read is never reported as done or as nothing to sync`() {
        assertEquals(UploadCategoryStatus.INCOMPLETE, UploadCountDisplay.status(running = false, total = 1000, isComplete = true, readIncomplete = true))
        assertEquals(UploadCategoryStatus.INCOMPLETE, UploadCountDisplay.status(running = false, total = 0, isComplete = true, readIncomplete = true))
        assertEquals(UploadCategoryStatus.RUNNING, UploadCountDisplay.status(running = true, total = 10, isComplete = false, readIncomplete = true))
        assertEquals(UploadCategoryStatus.DONE, UploadCountDisplay.status(running = false, total = 10, isComplete = true, readIncomplete = false))
        assertEquals(UploadCategoryStatus.PENDING, UploadCountDisplay.status(running = false, total = 10, isComplete = false, readIncomplete = false))
        assertEquals(UploadCategoryStatus.NOTHING, UploadCountDisplay.status(running = false, total = 0, isComplete = true, readIncomplete = false))
    }

    @Test
    fun `each read sets or clears only its own list`() {
        val afterCut = AccountReadLists.afterRead(emptySet(), AccountReadLists.ARTISTS, complete = false)
        assertEquals(setOf("artists"), afterCut)
        val both = AccountReadLists.afterRead(afterCut, AccountReadLists.LIKED_SONGS, complete = false)
        assertEquals(setOf("artists", "liked_songs"), both)
        assertEquals(setOf("liked_songs"), AccountReadLists.afterRead(both, AccountReadLists.ARTISTS, complete = true))
        assertEquals(emptySet<String>(), AccountReadLists.afterRead(emptySet(), AccountReadLists.PLAYLISTS, complete = true))
    }

    @Test
    fun `the sync screen uses the derivation for every category and holds the all-done notice`() {
        val screen = src("app/src/main/kotlin/com/music/echo/ui/screens/settings/YtmSyncScreen.kt")
        assertTrue(screen.contains("val (incompleteReads, _) = rememberPreference(YtmAccountReadsIncompleteKey, emptySet())"))
        for ((cat, list) in listOf(
            "playlists" to "PLAYLISTS",
            "artists" to "ARTISTS",
            "liked_songs" to "LIKED_SONGS",
            "liked_albums" to "LIKED_ALBUMS",
        )) {
            val field = mapOf("playlists" to "playlists", "artists" to "artists", "liked_songs" to "likedSongs", "liked_albums" to "likedAlbums")[cat]
            assertTrue(cat, screen.contains("UploadCategoryRow(stringResource(R.string.ytm_upload_cat_$cat), progress.$field, AccountReadLists.$list in incompleteReads || progress.$field.readCutShort)"))
        }
        val row = screen.substringAfter("private fun UploadCategoryRow(label: String, state: UploadCategoryProgress, readIncomplete: Boolean) {")
        assertTrue(row.contains("UploadCountDisplay.status(state.running, state.total, state.isComplete, readIncomplete)"))
        assertTrue(row.contains("UploadCountDisplay.line(state.synced, state.total, readIncomplete)"))
        assertTrue(row.contains("stringResource(R.string.ytm_upload_count_at_least, line.synced)"))
        assertTrue(row.contains("UploadCategoryStatus.INCOMPLETE -> stringResource(R.string.ytm_upload_list_incomplete)"))
        val incomplete = screen.indexOf("} else if (incompleteReads.isNotEmpty() || progress.anyReadCutShort) {")
        assertTrue(incomplete >= 0)
        assertTrue("checked before the all-done notice", incomplete < screen.indexOf("} else if (progress.everythingSynced) {"))
        assertTrue(screen.contains("text = stringResource(R.string.ytm_upload_incomplete_summary),"))
    }

    @Test
    fun `every read of a counted list records whether it reached the end`() {
        val sync = src("app/src/main/kotlin/com/music/echo/utils/SyncUtils.kt")
        assertTrue(sync.contains("libraryUploadSync.noteAccountRead(AccountReadLists.LIKED_SONGS, remoteComplete)"))
        assertTrue(sync.contains("libraryUploadSync.noteAccountRead(AccountReadLists.LIKED_SONGS, readComplete)"))
        assertTrue(sync.contains("libraryUploadSync.noteAccountRead(AccountReadLists.LIKED_ALBUMS, page.continuation == null)"))
        assertTrue(sync.contains("libraryUploadSync.noteAccountRead(AccountReadLists.ARTISTS, page.continuation == null)"))
        assertTrue(sync.contains("libraryUploadSync.noteAccountRead(AccountReadLists.PLAYLISTS, page.continuation == null)"))
        val upload = src("app/src/main/kotlin/com/music/echo/utils/LibraryUploadSync.kt")
        assertTrue(upload.contains("it[YtmAccountReadsIncompleteKey] = AccountReadLists.afterRead(it[YtmAccountReadsIncompleteKey].orEmpty(), list, complete)"))
        assertTrue(upload.contains("noteAccountRead(AccountReadLists.ARTISTS, page.continuation == null)"))
        assertTrue(upload.contains("if (remotePage != null) noteAccountRead(AccountReadLists.PLAYLISTS, remoteListComplete)"))
        assertTrue(upload.contains("noteAccountRead(AccountReadLists.LIKED_SONGS, remotePage.songsContinuation == null)"))
        assertTrue(upload.contains("noteAccountRead(AccountReadLists.LIKED_ALBUMS, remotePage.continuation == null)"))
    }

    @Test
    fun `a pass whose own read was cut short keeps its numbers a minimum after a later whole read clears the key`() {
        // Reviewer scenario: the upload pass reads LM short and stores {synced=S, pending=0, verified=false}; a later down sync
        // reads LM whole and clears the DataStore key but never touches the pass's numbers. Without the progress's own mark the
        // row said «S de S sincronizadas» next to «faltan 0» and the summary «Faltan 0 elementos».
        val upload = src("app/src/main/kotlin/com/music/echo/utils/LibraryUploadSync.kt")
        assertTrue(upload.contains("    val readCutShort: Boolean = false,"))
        assertTrue(upload.contains("synced = localLiked.count { it in remoteIds }, pending = 0, running = false, verified = false, readCutShort = true,"))
        assertTrue(upload.contains("synced = localAlbums.size - missing.size, pending = 0, running = false, verified = false, readCutShort = true,"))
        assertTrue(upload.contains("playlists.readCutShort || artists.readCutShort || likedSongs.readCutShort || likedAlbums.readCutShort"))
    }

    @Test
    fun `the device-owned skips clear the incomplete mark that no read could clear while they own the library`() {
        val upload = src("app/src/main/kotlin/com/music/echo/utils/LibraryUploadSync.kt")
        for ((fn, key, field) in listOf(
            Triple("private suspend fun uploadLikedSongs() {", "LIKED_SONGS", "likedSongs"),
            Triple("private suspend fun uploadLikedAlbums() {", "LIKED_ALBUMS", "likedAlbums"),
        )) {
            val skip = upload.substringAfter(fn)
                .substringAfter("if (iad1tya.echo.music.devicesync.DeviceSyncHub.libraryOwnedByDevices) {")
                .substringBefore("return\n")
            assertTrue(key, skip.contains("noteAccountRead(AccountReadLists.$key, complete = true)"))
            assertTrue(key, skip.contains("_progress.value = _progress.value.copy($field = _progress.value.$field.copy(readCutShort = false))"))
        }
    }

    @Test
    fun `the minimum wording is in English and both Spanish files, with its count`() {
        for (folder in listOf("values", "values-es", "values-es-rUS")) {
            val strings = src("app/src/main/res/$folder/echo_strings.xml")
            val line = strings.lines().single { it.contains("name=\"ytm_upload_count_at_least\"") }
            assertTrue("$folder: the count placeholder", line.contains("%1\$d"))
            assertTrue(folder, strings.contains("name=\"ytm_upload_list_incomplete\""))
            assertTrue(folder, strings.contains("name=\"ytm_upload_incomplete_summary\""))
        }
    }
}
