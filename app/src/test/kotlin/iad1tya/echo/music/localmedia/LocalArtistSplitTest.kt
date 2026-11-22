package iad1tya.echo.music.localmedia

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Local files: the artist tag used to be split on , ; / & — "AC/DC" and "Simon & Garfunkel" became
 * two artists each, while "feat."/"ft."/" x " were never recognised. [splitArtistNames] is now
 * conservative: names that contain "/" or "&" stay whole.
 */
class LocalArtistSplitTest {

    @Test
    fun `slash and ampersand stay inside a name`() {
        assertEquals(listOf("AC/DC"), splitArtistNames("AC/DC"))
        assertEquals(listOf("Simon & Garfunkel"), splitArtistNames("Simon & Garfunkel"))
        assertEquals(listOf("Lil Nas X"), splitArtistNames("Lil Nas X"))
    }

    @Test
    fun `semicolon separates artists`() {
        assertEquals(listOf("Bad Bunny", "Jhay Cortez"), splitArtistNames("Bad Bunny; Jhay Cortez"))
        assertEquals(listOf("AC/DC", "Simon & Garfunkel"), splitArtistNames("AC/DC;Simon & Garfunkel"))
    }

    @Test
    fun `featuring markers separate artists`() {
        assertEquals(listOf("Calvin Harris", "Rihanna"), splitArtistNames("Calvin Harris feat. Rihanna"))
        assertEquals(listOf("Calvin Harris", "Rihanna"), splitArtistNames("Calvin Harris Feat Rihanna"))
        assertEquals(listOf("Eminem", "Dido"), splitArtistNames("Eminem ft. Dido"))
        assertEquals(listOf("Ed Sheeran", "Khalid"), splitArtistNames("Ed Sheeran featuring Khalid"))
        assertEquals(listOf("Drake", "Rihanna"), splitArtistNames("Drake (feat. Rihanna)"))
        assertEquals(listOf("Daft Punk", "Pharrell Williams"), splitArtistNames("Daft Punk [ft. Pharrell Williams]"))
        assertEquals(listOf("Sigala", "Ella Eyre"), splitArtistNames("Sigala x Ella Eyre"))
    }

    @Test
    fun `comma separates a plain list but not band names`() {
        assertEquals(listOf("Bad Bunny", "Jhay Cortez"), splitArtistNames("Bad Bunny, Jhay Cortez"))
        assertEquals(listOf("Tyler, The Creator"), splitArtistNames("Tyler, The Creator"))
        assertEquals(listOf("Earth, Wind & Fire"), splitArtistNames("Earth, Wind & Fire"))
        assertEquals(listOf("Peter, Paul and Mary"), splitArtistNames("Peter, Paul and Mary"))
        assertEquals(listOf("10,000 Maniacs"), splitArtistNames("10,000 Maniacs"))
    }

    @Test
    fun `markers combine`() {
        assertEquals(
            listOf("Bad Bunny", "Jhay Cortez", "Simon & Garfunkel"),
            splitArtistNames("Bad Bunny, Jhay Cortez feat. Simon & Garfunkel"),
        )
    }
}
