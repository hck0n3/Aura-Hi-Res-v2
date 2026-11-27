package iad1tya.echo.music.viewmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 905 — «Seguidos solo en Aura» inside «Revisar suscripciones». The local removal must never reach the
 * account, start only from its preview dialog, re-check each channel right before the write, and log counts only. Room,
 * DataStore and Compose do not run in a JVM test, so the wiring is pinned as source.
 */
class LocalFollowCleanupGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val policyPath = "app/src/main/kotlin/com/music/echo/utils/LocalFollowCleanupPolicy.kt"
    private val cleanupPath = "app/src/main/kotlin/com/music/echo/utils/LocalFollowCleanup.kt"
    private val vmPath = "app/src/main/kotlin/com/music/echo/viewmodels/LocalFollowCleanupViewModel.kt"
    private val screenPath = "app/src/main/kotlin/com/music/echo/ui/screens/settings/SubscriptionCleanupScreen.kt"

    @Test
    fun `the local removal never talks to the account nor writes an unfollow marker`() {
        listOf(src(policyPath), src(cleanupPath), src(vmPath)).forEach {
            listOf("subscribeChannel(", "toggleArtistFollow", "toggleLike(", "localToggleLike(", "YouTube.", "AccountPush.", "runUploadPass", "unfollowedByUserAt =")
                .forEach { forbidden -> assertFalse("$forbidden in the local cleanup", it.contains(forbidden)) }
        }
        val policy = src(policyPath)
        assertTrue(policy.contains("return row.copy(followedByUserAt = null, bookmarkedAt = null)"))
    }

    @Test
    fun `each channel is re-checked on fresh rows and fresh lists inside one transaction`() {
        val remove = src(cleanupPath).substringAfter("suspend fun remove(").substringBefore("\n    /**")
        assertTrue(remove.contains("val lists = lists() ?: return@withLock null"))
        assertTrue(remove.contains("database.withTransaction {"))
        assertTrue(remove.contains("val spellings = artistEntitiesByIdsInTransaction(ArtistSyncPolicy.aliasIds(candidate.id))"))
        assertTrue(remove.contains("LocalFollowCleanupPolicy.cleared(row, spellings, lists.awaiting, lists.localOnly)"))
        val undo = src(cleanupPath).substringAfter("suspend fun undo(").substringBefore("\n    private companion object")
        assertTrue(undo.contains("LocalFollowCleanupPolicy.restored(row, snapshot) ?: continue"))
        // The lists are the import lists of the account signed in now, read the way the upload pass reads them.
        val lists = src(cleanupPath).substringAfter("suspend fun lists(").substringBefore("\n    /**")
        assertTrue(lists.contains("libraryUploadSync.importFollowsHeldBack()"))
        assertTrue(lists.contains("prefs[ImportFollowsLocalOnlyKey].orEmpty()"))
    }

    /**
     * Repair of row 905: the awaiting list alone is no proof an import wrote a follow (its one-time move also files the
     * onboarding picks, follows made signed out and a restore's follows). Only the ids the importers record are offered.
     */
    @Test
    fun `only the follows an import itself wrote are offered`() {
        val lists = src(cleanupPath).substringAfter("suspend fun lists(").substringBefore("\n    /**")
        assertTrue(
            lists.contains(
                "LocalFollowCleanupPolicy.importedAwaiting(\n" +
                    "                prefs[ImportFollowsAwaitingConfirmKey].orEmpty(),\n" +
                    "                prefs[ImportFollowsWrittenByImportKey].orEmpty(),\n" +
                    "            ),",
            ),
        )
        assertTrue(
            src("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
                .contains("val ImportFollowsWrittenByImportKey = stringSetPreferencesKey(\"import_follows_written_by_import\")"),
        )
        val importWrite = "it[ImportFollowsAwaitingConfirmKey] = it[ImportFollowsAwaitingConfirmKey].orEmpty() + match.id\n" +
            "                it[ImportFollowsWrittenByImportKey] = it[ImportFollowsWrittenByImportKey].orEmpty() + match.id"
        listOf(
            "app/src/main/kotlin/com/music/echo/spotifyimport/SpotifyImportRepository.kt",
            "app/src/main/kotlin/com/music/echo/ui/screens/migration/MigrationViewModel.kt",
        ).forEach { assertTrue("$it records what the import wrote", src(it).contains(importWrite)) }
        // Only the importers add to the record; the follow tap takes ids out of it.
        val writers = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("it[ImportFollowsWrittenByImportKey] = ") }
            .map { it.name }.toSet()
        assertEquals(setOf("SpotifyImportRepository.kt", "MigrationViewModel.kt", "AccountPush.kt"), writers)
        assertTrue(
            src("app/src/main/kotlin/com/music/echo/utils/AccountPush.kt").substringAfter("suspend fun onUserFollowedArtist(artistId: String) {")
                .substringBefore("\n    }\n")
                .contains("it[ImportFollowsWrittenByImportKey] = it[ImportFollowsWrittenByImportKey].orEmpty() - artistId"),
        )
        val restore = src("app/src/main/kotlin/com/music/echo/viewmodels/BackupRestoreViewModel.kt")
        assertTrue(
            restore.contains(
                "settings.remove(iad1tya.echo.music.constants.ImportFollowsPendingHeldV1Key)\n" +
                    "                        // Row 905: the rows it described were replaced; a restored follow is never «written by an import».\n" +
                    "                        settings.remove(iad1tya.echo.music.constants.ImportFollowsWrittenByImportKey)",
            ),
        )
    }

    /** Repair of row 905: the removed count lives next to the undo in the app-scoped singleton, not in the screen's ViewModel. */
    @Test
    fun `the undo row counts the channels kept by the singleton, and a failed removal says so`() {
        val cleanup = src(cleanupPath)
        assertTrue(cleanup.contains("data class Undo(val channels: Int = 0, val rows: List<LocalFollowCleanupPolicy.Snapshot> = emptyList())"))
        assertTrue(cleanup.contains("if (snapshots.isNotEmpty()) _undo.value = Undo(cleared, snapshots)"))
        assertTrue(cleanup.contains("suspend fun remove(plan: List<LocalFollowCleanupPolicy.Candidate>): Int? = lock.withLock {"))
        val vm = src(vmPath)
        assertTrue(vm.contains("private fun UiState.withUndo(undo: LocalFollowCleanup.Undo) = copy(lastRemoved = undo.channels, undoRows = undo.rows.size)"))
        assertTrue(vm.contains("combine(_state, cleanup.undo) { s, undo -> s.withUndo(undo) }"))
        assertFalse("the count is never held by the ViewModel itself", vm.contains("lastRemoved = removed"))
        assertTrue(vm.contains("removeFailed = removed == null,"))
        val screen = src(screenPath)
        assertTrue(screen.contains("Text(stringResource(R.string.sub_cleanup_local_remove_failed), color = MaterialTheme.colorScheme.error)"))
        assertTrue(screen.contains("pluralStringResource(R.plurals.sub_cleanup_local_removed, state.lastRemoved, state.lastRemoved)"))
        // The «N artistas seguidos solo en Aura» title is shown only when there is at least one.
        assertTrue(
            screen.contains(
                "            if (state.candidates.isNotEmpty()) {\n" +
                    "                Text(\n" +
                    "                    pluralStringResource(R.plurals.sub_cleanup_local_title, state.candidates.size, state.candidates.size),",
            ),
        )
    }

    @Test
    fun `logs carry counts only`() {
        listOf(cleanupPath, vmPath).forEach { path ->
            val lines = Regex("""Timber\.\w\([^\n]*(\n[^\n]*){0,3}""").findAll(src(path)).map { it.value }.toList()
            lines.forEach { line ->
                listOf(".name", ".title", "thumbnail", "cookie", "candidate.id", "it.id").forEach { assertFalse("$it in a log: $line", line.contains(it)) }
            }
        }
        assertTrue(src(cleanupPath).contains("\"LOCAL_FOLLOW_CLEANUP removed=%d skipped=%d rows=%d\""))
    }

    @Test
    fun `every new text exists in English and Spanish`() {
        val keys = { path: String -> Regex("""name="(sub_cleanup_local_\w+|sub_cleanup_confirm_list)"""").findAll(src(path)).map { it.groupValues[1] }.toSet() }
        val en = keys("app/src/main/res/values/subscription_cleanup_strings.xml")
        val es = keys("app/src/main/res/values-es/subscription_cleanup_strings.xml")
        assertTrue(en.size >= 14)
        assertEquals(en, es)
        listOf("values", "values-es").forEach { v ->
            assertFalse(src("app/src/main/res/$v/subscription_cleanup_strings.xml").substringAfter("Registry row 905").contains("YouTube"))
        }
    }
}
