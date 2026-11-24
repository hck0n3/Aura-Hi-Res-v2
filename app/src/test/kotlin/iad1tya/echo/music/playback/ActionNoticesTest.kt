package iad1tya.echo.music.playback

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 805 (owner decision H8): «sesión caducada» and «cola vacía tras filtros» reach the app's snackbar with
 * «Volver a entrar» / «Ver filtros»; with no screen collecting, the service keeps the toast it always showed.
 */
class ActionNoticesTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `a notice is taken only while a screen collects it`() = runBlocking {
        assertFalse("no screen: the caller must toast", ActionNotices.post(ActionNotices.Kind.SESSION_EXPIRED))
        val got = CompletableDeferred<ActionNotices.Kind>()
        val screen = launch(start = CoroutineStart.UNDISPATCHED) { ActionNotices.events.collect { got.complete(it) } }
        assertTrue(ActionNotices.post(ActionNotices.Kind.QUEUE_EMPTY_AFTER_FILTERS))
        assertEquals(ActionNotices.Kind.QUEUE_EMPTY_AFTER_FILTERS, withTimeout(1_000) { got.await() })
        screen.cancelAndJoin()
        assertFalse("screen stopped: back to the toast", ActionNotices.post(ActionNotices.Kind.QUEUE_EMPTY_AFTER_FILTERS))
    }

    @Test
    fun `each notice opens the screen that fixes it`() {
        assertEquals("settings/accounts", ActionNotices.Kind.SESSION_EXPIRED.route)
        // Same destination as the playback error card's sign-in step (P22A, row 732).
        assertEquals(iad1tya.echo.music.ui.player.PlaybackErrorAdvice.Action.SIGN_IN.route, ActionNotices.Kind.SESSION_EXPIRED.route)
        assertEquals("settings/content", ActionNotices.Kind.QUEUE_EMPTY_AFTER_FILTERS.route)
        assertTrue(main("ui/screens/NavigationBuilder.kt").contains("composable(\"settings/content\") {"))
        assertTrue(main("ui/screens/NavigationBuilder.kt").contains("composable(\"settings/accounts\") {"))
    }

    @Test
    fun `the service offers the notice first and keeps its toast and log as the fallback`() {
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("!ActionNotices.post(ActionNotices.Kind.QUEUE_EMPTY_AFTER_FILTERS)"))
        assertTrue(service.contains("getString(R.string.queue_empty_after_filters),"))
        assertTrue(service.contains("\"playQueue: nothing to play — the queue returned %d item(s) and %s\","))
        // The re-login hint is consumed BEFORE the notice is offered: one streak, one notice, as before.
        val relogin = service.substringAfter("identityRejections.consumeReLoginHint() &&").substringBefore("VIDEO MODE:")
        assertTrue(relogin.contains("!ActionNotices.post(ActionNotices.Kind.SESSION_EXPIRED)"))
        assertTrue(relogin.contains("getString(R.string.session_expired_sign_in_again),"))
    }

    @Test
    fun `the activity shows it with its button only while started and never under the expanded player`() {
        val shell = main("MainActivity.kt")
        val collector = shell.substringAfter("this@MainActivity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {")
            .substringBefore("var showSettingDialoge")
        assertTrue(collector.contains("iad1tya.echo.music.playback.ActionNotices.events.collect { notice ->"))
        assertTrue(collector.contains("if (playerBottomSheetState.isExpanded) {"))
        assertTrue(collector.contains("actionLabel = getString(notice.action),"))
        assertTrue(collector.contains("runCatching { navController.navigateOnce(notice.route) }"))
    }

    @Test
    fun `the buttons are worded in English and Spanish`() {
        val en = res("values/notice_actions_strings.xml")
        assertTrue(en.contains("<string name=\"notice_action_sign_in_again\">Sign in again</string>"))
        assertTrue(en.contains("<string name=\"notice_action_view_filters\">View filters</string>"))
        val es = res("values-es/notice_actions_strings.xml")
        assertTrue(es.contains("<string name=\"notice_action_sign_in_again\">Volver a entrar</string>"))
        assertTrue(es.contains("<string name=\"notice_action_view_filters\">Ver filtros</string>"))
    }
}
