package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19 (foldable): *«las animaciones cuando me muevo de arriba abajo… y la hago grande y pequeña se ven muy
 * feas, no son fluidas»*. There is no device and no frame profiler in the unit job, so what can be pinned is the SHAPE of
 * the fixes — where the work happens — as source text. What the eye sees can only be judged on the phone.
 */
class PlayerSheetFluidityGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `a drag delta moves the sheet inside the input event, not one dispatch later`() {
        val sheet = main("ui/component/BottomSheet.kt")
        val draggable = sheet.substringAfter("draggableState = DraggableState { delta ->").substringBefore("onAnchorChanged = {")
        assertTrue(draggable.contains("CoroutineStart.UNDISPATCHED"))
        assertTrue(draggable.contains("animatable.snapTo("))
        val snap = sheet.substringAfter("fun snapTo(value: Dp)").substringBefore("fun performFling")
        assertTrue(snap.contains("CoroutineStart.UNDISPATCHED"))
    }

    @Test
    fun `a tapped expand composes the player before the spring starts and never leaves it composed`() {
        val sheet = main("ui/component/BottomSheet.kt")
        assertTrue("el contenido se compone también con contentWarm", sheet.contains("if (!state.isCollapsed || state.contentWarm)"))
        val expand = sheet.substringAfter("fun expand(animationSpec: AnimationSpec<Dp>)").substringBefore("private fun collapse()")
        assertTrue(expand.contains("contentWarm = true"))
        assertTrue("dos fotogramas de calentamiento antes de animar", expand.split("withFrameNanos").size - 1 == 2)
        assertTrue("el calentamiento va ANTES del muelle", expand.indexOf("withFrameNanos") < expand.indexOf("animatable.animateTo("))
        assertTrue("la bandera se apaga siempre, también al cancelar", expand.contains("finally") && expand.contains("contentWarm = false"))
        assertTrue(sheet.substringAfter("fun dismiss()").substringBefore("suspend fun dismissAndWait").contains("contentWarm = false"))
    }

    @Test
    fun `the sheet springs end when the eye cannot tell, not at a hundredth of a dp`() {
        val sheet = main("ui/component/BottomSheet.kt")
        assertTrue(sheet.contains("private val SHEET_VISIBILITY_THRESHOLD = 0.1.dp"))
        val soft = sheet.substringAfter("fun collapseSoft()").substringBefore("fun dismiss()")
        assertTrue(soft.split("visibilityThreshold = SHEET_VISIBILITY_THRESHOLD").size - 1 == 2)
        assertFalse("ya no hay SpringSpec() sin umbral", sheet.contains("SpringSpec()"))
    }

    @Test
    fun `the split frame fades by painting with alpha, not through a window-sized offscreen layer`() {
        val frame = main("ui/newui/AuraWideChrome.kt").substringAfter("fun AuraSplitCardsFrame(")
        assertFalse(frame.contains("graphicsLayer {"))
        assertTrue(frame.contains("drawPath(frame, ground, alpha = a)"))
        assertTrue("el progreso se lee al dibujar", frame.substringAfter("onDrawBehind {").contains("progress()"))
    }

    @Test
    fun `in the split pane the player ground is clipped to the card so it cannot tint the strip above it`() {
        // Owner 2026-10-20: a glint at the top while the mini player expands / minimizes on the unfolded Fold. The ground
        // fills the whole pane and fades in faster than AuraSplitCardsFrame covers the status-bar strip.
        val player = main("ui/newui/AuraPlayer.kt")
        val background = player.substringAfter("    BottomSheet(\n        state = state,").substringBefore("intensity = rhythmIntensity")
        assertTrue(background.contains("AuraSplitPaneCardShape("))
        assertTrue("solo en el panel dividido", background.contains("widePlayerMode.split"))
        val chrome = main("ui/newui/AuraWideChrome.kt")
        assertTrue("la forma sale de las mismas tarjetas que el marco", chrome.substringAfter("class AuraSplitPaneCardShape").contains("auraSplitCardRects("))
        assertTrue(main("MainActivity.kt").contains("cardTopInset = splitTopInset"))
    }
}
