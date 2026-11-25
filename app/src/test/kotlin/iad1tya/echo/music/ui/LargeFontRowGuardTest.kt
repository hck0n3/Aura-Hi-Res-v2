package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 870 (plan, phase 1.3): the artist page's song pages laid every row in a fixed 66 dp slot, so with large
 * font the title + artist lines (20 sp + 18 sp) outgrew it and the artist line was clipped. The slot now grows with
 * those two lines and is exactly 66 dp at 100 %.
 */
class LargeFontRowGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun spOf(type: String, style: String, field: String): Int =
        Regex("""\b$field = (\d+)\.sp""").find(type.substringAfter("val $style = TextStyle(").substringBefore(")"))
            ?.groupValues?.get(1)?.toInt() ?: error("$style.$field not found")

    @Test
    fun `the song page slot grows with the two text lines and is 66 dp at 100 percent`() {
        val src = main("ui/newui/AuraContent.kt")
        assertTrue(src.contains("private val AuraSongRowSlot = 66.dp"))
        val pad = Regex("""private val AuraSongRowTextPad = (\d+)\.dp""").find(src)?.groupValues?.get(1)?.toInt()
            ?: error("AuraSongRowTextPad not found")
        val type = main("ui/newui/AuraType.kt")
        val lines = spOf(type, "RowTitle", "lineHeight") + spOf(type, "RowSubtitle", "lineHeight")
        assertEquals("at 100 % the slot is the 66 dp it always was", 66, lines + pad)

        val pages = src.substringAfter("fun AuraSongPages(").substringBefore("\n}\n")
        assertTrue(pages.contains("with(LocalDensity.current) {"))
        assertTrue(pages.contains("maxOf(AuraSongRowSlot, AuraType.RowTitle.lineHeight.toDp() + AuraType.RowSubtitle.lineHeight.toDp() + AuraSongRowTextPad)"))
        assertTrue(pages.contains("val pageHeight = rowSlot * AuraSongPageSize"))
        assertTrue(pages.contains(".height(pageHeight + AuraSpacing.SectionGap)"))
        assertTrue(pages.contains(".height(pageHeight),"))
        assertTrue(pages.contains(".height(rowSlot),"))
        assertFalse("ningún alto fijo queda en las páginas", pages.contains(".height(AuraSongRowSlot)") || pages.contains("AuraSongPageHeight"))
    }
}
