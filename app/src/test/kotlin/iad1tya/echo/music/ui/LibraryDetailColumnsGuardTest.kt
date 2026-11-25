package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.newui.auraLibraryCellDp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry rows 875 (Biblioteca in columns) and 876 (album / playlist / artist / collection detail in two columns), plan
 * phase 1 item 4. There is no Android SDK in the unit job, so the wiring is pinned on the source text; the decisions
 * themselves are pure and tested in `ui/newui/AuraLibraryDetailLayoutTest.kt`.
 */
class LibraryDetailColumnsGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── 875 · one width reading and one threshold for the Biblioteca cells and the picker rows ─────────────────────────

    @Test
    fun `the library cells and the picker rows read the layer's container width and its medium line`() {
        val cells = main("ui/newui/AuraLibraryViewType.kt")
        assertFalse("la celda ya no cae a Configuration", cells.contains("configWidthDp"))
        assertTrue(cells.contains("?: auraWindowInfo().widthDp"))
        assertTrue(cells.contains("if (windowWidthDp < AURA_MEDIUM_MIN_WIDTH_DP) return if (big) 150f else 104f"))

        val sizing = main("ui/component/ListItemSizing.kt")
        assertFalse("la fila amplia ya no mide Configuration", sizing.contains("configWidthDp"))
        assertTrue(sizing.contains("const val ROOMY_LIST_WIDE_DP = iad1tya.echo.music.ui.newui.AURA_MEDIUM_MIN_WIDTH_DP"))
        assertTrue(sizing.contains("auraWindowInfo().widthDp >= ROOMY_LIST_WIDE_DP"))
    }

    @Test
    fun `the cell sizes keep their old lines exactly - a phone and a folded foldable are untouched`() {
        assertEquals(150f, auraLibraryCellDp(599.9f, true), 0f)
        assertEquals(104f, auraLibraryCellDp(599.9f, false), 0f)
        assertEquals(176f, auraLibraryCellDp(600f, true), 0f)
        assertEquals(132f, auraLibraryCellDp(600f, false), 0f)
        // The owner's Z Fold7 cover screen, ~360-410 dp.
        for (w in listOf(360f, 384f, 411f)) {
            assertEquals(150f, auraLibraryCellDp(w, true), 0f)
            assertEquals(104f, auraLibraryCellDp(w, false), 0f)
        }
    }
}
