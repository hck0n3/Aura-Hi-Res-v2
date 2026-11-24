package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 646 — artist subscriptions the user never made (owner 2026-11-22: «me aparecen suscripciones de la nada
 * de artistas que no conozco»). Source-text checks on purpose: every door goes through a restore, a YouTube search or
 * the signed-in account, none of which runs in a JVM test.
 */
class PhantomArtistSubscriptionGuardTest {
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

    private val awaitingWrite =
        "it[ImportFollowsAwaitingConfirmKey] = it[ImportFollowsAwaitingConfirmKey].orEmpty() + match.id"

    /** (a) A restore keeps the restored follows: the upload switch goes off, and a cancelled screen cannot skip it. */
    @Test
    fun `a restore switches the library upload off under NonCancellable`() {
        val src = main("viewmodels/BackupRestoreViewModel.kt")
        assertTrue(
            src.contains(
                "withContext(kotlinx.coroutines.NonCancellable) {\n" +
                    "                    context.dataStore.edit { settings ->\n" +
                    "                        settings[iad1tya.echo.music.constants.YtmUploadSyncKey] =\n" +
                    "                            iad1tya.echo.music.utils.LibraryUploadOptIn.onAccountDetached()",
            ),
        )
    }

    /** (b) Spotify import: exact name, keeps the user's unfollows, only a local follow held for confirmation. */
    @Test
    fun `the Spotify artist import follows exactly, respects unfollows and never subscribes on its own`() {
        val src = main("spotifyimport/SpotifyImportRepository.kt")
        assertFalse(src.contains("YouTube.subscribeChannel("))
        assertFalse(src.contains("ArtistNameMatching.isLikelyMatch("))

        val follow = body(src, "private suspend fun matchAndFollowArtistDetailed(spotifyArtist: SpotifyArtist, handledEarlier: Boolean): ItemMatchOutcome {")
        assertTrue(follow.contains("val name = ArtistNameMatching.normalize(spotifyArtist.name)"))
        assertTrue(follow.contains("name.isNotEmpty() && name == ArtistNameMatching.normalize(it.title)"))
        assertTrue(follow.contains("if (existing?.unfollowedByUserAt != null) return ItemMatchOutcome.Success"))
        assertFalse(follow.contains("unfollowedByUserAt = null"))
        assertFalse(follow.contains("ytmSyncedAt"))
        assertTrue(follow.contains(awaitingWrite))
        assertTrue(
            "the awaiting id is recorded before the follow is written",
            follow.indexOf(awaitingWrite) < follow.indexOf("database.withTransaction {"),
        )
    }

    /** (c) Tidal/Deezer migration: same exact match, unfollow respect and confirmation hold. */
    @Test
    fun `the followed-artists migration follows exactly and respects unfollows`() {
        val src = main("ui/screens/migration/MigrationViewModel.kt")
        assertFalse(src.contains("ArtistNameMatching.isLikelyMatch("))
        assertFalse(src.contains("YouTube.subscribeChannel("))

        val follow = body(src, "private suspend fun bookmarkArtist(artist: SourceArtist): Boolean {")
        assertTrue(follow.contains("name.isNotEmpty() && name == ArtistNameMatching.normalize(it.title)"))
        assertTrue(follow.contains("if (existing?.unfollowedByUserAt != null) return true"))
        assertFalse(follow.contains("unfollowedByUserAt = null"))
        assertTrue(follow.contains(awaitingWrite))
        assertTrue(follow.indexOf(awaitingWrite) < follow.indexOf("database.withTransaction {"))
    }

    /** (d) The upload pass never subscribes held artists, and holds everything when it cannot read the sets. */
    @Test
    fun `the upload pass skips imported follows until the user confirms them`() {
        val keys = main("constants/PreferenceKeys.kt")
        // Renaming a key would release every held artist on the next pass.
        assertTrue(keys.contains("val ImportFollowsAwaitingConfirmKey = stringSetPreferencesKey(\"import_follows_awaiting_confirm\")"))
        assertTrue(keys.contains("val ImportFollowsLocalOnlyKey = stringSetPreferencesKey(\"import_follows_local_only\")"))
        assertTrue(keys.contains("val ImportFollowsPendingHeldV1Key = booleanPreferencesKey(\"import_follows_pending_held_v1\")"))

        val src = main("utils/LibraryUploadSync.kt")
        assertTrue(src.contains(".filter { held != null && it.id !in held }"))
        assertTrue(src.contains("pending = (followsTotal - followsSynced - heldPending).coerceAtLeast(0) + pendingUnsubs"))

        val held = body(src, "suspend fun importFollowsHeldBack(): Set<String>? = withContext(Dispatchers.IO) {")
        assertTrue("follows pending before this build are held once", held.contains("if (prefs[ImportFollowsPendingHeldV1Key] != true) {"))
        assertTrue(held.contains("it[ImportFollowsAwaitingConfirmKey] = it[ImportFollowsAwaitingConfirmKey].orEmpty() + pendingIds"))
    }

    /** (d) Switching the upload on asks first when it would subscribe pending follows; "Subscribe" releases only the counted ones. */
    @Test
    fun `the sync screen asks before subscribing pending or imported follows`() {
        val src = main("ui/screens/settings/YtmSyncScreen.kt")
        assertTrue(src.contains(".count { iad1tya.echo.music.utils.ArtistSyncPolicy.maySubscribe(it) && it.id !in held }"))
        assertTrue(src.contains("if (pending > 0) pendingFollowsToConfirm.value = pending else setUploadEnabled(true)"))
        assertTrue(src.contains("val confirmed = awaitingImportCounted"))
        assertTrue(src.contains("val keptLocal = awaitingImportCounted"))
        assertTrue(src.contains("R.string.ytm_upload_import_subscribe"))
        assertTrue(src.contains("R.string.ytm_upload_import_local_only"))
        assertTrue("undo «Only in Aura»", src.contains("R.plurals.ytm_upload_import_local_only_count"))
    }

    /** A follow made by tap releases the artist from both held sets. */
    @Test
    fun `a tap follow releases the import hold`() {
        assertTrue(
            main("db/entities/ArtistEntity.kt")
                .contains("if (followedByUserAt == null) iad1tya.echo.music.utils.AccountPush.onUserFollowedArtist(id)"),
        )
        val release = body(main("utils/AccountPush.kt"), "suspend fun onUserFollowedArtist(artistId: String) {")
        assertTrue(release.contains("it[ImportFollowsAwaitingConfirmKey] = it[ImportFollowsAwaitingConfirmKey].orEmpty() - artistId"))
        assertTrue(release.contains("it[ImportFollowsLocalOnlyKey] = it[ImportFollowsLocalOnlyKey].orEmpty() - artistId"))
    }
}
