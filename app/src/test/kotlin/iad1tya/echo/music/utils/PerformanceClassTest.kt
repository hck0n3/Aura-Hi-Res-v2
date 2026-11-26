package iad1tya.echo.music.utils

import iad1tya.echo.music.constants.AlbumCanvasEnabledKey
import iad1tya.echo.music.constants.CanvasThumbnailAnimationKey
import iad1tya.echo.music.constants.HighPerformanceModeKey
import iad1tya.echo.music.constants.LiquidGlassGlobalEnabledKey
import iad1tya.echo.music.constants.LiquidGlassInteractiveKey
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 898 (plan phase 7): performance classes, the one-time seed that never overwrites, and the settings row. */
class PerformanceClassTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── Class derivation: the existing tier, unchanged, under a name ──

    @Test
    fun `the tier comes from the low-RAM flag, the reported RAM, and only without RAM from cores and perf class`() {
        assertEquals(DeviceTier.LOW, DeviceCapabilities.classify(lowRam = true, totalRamMb = 12_000, cores = 8, perfClass = 34))
        assertEquals(DeviceTier.LOW, DeviceCapabilities.classify(false, 3_072, 8, 0))
        assertEquals(DeviceTier.MID, DeviceCapabilities.classify(false, 3_800, 8, 0))
        assertEquals(DeviceTier.MID, DeviceCapabilities.classify(false, 6_800, 8, 0))
        assertEquals(DeviceTier.HIGH, DeviceCapabilities.classify(false, 7_500, 4, 0))
        // RAM unknown (0): cores, then the OEM media performance class.
        assertEquals(DeviceTier.LOW, DeviceCapabilities.classify(false, 0, 4, 0))
        assertEquals(DeviceTier.MID, DeviceCapabilities.classify(false, 0, 6, 0))
        assertEquals(DeviceTier.MID, DeviceCapabilities.classify(false, 0, 8, 30))
        assertEquals(DeviceTier.HIGH, DeviceCapabilities.classify(false, 0, 8, 31))
        assertEquals(DeviceTier.HIGH, DeviceCapabilities.classify(false, 0, 8, 0))
    }

    @Test
    fun `each tier has exactly one class and the manual choice wins over the detected one`() {
        assertEquals(PerformanceClass.ENTRY, PerformanceClasses.fromTier(DeviceTier.LOW))
        assertEquals(PerformanceClass.ENTRY, PerformanceClasses.fromTier(DeviceTier.ULTRA))
        assertEquals(PerformanceClass.BALANCED, PerformanceClasses.fromTier(DeviceTier.MID))
        assertEquals(PerformanceClass.PREMIUM, PerformanceClasses.fromTier(DeviceTier.HIGH))
        for (detected in PerformanceClass.entries) {
            assertEquals(detected, PerformanceClasses.effective(detected, PerformanceClassChoice.AUTO))
            assertEquals(PerformanceClass.ENTRY, PerformanceClasses.effective(detected, PerformanceClassChoice.ENTRY))
            assertEquals(PerformanceClass.BALANCED, PerformanceClasses.effective(detected, PerformanceClassChoice.BALANCED))
            assertEquals(PerformanceClass.PREMIUM, PerformanceClasses.effective(detected, PerformanceClassChoice.PREMIUM))
        }
        assertEquals(PerformanceClassChoice.AUTO, PerformanceClasses.parseChoice(null))
        assertEquals(PerformanceClassChoice.AUTO, PerformanceClasses.parseChoice("garbage"))
        assertEquals(PerformanceClassChoice.PREMIUM, PerformanceClasses.parseChoice("PREMIUM"))
    }

    // ── What each class writes (the same values the existing one-time tier seeds use) ──

    @Test
    fun `entry turns the performance mode on and the canvas and glass off`() {
        val v = PerformanceClasses.seedValues(PerformanceClasses.seedFor(PerformanceClass.ENTRY, glassCapable = true))
        assertEquals(true, v[HighPerformanceModeKey.name])
        assertEquals(false, v[CanvasThumbnailAnimationKey.name])
        assertEquals(false, v[AlbumCanvasEnabledKey.name])
        assertEquals(false, v[LiquidGlassGlobalEnabledKey.name])
        assertEquals(false, v[LiquidGlassInteractiveKey.name])
    }

    @Test
    fun `balanced leaves the glass to the user and premium turns it on only where it can be drawn`() {
        val balanced = PerformanceClasses.seedValues(PerformanceClasses.seedFor(PerformanceClass.BALANCED, glassCapable = true))
        assertEquals(false, balanced[HighPerformanceModeKey.name])
        assertEquals(true, balanced[CanvasThumbnailAnimationKey.name])
        assertFalse(LiquidGlassGlobalEnabledKey.name in balanced)
        assertFalse(LiquidGlassInteractiveKey.name in balanced)

        val premium = PerformanceClasses.seedValues(PerformanceClasses.seedFor(PerformanceClass.PREMIUM, glassCapable = true))
        assertEquals(false, premium[HighPerformanceModeKey.name])
        assertEquals(true, premium[AlbumCanvasEnabledKey.name])
        assertEquals(true, premium[LiquidGlassGlobalEnabledKey.name])
        assertEquals(true, premium[LiquidGlassInteractiveKey.name])

        val premiumNoGlass = PerformanceClasses.seedValues(PerformanceClasses.seedFor(PerformanceClass.PREMIUM, glassCapable = false))
        assertFalse(LiquidGlassGlobalEnabledKey.name in premiumNoGlass)
        assertFalse(LiquidGlassInteractiveKey.name in premiumNoGlass)
    }

    // ── The automatic seed never overwrites ──

    @Test
    fun `the automatic seed fills only keys that were never written`() {
        val values = PerformanceClasses.seedValues(PerformanceClasses.seedFor(PerformanceClass.ENTRY, glassCapable = true))
        // The user (or an earlier one-time seed, which cannot be told apart) already set the performance mode.
        val present = setOf(HighPerformanceModeKey.name, LiquidGlassGlobalEnabledKey.name)
        val writes = PerformanceClasses.untouchedOnly(values, present)
        assertFalse(HighPerformanceModeKey.name in writes)
        assertFalse(LiquidGlassGlobalEnabledKey.name in writes)
        assertEquals(false, writes[CanvasThumbnailAnimationKey.name])
        assertEquals(false, writes[AlbumCanvasEnabledKey.name])
        assertEquals(false, writes[LiquidGlassInteractiveKey.name])
        // Everything already set: nothing at all is written.
        assertTrue(PerformanceClasses.untouchedOnly(values, values.keys).isEmpty())
        // Every key a class can name is one the writer knows.
        assertEquals(PerformanceClasses.SEEDED_KEYS.map { it.name }.toSet(), values.keys)
    }

    @Test
    fun `the seed runs once, last, from the shared transaction, never overwriting, and is recorded under its own key`() {
        val app = src("App.kt")
        val batch = app.substringAfter("if (batchAPending) {").substringBefore("migrateAudioDefaultsV2(settings)")
        assertTrue(
            "after every other seed of the batch",
            batch.indexOf("applyPerformanceClassSeedV1(p)") > batch.indexOf("applyPlayerBgHomeLookV1(p, settings)"),
        )
        assertTrue(app.contains("settings[iad1tya.echo.music.constants.PerformanceClassSeededKey] == null ||"))
        val seed = app.substringAfter("private fun applyPerformanceClassSeedV1(").substringBefore("private fun applySingleUiV1(")
        assertTrue(seed.contains("if (p[iad1tya.echo.music.constants.PerformanceClassSeededKey] != null) return"))
        assertTrue("never overwrites", seed.contains("classes.untouchedOnly(") && seed.contains("classes.presentIn(p)"))
        assertTrue(seed.contains("p[iad1tya.echo.music.constants.PerformanceClassSeededKey] = cls.name"))
        val keys = src("constants/PreferenceKeys.kt")
        assertTrue(keys.contains("stringPreferencesKey(\"performance_class_seeded_v1\")"))
        assertTrue(keys.contains("stringPreferencesKey(\"performance_class_choice\")"))
    }

    @Test
    fun `the settings page shows the class translated and offers the override, nothing removed`() {
        val page = src("ui/screens/settings/PerformanceSettings.kt")
        assertFalse("the detected text is translated now", page.contains("\"Dispositivo detectado: "))
        assertTrue(page.contains("R.string.perf_class_detected"))
        assertTrue(page.contains("title = { Text(detected) }"))
        assertTrue(page.contains("title = { Text(stringResource(R.string.perf_class_choice_title)) }"))
        assertTrue(page.contains("ActionLog.tap(ActionId.SETPERF_CLASS)"))
        assertTrue(page.contains("PerformanceClassChoice.entries.toList()"))
        // The rows that were there stay.
        for (row in listOf("Modo alto rendimiento", "Vista dividida estilo Spotify", "Panel del reproductor a la izquierda", "Mostrar el panel del reproductor")) {
            assertTrue("$row se perdió", page.contains(row))
        }
    }
}
