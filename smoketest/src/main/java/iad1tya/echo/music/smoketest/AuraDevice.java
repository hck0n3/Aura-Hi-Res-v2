package iad1tya.echo.music.smoketest;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Drives the app under test from OUTSIDE its process (the smoke module is self-instrumenting): UI Automator for the
 * screen, shell commands for the device, the app's own resources for every label. A label matches the app's string
 * in BOTH the language the app forces on a fresh install (es-419, see {@link #APP_SEED_LANGUAGE}) and the emulator's
 * own language (what the very first start shows, before the seed reaches the next cold start). It never calls app
 * code: what it checks is what a user would see.
 */
final class AuraDevice {
    static final String TAG = "AuraSmoke";

    /** `-Pnosub=true` + the debug buildType suffix (app/build.gradle.kts). The run script passes the real one. */
    static final String DEFAULT_TARGET = "iad1tya.aura.music.dev.debug";
    /** The app's Kotlin namespace: manifest class names and the resource table are resolved against it. */
    static final String NAMESPACE = "iad1tya.echo.music";
    static final String LAUNCHER_ALIAS = NAMESPACE + ".MainActivityAlias";
    static final String MAIN_ACTIVITY = NAMESPACE + ".MainActivity";
    static final String MUSIC_SERVICE = NAMESPACE + ".playback.MusicService";

    /** The file the workflow pushes into /sdcard/Music (scripts/emulator_smoke/smoke_tools.py TONE_NAME). */
    static final String TONE_FILE = "aura_smoke_tone.wav";
    static final Pattern TONE_ROW = Pattern.compile("(?is).*aura.?smoke.?tone.*");

    /**
     * The UI language a fresh install stores (App.kt applySeedDefaults and applyLyricsEsLatamAutoTranslateV1 write
     * AppLanguageKey = "es-419"; MainActivity.attachBaseContext applies it from the next cold start on). Pinned by
     * scripts/emulator_smoke/test_smoke_tools.py AppLiteralsTest.
     */
    static final String APP_SEED_LANGUAGE = "es-419";

    final Instrumentation instrumentation;
    final UiDevice device;
    final Context context;
    final String target;

    /** Set by {@link #settleOnHome} when it had to walk a step of the new welcome (registry rows 871/872). */
    boolean newWelcomeSeen;

    AuraDevice() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        context = instrumentation.getContext();
        String arg = InstrumentationRegistry.getArguments().getString("auraTargetPackage");
        target = arg == null || arg.isEmpty() ? DEFAULT_TARGET : arg;
        // The app animates continuously (visualizer, shimmer while Home loads): waiting for a fully idle UI
        // would stall every lookup for the default 10 s.
        Configurator.getInstance().setWaitForIdleTimeout(1_000);
    }

    // ── Shell, process and crash checks ───────────────────────────────────────────────────────────────

    /** One command, no shell syntax (UiAutomation does not run a shell: no pipes, no `;`). */
    String shell(String command) {
        try {
            return device.executeShellCommand(command);
        } catch (IOException e) {
            Log.w(TAG, "shell failed: " + command, e);
            return "";
        }
    }

    void log(String message) {
        Log.i(TAG, message);
    }

    String pid() {
        return shell("pidof " + target).trim();
    }

    String appLabel() {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(target, 0);
            return String.valueOf(context.getPackageManager().getApplicationLabel(info));
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError("The app under test is not installed: " + target, e);
        }
    }

    /** Fails on a dead process, Aura's own crash screen, or a "not responding" dialog for the app. */
    void assertHealthy(String where) {
        for (String line : shell("dumpsys activity activities").split("\n")) {
            if (line.contains("ResumedActivity") && line.contains("CrashActivity")) {
                fail(where + ": Aura is showing its crash screen (" + line.trim() + ")");
            }
        }
        UiObject2 anr = device.findObject(By.text(Pattern.compile("(?is).*(isn.t responding|no responde).*")));
        if (anr != null) {
            String text = safeText(anr);
            if (text.contains(appLabel())) {
                fail(where + ": ANR dialog for the app: " + text);
            }
            // Another process (System UI on a slow emulator): let it be, it is not the app under test.
            UiObject2 wait = device.findObject(By.text(Pattern.compile("(?i)wait|esperar")));
            if (wait != null) wait.click();
        }
        if (pid().isEmpty()) {
            fail(where + ": the app process is not running (crashed or killed)");
        }
    }

    // ── Labels from the app's own resources ───────────────────────────────────────────────────────────

    /**
     * One on-screen label in every language the app may be rendering it in. The app resolves its strings through
     * a context whose Configuration only overrides the locale (Utils.kt localeAwareContext); the variants are
     * resolved the same way, so the resource folder Android picks for es-419 is the one the app picks.
     */
    static final class UiLabel {
        final List<String> variants;

        UiLabel(Set<String> variants) {
            this.variants = new ArrayList<>(variants);
        }

        static UiLabel of(String text) {
            Set<String> one = new LinkedHashSet<>();
            one.add(text);
            return new UiLabel(one);
        }

        boolean isEmpty() {
            for (String v : variants) if (!v.isEmpty()) return false;
            return true;
        }

        /** A non-capturing alternation of the quoted variants. */
        String regex() {
            StringBuilder sb = new StringBuilder("(?:");
            for (int i = 0; i < variants.size(); i++) {
                if (i > 0) sb.append('|');
                sb.append(Pattern.quote(variants.get(i)));
            }
            return sb.append(')').toString();
        }

        @Override
        public String toString() {
            return String.join(" / ", variants);
        }
    }

    /** The app's string as es-419 and as the device's language; `fallback` only when the resource cannot be found. */
    UiLabel appString(String name, String fallback) {
        Set<String> found = new LinkedHashSet<>();
        for (Locale locale : new Locale[] {Locale.forLanguageTag(APP_SEED_LANGUAGE), null}) {
            String text = appStringIn(name, locale);
            if (text != null) found.add(text);
        }
        if (found.isEmpty()) {
            Log.w(TAG, "string " + name + " not found, using fallback");
            found.add(fallback);
        }
        return new UiLabel(found);
    }

    /** `locale == null`: the device's own configuration. */
    private String appStringIn(String name, Locale locale) {
        try {
            Context app = context.createPackageContext(target, 0);
            Resources res = withLocale(app, locale).getResources();
            for (String pkg : new String[] {target, NAMESPACE}) {
                int id = res.getIdentifier(name, "string", pkg);
                if (id != 0) return res.getString(id);
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            Log.w(TAG, "string " + name + " not resolved for " + locale, e);
        }
        return null;
    }

    /** A framework string (android.R.string.*) as the app shows it: es-419 and the device's language. */
    UiLabel frameworkString(int id) {
        Set<String> found = new LinkedHashSet<>();
        found.add(withLocale(context, Locale.forLanguageTag(APP_SEED_LANGUAGE)).getResources().getString(id));
        found.add(Resources.getSystem().getString(id));
        return new UiLabel(found);
    }

    private static Context withLocale(Context base, Locale locale) {
        if (locale == null) return base;
        Configuration cfg = new Configuration();
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }

    static Pattern exactly(String text) {
        return exactly(UiLabel.of(text));
    }

    static Pattern exactly(UiLabel label) {
        return Pattern.compile("\\s*" + label.regex() + "\\s*");
    }

    static Pattern containing(String text) {
        return containing(UiLabel.of(text));
    }

    static Pattern containing(UiLabel label) {
        return Pattern.compile("(?is).*" + label.regex() + ".*");
    }

    // ── Launching and the first-run flow ─────────────────────────────────────────────────────────────

    /** Force-stops the app and starts it from its launcher entry; returns `am start -W` output (TotalTime). */
    String launchCold() {
        shell("am force-stop " + target);
        String out = shell("am start -W -n " + target + "/" + LAUNCHER_ALIAS);
        log("cold start: " + out.replace('\n', ' '));
        return out;
    }

    /** Brings the app to the front without killing it. */
    void launchWarm() {
        shell("am start -W -n " + target + "/" + LAUNCHER_ALIAS);
    }

    UiLabel homeLabel() {
        return appString("home", "Home");
    }

    UiLabel libraryLabel() {
        return appString("filter_library", "Library");
    }

    /**
     * Walks whatever first-run surface is in the way (the blocking Terms & Conditions screen's «Aceptar y continuar»,
     * the onboarding's hard-coded Spanish buttons, the welcome tour, the one-shot "open links" prompt) until Home is on
     * screen with its bar and nothing new has popped up for 7 s (the links prompt waits 4 s after the tour closes).
     * Registry rows 871/872: the new welcome's «Continuar» (permissions), «Más tarde» («Conecta tu música») and «Empezar a
     * escuchar» («Listo») are steps too; walking the first or the last sets {@link #newWelcomeSeen}.
     */
    void settleOnHome(long timeoutMs) {
        UiLabel acceptTerms = appString("terms_accept", "Accept and continue");
        UiLabel later = appString("open_links_prompt_later", "Later");
        UiLabel welcomeContinue = appString("welcome_continue", "Continue");
        UiLabel welcomeLater = appString("welcome_later", "Later");
        UiLabel welcomeStart = appString("welcome_done_start", "Start listening");
        // «Más tarde» is left out: the one-shot "open links" prompt has a button with the same text.
        Pattern welcomeSteps = Pattern.compile("\\s*(" + welcomeContinue.regex() + "|" + welcomeStart.regex() + ")\\s*");
        Pattern steps = Pattern.compile("\\s*(" + acceptTerms.regex()
                + "|Omitir|Siguiente: tu cuenta de Google|Siguiente: YouTube Music|Comenzar|" + later.regex()
                + "|" + welcomeContinue.regex() + "|" + welcomeLater.regex() + "|" + welcomeStart.regex() + ")\\s*");
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        long quietSince = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() < deadline) {
            assertHealthy("first run");
            UiObject2 step = device.findObject(By.text(steps));
            if (step != null) {
                log("first run: tapping '" + safeText(step) + "'");
                String text = safeText(step);
                if (text != null && welcomeSteps.matcher(text).matches()) newWelcomeSeen = true;
                try {
                    step.click();
                } catch (StaleObjectException e) {
                    // Recomposed between the lookup and the tap (second emulator run): look again.
                    log("first run: the step went stale before the tap");
                }
                quietSince = SystemClock.uptimeMillis();
                SystemClock.sleep(1_000);
                continue;
            }
            boolean onHome = navCell(homeLabel()) != null && navCell(libraryLabel()) != null;
            if (!onHome) {
                quietSince = SystemClock.uptimeMillis();
            } else if (SystemClock.uptimeMillis() - quietSince >= 7_000) {
                log("Home reached");
                return;
            }
            SystemClock.sleep(500);
        }
        fail("Home was not reached within " + timeoutMs + " ms");
    }

    // ── Bottom bar ───────────────────────────────────────────────────────────────────────────────────

    /** The bottom-bar cell with this label: the LOWEST on-screen match, and only inside the bottom quarter. */
    UiObject2 navCell(UiLabel label) {
        UiObject2 best = lowest(device.findObjects(By.text(exactly(label))));
        if (best == null) return null;
        Rect r = bounds(best);
        return r != null && r.centerY() >= device.getDisplayHeight() * 3 / 4 ? best : null;
    }

    /**
     * Taps a bottom-bar cell and waits until the bar reports it selected. The selected cell is looked for anywhere on
     * screen: a screen that opens the keyboard (Search) pushes the bar up out of the bottom quarter.
     * Compose exposes its UNMERGED tree: `selected` sits on the cell (AuraShell.kt AuraNavItem, Role.Tab), the label
     * on its Text child, so the selected node is the one that HAS the label below it (second emulator run).
     */
    void openTab(UiLabel label) {
        UiObject2 cell = waitFor(() -> navCell(label), 15_000, "bottom bar cell '" + label + "'");
        try {
            cell.click();
        } catch (StaleObjectException e) {
            waitFor(() -> navCell(label), 15_000, "bottom bar cell '" + label + "'").click();
        }
        long start = SystemClock.uptimeMillis();
        long deadline = start + 10_000;
        boolean retapped = false;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 now = device.findObject(By.selected(true).hasDescendant(By.text(exactly(label))));
            if (now != null) {
                log("tab '" + label + "' selected");
                return;
            }
            // A slow emulator can drop the first tap (run 45504112186): tap the cell once more halfway through.
            if (!retapped && SystemClock.uptimeMillis() - start > 5_000) {
                retapped = true;
                UiObject2 again = navCell(label);
                if (again != null) {
                    try {
                        again.click();
                        log("tab '" + label + "' tapped again");
                    } catch (StaleObjectException e) {
                        // Recomposed: the next poll looks again.
                    }
                }
            }
            SystemClock.sleep(300);
        }
        fail("tab '" + label + "' never became selected");
    }

    // ── Local music ──────────────────────────────────────────────────────────────────────────────────

    /** Waits until MediaStore lists the pushed tone with a duration (what the app's scanner requires). */
    void waitForToneInMediaStore() {
        long deadline = SystemClock.uptimeMillis() + 60_000;
        boolean nudged = false;
        while (SystemClock.uptimeMillis() < deadline) {
            if (toneDurationMs() > 0) {
                log("tone indexed by MediaStore");
                return;
            }
            if (!nudged && SystemClock.uptimeMillis() > deadline - 45_000) {
                nudged = true;
                MediaScannerConnection.scanFile(context, new String[] {"/sdcard/Music/" + TONE_FILE}, null, null);
                shell("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/" + TONE_FILE);
            }
            SystemClock.sleep(1_000);
        }
        fail("SETUP: MediaStore never indexed /sdcard/Music/" + TONE_FILE + " (pushed by the workflow?)");
    }

    private long toneDurationMs() {
        String[] projection = {MediaStore.Audio.Media.DURATION};
        try (Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.DISPLAY_NAME + "=?",
                new String[] {TONE_FILE},
                null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (SecurityException e) {
            fail("SETUP: the smoke APK may not read MediaStore (READ_MEDIA_AUDIO not granted by the workflow?)");
        }
        return 0;
    }

    /**
     * Library ▸ «Local»: the hub tile that opens the embedded LocalSongScreen. The Library keeps its filter across
     * tab switches, so an earlier scenario may have left it ON Local already (tone row or empty state on screen).
     */
    void openLocalSongs() {
        openTab(libraryLabel());
        UiLabel local = appString("filter_local", "Local");
        UiLabel emptyScan = appString("local_songs_empty_scan_action", "Scan device");
        UiObject2 found = waitFor(() -> {
            UiObject2 onLocal = device.findObject(By.text(TONE_ROW));
            if (onLocal == null) onLocal = device.findObject(By.text(exactly(emptyScan)));
            return onLocal != null ? onLocal : device.findObject(By.text(exactly(local)));
        }, 15_000, "Library tile '" + local + "' (or the Local list itself)");
        if (exactly(local).matcher(safeText(found)).matches()) {
            found.click();
            SystemClock.sleep(1_500);
        }
        assertHealthy("open Local");
    }

    /**
     * On the Local screen: runs the app's own scan unless the tone is already listed, then returns the tone's row.
     * The scan is the user path: empty state «Scan device» → scan sheet → its primary «Scan device».
     */
    UiObject2 scanUntilToneListed() {
        UiLabel emptyScan = appString("local_songs_empty_scan_action", "Scan device");
        long deadline = SystemClock.uptimeMillis() + 20_000;
        UiObject2 emptyButton = null;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 row = device.findObject(By.text(TONE_ROW));
            if (row != null) {
                log("tone already listed, no scan needed");
                return row;
            }
            emptyButton = device.findObject(By.text(exactly(emptyScan)));
            if (emptyButton != null) break;
            SystemClock.sleep(500);
        }
        if (emptyButton == null) fail("Local screen showed neither the tone nor the empty-state scan button");
        Rect emptyBounds = bounds(emptyButton);
        emptyButton.click();

        UiLabel sheetTitle = appString("local_songs_scan_title", "");
        if (!sheetTitle.isEmpty()) {
            waitFor(() -> device.findObject(By.text(exactly(sheetTitle))), 10_000, "scan sheet '" + sheetTitle + "'");
        } else {
            SystemClock.sleep(2_000);
        }
        UiLabel scanLabel = appString("scan_device", "Scan device");
        UiObject2 primary = null;
        for (int attempt = 0; attempt < 5 && primary == null; attempt++) {
            for (UiObject2 candidate : device.findObjects(By.text(exactly(scanLabel)))) {
                Rect r = bounds(candidate);
                if (r != null && !r.equals(emptyBounds)) primary = candidate;
            }
            if (primary == null) {
                int x = device.getDisplayWidth() / 2;
                device.swipe(x, device.getDisplayHeight() * 85 / 100, x, device.getDisplayHeight() * 35 / 100, 20);
                SystemClock.sleep(800);
            }
        }
        if (primary == null) fail("the scan sheet's '" + scanLabel + "' button never appeared");
        log("starting the app's local scan");
        primary.click();

        long scanDeadline = SystemClock.uptimeMillis() + 90_000;
        UiLabel scanning = appString("scanning_device", "Scanning");
        SystemClock.sleep(2_000);
        while (SystemClock.uptimeMillis() < scanDeadline
                && device.findObject(By.text(containing(scanning))) != null) {
            SystemClock.sleep(1_000);
        }
        assertHealthy("local scan");
        device.pressBack(); // closes the scan sheet
        UiObject2 row = device.wait(Until.findObject(By.text(TONE_ROW)), 20_000);
        if (row == null) {
            // The back also left Local (sheet already gone): open it again and look once more.
            UiLabel local = appString("filter_local", "Local");
            UiObject2 tile = device.findObject(By.text(exactly(local)));
            if (tile != null) tile.click();
            row = waitFor(() -> device.findObject(By.text(TONE_ROW)), 20_000, "the scanned tone in the Local list");
        }
        log("scan imported the tone");
        return row;
    }

    // ── Generic waits and diagnostics ────────────────────────────────────────────────────────────────

    interface Lookup {
        UiObject2 find();
    }

    UiObject2 waitFor(Lookup lookup, long timeoutMs, String what) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 found = lookup.find();
            if (found != null) return found;
            SystemClock.sleep(400);
        }
        fail("timed out after " + timeoutMs + " ms waiting for " + what);
        return null;
    }

    boolean hasInternet() {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        return cm != null && cm.getActiveNetwork() != null;
    }

    /** Saves a screenshot and the window hierarchy next to the smoke APK's files (the workflow pulls them). */
    void capture(String name) {
        File dir = context.getExternalFilesDir(null);
        if (dir == null) return;
        String safe = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        try {
            device.takeScreenshot(new File(dir, safe + ".png"));
            device.dumpWindowHierarchy(new File(dir, safe + ".xml"));
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "capture failed: " + name, e);
        }
    }

    void fail(String message) {
        capture("FAIL-" + SystemClock.uptimeMillis());
        Log.e(TAG, message);
        throw new AssertionError(message);
    }

    static String safeText(UiObject2 o) {
        try {
            String t = o.getText();
            return t == null ? "" : t;
        } catch (RuntimeException e) {
            return "";
        }
    }

    static Rect bounds(UiObject2 o) {
        try {
            return o.getVisibleBounds();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static UiObject2 lowest(List<UiObject2> objects) {
        UiObject2 best = null;
        int bestY = Integer.MIN_VALUE;
        for (UiObject2 o : objects) {
            Rect r = bounds(o);
            if (r != null && r.centerY() > bestY) {
                best = o;
                bestY = r.centerY();
            }
        }
        return best;
    }
}
