package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry row 795: when the players show «Sin conexión», and when that sign offers «Volver online». */
class OfflinePlaybackNoticeTest {
    @Test
    fun `nothing to say while the player is online`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = false, manual = false)
        assertEquals(OfflinePlaybackNotice.NONE, notice)
        assertFalse(notice.visible)
        assertFalse(notice.offersGoOnline)
    }

    @Test
    fun `the automatic session shows the sign without a button that would do nothing`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = true, manual = false)
        assertEquals(OfflinePlaybackNotice.AUTOMATIC, notice)
        assertTrue(notice.visible)
        assertFalse("the session ends by itself when the network is back", notice.offersGoOnline)
    }

    @Test
    fun `the manual switch shows the sign with its way out`() {
        val notice = OfflinePlaybackNotice.of(sessionActive = false, manual = true)
        assertEquals(OfflinePlaybackNotice.MANUAL, notice)
        assertTrue(notice.visible)
        assertTrue(notice.offersGoOnline)
    }

    @Test
    fun `the manual switch wins over a session running at the same time`() {
        assertEquals(OfflinePlaybackNotice.MANUAL, OfflinePlaybackNotice.of(sessionActive = true, manual = true))
    }

    @Test
    fun `the sign goes away as soon as both states end`() {
        val states = listOf(true to false, true to true, false to true, false to false)
        val shown = states.map { (session, manual) -> OfflinePlaybackNotice.of(session, manual).visible }
        assertEquals(listOf(true, true, true, false), shown)
    }
}
