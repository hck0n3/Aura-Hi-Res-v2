package iad1tya.echo.music.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 500 (owner 2026-10-21): the full-screen player's ⋯ menu and the audio-output window use the glass slabs of
 * the song ⋯ menu, and nothing they offered went away.
 */
class PlayerAndAudioSheetGlassGuardTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    }

    private fun main(path: String) =
        File(root, "app/src/main/kotlin/com/music/echo/$path").also { assertTrue("$path se movió", it.isFile) }.readText()

    @Test
    fun `the player menu is glass slabs and keeps every row`() {
        val s = main("ui/newui/AuraPlayerMenu.kt")
        assertTrue("las fijas, en una losa", s.contains("AuraGlassSlab(modifier = Modifier.padding(horizontal = AuraSpacing.Gutter)) {\n                SlabRowsColumn {"))
        assertTrue("cada acordeón es su losa", s.contains("AuraGlassRow(\n            title = title,\n            onClick = onToggle,"))
        assertTrue(s.contains("AuraGlassDivider(inset = 56.dp)"))
        assertFalse("ya no hay filas sueltas", s.contains("AuraMenuRow("))
        listOf(
            "R.string.view_artist", "R.string.view_album", "R.string.equalizer", "\"Sonido\"", "R.string.details", "R.string.settings",
            "R.string.shuffle", "R.string.repeat", "R.string.start_radio", "R.string.advanced", "R.string.sleep_timer", "R.string.refetch",
            "R.string.add_to_library", "R.string.add_to_playlist", "R.string.action_export", "R.string.set_as_ringtone",
            "R.string.go_to_podcast", "\"Vídeo\"", "R.string.audio_devices", "R.string.listen_together", "R.string.resync",
            "R.string.ambient_mode", "R.string.share", "SECTION_PLAYBACK", "SECTION_LIBRARY", "SECTION_MORE",
        ).forEach { assertTrue(it, s.contains(it)) }
    }

    @Test
    fun `the audio output window wears the glass slabs on the premium skin and keeps the Material body otherwise`() {
        val s = main("echomusic/AudioDeviceBottomSheet.kt")
        assertTrue(s.contains("premiumSheet -> GlassAudioDeviceBody("))
        assertTrue("el cuerpo Material sigue para la piel clásica", s.contains("MaterialTheme.colorScheme.primaryContainer"))
        val glass = s.substringAfter("private fun GlassAudioDeviceBody(").substringBefore("fun VolumeControlRow(")
        assertTrue(glass.contains("AuraGlassSlab {"))
        assertTrue("elegir salida sigue siendo la misma acción", s.contains("service?.setPreferredAudioDevice(dev.deviceId)"))
        listOf("R.string.audio_device_connected", "R.string.this_phone", "R.string.volume", "R.string.done", "CircularWavyProgressIndicator", "onDone")
            .forEach { assertTrue(it, s.contains(it)) }
    }

    @Test
    fun `the vector row primitive exists`() {
        assertTrue(main("ui/newui/AuraGlassSlab.kt").contains("internal fun AuraGlassVectorRow("))
    }
}
