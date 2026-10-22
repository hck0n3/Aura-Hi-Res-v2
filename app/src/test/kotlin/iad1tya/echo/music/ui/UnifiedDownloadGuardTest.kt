package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-10-19:
 *  · *«cuando esté exportando a mp3 o en video quiero ver cómo va el proceso en la barra de notificaciones, no quiero que
 *    cada rato me esté notificando, solo quiero ver la barra de progreso»*;
 *  · *«que el botón de descargar esté unificado… todos los tipos de descarga en un solo botón… y si estoy en un álbum o
 *    playlist que pregunte si deseo seleccionar las que yo quiero o descargarlo todo… visible debajo de las portadas»*.
 *
 * Source-text guards: the service and the screens are Android code, so what is pinned here is the wiring a refactor
 * could silently undo.
 */
class UnifiedDownloadGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    private fun res(path: String) =
        File(root, "app/src/main/res/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    // ── A · one quiet notification with a progress bar ────────────────────────────────────────────────

    @Test
    fun `the export notification is one silent progress notification, throttled`() {
        val svc = main("playback/AudioExportService.kt")
        val build = svc.substringAfter("private fun buildExportNotification(").substringBefore("private fun startExportForeground()")
        assertTrue("sin sonido ni aviso en cada actualización", build.contains(".setOnlyAlertOnce(true)") && build.contains(".setSilent(true)"))
        assertTrue(build.contains(".setCategory(NotificationCompat.CATEGORY_PROGRESS)"))
        assertTrue("barra de progreso", build.contains(".setProgress(100, overallPercent, indeterminate)"))
        assertTrue("el aviso de arranque sale por el mismo constructor (antes no llevaba onlyAlertOnce)",
            svc.substringAfter("private fun startExportForeground()").substringBefore("override fun onBind").contains("buildExportNotification("))
        val post = svc.substringAfter("private fun postProgressNotification(").substringBefore("private fun buildExportNotification(")
        assertTrue("no se reconstruye en cada tic: tope de frecuencia", post.contains("NOTIFY_MIN_INTERVAL_MS") && post.contains("nm.notify(NOTIFICATION_ID"))
        assertTrue(svc.contains("private const val NOTIFY_MIN_INTERVAL_MS = 1_000L"))
        assertTrue("canal de importancia baja, sin sonido ni vibración", svc.contains("NotificationManager.IMPORTANCE_LOW") &&
            svc.contains("setSound(null, null)") && svc.contains("enableVibration(false)"))
    }

    @Test
    fun `a list is exported under ONE notification, not one per song`() {
        val svc = main("playback/AudioExportService.kt")
        assertTrue("la lista entera se encola de una vez", svc.contains("fun startBatch(context: Context, tasks: List<AudioExportTask>)"))
        assertTrue(svc.contains("private suspend fun drainQueue()"))
        // between two songs the notification is NOT removed: only the last song (or an idle timeout) stops the service
        val finish = svc.substringAfter("private fun finishJob(").substringBefore("private fun stopExportService()")
        assertTrue(finish.contains("batchQueue.isEmpty()"))
        assertTrue(finish.contains("lastOfBatch -> stopExportService()"))
        assertFalse("ya no se cierra la notificación tras cada canción",
            svc.substringAfter("private suspend fun exportSong(").substringBefore("private fun finishJob(")
                .contains("ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)"))
        // the bar is the whole list's progress, and the title says «k de N»
        assertTrue(svc.contains("((batchIndex * 100 + itemPercent) / batchTotal)"))
        assertTrue(svc.contains("R.string.export_notification_batch_title"))
        // one toast at the end instead of one per song
        assertTrue(svc.contains("R.plurals.export_batch_finished") && svc.contains("if (batchTotal <= 1)"))
    }

    @Test
    fun `every batch caller hands the whole list to the service`() {
        for (f in listOf("ui/menu/Mp3BatchExport.kt", "ui/menu/PlaylistMenu.kt", "ui/menu/PlaylistScreenMenus.kt", "ui/menu/YouTubePlaylistMenu.kt")) {
            val src = main(f)
            assertTrue("$f usa la cola del servicio", src.contains("AudioExportService.startBatch("))
            assertFalse("$f ya no recorre las canciones de una en una", src.contains("exporting.first { !it }"))
        }
    }

    // ── B · the unified download button ───────────────────────────────────────────────────────────────

    @Test
    fun `the sheet offers offline, MP3 and video, and asks all-or-pick for a list`() {
        val sheet = main("ui/newui/AuraDownloadSheet.kt")
        assertTrue(sheet.contains("fun AuraDownloadSheet("))
        assertTrue("formato sin conexión", sheet.contains("R.string.download_offline_only"))
        assertTrue("formato MP3", sheet.contains("R.string.export_as_mp3"))
        assertTrue("formato vídeo", sheet.contains("R.string.export_as_video"))
        assertTrue("el ajuste «Exportar como MP3» sigue mandando", sheet.contains("EnableExportAsMp3Key"))
        assertTrue("vídeo solo para lo que lo tiene", sheet.contains("videoItems.isNotEmpty()"))
        assertTrue("paso «todas o elegir» antes de descargar una lista", sheet.contains("DownloadSheetStep.Scope") && sheet.contains("DownloadSheetStep.Pick"))
        assertTrue(sheet.contains("R.string.download_sheet_all") && sheet.contains("R.string.download_sheet_pick"))
        assertTrue("una sola canción salta la pregunta", sheet.contains("if (pool.size <= 1)"))
        assertTrue("las exportaciones van a la cola única", sheet.contains("AudioExportService.startBatch("))
        for (lang in listOf("values", "values-es")) {
            val xml = res("$lang/echo_strings.xml")
            for (key in listOf("download_sheet_all", "download_sheet_pick", "download_sheet_start", "download_sheet_back",
                "export_notification_batch_title", "export_batch_finished", "export_batch_finished_failed")) {
                assertTrue("$lang: falta $key", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the button sits under the cover of albums and lists and opens the sheet`() {
        val album = main("ui/newui/AuraAlbumScreen.kt")
        val albumHeader = album.substringAfter("private fun AuraAlbumHeader(").substringBefore("// ── Piezas compartidas")
        assertTrue(albumHeader.contains("AuraDownloadSheet(") && albumHeader.contains("showDownloadSheet = true"))
        val local = main("ui/newui/AuraLocalPlaylistScreen.kt")
        val localHeader = local.substringAfter("private fun AuraLocalPlaylistHeader(").substringBefore("private fun AuraLocalPlaylistMenuHost(")
        assertTrue(localHeader.contains("AuraDownloadSheet(") && localHeader.contains("showDownloadSheet = true"))
        val online = main("ui/newui/AuraOnlinePlaylistScreen.kt")
        val onlineHeader = online.substringAfter("private fun AuraOnlinePlaylistHeader(")
        assertTrue(onlineHeader.contains("AuraDownloadSheet(") && onlineHeader.contains("showDownloadSheet = true"))
        assertTrue("una lista online a medio cargar se completa antes de ofrecerla", onlineHeader.contains(".completed()"))
        // nothing is taken out of the ⋮ menus (UI_INVENTORY rule 5)
        assertTrue(main("ui/menu/AlbumMenu.kt").contains("R.string.action_download"))
        assertTrue(main("ui/menu/YouTubePlaylistMenu.kt").contains("R.string.action_download"))
    }

    @Test
    fun `a single song opens the same sheet from the player and the song menus`() {
        assertTrue(main("ui/newui/AuraPlayer.kt").contains("AuraDownloadSheet("))
        assertTrue(main("ui/newui/AuraPlayerMenu.kt").contains("AuraDownloadSheet("))
        assertTrue(main("ui/menu/SongMenu.kt").contains("AuraDownloadSheet("))
        assertTrue(main("ui/menu/YouTubeSongMenu.kt").contains("AuraDownloadSheet("))
        // the video probe is shared, so the sheet and the old chooser answer the same way
        assertTrue(main("ui/utils/ExportFormatChooser.kt").contains("fun rememberExportVideoAvailability("))
    }

    // ── C · the picker opens EMPTY, with a real «select all» button (owner 2026-10-19) ─────────────────

    @Test
    fun `the song picker opens with nothing ticked and has a select-all button with a counter`() {
        val sheet = main("ui/newui/AuraDownloadSheet.kt")
        val scope = sheet.substringAfter("DownloadSheetStep.Scope -> {").substringBefore("DownloadSheetStep.Pick -> {")
        assertTrue("entrar a «Elegir canciones» deja la selección vacía", scope.contains("picked = emptySet()"))
        assertFalse("nada preseleccionado al entrar", scope.contains("picked = pool.map { it.id }.toSet()"))
        val pick = sheet.substringAfter("DownloadSheetStep.Pick -> {").substringBefore("DownloadSheetStep.ConfirmRemove -> {")
        assertTrue("botón seleccionar todo / quitar selección con contador", pick.contains("SelectAllBar(") && pick.contains("selected = picked.size") && pick.contains("total = pool.size"))
        assertTrue("el botón de empezar se ve deshabilitado con 0", pick.contains("alpha(if (picked.isEmpty()) 0.4f else 1f)") && pick.contains("if (picked.isNotEmpty())"))
        assertTrue("volver atrás también vacía la selección", pick.substringAfter("SheetBack {").contains("picked = emptySet()"))
        val bar = sheet.substringAfter("private fun SelectAllBar(").substringBefore("private fun PickRow(")
        assertTrue(bar.contains("download_sheet_select_none") && bar.contains("download_sheet_select_all") && bar.contains("download_sheet_selected_count"))
        for (lang in listOf("values", "values-es")) {
            val xml = res("$lang/echo_strings.xml")
            for (key in listOf("download_sheet_select_all", "download_sheet_select_none", "download_sheet_selected_count")) {
                assertTrue("$lang: falta $key", xml.contains("name=\"$key\""))
            }
        }
    }

    @Test
    fun `the playlist MP3 pickers open empty and show the count, and Continuar needs a selection`() {
        for ((file, pool) in listOf(
            "ui/menu/PlaylistMenu.kt" to "songs",
            "ui/menu/PlaylistScreenMenus.kt" to "exportSongs",
            "ui/menu/YouTubePlaylistMenu.kt" to "mp3SongPool",
        )) {
            val src = main(file)
            assertFalse("$file: «Seleccionar» ya no marca todo", src.contains("selectedMp3Ids = $pool.map { it.id }.toSet()\n                        showMp3SelectDialog = true"))
            assertTrue("$file: abre vacío", src.contains("selectedMp3Ids = emptySet()\n                        showMp3SelectDialog = true"))
            assertTrue("$file: contador", src.contains("R.string.download_sheet_selected_count"))
            assertTrue("$file: Continuar exige selección", src.contains("enabled = selectedMp3Ids.isNotEmpty()"))
        }
    }

    // ── D · the sheet is a glass panel, not a see-through window (owner 2026-10-19) ───────────────────

    @Test
    fun `the download sheet is an in-window glass panel with the Material sheet only as fallback`() {
        val sheet = main("ui/newui/AuraDownloadSheet.kt")
        assertTrue("usa el panel de cristal en la ventana", sheet.contains("AuraInWindowDialog(") && sheet.contains("center = false"))
        assertTrue("solo con fuente haze y host de overlays", sheet.contains("LocalOverlayHazeState.current != null") && sheet.contains("auraOverlayHostAvailable()"))
        assertTrue("sin cristal, la hoja Material de siempre", sheet.contains("AuraModalBottomSheet("))
        assertTrue("un único cuerpo para los dos contenedores", sheet.contains("val sheetBody: @Composable ColumnScope.() -> Unit"))
    }
}
