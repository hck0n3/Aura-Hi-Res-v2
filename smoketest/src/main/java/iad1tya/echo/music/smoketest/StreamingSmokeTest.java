package iad1tya.echo.music.smoketest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scenario 6 (allowed to fail: YouTube may refuse datacenter IPs): one public song opened through the app's own
 * music.youtube.com deep link must resolve, reach READY + playing and advance.
 */
@RunWith(AndroidJUnit4.class)
public class StreamingSmokeTest {

    /** A real id already used by the unit tests (ShareLinksTest); the workflow can pass another one. */
    static final String DEFAULT_VIDEO_ID = "_KZEkEb_dvA";

    @Test
    public void publicSongPlaysFromDeepLink() {
        AuraDevice aura = new AuraDevice();
        String arg = InstrumentationRegistry.getArguments().getString("auraStreamVideoId");
        String videoId = arg == null || arg.isEmpty() ? DEFAULT_VIDEO_ID : arg;
        aura.shell("am start -W -a android.intent.action.VIEW -d https://music.youtube.com/watch?v=" + videoId
                + " -n " + aura.target + "/" + AuraDevice.MAIN_ACTIVITY);
        try (SessionProbe probe = SessionProbe.connect(aura)) {
            probe.waitUntil(
                    s -> videoId.equals(s.mediaId) && SessionProbe.readyAndPlaying(s), 90_000,
                    "video " + videoId + " to play");
            LocalTone.assertAdvances(aura, probe, 7_000, 4_500, "streaming " + videoId);
            aura.assertHealthy("streaming");
            aura.capture("06-streaming");
        }
    }
}
