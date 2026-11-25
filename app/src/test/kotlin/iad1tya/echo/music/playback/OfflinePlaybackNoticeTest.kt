package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 795: when the players show «Sin conexión», and when that sign offers «Volver online». */
class OfflinePlaybackNoticeTest {
    @Test
    fun `nothing to say while the player is online`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        assertEquals(OfflinePlaybackNotice.NONE, notice)
        assertFalse(notice.visible)
        assertFalse(notice.offersGoOnline)
    }

    @Test
    fun `the automatic session shows the sign without a button that would do nothing`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = true, queueParked = false, manual = false)
        assertEquals(OfflinePlaybackNotice.AUTOMATIC, notice)
        assertTrue(notice.visible)
        assertFalse("the session ends by itself when the network is back", notice.offersGoOnline)
    }

    @Test
    fun `the manual switch shows the sign with its way out`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = true)
        assertEquals(OfflinePlaybackNotice.MANUAL, notice)
        assertTrue(notice.visible)
        assertTrue(notice.offersGoOnline)
    }

    @Test
    fun `the manual switch wins over a session running at the same time`() {
        assertEquals(OfflinePlaybackNotice.MANUAL, OfflinePlaybackNotice.of(sessionActive = true, queueParked = false, manual = true))
    }

    @Test
    fun `the sign goes away as soon as both states end`() {
        val states = listOf(true to false, true to true, false to true, false to false)
        val shown = states.map { (session, manual) -> OfflinePlaybackNotice.of(session, queueParked = false, manual = manual).visible }
        assertEquals(listOf(true, true, true, false), shown)
    }

    @Test
    fun `the queue parked after the session timed out keeps the sign, as it keeps playing offline music only`() {
        // Row 717: Wi-Fi with no internet, the session expires and the offline queue stays parked (offlineQueueModeNow is true).
        val notice = OfflinePlaybackNotice.of(sessionActive = false, queueParked = true, manual = false)
        assertEquals(OfflinePlaybackNotice.AUTOMATIC, notice)
        assertTrue(notice.visible)
        assertFalse("it ends by itself on the first real proof the network works", notice.offersGoOnline)
        assertEquals(OfflinePlaybackNotice.MANUAL, OfflinePlaybackNotice.of(sessionActive = false, queueParked = true, manual = true))
    }

    // Row 853 (owner test #17, beta8): airplane mode on while an online song played, no sign after 25 s.

    @Test
    fun `the device is offline only with no transport, no validated network and the automatic mode on`() {
        assertTrue(OfflinePlaybackNotice.deviceOffline(online = false, validated = false, autoOffline = true))
        assertFalse("a VPN / IPv6-only / signed-in portal keeps a transport", OfflinePlaybackNotice.deviceOffline(online = true, validated = false, autoOffline = true))
        assertFalse(OfflinePlaybackNotice.deviceOffline(online = true, validated = true, autoOffline = true))
        assertFalse(OfflinePlaybackNotice.deviceOffline(online = false, validated = true, autoOffline = true))
        assertFalse("with «que se ponga solo» off the player never goes offline by itself", OfflinePlaybackNotice.deviceOffline(online = false, validated = false, autoOffline = false))
    }

    @Test
    fun `the sign is on as soon as the device is offline, without waiting for the player's session`() {
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        val notice = OfflinePlaybackNotice.shown(none, deviceOffline = true)
        assertTrue(notice.visible)
        assertFalse("nothing to switch off: it ends by itself when the network is back", notice.offersGoOnline)
    }

    @Test
    fun `the sign goes away when the network comes back`() {
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        assertEquals(OfflinePlaybackNotice.NONE, OfflinePlaybackNotice.shown(none, deviceOffline = false))
    }

    @Test
    fun `the device state never hides or overrides the session or the manual switch`() {
        for (offline in listOf(true, false)) {
            assertEquals(OfflinePlaybackNotice.MANUAL, OfflinePlaybackNotice.shown(OfflinePlaybackNotice.MANUAL, deviceOffline = offline))
            assertTrue(OfflinePlaybackNotice.shown(OfflinePlaybackNotice.AUTOMATIC, deviceOffline = offline).visible)
        }
    }
}
