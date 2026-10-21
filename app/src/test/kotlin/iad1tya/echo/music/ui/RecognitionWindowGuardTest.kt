package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «quiero que el reconocedor de música tipo shazam sea también como ventana flotante». Recognition is no
 * longer a full-screen route: every door opens ONE floating window composed by the activity, and closing that window must
 * stop the microphone. There is no Android SDK in the unit job, so the wiring is pinned as source text.
 */
class RecognitionWindowGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `no door navigates to the recognition route any more, they all open the window`() {
        val activity = main("MainActivity.kt")
        assertFalse("la ruta ya solo redirige", activity.contains("navController.navigate(\"recognition"))
        // toolbar mic + header button (one lambda), rail long-press, and the notification / tile / widget intent
        assertEquals(3, Regex("""RecognitionWindowState\.open\(""").findAll(activity).count())
        assertEquals(1, Regex("""RecognitionWindowHost\(navController\)""").findAll(activity).count())
    }

    @Test
    fun `the old route is a thin redirect that draws nothing`() {
        val s = main("ui/screens/recognition/RecognitionScreen.kt")
        val redirect = s.substringAfter("fun RecognitionScreen(").substringBefore("fun RecognitionWindowHost(")
        assertTrue(redirect.contains("RecognitionWindowState.open(autoStart)"))
        assertTrue(redirect.contains("navController.popBackStack()"))
        assertFalse(redirect.contains("Scaffold"))
        // the route stays registered with the same argument, so deep links / saved back stacks do not break
        assertTrue(main("ui/screens/NavigationBuilder.kt").contains("\"recognition?autoStart={autoStart}\""))
    }

    @Test
    fun `the window uses the same glass host as the other bottom sheets and a classic sheet as fallback`() {
        val s = main("ui/screens/recognition/RecognitionScreen.kt")
        assertTrue(s.contains("iad1tya.echo.music.ui.newui.AuraInWindowDialog("))
        assertTrue(s.contains("center = false,\n            fullHeight = false,"))
        assertTrue(s.contains("iad1tya.echo.music.ui.component.AuraModalBottomSheet("))
        assertFalse("la pantalla completa ya no existe", s.contains("Scaffold("))
    }

    @Test
    fun `closing the window stops the microphone, except when the activity itself is going away`() {
        val s = main("ui/screens/recognition/RecognitionScreen.kt")
        val teardown = s.substringAfter("DisposableEffect(Unit) {").substringBefore("val recognitionStatus by")
        assertTrue(teardown.contains("activity.isFinishing || activity.isChangingConfigurations"))
        // live / not-yet-started session: abort the capture AND the foreground service
        assertTrue(teardown.contains("else -> iad1tya.echo.music.recognition.MusicRecognitionService.cancel(context)"))
        // a finished session keeps its result notification: only the UI state is reset
        assertTrue(teardown.contains("is RecognitionStatus.Success,"))
        assertTrue(teardown.contains("MusicRecognitionService.reset()"))
        // a permission result that arrives after the window was closed must not start the mic
        assertTrue(s.contains("if (isGranted && RecognitionWindowState.request != null)"))
    }
}
