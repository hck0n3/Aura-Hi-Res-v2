package iad1tya.echo.music.eq

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 921 — «Comparar A/B», as a source guard (no Android runtime here): the comparison never writes the saved
 * profile or any setting, B is the same bypass the equalizer switch publishes plus a level-matched preamp, the
 * switch is instant (no processor rebuild; bands and preamp glide in the native chain, row 912), and it is not
 * offered in «Modo Fidelidad».
 */
class AbCompareGuardTest {

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun source(path: String): String =
        File(repoRoot, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private val vm by lazy { source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt") }
    private val service by lazy { source("app/src/main/kotlin/com/music/echo/eq/EqualizerService.kt") }
    private val processor by lazy { source("app/src/main/kotlin/com/music/echo/eq/audio/CustomEqualizerAudioProcessor.kt") }
    private val screen by lazy { source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqScreen.kt") }

    private fun body(src: String, signature: String): String {
        assertTrue("$signature not found", src.contains(signature))
        return src.substringAfter(signature).substringBefore("\n    }\n")
    }

    /** Anything that would store a setting or a profile. */
    private val writes = listOf(
        "prefs", "edit()", "putFloat", "putBoolean", "dataStore", "eqProfileRepository", "saveProfile",
        "setUnsavedProfile", "setActiveProfile", "commit(", "persist", "applyProfile(",
    )

    @Test
    fun `the A-B toggle never writes the saved profile or a setting`() {
        val preview = body(vm, "fun previewBypass(bypass: Boolean) {")
        writes.forEach { assertFalse("previewBypass must not touch «$it»", preview.contains(it)) }
        assertTrue(preview.contains("val on = bypass && _enabled.value && !fidelityOn.value"))
        assertTrue(preview.contains("_abReferenceGainDb.value = equalizerService.setAbReference(on)"))
        // Leaving the screen ends it (onCleared also commits DIRTY edits, which A/B never makes: it sets no field).
        val cleared = body(vm, "override fun onCleared() {")
        assertTrue(cleared.contains("if (abRequested) equalizerService.setAbReference(false)"))
        assertFalse(preview.contains("_isDirty"))

        val svc = body(service, "fun setAbReference(on: Boolean): Double? {")
        listOf("pendingProfile", "shouldDisable", "applyProfile").forEach {
            assertFalse("EqualizerService.setAbReference must not touch «$it»", svc.contains(it))
        }
        assertTrue(svc.contains("processor.setAbReference(on)"))

        val proc = body(processor, "fun setAbReference(on: Boolean): Double? {")
        listOf("currentProfile =", "prefs", "SharedPreferences", "dataStore").forEach {
            assertFalse("processor.setAbReference must not touch «$it»", proc.contains(it))
        }
    }

    @Test
    fun `B is the equalizer bypass plus a level-matched preamp, and the switch is instant`() {
        // The same stages the equalizer switch bypasses (EqBypass.toneStages), through the single publisher.
        val push = body(processor, "private fun pushToneStages(ptr: Long) {")
        assertTrue(push.contains("masterEqOn = enabled && !abReference,"))
        // No band is loaded in B (each glides to flat natively), and the preamp is A's level.
        val apply = body(processor, "fun applyProfile(profile: ParametricEQ) {")
        assertTrue(apply.contains("val reference = abReference"))
        assertTrue(apply.contains("setPreamp(ptr, (if (reference) abReferenceGainDb(profile, effectivePreamp) else effectivePreamp).toFloat())"))
        assertTrue(apply.contains("if (!reference) allBands.forEachIndexed { index, band ->"))
        assertTrue(processor.contains("iad1tya.echo.music.eq.EqLevelMatch.referenceGainDb("))
        // Re-applies the current profile in place: no release/re-init of the native processor.
        val proc = body(processor, "fun setAbReference(on: Boolean): Double? {")
        assertTrue(proc.contains("if (enabled) currentProfile?.let { applyProfile(it) }"))
        listOf("releaseSuperpowered", "initSuperpowered", "disable()").forEach { assertFalse(proc.contains(it)) }
        // Switching the equalizer off (or the playback side's own bypass) ends the comparison.
        assertTrue(body(processor, "fun disable() {").contains("abReference = false"))
        assertTrue(body(service, "fun disable() {").contains("abReference = false"))
        // Never armed with the equalizer off, so switching it on always starts on A.
        assertTrue(proc.contains("val target = on && enabled"))
        // A player created mid-comparison plays the same side, once its profile is in.
        val add = body(service, "fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {")
        assertTrue(add.indexOf("applyProfileToProcessor(processor, profile)") < add.indexOf("if (abReference) processor.setAbReference(true)"))
        // What makes it click-free: the native glide of row 912 for removed bands and for the preamp.
        val bridge = source("app/src/main/cpp/superpowered/SuperpoweredBridge.cpp")
        assertTrue(bridge.contains("eqBiquads[i].glideTo(kIdentity, glide);"))
        assertTrue(bridge.contains("preampGlideLeft = n;"))
        // B needs runEq (enabled stays true) so the bands keep running while they glide to flat.
        assertFalse(proc.contains("enabled = false"))
    }

    @Test
    fun `the screen offers it in both modes, releases it on leave and explains when it is unavailable`() {
        assertEquals(1, Regex("""AbCompareCard\(\s*enabled = enabled,""").findAll(screen).count())
        assertTrue(screen.contains("onPreview = { viewModel.previewBypass(it) },"))
        val card = screen.substringAfter("private fun AbCompareCard(").substringBefore("\n}\n")
        assertTrue(card.contains("val active = enabled && !fidelityOn"))
        assertTrue(card.contains("} finally {\n                            onPreview(false)\n                        }"))
        listOf(
            "R.string.eq_ab_compare_button", "R.string.eq_ab_compare_unavailable_fidelity",
            "R.string.eq_ab_compare_needs_eq", "R.string.eq_ab_compare_listening_b", "R.string.eq_ab_compare_desc",
        ).forEach { assertTrue(it, card.contains(it)) }
        // The old graphic-only hold button is gone: one A/B control, not two.
        assertFalse(screen.contains("Mantén presionado para escuchar sin EQ"))
        for (dir in listOf("values", "values-es")) {
            val strings = source("app/src/main/res/$dir/strings.xml")
            listOf(
                "eq_ab_compare_button", "eq_ab_compare_desc", "eq_ab_compare_listening_b",
                "eq_ab_compare_unavailable_fidelity", "eq_ab_compare_needs_eq",
            ).forEach { assertTrue("$dir: $it", strings.contains("<string name=\"$it\">")) }
        }
    }
}
