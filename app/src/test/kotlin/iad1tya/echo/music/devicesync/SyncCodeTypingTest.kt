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

/** Row 615: the pairing QR carries a link that round-trips to the same code and rejects anything else. */
class SyncCodePairLinkTest {
    @Test
    fun theLinkRoundTripsToTheSameNormalizedCode() {
        val code = SyncCode.normalize(SyncCode.generate())!!
        val link = SyncCode.pairLink(code)
        assertEquals(true, link.startsWith("echomusic://pair?code="))
        assertEquals(code, SyncCode.fromPairLink(link))
    }

    @Test
    fun otherLinksAndShortCodesAreRejected() {
        assertEquals(null, SyncCode.fromPairLink("echomusic://listen?code=ABCDEF"))
        assertEquals(null, SyncCode.fromPairLink("echomusic://pair?code=ABCD-EFGH"))
        assertEquals(null, SyncCode.fromPairLink("https://example.com/pair?code=ABCD-EFGH-JKMN-PQRS-TUVW"))
    }

    @Test
    fun theWiringIsInPlace() {
        val main = java.io.File("src/main/kotlin/com/music/echo/MainActivity.kt").readText()
        org.junit.Assert.assertTrue(main.contains("SyncCode.fromPairLink(uri.toString())"))
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        org.junit.Assert.assertTrue(manifest.contains("android:host=\"pair\""))
        val room = java.io.File("src/main/kotlin/com/music/echo/ui/screens/ListenTogetherScreen.kt").readText()
        org.junit.Assert.assertTrue("room QR missing", room.contains("QrImage(text = inviteLink"))
    }
}
