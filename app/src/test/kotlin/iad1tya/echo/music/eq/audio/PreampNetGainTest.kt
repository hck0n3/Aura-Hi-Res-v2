package iad1tya.echo.music.eq.audio

import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 639 (owner 2026-11-02: raising the preamp barely raised the volume). The REAL volume change of a preamp step is the step
 * itself plus whatever Safe Volume gives back. Pins the numbers so the rule cannot silently drift back to «takes 75 % away».
 */
class PreampNetGainTest {
    private fun db(linear: Float): Double = 20.0 * log10(linear.toDouble())

    /** Net dB of raising the preamp from 0 to [preampDb] on a song whose Safe Volume base gain is [base]. */
    private fun netDb(base: Float, preampDb: Double): Double =
        preampDb + db(safeVolumeGainWithEqPreamp(base, preampDb)) - db(safeVolumeGainWithEqPreamp(base, 0.0))

    @Test
    fun quietSongsGetAtLeastTwoThirdsOfEveryDbTheOwnerAdds() {
        for (base in listOf(1.2f, 1.6f, 2.0f, 3.0f)) {
            for (p in listOf(3.0, 6.0, 9.0, 12.0)) {
                val net = netDb(base, p)
                assertTrue("base=$base preamp=$p net=$net", net >= 0.65 * p)
                assertTrue("never more than the step itself: base=$base preamp=$p net=$net", net <= p + 1e-6)
            }
        }
    }

    @Test
    fun sixDbOfPreampIsAtLeastFourDbOfRealVolume() {
        assertTrue(netDb(1.6f, 6.0) >= 4.0)
    }

    @Test
    fun loudSongsKeepTheFullStep() {
        for (base in listOf(0.5f, 0.8f, 1.0f)) assertEquals(6.0, netDb(base, 6.0), 1e-6)
    }

    @Test
    fun theOldFactorWouldHaveFailedTheQuietSongCase() {
        // The regression this guards: with 0.75 the net of +6 dB was only 1.5 dB.
        val old = 6.0 + db(safeVolumeGainWithEqPreamp(1.6f, 6.0, 0.75)) - db(1.6f)
        assertTrue(old < 2.0)
        assertTrue(netDb(1.6f, 6.0) > old + 2.0)
    }
}
