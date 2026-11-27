#!/usr/bin/env python3
"""Builds app/src/main/assets/autoeq_profiles.tsv.gz: the offline AutoEq catalogue (model -> parametric bands).

Input: a checkout of jaakkopasanen/AutoEq (MIT). Only the `results/**/<Model> ParametricEQ.txt` files are read; a
sparse, blob-less clone is enough and takes a few seconds:

    git clone --filter=blob:none --no-checkout --depth 1 https://github.com/jaakkopasanen/AutoEq.git /tmp/autoeq
    git -C /tmp/autoeq sparse-checkout set --no-cone '/results/**/*ParametricEQ.txt' '/results/README.md' '/LICENSE'
    git -C /tmp/autoeq checkout
    python3 -I scripts/build_autoeq_profiles.py /tmp/autoeq app/src/main/assets/autoeq_profiles.tsv.gz

Output, one line per measurement, UTF-8, gzip (level 9, mtime 0 so the same input gives the same bytes):

    # autoeq <commit sha or "unknown">
    <name>\t<source>\t<type>\t<recommended>\t<preamp dB>\t<bands>

`<type>` is the rig kind from the results folder: `in-ear`, `over-ear` or `earbud`. `<recommended>` is `1` when
AutoEq's own `results/README.md` («Recommended Results»: the most accurate measurement of each model) links that
exact folder, else `0`. `<bands>` is `;`-separated
`<T><fc>,<gain>,<q>` with T = P (peak), L (low shelf) or H (high shelf), fc in Hz (integer), gain in dB (1 decimal)
and Q (2 decimals), exactly as AutoEq printed them. Only `ON PK|LSC|HSC` filters are kept: the same set
`AutoEqParser` accepts (registry row 792), so an offline profile and a downloaded one are the same filters.
Read by `eq/autoeq/AutoEqOfflineCatalog.kt`.
"""
import gzip
import io
import os
import re
import subprocess
import sys
from urllib.parse import unquote

FILTER = re.compile(
    r"Filter\s+\d+:\s*ON\s+(PK|LSC|HSC)\s+Fc\s+(\d+(?:\.\d+)?)\s*Hz\s+Gain\s+(-?\d+(?:\.\d+)?)\s*dB\s+Q\s+(\d+(?:\.\d+)?)",
    re.IGNORECASE,
)
PREAMP = re.compile(r"Preamp:\s*(-?\d+(?:\.\d+)?)\s*dB", re.IGNORECASE)
LETTER = {"PK": "P", "LSC": "L", "HSC": "H"}
LINK = re.compile(r"^- \[[^\]]*\]\(\./([^)]*)\)\s*$")


def recommended_folders(results: str) -> set:
    """`results/README.md` lists one link per model: `- [Name](./<source>/<rig>/<Name>)`, URL-encoded."""
    found = set()
    path = os.path.join(results, "README.md")
    if not os.path.isfile(path):
        sys.exit("results/README.md is missing: add it to the sparse checkout")
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            m = LINK.match(line.strip())
            if m:
                found.add(unquote(m.group(1)).rstrip("/"))
    return found


def rig_type(rig: str) -> str:
    r = rig.lower()
    if "in-ear" in r:
        return "in-ear"
    if "earbud" in r:
        return "earbud"
    if "over-ear" in r:
        return "over-ear"
    return ""


def clean(text: str) -> str:
    return text.replace("\t", " ").replace("\n", " ").strip()


def main(repo: str, out: str) -> None:
    results = os.path.join(repo, "results")
    try:
        sha = subprocess.run(["git", "-C", repo, "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except Exception:
        sha = "unknown"
    recommended = recommended_folders(results)
    rows = []
    for dirpath, _, files in os.walk(results):
        for f in files:
            if not f.endswith(" ParametricEQ.txt"):
                continue
            rel = os.path.relpath(os.path.join(dirpath, f), results).split(os.sep)
            # <source>/<rig>/<Model>/<Model> ParametricEQ.txt
            if len(rel) < 4:
                continue
            source, rig, name = rel[0], rel[1], rel[-2]
            kind = rig_type(rig)
            with open(os.path.join(dirpath, f), encoding="utf-8", errors="replace") as fh:
                text = fh.read()
            bands = []
            for m in FILTER.finditer(text):
                fc, gain, q = float(m.group(2)), float(m.group(3)), float(m.group(4))
                if fc <= 0 or q <= 0:
                    continue
                bands.append(f"{LETTER[m.group(1).upper()]}{round(fc)},{gain:.1f},{q:.2f}")
            if not bands:
                continue
            pm = PREAMP.search(text)
            preamp = float(pm.group(1)) if pm else 0.0
            rec = "1" if "/".join([source, rig, name]) in recommended else "0"
            rows.append((clean(name), clean(source), kind, rec, f"{preamp:.1f}", ";".join(bands), rig))
    # Deterministic order; within one model + source + rig kind the recommended rig comes first, then by rig name.
    rows.sort(key=lambda r: (r[0].lower(), r[0], r[1].lower(), r[2], r[3] != "1", r[6]))
    # The same model + source + rig kind measured on two rigs: keep only the first one.
    seen = set()
    lines = [f"# autoeq {sha}"]
    for r in rows:
        key = (r[0], r[1], r[2])
        if key in seen:
            continue
        seen.add(key)
        lines.append("\t".join(r[:6]))
    raw = ("\n".join(lines) + "\n").encode("utf-8")
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", compresslevel=9, mtime=0, filename="") as gz:
        gz.write(raw)
    with open(out, "wb") as fh:
        fh.write(buf.getvalue())
    print(f"{len(lines) - 1} profiles, {len(raw)} bytes raw, {len(buf.getvalue())} bytes gzip, AutoEq {sha}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit("usage: build_autoeq_profiles.py <AutoEq checkout> <out .tsv.gz>")
    main(sys.argv[1], sys.argv[2])
