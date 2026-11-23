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


ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def _read(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
        return f.read()


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
        self.assertIn('Text("Omitir")', _read(*screens, "OnboardingArtistsScreen.kt"))
        self.assertIn('Text("Omitir")', _read(*screens, "OnboardingGenresScreen.kt"))
        self.assertIn('Text("Siguiente: YouTube Music")', _read(*screens, "OnboardingSpotifyScreen.kt"))
        self.assertIn('Text("Comenzar")', _read(*screens, "OnboardingYouTubeScreen.kt"))
        self.assertIn('"Omitir"', _read(*screens, "WelcomeDialog.kt"))
        self.assertIn('Text("Ajustes")', _read(*screens, "SettingDialoge.kt"))

    def test_string_resources_exist(self):
        values = os.path.join(ROOT, "app", "src", "main", "res", "values")
        text = "".join(_read("app", "src", "main", "res", "values", n) for n in sorted(os.listdir(values)) if n.endswith(".xml"))
        for name in ["home", "filter_library", "search", "tab_novedades", "account", "appearance", "filter_local",
                     "local_songs_empty_scan_action", "local_songs_scan_title", "scan_device", "scanning_device",
                     "open_links_prompt_later", "create_playlist", "filter_playlists", "terms_accept"]:
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
