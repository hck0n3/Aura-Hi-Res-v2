package iad1tya.echo.music.listentogether

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 643 (owner 2026-11-02: «sin importar cuántos dispositivos haya en la sala, que se sincronicen todos»). Every guest corrects against
 * the ROOM's position, never against another guest, so the result must not depend on how many guests there are, on who started late, or
 * on whether the host itself was behind. This pins that with the real [SyncCorrection]: N guests with very different starting errors, one
 * correction per heartbeat (15 s), and after a minute every guest — and therefore every PAIR of guests — is within the budget.
 */
class RoomConvergenceTest {
    private val heartbeatMs = 15_000L

    /** One guest, one heartbeat: apply [SyncCorrection] to its error against the room and let the chosen speed run for a heartbeat. */
    private fun step(errorMs: Long): Long = when (val action = SyncCorrection.correct(errorMs, targetMs = 0L)) {
        is SyncAction.Hold -> errorMs
        is SyncAction.Resync -> 0L // a jump lands on the room's position
        is SyncAction.Trim -> errorMs + ((action.speed - 1f) * heartbeatMs).toLong()
    }

    private fun settle(errors: List<Long>, heartbeats: Int): List<Long> =
        errors.map { start -> (1..heartbeats).fold(start) { e, _ -> step(e) } }

    @Test
    fun anyNumberOfGuestsEndsWithinTheBudgetAfterAMinute() {
        val starts = listOf(-3_000L, -1_900L, -1_000L, -700L, -400L, -150L, 0L, 120L, 380L, 850L, 1_000L, 2_400L, 3_000L)
        for (n in listOf(2, 3, 5, 8, starts.size)) {
            val guests = starts.take(n)
            val ends = settle(guests, heartbeats = 4)
            ends.forEachIndexed { i, e -> assertTrue("n=$n guest $i started ${guests[i]} ms off and ended $e ms off", abs(e) <= 150) }
            val spread = ends.max() - ends.min()
            assertTrue("n=$n the spread between any two guests is $spread ms", spread <= 300)
        }
    }

    @Test
    fun aGuestThatStartsATrackUpToSevenHundredMsOffEndsWithinThirtyAfterAMinute() {
        // Row 647 (owner 2026-11-02): «la tablet va más adelantada» with ~80 ms measured; the old 60 ms dead zone left that
        // standing for the whole song. Each track change can start a guest a little off, so every size of start error up
        // to the trim range must end tight, not just inside the loose 150 ms budget above.
        val starts = listOf(-700L, -400L, -150L, -100L, -80L, -60L, -40L, 40L, 60L, 80L, 100L, 120L, 380L)
        settle(starts, heartbeats = 4).forEachIndexed { i, e ->
            assertTrue("started ${starts[i]} ms off and ended $e ms off", abs(e) <= 30)
        }
    }

    @Test
    fun aHostThatIsBehindOrAheadMovesEveryGuestTheSameWay() {
        // The reference is the room's clock: shifting the whole room by any amount shifts every error by the same amount, so a
        // guest's correction depends only on its own error against the room, not on who is the slowest or the creator.
        val guests = listOf(-800L, 0L, 300L, 900L)
        val baseline = settle(guests, 4)
        val sameWithEveryGuestRunLateBy200 = settle(guests.map { it + 200 }, 4)
        sameWithEveryGuestRunLateBy200.zip(baseline).forEach { (late, base) ->
            assertTrue("a uniform 200 ms shift must not break the convergence: $late vs $base", abs(late) <= 150)
        }
    }

    @Test
    fun theCorrectionNeverOscillatesOutward() {
        // From any error within the trim range, the error after a heartbeat is smaller in magnitude than before (it lands, it does not ring).
        for (e in -1_000L..1_000L step 25) {
            if (abs(e) <= SyncCorrection.IN_SYNC_MS) continue
            assertTrue("error $e became ${step(e)}", abs(step(e)) < abs(e))
        }
    }
}
