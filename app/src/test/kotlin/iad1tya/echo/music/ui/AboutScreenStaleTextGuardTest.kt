package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 827: Ajustes ▸ Acerca de must not describe features removed in Aug-Sep 2026 (Qobuz, JioSaavn and the
 * «Sin pérdida» quality), and its subscription entry must state the real device rule (one device with the key alone,
 * up to 3 with the purchase email, as the activation screen says, row 530).
 */
class AboutScreenStaleTextGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun `about screen names no removed service or quality`() {
        val about = main("ui/screens/settings/AboutScreen.kt")
        for (stale in listOf("Qobuz", "Saavn", "Sin pérdida", "sin pérdida", "Lossless", "lossless")) {
            assertFalse("AboutScreen.kt still mentions «$stale»", about.contains(stale))
        }
        assertFalse(about.contains("YouTube Music"))
    }

    @Test
    fun `subscription entry states the email multi-device rule`() {
        val about = main("ui/screens/settings/AboutScreen.kt")
        assertFalse(about.contains("Funciona en un equipo a la vez"))
        assertTrue(
            about.contains(
                "Con solo la clave funciona en un equipo a la vez; si además escribes el correo con el que la compraste, " +
                    "puedes usarla en hasta 3 equipos.",
            ),
        )
        // The activation screen promises the same number.
        val screens = main("license/LicenseScreens.kt")
        assertTrue(screens.contains("Con el mismo correo puedes usar la licencia en hasta 3 equipos."))
    }

    @Test
    fun `accounts entry lists only the services the accounts screen shows`() {
        val about = main("ui/screens/settings/AboutScreen.kt")
        assertTrue(about.contains("—tu cuenta de Google, Spotify, Last.fm y ListenBrainz—"))
    }
}
