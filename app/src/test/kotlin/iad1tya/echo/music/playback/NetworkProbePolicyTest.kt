package iad1tya.echo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row 644: a network that says «internet» but did not validate is checked once with DNS; a validated one is never probed. */
class NetworkProbePolicyTest {
    @Test
    fun aValidatedNetworkIsUsableAndNeverProbed() {
        var probes = 0
        assertTrue(NetworkProbePolicy.usable(hasInternetCapability = true, validated = true) { probes++; false })
        assertEquals(0, probes)
    }

    @Test
    fun noInternetCapabilityIsUnusableWithoutProbing() {
        var probes = 0
        assertFalse(NetworkProbePolicy.usable(hasInternetCapability = false, validated = false) { probes++; true })
        assertEquals(0, probes)
    }

    @Test
    fun anUnvalidatedNetworkIsDecidedByTheDnsProbe() {
        assertTrue(NetworkProbePolicy.usable(true, false) { true })
        assertFalse(NetworkProbePolicy.usable(true, false) { false })
    }

    @Test
    fun aProbeAnswerIsReusedForFifteenSecondsOnly() {
        assertTrue(NetworkProbePolicy.cacheValid(probedAtMs = 1_000, nowMs = 1_000))
        assertTrue(NetworkProbePolicy.cacheValid(1_000, 15_999))
        assertFalse(NetworkProbePolicy.cacheValid(1_000, 16_000))
        assertFalse("a clock that went backwards is never trusted", NetworkProbePolicy.cacheValid(5_000, 4_000))
    }

    @Test
    fun theServiceAsksThePolicyAndFailsOpen() {
        val src = java.io.File("src/main/kotlin/com/music/echo/playback/MusicService.kt").readText()
        val fn = src.substringAfter("internal fun hasLiveInternetConnection()").substringBefore("@Volatile private var dnsProbe")
        assertTrue(fn.contains("NetworkProbePolicy.usable("))
        assertTrue("any unexpected error keeps the old fail-open answer", fn.contains("}.getOrDefault(true)"))
    }
}
