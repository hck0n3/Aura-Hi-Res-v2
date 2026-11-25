package iad1tya.echo.music.ui.screens.settings

import iad1tya.echo.music.utils.PhantomSubscriptionPolicy
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Candidate
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy.Interest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 856 (owner test #3, 2026-11-24): «Revisar suscripciones» showed no total; the tester counted ~413 by
 * scrolling. The header says how many the account lists («al menos» when the read stopped early, row 837) and how many
 * are suggested — the same number «Seleccionar las sugeridas (N)» shows.
 */
class SubscriptionCleanupTotalsTest {

    private val root: File =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved: update this test", it.isFile) }.readText()

    private fun candidate(id: String, suggested: Boolean, pending: Boolean = false, confirmed: Boolean = false) = Candidate(
        id = id,
        name = id,
        thumbnailUrl = null,
        channelId = null,
        verdict = PhantomSubscriptionPolicy.verdict(if (suggested) Interest() else Interest(likedSongs = 1)),
        removalPending = pending,
        removalConfirmed = confirmed,
    )

    @Test
    fun `the header counts every listed row and the suggestions the select button would pick`() {
        val candidates = List(410) { candidate("UC$it", suggested = it < 40) } +
            candidate("UCpending", suggested = true, pending = true) +
            candidate("UCconfirmed", suggested = true, confirmed = true) +
            candidate("UCkept", suggested = false)
        val totals = subscriptionCleanupTotals(candidates, incomplete = false)
        assertEquals(413, totals.listed)
        assertEquals(40, totals.suggested)
        assertEquals(PhantomSubscriptionPolicy.suggestedSelection(candidates).size, totals.suggested)
        assertEquals(false, totals.atLeast)
        assertEquals(true, subscriptionCleanupTotals(candidates, incomplete = true).atLeast)
        assertEquals(SubscriptionCleanupTotals(0, 0, false), subscriptionCleanupTotals(emptyList(), incomplete = false))
    }

    @Test
    fun `the screen shows the header with the incomplete flag`() {
        val screen = text("app/src/main/kotlin/com/music/echo/ui/screens/settings/SubscriptionCleanupScreen.kt")
        assertTrue(screen.contains("val totals = subscriptionCleanupTotals(state.candidates, state.incomplete)"))
        assertTrue(screen.contains("pluralStringResource(R.plurals.sub_cleanup_total_at_least, totals.listed, totals.listed)"))
        assertTrue(screen.contains("pluralStringResource(R.plurals.sub_cleanup_total, totals.listed, totals.listed)"))
        assertTrue(screen.contains("pluralStringResource(R.plurals.sub_cleanup_total_suggested, totals.suggested, totals.suggested)"))
        for (values in listOf("values", "values-es")) {
            val strings = text("app/src/main/res/$values/subscription_cleanup_strings.xml")
            for (key in listOf("sub_cleanup_total", "sub_cleanup_total_at_least", "sub_cleanup_total_suggested")) {
                assertTrue("$values $key", strings.contains("<plurals name=\"$key\">"))
            }
        }
    }
}
