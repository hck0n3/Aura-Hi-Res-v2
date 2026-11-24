package iad1tya.echo.music.listentogether

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 818 (owner decision B1, 2026-11-24): Listen Together rooms only on Aura's own server. */
class AuraOnlyRoomsTest {
    private val client =
        ListenTogetherClient(
            clientVersion = "test",
            userAgent = "AuraHiRes/test (JVM)",
            auraRooms = { true },
            elapsedRealtime = { System.nanoTime() / 1_000_000 },
        )
    private val session = ListenTogetherSession(client, tokenStore = null)

    @Test
    fun aMetrolistCodeOrAnOldInviteIsRefusedClearlyBeforeAnySocket() =
        runBlocking {
            session.joinRoom("abcd1234", "me").join()
            assertEquals(RoomCodes.NOT_AURA_CODE_ERROR, session.state.value.error)
            assertNull(session.state.value.pendingJoinCode)
            assertNull("no Aura room was aimed at", client.roomTarget)
            assertFalse(client.isConnected)
        }

    @Test
    fun onlyASixCharacterCodeIsAnAuraRoomsAndAPastedMetrolistCodeIsToldApart() {
        assertTrue(RoomCodes.isAuraCode("ABC234"))
        assertFalse(RoomCodes.isAuraCode("ABCD1234"))
        assertFalse(RoomCodes.isAuraCode("ABC-23"))
        assertFalse(RoomCodes.isAuraCode(""))
        assertTrue(RoomCodes.isSharedServerCode("abcd-1234"))
        assertFalse(RoomCodes.isSharedServerCode("ABC234"))
        assertFalse("typing one character too many is not a pasted code", RoomCodes.isSharedServerCode("ABC2345"))
    }

    @Test
    fun theStoredSwitchIsTurnedOnOnceAndThenLeftAlone() {
        assertTrue(auraRoomsOnlyMigrationNeeded(null))
        assertTrue(auraRoomsOnlyMigrationNeeded(false))
        assertFalse(auraRoomsOnlyMigrationNeeded(true))
    }

    // ── wiring: Android-only code, pinned from the source ──────────────────────────────────────────────────────────────────────

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
    }

    private fun text(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    @Test
    fun theManagerRewritesAStoredOffOnceAndTheSessionChecksEveryJoin() {
        val manager = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherManager.kt")
        assertTrue(manager.contains("if (auraRoomsOnlyMigrationNeeded(prefs[ListenTogetherAuraOnlyAppliedKey])) {"))
        assertTrue(manager.contains("prefs[ListenTogetherAuraRoomsKey] = true"))
        assertTrue(manager.contains("prefs[ListenTogetherAuraOnlyAppliedKey] = true"))
        val keys = text("app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt")
        assertTrue(keys.contains("val ListenTogetherAuraOnlyAppliedKey = booleanPreferencesKey(\"listen_together_aura_only_applied\")"))
        val session = text("app/src/main/kotlin/com/music/echo/listentogether/ListenTogetherSession.kt")
        val join = session.substringAfter("fun joinRoom(").substringBefore("val sent = client.send(")
        assertTrue(join.indexOf("if (client.isAuraRooms && !RoomCodes.isAuraCode(code)) {") in 0 until join.indexOf("client.roomTarget = "))
    }

    @Test
    fun bothScreensSayWhyANonAuraCodeIsRefused() {
        for (screen in listOf("ui/menu/PlayerMenu.kt", "ui/screens/ListenTogetherScreen.kt")) {
            val src = text("app/src/main/kotlin/com/music/echo/$screen")
            assertTrue(screen, src.contains("raw == iad1tya.echo.music.listentogether.RoomCodes.NOT_AURA_CODE_ERROR -> notAuraCodeText"))
            assertTrue(screen, src.contains("iad1tya.echo.music.listentogether.RoomCodes.isSharedServerCode("))
            assertTrue(screen, src.contains("stringResource(R.string.listen_together_not_aura_code)"))
        }
        for (dir in listOf("values", "values-es", "values-es-rUS")) {
            assertTrue(dir, text("app/src/main/res/$dir/echo_strings.xml").contains("<string name=\"listen_together_not_aura_code\">"))
        }
    }
}
