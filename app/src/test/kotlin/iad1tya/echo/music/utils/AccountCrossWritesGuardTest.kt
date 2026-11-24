package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 835 (owner 2026-11-24, «haz K1, K2 y K3»): a write meant for one YouTube account never reaches another one, and what
 * another device did is not written to this device's account. Source-text checks: each path needs a signed-in account or a paired room.
 */
class AccountCrossWritesGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    @Test
    fun `the push mailbox checks the account before every send and is emptied when the account is detached`() {
        val push = main("utils/AccountPush.kt")
        val drive = push.substringAfter("private suspend fun drive(key: String, force: Boolean) {").substringBefore("\n    }\n")
        val read = drive.indexOf("val e = synchronized(lock) { ensureLoaded(); adoptAccount(); entries[key] } ?: return")
        assertTrue("the entry is read only after the account was adopted", read >= 0)
        assertTrue("before anything is sent", read < drive.indexOf("val outcome = send(e)"))

        val forget = push.substringAfter("fun forgetAccount() {").substringBefore("\n    }\n")
        assertTrue(forget.contains("entries.clear()"))
        assertTrue(forget.contains("accountTag = null"))
        assertTrue("written to disk too, so a restart does not bring them back", forget.contains("persist()"))
        assertFalse("counts only (rule 4)", forget.contains("e.id"))

        val app = main("App.kt").substringAfter("suspend fun forgetAccount(context: Context) {")
        val call = app.indexOf("iad1tya.echo.music.utils.AccountPush.forgetAccount()")
        assertTrue("emptied before the session goes", call in 0 until app.indexOf("YouTube.cookie = null"))
        // Row 716 stays right next to it.
        assertTrue(app.indexOf("ArtistEntity.dropPendingAccountCalls()") in 0 until call)
    }
}
