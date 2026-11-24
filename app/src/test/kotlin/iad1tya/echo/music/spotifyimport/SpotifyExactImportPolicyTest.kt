package iad1tya.echo.music.spotifyimport

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 830 (owner 2026-11-24, K1: «quiero que la sincronización sea exacta»): the Spotify import starts with nothing
 * ticked and its schedule only keeps playlists up to date.
 */
class SpotifyExactImportPolicyTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `nothing is preselected when the sources load`() {
        val loaded = listOf("liked_songs", "followed_artists", "saved_albums", "playlist:a")
        assertEquals(emptySet<String>(), SpotifyExactImportPolicy.initialSelection(loaded, emptySet()))
    }

    @Test
    fun `a reload keeps only what the user ticked and is still offered`() {
        val loaded = listOf("liked_songs", "followed_artists", "playlist:a")
        assertEquals(
            setOf("playlist:a"),
            SpotifyExactImportPolicy.initialSelection(loaded, setOf("playlist:a", "playlist:gone")),
        )
    }

    @Test
    fun `the schedule offers playlists and liked songs, never followed artists nor saved albums`() {
        assertTrue(SpotifyExactImportPolicy.isSchedulable(SpotifyImportSourceType.PLAYLIST))
        assertTrue(SpotifyExactImportPolicy.isSchedulable(SpotifyImportSourceType.LIKED_SONGS))
        assertFalse(SpotifyExactImportPolicy.isSchedulable(SpotifyImportSourceType.ARTISTS))
        assertFalse(SpotifyExactImportPolicy.isSchedulable(SpotifyImportSourceType.SAVED_ALBUMS))
    }

    @Test
    fun `a stored schedule drops followed artists and saved albums and keeps the rest`() {
        assertEquals(
            setOf("liked_songs", "playlist:a"),
            SpotifyExactImportPolicy.scheduledIds("liked_songs, followed_artists,saved_albums,,playlist:a"),
        )
        assertEquals(emptySet<String>(), SpotifyExactImportPolicy.scheduledIds(""))
        assertEquals(emptySet<String>(), SpotifyExactImportPolicy.scheduledIds("followed_artists"))
    }

    @Test
    fun `the source ids the schedule filters are the ones the repository builds`() {
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        assertTrue(repo.contains("override val id: String = \"${SpotifyExactImportPolicy.FOLLOWED_ARTISTS_SOURCE_ID}\""))
        assertTrue(repo.contains("override val id: String = \"${SpotifyExactImportPolicy.SAVED_ALBUMS_SOURCE_ID}\""))
    }

    @Test
    fun `two channels with the exact name are ambiguous, one channel under both spellings is not`() {
        assertFalse(SpotifyExactImportPolicy.isAmbiguousArtist(emptyList()))
        assertFalse(SpotifyExactImportPolicy.isAmbiguousArtist(listOf("UCaaa")))
        assertFalse(SpotifyExactImportPolicy.isAmbiguousArtist(listOf("UCaaa", "MPLAUCaaa")))
        assertTrue(SpotifyExactImportPolicy.isAmbiguousArtist(listOf("UCaaa", "UCbbb")))
        assertTrue(SpotifyExactImportPolicy.isAmbiguousArtist(listOf("UCaaa", "MPLAUCaaa", "UCbbb")))
    }

    @Test
    fun `an ambiguous artist is not followed and goes to the failures`() {
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        val follow = repo.substringAfter("private suspend fun matchAndFollowArtistDetailed(").substringBefore("\n    }\n")
        assertTrue(follow.contains(".filter { name.isNotEmpty() && name == ArtistNameMatching.normalize(it.title) }"))
        assertFalse("the first exact result is not taken blindly", follow.contains(".firstOrNull {"))
        val ambiguous = follow.indexOf("if (SpotifyExactImportPolicy.isAmbiguousArtist(exactMatches.map { it.id })) {")
        assertTrue(ambiguous >= 0)
        assertTrue(
            follow.substring(ambiguous).substringBefore("\n        }").contains("return ItemMatchOutcome.Failure(SpotifyImportFailureReason.LOW_SCORE)"),
        )
        assertTrue("decided before anything is written", ambiguous < follow.indexOf("database.withTransaction {"))
        assertTrue(ambiguous < follow.indexOf("ImportFollowsAwaitingConfirmKey"))
    }

    @Test
    fun `the screen, the view model and the worker go through the policy`() {
        val vm = main("spotifyimport/SpotifyImportViewModel.kt")
        assertTrue(vm.contains("selectedSourceIds = SpotifyExactImportPolicy.initialSelection("))
        assertFalse("every source ticked on load", vm.contains("val selectedIds = loadedSources.mapTo(LinkedHashSet()) { it.id }"))

        val screen = main("ui/screens/SpotifyImportScreen.kt")
        assertTrue(screen.contains("val autoSyncIds = remember(autoSyncCsv) { SpotifyExactImportPolicy.scheduledIds(autoSyncCsv) }"))
        assertTrue(screen.contains("sources = state.sources.filter { SpotifyExactImportPolicy.isSchedulable(it.type) },"))

        val worker = main("spotifyimport/SpotifyAutoSyncWorker.kt")
        assertTrue(worker.contains("val ids = SpotifyExactImportPolicy.scheduledIds(csv)"))
        assertTrue(worker.contains("val toSync = sources.filter { it.id in ids }"))
    }
}
