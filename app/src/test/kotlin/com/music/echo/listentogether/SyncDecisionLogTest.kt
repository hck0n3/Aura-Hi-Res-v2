package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 797: one INFO line per sync decision that changes, repeats throttled, and nothing about the song in it (rule 4). */
class SyncDecisionLogTest {
    @Test
    fun everyChangeOfDecisionIsLogged() {
        val gate = SyncDecisionLogGate()
        assertTrue(gate.shouldLog("hold", 0L))
        assertTrue(gate.shouldLog("trim", 15_000L))
        assertTrue(gate.shouldLog("hold", 30_000L))
        assertTrue(gate.shouldLog("resync", 30_001L))
    }

    @Test
    fun anInSyncHoldIsNeverRepeated() {
        val gate = SyncDecisionLogGate()
        assertTrue(gate.shouldLog("hold", 0L))
        for (beat in 1..20) assertFalse(gate.shouldLog("hold", beat * 15_000L))
    }

    @Test
    fun aRepeatingCorrectionIsLoggedAtMostOncePerWindow() {
        val gate = SyncDecisionLogGate()
        assertTrue(gate.shouldLog("resync", 0L))
        assertFalse(gate.shouldLog("resync", 15_000L))
        assertFalse(gate.shouldLog("resync", SYNC_LOG_REPEAT_MS - 1L))
        assertTrue("a jump loop still shows up", gate.shouldLog("resync", SYNC_LOG_REPEAT_MS))
    }

    @Test
    fun aNewRoomStartsWithItsFirstDecisionLogged() {
        val gate = SyncDecisionLogGate()
        assertTrue(gate.shouldLog("hold", 0L))
        gate.reset()
        assertTrue(gate.shouldLog("hold", 1L))
    }

    @Test
    fun theLabelsAreTheThreeRegimes() {
        assertEquals("hold", syncActionLabel(SyncAction.Hold))
        assertEquals("trim", syncActionLabel(SyncAction.Trim(0.98f)))
        assertEquals("resync", syncActionLabel(SyncAction.Resync(1_000L)))
    }

    @Test
    fun theBridgeLogsOnlyNumbersThroughTheGate() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val bridge = File(root, "app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherPlaybackBridge.kt").readText()
        assertTrue(bridge.contains("if (syncLogGate.shouldLog(label, android.os.SystemClock.elapsedRealtime())) {"))
        val line = bridge.substringAfter("\"SYNC_DECISION ").substringBefore("trimEnd?.cancel()")
        assertTrue(line.startsWith("server=%s drift_ms=%d action=%s speed=%.4f clock=%b anchored=%b\""))
        for (forbidden in listOf("title", "artist", "asLogToken", ".id")) assertFalse("rule 4: $forbidden", line.contains(forbidden))
        assertTrue(bridge.contains("\"SYNC_TRACK server=%s step=reload attempt=%d\", serverKind(), step.attempt)"))
        assertTrue(bridge.contains("\"SYNC_TRACK server=%s step=hold\", serverKind())"))
        assertTrue("leaving the room resets it", bridge.contains("roomReceipt = null\n                    syncLogGate.reset()"))
    }
}
