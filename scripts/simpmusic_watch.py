"""Watches SimpMusic for playback fixes Aura should port (owner 2026-11-24).

Owner, literal: «quiero que cuando falle, si hay un vigilante, que tome la solución de SimpMusic». Aura uses the same
stream extractors as SimpMusic, so when SimpMusic ships a playback fix this script says what changed and which Aura files
it maps to (docs/watch/README.md). It only OBSERVES: it never edits Aura's code, never writes player_configs.json and never
publishes anything (AGENTS.md: never publish unverified player values; main and stable releases only on the owner's order).

Signals, against docs/watch/simpmusic_seen.json:
  S1  pipepipe / brave-newpipe pins in SimpMusic's gradle/libs.versions.toml that differ from the seen ones (and from
      Aura's own pins in gradle/libs.versions.toml).
  S2  commits and diffs in maxrave-dev/core (at the commit SimpMusic's `core` submodule pins) that touch the playback
      files listed in CORE_PATHS, since the seen core commit.
  S3  player ids in maxrave-dev/simpmusic-files registry/player_configs.json that Aura's player_configs.json lacks:
      `new_since_seen` (added to their registry since the seen commit; this is what sets "changed") and the whole
      `backlog_count` with its newest entries. They are LEADS, not values to copy: each one must pass
      scripts/verify/player_verify.mjs before it goes anywhere.

Usage (stdlib only, run isolated):  python3 -I scripts/simpmusic_watch.py [--update-state]
Prints one JSON object. Exit code 0 even when there is news (read "changed"); 2 when a repository could not be read.
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
STATE = os.path.join(ROOT, "docs", "watch", "simpmusic_seen.json")
OURS = os.path.join(ROOT, "player_configs.json")
AURA_TOML = os.path.join(ROOT, "gradle", "libs.versions.toml")

SIMPMUSIC_URL = "https://github.com/maxrave-dev/SimpMusic.git"
CORE_URL = "https://github.com/maxrave-dev/core.git"
FILES_URL = "https://github.com/maxrave-dev/simpmusic-files.git"
REGISTRY_PATH = "registry/player_configs.json"

_SCRAPER = "service/kotlinYtmusicScraper/src/"
_PKG = "kotlin/com/maxrave/kotlinytmusicscraper/"
# Directories end with "/" (prefix match); anything else is an exact file.
CORE_PATHS = [
    _SCRAPER + "commonMain/" + _PKG + "cipher/",
    _SCRAPER + "commonMain/" + _PKG + "YouTube.kt",
    _SCRAPER + "commonMain/" + _PKG + "Ytmusic.kt",
    _SCRAPER + "commonMain/" + _PKG + "models/YouTubeClient.kt",
    _SCRAPER + "androidMain/" + _PKG + "extractor/",
    _SCRAPER + "commonMain/assets/po_token.html",
    "data/src/commonMain/kotlin/com/maxrave/data/repository/StreamRepositoryImpl.kt",
]

_CIPHER = "app/src/main/kotlin/com/music/echo/utils/cipher/"
_YTP = "app/src/main/kotlin/com/music/echo/utils/YTPlayerUtils.kt"
_IT = "innertube/src/main/kotlin/com/music/innertube/"
# SimpMusic file (or prefix) -> the Aura files where the same logic lives. First match wins, most specific first.
AURA_MAP = [
    (CORE_PATHS[0] + "RemotePlayerConfig", [_CIPHER + "RemotePlayerConfig.kt", "player_configs.json"]),
    (CORE_PATHS[0] + "PlayerConfigRepository.kt", [_CIPHER + "RemotePlayerConfig.kt", "player_configs.json"]),
    (CORE_PATHS[0], [_CIPHER + "CipherWebView.kt", _CIPHER + "CipherDeobfuscator.kt", _CIPHER + "PlayerJsFetcher.kt",
                     "app/src/main/kotlin/com/music/echo/utils/sabr/EjsNTransformSolver.kt"]),
    (CORE_PATHS[1], [_YTP + " (playerResponseForPlaybackImpl, resolvePlaybackData, newPipeAudioRescue)", _IT + "YouTube.kt"]),
    (CORE_PATHS[2], [_IT + "InnerTube.kt"]),
    (CORE_PATHS[3], [_IT + "models/YouTubeClient.kt", _YTP + " (client choice)"]),
    (CORE_PATHS[4] + "FaradayJsDecoder.kt", [_CIPHER + "PipePipeLocalCipherDecoder.kt"]),
    (CORE_PATHS[4], [_IT + "pages/NewPipe.kt", _IT + "pages/BraveNewPipe.kt", _CIPHER + "PipePipeLocalCipherDecoder.kt"]),
    (CORE_PATHS[5], ["app/src/main/kotlin/com/music/echo/utils/potoken/"]),
    (CORE_PATHS[6], [_YTP + " (resolvePlaybackData)"]),
]

MAX_DIFF_CHARS = 200_000
BACKLOG_SHOWN = 30
HASH_RE = re.compile(r"^[0-9a-f]{8}$")


# ---------------------------------------------------------------- pure parts (unit-tested)

def parse_versions(toml_text, keys):
    """Values of `keys` inside the [versions] table of a Gradle version catalog."""
    out = {}
    section = None
    for raw in toml_text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        m = re.fullmatch(r"\[([^\]]+)\]", line)
        if m:
            section = m.group(1).strip()
            continue
        if section != "versions":
            continue
        m = re.fullmatch(r'([A-Za-z0-9_.-]+)\s*=\s*"([^"]*)"', line)
        if m and m.group(1) in keys:
            out[m.group(1)] = m.group(2)
    return out


def simpmusic_pins(toml_text):
    v = parse_versions(toml_text, {"pipepipe", "brave-newpipe"})
    return {"pipepipe": v.get("pipepipe"), "brave": v.get("brave-newpipe")}


def aura_pins(toml_text):
    v = parse_versions(toml_text, {"pipepipeextractor", "bravenewpipeextractor"})
    return {"pipepipe": v.get("pipepipeextractor"), "brave": v.get("bravenewpipeextractor")}


def same_commit(a, b):
    """Two (possibly abbreviated) commit ids name the same commit when one is a prefix of the other."""
    if not a or not b:
        return False
    a, b = a.lower(), b.lower()
    return a.startswith(b) or b.startswith(a)


def pin_changes(current, reference):
    """[{key, old, new}] for every key whose commit differs between `reference` and `current`."""
    changes = []
    for key in ("pipepipe", "brave"):
        new, old = current.get(key), reference.get(key)
        if new and not same_commit(new, old):
            changes.append({"key": key, "old": old, "new": new})
    return changes


def is_watched(path):
    for p in CORE_PATHS:
        if (p.endswith("/") and path.startswith(p)) or path == p:
            return True
    return False


def watched(paths):
    return [p for p in paths if is_watched(p)]


def aura_targets(path):
    for prefix, targets in AURA_MAP:
        if path.startswith(prefix):
            return targets
    return []


def simpmusic_players(text):
    """{player id: {sts, sig, nClass, primary}} from simpmusic-files' registry, aliases included."""
    data = json.loads(text)
    players = data.get("players", {}) if isinstance(data, dict) else {}
    out = {}
    for pid, entry in players.items():
        if not isinstance(entry, dict):
            continue
        info = {"sts": entry.get("sts"), "sig": entry.get("sig"), "nClass": entry.get("nClass")}
        for h in [pid] + list(entry.get("aliases") or []):
            h = str(h).strip().lower()
            if HASH_RE.match(h) and h not in out:
                out[h] = dict(info, primary=str(pid).lower())
    return out


def our_hashes(text):
    """Player hashes in Aura's player_configs.json (bare array or {"configs": [...]}, as RemotePlayerConfig reads it)."""
    data = json.loads(text)
    entries = data.get("configs", []) if isinstance(data, dict) else data
    return {str(e.get("hash", "")).strip().lower() for e in entries if isinstance(e, dict)} - {""}


def new_players(simpmusic_text, ours_text):
    """Players SimpMusic knows and Aura does not: leads to verify with player_verify.mjs, never values to publish."""
    theirs = simpmusic_players(simpmusic_text)
    ours = our_hashes(ours_text)
    return [dict(theirs[h], hash=h) for h in sorted(theirs, key=lambda k: (theirs[k].get("sts") or 0, k), reverse=True)
            if h not in ours]


def added_since(old_text, new_text):
    """Player ids the registry gained between two of its versions (None = old version unknown)."""
    if old_text is None:
        return None
    old = simpmusic_players(old_text)
    return {h for h in simpmusic_players(new_text) if h not in old}


def cap(text, limit=MAX_DIFF_CHARS):
    if len(text) <= limit:
        return text, False
    return text[:limit], True


# ---------------------------------------------------------------- git (needs network)

def git(*args, cwd=None):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        raise RuntimeError("git %s failed: %s" % (args[0], r.stderr.strip()[-500:]))
    return r.stdout


def clone(url, dest, shallow):
    args = ["clone", "--quiet", "--filter=blob:none", "--no-checkout"]
    if shallow:
        args.append("--depth=1")
    git(*args, url, dest)


def has_commit(repo, sha):
    return subprocess.run(["git", "cat-file", "-e", sha + "^{commit}"], cwd=repo, capture_output=True).returncode == 0


def ensure_commit(repo, sha):
    if not has_commit(repo, sha):
        git("fetch", "--quiet", "--filter=blob:none", "origin", sha, cwd=repo)


def core_changes(repo, seen, pinned):
    if seen and same_commit(seen, pinned):
        return {"commits": [], "diff": "", "truncated": False}
    if not seen or not has_commit(repo, seen):
        return {"commits": [], "diff": "", "truncated": False,
                "error": "seen core commit %s not in history; review core by hand" % seen}
    rng = "%s..%s" % (seen, pinned)
    log = git("log", "--format=%H%x09%ad%x09%s", "--date=short", rng, "--", *CORE_PATHS, cwd=repo)
    commits = []
    for line in log.splitlines():
        sha, date, subject = line.split("\t", 2)
        files = watched(git("diff-tree", "--no-commit-id", "--name-only", "-r", sha, cwd=repo).split())
        commits.append({"sha": sha, "date": date, "subject": subject, "files": files,
                        "aura_targets": sorted({t for f in files for t in aura_targets(f)})})
    diff, truncated = cap(git("diff", seen, pinned, "--", *CORE_PATHS, cwd=repo)) if commits else ("", False)
    return {"commits": commits, "diff": diff, "truncated": truncated}


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def run(args):
    state = json.loads(read(args.state))
    result = {"checked_at": datetime.date.today().isoformat(), "seen": state, "errors": []}
    with tempfile.TemporaryDirectory(prefix="simpmusic_watch_") as tmp:
        sm, core, files = (os.path.join(tmp, n) for n in ("simpmusic", "core", "files"))
        try:
            clone(SIMPMUSIC_URL, sm, shallow=True)
            sm_head = git("rev-parse", "HEAD", cwd=sm).strip()
            core_pin = git("ls-tree", "HEAD", "core", cwd=sm).split()[2]
            pins = simpmusic_pins(git("show", "HEAD:gradle/libs.versions.toml", cwd=sm))
            ours = aura_pins(read(args.aura_toml))
            result["S1"] = {"simpmusic_head": sm_head, "simpmusic_pins": pins, "aura_pins": ours,
                            "since_seen": pin_changes(pins, state), "aura_differs": pin_changes(pins, ours)}
        except Exception as e:  # noqa: BLE001 - reported, never swallowed
            result["errors"].append("SimpMusic: %s" % e)
            sm_head = core_pin = None
        if core_pin:
            try:
                clone(CORE_URL, core, shallow=False)
                ensure_commit(core, core_pin)
                result["S2"] = dict(core_changes(core, state.get("core"), core_pin), core_pin=core_pin)
            except Exception as e:  # noqa: BLE001
                result["errors"].append("core: %s" % e)
        try:
            clone(FILES_URL, files, shallow=False)
            files_head = git("rev-parse", "HEAD", cwd=files).strip()
            registry = git("show", "HEAD:" + REGISTRY_PATH, cwd=files)
            seen_reg = state.get("registry")
            old_registry = (git("show", seen_reg + ":" + REGISTRY_PATH, cwd=files)
                            if seen_reg and has_commit(files, seen_reg) else None)
            leads = new_players(registry, read(args.ours))
            fresh = added_since(old_registry, registry)
            result["S3"] = {"registry_head": files_head,
                            "registry_changed": not same_commit(files_head, seen_reg),
                            "new_since_seen": [x for x in leads if fresh is None or x["hash"] in fresh],
                            "backlog_count": len(leads),
                            "backlog_newest": leads[:BACKLOG_SHOWN],
                            "rule": "leads only: verify each with scripts/verify/player_verify.mjs before adding it"}
        except Exception as e:  # noqa: BLE001
            result["errors"].append("simpmusic-files: %s" % e)
            files_head = None

    result["changed"] = bool(
        (result.get("S1") or {}).get("since_seen") or (result.get("S1") or {}).get("aura_differs")
        or (result.get("S2") or {}).get("commits") or (result.get("S2") or {}).get("error")
        or (result.get("S3") or {}).get("new_since_seen"))
    if args.update_state and not result["errors"]:
        new_state = {"simpmusic": sm_head, "core": core_pin,
                     "pipepipe": result["S1"]["simpmusic_pins"]["pipepipe"],
                     "brave": result["S1"]["simpmusic_pins"]["brave"],
                     "registry": files_head, "checked_at": result["checked_at"]}
        with open(args.state, "w", encoding="utf-8") as f:
            json.dump(new_state, f, indent=2)
            f.write("\n")
        result["state_updated"] = True
    return result


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    ap.add_argument("--update-state", action="store_true", help="write the SHAs seen now to the state file")
    ap.add_argument("--state", default=STATE)
    ap.add_argument("--ours", default=OURS, help="Aura's player_configs.json")
    ap.add_argument("--aura-toml", default=AURA_TOML)
    args = ap.parse_args(argv)
    result = run(args)
    json.dump(result, sys.stdout, indent=2)
    sys.stdout.write("\n")
    return 2 if result["errors"] else 0


if __name__ == "__main__":
    sys.exit(main())
