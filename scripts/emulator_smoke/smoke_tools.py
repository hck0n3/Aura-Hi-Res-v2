"""Host-side helpers for the emulator smoke run (.github/workflows/emulator-smoke.yml, docs/QA_EMULATOR.md).

Everything here runs on the GitHub runner, never on the device, and only reads files the run produced:
  tone           writes the test tone pushed into the emulator's Music folder
  instrument     decides whether one `am instrument -w` run passed (0 tests run is a FAILURE, not a pass)
  logcat         finds crashes and ANRs of the app under test in a logcat dump
  db             reads the app's Room database copied off the emulator (update-survival check)
"""

import argparse
import json
import math
import os
import re
import sqlite3
import struct
import sys
import wave

TONE_NAME = "aura_smoke_tone.wav"


def write_tone(path, seconds=90, rate=22050, freq=440.0, amplitude=0.25):
    """A mono 16-bit PCM sine. WAV is in SupportedLocalAudio's list, so the app's scanner imports it."""
    frames = int(seconds * rate)
    peak = int(32767 * amplitude)
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        chunk = []
        for i in range(frames):
            chunk.append(struct.pack("<h", int(peak * math.sin(2.0 * math.pi * freq * i / rate))))
            if len(chunk) == rate:
                out.writeframes(b"".join(chunk))
                chunk = []
        if chunk:
            out.writeframes(b"".join(chunk))
    return frames


_OK = re.compile(r"^OK \((\d+) tests?\)\s*$", re.MULTILINE)


def instrument_passed(output):
    """(passed, reason) for the text of one `am instrument -w` run."""
    if "INSTRUMENTATION_FAILED" in output:
        return False, "instrumentation failed to start"
    if "Process crashed" in output:
        return False, "the test process crashed"
    if "FAILURES!!!" in output:
        return False, "assertion failures"
    match = _OK.search(output)
    if not match:
        return False, "no OK line (the run did not finish)"
    if int(match.group(1)) == 0:
        return False, "zero tests ran (wrong class or method name?)"
    return True, "OK (%s)" % match.group(1)


def crash_lines(logcat_text, package):
    """Lines that prove a crash or an ANR of `package` (or of one of its :sub processes)."""
    pkg = re.escape(package)
    fatal_process = re.compile(r"AndroidRuntime: Process: " + pkg + r"(:[\w.]+)?, PID")
    anr = re.compile(r"ANR in " + pkg + r"(:[\w.]+)?(\s|$|\()")
    native = re.compile(r">>> " + pkg + r"(:[\w.]+)? <<<")
    hits = []
    for line in logcat_text.splitlines():
        if fatal_process.search(line) or anr.search(line) or native.search(line):
            hits.append(line.strip())
    return hits


def room_version(database_kt_text):
    """The `version = N` of the @Database annotation (Room writes it to PRAGMA user_version)."""
    match = re.search(r"^\s*version\s*=\s*(\d+)\s*,", database_kt_text, re.MULTILINE)
    return int(match.group(1)) if match else None


def db_report(db_dir, playlist_name, expect_version=None):
    """user_version and whether a playlist called `playlist_name` exists, from a copy of song.db (+ its -wal).
    With `expect_version`, the copy must also be at that schema version (the update's migration really ran)."""
    db_path = os.path.join(db_dir, "song.db")
    if not os.path.isfile(db_path):
        return {"ok": False, "error": "song.db was not copied off the device"}
    # A -shm copied from another machine is not trustworthy; SQLite rebuilds it from the -wal.
    shm = db_path + "-shm"
    if os.path.exists(shm):
        os.remove(shm)
    con = sqlite3.connect(db_path)
    try:
        version = con.execute("PRAGMA user_version").fetchone()[0]
        rows = con.execute("SELECT COUNT(*) FROM playlist WHERE name = ?", (playlist_name,)).fetchone()[0]
        total = con.execute("SELECT COUNT(*) FROM playlist").fetchone()[0]
    finally:
        con.close()
    report = {"ok": rows > 0, "user_version": version, "matching_playlists": rows, "playlists": total}
    if expect_version is not None:
        report["expected_version"] = expect_version
        report["ok"] = report["ok"] and version == expect_version
    return report


def main(argv=None):
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="cmd", required=True)
    tone = sub.add_parser("tone")
    tone.add_argument("out")
    tone.add_argument("--seconds", type=float, default=90)
    inst = sub.add_parser("instrument")
    inst.add_argument("output_file")
    logcat = sub.add_parser("logcat")
    logcat.add_argument("logcat_file")
    logcat.add_argument("package")
    db = sub.add_parser("db")
    db.add_argument("db_dir")
    db.add_argument("playlist_name")
    db.add_argument("--expect-version-from", help="MusicDatabase.kt whose @Database version the copy must have")
    args = parser.parse_args(argv)

    if args.cmd == "tone":
        frames = write_tone(args.out, seconds=args.seconds)
        print("wrote %s (%d frames)" % (args.out, frames))
        return 0
    if args.cmd == "instrument":
        with open(args.output_file, encoding="utf-8", errors="replace") as f:
            passed, reason = instrument_passed(f.read())
        print(reason)
        return 0 if passed else 1
    if args.cmd == "logcat":
        with open(args.logcat_file, encoding="utf-8", errors="replace") as f:
            hits = crash_lines(f.read(), args.package)
        for line in hits:
            print(line)
        return 1 if hits else 0
    if args.cmd == "db":
        expected = None
        if args.expect_version_from:
            with open(args.expect_version_from, encoding="utf-8") as f:
                expected = room_version(f.read())
            if expected is None:
                print(json.dumps({"ok": False, "error": "no @Database version in " + args.expect_version_from}))
                return 1
        report = db_report(args.db_dir, args.playlist_name, expected)
        print(json.dumps(report))
        return 0 if report.get("ok") else 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
