package iad1tya.echo.music.edge

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 543 — the rules of the edge layer that a refactor could silently lose. */
class EdgeGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun kotlinFiles() = File(root, "app/src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `playback and the licence have no remote flag`() {
        for (f in RemoteConfigs.KNOWN_FLAGS) {
            assertFalse(f, Regex("reproduc|playback|licen|play|equaliz|ecualiz").containsMatchIn(f))
        }
        val worker = File(root, "docs/worker/edge/src/index.js").readText()
        val list = Regex("export const FLAGS = \\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(worker)!!.groupValues[1]
        val workerFlags = Regex("\"([a-z_]+)\"").findAll(list).map { it.groupValues[1] }.toSet()
        assertEquals("la lista de la app y la del Worker son la misma", RemoteConfigs.KNOWN_FLAGS, workerFlags)
    }

    @Test
    fun `the log is only ever sent when the user presses the button`() {
        val callers = kotlinFiles().filter { "sendDiagnostics(" in it.readText() }.map { it.name }.toSet()
        assertEquals(setOf("EdgeClient.kt", "AuraServicesSettings.kt"), callers)
    }

    @Test
    fun `the layer is off by default and logs no personal data`() {
        val client = File(root, "app/src/main/kotlin/com/music/echo/edge/EdgeClient.kt").readText()
        assertTrue(client.contains("s.base.isBlank() -> State.NoAddress"))
        assertTrue(client.contains("s.key.isBlank() -> State.NoKey"))
        assertFalse("la dirección del servidor no se escribe en el registro", Regex("Timber[^\\n]*(s\\.base|s\\.key)").containsMatchIn(client))
    }

    @Test
    fun `the artist name never reaches the log`() {
        val client = File(root, "app/src/main/kotlin/com/music/echo/edge/EdgeClient.kt").readText()
        val body = client.substringAfter("suspend fun artistTags").substringBefore("private fun readCapped")
        assertFalse(Regex("Timber[^\\n]*artist").containsMatchIn(body))
    }
}
