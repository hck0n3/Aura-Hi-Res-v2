"""Stage 5 of the semi-automatic YouTube repair: the «Publicar» button.

Downloads YouTube's CURRENT player, builds the entry with the extractor, RUNS the real player with it through the verifier
and only if that passes writes the entry (newest first) into player_configs.json. Anything less -> exit 1 and the file is
untouched: values are never guessed (AGENTS.md). Nothing here pushes; the workflow decides, and it only runs when the owner
presses the button.

Prints `PUBLISHED <hash>` / `ALREADY <hash>` / `REFUSED <why>`.
"""
import json
import os
import re
import sys

import player_extract as px
import player_validate as pv
import player_watch as pw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
CONFIGS = os.path.join(ROOT, "player_configs.json")


def render(entries):
    """One entry per line, like the hand-maintained file."""
    return "[\n" + ",\n".join("  " + json.dumps(e, ensure_ascii=False, separators=(",", ":")) for e in entries) + "\n]\n"


def main():
    html = pw.fetch("https://music.youtube.com/")
    url = pw.player_url_from_html(html)
    h = pw.hash_of(url) if url else None
    if not h or not re.fullmatch(r"[0-9a-f]{8}", h):
        print("REFUSED could not read the player hash from music.youtube.com")
        return 1
    with open(CONFIGS, encoding="utf-8") as f:
        entries = json.load(f)
    if any(e.get("hash") == h for e in entries):
        print(f"ALREADY {h}")
        return 0
    js = pw.fetch(url)
    cand, why = px.build_candidate(h, js)
    if cand is None:
        print(f"REFUSED {h}: extractor says {why}")
        return 1
    line, ok = pv.run_verifier(js, cand)
    print(f"verifier: {line}")
    if not ok:
        print(f"REFUSED {h}: the real player did not accept the candidate")
        return 1
    with open(CONFIGS, "w", encoding="utf-8") as f:
        f.write(render([cand] + entries))
    print(f"PUBLISHED {h}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
