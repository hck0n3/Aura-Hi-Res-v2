package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 864 — owner decision H18: the app targets Android 17 (API 37). Raising or lowering it changes what the
 * system enforces (local network permission, background audio hardening, widget bitmap memory…), so the number is
 * pinned here together with the compileSdk it needs.
 */
class TargetSdk37GuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val appBuild by lazy {
        val f = File(root, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts moved: update this test", f.isFile)
        f.readText()
    }

    private fun intSetting(name: String): Int {
        val matches = Regex("""(?m)^\s*$name\s*=\s*(\d+)\s*$""").findAll(appBuild).map { it.groupValues[1].toInt() }.toList()
        assertEquals("expected exactly one `$name = N` in app/build.gradle.kts", 1, matches.size)
        return matches.single()
    }

    @Test
    fun `the app targets API 37`() {
        assertEquals(37, intSetting("targetSdk"))
    }

    @Test
    fun `compileSdk is at least the target`() {
        assertTrue(intSetting("compileSdk") >= intSetting("targetSdk"))
    }
}
