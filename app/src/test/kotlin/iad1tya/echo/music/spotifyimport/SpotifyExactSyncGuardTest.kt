package iad1tya.echo.music.spotifyimport

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 832-833 (owner 2026-11-24: «quiero que la sincronización sea exacta y no que sincronice otras cosas que no tengan nada
 * que ver con lo que estoy pidiendo»). Source-text checks on purpose: every path here runs against Spotify, YouTube or Room.
 */
class SpotifyExactSyncGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    /** The member body that starts at [signature], up to its closing brace at class-member indentation. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    private val repo get() = main("spotifyimport/SpotifyImportRepository.kt")

    /** Row 832: liked songs and playlists go through the strict matcher; external links keep the lenient one. */
    @Test
    fun `the import matches tracks strictly and external links do not`() {
        val src = repo
        val matchTracks = body(src, "    private suspend fun matchTracks(")
        assertTrue(matchTracks.contains("matchTrack(track, index, strict = true)"))
        val external = body(src, "    suspend fun matchExternalTracks(")
        assertTrue(external.contains("matchTrack(track, index)"))
        val search = body(src, "    private suspend fun searchAndScore(")
        assertTrue(search.contains("if (strict && candidates.isNotEmpty()) {"))
        assertTrue(search.contains("SpotifyTrackMatcher.pick("))
        assertTrue(search.contains("?: return MatchResult.Failure(SpotifyImportFailureReason.LOW_SCORE)"))
    }

    /** Row 832: the import never pushes every local like to the account; the upload pass (behind its switch) does that. */
    @Test
    fun `the import does not call syncLikedSongs`() {
        val src = repo
        assertFalse(src.contains("syncUtils.syncLikedSongs()"))
        assertFalse(src.contains(".syncLikedSongsSuspend()"))
        assertEquals(1, Regex("""YtmSyncWorker\.TYPE_UPLOAD_LIBRARY""").findAll(src).count())
    }

    /** Row 832: a scheduled pass only marks as liked what is new in Spotify since the last pass (SpotifyLikedImportLedger). */
    @Test
    fun `only new Spotify likes are marked as liked`() {
        val src = repo
        assertTrue(src.contains("likedIds = SpotifyLikedImportLedger.toMark(matched, previous, mirrorSongIds),"))
        assertTrue(src.contains("it[SpotifyLikedImportLedger.keyOf(tag)] = SpotifyLikedImportLedger.next(previous, matched)"))
        val mirror = body(src, "    private suspend fun mirrorPlaylist(")
        assertTrue(mirror.contains("val markLiked = metadata.id in likedIds"))
        assertFalse(mirror.contains("source is SpotifyImportSource.LikedSongs"))
    }

    private val upload get() = main("utils/LibraryUploadSync.kt")

    /** Row 833: a Spotify mirror is never linked by name to a playlist already on the account; it always gets a new one. */
    @Test
    fun `a Spotify mirror is never linked to an account playlist by name`() {
        val src = body(upload, "    private suspend fun uploadPlaylists() {")
        assertTrue(src.contains("?.takeIf { !playlist.id.startsWith(\"SPOTIFY_\") && it.isEditable && it.id !in claimed }"))
        assertTrue(src.contains(".putString(SPOTIFY_MIRROR_CREATED_PREFIX + playlist.id, browseId).commit()"))
    }

    /** Row 833: reconcile only removes songs from a playlist Aura itself created for that Spotify source. */
    @Test
    fun `mirror reconcile removes songs only from playlists Aura created`() {
        val src = body(upload, "    private suspend fun reconcileSpotifyMirrors() {")
        assertTrue(src.contains("val createdByAura = prefs.getString(SPOTIFY_MIRROR_CREATED_PREFIX + playlistId, null) == browseId"))
        assertTrue(src.contains("val extras = if (shrunkTooMuch || !createdByAura) {"))
        assertEquals(1, Regex("""YouTube\.removeFromPlaylist\(""").findAll(src).count())
        val removal = src.indexOf("YouTube.removeFromPlaylist(")
        assertTrue(src.indexOf("for (song in extras) {") in 0 until removal)
    }
}
