package iad1tya.echo.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Biblioteca ▸ «Fijados» (owner 2026-10-20). The rules of the pin list: what is stored, what survives garbage, what a toggle,
 * a cap, a reorder and a sort do. The store itself is one JSON string in the DataStore `settings` (no Room table, no migration).
 */
class LibraryPinsTest {

    private fun pin(
        kind: LibraryPinKind = LibraryPinKind.PLAYLIST,
        id: String,
        title: String = id,
        at: Long = 0L,
    ) = LibraryPin(kind, id, title, pinnedAt = at)

    @Test
    fun `a pin list round-trips with its order and every field`() {
        val pins = listOf(
            LibraryPin(LibraryPinKind.ALBUM, "MPREb_1", "Álbum \"uno\"", subtitle = "A, B", thumbnailUrl = "https://x/y.jpg", extra = "OLAK5uy_1", pinnedAt = 42L),
            LibraryPin(LibraryPinKind.PLAYLIST, "uuid-2", "Mi lista", local = true, pinnedAt = 7L),
            LibraryPin(LibraryPinKind.ARTIST, "UC3", "Artista"),
        )
        assertEquals(pins, LibraryPins.parse(LibraryPins.encode(pins)))
    }

    @Test
    fun `garbage in the preference never throws and never invents a pin`() {
        assertTrue(LibraryPins.parse(null).isEmpty())
        assertTrue(LibraryPins.parse("").isEmpty())
        assertTrue(LibraryPins.parse("not json").isEmpty())
        assertTrue(LibraryPins.parse("{\"k\":\"P\"}").isEmpty())
        // The good element survives its bad neighbours: unknown kind, blank id, a number where an object should be.
        val parsed = LibraryPins.parse(
            "[1,{\"k\":\"Z\",\"i\":\"a\"},{\"k\":\"P\",\"i\":\"\"},{\"k\":\"P\",\"i\":\"ok\",\"t\":\"Bien\"}]",
        )
        assertEquals(listOf("P:ok"), parsed.map { it.key })
    }

    @Test
    fun `a JSON null is no text - the word null never reaches a subtitle`() {
        val parsed = LibraryPins.parse("[{\"k\":\"A\",\"i\":\"a\",\"t\":\"T\",\"s\":null,\"u\":null}]")
        assertNull(parsed.single().subtitle)
        assertNull(parsed.single().thumbnailUrl)
    }

    @Test
    fun `the same item is never listed twice and the list is capped`() {
        val dup = LibraryPins.parse(
            "[{\"k\":\"P\",\"i\":\"a\",\"t\":\"1\"},{\"k\":\"P\",\"i\":\"a\",\"t\":\"2\"},{\"k\":\"A\",\"i\":\"a\",\"t\":\"3\"}]",
        )
        // Same id, different kind = different item.
        assertEquals(listOf("P:a", "A:a"), dup.map { it.key })
        val many = (1..LibraryPins.MAX_PINS + 10).map { pin(id = "p$it") }
        assertEquals(LibraryPins.MAX_PINS, LibraryPins.parse(LibraryPins.encode(many)).size)
    }

    @Test
    fun `pinning appends, refreshing keeps position and date, and the cap refuses a new pin`() {
        var pins = emptyList<LibraryPin>()
        pins = LibraryPins.withPin(pins, pin(id = "a", title = "A"), now = 100L)
        pins = LibraryPins.withPin(pins, pin(id = "b", title = "B"), now = 200L)
        assertEquals(listOf("P:a", "P:b"), pins.map { it.key })
        assertEquals(listOf(100L, 200L), pins.map { it.pinnedAt })

        // Pinned again with a new title: the snapshot is refreshed IN PLACE.
        val refreshed = LibraryPins.withPin(pins, pin(id = "a", title = "A renamed"), now = 999L)
        assertEquals(listOf("P:a", "P:b"), refreshed.map { it.key })
        assertEquals("A renamed", refreshed.first().title)
        assertEquals(100L, refreshed.first().pinnedAt)

        val full = (1..LibraryPins.MAX_PINS).map { pin(id = "p$it", at = 1L) }
        assertFalse(LibraryPins.canAdd(full))
        assertEquals(full, LibraryPins.withPin(full, pin(id = "extra")))
        // A pin that is already there can still be refreshed when the list is full.
        assertEquals("new", LibraryPins.withPin(full, pin(id = "p1", title = "new")).first().title)
    }

    @Test
    fun `toggle pins what is not pinned and unpins what is`() {
        val a = pin(id = "a")
        val once = LibraryPins.toggled(emptyList(), a, now = 5L)
        assertTrue(LibraryPins.isPinned(once, LibraryPinKind.PLAYLIST, "a"))
        assertFalse(LibraryPins.isPinned(once, LibraryPinKind.ALBUM, "a"))
        assertTrue(LibraryPins.toggled(once, a).isEmpty())
    }

    @Test
    fun `reordering can never lose a pin`() {
        val pins = listOf(pin(id = "a"), pin(id = "b"), pin(id = "c"), pin(id = "d"))
        assertEquals(
            listOf("P:c", "P:a", "P:b", "P:d"),
            LibraryPins.reordered(pins, listOf("P:c", "P:a")).map { it.key },
        )
        // Unknown keys are ignored; a key listed twice counts once; a pin added while dragging (not listed) stays, after.
        assertEquals(
            listOf("P:d", "P:b", "P:a", "P:c"),
            LibraryPins.reordered(pins, listOf("P:d", "ghost", "P:b", "P:d")).map { it.key },
        )
        assertEquals(pins.map { it.key }.toSet(), LibraryPins.reordered(pins, emptyList()).map { it.key }.toSet())
    }

    @Test
    fun `without removes exactly one pin`() {
        val pins = listOf(pin(id = "a"), pin(LibraryPinKind.ALBUM, "a"), pin(id = "b"))
        assertEquals(listOf("A:a", "P:b"), LibraryPins.without(pins, "P:a").map { it.key })
    }

    private fun sorted(pins: List<LibraryPin>, sort: LibraryPinSort, descending: Boolean = false) =
        LibraryPins.sorted(pins, sort, descending, pinnedAt = { it.pinnedAt }, name = { it.title }, updatedAt = { it.pinnedAt * 10 })

    @Test
    fun `my order is the stored order whatever the direction`() {
        val pins = listOf(pin(id = "z", at = 3), pin(id = "a", at = 1), pin(id = "m", at = 2))
        assertEquals(pins, sorted(pins, LibraryPinSort.MANUAL))
        assertEquals(pins, sorted(pins, LibraryPinSort.MANUAL, descending = true))
    }

    @Test
    fun `date and name orders are stable and reversible, names ignore case and accents`() {
        val pins = listOf(
            pin(id = "1", title = "beta", at = 30),
            pin(id = "2", title = "Ángel", at = 10),
            pin(id = "3", title = "alfa", at = 20),
        )
        assertEquals(listOf("2", "3", "1"), sorted(pins, LibraryPinSort.CREATE_DATE).map { it.id })
        assertEquals(listOf("1", "3", "2"), sorted(pins, LibraryPinSort.CREATE_DATE, descending = true).map { it.id })
        assertEquals(listOf("3", "2", "1"), sorted(pins, LibraryPinSort.NAME).map { it.id })
        assertEquals(listOf("1", "2", "3"), sorted(pins, LibraryPinSort.NAME, descending = true).map { it.id })
        assertEquals(listOf("2", "3", "1"), sorted(pins, LibraryPinSort.LAST_UPDATED).map { it.id })
    }
}
