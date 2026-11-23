package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Row 678 (owner 2026-11-23): Google account sync OFF by default on NEW installs; every existing install keeps
 * what it had — a stored choice is never flipped and a never-written value is written as the OLD default.
 */
class GoogleSyncDefaultsTest {
    private fun src(path: String) = File("src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun aFreshInstallStartsWithEverySyncOff() {
        assertFalse(GoogleSyncDefaults.downloadSync(stored = null, freshInstall = true))
        assertEquals(0, GoogleSyncDefaults.autoSyncFreqDays(stored = null, freshInstall = true))
        assertFalse(LibraryUploadOptIn.decide(stored = null, freshInstall = true))
    }

    @Test
    fun anExistingInstallWithNothingStoredKeepsWhatItWasRunningOn() {
        assertTrue(GoogleSyncDefaults.downloadSync(stored = null, freshInstall = false))
        assertEquals(3, GoogleSyncDefaults.autoSyncFreqDays(stored = null, freshInstall = false))
        assertEquals(YtmAutoSyncWorker.DEFAULT_FREQ_DAYS, GoogleSyncDefaults.autoSyncFreqDays(stored = null, freshInstall = false))
    }

    @Test
    fun aStoredChoiceIsNeverFlipped() {
        for (fresh in listOf(true, false)) {
            assertTrue(GoogleSyncDefaults.downloadSync(stored = true, freshInstall = fresh))
            assertFalse(GoogleSyncDefaults.downloadSync(stored = false, freshInstall = fresh))
            assertEquals(1, GoogleSyncDefaults.autoSyncFreqDays(stored = 1, freshInstall = fresh))
            assertEquals(0, GoogleSyncDefaults.autoSyncFreqDays(stored = 0, freshInstall = fresh))
            assertEquals(7, GoogleSyncDefaults.autoSyncFreqDays(stored = 7, freshInstall = fresh))
        }
    }

    @Test
    fun theOneTimeWriteRunsBeforeTheStartupSyncsAndOnlyForAFreshInstallTurnsThemOff() {
        val app = src("App.kt")
        val init = app.substringAfter("private suspend fun initializeSettings()")
        val write = init.indexOf("applyGoogleSyncDefaultsV1(p, settings, freshInstall)")
        assertTrue("the one-time write is gone", write > 0)
        assertTrue("it must run before the cold-start tryAutoSync", write < init.indexOf("syncUtils.tryAutoSync()"))
        assertTrue("it must run before the HALLAZGO-061 recovery", write < init.indexOf("syncUtils.syncLibraryAfterLogin()"))
        val freshLine = init.substring(init.lastIndexOf("val freshInstall", write), write)
        assertTrue(freshLine.contains("installOrigin == iad1tya.echo.music.utils.InstallOrigin.FRESH"))
        val fn = app.substringAfter("private fun applyGoogleSyncDefaultsV1(").substringBefore("\n    }\n")
        assertTrue(fn.contains("GoogleSyncDefaultsV1AppliedKey] == true) return"))
        assertTrue("stored values come from the live transaction", fn.contains("stored = p[YtmSyncKey]"))
        assertTrue(fn.contains("stored = p[iad1tya.echo.music.constants.YtmAutoSyncFreqDaysKey]"))
    }

    @Test
    fun everyAutomaticPullHonoursTheOffState() {
        val app = src("App.kt").substringAfter("private suspend fun initializeSettings()")
        val recovery = app.substringBefore("syncUtils.syncLibraryAfterLogin()").substringAfterLast("HALLAZGO-061: stuck-state recovery")
        assertTrue("the cold-start recovery must read the download-sync switch", recovery.contains("dataStore.data.first()[YtmSyncKey] ?: true"))

        val sync = src("utils/SyncUtils.kt").substringAfter("fun tryAutoSync() {").substringBefore("fun runAllSyncs()")
        assertTrue(sync.contains("!context.isSyncEnabled()"))
        assertTrue("row 214: <= 0 means no automatic sync", sync.contains("if (freqDays <= 0) return@launch"))

        val worker = src("utils/YtmAutoSyncWorker.kt").substringAfter("override suspend fun doWork()")
        assertTrue(
            "the scheduled worker must no-op when the cadence is off",
            worker.indexOf("DEFAULT_FREQ_DAYS) <= 0") in 1 until worker.indexOf("performFullSyncSuspend()"),
        )
        assertTrue(src("utils/YtmAutoSyncWorker.kt").contains("if (freqDays <= 0) {\n                wm.cancelUniqueWork(WORK_NAME)"))

        // A «library» notice from another of the user's devices pulls from YouTube: it honours the switch too.
        val library = src("devicesync/DeviceSyncEngine.kt").substringAfter("\"library\" -> {").substringBefore("withContext(Dispatchers.Main)")
        assertTrue(library.contains("dataStore.data.first()[iad1tya.echo.music.constants.YtmSyncKey] == false"))

        val tabs = src("ui/newui/AuraLibraryTabs.kt")
        assertFalse("a library tab pulls without the switch", Regex("""\n\s+syncUtils\.tryAutoSync\(\)""").containsMatchIn(tabs))
    }

    @Test
    fun logoutStillStoresAnExplicitFalse() {
        // Row 106: never remove() the upload switch on detach.
        val app = src("App.kt")
        assertTrue(app.contains("settings[iad1tya.echo.music.constants.YtmUploadSyncKey] =\n                    iad1tya.echo.music.utils.LibraryUploadOptIn.onAccountDetached()"))
        assertFalse(app.contains("remove(iad1tya.echo.music.constants.YtmUploadSyncKey)"))
    }
}
