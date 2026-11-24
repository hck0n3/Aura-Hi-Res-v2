package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 846 (owner 2026-11-24, Odesli points 1 and 2): a link shared from another service opens the exact song without
 * Odesli's retired API. Source-text checks on purpose: every path here runs against the network.
 */
class ExternalLinksExactGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    /** Odesli's keyless API answers 401 PUBLIC_API_ACCESS_DEPRECATED: no request to it (or to its private website API) remains. */
    @Test
    fun `no request to the Odesli API remains`() {
        val sources = File(root, "app/src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        for (file in sources) {
            val text = file.readText()
            assertFalse("${file.name} calls api.song.link", text.contains("api.song.link"))
            assertFalse("${file.name} calls api.odesli.co", text.contains("api.odesli.co"))
        }
        val links = main("utils/ExternalMusicLinks.kt")
        assertFalse(links.contains("odesliYouTubeMusicId"))
        assertFalse(links.contains("DirectVideo"))
        assertFalse(main("MainActivity.kt").contains("Resolved.DirectVideo"))
    }

    /** Every song of a shared link (track, album or playlist queue) is matched by the strict same-recording matcher. */
    @Test
    fun `shared tracks are matched strictly`() {
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        val start = repo.indexOf("    suspend fun matchExternalTracks(")
        assertTrue(start >= 0)
        val external = repo.substring(start, repo.indexOf("    suspend fun ensureSpotifyReadToken(", start))
        assertTrue(external.contains("matchTrack(track, index, strict = true)"))
        val search = repo.substring(repo.indexOf("    private suspend fun searchAndScore("))
        assertTrue(search.contains("if (strict && candidates.isNotEmpty()) {"))
        assertTrue(search.contains("SpotifyTrackMatcher.pick("))
        assertTrue(main("spotifyimport/SpotifyTrackMatcher.kt").contains("ranked.firstOrNull { RecordingMatcher.accepts(source, it.candidate) }"))
        val activity = openExternal()
        assertTrue(activity.contains("val matched = repository.matchExternalTracks(tracks).filterNotNull()"))
        assertTrue(activity.contains("val song = repository.matchExternalTracks(resolved.tracks, limit = 1).firstOrNull()"))
    }

    /** An album opens only when title AND artist are the same (row 830's rule); never the first album whose title contains it. */
    @Test
    fun `an album opens only on an exact title and artist match`() {
        val activity = openExternal()
        assertTrue(activity.contains("SpotifyExactImportPolicy.isSameAlbum("))
        assertTrue(activity.contains("youTubeArtists = album.artists.orEmpty().map { it.name },"))
        assertFalse(activity.contains(".contains(wanted)"))
        assertFalse(activity.contains("wanted.contains("))
        // A page title alone carries no separate artist: it goes to the search, never to a guessed album.
        assertTrue(activity.contains("ExternalMusicLinks.Kind.ALBUM -> notFound(resolved.text)"))
        assertFalse(activity.contains("findAlbum(resolved.text, resolved.text)"))
    }

    /**
     * A Spotify or Tidal track whose page gives no artist can never pass the strict matcher (SpotifyTrackMatcher.pick returns
     * null without one): it goes to the title search, as row 846 says, instead of three YouTube searches bound to fail.
     */
    @Test
    fun `a shared track without an artist goes to the title search`() {
        val links = main("utils/ExternalMusicLinks.kt")
        val spotify = links.substring(links.indexOf("    private suspend fun spotify("), links.indexOf("    private fun deezer("))
        assertTrue(spotify.contains("if (artist.isEmpty()) return Resolved.Query(Kind.TRACK, title)"))
        val tidal = links.substring(links.indexOf("    private fun tidal("), links.indexOf("    private fun fetchPage("))
        assertTrue(tidal.contains("if (artist.isBlank()) return Resolved.Query(Kind.TRACK, name)"))
        assertFalse(links.contains("Empty is the safe fallback"))
        assertTrue(main("spotifyimport/SpotifyTrackMatcher.kt").contains("if (artists.isEmpty()) return null"))
    }

    /**
     * A Spotify track page gives no duration, so the strict matcher loses its duration band: Spotify's own search fills it in,
     * but only with the result whose id is the shared track's (never a look-alike), bounded in time, before the page fields.
     */
    @Test
    fun `a Spotify track is read with its duration from the same id`() {
        val links = main("utils/ExternalMusicLinks.kt")
        val spotify = links.substring(links.indexOf("    private suspend fun spotify("), links.indexOf("    private fun deezer("))
        val lookup = spotify.indexOf("spotifyTrackById(repository, id, \"\$title \$artist\".trim())?.let { exact ->")
        assertTrue(lookup >= 0)
        assertTrue(lookup < spotify.indexOf("if (artist.isEmpty()) return Resolved.Query(Kind.TRACK, title)"))
        assertTrue(spotify.contains("withTimeoutOrNull(12_000L) {"))
        assertTrue(spotify.contains("if (!repository.ensureSpotifyReadToken()) {"))
        assertTrue(spotify.contains(".firstOrNull { it.id == id && it.name.isNotBlank() && it.artists.any { artist -> artist.name.isNotBlank() } }"))
    }

    private fun openExternal(): String {
        val activity = main("MainActivity.kt")
        val start = activity.indexOf("    private fun openExternalMusicLink(")
        assertTrue(start >= 0)
        return activity.substring(start, activity.indexOf("\n    }\n", start))
    }

    /** The song.link forms Aura itself shares still open without any network request. */
    @Test
    fun `song_link links Aura shares are still read locally`() {
        val activity = main("MainActivity.kt")
        assertTrue(activity.contains("ExternalMusicLinks.songLinkVideoId(uri)?.let { videoId ->"))
        assertTrue(activity.contains("ExternalMusicLinks.songLinkWrappedYouTubeUrl(opened.toString())?.toUri() ?: opened"))
    }
}
