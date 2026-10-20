package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19: a user sees «Accede para confirmar tu edad» and the owner's order is that Aura uses the signed-in account
 * so nobody is ever locked out of explicit content. YouTube answers an age-gated video to the main client with LOGIN_REQUIRED
 * (not an AGE_* status) and ANDROID_VR never sends the account, so the recovery goes through the clients that do. The trigger
 * must be structural (status), never the localised reason text.
 */
class AgeGateRecoveryGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun file(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val src: String by lazy { file("app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt") }

    /** The recovery block. It starts in the MIDDLE of a comment line, so that first line is dropped. */
    private val block: String by lazy {
        src.substringAfter("AGE GATE (owner 2026-10-19").substringBefore("NOTE (fix #5)")
    }

    @Test
    fun `an age-gate-shaped main response runs the chain with the real signature timestamp and the account`() {
        assertTrue(block.contains("AgeGateChain.isAgeGateShaped(mainStatus, mainPlayerResponse?.playabilityStatus?.desktopLegacyAgeGateReason)"))
        assertTrue(block.contains("AgeGateChain.order(isLoggedIn)"))
        for (client in listOf("WEB_EMBEDDED_PLAYER", "TVHTML5_SAMSUNG", "WEB_CREATOR", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")) {
            assertTrue("falta $client", block.contains(client))
        }
        assertTrue("WEB_REMIX con la cuenta es un paso de la cadena", block.contains("AgeGateChain.Step.ACCOUNT_WEB_REMIX -> WEB_REMIX"))
        assertTrue("los pasos que exigen PO token lo piden y se saltan sin él", block.contains("AgeGateChain.needsStreamPoToken(step)") && block.contains("potoken=unavailable"))
        assertTrue("el token viaja como PO token de la petición", block.contains("gatePoToken?.playerRequestPoToken"))
        assertTrue("el pot= de la URL sale del token del paso", block.contains("poToken = hit.poToken"))
        assertTrue("signatureTimestamp real, no null", block.contains("awaitSts().timestamp"))
        assertTrue("la sesión NO se apaga en este camino", block.contains("noLogin = noLogin"))
        assertTrue("una respuesta con streamingData sustituye a la rechazada", block.contains("mainPlayerResponse = hit.response"))
        assertTrue("NewPipe se salta en vídeos con edad", block.contains("wasOriginallyAgeRestricted = true"))
    }

    @Test
    fun `the recovery never matches the localised reason text and never logs it`() {
        val code = block.lines().drop(1).filterNot { it.trim().startsWith("//") }.joinToString("\n")
        assertFalse(code.contains(".reason"))
        assertFalse(code.contains("confirm your age", ignoreCase = true))
        assertFalse(code.contains("edad", ignoreCase = true))
        assertTrue("el log lleva cliente y estado", code.contains("AGE_GATE client="))
        val logs = code.lines().filter { it.contains("AGE_GATE") }.joinToString("\n")
        for (secret in listOf("videoId", "cookie", "visitorData", "streamingDataPoToken", "playerRequestPoToken")) {
            assertFalse("el log AGE_GATE no lleva $secret", logs.contains(secret))
        }
    }

    @Test
    fun `an age gate that no client could open skips the anonymous retry and tells the truth`() {
        assertTrue(block.contains("ageGated?.set(true)"))
        assertTrue("el reintento anónimo no se dispara por una puerta de edad", src.contains("&& !ageGated.get()"))
        assertTrue(src.contains("AgeGateChain.failureMessage(isLoggedIn)"))
    }

    @Test
    fun `a client that answered OK but whose URL is unusable hands over to the next chain step`() {
        assertTrue("el hueco del cliente recuperado se repite con el siguiente paso", src.contains("val next = tryNextGateStep(poToken) ?: continue"))
        assertTrue(src.contains("val gateRetrySlots"))
        assertTrue("la puerta cerrada se marca también si el cliente recuperado no sirvió", src.contains("if (ageGateMet && ("))
    }

    @Test
    fun `an ambiguous login-required still gets the stale-session retry as the last step`() {
        assertTrue(src.contains("if (finished.isFailure && staleSessionShaped && firstAgeGated)"))
        assertTrue("el WebView decide con la bandera del primer intento", src.contains("finishWithEmbeddedFallback(videoId, firstAttempt, firstAgeGated)"))
    }

    @Test
    fun `the age-gate clients are served by the www host with the www origin and the account headers`() {
        val clients = file("innertube/src/main/kotlin/com/music/innertube/models/YouTubeClient.kt")
        assertTrue(clients.contains("val WEB_EMBEDDED_PLAYER = YouTubeClient("))
        assertTrue(clients.contains("clientId = \"56\""))
        assertTrue(clients.contains("val TVHTML5_SAMSUNG = YouTubeClient("))
        val tube = file("innertube/src/main/kotlin/com/music/innertube/InnerTube.kt")
        assertTrue(tube.contains("client.useWwwHost) YouTubeClient.API_URL_YOUTUBE_WWW + \"player\""))
        assertTrue(tube.contains("sha1(\"\$currentTime \${cookieMap[\"SAPISID\"]} \$origin\")"))
        assertTrue(tube.contains("X-Goog-AuthUser"))
        assertTrue("embedUrl propio del cliente", tube.contains("client.embedUrl ?:"))
        assertTrue("los embebidos envían clientScreen EMBED", tube.contains("asEmbedded("))
        assertTrue("WEB_CREATOR exige PO token", clients.substringAfter("val WEB_CREATOR = YouTubeClient(").substringBefore("val TVHTML5 =").contains("useWebPoTokens = true"))
    }
}
