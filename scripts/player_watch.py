"""Watches YouTube's player and says whether the app already knows how to decipher it.

Stage 2 of the semi-automatic repair (owner 2026-10-30): when YouTube rotates its player, the owner is told at once —with the new
player already downloaded— instead of finding out from a user complaint. This script only OBSERVES: it never writes
player_configs.json and never publishes anything (AGENTS.md: never publish guessed values; only values verified by running the
real player.js, and only on the owner's order).

Output (GitHub Actions): `hash`, `covered` (true/false), `url` in $GITHUB_OUTPUT; the player file is saved as player-<hash>.js when
the hash is new to the app.
"""
import json
import os
import re
import sys
import urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
KT = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "music", "echo", "utils", "cipher", "FunctionNameExtractor.kt")
CONFIGS = os.path.join(ROOT, "player_configs.json")
HASH_IN_URL = re.compile(r"/player/([a-f0-9]{8})/")


def player_url_from_html(html):
    m = re.search(r'jsUrl["\']\s*:\s*["\']([^"\']+/player/[a-f0-9]{8}/[^"\']+)["\']', html)
    if not m:
        return None
    url = m.group(1)
    return "https://music.youtube.com" + url if url.startswith("/") else url


def hash_of(url):
    m = HASH_IN_URL.search(url or "")
    return m.group(1) if m else None


def hashes_in_json(text):
    data = json.loads(text)
    entries = data.get("configs", []) if isinstance(data, dict) else data
    return {e["hash"] for e in entries if isinstance(e, dict) and re.fullmatch(r"[a-f0-9]{8}", str(e.get("hash", "")))}


def hashes_in_kotlin(text):
    # The hardcoded map: "3891b194" to HardcodedPlayerConfig(
    return set(re.findall(r'"([a-f0-9]{8})"\s+to\s+HardcodedPlayerConfig', text))


def known_hashes(configs_text, kotlin_text):
    return hashes_in_json(configs_text) | hashes_in_kotlin(kotlin_text)


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "replace")


def write_output(**values):
    path = os.environ.get("GITHUB_OUTPUT")
    lines = "".join(f"{k}={v}\n" for k, v in values.items())
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(lines)
    print(lines, end="")


def main():
    html = fetch("https://music.youtube.com/")
    url = player_url_from_html(html)
    h = hash_of(url)
    if not h:
        print("Could not find the player in music.youtube.com", file=sys.stderr)
        write_output(hash="", covered="unknown", url="")
        return 1
    with open(CONFIGS, encoding="utf-8") as f:
        configs_text = f.read()
    with open(KT, encoding="utf-8") as f:
        kotlin_text = f.read()
    covered = h in known_hashes(configs_text, kotlin_text)
    write_output(hash=h, covered="true" if covered else "false", url=url)
    if not covered:
        js = fetch(url)
        out = os.path.join(ROOT, f"player-{h}.js")
        with open(out, "w", encoding="utf-8") as f:
            f.write(js)
        sts = re.search(r'signatureTimestamp["\']?\s*:\s*(\d+)', js)
        print(f"Saved {out} ({len(js)} chars), signatureTimestamp={sts.group(1) if sts else 'not found'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
