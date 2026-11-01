package iad1tya.echo.music.listentogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Registry row 601 — with Aura's own rooms the Join button must enable at 6 characters, not 8. */
class RoomCodesTest {
    @Test
    fun `an Aura code is complete at six characters and a shared one at eight`() {
        assertTrue(RoomCodes.isComplete("ABC234", auraRooms = true))
        assertFalse(RoomCodes.isComplete("ABC234", auraRooms = false))
        assertTrue(RoomCodes.isComplete("ABCD1234", auraRooms = false))
        assertFalse(RoomCodes.isComplete("ABCD1234", auraRooms = true))
        assertFalse(RoomCodes.isComplete("ABC23", auraRooms = true))
    }

    @Test
    fun `typing is cleaned and cut to the rooms in use`() {
        assertEquals("ABC234", RoomCodes.normalize("abc-234zz", auraRooms = true))
        assertEquals("ABC234ZZ", RoomCodes.normalize("abc-234zz", auraRooms = false))
    }

    @Test
    fun `the length matches what the Worker accepts and both screens use the helper`() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }
        val worker = File(root, "docs/worker/rooms/src/index.js").readText()
        assertTrue("the Worker must still ask for ${RoomCodes.AURA_LENGTH}-character codes", worker.contains("code.length !== ${RoomCodes.AURA_LENGTH}"))
        for (screen in listOf("ui/screens/ListenTogetherScreen.kt", "ui/menu/PlayerMenu.kt")) {
            val src = File(root, "app/src/main/kotlin/com/music/echo/$screen").readText()
            assertTrue("$screen must use RoomCodes", src.contains("RoomCodes.isComplete("))
            assertFalse("$screen must not hard-code 8 characters", src.contains("roomCodeInput.length == 8"))
        }
    }
}
