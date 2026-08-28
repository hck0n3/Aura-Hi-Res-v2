package iad1tya.echo.music.artistvideo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cache audit H3 (HALLAZGO-037): the artist video autoplays in a muted loop on every artist
 * screen visit — over mobile data that used to be a full video download with no user decision.
 * The gate pins the new rule: artist video only starts on an unmetered network; on mobile data
 * the screen falls back to the static artist image.
 */
class ArtistVideoNetworkGateTest {

    @Test
    fun artistVideoStartsOnUnmeteredNetwork() {
        assertTrue(artistVideoNetworkAllowed(isActiveNetworkMetered = false))
    }

    @Test
    fun artistVideoStaysOffOnMeteredNetwork() {
        assertFalse(artistVideoNetworkAllowed(isActiveNetworkMetered = true))
    }
}
