package iad1tya.echo.music.smoketest;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Scenario 1: a cold start (the first run of a fresh install) reaches Home without a crash or an ANR. */
@RunWith(AndroidJUnit4.class)
public class ColdStartTest {

    @Test
    public void coldStartReachesHome() {
        AuraDevice aura = new AuraDevice();
        String out = aura.launchCold();
        assertTrue("am start did not report success: " + out, out.contains("Status: ok"));
        aura.settleOnHome(180_000);
        aura.assertHealthy("Home after cold start");
        aura.capture("01-home");
    }
}
