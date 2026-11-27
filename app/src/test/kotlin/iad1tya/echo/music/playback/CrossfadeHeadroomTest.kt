package iad1tya.echo.music.playback

import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 912: while two players overlap in a crossfade, their sum can never pass 0 dBFS. The fade curves themselves
 * ([CrossfadeMath.getGains]) are untouched; only the shared overlap headroom is new.
 */
class CrossfadeHeadroomTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")

    private val peaks = listOf(CrossfadeMath.LIMITED_PEAK_LINEAR, 1f)

    @Test
    fun `the summed peak of the two players never passes full scale, on any curve, even with the two clocks apart`() {
        for (peak in peaks) for (curve in 0..8) {
            for (i in 0..100) for (o in 0..100) {
                val fadeIn = CrossfadeMath.getGains(curve, i / 100f).first
                val fadeOut = CrossfadeMath.getGains(curve, o / 100f).second
                val h = CrossfadeMath.overlapHeadroom(fadeIn, fadeOut, peak)
                assertTrue("curve $curve in $i out $o", (fadeIn + fadeOut) * h * peak <= 1f + 1e-6f)
                assertTrue(h in 0f..1f)
            }
        }
    }

    @Test
    fun `it is exactly 1 at both ends of every fade, so nothing steps`() {
        for (peak in peaks) for (curve in 0..8) {
            val (in0, out0) = CrossfadeMath.getGains(curve, 0f)
            val (in1, out1) = CrossfadeMath.getGains(curve, 1f)
            assertEquals(1f, CrossfadeMath.overlapHeadroom(in0, out0, peak), 0f)
            assertEquals(1f, CrossfadeMath.overlapHeadroom(in1, out1, peak), 0f)
            var prev = 1f
            for (k in 0..1000) {
                val (a, b) = CrossfadeMath.getGains(curve, k / 1000f)
                val h = CrossfadeMath.overlapHeadroom(a, b, peak)
                assertTrue("curve $curve step $k", abs(h - prev) < 0.01f)
                prev = h
            }
        }
    }

    @Test
    fun `curves whose gains add up to 1 never dip, equal-power ones dip at most 2 dB behind the limiter`() {
        for (curve in listOf(0, 3, 5, 7, 8)) for (k in 0..100) {
            val (a, b) = CrossfadeMath.getGains(curve, k / 100f)
            assertEquals("curve $curve", 1f, CrossfadeMath.overlapHeadroom(a, b, CrossfadeMath.LIMITED_PEAK_LINEAR), 0f)
        }
        for (curve in listOf(1, 2, 4)) {
            val dip = (0..100).minOf { k ->
                val (a, b) = CrossfadeMath.getGains(curve, k / 100f)
                CrossfadeMath.overlapHeadroom(a, b, CrossfadeMath.LIMITED_PEAK_LINEAR)
            }
            val dipDb = 20 * log10(dip.toDouble())
            assertTrue("curve $curve dips $dipDb dB", dipDb >= -2.05 && dipDb <= -1.9)
        }
        assertEquals(-1.0, 20 * log10(CrossfadeMath.LIMITED_PEAK_LINEAR.toDouble()), 1e-4)
    }

    @Test
    fun `the fade loop applies it to both players`() {
        val xf = File(root, "app/src/main/kotlin/com/music/echo/playback/CrossfadeEngine.kt").readText()
        assertTrue(xf.contains("val xfPeak = if (safeVolumeEnabledHint) CrossfadeMath.LIMITED_PEAK_LINEAR else 1f"))
        val loop = xf.substringAfter("val fadeOut = crossfadeGains(curve, outP).second")
        assertTrue(loop.trimStart().startsWith("val xfHeadroom = CrossfadeMath.overlapHeadroom(fadeIn, fadeOut, xfPeak)"))
        assertTrue(xf.contains("player.volume = startVolume * fadeIn * xfHeadroom * sleepFadeGain"))
        assertTrue(xf.contains("fp?.volume = startVolume * fadeOut * xfHeadroom * sleepFadeGain"))
        assertTrue("the old fixed 1.0 is gone", !xf.contains("val xfHeadroom = 1f"))
    }
}
