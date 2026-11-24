package iad1tya.echo.music.eq.autoeq

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 802 (owner 2026-11-24): switching Auto-EQ OFF (or removing it) gives back the preamp the user had
 * before Auto-EQ was switched ON, instead of leaving the profile's headroom (−6.1 dB for an HD 650).
 */
class AutoEqPreampRestoreGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val viewModel by lazy {
        File(root, "app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt").readText()
    }

    private fun body(signature: String) = viewModel.substringAfter(signature).substringBefore("\n    }")

    @Test fun `the user's preamp is saved only on an OFF to ON transition`() {
        val remember = body("private fun rememberPreampBeforeAutoEq() {")
        assertTrue(remember.contains("if (!_autoEqActive.value) prefs.edit().putFloat(\"autoeq_preamp_before\", _preamp.value).apply()"))
    }

    @Test fun `applying a profile saves the user's preamp before overwriting it`() {
        val batch = body("fun applyProfileBatch(")
        val save = batch.indexOf("if (isAutoEq) rememberPreampBeforeAutoEq()")
        val overwrite = batch.indexOf("_preamp.value = preampDb.coerceIn(EqConstants.PREAMP_MIN, EqConstants.PREAMP_MAX)")
        assertTrue(save >= 0)
        assertTrue(overwrite > save)
    }

    @Test fun `ON saves the user's preamp before the profile preamp comes back`() {
        val on = body("fun reEnableAutoEq() {")
        val save = on.indexOf("rememberPreampBeforeAutoEq()")
        assertTrue(save >= 0)
        assertTrue(on.indexOf("prefs.getFloat(\"autoeq_preamp\", _preamp.value)") > save)
        assertTrue(on.indexOf("setAutoEqActive(true)") > save)
    }

    @Test fun `OFF and Quitar restore it while the stage is still marked ON`() {
        for (signature in listOf("fun disableAutoEq() {", "fun clearAutoEq() {")) {
            val b = body(signature)
            val restore = b.indexOf("restorePreampBeforeAutoEq()")
            assertTrue(signature, restore >= 0)
            assertTrue(signature, b.indexOf("setAutoEqActive(false)") > restore)
            assertTrue(signature, b.indexOf("applyToService()") > restore)
        }
    }

    @Test fun `the restore writes the master preamp once and forgets the saved value`() {
        val restore = body("private fun restorePreampBeforeAutoEq() {")
        assertTrue(restore.contains("if (!_autoEqActive.value || !prefs.contains(\"autoeq_preamp_before\")) return"))
        assertTrue(restore.contains("prefs.edit().putFloat(\"preampDb\", _preamp.value).remove(\"autoeq_preamp_before\").apply()"))
    }
}
