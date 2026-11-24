package iad1tya.echo.music.smoketest;

import android.os.SystemClock;

import androidx.test.uiautomator.UiObject2;

/** The pushed tone, played the way a user plays a local song: Library ▸ Local ▸ (scan) ▸ tap the row. */
final class LocalTone {

    private LocalTone() {
    }

    static boolean isTone(SessionProbe.Snapshot s) {
        return s.mediaId != null && s.mediaId.startsWith("content://media/");
    }

    /** Returns once the session reports the tone READY and playing. */
    static SessionProbe.Snapshot playFromApp(AuraDevice aura, SessionProbe probe) {
        aura.waitForToneInMediaStore();
        // Warm, not cold: the caller's SessionProbe is already bound to the running MusicService, and a force-stop
        // would leave it disconnected (run 45511056672: state=1 mediaId=null after the tap).
        aura.launchWarm();
        aura.settleOnHome(120_000);
        aura.openLocalSongs();
        UiObject2 row = aura.scanUntilToneListed();
        SessionProbe.Snapshot now = probe.read();
        if (isTone(now) && SessionProbe.readyAndPlaying(now)) {
            // Already playing (an earlier scenario started it): tapping the active row would PAUSE it.
            aura.log("tone already playing: " + now);
            return now;
        }
        row.click();
        SessionProbe.Snapshot playing = probe.waitUntil(
                s -> isTone(s) && SessionProbe.readyAndPlaying(s), 30_000, "the tone to play (STATE_READY + isPlaying)");
        aura.log("tone playing: " + playing);
        return playing;
    }

    /**
     * Over `windowMs` the same item keeps playing and its position moves by at least `minAdvanceMs`. The window is
     * longer than the session's periodic position update, so a stalled player cannot pass on extrapolation alone.
     */
    static void assertAdvances(AuraDevice aura, SessionProbe probe, long windowMs, long minAdvanceMs, String what) {
        SessionProbe.Snapshot before = probe.read();
        SystemClock.sleep(windowMs);
        SessionProbe.Snapshot after = probe.read();
        aura.log(what + ": before " + before + " / after " + after);
        if (!SessionProbe.readyAndPlaying(after)) {
            aura.fail(what + ": playback stopped (" + after + ")");
        }
        if (after.mediaId == null || !after.mediaId.equals(before.mediaId)) {
            aura.fail(what + ": the playing item changed from " + before.mediaId + " to " + after.mediaId);
        }
        long advance = after.positionMs - before.positionMs;
        if (advance < minAdvanceMs) {
            aura.fail(what + ": position advanced only " + advance + " ms in " + windowMs + " ms");
        }
    }
}
