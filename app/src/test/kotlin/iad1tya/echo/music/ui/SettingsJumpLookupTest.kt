package iad1tya.echo.music.ui

import iad1tya.echo.music.ui.utils.SettingsJumpLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P22D (row 735): a settings-search result must land on the exact row. This pins the key -> row rule that
 * `SettingsSearchJump` applies to the destination's semantics tree, on a plain model of that tree.
 */
class SettingsJumpLookupTest {

    private class N(val name: String, val texts: List<String> = emptyList(), val row: Boolean = false, vararg kids: N) {
        val children: List<N> = kids.toList()
        override fun toString() = name
    }

    private fun find(root: N, key: String) =
        SettingsJumpLookup.find(root, key, children = { it.children }, texts = { it.texts }, isRow = { it.row })

    @Test
    fun `the row wins over the same text in the top bar`() {
        val rowNode = N("row", listOf("Letra"), row = true)
        val screen = N(
            "root", emptyList(), false,
            N("topBar", listOf("Letra")),
            N("scroller", emptyList(), false, N("other", listOf("Otros"), row = true), rowNode),
        )
        val match = find(screen, "Letra")!!
        assertSame(rowNode, match.node)
        assertSame(rowNode, match.row)
    }

    @Test
    fun `a description entry lands on the row it describes`() {
        // Merged tree: the clickable row carries both its title and its description.
        val crossfade = N("crossfade", listOf("Fundido", "Mezcla el final de una canción con la siguiente"), row = true)
        val screen = N("root", emptyList(), false, N("scroller", emptyList(), false, crossfade))
        assertSame(crossfade, find(screen, "Mezcla el final de una canción con la siguiente")!!.row)
    }

    @Test
    fun `text inside an unmerged row resolves to that row`() {
        val title = N("title", listOf("Volumen seguro"))
        val row = N("row", emptyList(), true, title)
        val screen = N("root", emptyList(), false, row)
        val match = find(screen, "Volumen seguro")!!
        assertSame(title, match.node)
        assertSame(row, match.row)
    }

    @Test
    fun `the first row in screen order wins`() {
        val first = N("first", listOf("General"), row = true)
        val second = N("second", listOf("General"), row = true)
        val screen = N("root", emptyList(), false, N("a", emptyList(), false, first), second)
        assertSame(first, find(screen, "General")!!.row)
    }

    @Test
    fun `a screen without settings rows still gets a target, with nothing to highlight`() {
        val card = N("card", listOf("Oscuro"))
        val match = find(N("root", emptyList(), false, card), "Oscuro")!!
        assertSame(card, match.node)
        assertNull(match.row)
    }

    @Test
    fun `a title that is not on screen finds nothing, so the screen opens at the top`() {
        val screen = N("root", emptyList(), false, N("row", listOf("Letra"), row = true))
        assertNull(find(screen, "¿Borrar todas las descargas?"))
        assertNull(find(screen, "   "))
    }

    @Test
    fun `matching is exact after trimming, not a substring`() {
        assertTrue(SettingsJumpLookup.matches(listOf("  Letra \n"), "Letra"))
        assertTrue(SettingsJumpLookup.matches(listOf("Prioridad de  proveedores"), "Prioridad de proveedores"))
        assertFalse(SettingsJumpLookup.matches(listOf("Letras: fuentes"), "Letra"))
        assertFalse(SettingsJumpLookup.matches(emptyList(), "Letra"))
    }

    @Test
    fun `the scroll delta centres the row in the viewport`() {
        // Viewport 100..900 (centre 500); a 100 px row at 1200 has its centre at 1250 -> scroll down 750.
        assertEquals(750f, SettingsJumpLookup.scrollDelta(1200f, 100f, 100f, 900f), 0.001f)
        // A row above the centre scrolls back up (negative).
        assertEquals(-300f, SettingsJumpLookup.scrollDelta(150f, 100f, 100f, 900f), 0.001f)
    }
}
