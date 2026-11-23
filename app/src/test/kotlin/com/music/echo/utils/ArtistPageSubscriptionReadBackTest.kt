package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-20: «los cantantes a los que ya estoy suscrito, en algunos al meterme me aparece que no
 * estoy suscrito; no es con todos» (row 487): the alias ids and the single answer both screens read.
 *
 * Owner 2026-11-23 (row 700, «el peor error»): the artist page also asked the account's subscribe button
 * and WROTE a follow on `subscribed = true`; after «Pedir música» it showed «Suscrito» for artists he never
 * followed. That write-back is removed and must stay removed: opening a page never follows anyone.
 */
class ArtistPageSubscriptionReadBackTest {

    @Test
    fun `a channel id and its library spelling name the same artist`() {
        assertEquals(listOf("UCabc", "MPLAUCabc"), ArtistSyncPolicy.aliasIds("UCabc"))
        assertEquals(listOf("MPLAUCabc", "UCabc"), ArtistSyncPolicy.aliasIds("MPLAUCabc"))
    }

    @Test
    fun `ids that are not channels have no alias`() {
        assertEquals(listOf("LAabcdefgh"), ArtistSyncPolicy.aliasIds("LAabcdefgh"))
        assertEquals(listOf("FEmusic_library_privately_owned_artist_x"), ArtistSyncPolicy.aliasIds("FEmusic_library_privately_owned_artist_x"))
        assertEquals(listOf("MPLAxyz"), ArtistSyncPolicy.aliasIds("MPLAxyz"))
    }

    // ---- wiring (no Android SDK in the unit job, so pinned as source text) -----------------------

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `both artist screens show and toggle the ViewModel's single answer`() {
        for (screen in listOf("ui/newui/AuraArtistScreen.kt", "ui/screens/artist/ArtistScreen.kt")) {
            val s = main(screen)
            assertTrue("$screen lee isSubscribed", s.contains("viewModel.isSubscribed"))
            assertTrue("$screen alterna por el ViewModel", s.contains("viewModel.toggleSubscription()"))
            assertFalse(
                "$screen volvió a leer solo la fila del id abierto",
                s.contains("libraryArtist?.artist?.followedByUserAt"),
            )
        }
    }

    @Test
    fun `opening an artist page never writes a follow`() {
        val vm = main("viewmodels/ArtistViewModel.kt")
        for (gone in listOf("reconcileRemoteSubscription(", "artistSubscribed(", "healFromRemoteSubscription(", "SUBSCRIPTION_HEALED\"")) {
            assertFalse("row 700: the artist page writes a follow again ($gone)", vm.contains(gone))
        }
        assertFalse(
            "row 700: the ViewModel writes the follow column itself",
            Regex("(?<![A-Za-z])followedByUserAt\\s*=(?!=)").containsMatchIn(vm),
        )
        assertFalse(
            "row 700: a page read can turn into a follow again",
            main("utils/ArtistSyncPolicy.kt").contains("fun healFromRemoteSubscription("),
        )
        val yt = File(root, "innertube/src/main/kotlin/com/music/innertube/YouTube.kt").readText()
        assertFalse("row 700: the subscribe-button read is back", yt.contains("fun artistSubscribed("))
    }

    @Test
    fun `the page's follow button toggles once per tap and only against what it showed`() {
        val tap = main("viewmodels/ArtistViewModel.kt")
            .substringAfter("fun toggleSubscription() {").substringBefore("\n    }\n")
        assertTrue("row 700: state shown read at tap time", tap.contains("val shownFollowed = isSubscribed.value"))
        assertTrue("row 700: one tap at a time", tap.contains("if (!toggleInFlight.compareAndSet(false, true)) return"))
        assertTrue(
            "row 700: a tap against a state the button did not show toggles",
            // row 716: checked inside the toggle's own transaction, not by a read before it
            tap.contains("shownFollowed = shownFollowed,"),
        )
        assertTrue("row 700: the guard is released", tap.contains("finally {\n                toggleInFlight.set(false)"))
        assertTrue("the shared tap is still the one used", tap.contains("database.toggleArtistFollow("))
    }
}
