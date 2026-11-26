package iad1tya.echo.music.smoketest;

import android.graphics.Rect;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
        // Wait for the chip instead of one look: on a slow emulator it is not drawn yet, the test stays on the hub (pins
        // only) and the new playlist never shows there (run 45445171939, attempt 1).
        long chipDeadline = SystemClock.uptimeMillis() + aura.scaled(10_000);
        while (SystemClock.uptimeMillis() < chipDeadline) {
            UiObject2 playlists = aura.device.findObject(By.text(AuraDevice.exactly(chip)));
            if (playlists != null) {
                try {
                    playlists.click();
                    break;
                } catch (StaleObjectException e) {
                    // Recomposed between the lookup and the tap: look again.
                }
            }
            SystemClock.sleep(250);
        }

        AuraDevice.UiLabel create = aura.appString("create_playlist", "Create playlist");
        UiObject2 fab = aura.waitFor(() -> libraryPlusButton(aura, create), 15_000, "the Library '+' button");
        // From v2.0.69-beta3 the EMPTY Playlists tab also shows «Crear lista» as its chip (AuraLibraryTabs P22A), on
        // screen before the menu opens: the first poll returned that chip and the tap landed outside the opening menu,
        // which only closed it (fifth run 45323606796). The menu item is the label that was NOT there before the tap.
        SystemClock.sleep(1_500);
        List<Rect> before = labelBounds(aura, create);
        try {
            fab.click();
        } catch (StaleObjectException e) {
            // The '+' recomposed during the pause (run 45445171939, attempt 2): find it again and tap that one.
            aura.waitFor(() -> libraryPlusButton(aura, create), 10_000, "the Library '+' button").click();
        }
        UiObject2 item = aura.waitFor(() -> newLabel(aura, create, before), 10_000, "the '" + create + "' menu item");
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

    private static final Pattern ANY = Pattern.compile("(?s).*\\S.*");

    private static List<Rect> labelBounds(AuraDevice aura, AuraDevice.UiLabel label) {
        List<Rect> found = new ArrayList<>();
        for (UiObject2 o : aura.device.findObjects(By.text(AuraDevice.exactly(label)))) {
            Rect r = AuraDevice.bounds(o);
            if (r != null) found.add(r);
        }
        return found;
    }

    private static UiObject2 newLabel(AuraDevice aura, AuraDevice.UiLabel label, List<Rect> before) {
        for (UiObject2 o : aura.device.findObjects(By.text(AuraDevice.exactly(label)))) {
            Rect r = AuraDevice.bounds(o);
            if (r != null && !before.contains(r)) return o;
        }
        return null;
    }

    /**
     * The Library '+' (AuraLibraryScreen AuraFab): an icon-only round button whose name only reaches the click ACTION
     * (auraClickableInternal → clickable(onClickLabel = …)), never contentDescription, so By.desc cannot see it on the
     * previous build (third emulator run 45243030897). By its name where a build exposes one; else by shape: the lowest
     * clickable, roughly square 40–64 dp node in the lower-right quarter with no text or description on it or below it.
     */
    private static UiObject2 libraryPlusButton(AuraDevice aura, AuraDevice.UiLabel create) {
        UiObject2 named = aura.device.findObject(By.desc(AuraDevice.exactly(create)));
        if (named != null) return named;
        float density = aura.context.getResources().getDisplayMetrics().density;
        int width = aura.device.getDisplayWidth();
        int height = aura.device.getDisplayHeight();
        List<UiObject2> shaped = new ArrayList<>();
        for (UiObject2 o : aura.device.findObjects(By.clickable(true))) {
            try {
                Rect r = o.getVisibleBounds();
                float w = r.width() / density;
                float h = r.height() / density;
                if (w < 40 || w > 64 || h < 40 || h > 64) continue;
                if (r.centerX() < width * 3 / 4 || r.centerY() < height / 2) continue;
                String desc = o.getContentDescription();
                if (!AuraDevice.safeText(o).isEmpty() || (desc != null && !desc.isEmpty())) continue;
                if (o.hasObject(By.text(ANY)) || o.hasObject(By.desc(ANY))) continue;
                shaped.add(o);
            } catch (StaleObjectException e) {
                // Recomposed between the lookup and the read: waitFor polls again.
            }
        }
        UiObject2 fab = AuraDevice.lowest(shaped);
        if (fab != null) aura.log("Library '+' found by its shape (no accessible name on this build)");
        return fab;
    }

    @Test
    public void playlistSurvivesUpdate() {
        AuraDevice aura = new AuraDevice();
        aura.launchCold();
        aura.settleOnHome(180_000);
        // Registry row 871: a user who already used the previous build never sees the new welcome after the update.
        org.junit.Assert.assertFalse("the new welcome was shown after an update", aura.newWelcomeSeen);
        aura.openTab(aura.libraryLabel());
        AuraDevice.UiLabel chip = aura.appString("filter_playlists", "Playlists");
        // The chip is a persisted toggle (ChipSortTypeKey): 5a left Biblioteca on Playlists, and tapping the active chip
        // returns to the hub, which lists only PINNED playlists (fourth run 45249314558). Look first, tap only while absent.
        BySelector created = By.text(AuraDevice.containing(PLAYLIST));
        for (int tap = 0; tap < 2 && aura.device.wait(Until.findObject(created), 10_000) == null; tap++) {
            UiObject2 playlists = aura.device.findObject(By.text(AuraDevice.exactly(chip)));
            if (playlists != null) playlists.click();
        }
        aura.waitFor(() -> aura.device.findObject(created), 20_000, "the playlist created on the previous build");
        aura.assertHealthy("after update");
        aura.capture("05-playlist-after-update");
    }
}
