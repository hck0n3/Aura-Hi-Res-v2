package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 520 — owner 2026-10-29: «la opción de compartir que pregunte si quiero compartir por enlace o por MP3; si la
 * canción tiene vídeo que también pregunte; y si no están descargadas y elijo MP3 o vídeo, que lo descargue al seleccionar y
 * luego me aparezca el menú de compartir».
 */
class ShareChoiceGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private fun main(path: String) = File(root, "app/src/main/kotlin/com/music/echo/$path").readText()

    @Test
    fun `every song share opens the choice window instead of sending the link at once`() {
        for (menu in listOf("ui/menu/SongMenu.kt", "ui/menu/YouTubeSongMenu.kt", "ui/menu/QueueMenu.kt", "ui/menu/PlayerMenu.kt", "ui/menu/OldPlayerMenu.kt", "ui/player/Player.kt")) {
            assertTrue("$menu abre la ventana de elección", main(menu).contains("ShareChoiceState.open("))
        }
        // The two new-interface entry points go through shareSong(), which opens it too.
        val media = main("utils/LocalMediaIntents.kt")
        assertTrue(media.substring(media.indexOf("suspend fun shareSong(")).contains("ShareChoiceState.open("))
        assertTrue(main("ui/newui/AuraPlayer.kt").contains("hasVideo = meta.isVideoSong"))
        assertTrue(main("ui/newui/AuraPlayerMenu.kt").contains("hasVideo = mediaMetadata.isVideoSong"))
        assertTrue("el anfitrión está en la actividad", main("MainActivity.kt").contains("ShareChoiceHost()"))
    }

    @Test
    fun `the window offers link and MP3, video only for a song that has one, and saves before sharing`() {
        val sheet = main("ui/newui/AuraShareSheet.kt")
        assertTrue(sheet.contains("R.string.share_choice_link") && sheet.contains("R.string.share_choice_mp3"))
        assertTrue("el vídeo solo si hay vídeo", sheet.contains("if (request.hasVideo)"))
        // what is already saved is shared at once; what is not is exported and shared when the file exists
        assertTrue(sheet.contains("shareSavedFile(kind)") && sheet.contains("AudioExportService.startBatch("))
        assertTrue(sheet.indexOf("AudioExportService.startBatch(") < sheet.indexOf("lookupExportedFileUri(context, request.songId)", sheet.indexOf("LaunchedEffect(waiting)")))
        assertFalse("sin títulos en el registro", Regex("""Timber\.tag\("SHARE"\)[^\n]*title""").containsMatchIn(sheet))
    }
}
