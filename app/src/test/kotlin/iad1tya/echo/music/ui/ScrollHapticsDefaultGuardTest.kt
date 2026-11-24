package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 806 (owner decision H2): the ~100 ms vibration while dragging is OFF by default behind its own switch;
 * the tap haptic of row 37 and its stored key stay as they were.
 */
class ScrollHapticsDefaultGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the scroll tick has its own key, off by default, and the tap key is never renamed`() {
        val keys = main("constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val ScrollHapticsKey = booleanPreferencesKey(\"scrollHaptics\")"))
        // Row 37: a rename of the tap key would reset everyone's choice.
        assertTrue(keys.contains("val GlobalHapticsKey = booleanPreferencesKey(\"globalHaptics\")"))
        val settings = main("ui/screens/settings/AppearanceSettings.kt")
        assertTrue(settings.contains("iad1tya.echo.music.constants.ScrollHapticsKey,\n        defaultValue = false"))
        assertTrue(settings.contains("iad1tya.echo.music.constants.GlobalHapticsKey,\n        defaultValue = true"))
    }

    @Test
    fun `the root observer only ticks on drags when the scroll switch is on, taps unchanged`() {
        val shell = main("MainActivity.kt")
        assertTrue(shell.contains("val (globalHaptics) = rememberPreference(GlobalHapticsKey, defaultValue = true)"))
        assertTrue(shell.contains("val (scrollHaptics) = rememberPreference(iad1tya.echo.music.constants.ScrollHapticsKey, defaultValue = false)"))
        assertTrue(shell.contains(".pointerInput(globalHaptics, scrollHaptics) {"))
        assertFalse(shell.contains(".pointerInput(globalHaptics) {"))
        val observer = shell.substringAfter(".pointerInput(globalHaptics, scrollHaptics) {").substringBefore("val focusManager")
        assertTrue(observer.contains("if (!globalHaptics) return@pointerInput"))
        assertTrue(observer.contains("rootView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)"))
        assertTrue(observer.contains("} else if (scrollHaptics && changes.fastAny { it.pressed && it.positionChange().getDistance() > slop }) {"))
        assertTrue(observer.contains("if (now - lastScrollHaptic > SCROLL_HAPTIC_THROTTLE_MS) {"))
        assertTrue(shell.contains("private const val SCROLL_HAPTIC_THROTTLE_MS = 100L"))
    }

    @Test
    fun `the switch is searchable and worded in English and Spanish`() {
        val index = main("ui/screens/settings/SearchableSettings.kt")
        assertTrue(index.contains("Triple(stringResource(R.string.scroll_haptics), \"Apariencia\", \"settings/appearance\"),"))
        assertTrue(index.contains("Triple(stringResource(R.string.scroll_haptics_desc), \"Apariencia\", \"settings/appearance\"),"))
        assertTrue(res("values/scroll_haptics_strings.xml").contains("<string name=\"scroll_haptics\">Vibrate while scrolling</string>"))
        assertTrue(res("values-es/scroll_haptics_strings.xml").contains("<string name=\"scroll_haptics\">Vibrar al desplazarte</string>"))
        // The tap switch no longer promises the scroll vibration.
        assertTrue(res("values/echo_strings.xml").contains("<string name=\"global_haptics_desc\">Vibrate when tapping anywhere in the app</string>"))
        assertTrue(res("values-es/echo_strings.xml").contains("<string name=\"global_haptics_desc\">Vibra al tocar en toda la app</string>"))
        assertTrue(res("values-es-rUS/echo_strings.xml").contains("<string name=\"global_haptics_desc\">Vibra al tocar en toda la app</string>"))
    }
}
