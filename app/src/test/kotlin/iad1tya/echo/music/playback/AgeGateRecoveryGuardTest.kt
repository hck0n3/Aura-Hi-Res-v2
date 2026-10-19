package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: a user sees «Accede para confirmar tu edad» (IO_UNSPECIFIED 2000) and the owner's order is that nobody is
 * ever locked out of explicit content. YouTube answers an age-gated video to the main client with LOGIN_REQUIRED, not with an
 * AGE_* status, so the age branches never ran. The recovery must be structural (status), never a match on the localised reason.
 */
class AgeGateRecoveryGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private val src: String by lazy {
        File(root, "app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt").also { assertTrue(it.isFile) }.readText()
    }

    @Test
    fun `a LOGIN_REQUIRED from the main client gets one more chance through the age-gate clients`() {
        val block = src.substringAfter("AGE GATE THAT ARRIVES AS LOGIN_REQUIRED").substringBefore("NOTE (fix #5)")
        assertTrue(block.contains("mainStatus == \"LOGIN_REQUIRED\""))
        assertTrue("the account's creator client for a signed-in user", block.contains("WEB_CREATOR"))
        assertTrue("the TV embedded player, which needs no login", block.contains("TVHTML5_SIMPLY_EMBEDDED_PLAYER"))
        assertTrue("a recovered response replaces the refused one", block.contains("mainPlayerResponse = gateResponse"))
        assertTrue("NewPipe is skipped for age-gated videos", block.contains("wasOriginallyAgeRestricted = true"))
    }

    @Test
    fun `the recovery never matches the localised reason text and never logs it`() {
        val block = src.substringAfter("AGE GATE THAT ARRIVES AS LOGIN_REQUIRED").substringBefore("NOTE (fix #5)")
        // The block starts in the MIDDLE of the first comment line, so that line is dropped before comments are filtered out.
        val code = block.lines().drop(1).filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(code.contains(".reason"))
        assertFalse(code.contains("confirm your age", ignoreCase = true))
        assertFalse(code.contains("edad", ignoreCase = true))
    }
}
