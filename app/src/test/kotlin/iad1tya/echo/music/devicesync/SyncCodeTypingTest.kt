package iad1tya.echo.music.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Row 609: the pairing code field puts the dashes in by itself. */
class SyncCodeTypingTest {
    @Test
    fun dashesAppearEveryFourCharacters() {
        assertEquals("ABCD", SyncCode.typing("abcd"))
        assertEquals("ABCD-E", SyncCode.typing("abcde"))
        assertEquals("ABCD-EFGH-JKMN", SyncCode.typing("abcdefghjkmn"))
    }

    @Test
    fun aPastedDashedCodeGivesTheSameText() {
        val code = SyncCode.generate()
        assertEquals(code, SyncCode.typing(code))
        assertEquals(code, SyncCode.typing(code.lowercase().replace("-", " ")))
    }

    @Test
    fun outsiderCharactersAndExtraLengthAreDropped() {
        assertEquals("ABCD-EFGH", SyncCode.typing("ab!cd0efgh1"))
        val full = SyncCode.typing("A".repeat(40))
        assertEquals(24, full.length)
        assertNotNull(SyncCode.normalize(full))
    }
}
