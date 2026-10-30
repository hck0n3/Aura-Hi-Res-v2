package iad1tya.echo.music.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 540 — a device's identity in the sync room comes from its hardware, so a copied backup can never make two devices one. */
class DeviceIdentityTest {
    @Test
    fun `the same hardware always gives the same id, different hardware gives different ids`() {
        val phone = DeviceIdentity.fromHardware("a1b2c3d4e5f60718")
        val tablet = DeviceIdentity.fromHardware("0f9e8d7c6b5a4938")
        assertEquals(phone, DeviceIdentity.fromHardware(" a1b2c3d4e5f60718 "))
        assertNotEquals(phone, tablet)
    }

    @Test
    fun `the id is accepted by the room and does not reveal the hardware id`() {
        val id = DeviceIdentity.fromHardware("a1b2c3d4e5f60718")!!
        assertTrue(Regex("[A-Za-z0-9_-]{8,64}").matches(id))
        assertEquals(20, id.length)
        assertTrue(!id.contains("a1b2c3d4e5f60718"))
    }

    @Test
    fun `an unusable hardware id gives no identity (the caller keeps a random one)`() {
        assertNull(DeviceIdentity.fromHardware(null))
        assertNull(DeviceIdentity.fromHardware(""))
        assertNull(DeviceIdentity.fromHardware("123"))
        assertNull(DeviceIdentity.fromHardware("9774d56d682e549c"))
    }

    @Test
    fun `two devices tell themselves apart by the last four characters`() {
        assertEquals("cdef", DeviceIdentity.shortTag("dabcdef"))
    }
}
