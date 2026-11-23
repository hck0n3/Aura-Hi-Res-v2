package iad1tya.echo.music.eq.autoeq

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 133 and 792: the Auto-EQ screen's switch must never touch the master EQ, and OFF must keep the
 * profile's bands so ON (reEnableAutoEq, a no-op without bands) can bring the same correction back.
 */
class AutoEqSwitchGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun source(path: String): String = File(root, path).readText()

    private val screen by lazy {
        source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/autoeq/AutoEqScreen.kt")
    }
    private val viewModel by lazy {
        source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/axion/AxionEqViewModel.kt")
    }

    @Test fun `switch OFF disables Auto-EQ instead of erasing it`() {
        assertTrue(screen.contains("if (on) eqViewModel.reEnableAutoEq() else eqViewModel.disableAutoEq()"))
        assertFalse(screen.contains("eqViewModel.clearAutoEq()"))
        assertFalse(screen.contains("eqViewModel.setEnabled("))
    }

    @Test fun `disableAutoEq keeps the stored bands`() {
        val body = viewModel.substringAfter("fun disableAutoEq() {").substringBefore("\n    }")
        assertTrue(body.contains("setAutoEqActive(false)"))
        assertFalse(body.contains("_autoEqBands.value ="))
        assertFalse(body.contains("persistAutoEqBands()"))
        assertFalse(body.contains("setEnabled("))
    }
}
