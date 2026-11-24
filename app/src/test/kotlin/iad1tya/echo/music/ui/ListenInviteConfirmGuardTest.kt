package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 804 (owner decision H9): opening a Listen Together invite link asks «¿Unirte a la sala X?» before joining.
 * Pinned as source text because a deep link that joins on its own again would compile just as well.
 */
class ListenInviteConfirmGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the invite link only arms the question, it never joins by itself`() {
        val shell = main("MainActivity.kt")
        val branch = shell.substringAfter("if (!listenCode.isNullOrBlank() && isListenLink) {").substringBefore("return")
        assertTrue(branch.contains("pendingListenInvite = listenCode"))
        assertFalse(branch.contains("joinRoom("))
        // The ONLY join left in the activity is the dialog's «Unirme».
        assertEquals(1, Regex("""listenTogetherManager\.joinRoom\(""").findAll(shell).count())
        val dialog = shell.substringAfter("pendingListenInvite?.let { code ->").substringBefore("dismissButton = {")
        assertTrue(dialog.contains("listenTogetherManager.joinRoom(code, username)"))
        assertTrue(dialog.contains("stringResource(R.string.listen_invite_confirm_title, code)"))
        assertTrue(dialog.contains("onDismissRequest = { pendingListenInvite = null }"))
    }

    @Test
    fun `an unanswered invite survives a rotation`() {
        val shell = main("MainActivity.kt")
        assertTrue(shell.contains("pendingListenInvite?.let { outState.putString(STATE_PENDING_LISTEN_INVITE, it) }"))
        assertTrue(shell.contains("pendingListenInvite = savedInstanceState?.getString(STATE_PENDING_LISTEN_INVITE)"))
    }

    @Test
    fun `the dialog texts exist in English and Spanish`() {
        val en = res("values/listen_invite_strings.xml")
        assertTrue(en.contains("<string name=\"listen_invite_confirm_title\">Join room %1\$s?</string>"))
        assertTrue(en.contains("<string name=\"listen_invite_join\">Join</string>"))
        val es = res("values-es/listen_invite_strings.xml")
        assertTrue(es.contains("<string name=\"listen_invite_confirm_title\">¿Unirte a la sala %1\$s?</string>"))
        assertTrue(es.contains("<string name=\"listen_invite_join\">Unirme</string>"))
    }
}
