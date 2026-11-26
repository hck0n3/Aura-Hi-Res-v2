import contextlib
import io
import os
import re
import sqlite3
import tempfile
import unittest
import wave

import smoke_tools as st

PKG = "iad1tya.aura.music.dev.debug"


class ToneTest(unittest.TestCase):
    def test_tone_is_a_mono_16bit_wav_of_the_asked_length(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, st.TONE_NAME)
            frames = st.write_tone(path, seconds=2, rate=8000)
            with wave.open(path) as w:
                self.assertEqual(w.getnchannels(), 1)
                self.assertEqual(w.getsampwidth(), 2)
                self.assertEqual(w.getframerate(), 8000)
                self.assertEqual(w.getnframes(), frames)
                self.assertEqual(frames, 16000)
                self.assertNotEqual(w.readframes(100), b"\x00" * 200)


class InstrumentTest(unittest.TestCase):
    def test_ok_line_passes(self):
        out = "INSTRUMENTATION_STATUS_CODE: 1\n.\nTime: 12.3\n\nOK (1 test)\n\n"
        self.assertEqual(st.instrument_passed(out), (True, "OK (1)"))
        self.assertTrue(st.instrument_passed("Time: 1\n\nOK (3 tests)\n")[0])

    def test_zero_tests_is_a_failure_not_a_pass(self):
        passed, reason = st.instrument_passed("Time: 0.01\n\nOK (0 tests)\n")
        self.assertFalse(passed)
        self.assertIn("zero tests", reason)

    def test_failures_crashes_and_unfinished_runs_fail(self):
        self.assertFalse(st.instrument_passed("FAILURES!!!\nTests run: 1,  Failures: 1\n")[0])
        self.assertFalse(st.instrument_passed("INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n")[0])
        self.assertFalse(st.instrument_passed("INSTRUMENTATION_FAILED: x/y\n")[0])
        self.assertFalse(st.instrument_passed("INSTRUMENTATION_STATUS: test=a\n")[0])


class LogcatTest(unittest.TestCase):
    def test_finds_app_crashes_and_anrs_only(self):
        text = "\n".join([
            "11-23 12:00:00.000  100  100 E AndroidRuntime: FATAL EXCEPTION: main",
            "11-23 12:00:00.000  100  100 E AndroidRuntime: Process: %s, PID: 100" % PKG,
            "11-23 12:00:01.000  200  200 E AndroidRuntime: Process: com.android.systemui, PID: 200",
            "11-23 12:00:02.000  300  300 E ActivityManager: ANR in %s (%s/iad1tya.echo.music.MainActivity)" % (PKG, PKG),
            "11-23 12:00:03.000  300  300 E ActivityManager: ANR in com.android.systemui",
            "11-23 12:00:04.000  400  400 E AndroidRuntime: Process: %s:crash, PID: 400" % PKG,
            "11-23 12:00:05.000  500  500 F DEBUG   : pid: 500, tid: 500, name: x  >>> %s <<<" % PKG,
            "11-23 12:00:06.000  600  600 E AndroidRuntime: Process: %s.other, PID: 600" % PKG,
        ])
        hits = st.crash_lines(text, PKG)
        self.assertEqual(len(hits), 4)
        self.assertFalse(any("systemui" in h for h in hits))
        self.assertFalse(any(".other" in h for h in hits))

    def test_clean_logcat_has_no_hits(self):
        self.assertEqual(st.crash_lines("I ActivityManager: Start proc 1:%s/u0a1" % PKG, PKG), [])


class DbTest(unittest.TestCase):
    def _db(self, d, names, version=45):
        con = sqlite3.connect(os.path.join(d, "song.db"))
        con.execute("CREATE TABLE playlist (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
        for i, n in enumerate(names):
            con.execute("INSERT INTO playlist VALUES (?, ?)", ("LP%d" % i, n))
        con.execute("PRAGMA user_version = %d" % version)
        con.commit()
        con.close()

    def test_reports_the_playlist_and_version(self):
        with tempfile.TemporaryDirectory() as d:
            self._db(d, ["Aura smoke playlist", "Other"])
            with open(os.path.join(d, "song.db-shm"), "wb") as f:
                f.write(b"stale")
            r = st.db_report(d, "Aura smoke playlist")
            self.assertTrue(r["ok"])
            self.assertEqual(r["user_version"], 45)
            self.assertEqual(r["playlists"], 2)
            self.assertFalse(os.path.exists(os.path.join(d, "song.db-shm")))

    def test_expected_version_must_match(self):
        with tempfile.TemporaryDirectory() as d:
            self._db(d, ["Aura smoke playlist"], version=44)
            self.assertFalse(st.db_report(d, "Aura smoke playlist", expect_version=45)["ok"])
            self.assertTrue(st.db_report(d, "Aura smoke playlist", expect_version=44)["ok"])

    def test_room_version_is_read_from_the_real_database_class(self):
        here = os.path.dirname(os.path.abspath(__file__))
        path = os.path.join(here, "..", "..", "app", "src", "main", "kotlin", "com", "music", "echo", "db", "MusicDatabase.kt")
        with open(path, encoding="utf-8") as f:
            version = st.room_version(f.read())
        self.assertIsNotNone(version)
        self.assertGreaterEqual(version, 45)
        self.assertIsNone(st.room_version("val x = 1"))

    def test_missing_playlist_or_database_is_not_ok(self):
        with tempfile.TemporaryDirectory() as d:
            self.assertFalse(st.db_report(d, "x")["ok"])
            self._db(d, ["Other"])
            self.assertFalse(st.db_report(d, "Aura smoke playlist")["ok"])


class RecoveryTest(unittest.TestCase):
    def test_lists_only_the_smoke_apks_recovery_lines(self):
        text = "\n".join([
            "11-25 22:37:00.000  1234  1250 W AuraSmoke: RECOVERY tab 'Inicio / Home' tapped again (2/3)",
            "11-25 22:37:01.000  1234  1250 I AuraSmoke: tab 'Inicio / Home' selected",
            "11-25 22:37:02.000  1234  1250 W AuraSmoke: RECOVERY first run: answering 'wait' on a system dialog: "
            "System UI isn't responding",
            "11-25 22:37:03.000   900   900 W OtherTag: RECOVERY not ours",
        ])
        self.assertEqual(st.recovery_lines(text), [
            "tab 'Inicio / Home' tapped again (2/3)",
            "first run: answering 'wait' on a system dialog: System UI isn't responding",
        ])
        self.assertEqual(st.recovery_lines("I AuraSmoke: Home reached"), [])

    def test_missing_steps_file_is_zero_recoveries_not_a_failure(self):
        with tempfile.TemporaryDirectory() as d, contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(st.main(["recoveries", os.path.join(d, "absent.txt")]), 0)
        self.assertEqual(out.getvalue(), "0\n")


ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def _read(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
        return f.read()


_SMOKE = ("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest")


def _method(source, signature):
    """The body of one Java method, from its signature to the first line that closes it at class indentation."""
    body = source[source.index(signature):]
    return body[:body.index("\n    }\n")]


class SlowEmulatorTest(unittest.TestCase):
    """Row 885: betas 11 and 13 failed on 10-15 s UI waits with no app crash, a different scenario on each attempt. The
    helpers wait longer, keep the app in front and re-tap a bounded number of times, without touching what is asserted
    about the app itself."""

    def test_run_script_passes_a_validated_slow_factor(self):
        run = _read("scripts", "emulator_smoke", "run_smoke.sh")
        self.assertIn("SMOKE_SLOW=${SMOKE_SLOW:-2}", run)
        self.assertIn('[[ "$SMOKE_SLOW" =~ ^[1-5]$ ]]', run)
        self.assertIn('-e auraSlowFactor "$SMOKE_SLOW"', run)
        device = _read(*_SMOKE, "AuraDevice.java")
        self.assertIn('getString("auraSlowFactor")', device)
        self.assertIn("Math.max(1, Math.min(5, Integer.parseInt(arg.trim())))", device)

    def test_ui_waits_scale_but_playback_checks_do_not(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        wait = _method(device, "UiObject2 waitFor(Lookup lookup, long timeoutMs, String what) {")
        self.assertIn("long limit = scaled(timeoutMs);", wait)
        self.assertIn('fail("timed out after " + limit + " ms waiting for " + what);', wait)
        settle = _method(device, "void settleOnHome(long timeoutMs) {")
        self.assertIn("long limit = scaled(timeoutMs);", settle)
        # What the scenarios assert about the app keeps its own times: READY + playing, the position advancing.
        for name in ("LocalTone.java", "SessionProbe.java", "LocalPlaybackTest.java", "OfflinePlaybackTest.java",
                     "StreamingSmokeTest.java"):
            self.assertNotIn("scaled(", _read(*_SMOKE, name), name)
        tone = _read(*_SMOKE, "LocalTone.java")
        self.assertIn('30_000, "the tone to play (STATE_READY + isPlaying)"', tone)
        self.assertIn("LocalTone.assertAdvances(aura, probe, 7_000, 4_500", _read(*_SMOKE, "LocalPlaybackTest.java"))
        self.assertIn("LocalTone.assertAdvances(aura, probe, 10_000, 7_000", _read(*_SMOKE, "OfflinePlaybackTest.java"))
        survival = _read(*_SMOKE, "UpdateSurvivalTest.java")
        self.assertIn('assertFalse("the new welcome was shown after an update", aura.newWelcomeSeen)', survival)

    def test_foreground_recovery_never_hides_a_crash_and_is_bounded(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        self.assertIn("static final int MAX_RELAUNCHES = 3;", device)
        fore = _method(device, "void ensureForeground(String where) {")
        # The crash screen, the app's own ANR and a dead process fail BEFORE anything is relaunched.
        self.assertLess(fore.index("assertHealthy(where);"), fore.index("launchWarm();"))
        self.assertLess(fore.index("if (++relaunches > MAX_RELAUNCHES) {"), fore.index("launchWarm();"))
        healthy = _method(device, "void assertHealthy(String where) {")
        self.assertIn('line.contains("CrashActivity")', healthy)
        self.assertIn("pid().isEmpty()", healthy)
        dialogs = _method(device, "boolean dismissSystemDialogs(String where) {")
        self.assertLess(dialogs.index("if (text.contains(appLabel())) {"), dialogs.index("wait.click();"))
        poll = _method(device, "UiObject2 poll(Lookup lookup, long timeoutMs, String what) {")
        self.assertIn('ensureForeground("waiting for " + what);', poll)
        self.assertIn("poll(lookup, limit, what)", _method(device, "UiObject2 waitFor(Lookup lookup, long timeoutMs, String what) {"))

    def test_tabs_are_retapped_a_bounded_number_of_times_after_clearing_the_screen(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        self.assertIn("static final int MAX_TAB_TAPS = 3;", device)
        tab = _method(device, "void openTab(UiLabel label) {")
        self.assertIn("if (taps < MAX_TAB_TAPS && SystemClock.uptimeMillis() >= nextTap) {", tab)
        self.assertLess(tab.index("ensureForeground(\"tab '\" + label + \"'\");\n                UiObject2 again"),
                        tab.index("again.click();"))
        self.assertIn("never became selected", tab)

    def test_settle_presses_back_only_when_stuck_and_bounded(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        self.assertIn("static final int MAX_SETTLE_BACKS = 3;", device)
        settle = _method(device, "void settleOnHome(long timeoutMs) {")
        self.assertIn('ensureForeground("first run");', settle)
        self.assertNotIn('assertHealthy("first run");', settle)
        self.assertIn("if (backs < MAX_SETTLE_BACKS && quietSince - progressAt >= stuckAfter) {", settle)
        self.assertEqual(settle.count("device.pressBack();"), 1)

    def test_scan_sheet_is_retapped_only_while_it_is_not_open(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        scan = _method(device, "UiObject2 scanUntilToneListed() {")
        retap = scan[scan.index("if (poll(sheet, scaled(5_000), what) == null) {"):]
        self.assertLess(retap.index("device.findObject(By.text(exactly(sheetTitle))) == null"),
                        retap.index("again.click();"))
        self.assertIn("waitFor(sheet, 10_000, what);", scan)

    def test_every_fail_leaves_a_capture_and_says_what_was_in_front(self):
        device = _read(*_SMOKE, "AuraDevice.java")
        fail = _method(device, "void fail(String message) {")
        self.assertLess(fail.index('capture("FAIL-'), fail.index("throw new AssertionError(detail);"))
        self.assertIn("foregroundState()", fail)
        run = _read("scripts", "emulator_smoke", "run_smoke.sh")
        self.assertIn('adb pull "/sdcard/Android/data/$TEST_PKG/files" "$OUT/captures"', run)
        self.assertIn('adb shell dumpsys window windows > "$OUT/window-$name.txt"', run)
        self.assertIn('record "smoke recoveries" INFO', run)


class AppLiteralsTest(unittest.TestCase):
    """What the smoke APK (smoketest/) looks for in the app. Fails here, in seconds, instead of on the emulator."""

    def test_package_and_components(self):
        gradle = _read("app", "build.gradle.kts")
        self.assertIn('"iad1tya.aura.music.dev"', gradle)
        self.assertIn('applicationIdSuffix = ".debug"', gradle)
        manifest = _read("app", "src", "main", "AndroidManifest.xml")
        self.assertIn('android:name=".MainActivityAlias"', manifest)
        self.assertIn('android:name=".playback.MusicService"', manifest)
        self.assertRegex(manifest, r'android:name="\.MainActivity"\s+android:exported="true"')
        self.assertIn('namespace = "iad1tya.echo.music"', gradle)

    def test_hard_coded_first_run_and_settings_texts(self):
        screens = ("app", "src", "main", "kotlin", "com", "music", "echo", "ui", "screens")
        # Rows 871/872 (plan phase 2): artists and genres left the first run (Ajustes ▸ Contenido now); the new welcome's
        # steps are the labels AuraDevice.settleOnHome taps, read from the app's own strings.
        welcome = screens + ("welcome",)
        self.assertIn("Text(stringResource(R.string.welcome_continue))", _read(*welcome, "WelcomePermissionsScreen.kt"))
        connect = _read(*welcome, "WelcomeConnectScreen.kt")
        self.assertIn("R.string.welcome_later", connect)
        self.assertIn("Text(stringResource(R.string.welcome_done_start))", connect)
        # Decision A1 (2026-11-24, row 815): the onboarding step now reads «Siguiente: tu cuenta de Google»; the previous build
        # used in phase 2 still says «Siguiente: YouTube Music», so AuraDevice accepts both.
        self.assertIn('Text("Siguiente: tu cuenta de Google")', _read(*screens, "OnboardingSpotifyScreen.kt"))
        device = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest", "AuraDevice.java")
        self.assertIn("Siguiente: tu cuenta de Google|Siguiente: YouTube Music", device)
        for name in ("welcome_continue", "welcome_later", "welcome_done_start"):
            self.assertIn('appString("%s"' % name, device)
        self.assertIn('Text("Comenzar")', _read(*screens, "OnboardingYouTubeScreen.kt"))
        self.assertIn('"Omitir"', _read(*screens, "WelcomeDialog.kt"))
        self.assertIn('Text("Ajustes")', _read(*screens, "SettingDialoge.kt"))

    def test_string_resources_exist(self):
        values = os.path.join(ROOT, "app", "src", "main", "res", "values")
        text = "".join(_read("app", "src", "main", "res", "values", n) for n in sorted(os.listdir(values)) if n.endswith(".xml"))
        for name in ["home", "filter_library", "search", "tab_novedades", "account", "appearance", "filter_local",
                     "local_songs_empty_scan_action", "local_songs_scan_title", "scan_device", "scanning_device",
                     "open_links_prompt_later", "create_playlist", "filter_playlists", "terms_accept",
                     "welcome_continue", "welcome_later", "welcome_done_start"]:
            self.assertIn('<string name="%s">' % name, text, name)

    def test_terms_gate_is_walked_by_its_accept_label(self):
        # A fresh install renders TermsGate's acceptance screen INSTEAD of the app (first emulator run, 2026-11-23:
        # every scenario stuck on it). settleOnHome must tap the same resource the screen shows.
        terms = _read("app", "src", "main", "kotlin", "com", "music", "echo", "legal", "TermsScreens.kt")
        self.assertIn("stringResource(R.string.terms_accept)", terms)
        main = _read("app", "src", "main", "kotlin", "com", "music", "echo", "MainActivity.kt")
        self.assertIn("iad1tya.echo.music.legal.TermsGate {", main)
        device = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest", "AuraDevice.java")
        self.assertIn('appString("terms_accept"', device)

    def test_selected_tab_is_the_cell_above_its_label(self):
        # Second emulator run (45201346815): Compose hands UI Automator its UNMERGED tree, so `selected` sits on the
        # Role.Tab cell and the label on its Text child. By.text(label).selected(true) never matched: every scenario
        # that opens a tab failed with "never became selected" although the tap worked.
        shell = _read("app", "src", "main", "kotlin", "com", "music", "echo", "ui", "newui", "AuraShell.kt")
        item = shell[shell.index("private fun RowScope.AuraNavItem("):]
        item = item[:item.index("\n}\n")]
        self.assertIn("role = Role.Tab", item)
        self.assertIn("this.selected = selected", item)
        self.assertIn("text = label", item)
        device = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest", "AuraDevice.java")
        self.assertIn("By.selected(true).hasDescendant(By.text(exactly(label)))", device)
        self.assertNotIn("By.text(exactly(label)).selected(true)", device)

    def test_first_run_taps_survive_a_stale_node(self):
        # Second emulator run: the cold start died on StaleObjectException in settleOnHome's tap (the screen recomposed
        # between the lookup and the click). The loop must look again instead of failing the scenario.
        device = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest", "AuraDevice.java")
        settle = device[device.index("void settleOnHome("):device.index("UiObject2 navCell(")]
        self.assertIn("catch (StaleObjectException e)", settle)

    def test_library_plus_button_is_found_without_an_accessible_name(self):
        # Third emulator run (45243030897): 5a timed out on By.desc("create_playlist") on v2.0.69-beta2. AuraFab hands
        # its name only to the click ACTION (clickable(onClickLabel)), not to contentDescription, so the previous build
        # can only be found by shape. A later build that names the button is found by its name first.
        newui = ("app", "src", "main", "kotlin", "com", "music", "echo", "ui", "newui")
        content = _read(*newui, "AuraContent.kt")
        clickable = content[content.index("internal fun Modifier.auraClickableInternal("):]
        self.assertIn("onClickLabel = contentDescription,", clickable[:clickable.index("\n)\n")])
        library = _read(*newui, "AuraLibraryScreen.kt")
        self.assertIn("contentDescription = stringResource(R.string.create_playlist),", library)
        survival = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest",
                         "UpdateSurvivalTest.java")
        self.assertIn("aura.waitFor(() -> libraryPlusButton(aura, create), 15_000", survival)
        finder = survival[survival.index("private static UiObject2 libraryPlusButton("):]
        self.assertIn("By.desc(AuraDevice.exactly(create))", finder)
        self.assertIn("By.clickable(true)", finder)
        self.assertIn("catch (StaleObjectException e)", finder)

    def test_create_menu_item_is_not_the_empty_tab_chip(self):
        # Fifth emulator run (45323606796): 5a on v2.0.69-beta3 timed out on the playlist name field. The empty
        # Playlists tab draws «Crear lista» as its own chip, already on screen when the '+' menu opens, so a plain
        # By.text lookup returned the chip and the tap only closed the menu. 5a must pick the label that is new.
        newui = ("app", "src", "main", "kotlin", "com", "music", "echo", "ui", "newui")
        tabs = _read(*newui, "AuraLibraryTabs.kt")
        self.assertIn("stringResource(R.string.create_playlist)\n                    } else {", tabs)
        library = _read(*newui, "AuraLibraryScreen.kt")
        self.assertIn("onCreatePlaylist = { showCreatePlaylistDialog = true },", library)
        self.assertIn("text = { Text(stringResource(R.string.create_playlist)", library)
        survival = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest",
                         "UpdateSurvivalTest.java")
        create = survival[survival.index("public void createPlaylistOnPreviousBuild("):]
        create = create[:create.index("\n    }\n")]
        self.assertLess(create.index("List<Rect> before = labelBounds(aura, create);"), create.index("fab.click();"))
        self.assertIn("aura.waitFor(() -> newLabel(aura, create, before), 10_000", create)
        self.assertNotIn("aura.device.findObject(By.text(AuraDevice.exactly(create)))", create)
        fresh = survival[survival.index("private static UiObject2 newLabel("):]
        self.assertIn("!before.contains(r)", fresh[:fresh.index("\n    }\n")])

    def test_update_check_does_not_toggle_the_playlists_chip_off(self):
        # Fourth emulator run (45249314558): 5b timed out on the playlist although 5c found it in the database. The
        # filter is a persisted preference that 5a left on Playlists, and tapping the active chip returns to the hub
        # (pinned playlists only). 5b must look before tapping the chip.
        library = _read("app", "src", "main", "kotlin", "com", "music", "echo", "ui", "newui", "AuraLibraryScreen.kt")
        self.assertIn("rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)", library)
        self.assertIn("filterType = if (filterType == chip) LibraryFilter.LIBRARY else chip", library)
        survival = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest",
                         "UpdateSurvivalTest.java")
        check = survival[survival.index("public void playlistSurvivesUpdate("):]
        look = check.index("aura.device.wait(Until.findObject(created), 10_000) == null")
        self.assertLess(look, check.index("playlists.click()"))
        self.assertNotIn("if (playlists != null) playlists.click();\n        aura.waitFor(", check)

    def test_labels_are_looked_up_in_the_language_the_app_seeds(self):
        # A fresh install stores AppLanguageKey = es-419 and MainActivity applies it from the next cold start, so the
        # emulator (en-US) and the app disagree on every label: AuraDevice must look them up in that same language.
        app = _read("app", "src", "main", "kotlin", "com", "music", "echo", "App.kt")
        seeds = re.findall(r'p\[iad1tya\.echo\.music\.constants\.AppLanguageKey\] = "([^"]+)"', app)
        self.assertGreaterEqual(len(seeds), 2, "applySeedDefaults + applyLyricsEsLatamAutoTranslateV1")
        self.assertEqual(set(seeds), {"es-419"})
        main = _read("app", "src", "main", "kotlin", "com", "music", "echo", "MainActivity.kt")
        self.assertIn("super.attachBaseContext(localeAwareContext(newBase))", main)
        device = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest", "AuraDevice.java")
        self.assertIn('static final String APP_SEED_LANGUAGE = "%s";' % seeds[0], device)
        survival = _read("smoketest", "src", "main", "java", "iad1tya", "echo", "music", "smoketest",
                         "UpdateSurvivalTest.java")
        self.assertIn("aura.frameworkString(android.R.string.ok)", survival)
        self.assertNotIn("Resources.getSystem()", survival)


if __name__ == "__main__":
    unittest.main()
