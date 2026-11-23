package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 768: «Modo rendimiento» applies while the app is open, not only at the next start.
 */
class LeanModeLiveGuardTest {
    private val app: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/App.kt").readText()
    }

    @Test
    fun `every settings emission re-checks the lean flag the image loader was built with`() {
        val collector = app.substringAfter("applicationContext.dataStore.data.collect {").substringBefore("}.exceptionOrNull()")
        val publish = collector.indexOf("iad1tya.echo.music.utils.PrefsBridge.publish(it)")
        val reset = collector.indexOf("resetImageLoaderIfLeanWasStale()")
        assertTrue(publish >= 0)
        assertTrue("the check must read the freshly published value", reset > publish)
        assertFalse("the check must not be limited to the first emission", collector.contains("firstEmission"))
    }

    @Test
    fun `onTrimMemory treats lean mode like a LOW-tier device`() {
        val constrained = app.substringAfter("override fun onTrimMemory(level: Int) {")
            .substringAfter("val constrained =")
            .substringBefore("runCatching {")
            .replace(Regex("\\s+"), " ")
            .trim()
        assertTrue(
            constrained,
            constrained == "iad1tya.echo.music.utils.DeviceCapabilities.tier(this) == iad1tya.echo.music.utils.DeviceTier.LOW || " +
                "iad1tya.echo.music.utils.LeanMode.isOn()",
        )
    }
}
