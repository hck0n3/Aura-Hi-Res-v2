package iad1tya.echo.music.utils

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 834 (owner 2026-11-24, «haz K1, K2 y K3»: «quiero que la sincronización sea exacta y no que sincronice otras cosas
 * que no tengan nada que ver con lo que estoy pidiendo»): nothing is written to the YouTube account in bulk without the library-upload
 * switch. Source-text checks on purpose: every one of these paths needs a signed-in account, which no JVM test has.
 */
class AccountWritesGateGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/$path")
        assertTrue("$path moved: update this test", f.isFile)
        return f.readText()
    }

    /** The member body that starts at [signature], up to its closing brace at class-member indentation. */
    private fun body(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue("signature not found: $signature", start >= 0)
        val end = src.indexOf("\n    }\n", start)
        assertTrue("end of body not found: $signature", end > start)
        return src.substring(start, end)
    }

    @Test
    fun `the down sync of liked songs re-sends local likes only with the upload switch on`() {
        val src = main("utils/SyncUtils.kt")
        val gate = body(src, "private suspend fun isUploadSwitchOn(): Boolean = runCatching {")
        assertTrue("fails closed", gate.contains("context.dataStore.data.first()[YtmUploadSyncKey] ?: false"))
        assertTrue(gate.contains("}.getOrDefault(false)"))

        val liked = body(src, "private suspend fun executeSyncLikedSongs() = withContext(Dispatchers.IO) {")
        assertTrue(liked.contains("var pushBudget = if (isUploadSwitchOn()) LIKE_REPUSH_PER_SYNC else 0"))
        assertFalse("the budget is never unconditional again", liked.contains("var pushBudget = LIKE_REPUSH_PER_SYNC\n"))
        // Row 368 stays: the re-push still spends the budget, and a like the account never had is still not dropped.
        assertTrue(liked.contains("if (pushBudget > 0) {"))
        assertTrue(liked.contains("if (shouldPush || !everOnAccount) {"))
    }
}
