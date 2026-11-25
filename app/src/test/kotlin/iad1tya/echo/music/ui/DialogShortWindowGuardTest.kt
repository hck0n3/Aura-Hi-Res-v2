package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 870 (plan, phase 1.3): a dialog whose body has a fixed height pushed its own buttons out of a short
 * window (the Fold cover screen in landscape, large font). [DefaultDialog] measures the buttons before a body that is
 * `weight(1f, fill = false)`, so the body keeps its height when it fits and shrinks (it scrolls) when it does not.
 */
class DialogShortWindowGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun squash(s: String) = s.replace(Regex("\\s+"), "")

    @Test
    fun `DefaultDialog keeps its body inside a Column so a weighted body yields to the buttons`() {
        val dialog = main("ui/component/Dialog.kt")
        assertTrue(dialog.contains("content: @Composable ColumnScope.() -> Unit,"))
        val body = dialog.substringAfter("private fun DialogBody(").substringBefore("fun ActionPromptDialog(")
        assertTrue("el cuerpo va en una Column", body.contains("Column(") && body.contains("content()"))
        assertTrue("los botones siguen detrás del contenido", body.indexOf("content()") < body.indexOf("buttons()"))
    }

    @Test
    fun `the playback log box yields to the buttons on a short window`() {
        val src = squash(main("ui/component/PlaybackLogsDialog.kt"))
        assertTrue(src.contains("Box(modifier=Modifier.weight(1f,fill=false).fillMaxWidth().height(400.dp))"))
    }

    @Test
    fun `the podcast region list yields to the close button on a short window`() {
        val src = main("ui/screens/PodcastScreen.kt")
        assertTrue(src.contains("LazyColumn(modifier = Modifier.weight(1f, fill = false).height(360.dp)) {"))
    }

    @Test
    fun `the blocked users list yields to the OK button on a short window`() {
        val src = squash(main("ui/screens/settings/integrations/ListenTogetherSettings.kt"))
        assertTrue(src.contains("LazyColumn(state=listState,modifier=Modifier.weight(1f,fill=false).fillMaxWidth().height(280.dp))"))
    }

    @Test
    fun `the colour picker body scrolls instead of pushing OK and Cancel off a short window`() {
        val src = main("ui/component/ColorPicker.kt")
        val dialog = squash(src.substringAfter("fun ColorPickerDialog(").substringBefore("private fun PresetSwatch("))
        assertTrue(dialog.contains("Column(modifier=Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),){SaturationValuePanel("))
        assertTrue(src.contains("import androidx.compose.foundation.verticalScroll"))
        assertTrue(src.contains("import androidx.compose.foundation.rememberScrollState"))
    }
}
