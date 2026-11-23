package iad1tya.echo.music.listentogether

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 744 (P1-12): «the room survives process death» was a placebo — the token was persisted, but the client only ever replayed the
 * one in its memory, which a new process does not have. The manager now hands the persisted token (and, for Aura's rooms, the room's
 * code) to the client before it connects.
 */
class ResumeAfterProcessDeathTest {
    private fun client(aura: Boolean) =
        ListenTogetherClient(
            clientVersion = "test",
            userAgent = "AuraHiRes/test (JVM)",
            auraRooms = { aura },
            elapsedRealtime = { System.nanoTime() / 1_000_000 },
        )

    private fun tokenOf(c: ListenTogetherClient): String? {
        val field = ListenTogetherClient::class.java.getDeclaredField("sessionToken")
        field.isAccessible = true
        return field.get(c) as String?
    }

    private fun confirmedOf(c: ListenTogetherClient): Boolean {
        val field = ListenTogetherClient::class.java.getDeclaredField("tokenConfirmed")
        field.isAccessible = true
        return field.get(c) as Boolean
    }

    @Test
    fun aPersistedTokenIsNotConfirmedUntilTheServerTakesItBack() {
        // Unconfirmed, a dropped socket gets the 5-attempt budget, not the room's 10-minute window: the room may be long gone.
        val c = client(aura = true)
        c.resumeWith("u1.secret", "ABC123")
        assertFalse(confirmedOf(c))
        c.forgetSessionToken()
        assertFalse(confirmedOf(c))
    }

    @Test
    fun theSharedServerResumesWithThePersistedToken() {
        val c = client(aura = false)
        c.resumeWith("u1.secret", "ROOM1")
        assertEquals("u1.secret", tokenOf(c))
        assertNull("a Metrolist-compatible server has one lobby socket", c.roomTarget)
    }

    @Test
    fun auraRoomsAlsoGoBackToTheRoomsOwnSocket() {
        val c = client(aura = true)
        c.resumeWith("u1.secret", " abc12 ")
        assertEquals("u1.secret", tokenOf(c))
        assertEquals(ListenTogetherClient.RoomTarget.Join("ABC12"), c.roomTarget)
    }

    @Test
    fun auraRoomsWithoutTheRoomCodeDoNotReplayTheToken() {
        val c = client(aura = true)
        c.resumeWith("u1.secret", "")
        assertNull(tokenOf(c))
        assertNull(c.roomTarget)
    }

    @Test
    fun aTokenThisProcessAlreadyHoldsIsNotReplaced() {
        val c = client(aura = false)
        c.resumeWith("first", "ROOM1")
        c.resumeWith("older", "ROOM0")
        assertEquals("first", tokenOf(c))
        c.forgetSessionToken()
        assertNull(tokenOf(c))
    }

    @Test
    fun theManagerHandsThePersistedTokenOverBeforeConnecting() {
        val root =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
        val manager = File(root, "app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherManager.kt").readText()
        val resume = manager.substringAfter("fun connectIfResumable() {").substringBefore("fun disconnect()")
        assertTrue(resume.contains("client.resumeWith(token, context.dataStore.get(ListenTogetherRoomCodeKey, \"\"))"))
        assertTrue("the token is handed over first", resume.indexOf("client.resumeWith(") < resume.indexOf("session.connect()"))
    }
}
