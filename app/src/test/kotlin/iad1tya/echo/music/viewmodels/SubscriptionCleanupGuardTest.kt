package iad1tya.echo.music.viewmodels

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 813 — «Revisar suscripciones». The tool must remove subscriptions ONLY through the manual unfollow tap
 * (`toggleArtistFollow`: the per-channel queue and bookkeeping of rows 700/711/716), only what the user selected and
 * confirmed, and log counts only. None of that runs in a JVM test (account, Room, Compose), so it is pinned as source.
 */
class SubscriptionCleanupGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val vmPath = "app/src/main/kotlin/com/music/echo/viewmodels/SubscriptionCleanupViewModel.kt"
    private val screenPath = "app/src/main/kotlin/com/music/echo/ui/screens/settings/SubscriptionCleanupScreen.kt"

    @Test
    fun `the tool unsubscribes only through the manual unfollow tap`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("database.toggleArtistFollow(item.id, fallback = null, shownFollowed = true)"))
        listOf(vm, src(screenPath), src("app/src/main/kotlin/com/music/echo/utils/PhantomSubscriptionPolicy.kt")).forEach {
            assertFalse(it.contains("subscribeChannel("))
            assertFalse(it.contains("unsubscribeChannel("))
            assertFalse(it.contains("unfollowedByUserAt ="))
        }
    }

    @Test
    fun `only the selection reaches the batch and only the confirmation dialog starts it`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("selected = s.selected,"))
        assertTrue(vm.contains("PhantomSubscriptionPolicy.planBatch("))
        assertTrue(vm.contains("init {\n        load()\n    }"))
        val screen = src(screenPath)
        assertEquals(1, Regex("""viewModel\.removeSelected\(\)""").findAll(screen).count())
        assertTrue(screen.contains("confirmCount = 0\n                    viewModel.removeSelected()"))
        val callers = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("removeSelected()") }
            .map { it.name }.toSet()
        assertEquals(setOf("SubscriptionCleanupViewModel.kt", "SubscriptionCleanupScreen.kt"), callers)
    }

    @Test
    fun `the batch is throttled and stops on sign-out or another account`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("spacingMs = PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS,"))
        assertTrue(vm.contains("shouldStop = { stopRequested.value || account == null || accountTag() != account },"))
    }

    @Test
    fun `the interest query keeps local-file artists, whose evidence the name bridge needs`() {
        val dao = src("app/src/main/kotlin/com/music/echo/db/DatabaseDao.kt")
        val query = dao.substringBefore("suspend fun artistInterestRows()").substringAfterLast("@Query(")
        assertTrue(query.contains("JOIN song ON song.id = song_artist_map.songId"))
        assertFalse(query.contains("isLocal = 0"))
    }

    @Test
    fun `stopping never reports a removal already sent to the account as failed`() {
        val vm = src(vmPath)
        val wait = vm.substringAfter("val confirmed = withTimeoutOrNull(PhantomSubscriptionPolicy.CONFIRM_TIMEOUT_MS) {")
            .substringBefore("} ?: false")
        assertTrue(wait.contains(".first { rows -> rows.none(ArtistSyncPolicy::hasPendingUnfollow) }"))
        assertFalse(wait.contains("stopRequested"))
        assertFalse(wait.contains("stopped"))
    }

    @Test
    fun `a resend goes through the same read-back and tap, with the hold the policy picks`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("resendAllowed = PhantomSubscriptionPolicy.resendAllowed(item.id, local.filter { it.id in spellings }, now),"))
        assertTrue(vm.contains("database.markArtistsSubscribedOnYtm(listOf(item.id), now, PhantomSubscriptionPolicy.readBackHoldSince(item, now))"))
        assertFalse(vm.contains("markArtistsSubscribedOnYtm(listOf(item.id), now, ArtistSyncPolicy.confirmedUnfollowHoldSince(now))"))
        val readBack = vm.indexOf("PhantomSubscriptionPolicy.readBackHoldSince(item, now)")
        assertTrue("read-back before the tap", readBack in 0 until vm.indexOf("database.toggleArtistFollow(item.id, fallback = null, shownFollowed = true)"))
        val screen = src(screenPath)
        assertTrue(screen.contains("candidate.resendAllowed -> Text(\n                                        stringResource(R.string.sub_cleanup_resend_badge),"))
        assertTrue("the resend badge wins over the confirmed one", screen.indexOf("candidate.resendAllowed ->") < screen.indexOf("candidate.removalConfirmed ->"))
    }

    @Test
    fun `logs carry counts only`() {
        val vm = src(vmPath)
        val lines = Regex("""Timber\.\w\([^\n]*(\n[^\n]*){0,4}""").findAll(vm).map { it.value }.toList()
        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            listOf(".name", ".title", "thumbnail", "cookie", "item.id").forEach { assertFalse("$it in a log: $line", line.contains(it)) }
        }
    }

    @Test
    fun `the row is reachable from Cuentas, registered and indexed, and every text exists in English and Spanish`() {
        assertTrue(
            src("app/src/main/kotlin/com/music/echo/ui/screens/settings/AccountsScreen.kt")
                .contains("onClick = { navController.navigate(\"settings/accounts/subscription_cleanup\") },"),
        )
        assertTrue(
            src("app/src/main/kotlin/com/music/echo/ui/screens/NavigationBuilder.kt")
                .contains("composable(\"settings/accounts/subscription_cleanup\") {"),
        )
        val index = src("app/src/main/kotlin/com/music/echo/ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.sub_cleanup_title), \"Cuentas\", \"settings/accounts\"),"))
        val keys = { path: String -> Regex("""name="(sub_cleanup_\w+)"""").findAll(src(path)).map { it.groupValues[1] }.toSet() }
        val en = keys("app/src/main/res/values/subscription_cleanup_strings.xml")
        val es = keys("app/src/main/res/values-es/subscription_cleanup_strings.xml")
        assertTrue(en.size >= 20)
        assertEquals(en, es)
    }
}
