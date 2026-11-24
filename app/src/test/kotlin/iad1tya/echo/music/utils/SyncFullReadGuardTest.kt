package iad1tya.echo.music.utils

import com.music.innertube.utils.FULL_READ_MAX_PAGES
import com.music.innertube.utils.walkContinuations
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 840 (owner 2026-11-24: «me dice en cada apartado que tengo 1000 suscriptores, mis canciones y mis listas, o sea
 * me está mintiendo, quiero que esos números sean reales»). The library sync pulls and the upload pass read the account with a
 * 50-page cap (about a thousand items), so the database — and every count of the sync screen built on it — stopped near 1000.
 * Those reads now page to the real end (bounded by [FULL_READ_MAX_PAGES]) and every consumer that removes, unlikes, rebuilds or
 * re-sends refuses a read that was cut short. Source-text checks where the code needs a signed-in account.
 */
class SyncFullReadGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    /** The member body that starts at [signature], up to its closing brace at class-member indentation. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    private val utilsPath = "innertube/src/main/kotlin/com/music/innertube/utils/Utils.kt"
    private val syncPath = "app/src/main/kotlin/com/music/echo/utils/SyncUtils.kt"
    private val uploadPath = "app/src/main/kotlin/com/music/echo/utils/LibraryUploadSync.kt"

    @Test
    fun `the full-read bound reads a library far bigger than the old cap to its end`() = runBlocking {
        assertEquals(1000, FULL_READ_MAX_PAGES)
        // 300 pages of 25 = 7 500 items: the old cap stopped at 51 pages (1 275).
        var fetched = 0
        val walk = walkContinuations(List(25) { it }, "t1", FULL_READ_MAX_PAGES) { token ->
            fetched++
            val n = token.removePrefix("t").toInt()
            List(25) { n * 25 + it } to (if (n + 1 < 300) "t${n + 1}" else null)
        }
        assertTrue(walk.complete)
        assertEquals(300 * 25, walk.items.size)
        assertEquals(299, fetched)
    }

    @Test
    fun `no 50-page cap is left in the walks the sync pulls use, and a cut-short playlist read says so`() {
        val utils = src(utilsPath)
        assertTrue(utils.contains("const val FULL_READ_MAX_PAGES = 1000"))
        assertFalse("no hard-coded cap inside a walk", utils.contains("val maxRequests = 50"))
        val playlist = utils.substringAfter("@JvmName(\"completedLibrary\")").substringBefore("@JvmName(\"completedPlaylist\")")
        assertTrue(playlist.contains("suspend fun Result<PlaylistPage>.completed(maxRequests: Int = 50): Result<PlaylistPage>"))
        assertTrue(playlist.contains("songsContinuation = continuation,"))
        assertFalse(playlist.contains("songsContinuation = null"))
        val streamingSongs = utils.substringAfter("@JvmName(\"completedStreamingLibrary\")").substringBefore("@JvmName(\"completedStreamingPlaylist\")")
        assertTrue(streamingSongs.contains("maxRequests: Int = FULL_READ_MAX_PAGES,\n    onPage: suspend (List<SongItem>) -> Unit,"))
        val streamingItems = utils.substringAfter("@JvmName(\"completedStreamingPlaylist\")").substringBefore("fun ByteArray.toHex()")
        assertTrue(streamingItems.contains("maxRequests: Int = FULL_READ_MAX_PAGES,\n    onPage: suspend (List<YTItem>) -> Unit,"))
    }

    @Test
    fun `every account read of the sync pulls pages to the end`() {
        val sync = src(syncPath)
        for (signature in listOf(
            "private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {",
            "private suspend fun executeMirrorLikedSongs() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncLibrarySongs() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncUploadedSongs() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncLikedAlbums() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncUploadedAlbums() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncArtistsSubscriptions() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncSavedPlaylists() = withContext(Dispatchers.IO) {",
            "private suspend fun executeSyncPlaylist(browseId: String, playlistId: String) = withContext(Dispatchers.IO) {",
        )) {
            val pull = body(sync, signature)
            assertFalse("$signature: a bare completed() keeps the 50-page cap", pull.contains(").completed()"))
            assertFalse("$signature: no explicit small cap", pull.contains("maxRequests = 50"))
            assertTrue(
                "$signature: reads the account",
                pull.contains(".completed(maxRequests = FULL_READ_MAX_PAGES)") || pull.contains(".completedStreaming {"),
            )
        }
        val upload = src(uploadPath)
        assertFalse("the upload pass reads the whole account too", upload.contains(").completed()"))
        assertEquals(6, Regex("""\.completed\(maxRequests = FULL_READ_MAX_PAGES\)""").findAll(upload).count())
    }

    @Test
    fun `nothing is unliked, rebuilt or taken as the reference from a read cut short`() {
        val sync = src(syncPath)
        val liked = body(sync, "private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {")
        assertTrue(liked.contains("val remoteComplete = page.songsContinuation == null"))
        assertTrue(liked.contains("localSongs.filterNot { !remoteComplete || it.id in remoteIds || it.song.isLocal }.forEach { song ->"))
        assertTrue(liked.contains("if (remoteComplete && remoteIds.isNotEmpty()) {\n                        likeConfirmPrefs.edit().putStringSet(LIKED_CONFIRMED_KEY"))
        // The drop decision compares likedDate with this time: a cut-short read must not move it (a failed re-like would then drop).
        // Only the very first stamp is written regardless, so the HALLAZGO-061 cold-start recovery does not re-run on every start.
        assertTrue(liked.contains("if (remoteComplete || lastLikedSyncMs <= 0L) context.dataStore.edit { it[LastLikedSyncTimeKey] = nowEpochMs }"))
        assertFalse(liked.contains("\n                    context.dataStore.edit { it[LastLikedSyncTimeKey] = nowEpochMs }"))

        val mirror = body(sync, "private suspend fun executeMirrorLikedSongs() = withContext(Dispatchers.IO) {")
        assertTrue(mirror.contains("val readComplete = page.songsContinuation == null"))
        assertTrue(mirror.contains(".filterNot { !readComplete || it.id in remoteIds || it.song.isLocal }"))
        assertTrue(mirror.indexOf("val readComplete") < mirror.indexOf("batchUpdateSongs(toUnlike)"))

        val playlist = body(sync, "private suspend fun executeSyncPlaylist(browseId: String, playlistId: String) = withContext(Dispatchers.IO) {")
        val guard = playlist.indexOf("if (page.songsContinuation != null) {")
        assertTrue(guard >= 0)
        assertTrue("checked before the clear-and-replace", guard < playlist.indexOf("database.clearPlaylist(playlistId)"))
    }

    @Test
    fun `the upload pass sends nothing on a read cut short`() {
        val upload = src(uploadPath)
        val playlists = body(upload, "private suspend fun uploadPlaylists() {")
        assertTrue(playlists.contains("val remoteListComplete = remotePage != null && remotePage.continuation == null"))
        val skip = playlists.indexOf("if (!remoteListComplete && !playlist.id.startsWith(\"SPOTIFY_\")) continue")
        assertTrue(skip >= 0)
        assertTrue("checked before a playlist is created", skip < playlists.indexOf("YouTube.createPlaylist(playlist.name)"))

        val likes = body(upload, "private suspend fun uploadLikedSongs() {")
        val likesCut = likes.indexOf("if (remotePage.songsContinuation != null) {")
        assertTrue(likesCut >= 0)
        assertTrue(likesCut < likes.indexOf("YouTube.likeVideo(songId, true)"))
        assertTrue(likes.substring(likesCut).substringBefore("return\n").contains("pending = 0, running = false, verified = false,"))

        val albums = body(upload, "private suspend fun uploadLikedAlbums() {")
        val albumsCut = albums.indexOf("if (remotePage.continuation != null) {")
        assertTrue(albumsCut >= 0)
        assertTrue(albumsCut < albums.indexOf("YouTube.likePlaylist(playlistId, true)"))

        // Rule 7: a category left unverified by a cut-short read must not keep YtmSyncWorker chaining passes that write nothing.
        assertTrue(upload.contains("moreWorkPending = !done && requestBudget > 0,"))

        val topUp = body(upload, "private suspend fun topUpRemotePlaylist(")
        assertTrue(topUp.contains("?.takeIf { it.songsContinuation == null }"))
        val spotify = body(upload, "private suspend fun reconcileSpotifyMirrors() {")
        assertTrue(spotify.contains("?.takeIf { it.songsContinuation == null }?.songs"))
    }

    @Test
    fun `the playlists incomplete mark clears while the devices own the library`() {
        val playlists = body(src(uploadPath), "private suspend fun uploadPlaylists() {")
        val empty = playlists.substringAfter("if (pending.isEmpty()) {").substringBefore("return\n")
        assertTrue(empty.contains("if (iad1tya.echo.music.devicesync.DeviceSyncHub.libraryOwnedByDevices) {"))
        assertTrue(empty.contains("noteAccountRead(AccountReadLists.PLAYLISTS, complete = true)"))
    }
}
