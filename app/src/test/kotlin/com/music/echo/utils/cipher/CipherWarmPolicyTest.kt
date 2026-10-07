package iad1tya.echo.music.utils.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Row 330 (plan A2): the startup cipher WebView is built only when a consumer will use it. */
class CipherWarmPolicyTest {

    @Test
    fun noPlayerJsSkips() {
        assertEquals(CipherWarmPolicy.Decision.SKIP_NO_PLAYER_JS, CipherWarmPolicy.decide(null, 20702))
        assertEquals(CipherWarmPolicy.Decision.SKIP_NO_PLAYER_JS, CipherWarmPolicy.decide("", 20702))
    }

    @Test
    fun unpublishedHashSkips() {
        // The owner's state on 2026-10-06: YouTube rotated to 1f293754 and no config is published.
        assertEquals(
            CipherWarmPolicy.Decision.SKIP_NO_VERIFIED_CONFIG,
            CipherWarmPolicy.decide("1f293754", null),
        )
    }

    @Test
    fun verifiedHashStillPrewarms() {
        assertEquals(CipherWarmPolicy.Decision.CREATE, CipherWarmPolicy.decide("8c3fda2d", 20702))
    }

    @Test
    fun verifiedStsMirrorsTheDecoderCheck() {
        assertNull(CipherWarmPolicy.verifiedSts(null))
        assertNull(CipherWarmPolicy.verifiedSts(0))
        assertNull(CipherWarmPolicy.verifiedSts(-1))
        assertEquals(20702, CipherWarmPolicy.verifiedSts(20702))
    }

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate the repository root from ${File("").absolutePath}")
    }

    private fun source(path: String) =
        File(repoRoot, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun prewarmAndDecoderShareOnePredicateAndSelfRepairStays() {
        val deob = source("utils/cipher/CipherDeobfuscator.kt")
        val prewarm = deob.substringAfter("suspend fun prewarm()").substringBefore("\n    }\n")
        val decideAt = prewarm.indexOf("CipherWarmPolicy.decide")
        assertTrue("prewarm must go through the policy", decideAt >= 0)
        assertTrue("the WebView is built only after the decision", prewarm.indexOf("getOrCreateWebView(") > decideAt)
        assertTrue(prewarm.contains("verifiedStsForHash"))

        val decoder = source("utils/cipher/PipePipeLocalCipherDecoder.kt")
        val getPlayerData = decoder.substringAfter("override fun getPlayerData").substringBefore("\n    }\n")
        assertTrue(getPlayerData.contains("verifiedStsForHash("))
        assertFalse("no second, drifting sts check", getPlayerData.contains("signatureTimestamp"))

        // Self-repair through player_configs.json is untouched (rows #30, #226).
        assertTrue(deob.contains("RemotePlayerConfig.forceRefresh("))
        assertTrue(deob.contains("RemotePlayerConfig.configEpoch"))
    }
}
