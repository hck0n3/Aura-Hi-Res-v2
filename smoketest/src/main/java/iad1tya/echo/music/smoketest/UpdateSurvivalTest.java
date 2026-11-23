package iad1tya.echo.music.smoketest;

import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Scenario 5, in two halves the workflow runs around an `adb install -r`:
 * {@link #createPlaylistOnPreviousBuild()} on the PREVIOUS build, then {@link #playlistSurvivesUpdate()} on the new one.
 * The workflow also reads the copied database before and after, so the UI is not the only witness.
 */
@RunWith(AndroidJUnit4.class)
public class UpdateSurvivalTest {

    /** Also hard-coded in scripts/emulator_smoke/run_smoke.sh (SMOKE_PLAYLIST). */
    static final String PLAYLIST = "Aura smoke playlist";

    @Test
    public void createPlaylistOnPreviousBuild() {
        AuraDevice aura = new AuraDevice();
        aura.launchCold();
        aura.settleOnHome(180_000);
        aura.openTab(aura.libraryLabel());
        // The '+' lives on the hub and on the Playlists chip; the chip is the explicit place to create one.
        AuraDevice.UiLabel chip = aura.appString("filter_playlists", "Playlists");
        UiObject2 playlists = aura.device.findObject(By.text(AuraDevice.exactly(chip)));
        if (playlists != null) playlists.click();

        AuraDevice.UiLabel create = aura.appString("create_playlist", "Create playlist");
        UiObject2 fab = aura.waitFor(
                () -> aura.device.findObject(By.desc(AuraDevice.exactly(create))), 15_000, "the Library '+' button");
        fab.click();
        UiObject2 item = aura.waitFor(
                () -> aura.device.findObject(By.text(AuraDevice.exactly(create))), 10_000, "the '" + create + "' menu item");
        item.click();
        UiObject2 field = aura.waitFor(
                () -> aura.device.findObject(By.clazz("android.widget.EditText")), 10_000, "the playlist name field");
        field.setText(PLAYLIST);
        SystemClock.sleep(500);
        // TextFieldDialog renders stringResource(android.R.string.ok) in the app's language (es-419 after the seed).
        AuraDevice.UiLabel ok = aura.frameworkString(android.R.string.ok);
        UiObject2 okButton = aura.waitFor(
                () -> aura.device.findObject(By.text(AuraDevice.exactly(ok))), 10_000, "the dialog's '" + ok + "'");
        okButton.click();
        aura.waitFor(() -> aura.device.findObject(By.text(AuraDevice.containing(PLAYLIST))), 15_000, "the new playlist");
        aura.assertHealthy("playlist created");
        aura.capture("05-playlist-created");
        aura.device.pressBack();
        // Let Room flush before the workflow copies the database and installs the update over it.
        SystemClock.sleep(3_000);
    }

    @Test
    public void playlistSurvivesUpdate() {
        AuraDevice aura = new AuraDevice();
        aura.launchCold();
        aura.settleOnHome(180_000);
        aura.openTab(aura.libraryLabel());
        AuraDevice.UiLabel chip = aura.appString("filter_playlists", "Playlists");
        UiObject2 playlists = aura.device.findObject(By.text(AuraDevice.exactly(chip)));
        if (playlists != null) playlists.click();
        aura.waitFor(
                () -> aura.device.findObject(By.text(AuraDevice.containing(PLAYLIST))), 20_000,
                "the playlist created on the previous build");
        aura.assertHealthy("after update");
        aura.capture("05-playlist-after-update");
    }
}
