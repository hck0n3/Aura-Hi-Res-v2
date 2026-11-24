package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 795 (owner 2026-11-24, beta3: «quiero que el reproductor esté con un aviso que está continuando en el modo sin
 * conexión»). The full player and the mini player both draw the «Sin conexión» sign from the SERVICE's flow (no polling, rule
 * 7), and the flow never writes the manual switch (row 284). The state→visibility rules are in `playback/OfflinePlaybackNoticeTest`.
 */
class OfflinePlayerNoticeGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun src(path: String): String =
        File(root, path).also { assertTrue("$path se movió — actualiza este test", it.isFile) }.readText()

    private fun main(path: String) = src("app/src/main/kotlin/com/music/echo/$path")

    @Test
    fun `the service feeds the sign from the offline session and the manual switch, by events`() {
        val service = main("playback/MusicService.kt")
        assertTrue(service.contains("private val _offlinePlaybackNotice = MutableStateFlow(OfflinePlaybackNotice.NONE)"))
        assertTrue(
            service.contains(
                "val offlinePlaybackNotice: kotlinx.coroutines.flow.StateFlow<OfflinePlaybackNotice> get() = _offlinePlaybackNotice",
            ),
        )
        val marker = "// Row 795: the players' «Sin conexión» sign follows the session and the manual switch as they change."
        assertTrue(service.contains(marker))
        val feed = service.substringAfter(marker).substringBefore(".collect { _offlinePlaybackNotice.value = it }")
        assertTrue(feed.contains("PlaybackOfflineSession.active,"))
        assertTrue(feed.contains("dataStore.data.map { it[OfflineModeKey] ?: false }.distinctUntilChanged(),"))
        assertTrue(feed.contains("OfflinePlaybackNotice.of(sessionActive = session, manual = manual)"))
        assertFalse("no polling", feed.contains("delay("))
        assertFalse("reads the manual switch, never writes it (row 284)", feed.contains("edit"))
        assertEquals("one place writes the sign", 1, Regex("""_offlinePlaybackNotice\.value =""").findAll(service).count())
    }

    @Test
    fun `the full player and the mini player both render the sign from the service flow`() {
        val player = main("ui/newui/AuraPlayer.kt")
        val sleepChip = player.indexOf("AuraSleepTimerChip(visible = state.isExpanded)")
        val offlineChip = player.indexOf("AuraOfflinePlaybackChip()")
        assertTrue("next to the sleep-timer chip in the header", sleepChip >= 0 && offlineChip > sleepChip)
        assertTrue(offlineChip < player.indexOf("val artworkContent: @Composable (Modifier) -> Unit"))

        val chip = main("ui/newui/AuraOfflinePlaybackNotice.kt")
        val full = chip.substringAfter("internal fun AuraOfflinePlaybackChip(").substringBefore("internal fun AuraMiniOfflineBadge(")
        assertTrue(full.contains("val notice by service.offlinePlaybackNotice.collectAsState()"))
        assertTrue(full.contains("if (!notice.visible) return"))
        assertTrue("a tap opens the explanation", full.contains(".unstealableClick(open)"))
        assertTrue(full.contains("stringResource(R.string.offline_player_chip)"))

        val shell = main("ui/newui/AuraShell.kt")
        val mini = shell.substringAfter("fun AuraMiniPlayer(").substringBefore("internal fun Modifier.unstealableClick(")
        assertTrue(mini.contains("val offlineNotice by playerConnection.service.offlinePlaybackNotice.collectAsState()"))
        assertTrue(mini.contains("if (artists.isNotBlank() || explicit || offlineNotice.visible) {"))
        assertTrue(mini.contains("AuraMiniOfflineBadge(offlineNotice)"))
        val badge = chip.substringAfter("internal fun AuraMiniOfflineBadge(").substringBefore("private fun AuraOfflinePlaybackDialog(")
        assertTrue(badge.contains("if (!notice.visible) return"))
        assertTrue(badge.contains("stringResource(R.string.offline_player_mini)"))

        for (text in listOf(full, badge)) {
            assertFalse("rule 7: nothing ticks", text.contains("delay(") || text.contains("while (true)"))
        }
    }

    @Test
    fun `only the manual switch gets a way out, and it is the user's own switch`() {
        val chip = main("ui/newui/AuraOfflinePlaybackNotice.kt")
        val dialog = chip.substringAfter("private fun AuraOfflinePlaybackDialog(")
        assertTrue(dialog.contains("if (notice.offersGoOnline) {"))
        assertTrue(dialog.contains("offlineMode = false"))
        assertTrue(dialog.contains("R.string.offline_player_notice_manual else R.string.offline_player_notice_auto"))
        assertFalse("the automatic session is never ended from here", dialog.contains("PlaybackOfflineSession"))
        assertFalse("row 284: the session never writes the switch", main("playback/PlaybackOfflineSession.kt").contains("dataStore"))
    }

    @Test
    fun `the sign is translated`() {
        val keys = listOf(
            "offline_player_chip", "offline_player_chip_cd", "offline_player_mini", "offline_player_notice_title",
            "offline_player_notice_auto", "offline_player_notice_manual",
        )
        for (dir in listOf("values", "values-es")) {
            val strings = src("app/src/main/res/$dir/offline_player_notice_strings.xml")
            for (key in keys) assertTrue("$dir: $key", strings.contains("<string name=\"$key\">"))
        }
    }
}
