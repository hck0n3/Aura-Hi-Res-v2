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

    @Test
    fun `explanatory text is capped at a readable width that no phone row reaches`() {
        val readable = main("ui/newui/AuraHomeSearchReadable.kt")
        assertTrue(readable.contains("internal val AURA_READABLE_MAX_WIDTH = 560.dp"))
        assertTrue(readable.contains(".fillMaxWidth()\n        .wrapContentWidth(Alignment.CenterHorizontally)\n        .widthIn(max = AURA_READABLE_MAX_WIDTH)"))
        val home = main("ui/newui/AuraHomeScreen.kt")
        for (hint in listOf("R.string.home_cold_empty_hint", "R.string.home_offline_downloads_hint")) {
            val text = home.substringAfter("text = stringResource($hint),").substringBefore(")")
            assertTrue("$hint no cruza una pantalla ancha de lado a lado", text.contains("modifier = Modifier.auraReadableWidth("))
        }
        val novedades = main("ui/newui/AuraNovedadesScreen.kt").substringAfter("item(key = \"aura_novedades_empty\"").substringBefore("AuraChip(")
        assertTrue(
            "el vacío de Novedades (texto, pista y Reintentar) en una columna de ancho legible",
            novedades.contains("Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.auraReadableWidth()) {"),
        )
    }
}
