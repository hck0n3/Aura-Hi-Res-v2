package iad1tya.echo.music.eq.autoeq

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 915-916: the automatic correction is wired end to end (offer → assignment → every output change → the
 * engine), «Modo Fidelidad» still bypasses it, the Bluetooth name never reaches a log, and the credits are shown.
 */
class AutoEqDeviceGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }
    private fun source(path: String): String = File(root, path).readText()
    private val main = "app/src/main/kotlin/com/music/echo"

    @Test fun `every output change syncs the correction and Fidelidad still skips the engine`() {
        val service = source("$main/playback/MusicService.kt")
        val eq = service.substringAfter("private fun applyEqForCurrentOutput() {").substringBefore("\n    }\n")
        assertTrue(eq.contains("if (!store.isAutoApplyEnabled(this@MusicService)) return@launch"))
        assertTrue(eq.contains("applyDeviceAutoEq(key)"))
        assertTrue(eq.contains("applyDeviceAutoEq(key, afterDeviceProfile = true)"))
        val body = service.substringAfter("private suspend fun applyDeviceAutoEq(").substringBefore("\n    }\n")
        assertTrue(body.contains("AutoEqDeviceCorrection"))
        assertTrue(body.contains("if (!eqOn || base == null) return"))
        assertTrue(body.contains("if (!fidelityModeHint) equalizerService.applyProfile(next)"))
        // Rule 4: the log line names the state, never the output key (it holds the Bluetooth name).
        assertTrue(body.contains("Timber.tag(TAG).i(\"Auto-EQ device correction %s\", if (it.active) \"on\" else \"off\")"))
        assertTrue(body.split('\n').filter { it.contains("Timber") }.none { it.contains("outputKey") || it.contains("key") })
    }

    @Test fun `the device's correction never logs and a manual choice takes the stage back`() {
        for (file in listOf("AutoEqDeviceCorrection.kt", "AutoEqNameMatcher.kt", "AutoEqDeviceSwitch.kt", "AutoEqSafety.kt", "AutoEqOfflineCatalog.kt")) {
            val text = source("$main/eq/autoeq/$file")
            assertFalse(file, text.contains("Timber") || text.contains("AppLogger") || text.contains("Log."))
        }
        val offer = source("$main/ui/screens/equalizer/autoeq/AutoEqDeviceOffer.kt")
        assertFalse(offer.contains("Timber") || offer.contains("AppLogger"))
        val vm = source("$main/ui/screens/equalizer/axion/AxionEqViewModel.kt")
        val clear = vm.substringAfter("fun clearAutoEq() {").substringBefore("\n    }")
        assertTrue(clear.contains("AutoEqDeviceCorrection.forgetDeviceOwnership(prefs, keepOwner = true)"))
        val batch = vm.substringAfter("fun applyProfileBatch(").substringBefore("\n    }")
        assertTrue(batch.substringAfter("if (isAutoEq) {").substringBefore("} else {").contains("forgetDeviceOwnership(prefs)"))
        // Switching Auto-EQ off by hand keeps the owner, so leaving the headphone still restores the user's state.
        assertFalse(vm.substringAfter("fun disableAutoEq() {").substringBefore("\n    }").contains("forgetDeviceOwnership"))
    }

    @Test fun `the offer is hosted once, asked once, and the screen keeps the choice`() {
        assertTrue(source("$main/MainActivity.kt").contains("iad1tya.echo.music.ui.screens.equalizer.autoeq.AutoEqDeviceOfferHost()"))
        val offer = source("$main/ui/screens/equalizer/autoeq/AutoEqDeviceOffer.kt")
        assertTrue(offer.contains("!store.wasAutoEqOffered(context, output.key)"))
        assertTrue(offer.contains("EqDeviceProfileStore.markAutoEqOffered(context, current.output.key)"))
        // A dismissed offer is not lost: «Corregir automáticamente» stays in the Auto-EQ screen (hiding is losing).
        assertTrue(offer.contains("ActionLog.tap(ActionId.AUTOEQ_DEVICE_APPLY)"))
        assertTrue(source("$main/ui/screens/equalizer/autoeq/AutoEqScreen.kt").contains("AutoEqDeviceSection(eqViewModel)"))
    }

    @Test fun `the offer's view model plays the manual curve as saved, not as it was at app start`() {
        val vm = source("$main/ui/screens/equalizer/axion/AxionEqViewModel.kt")
        val sync = vm.substringAfter("fun syncDeviceAutoEq(").substringBefore("\n    }\n")
        assertTrue(sync.indexOf("reloadManualEqFromPrefs()") in 0 until sync.indexOf("AutoEqDeviceCorrection.sync("))
        val reload = vm.substringAfter("private fun reloadManualEqFromPrefs() {").substringBefore("\n    }")
        for (key in listOf("band24_", "gtype_", "gq_", "eq_mode")) assertTrue(key, reload.contains("\"$key"))
        assertTrue(reload.contains("loadPeqBands()"))
    }
}
