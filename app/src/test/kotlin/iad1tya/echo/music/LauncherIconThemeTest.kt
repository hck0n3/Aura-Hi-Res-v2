package iad1tya.echo.music

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 620: the launcher icon has a light (default) and a dark (-night) adaptive version, each with its own layers and a themed layer. */
class LauncherIconThemeTest {
    private fun xml(path: String) = File("src/main/res/$path").readText()

    @Test
    fun lightIsTheDefaultAndDarkIsTheNightVariant() {
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val light = xml("mipmap-anydpi-v26/$name.xml")
            val dark = xml("mipmap-night-anydpi-v26/$name.xml")
            assertTrue(light.contains("@drawable/ic_launcher_bg_light") && light.contains("@drawable/ic_launcher_mark_light"))
            assertTrue(dark.contains("@drawable/ic_launcher_bg_dark") && dark.contains("@drawable/ic_launcher_mark_dark"))
            assertTrue(light.contains("<monochrome") && dark.contains("<monochrome"))
        }
    }

    @Test
    fun theStaticAliasFollowsTheSameThemes() {
        for (name in listOf("ic_launcher_static", "ic_launcher_static_round")) {
            assertTrue(xml("mipmap-anydpi/$name.xml").contains("ic_launcher_mark_light"))
            assertTrue(xml("mipmap-night-anydpi/$name.xml").contains("ic_launcher_mark_dark"))
        }
    }

    @Test
    fun everyLayerTheIconNamesExists() {
        for (f in listOf("drawable-nodpi/ic_launcher_mark_light.png", "drawable-nodpi/ic_launcher_mark_dark.png", "drawable-nodpi/ic_launcher_mark_mono.png",
            "drawable-nodpi/ic_launcher_bg_light.png", "drawable-nodpi/ic_launcher_bg_dark.png")) {
            assertTrue("$f missing", File("src/main/res/$f").length() > 0)
        }
        assertEquals(true, File("src/main/AndroidManifest.xml").readText().contains("android:icon=\"@mipmap/ic_launcher\""))
    }
}
