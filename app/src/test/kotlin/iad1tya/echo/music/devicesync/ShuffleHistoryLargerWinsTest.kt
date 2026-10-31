package iad1tya.echo.music.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 614 (owner 2026-10-31: «el historial del aleatorio con prioridad al dispositivo que tiene más reproducciones»). Pins what the merge
 * already guarantees, so a later change cannot break it: inside one cycle the histories are UNITED, so the device with more recorded plays
 * never loses one to the device with fewer; only a newer cycle (a finished lap or «Iniciar desde cero») replaces the set.
 */
class ShuffleHistoryLargerWinsTest {
    private val big = (1..40).map { "s$it" }

    @Test
    fun theSmallerDeviceReceivesEverythingTheLargerOneHas() {
        val plan = ShuffleMerge.plan(localEpoch = 2, localIds = setOf("s1"), remoteEpoch = 2, remoteIds = big)
        assertTrue(plan is ShuffleMerge.Plan.Add)
        assertEquals(big.drop(1), (plan as ShuffleMerge.Plan.Add).ids)
    }

    @Test
    fun theLargerDeviceKeepsItsPlaysWhenTheSmallerOneReports() {
        val plan = ShuffleMerge.plan(localEpoch = 2, localIds = big.toSet(), remoteEpoch = 2, remoteIds = listOf("s1", "s2"))
        assertEquals(ShuffleMerge.Plan.Ignore, plan)
    }

    @Test
    fun anOlderCycleNeverOverwritesTheNewerOne() {
        assertEquals(ShuffleMerge.Plan.Ignore, ShuffleMerge.plan(localEpoch = 3, localIds = setOf("a"), remoteEpoch = 2, remoteIds = big))
    }
}
