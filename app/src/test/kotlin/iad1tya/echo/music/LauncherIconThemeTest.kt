package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 620: the launcher icon is the owner's liquid-glass artwork (one look in light and dark), with a themed silhouette layer. */
class LauncherIconThemeTest {
    private fun xml(path: String) = File("src/main/res/$path").readText()

    @Test
    fun everyAdaptiveIconUsesTheOwnersArtworkAndTheSilhouette() {
        val files = listOf(
            "mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml",
            "mipmap-anydpi/ic_launcher_static.xml", "mipmap-anydpi/ic_launcher_static_round.xml",
        )
        for (f in files) {
            val x = xml(f)
            assertTrue("$f background", x.contains("@drawable/ic_launcher_art"))
            assertTrue("$f themed layer", x.contains("<monochrome android:drawable=\"@drawable/ic_launcher_mark_mono\""))
        }
    }

    @Test
    fun theArtworkAndTheSilhouetteExistAndNoNightVariantOverridesThem() {
        assertTrue(File("src/main/res/drawable-nodpi/ic_launcher_art.webp").length() > 10_000)
        assertTrue(File("src/main/res/drawable-nodpi/ic_launcher_mark_mono.png").length() > 500)
        for (d in listOf("mipmap-night-anydpi-v26", "mipmap-night-anydpi")) {
            assertTrue("$d would replace the artwork in dark mode", !File("src/main/res/$d").exists())
        }
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:icon=\"@mipmap/ic_launcher\""))
    }
}
