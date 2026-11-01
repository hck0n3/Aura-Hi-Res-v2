"""Stage 4 groundwork (owner 2026-10-30): LOOK at real, already-solved players before writing any extractor.

For the first N entries of player_configs.json (their expressions were derived by hand and are known good) it downloads that
player and prints, for the functions named in the known expression, how they are defined in the real file. Read-only; prints
structure only (no secrets exist here), never writes player_configs.json.
"""
import json
import os
import re
import sys
import urllib.request

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BASES = [
    "https://www.youtube.com/s/player/{h}/player_ias.vflset/en_US/base.js",
    "https://music.youtube.com/s/player/{h}/player_ias.vflset/en_US/base.js",
]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=40).read().decode("utf-8", "replace")


def idents(expr):
    return [i for i in re.findall(r"[A-Za-z_$][\w$]*", expr or "") if i not in ("INPUT", "function", "try", "var", "return", "catch", "new", "true", "n", "u", "t", "e", "g")]


def around(js, needle, before=60, after=220):
    i = js.find(needle)
    return None if i < 0 else js[max(0, i - before): i + after].replace("\n", " ")


def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    with open(os.path.join(ROOT, "player_configs.json"), encoding="utf-8") as f:
        entries = json.load(f)
    for e in entries[:n]:
        h = e["hash"]
        js = None
        for base in BASES:
            try:
                js = fetch(base.format(h=h))
                break
            except Exception as ex:  # noqa: BLE001
                print(f"[{h}] {base.split('/')[2]}: {type(ex).__name__} {getattr(ex, 'code', '')}")
        if js is None:
            print(f"[{h}] NOT DOWNLOADABLE")
            continue
        sts = re.search(r'signatureTimestamp["\']?\s*:\s*(\d+)', js)
        print(f"[{h}] size={len(js)} sts_in_file={sts.group(1) if sts else None} sts_expected={e.get('signatureTimestamp')}")
        sig = e.get("sigJsExpression") or ""
        print(f"[{h}] sig expression: {sig}")
        # WHERE is the known expression USED in the real file? (the extractor has to find this without knowing the names)
        pat = re.escape(sig).replace("INPUT", r"([\w$.\[\]]+)")
        pat = pat.replace(r"\(", r"\(").replace(r"\,", ",")
        uses = [m for m in re.finditer(pat, js)]
        print(f"[{h}]   uses of the known sig expression in the file: {len(uses)}")
        for m in uses[:2]:
            print(f"[{h}]     ...{js[max(0, m.start() - 160): m.end() + 120]}...".replace("\n", " "))
        generic = re.findall(r"([\w$]+)\((\d+),(\d+),([\w$]+)\((\d+),(\d+),([\w$.]+)\)\)", js)
        print(f"[{h}]   generic nested-call pattern matches in the file: {len(generic)}; first 6: {generic[:6]}")
        nexpr = e.get("nJsExpression") or ""
        cls = re.search(r"new g\.([\w$]+)\(", nexpr)
        if cls:
            c = cls.group(1)
            sites = [m.start() for m in re.finditer(r"new g\." + re.escape(c) + r"\(", js)]
            print(f"[{h}]   'new g.{c}(' sites: {len(sites)}")
            for i in sites[:3]:
                print(f"[{h}]     ...{js[max(0, i - 120): i + 140]}...".replace("\n", " "))
        gets = [m.start() for m in re.finditer(r'\.get\("n"\)', js)]
        print(f"[{h}]   '.get(\"n\")' sites: {len(gets)}")
        for i in gets[:3]:
            print(f"[{h}]     ...{js[max(0, i - 140): i + 140]}...".replace("\n", " "))
    return 0


if __name__ == "__main__":
    sys.exit(main())
