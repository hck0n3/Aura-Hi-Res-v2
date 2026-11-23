package iad1tya.echo.music.smoketest;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Scenario 2: Library, Search, Novedades and Settings each open, render and come back, with the app alive. */
@RunWith(AndroidJUnit4.class)
public class NavigationTest {

    @Test
    public void topLevelScreensRenderAndComeBack() {
        AuraDevice aura = new AuraDevice();
        aura.launchCold();
        aura.settleOnHome(120_000);

        // Library: its hub shows the «Local» tile.
        aura.openTab(aura.libraryLabel());
        AuraDevice.UiLabel local = aura.appString("filter_local", "Local");
        aura.waitFor(() -> aura.device.findObject(By.text(AuraDevice.exactly(local))), 15_000, "Library hub");
        aura.capture("02-library");
        back(aura, "Library");

        // Search: the screen owns a text field.
        aura.openTab(aura.appString("search", "Search"));
        aura.waitFor(() -> aura.device.findObject(By.clazz("android.widget.EditText")), 15_000, "Search text field");
        aura.capture("02-search");
        back(aura, "Search");

        // Novedades: with or without network it must open and stay up (its content comes from YouTube).
        aura.openTab(aura.appString("tab_novedades", "New"));
        SystemClock.sleep(4_000);
        aura.assertHealthy("Novedades");
        aura.capture("02-novedades");
        back(aura, "Novedades");

        // Settings: the top-right account/cog button → «Ajustes» in its sheet → the settings list.
        openSettings(aura);
        aura.capture("02-settings");
        back(aura, "Settings");
        aura.assertHealthy("after navigation");
    }

    private static void openSettings(AuraDevice aura) {
        aura.openTab(aura.homeLabel());
        AuraDevice.UiLabel account = aura.appString("account", "Account");
        UiObject2 cog = aura.waitFor(
                () -> aura.device.findObject(By.desc(AuraDevice.containing(account))), 15_000, "account/settings button");
        cog.click();
        UiObject2 entry = null;
        for (int i = 0; i < 6 && entry == null; i++) {
            entry = aura.device.findObject(By.text(AuraDevice.exactly("Ajustes")));
            if (entry == null) {
                int x = aura.device.getDisplayWidth() / 2;
                aura.device.swipe(x, aura.device.getDisplayHeight() * 80 / 100, x, aura.device.getDisplayHeight() * 40 / 100, 20);
                SystemClock.sleep(700);
            }
        }
        if (entry == null) aura.fail("«Ajustes» not found in the account sheet");
        entry.click();
        AuraDevice.UiLabel appearance = aura.appString("appearance", "Appearance");
        aura.waitFor(() -> aura.device.findObject(By.text(AuraDevice.containing(appearance))), 15_000, "Settings list");
        aura.assertHealthy("Settings");
    }

    /** System back; the app must survive it. Back from a top-level tab may leave the app: then bring it back. */
    private static void back(AuraDevice aura, String from) {
        aura.device.pressBack();
        SystemClock.sleep(1_500);
        aura.assertHealthy("back from " + from);
        if (!aura.target.equals(aura.device.getCurrentPackageName())) {
            aura.log("back from " + from + " left the app; relaunching");
            aura.launchWarm();
        }
        aura.settleOnHome(60_000);
    }
}
