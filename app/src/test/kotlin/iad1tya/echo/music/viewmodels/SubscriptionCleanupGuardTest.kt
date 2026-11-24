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

    /** Row 838: the run moved out of the ViewModel into the app-scoped runner and its pure engine. */
    private val runnerPath = "app/src/main/kotlin/com/music/echo/utils/SubscriptionCleanupRunner.kt"
    private val enginePath = "app/src/main/kotlin/com/music/echo/utils/SubscriptionCleanupEngine.kt"
    private val workerPath = "app/src/main/kotlin/com/music/echo/utils/SubscriptionCleanupWorker.kt"

    @Test
    fun `the tool unsubscribes only through the manual unfollow tap`() {
        val runner = src(runnerPath)
        assertTrue(runner.contains("database.toggleArtistFollow(item.id, fallback = null, shownFollowed = true)"))
        listOf(
            src(vmPath), src(screenPath), runner, src(enginePath), src(workerPath),
            src("app/src/main/kotlin/com/music/echo/utils/PhantomSubscriptionPolicy.kt"),
        ).forEach {
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
        val engine = src(enginePath)
        assertTrue(engine.contains("spacingMs = PhantomSubscriptionPolicy.UNSUBSCRIBE_SPACING_MS,"))
        assertTrue(engine.contains("shouldStop = { stopRequested.value || account == null || accountTag() != account },"))
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
        val runner = src(runnerPath)
        val wait = runner.substringAfter("val confirmed = withTimeoutOrNull(PhantomSubscriptionPolicy.CONFIRM_TIMEOUT_MS) {")
            .substringBefore("} ?: false")
        assertTrue(wait.contains(".first { rows -> rows.none(ArtistSyncPolicy::hasPendingUnfollow) }"))
        assertFalse(wait.contains("stopRequested"))
        assertFalse(wait.contains("stopped"))
    }

    @Test
    fun `a resend goes through the same read-back and tap, with the hold the policy picks`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("resendAllowed = PhantomSubscriptionPolicy.resendAllowed(item.id, local.filter { it.id in spellings }, now),"))
        val runner = src(runnerPath)
        assertTrue(runner.contains("database.markArtistsSubscribedOnYtm(listOf(item.id), now, PhantomSubscriptionPolicy.readBackHoldSince(item, now))"))
        assertFalse(runner.contains("markArtistsSubscribedOnYtm(listOf(item.id), now, ArtistSyncPolicy.confirmedUnfollowHoldSince(now))"))
        val readBack = runner.indexOf("PhantomSubscriptionPolicy.readBackHoldSince(item, now)")
        assertTrue("read-back before the tap", readBack in 0 until runner.indexOf("database.toggleArtistFollow(item.id, fallback = null, shownFollowed = true)"))
        val screen = src(screenPath)
        assertTrue(screen.contains("candidate.resendAllowed -> Text(\n                                        stringResource(R.string.sub_cleanup_resend_badge),"))
        assertTrue("the resend badge wins over the confirmed one", screen.indexOf("candidate.resendAllowed ->") < screen.indexOf("candidate.removalConfirmed ->"))
    }

    @Test
    fun `logs carry counts only`() {
        listOf(vmPath, runnerPath, enginePath, workerPath).forEach { path ->
            val lines = Regex("""(Timber\.\w|\blog)\([^\n]*(\n[^\n]*){0,4}""").findAll(src(path)).map { it.value }.toList()
            assertTrue(path, lines.isNotEmpty())
            lines.forEach { line ->
                listOf(".name", ".title", "thumbnail", "cookie", "item.id").forEach { assertFalse("$it in a log: $line", line.contains(it)) }
            }
        }
    }

    @Test
    fun `the run belongs to the app-scoped runner, not to the ViewModel`() {
        val vm = src(vmPath)
        listOf("runBatch(", "toggleArtistFollow", "stopRequested", "withTimeoutOrNull", "awaitingAccount", "runUploadPass").forEach {
            assertFalse("the ViewModel owns $it again", vm.contains(it))
        }
        assertTrue(vm.contains("private val runner: SubscriptionCleanupRunner,"))
        assertTrue(vm.contains("combine(_state, runner.state) { list, run -> merge(list, run) }"))
        assertTrue(vm.contains("refuseUnlessStarted(runner.start(plan.mapNotNull { byId[it] }))"))
        val runner = src(runnerPath)
        assertTrue(runner.contains("@Singleton\nclass SubscriptionCleanupRunner @Inject constructor("))
        assertTrue(runner.contains("private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)"))
        assertFalse(runner.contains("viewModelScope"))
        assertTrue(src(enginePath).contains("PhantomSubscriptionPolicy.runBatch("))
        // The foreground job exists only while a run is active, follows the state (no polling) and is started only by a run.
        val worker = src(workerPath)
        assertTrue(worker.contains("if (!runner.state.value.active) return Result.success()"))
        assertTrue(worker.contains(".takeWhile { it.active }"))
        assertTrue(worker.contains("ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(worker.contains("R.string.sub_cleanup_notification_progress, done, total"))
        listOf("delay(", "while (true)", "setInitialDelay", "PeriodicWorkRequest").forEach { assertFalse("polling: $it", worker.contains(it)) }
        val enqueuers = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("SubscriptionCleanupWorker.enqueue(") }
            .map { it.name }.toSet()
        assertEquals(setOf("SubscriptionCleanupRunner.kt"), enqueuers)
        assertEquals(2, Regex("""if \(started\) SubscriptionCleanupWorker\.enqueue\(context\)""").findAll(runner).count())
    }

    @Test
    fun `a run cut short by a process death comes back as a notice, never as a run`() {
        val runner = src(runnerPath)
        // Its own small DataStore file, not the settings one (every settings write wakes all its collectors).
        assertTrue(runner.contains("by preferencesDataStore(name = \"subscription_cleanup_run\")"))
        assertFalse(runner.contains("context.dataStore"))
        assertTrue(runner.contains("prefs[SubscriptionCleanupRunKey] = SubscriptionCleanupEngine.encode(run)"))
        val init = runner.substringAfter("    init {").substringBefore("\n    }\n")
        assertTrue(init.contains("engine.restore(saved)"))
        assertFalse("nothing resumes on its own", init.contains("start("))
        val vm = src(vmPath)
        assertTrue(vm.contains("interrupted = run.interrupted?.takeIf { !run.active && runner.isCurrentAccount(it) },"))
        val resume = vm.substringAfter("fun continueInterrupted() {").substringBefore("\n    }\n")
        assertTrue("re-planned against the list just read", resume.contains("selected = saved.pending.toSet(),"))
        assertTrue(resume.contains("blocked = s.candidates.filter { it.blocked }.map { it.id }.toSet(),"))
        assertTrue(resume.contains("refuseUnlessStarted(runner.start(plan.mapNotNull { byId[it] }))"))
        val screen = src(screenPath)
        assertEquals(1, Regex("""viewModel\.continueInterrupted\(\)""").findAll(screen).count())
        assertTrue(screen.contains("stringResource(R.string.sub_cleanup_interrupted, interrupted.done, interrupted.total),"))
        assertTrue(screen.contains("state.interrupted?.takeIf { !running }?.let { interrupted ->"))
    }

    @Test
    fun `the notification's stop asks the runner to stop, only when this app cancelled the job`() {
        val worker = src(workerPath)
        assertTrue(worker.contains("WorkManager.getInstance(ctx).createCancelPendingIntent(this@SubscriptionCleanupWorker.id),"))
        assertTrue(worker.contains("if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) runner.stop()"))
        assertTrue("no stop button while stopping", worker.contains("if (!stopping) {\n                    addAction("))
    }

    @Test
    fun `every new run first folds what the last one removed into the list`() {
        val vm = src(vmPath)
        assertTrue(vm.contains("val gone = SubscriptionCleanupEngine.removedBy(runner.state.value)"))
        assertTrue(vm.contains("_state.update { s -> s.copy(candidates = s.candidates.filterNot { it.id in gone }, selected = s.selected - gone) }"))
        listOf("fun removeSelected() {", "fun retryFailed() {", "fun continueInterrupted() {").forEach { name ->
            val body = vm.substringAfter(name).substringBefore("\n    }\n")
            val start = Regex("""runner\.(start|retryFailed)\(""").find(body)?.range?.first ?: -1
            assertTrue("$name starts a run", start >= 0)
            assertTrue("$name folds the last run first", body.indexOf("absorbLastRun()") in 0 until start)
        }
        assertEquals(
            "every run start is preceded by the fold",
            Regex("""runner\.(start|retryFailed)\(""").findAll(vm).count(),
            Regex("""absorbLastRun\(\)\n""").findAll(vm).count(),
        )
    }

    @Test
    fun `the screen marks the run's rows and says when a new run is refused`() {
        val screen = src(screenPath)
        assertTrue(screen.contains("candidate.id in state.removedIds -> Text("))
        assertTrue(screen.contains("candidate.id in state.failedIds -> Text("))
        assertTrue(screen.contains("if (state.refused) {"))
        assertTrue(screen.contains("stringResource(R.string.sub_cleanup_background_hint),"))
        assertTrue("the marks win over the older badges", screen.indexOf("candidate.id in state.removedIds ->") < screen.indexOf("candidate.removalPending ->"))
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
