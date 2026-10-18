package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 423 (owner 2026-10-18): «le estaba dictando y se cortó el dictado y empezó a buscar». Google's recognizer
 * ends a phrase on its own short pause, so its FINAL result is not "the user stopped". Pedir música must keep each final
 * result, restart the recognizer, and send only after a real silence (or the «OK» button).
 */
class VoiceDictationSettleTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun ui(name: String): String {
        val f = File(root, "app/src/main/kotlin/com/music/echo/ui/newui/$name")
        assertTrue("$name se movió", f.isFile)
        return f.readText()
    }

    @Test
    fun `Pedir musica dictation waits for a silence and the plain search bars keep the old behaviour`() {
        val request = ui("AuraMusicRequest.kt")
        assertTrue(request.contains("settleMs = MUSIC_REQUEST_DICTATION_SETTLE_MS"))
        val voice = request.substringAfter("val voice = rememberAuraVoiceSearch(").substringBefore("Column(")
        assertTrue("el envío sigue dentro de onResult", voice.contains("send(spoken)"))
        for (screen in listOf("AuraPlayerQuickSearch.kt", "AuraSearchResultScreen.kt")) {
            val call = ui(screen).substringAfter("rememberAuraVoiceSearch(").substringBefore("\n    )")
            assertTrue("$screen no debe esperar (barra de búsqueda normal)", !call.contains("settleMs"))
        }
    }

    @Test
    fun `settled dictation keeps the final phrase, restarts the recognizer and cancels its timer when speech resumes`() {
        val src = ui("AuraSearchScreen.kt")
        val results = src.substringAfter("override fun onResults(").substringBefore("override fun onPartialResults(")
        assertTrue("acumula la frase final", results.contains("committed.append(spoken)"))
        assertTrue("reinicia el reconocedor", results.contains("speech.startListening(recognitionIntent())"))
        assertTrue("el envío espera settleMs", results.contains("delay(settleMs)"))
        assertTrue("hablar de nuevo cancela la espera", src.substringAfter("override fun onBeginningOfSpeech()").take(200).contains("settleJob?.cancel()"))
        assertTrue("Cancelar descarta lo dictado", src.contains("val cancelDictation") && src.contains("committed.clear()"))
    }
}
