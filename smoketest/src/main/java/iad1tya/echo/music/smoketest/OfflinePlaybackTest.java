package iad1tya.echo.music.smoketest;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scenario 4: with the local tone playing, the emulator loses every network (airplane mode + Wi-Fi + mobile data);
 * the same item keeps playing, the position keeps moving and the app does not crash. The network comes back at the end
 * whatever happens, so the next scenario starts online.
 */
@RunWith(AndroidJUnit4.class)
public class OfflinePlaybackTest {

    @Test
    public void localPlaybackSurvivesNetworkLoss() {
        AuraDevice aura = new AuraDevice();
        try (SessionProbe probe = SessionProbe.connect(aura)) {
            LocalTone.playFromApp(aura, probe);
            try {
                goOffline(aura);
                LocalTone.assertAdvances(aura, probe, 10_000, 7_000, "offline playback");
                aura.assertHealthy("offline");
                aura.capture("04-offline-playing");
            } finally {
                goOnline(aura);
            }
        }
    }

    private static void goOffline(AuraDevice aura) {
        aura.shell("cmd connectivity airplane-mode enable");
        aura.shell("svc wifi disable");
        aura.shell("svc data disable");
        long deadline = SystemClock.uptimeMillis() + 20_000;
        while (aura.hasInternet() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(500);
        }
        // Without this the scenario would pass while still online: a test that cannot fail proves nothing.
        if (aura.hasInternet()) aura.fail("SETUP: the emulator still has an active network after going offline");
        aura.log("network is off");
        SystemClock.sleep(2_000);
    }

    private static void goOnline(AuraDevice aura) {
        aura.shell("cmd connectivity airplane-mode disable");
        aura.shell("svc wifi enable");
        aura.shell("svc data enable");
        aura.log("network restored");
    }
}
