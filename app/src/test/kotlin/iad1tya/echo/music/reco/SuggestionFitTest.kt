package iad1tya.echo.music.reco

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 526 — the playlist footer's «Canciones sugeridas» read what the playlist is (style, language, faith). */
class SuggestionFitTest {
    private fun e(title: String, artist: String, album: String? = null) = SuggestionFit.Entry(title, listOf(artist), album)

    private val salsa = listOf(
        e("Salsa para mi Dios", "A"), e("Salsa de la fe", "B"), e("Mi salsa cristiana", "C"),
        e("Salsa brava de alabanza", "D"), e("Salsa de gloria", "E"),
    )

    @Test
    fun `a playlist of salsa is read as salsa and a reggaeton song does not fit it`() {
        val profile = SuggestionFit.profileOf(salsa, emptyMap())
        assertTrue(profile.active)
        assertEquals(setOf("salsa"), profile.target.styles)
        assertFalse(SuggestionFit.fits(profile, e("Reggaeton para bailar", "Z")))
        assertTrue(SuggestionFit.fits(profile, e("Otra salsa más", "Y")))
        // nothing known about it: never held against it
        assertTrue(SuggestionFit.fits(profile, e("Un título cualquiera", "X")))
    }

    @Test
    fun `a small or mixed playlist is not judged at all`() {
        assertFalse(SuggestionFit.profileOf(salsa.take(3), emptyMap()).active)
        val mixed = listOf(
            e("Salsa uno", "A"), e("Cumbia uno", "B"), e("Rock uno", "C"), e("Merengue uno", "D"),
            e("Bachata uno", "E"), e("Trap uno", "F"), e("Punk uno", "G"), e("Metal uno", "H"),
        )
        val profile = SuggestionFit.profileOf(mixed, emptyMap())
        // many styles each under the 25 % share: no single style is imposed
        assertTrue(profile.target.styles == null || profile.target.styles!!.size >= 2 || !profile.target.active)
    }

    @Test
    fun `the songs that fit keep their order and go first, the others go last and none is dropped`() {
        val profile = SuggestionFit.profileOf(salsa, emptyMap())
        val entries = mapOf(
            "1" to e("Reggaeton uno", "Z1"), "2" to e("Salsa dos", "Z2"),
            "3" to e("Reggaeton tres", "Z3"), "4" to e("Salsa cuatro", "Z4"),
        )
        val ordered = SuggestionFit.order(profile, listOf("1", "2", "3", "4")) { entries[it] }
        assertEquals(listOf("2", "4", "1", "3"), ordered)
        // an unreadable playlist leaves the order alone
        val none = SuggestionFit.profileOf(emptyList(), emptyMap())
        assertEquals(listOf("1", "2", "3", "4"), SuggestionFit.order(none, listOf("1", "2", "3", "4")) { entries[it] })
    }

    @Test
    fun `the footer applies the fit before its artist-diversity pass and logs counts only`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val s = File(root, "app/src/main/kotlin/com/music/echo/viewmodels/LocalPlaylistViewModel.kt").readText()
        assertTrue(s.indexOf("SuggestionFit.order(") in 1 until s.indexOf("for (id in fittedForPicking)"))
        // Row 586: what plainly does not fit is left out of the picks and of both backfills, not only moved behind.
        assertTrue(s.contains("val fittedForPicking = if (fittingOnly.isNotEmpty()) fittingOnly else fitted"))
        assertTrue(s.contains("fitsPlaylist(it)"))
        assertFalse(Regex("""SUGGEST_FIT[^\n]*(title|artist\b)""").containsMatchIn(s))
    }
}
