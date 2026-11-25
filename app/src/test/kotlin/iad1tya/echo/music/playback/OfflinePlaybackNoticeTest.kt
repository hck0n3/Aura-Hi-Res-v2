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
        val notice = OfflinePlaybackNotice.shown(none, deviceOffline = true, fromLoaded = true)
        assertTrue(notice.visible)
        assertFalse("nothing to switch off: it ends by itself when the network is back", notice.offersGoOnline)
    }

    @Test
    fun `the sign goes away when the network comes back`() {
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        assertEquals(OfflinePlaybackNotice.NONE, OfflinePlaybackNotice.shown(none, deviceOffline = false, fromLoaded = true))
    }

    @Test
    fun `the device state never hides or overrides the session or the manual switch`() {
        for (offline in listOf(true, false)) {
            for (fromLoaded in listOf(true, false)) {
                assertEquals(
                    OfflinePlaybackNotice.MANUAL,
                    OfflinePlaybackNotice.shown(OfflinePlaybackNotice.MANUAL, deviceOffline = offline, fromLoaded = fromLoaded),
                )
                assertTrue(OfflinePlaybackNotice.shown(OfflinePlaybackNotice.AUTOMATIC, deviceOffline = offline, fromLoaded = fromLoaded).visible)
            }
        }
    }

    // Row 854: honest wording for the two phases.

    @Test
    fun `while the song from before the cut still plays, the sign says it goes on from what was loaded`() {
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        val notice = OfflinePlaybackNotice.shown(none, deviceOffline = true, fromLoaded = true)
        assertEquals(OfflinePlaybackNotice.BUFFERED, notice)
        assertEquals(OfflinePlaybackNotice.Wording.FROM_LOADED, notice.wording)
        assertTrue(notice.visible)
        assertFalse(notice.offersGoOnline)
        // The session already on (it switched the queue after the current song) says the same while that song plays.
        val session = OfflinePlaybackNotice.of(sessionActive = true, queueParked = false, manual = false)
        assertEquals(OfflinePlaybackNotice.BUFFERED, OfflinePlaybackNotice.shown(session, deviceOffline = true, fromLoaded = true))
        assertEquals(OfflinePlaybackNotice.BUFFERED, OfflinePlaybackNotice.shown(session, deviceOffline = false, fromLoaded = true))
    }

    @Test
    fun `once another song plays, the sign says the offline music is playing`() {
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        val session = OfflinePlaybackNotice.of(sessionActive = true, queueParked = false, manual = false)
        val parked = OfflinePlaybackNotice.of(sessionActive = false, queueParked = true, manual = false)
        for ((base, offline) in listOf(none to true, session to true, session to false, parked to false)) {
            val notice = OfflinePlaybackNotice.shown(base, deviceOffline = offline, fromLoaded = false)
            assertEquals(OfflinePlaybackNotice.AUTOMATIC, notice)
            assertEquals(OfflinePlaybackNotice.Wording.OFFLINE_MUSIC, notice.wording)
        }
    }

    @Test
    fun `the manual switch keeps its own wording and nothing is said while online`() {
        val manual = OfflinePlaybackNotice.shown(OfflinePlaybackNotice.MANUAL, deviceOffline = true, fromLoaded = true)
        assertEquals(OfflinePlaybackNotice.Wording.OFFLINE_MUSIC, manual.wording)
        assertTrue(manual.offersGoOnline)
        assertEquals(null, OfflinePlaybackNotice.shown(OfflinePlaybackNotice.NONE, deviceOffline = false, fromLoaded = true).wording)
        assertEquals(null, OfflinePlaybackNotice.shown(OfflinePlaybackNotice.NONE, deviceOffline = false, fromLoaded = false).wording)
    }

    @Test
    fun `every visible state has a wording`() {
        for (notice in OfflinePlaybackNotice.entries) assertEquals(notice.visible, notice.wording != null)
    }

    // Row 854 (repair): the first phase only for a song really playing when the sign came on.

    @Test
    fun `a paused, idle or restored song is not said to go on from what was loaded`() {
        assertFalse("a cold start with no network: a restored queue, never prepared", OfflinePlaybackNotice.loadedSongGoesOn(playingNow = false, songMovedOn = false))
        val none = OfflinePlaybackNotice.of(sessionActive = false, queueParked = false, manual = false)
        val fromLoaded = OfflinePlaybackNotice.loadedSongGoesOn(playingNow = false, songMovedOn = false)
        assertEquals(OfflinePlaybackNotice.Wording.OFFLINE_MUSIC, OfflinePlaybackNotice.shown(none, deviceOffline = true, fromLoaded = fromLoaded).wording)
    }

    @Test
    fun `a song playing when the sign came on goes on from what was loaded, unless the offline queue already moved on`() {
        assertTrue(OfflinePlaybackNotice.loadedSongGoesOn(playingNow = true, songMovedOn = false))
        assertFalse("the error path entered the session and skipped before the sign came on", OfflinePlaybackNotice.loadedSongGoesOn(playingNow = true, songMovedOn = true))
        assertFalse(OfflinePlaybackNotice.loadedSongGoesOn(playingNow = false, songMovedOn = true))
    }
}
