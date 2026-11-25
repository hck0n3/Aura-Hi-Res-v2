package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 877 (plan, phase 1 item 4, second half): Inicio, Búsqueda, Estadísticas and Novedades on wide windows.
 * Source-text guards (no Android SDK in the unit job): the wiring a refactor could silently undo.
 */
class HomeSearchWideGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the wide carousels of Inicio and Novedades decide on the same window and height line as the wide shell`() {
        // Row 869 left them on Configuration with a hand-written 480: the shell decides on the container and isCompactHeight.
        val home = main("ui/newui/AuraHomeScreen.kt")
            .substringAfter("private fun AuraQuickPicksCarousel(").substringBefore("private fun AuraHeroCarouselCard(")
        val novedades = main("ui/newui/AuraNovedadesScreen.kt")
        for ((name, src) in listOf("Inicio" to home, "Novedades" to novedades)) {
            assertTrue("$name lee la capa", src.contains("val window = auraWindowInfo()"))
            assertTrue("$name: el alto del contenedor", src.contains("val screenHeightDp = window.heightDp.toInt()"))
            assertTrue("$name: la línea de altura compacta de la capa", src.contains("AURA_WIDE_HERO_MIN_WIDTH && !window.isCompactHeight"))
            assertFalse("$name: sin 480 escrito a mano", src.contains("screenHeightDp >= 480"))
            assertFalse("$name: sin el alto de Configuration", src.contains("configHeightDp"))
        }
    }
}
