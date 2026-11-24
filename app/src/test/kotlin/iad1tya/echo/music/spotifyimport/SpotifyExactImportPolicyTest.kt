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
    fun `every spelling of the matched channel is checked for the user's unfollow`() {
        assertEquals(listOf("UCaaa", "MPLAUCaaa"), SpotifyExactImportPolicy.storedSpellings("UCaaa", "UCaaa"))
        assertEquals(listOf("MPLAUCaaa", "UCaaa"), SpotifyExactImportPolicy.storedSpellings("MPLAUCaaa", null))
        assertEquals(listOf("browse1", "UCbbb", "MPLAUCbbb"), SpotifyExactImportPolicy.storedSpellings("browse1", "UCbbb"))
        assertEquals(listOf("browse1"), SpotifyExactImportPolicy.storedSpellings("browse1", ""))
    }

    @Test
    fun `an unfollow on any spelling stops the import before it writes`() {
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        val follow = repo.substringAfter("private suspend fun matchAndFollowArtistDetailed(").substringBefore("\n    }\n")
        val check = "if (database.getArtistEntitiesByIds(spellings).any { it.unfollowedByUserAt != null }) return ItemMatchOutcome.Success"
        assertTrue(follow.contains("val spellings = SpotifyExactImportPolicy.storedSpellings(match.id, channelId)"))
        assertTrue(follow.contains(check))
        assertTrue(follow.indexOf(check) < follow.indexOf("it[ImportFollowsAwaitingConfirmKey] ="))
        assertTrue(follow.indexOf(check) < follow.indexOf("database.withTransaction {"))
    }

    @Test
    fun `only artists new since the last pass are followed`() {
        val before = setOf("sp1", "sp2")
        assertFalse(SpotifyExactImportPolicy.isNewSinceLastPass("sp1", before))
        assertTrue(SpotifyExactImportPolicy.isNewSinceLastPass("sp3", before))
        assertTrue(SpotifyExactImportPolicy.isNewSinceLastPass("sp1", emptySet()))
    }

    @Test
    fun `the set keeps what Spotify still lists, adds what this pass handled and never a blank id`() {
        // sp2 left Spotify's list; sp3 was handled now; sp4 failed (not handled) and stays new for the next pass.
        assertEquals(
            setOf("sp1", "sp3"),
            SpotifyExactImportPolicy.importedAfterPass(
                previous = setOf("sp1", "sp2"),
                listedNow = listOf("sp1", "sp3", "sp4", ""),
                handledNow = listOf("sp3", ""),
            ),
        )
        // An artist unfollowed on Spotify and followed there again later is new again.
        val afterUnfollow = SpotifyExactImportPolicy.importedAfterPass(setOf("sp1"), listOf("sp9"), emptyList())
        assertTrue(SpotifyExactImportPolicy.isNewSinceLastPass("sp1", afterUnfollow))
    }

    @Test
    fun `the set is per Google account`() {
        assertEquals("abc123", SpotifyExactImportPolicy.accountTag("abc123"))
        assertEquals(SpotifyExactImportPolicy.SIGNED_OUT_TAG, SpotifyExactImportPolicy.accountTag(null))
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        assertTrue(repo.contains("stringSetPreferencesKey(\"spotify_imported_artist_ids@\$accountTag\")"))
        assertTrue(
            repo.contains(
                "val importedKey = spotifyImportedArtistIdsKey(SpotifyExactImportPolicy.accountTag(ImportFollowsAccounts.tagOf(prefs[AccountEmailKey])))",
            ),
        )
    }

    @Test
    fun `the followed-artists pass matches only new ids and records the set even when cancelled`() {
        val repo = main("spotifyimport/SpotifyImportRepository.kt")
        val pass = repo.substringAfter("private suspend fun importFollowedArtists(").substringBefore("\n    }\n")
        val gate = "if (!SpotifyExactImportPolicy.isNewSinceLastPass(artist.id, alreadyImported)) {"
        assertTrue(pass.contains(gate))
        assertTrue("the gate comes before the match", pass.indexOf(gate) < pass.indexOf("matchAndFollowArtistDetailed(artist)"))
        assertTrue(pass.contains("handled += artist.id"))
        val fin = pass.substringAfter("} finally {")
        assertTrue(fin.contains("withContext(NonCancellable) {"))
        assertTrue(fin.contains("it[importedKey] = SpotifyExactImportPolicy.importedAfterPass("))
        // Counts only in the shared log (AGENTS rule 4).
        assertTrue(pass.contains("\"ARTIST_IMPORT_PASS listed=%d new=%d\""))
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
