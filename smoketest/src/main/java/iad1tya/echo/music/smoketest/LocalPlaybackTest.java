package iad1tya.echo.music.smoketest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scenario 3: the tone the workflow pushed into /sdcard/Music is imported by the app's OWN scan (Library ▸ Local ▸
 * Scan device), plays when tapped, and the media session reports STATE_READY + isPlaying with a moving position.
 */
@RunWith(AndroidJUnit4.class)
public class LocalPlaybackTest {

    @Test
    public void scannedLocalTonePlaysAndAdvances() {
        AuraDevice aura = new AuraDevice();
        try (SessionProbe probe = SessionProbe.connect(aura)) {
            LocalTone.playFromApp(aura, probe);
            LocalTone.assertAdvances(aura, probe, 7_000, 4_500, "local playback");
            aura.assertHealthy("local playback");
            aura.capture("03-local-playing");
        }
    }
}
