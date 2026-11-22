package iad1tya.echo.music.devicesync

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner 2026-11-22 («vuelvo a la app y el 1 pierde el control; el mini reproductor muestra lo de aquí»): a short drop of the link no longer
 * clears the remote at once; it stays shown for [RemotePlayback.LINK_DROP_HOLD_MS] and only a link that stays down clears it. Switching
 * device sync off (or re-configuring it) still clears it immediately.
 */
class RemoteHoldThroughDropGuardTest {
    private val engine: String = run {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        File(root, "app/src/main/kotlin/com/music/echo/devicesync/DeviceSyncEngine.kt").readText()
    }

    @Test
    fun `the hold is short and longer than the first reconnect`() {
        assertTrue(RemotePlayback.LINK_DROP_HOLD_MS in 5_000L..RemotePlayback.FRESH_MS)
    }

    @Test
    fun `a drop holds, a reconnect keeps, switching off clears`() {
        val collect = engine.substringAfter("client.state.collect {").substringBefore("scope.launch { client.messages")
        assertFalse("no instant clear on every non-connected state", collect.contains("if (it !is DeviceSyncHub.Connection.Connected) {\n                    library?.onDisconnected()\n                    RemotePlayback.update(null, 0L)"))
        assertTrue(collect.contains("else -> holdRemoteThroughDrop()"))
        val off = collect.substringAfter("DeviceSyncHub.Connection.Off -> {").substringBefore("else ->")
        assertTrue(off.contains("RemotePlayback.update(null, 0L)"))
        val connected = collect.substringAfter("DeviceSyncHub.Connection.Connected -> {").substringBefore("DeviceSyncHub.Connection.Off ->")
        assertTrue(connected.contains("dropHold?.cancel()"))
        assertTrue("the library still learns the link went down", collect.contains("library?.onDisconnected()"))
    }

    @Test
    fun `only a link that stays down clears the remote`() {
        val hold = engine.substringAfter("private fun holdRemoteThroughDrop() {").substringBefore("fun stop()")
        assertTrue(hold.contains("if (!RemotePlayback.active || dropHold?.isActive == true) return"))
        assertTrue(hold.contains("delay(RemotePlayback.LINK_DROP_HOLD_MS)"))
        assertTrue(hold.contains("if (DeviceSyncHub.connection.value !is DeviceSyncHub.Connection.Connected && RemotePlayback.active) {"))
    }
}
