package iad1tya.echo.music.playback

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry 764 (AGENTS rule 7): «Cola persistente» is read on the main thread and in the playing tick, so it never goes
 * through the blocking runBlocking DataStore read. Player callbacks and the tick read the [MusicService] mirror
 * `persistentQueueHint`; onCreate, shutdown/reboot and onDestroy read the exact process snapshot (getNonBlocking).
 */
class PersistentQueueReadGuardTest {
    private val service: String by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
    }

    @Test
    fun `no blocking read of the persistent queue flag is left`() {
        assertFalse(service.contains("dataStore.get(PersistentQueueKey"))
        assertFalse(service.contains("dataStore[PersistentQueueKey]"))
    }

    @Test
    fun `the non-blocking reads are only the documented ones`() {
        // 30 s save tick, its position twin, onCreate restore, shutdown/reboot receiver, onDestroy.
        assertEquals(5, Regex("dataStore\\.getNonBlocking\\(PersistentQueueKey, true\\)").findAll(service).count())
        assertTrue(service.contains("persistentQueueHint = prefs[PersistentQueueKey] ?: true"))
        assertTrue(service.contains("if (tick % 2 == 0 && persistentQueueHint) {"))
        assertTrue(service.contains("if (persistentQueueHint && !isSilenceSkipping) {"))
    }

    @Test
    fun `the audio device callback does not block the main thread`() {
        assertTrue(service.contains("val settingResume = dataStore.getNonBlocking(ResumeOnBluetoothConnectKey, false)"))
        assertFalse(service.contains("dataStore.get(ResumeOnBluetoothConnectKey"))
    }
}
