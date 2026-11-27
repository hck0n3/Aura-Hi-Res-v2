package iad1tya.echo.music.eq.autoeq

import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.DeviceCorrection
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_ACTIVE
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_BANDS
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_MASTER_PREAMP
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_OWNER
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_PREAMP
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_PREAMP_BEFORE
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_STASH_ACTIVE
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_STASH_BANDS
import iad1tya.echo.music.eq.autoeq.AutoEqDeviceSwitch.KEY_STASH_PREAMP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 915: the per-device switch. `echo_eq_prefs` is modelled as a map; every output change runs
 * [AutoEqDeviceSwitch.plan] and applies its edits the way AutoEqDeviceCorrection.sync does.
 */
class AutoEqDeviceSwitchTest {
    private val headphones = "bt:WH-1000XM4"
    private val buds = "bt:Galaxy Buds2 Pro"
    private val phone = "__phone__"
    private val xm4 = DeviceCorrection(bandsJson = "[xm4]", preampDb = -6.0f)
    private val buds2 = DeviceCorrection(bandsJson = "[buds]", preampDb = -3.0f)

    private val prefs = mutableMapOf<String, Any?>(KEY_MASTER_PREAMP to 2.5f)

    private fun state() = AutoEqDeviceSwitch.State(
        active = prefs[KEY_ACTIVE] as Boolean? ?: false,
        bandsJson = prefs[KEY_BANDS] as String?,
        autoPreamp = prefs[KEY_PREAMP] as Float?,
        preampBefore = prefs[KEY_PREAMP_BEFORE] as Float?,
        masterPreamp = prefs[KEY_MASTER_PREAMP] as Float,
        owner = prefs[KEY_OWNER] as String?,
        stashBandsJson = prefs[KEY_STASH_BANDS] as String?,
        stashActive = prefs[KEY_STASH_ACTIVE] as Boolean?,
        stashPreamp = prefs[KEY_STASH_PREAMP] as Float?,
    )

    /** One output change: returns whether anything was written. */
    private fun output(key: String, correction: DeviceCorrection?, force: Boolean = false): Boolean {
        val edits = AutoEqDeviceSwitch.plan(state(), key, correction, force)
        edits.forEach { (k, v) -> if (v == null) prefs.remove(k) else prefs[k] = v }
        return edits.isNotEmpty()
    }

    @Test fun `the headphone's correction comes on with it and goes away with any other output`() {
        assertTrue(output(headphones, xm4))
        assertEquals(true, prefs[KEY_ACTIVE])
        assertEquals("[xm4]", prefs[KEY_BANDS])
        assertEquals(-6.0f, prefs[KEY_MASTER_PREAMP])
        assertEquals(2.5f, prefs[KEY_PREAMP_BEFORE]) // row 802: the user's preamp, kept for the way back
        assertEquals(headphones, prefs[KEY_OWNER])

        assertTrue(output(phone, null))
        assertEquals(false, prefs[KEY_ACTIVE])
        assertNull(prefs[KEY_BANDS])
        assertEquals(2.5f, prefs[KEY_MASTER_PREAMP])
        assertFalse(prefs.containsKey(KEY_PREAMP_BEFORE))
        AutoEqDeviceSwitch.DEVICE_KEYS.forEach { assertFalse(it, prefs.containsKey(it)) }
    }

    @Test fun `the user's own Auto-EQ pick is kept aside and comes back`() {
        prefs += mapOf(KEY_ACTIVE to true, KEY_BANDS to "[hd650]", KEY_PREAMP to -6.1f, KEY_MASTER_PREAMP to -6.1f, KEY_PREAMP_BEFORE to 2.5f)
        output(headphones, xm4)
        assertEquals("[xm4]", prefs[KEY_BANDS])
        assertEquals(2.5f, prefs[KEY_PREAMP_BEFORE]) // already ON: the user's preamp stays the one from before
        output(phone, null)
        assertEquals(true, prefs[KEY_ACTIVE])
        assertEquals("[hd650]", prefs[KEY_BANDS])
        assertEquals(-6.1f, prefs[KEY_PREAMP])
        assertEquals(-6.1f, prefs[KEY_MASTER_PREAMP])
        assertEquals(2.5f, prefs[KEY_PREAMP_BEFORE])
        assertNull(prefs[KEY_OWNER])
    }

    @Test fun `a stage the user chose is never touched by an output without a correction`() {
        prefs += mapOf(KEY_ACTIVE to true, KEY_BANDS to "[hd650]", KEY_MASTER_PREAMP to -6.1f)
        val before = prefs.toMap()
        assertFalse(output(phone, null))
        assertFalse(output("bt:Car Kit", null))
        assertEquals(before, prefs)
    }

    @Test fun `the same headphone again changes nothing unless its settings changed`() {
        output(headphones, xm4)
        val snapshot = prefs.toMap()
        assertFalse(output(headphones, xm4))
        assertEquals(snapshot, prefs)
        // A new boost cap re-writes the correction while it is on.
        assertTrue(output(headphones, xm4.copy(bandsJson = "[xm4 capped]", preampDb = -3f), force = true))
        assertEquals("[xm4 capped]", prefs[KEY_BANDS])
        assertEquals(-3f, prefs[KEY_MASTER_PREAMP])
        assertEquals(2.5f, prefs[KEY_PREAMP_BEFORE])
    }

    @Test fun `switched off by hand it stays off while that headphone is connected`() {
        output(headphones, xm4)
        // AxionEqViewModel.disableAutoEq: restores the preamp (row 802) and turns the stage off.
        prefs[KEY_MASTER_PREAMP] = prefs.remove(KEY_PREAMP_BEFORE)
        prefs[KEY_ACTIVE] = false
        assertFalse(output(headphones, xm4))
        assertFalse(output(headphones, xm4, force = true))
        assertEquals(false, prefs[KEY_ACTIVE])
        // Leaving it: nothing to restore in the preamp, the device's marks go away.
        output(phone, null)
        assertEquals(2.5f, prefs[KEY_MASTER_PREAMP])
        assertNull(prefs[KEY_OWNER])
    }

    @Test fun `removed with «Quitar Auto-EQ» it stays removed until the headphone connects again`() {
        prefs += mapOf(KEY_ACTIVE to true, KEY_BANDS to "[hd650]", KEY_PREAMP to -6.1f, KEY_MASTER_PREAMP to -6.1f, KEY_PREAMP_BEFORE to 2.5f)
        output(headphones, xm4)
        // AxionEqViewModel.clearAutoEq: preamp back (row 802), bands gone, stage off; forgetDeviceOwnership(keepOwner = true).
        prefs[KEY_MASTER_PREAMP] = prefs.remove(KEY_PREAMP_BEFORE)
        prefs.remove(KEY_BANDS)
        prefs[KEY_ACTIVE] = false
        AutoEqDeviceSwitch.DEVICE_KEYS.filter { it != KEY_OWNER }.forEach { prefs.remove(it) }
        assertFalse(output(headphones, xm4))
        output(phone, null)
        assertEquals(false, prefs[KEY_ACTIVE])
        assertNull(prefs[KEY_BANDS]) // the old pick the user removed does not come back
        assertEquals(2.5f, prefs[KEY_MASTER_PREAMP])
        assertNull(prefs[KEY_OWNER])
        // Next time the headphone connects, its correction is offered to the stage again.
        assertTrue(output(headphones, xm4))
        assertEquals(true, prefs[KEY_ACTIVE])
    }

    @Test fun `from one headphone straight to another keeps what the user had`() {
        prefs += mapOf(KEY_ACTIVE to false, KEY_BANDS to "[hd650]", KEY_PREAMP to -6.1f)
        output(headphones, xm4)
        output(buds, buds2)
        assertEquals("[buds]", prefs[KEY_BANDS])
        assertEquals(buds, prefs[KEY_OWNER])
        assertEquals(-3.0f, prefs[KEY_MASTER_PREAMP])
        output(phone, null)
        assertEquals(false, prefs[KEY_ACTIVE])
        assertEquals("[hd650]", prefs[KEY_BANDS]) // the user's pick, still there to switch on by hand
        assertEquals(-6.1f, prefs[KEY_PREAMP])
        assertEquals(2.5f, prefs[KEY_MASTER_PREAMP])
    }

    @Test fun `removing the headphone's correction while it plays brings the user's state back`() {
        output(headphones, xm4)
        assertTrue(output(headphones, null)) // «Quitar» in Auto-EQ ▸ Este auricular
        assertEquals(false, prefs[KEY_ACTIVE])
        assertEquals(2.5f, prefs[KEY_MASTER_PREAMP])
    }

    @Test fun `the user's pick switched on again after the device turned it off by hand`() {
        prefs += mapOf(KEY_ACTIVE to true, KEY_BANDS to "[hd650]", KEY_PREAMP to -6.1f, KEY_MASTER_PREAMP to -6.1f, KEY_PREAMP_BEFORE to 2.5f)
        output(headphones, xm4)
        prefs[KEY_MASTER_PREAMP] = prefs.remove(KEY_PREAMP_BEFORE) // disableAutoEq while on the headphone
        prefs[KEY_ACTIVE] = false
        output(phone, null)
        assertEquals(true, prefs[KEY_ACTIVE])
        assertEquals(-6.1f, prefs[KEY_MASTER_PREAMP])
        // Switching the user's pick off later must give back the preamp they were on (2.5).
        assertEquals(2.5f, prefs[KEY_PREAMP_BEFORE])
    }
}
