#!/usr/bin/env bash
# Emulator smoke run (docs/QA_EMULATOR.md). Called ONCE by .github/workflows/emulator-smoke.yml inside
# reactivecircus/android-emulator-runner, with the emulator already booted.
#
# Inputs (environment):
#   APP_APK          the new debug APK (-Pnosub=true, x86_64 FOSS)            required
#   TEST_APK         the self-instrumenting smoke APK (:smoketest)            required
#   PREV_APK         the PREVIOUS build's debug APK, for the update scenario  optional (missing = scenario FAILS)
#   RUN_UPDATE       "true" (default) / "false" to skip the update scenario on purpose
#   RUN_STREAMING    "true" (default) / "false"
#   STREAM_VIDEO_ID  YouTube video id for the streaming scenario (11 chars)
#   TONE_WAV         the tone written by smoke_tools.py tone
#   OUT_DIR          where every log, screenshot and result goes (uploaded as an artifact)
#   SMOKE_SLOW       1-5, default 2: every UI wait of the smoke APK is multiplied by it (AuraDevice.scaled). The
#                    shared runner's emulator failed betas 11 and 13 on 10-15 s waits with no app crash (row 885).
#                    The playback checks (state, position advancing) are NOT scaled.
#
# Exit status: 1 when any REQUIRED scenario failed or the app crashed / hit an ANR during the new build's phases.
# The streaming scenario only ever warns: YouTube may refuse datacenter IPs.
set -uo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
OUT=${OUT_DIR:?OUT_DIR is required}
mkdir -p "$OUT"
RESULTS="$OUT/results.txt"
: > "$RESULTS"

EXPECTED_PKG="iad1tya.aura.music.dev.debug"
SMOKE_PLAYLIST="Aura smoke playlist"   # UpdateSurvivalTest.PLAYLIST
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS_PREFIX="iad1tya.echo.music.smoketest"
RUN_UPDATE=${RUN_UPDATE:-true}
RUN_STREAMING=${RUN_STREAMING:-true}
STREAM_VIDEO_ID=${STREAM_VIDEO_ID:-_KZEkEb_dvA}
SMOKE_SLOW=${SMOKE_SLOW:-2}
FAILED=0

# adb shell joins its arguments into a remote shell command line: only a plain video id may get there.
if ! [[ "$STREAM_VIDEO_ID" =~ ^[A-Za-z0-9_-]{11}$ ]]; then
  echo "::error::STREAM_VIDEO_ID is not an 11-character YouTube id"
  exit 1
fi
if ! [[ "$SMOKE_SLOW" =~ ^[1-5]$ ]]; then
  echo "::error::SMOKE_SLOW must be a single digit from 1 to 5"
  exit 1
fi

BT=$(ls -d "${ANDROID_SDK_ROOT:-$ANDROID_HOME}"/build-tools/* | sort -V | tail -n 1)
pkg_of() { "$BT/aapt2" dump packagename "$1" 2>/dev/null | tr -d '\r\n'; }

record() { # name status detail
  echo "$1|$2|$3" >> "$RESULTS"
  echo "[$2] $1 — $3"
  if [ "$2" = "FAIL" ]; then FAILED=1; fi
}

dump_logcat() { # file — dump and clear, one file per phase
  adb logcat -d -v threadtime > "$1" 2>&1 || true
  adb logcat -c || true
}

crash_check() { # phase logcat-file FAIL|WARN
  local hits
  if hits=$(python3 "$HERE/smoke_tools.py" logcat "$2" "$PKG"); then
    record "$1: crashes/ANR" PASS "none in logcat"
  else
    echo "$hits" > "$OUT/crashes-$1.txt"
    record "$1: crashes/ANR" "$3" "$(echo "$hits" | head -n 1)"
  fi
}

app_log() { # file — the app's own persisted log (AppLogger), what the owner would share from Ajustes ▸ Registros
  adb exec-out run-as "$PKG" cat files/logs/app.log > "$1" 2>/dev/null || true
}

grant_app() {
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
  adb shell pm grant "$PKG" android.permission.READ_MEDIA_AUDIO || true
}

network_on() {
  adb shell cmd connectivity airplane-mode disable || true
  adb shell svc wifi enable || true
  adb shell svc data enable || true
}

install_apk() { # apk label
  if adb install -r -t "$1" > "$OUT/install-$2.txt" 2>&1; then
    return 0
  fi
  cat "$OUT/install-$2.txt"
  return 1
}

run_test() { # name Class#method required|optional
  local name=$1 test=$2 mode=$3 out="$OUT/instrument-$1.txt" verdict rc
  echo "::group::$name ($test)"
  timeout 900 adb shell am instrument -w \
    -e class "$TEST_CLASS_PREFIX.$test" \
    -e auraTargetPackage "$PKG" \
    -e auraStreamVideoId "$STREAM_VIDEO_ID" \
    -e auraSlowFactor "$SMOKE_SLOW" \
    "$TEST_PKG/$RUNNER" > "$out" 2>&1
  cat "$out"
  verdict=$(python3 "$HERE/smoke_tools.py" instrument "$out")
  rc=$?
  adb exec-out screencap -p > "$OUT/screen-$name.png" 2>/dev/null || true
  if [ $rc -ne 0 ]; then
    # Also when the test died outside AuraDevice.fail (its own FAIL-* capture): what was on screen and in front.
    adb shell dumpsys window windows > "$OUT/window-$name.txt" 2>&1 || true
    adb shell uiautomator dump /data/local/tmp/aura-smoke-ui.xml > /dev/null 2>&1 \
      && adb pull /data/local/tmp/aura-smoke-ui.xml "$OUT/ui-$name.xml" > /dev/null 2>&1 || true
  fi
  echo "::endgroup::"
  if [ $rc -eq 0 ]; then
    record "$name" PASS "$verdict"
  elif [ "$mode" = "optional" ]; then
    record "$name" WARN "$verdict (allowed to fail)"
  else
    record "$name" FAIL "$verdict"
  fi
  return $rc
}

copy_db() { # dest-dir — the app is stopped first so the database + WAL pair is consistent
  mkdir -p "$1"
  adb shell am force-stop "$PKG" || true
  sleep 2
  local present
  present=$(adb shell run-as "$PKG" ls databases 2>/dev/null | tr -d '\r')
  for f in song.db song.db-wal; do
    if echo "$present" | grep -qx "$f"; then
      adb exec-out run-as "$PKG" cat "databases/$f" > "$1/$f"
    fi
  done
}

# ── Setup ────────────────────────────────────────────────────────────────────────────────────────
adb wait-for-device
adb logcat -G 16M || true
adb logcat -c || true

PKG=$(pkg_of "$APP_APK")
TEST_PKG=$(pkg_of "$TEST_APK")
echo "app: $PKG  smoke: $TEST_PKG  build-tools: $BT"
if [ "$PKG" != "$EXPECTED_PKG" ]; then
  echo "::error::the app APK is '$PKG', expected '$EXPECTED_PKG' (built without -Pnosub=true?)"
  exit 1
fi
if [ -z "$TEST_PKG" ]; then
  echo "::error::could not read the smoke APK's package name"
  exit 1
fi

install_apk "$TEST_APK" smoke || { echo "::error::could not install the smoke APK"; exit 1; }
adb shell pm grant "$TEST_PKG" android.permission.READ_MEDIA_AUDIO || true

# /sdcard can refuse writes for a few seconds after boot (run 45500477817: «remote couldn't create file: Operation not
# permitted», then 3 and 4 failed on a tone that was never there). Retry, and check the whole file landed.
TONE_DEST=/sdcard/Music/aura_smoke_tone.wav
TONE_SIZE=$(stat -c %s "$TONE_WAV")
tone_ok=0
for attempt in $(seq 1 30); do
  adb shell mkdir -p /sdcard/Music > /dev/null 2>&1
  if adb push "$TONE_WAV" "$TONE_DEST" \
     && [ "$(adb shell stat -c %s "$TONE_DEST" 2>/dev/null | tr -d '\r')" = "$TONE_SIZE" ]; then
    tone_ok=1; break
  fi
  echo "tone push attempt $attempt failed; storage not ready yet, retrying in 2 s"
  sleep 2
done
if [ $tone_ok -ne 1 ]; then
  echo "::error::could not push the test tone into /sdcard/Music"
  record setup-tone FAIL "adb push to /sdcard/Music refused after 30 attempts"
fi
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d "file://$TONE_DEST" > /dev/null || true

# ── Phase 1: fresh install of the NEW build ──────────────────────────────────────────────────────
adb uninstall "$PKG" > /dev/null 2>&1 || true
if install_apk "$APP_APK" new; then
  grant_app
  run_test 1-cold-start ColdStartTest#coldStartReachesHome required
  run_test 2-navigation NavigationTest#topLevelScreensRenderAndComeBack required
  run_test 3-local-playback LocalPlaybackTest#scannedLocalTonePlaysAndAdvances required
  run_test 4-offline OfflinePlaybackTest#localPlaybackSurvivesNetworkLoss required
  network_on
  adb shell dumpsys media_session > "$OUT/media_session-fresh.txt" 2>&1 || true
  app_log "$OUT/app-fresh-install.log"
else
  record "install new build" FAIL "adb install failed"
fi
dump_logcat "$OUT/logcat-fresh-install.txt"
crash_check fresh-install "$OUT/logcat-fresh-install.txt" FAIL

# ── Phase 2: update survival (previous build → new build over it) ────────────────────────────────
if [ "$RUN_UPDATE" != "true" ]; then
  record 5-update-survival SKIP "disabled for this run"
elif [ -z "${PREV_APK:-}" ] || [ ! -f "$PREV_APK" ]; then
  record 5-update-survival FAIL "no previous-build APK (see the 'Build the previous app' step)"
elif [ "$(pkg_of "$PREV_APK")" != "$PKG" ]; then
  record 5-update-survival FAIL "previous APK is '$(pkg_of "$PREV_APK")', not '$PKG'"
else
  adb uninstall "$PKG" > /dev/null 2>&1 || true
  if ! install_apk "$PREV_APK" previous; then
    record 5-update-survival FAIL "could not install the previous build"
  else
    grant_app
    if run_test 5a-create-on-previous UpdateSurvivalTest#createPlaylistOnPreviousBuild required; then
      app_log "$OUT/app-previous-build.log"
      copy_db "$OUT/db-before-update"
      if BEFORE=$(python3 "$HERE/smoke_tools.py" db "$OUT/db-before-update" "$SMOKE_PLAYLIST"); then
        echo "database before update: $BEFORE"
      else
        # The previous build did not store the playlist: the 'after' check could not mean anything.
        record 5-database-before-update FAIL "$BEFORE"
      fi
      dump_logcat "$OUT/logcat-previous-build.txt"
      crash_check previous-build "$OUT/logcat-previous-build.txt" WARN
      if install_apk "$APP_APK" update; then
        grant_app
        run_test 5b-survives-update UpdateSurvivalTest#playlistSurvivesUpdate required
        app_log "$OUT/app-after-update.log"
        copy_db "$OUT/db-after-update"
        # The copy must also be at the NEW build's Room version: the update's migration really ran.
        if AFTER=$(python3 "$HERE/smoke_tools.py" db "$OUT/db-after-update" "$SMOKE_PLAYLIST" \
            --expect-version-from "$HERE/../../app/src/main/kotlin/com/music/echo/db/MusicDatabase.kt"); then
          record 5c-database-after-update PASS "$AFTER (before: $BEFORE)"
        else
          record 5c-database-after-update FAIL "$AFTER (before: $BEFORE)"
        fi
      else
        record 5-update-survival FAIL "adb install -r of the new build over the previous one failed"
      fi
    fi
  fi
fi
dump_logcat "$OUT/logcat-update.txt"
crash_check update "$OUT/logcat-update.txt" FAIL

# ── Phase 3: streaming smoke (allowed to fail) ───────────────────────────────────────────────────
if [ "$RUN_STREAMING" = "true" ]; then
  network_on
  # Whatever phase 2 left installed (it can stop half way on the previous build), stream on the NEW one.
  install_apk "$APP_APK" streaming || true
  grant_app
  run_test 6-streaming StreamingSmokeTest#publicSongPlaysFromDeepLink optional
  adb shell dumpsys media_session > "$OUT/media_session-streaming.txt" 2>&1 || true
  app_log "$OUT/app-streaming.log"
  dump_logcat "$OUT/logcat-streaming.txt"
  crash_check streaming "$OUT/logcat-streaming.txt" FAIL
else
  record 6-streaming SKIP "disabled for this run"
fi

# ── Evidence ─────────────────────────────────────────────────────────────────────────────────────
adb pull "/sdcard/Android/data/$TEST_PKG/files" "$OUT/captures" > /dev/null 2>&1 || true
cat "$OUT"/logcat-*.txt 2>/dev/null | grep "AuraSmoke" > "$OUT/smoke-steps.txt" || true
# What the smoke had to do to keep going (system dialog answered, app relaunched, tab re-tapped, back pressed): listed,
# never a pass or a fail by itself, so a slow emulator that needed help is visible in the summary.
python3 "$HERE/smoke_tools.py" recoveries "$OUT/smoke-steps.txt" > "$OUT/recoveries.txt" 2>&1 || true
record "smoke recoveries" INFO "$(head -n 1 "$OUT/recoveries.txt") (recoveries.txt)"

{
  echo "## Emulator smoke"
  echo
  echo "| Scenario | Result | Detail |"
  echo "|---|---|---|"
  while IFS='|' read -r name status detail; do
    echo "| $name | $status | ${detail//|//} |"
  done < "$RESULTS"
} > "$OUT/summary.md"
cat "$OUT/summary.md"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat "$OUT/summary.md" >> "$GITHUB_STEP_SUMMARY"; fi

exit $FAILED
