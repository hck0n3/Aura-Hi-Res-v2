package iad1tya.echo.music.ui.newui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cerrar una hoja arrastrando su barrita (dueño, 2026-10-18). Lo que no puede pasar: que un roce cierre la hoja, que
 * un arrastre largo o un gesto rápido no la cierren, o que alguna de las tres hojas de cristal vuelva a dibujar la
 * barrita sin el gesto (el defecto original: se veía y no hacía nada).
 */
class AuraSheetDragTest {

    @Test
    fun `un roce corto y lento no cierra`() {
        assertFalse(AuraSheetDragState.shouldDismiss(offsetPx = 20f, velocityPxPerS = 100f, dismissDistancePx = 250f))
    }

    @Test
    fun `un arrastre largo cierra aunque sea lento`() {
        assertTrue(AuraSheetDragState.shouldDismiss(offsetPx = 400f, velocityPxPerS = 0f, dismissDistancePx = 250f))
    }

    @Test
    fun `un gesto rapido hacia abajo cierra aunque sea corto`() {
        assertTrue(AuraSheetDragState.shouldDismiss(offsetPx = 30f, velocityPxPerS = 4000f, dismissDistancePx = 250f))
    }

    @Test
    fun `subir rapido no cierra`() {
        assertFalse(AuraSheetDragState.shouldDismiss(offsetPx = 0f, velocityPxPerS = -4000f, dismissDistancePx = 250f))
    }

    @Test
    fun `las tres hojas de cristal usan la barrita arrastrable`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        listOf(
            "app/src/main/kotlin/com/music/echo/ui/component/BottomSheetPage.kt",
            "app/src/main/kotlin/com/music/echo/ui/component/BottomSheetMenu.kt",
            "app/src/main/kotlin/com/music/echo/ui/newui/AuraInWindowDialog.kt",
        ).forEach { path ->
            val text = File(root, path).readText()
            assertTrue("$path: la barrita debe ser AuraSheetHandle", text.contains("AuraSheetHandle("))
            assertTrue("$path: la hoja debe seguir al dedo", text.contains("auraSheetDragOffset("))
        }
    }
}
