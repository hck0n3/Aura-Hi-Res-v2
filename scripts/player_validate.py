"""Runs the extractor against REAL players whose entries were solved by hand, and says whether it reproduces them exactly.

An extractor is only trusted with a NEW player once it gets the known ones right. Read-only; prints PASS/DIFF per field.
"""
import json
import os
import subprocess
import sys
import tempfile

import player_extract as px
import player_probe as probe

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


VERIFY = os.path.join(ROOT, "scripts", "verify", "player_verify.mjs")


def run_verifier(js, entry):
    """Runs the REAL player with [entry] (stage 3) and returns the verifier's one-line verdict."""
    with tempfile.TemporaryDirectory() as tmp:
        jp, cp = os.path.join(tmp, "player.js"), os.path.join(tmp, "cand.json")
        with open(jp, "w", encoding="utf-8") as f:
            f.write(js)
        with open(cp, "w", encoding="utf-8") as f:
            json.dump(entry, f)
        r = subprocess.run(["node", VERIFY, jp, cp], capture_output=True, text=True, timeout=120, cwd=os.path.dirname(VERIFY))
        return (r.stdout.strip() or r.stderr.strip()[-300:]), r.returncode == 0


def main():
    verify = "--verify" in sys.argv
    argv = [a for a in sys.argv[1:] if not a.startswith("--")]
    limit = int(argv[0]) if argv else 8
    with open(os.path.join(ROOT, "player_configs.json"), encoding="utf-8") as f:
        entries = [e for e in json.load(f) if e.get("sigFuncName") == "_expr_sig"][:limit]
    ok = bad = skipped = 0
    for e in entries:
        h = e["hash"]
        js = None
        for base in probe.BASES:
            try:
                js = probe.fetch(base.format(h=h))
                break
            except Exception:  # noqa: BLE001
                continue
        if js is None:
            print(f"[{h}] SKIP (player not downloadable)")
            skipped += 1
            continue
        cand, why = px.build_candidate(h, js)
        if cand is None:
            print(f"[{h}] FAIL extractor: {why}")
            bad += 1
            continue
        if verify:
            known_line, known_ok = run_verifier(js, e)
            cand_line, cand_ok = run_verifier(js, cand)
            print(f"[{h}]   RUN known entry:      {'OK' if known_ok else 'NO'}  {known_line}")
            print(f"[{h}]   RUN extracted entry:  {'OK' if cand_ok else 'NO'}  {cand_line}")
        diffs = [k for k in ("signatureTimestamp", "sigJsExpression", "nJsExpression") if cand[k] != e.get(k)]
        if diffs:
            print(f"[{h}] DIFF in {diffs}")
            for k in diffs:
                print(f"    known:     {e.get(k)}\n    extracted: {cand[k]}")
            bad += 1
        else:
            print(f"[{h}] PASS (sts, sig and n all identical to the hand-solved entry)")
            ok += 1
    print(f"SUMMARY pass={ok} diff_or_fail={bad} skipped={skipped}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
