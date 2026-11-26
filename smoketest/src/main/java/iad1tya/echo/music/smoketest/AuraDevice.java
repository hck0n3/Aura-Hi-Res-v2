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

    /** How often one scenario may bring the app back to the front before that itself is the failure (row 885). */
    static final int MAX_RELAUNCHES = 3;
    /** Taps on one bottom-bar cell before "never became selected" (the first one included). */
    static final int MAX_TAB_TAPS = 3;
    /** System backs settleOnHome may press to close something left on top of the bar (a sheet, the keyboard). */
    static final int MAX_SETTLE_BACKS = 3;

    static final Pattern ANR_TEXT = Pattern.compile("(?is).*(isn.t responding|no responde).*");
    static final Pattern ANR_WAIT = Pattern.compile("(?i)wait|esperar");

    final Instrumentation instrumentation;
    final UiDevice device;
    final Context context;
    final String target;
    /**
     * Every UI wait is multiplied by this (instrumentation argument `auraSlowFactor`, run_smoke.sh SMOKE_SLOW; 1..5,
     * 1 when absent). Only waits for the SCREEN scale: the playback checks (SessionProbe, LocalTone) keep their times.
     */
    final int slow;

    /** Set by {@link #settleOnHome} when it had to walk a step of the new welcome (registry rows 871/872). */
    boolean newWelcomeSeen;

    private int relaunches;

    AuraDevice() {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(instrumentation);
        context = instrumentation.getContext();
        String arg = InstrumentationRegistry.getArguments().getString("auraTargetPackage");
        target = arg == null || arg.isEmpty() ? DEFAULT_TARGET : arg;
        slow = slowFactor(InstrumentationRegistry.getArguments().getString("auraSlowFactor"));
        // The app animates continuously (visualizer, shimmer while Home loads): waiting for a fully idle UI
        // would stall every lookup for the default 10 s.
        Configurator.getInstance().setWaitForIdleTimeout(1_000);
        if (slow > 1) log("UI waits scaled x" + slow);
    }

    static int slowFactor(String arg) {
        if (arg == null) return 1;
        try {
            return Math.max(1, Math.min(5, Integer.parseInt(arg.trim())));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** A UI wait for this emulator: `ms` times {@link #slow}. */
    long scaled(long ms) {
        return ms * slow;
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
        dismissSystemDialogs(where);
        if (pid().isEmpty()) {
            fail(where + ": the app process is not running (crashed or killed)");
        }
    }

    /**
     * A "… isn't responding" dialog: the app's own is a failure; another process's (System UI or the system itself on a
     * slow first boot) is answered «Wait», because it covers the app and swallows the next tap. True when it answered.
     */
    boolean dismissSystemDialogs(String where) {
        UiObject2 anr = device.findObject(By.text(ANR_TEXT));
        if (anr == null) return false;
        String text = safeText(anr);
        if (text.contains(appLabel())) {
            fail(where + ": ANR dialog for the app: " + text);
        }
        UiObject2 wait = device.findObject(By.text(ANR_WAIT));
        if (wait == null) return false;
        recovery(where + ": answering 'wait' on a system dialog: " + text);
        try {
            wait.click();
        } catch (StaleObjectException e) {
            return false;
        }
        SystemClock.sleep(500);
        return true;
    }

    /**
     * Before a wait or a tap: the app must be what is on screen. A crash, the app's own ANR or a dead process still fail
     * ({@link #assertHealthy}); another process's ANR dialog is answered; and when something else is in front (the
     * launcher, a slow boot's leftovers) the app is brought back warm, at most {@link #MAX_RELAUNCHES} times per scenario.
     */
    void ensureForeground(String where) {
        assertHealthy(where);
        String pkg = currentPackage();
        if (pkg == null || target.equals(pkg) || pkg.endsWith(".permissioncontroller")) return;
        if (++relaunches > MAX_RELAUNCHES) {
            fail(where + ": the app left the foreground more than " + MAX_RELAUNCHES + " times (now: " + pkg + ")");
        }
        recovery(where + ": '" + pkg + "' is in front of the app; relaunching it warm ("
                + relaunches + "/" + MAX_RELAUNCHES + ")");
        launchWarm();
        long deadline = SystemClock.uptimeMillis() + scaled(10_000);
        while (SystemClock.uptimeMillis() < deadline && !target.equals(currentPackage())) {
            SystemClock.sleep(500);
        }
    }

    String currentPackage() {
        try {
            return device.getCurrentPackageName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Something the smoke had to do to keep going; run_smoke.sh counts these lines in the summary. */
    void recovery(String message) {
        Log.w(TAG, "RECOVERY " + message);
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
        long limit = scaled(timeoutMs);
        long quiet = scaled(7_000);
        long stuckAfter = scaled(20_000);
        long deadline = SystemClock.uptimeMillis() + limit;
        long quietSince = SystemClock.uptimeMillis();
        long progressAt = SystemClock.uptimeMillis();
        int backs = 0;
        while (SystemClock.uptimeMillis() < deadline) {
            ensureForeground("first run");
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
                progressAt = quietSince;
                SystemClock.sleep(1_000);
                continue;
            }
            boolean onHome = navCell(homeLabel()) != null && navCell(libraryLabel()) != null;
            if (!onHome) {
                quietSince = SystemClock.uptimeMillis();
                // Nothing to tap and no bar for a long while: something an earlier, failed scenario left open (the
                // scan sheet survives a warm relaunch) or the keyboard covers it. A user would press back; a back
                // that leaves the app is undone by ensureForeground on the next pass. Bounded, and logged.
                if (backs < MAX_SETTLE_BACKS && quietSince - progressAt >= stuckAfter) {
                    backs++;
                    recovery("first run: no step and no bottom bar for " + stuckAfter + " ms; pressing back ("
                            + backs + "/" + MAX_SETTLE_BACKS + ")");
                    device.pressBack();
                    progressAt = SystemClock.uptimeMillis();
                    SystemClock.sleep(1_000);
                    continue;
                }
            } else {
                progressAt = SystemClock.uptimeMillis();
                if (progressAt - quietSince >= quiet) {
                    log("Home reached");
                    return;
                }
            }
            SystemClock.sleep(500);
        }
        fail("Home was not reached within " + limit + " ms");
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
        ensureForeground("tab '" + label + "'");
        UiObject2 cell = waitFor(() -> navCell(label), 15_000, "bottom bar cell '" + label + "'");
        try {
            cell.click();
        } catch (StaleObjectException e) {
            waitFor(() -> navCell(label), 15_000, "bottom bar cell '" + label + "'").click();
        }
        long window = scaled(10_000);
        long deadline = SystemClock.uptimeMillis() + window;
        long nextTap = SystemClock.uptimeMillis() + window / MAX_TAB_TAPS;
        int taps = 1;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 now = device.findObject(By.selected(true).hasDescendant(By.text(exactly(label))));
            if (now != null) {
                log("tab '" + label + "' selected");
                return;
            }
            // A slow emulator can drop a tap (run 45504112186), or a system "isn't responding" dialog swallows it
            // (beta13 run 45712994500, attempt 2): clear the screen and tap the cell again, a bounded number of times.
            if (taps < MAX_TAB_TAPS && SystemClock.uptimeMillis() >= nextTap) {
                ensureForeground("tab '" + label + "'");
                UiObject2 again = navCell(label);
                if (again != null) {
                    try {
                        again.click();
                        taps++;
                        recovery("tab '" + label + "' tapped again (" + taps + "/" + MAX_TAB_TAPS + ")");
                    } catch (StaleObjectException e) {
                        // Recomposed: the next poll looks again.
                    }
                }
                nextTap = SystemClock.uptimeMillis() + window / MAX_TAB_TAPS;
            }
            SystemClock.sleep(300);
        }
        fail("tab '" + label + "' never became selected (" + taps + " taps in " + window + " ms)");
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
        ensureForeground("Local screen");
        long deadline = SystemClock.uptimeMillis() + scaled(20_000);
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
            Lookup sheet = () -> device.findObject(By.text(exactly(sheetTitle)));
            String what = "scan sheet '" + sheetTitle + "'";
            // The first tap can be dropped on a slow emulator (beta13 run 45712994500, attempt 2). Tap the empty-state
            // button once more only while the sheet is not there: with the sheet open, a tap would land on its scrim.
            if (poll(sheet, scaled(5_000), what) == null) {
                UiObject2 again = device.findObject(By.text(exactly(emptyScan)));
                if (again != null && device.findObject(By.text(exactly(sheetTitle))) == null) {
                    try {
                        again.click();
                        recovery("the scan sheet did not open; tapping '" + emptyScan + "' again");
                    } catch (StaleObjectException e) {
                        // Recomposed: the wait below decides.
                    }
                }
            }
            waitFor(sheet, 10_000, what);
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
        UiObject2 row = device.wait(Until.findObject(By.text(TONE_ROW)), scaled(20_000));
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

    /** Waits {@link #scaled}(timeoutMs) for the lookup and fails with a capture when it never matches. */
    UiObject2 waitFor(Lookup lookup, long timeoutMs, String what) {
        long limit = scaled(timeoutMs);
        UiObject2 found = poll(lookup, limit, what);
        if (found == null) fail("timed out after " + limit + " ms waiting for " + what);
        return found;
    }

    /**
     * Polls for up to `timeoutMs` (already scaled) and returns null on timeout. While the lookup misses, every 3 s it
     * makes sure the app is in front and no system dialog covers it ({@link #ensureForeground}).
     */
    UiObject2 poll(Lookup lookup, long timeoutMs, String what) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        long nextCheck = 0;
        while (SystemClock.uptimeMillis() < deadline) {
            UiObject2 found = lookup.find();
            if (found != null) return found;
            if (SystemClock.uptimeMillis() >= nextCheck) {
                ensureForeground("waiting for " + what);
                nextCheck = SystemClock.uptimeMillis() + 3_000;
            }
            SystemClock.sleep(400);
        }
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

    /**
     * Every failure leaves a screenshot + window hierarchy named after it (pulled into the uploaded artifact) and says
     * in its message what was in front, so the next CI log is diagnosable even without the artifact.
     */
    void fail(String message) {
        String slug = message.length() > 60 ? message.substring(0, 60) : message;
        capture("FAIL-" + SystemClock.uptimeMillis() + "-" + slug);
        String detail = message + " [" + foregroundState() + "]";
        Log.e(TAG, detail);
        throw new AssertionError(detail);
    }

    /** The package on top and the focused window (component or system dialog names, nothing the user typed). */
    String foregroundState() {
        String focus = "";
        for (String line : shell("dumpsys window windows").split("\n")) {
            if (line.contains("mCurrentFocus=")) {
                focus = " " + line.trim();
                break;
            }
        }
        return "foreground=" + currentPackage() + focus;
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
