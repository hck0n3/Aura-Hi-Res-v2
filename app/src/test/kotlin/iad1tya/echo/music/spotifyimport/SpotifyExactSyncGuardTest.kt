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

    /**
     * Row 833: reconcile adds and removes songs only on Aura's own copy of that Spotify source (SpotifyMirrorOwnership); anything else
     * may be the user's own playlist an older version linked by name, and gets neither.
     */
    @Test
    fun `mirror reconcile touches only Aura's own copy`() {
        val src = body(upload, "    private suspend fun reconcileSpotifyMirrors() {")
        val gate = src.indexOf("val aurasCopy = SpotifyMirrorOwnership.isAurasCopy(")
        assertTrue(gate >= 0)
        assertTrue(src.contains("storedFingerprint = prefs.getString(playlistId, null),"))
        val skip = src.indexOf("if (!aurasCopy) {", gate)
        assertTrue(skip > gate)
        assertTrue(src.indexOf("continue", skip) < src.indexOf("YouTube.addToPlaylist(", skip))
        assertEquals(1, Regex("""YouTube\.addToPlaylist\(""").findAll(src).count())
        assertEquals(1, Regex("""YouTube\.removeFromPlaylist\(""").findAll(src).count())
        assertTrue(src.indexOf("YouTube.addToPlaylist(") > skip)
        assertTrue(src.indexOf("YouTube.removeFromPlaylist(") > skip)
        // Row 317: removals on Aura's copy keep only the «less than half» guard.
        assertTrue(src.contains("val extras = if (shrunkTooMuch) {"))
        // A skipped mirror is not read again until its songs change (battery, rule 7).
        assertTrue(src.contains("if (prefs.getString(SPOTIFY_MIRROR_SKIPPED_PREFIX + playlistId, null) == fingerprint) continue"))
    }

    /** Row 833: the created@ marker is written before the link, so a process death in between cannot lose it. */
    @Test
    fun `the created marker is written before the link is stored`() {
        val src = body(upload, "    private suspend fun uploadPlaylists() {")
        val create = src.indexOf("YouTube.createPlaylist(playlist.name)")
        val marker = src.indexOf(".putString(SPOTIFY_MIRROR_CREATED_PREFIX + playlist.id, browseId).commit()")
        val link = src.indexOf("database.update(playlist.copy(browseId = browseId, isAutoSync = true))")
        assertTrue(create in 0 until marker)
        assertTrue(marker in 0 until link)
    }

    /** Row 833: Spotify's «Liked Songs» are likes, never a playlist created (or kept in step) on the account. */
    @Test
    fun `the Spotify liked-songs mirror is never uploaded as a playlist`() {
        val dao = main("db/DatabaseDao.kt")
        val pendingFilter = "AND isEditable = 1 AND id != 'AURA_AI_RECS' AND id != 'SPOTIFY_LIKED_SONGS'"
        assertEquals("pending list and its count must agree", 2, dao.split(pendingFilter).size - 1)
        assertTrue(dao.contains("$pendingFilter\n$emptyMirrorFilter\n        ORDER BY bookmarkedAt LIMIT :limit"))
        val reconcile = body(upload, "    private suspend fun reconcileSpotifyMirrors() {")
        assertTrue(reconcile.contains("it.playlist.id != \"SPOTIFY_LIKED_SONGS\""))
        assertTrue(repo.contains("override val localPlaylistId: String = \"SPOTIFY_LIKED_SONGS\""))
    }

    private val emptyMirrorFilter = "          AND NOT (substr(id, 1, 8) = 'SPOTIFY_'\n" +
        "            AND NOT EXISTS (SELECT 1 FROM playlist_song_map WHERE playlist_song_map.playlistId = playlist.id))"

    /** Row 833: an empty Spotify source never creates an empty playlist, here or on the account. */
    @Test
    fun `an empty Spotify source creates no playlist`() {
        val mirror = body(repo, "    private suspend fun mirrorPlaylist(")
        assertTrue(mirror.contains("if (existing == null && tracks.isEmpty()) return@withTransaction"))
        val uploadPlaylists = body(upload, "    private suspend fun uploadPlaylists() {")
        assertTrue(uploadPlaylists.contains("if (songIds.isEmpty() && playlist.id.startsWith(\"SPOTIFY_\")) continue"))
    }

    /**
     * Row 833: an empty Spotify mirror (left by an older version, or emptied by a pass that matched nothing) is not pending at all:
     * the upload skips it, so counting it would keep the pass re-chaining itself (YtmSyncWorker) and the screen short of «todo listo».
     */
    @Test
    fun `an empty Spotify mirror is neither listed nor counted as pending`() {
        val dao = main("db/DatabaseDao.kt")
        assertEquals("pending list and its count must agree", 2, dao.split(emptyMirrorFilter).size - 1)
        val count = dao.substring(dao.indexOf("SELECT COUNT(*) FROM playlist\n        WHERE browseId IS NULL AND bookmarkedAt IS NOT NULL"))
        assertTrue(count.substringBefore("suspend fun countPlaylistsPendingUpload()").contains(emptyMirrorFilter))
    }
}
