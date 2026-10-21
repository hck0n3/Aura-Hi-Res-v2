package iad1tya.echo.music.utils.cipher

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner log 2026-10-20: age-gated songs were answered OK by the account's clients and then died with
 * «No stream URL … hasCipher=true»: the player had rotated to a hash the app has no entry for and the older cipher path found
 * no signature function. yt-dlp's EJS solver reads the real player and needs no entry, so it solves the signatures; the
 * signatureTimestamp the request carries comes from the same player file. These guards pin the wiring and the shipped assets
 * (the page was exercised in a real Chromium against a structural mock of the player).
 */
class EjsSolverWiringGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun file(path: String) = File(root, path).also { assertTrue("$path se movió", it.isFile) }.readText()

    private val kt = "app/src/main/kotlin/com/music/echo/utils"
    private val assets = "app/src/main/assets/solver"

    @Test
    fun `a signature cipher goes to the EJS solver first and the older path stays as the fallback`() {
        val src = file("$kt/cipher/CipherDeobfuscator.kt")
        val entry = src.substringAfter("suspend fun deobfuscateStreamUrl(").substringBefore("private suspend fun deobfuscateStreamUrlViaCipherWebView")
        assertTrue(entry.contains("EjsNTransformSolver.deobfuscateSignatureCipher(signatureCipher)"))
        assertTrue("el camino anterior sigue de respaldo", entry.contains("return deobfuscateStreamUrlViaCipherWebView(signatureCipher, videoId)"))
    }

    @Test
    fun `the solver answers sig and n from the real player, one at a time, and logs no challenge`() {
        val src = file("$kt/sabr/EjsNTransformSolver.kt")
        assertTrue(src.contains("suspend fun deobfuscateSignatureCipher("))
        assertTrue(src.contains("solve(\"sig\", parts.s)") && src.contains("solve(\"n\", nValue)"))
        assertTrue("sin respuesta no se inventa una URL", src.contains("?: return null"))
        assertTrue("una a la vez", src.contains("solverMutex.withLock"))
        assertTrue("cada respuesta lleva el id de su petición: una tardía no se entrega a la siguiente", src.contains("fun onSolved(id: Int, kind: String, result: String)") && src.contains("takePending(id)?.resumeSafely { it.resume(result) }"))
        assertTrue("un build fallido no se reintenta en cada canción", src.contains("CREATE_FAILURE_BACKOFF_MS"))
        assertTrue("si quien llama cancela, el WebView a medias se cierra", src.contains("catch (e: kotlinx.coroutines.CancellationException) {") && src.contains("created?.let { discard(it) }"))
        assertTrue("el solver se reconstruye cuando el reproductor rota", src.contains("currentHash == existing.playerHash"))
        assertTrue("diagnóstico sin datos del usuario", src.contains("EJS_SOLVE kind=%s status=%s ms=%d hash=%s") && src.contains("EJS_SOLVER ready hash=%s"))
        // The log line carries the kind and the state, never the value or the answer.
        val report = src.substringAfter("fun report(").substringBefore("return solverMutex")
        assertFalse(report.contains("value") || report.contains("answer"))
    }

    @Test
    fun `the signature timestamp of a request is read from the same player the signatures are solved against`() {
        val src = file("$kt/YTPlayerUtils.kt")
        val fn = src.substringAfter("private suspend fun getSignatureTimestampOrNull(").substringBefore("data class StreamUrlResult")
        assertTrue(fn.contains("PlayerJsFetcher.currentSignatureTimestamp()"))
        assertTrue("NewPipe queda de respaldo", fn.contains("NewPipeExtractor.getSignatureTimestamp(videoId)"))
        assertTrue("la memoria del sts sigue al reproductor", src.contains("stsMemoMatchesPlayer()"))
        assertTrue("el log nombra el cliente que sirvió, no siempre el principal", src.contains("(usedAgeRestrictedClient ?: MAIN_CLIENT)).clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName"))
    }

    @Test
    fun `the shipped solver assets are the yt-dlp bundle with the page that cannot navigate away`() {
        val lib = file("$assets/yt.solver.lib.js")
        val core = file("$assets/yt.solver.core.js")
        val page = file("$assets/yt.solver.page.html")
        assertTrue(lib.contains("meriyah") && lib.contains("astring") && lib.contains("Unlicense"))
        assertTrue(core.contains("SPDX-License-Identifier: Unlicense"))
        // In a browser, `globalThis.location = …` navigates the page and kills the solver; the patch keeps it a local var.
        assertFalse("el prólogo no puede asignar window.location (la rama sin URL no existe en un WebView)", core.contains("globalThis.location = new URL("))
        assertTrue(core.contains("var location = new URL("))
        assertTrue(page.contains("<script src=\"yt.solver.lib.js\"></script>"))
        assertTrue("la lib se publica como globales antes del core", page.indexOf("Object.assign(globalThis, lib)") in 1 until page.indexOf("yt.solver.core.js"))
        assertTrue(page.contains("function solve(id, kind, value)") && page.contains("EjsBridge.onSolved(id, kind, String(r))"))
        assertFalse("los motores viejos sueltos ya no se empaquetan", File(root, "$assets/meriyah.js").exists() || File(root, "$assets/astring.js").exists())
    }
}
