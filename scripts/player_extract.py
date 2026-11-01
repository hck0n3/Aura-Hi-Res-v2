"""Stage 4 of the semi-automatic YouTube repair: derive a player_configs.json entry from a player's JS.

READ-ONLY with respect to the repository: it returns a CANDIDATE and never writes player_configs.json (AGENTS.md: never publish
guessed values; a candidate becomes real only after it is verified by RUNNING the real player and the owner orders it published).

What YouTube's modern players look like (seen in the real files of hashes 3891b194, c74cbcd6, b0d2d49a, owner 2026-10-30):
  · the signature is deciphered at ONE call site that reads the `s` field of a format:   OUT(a,b,IN(c,d,<var>.s))
  · the `n` parameter is read through a URL class the file uses once as:               (new g.XX(P,!0)).get("n")
Only the NAMES (OUT, IN, XX) and the numbers change from player to player; the shape does not.
"""
import re

# OUTER(a,b,INNER(c,d,<var>.s)) — the `.s` argument is what ties it to the signature cipher field.
SIG_CALL = re.compile(r"([A-Za-z_$][\w$]*)\((\d+),(\d+),([A-Za-z_$][\w$]*)\((\d+),(\d+),[A-Za-z_$][\w$]*\.s\)\)")
# (new g.XX(P,!0)).get("n")
N_CLASS = re.compile(r'new g\.([A-Za-z_$][\w$]*)\([A-Za-z_$][\w$]*,!0\)\)\.get\("n"\)')
STS = re.compile(r'signatureTimestamp["\']?\s*:\s*(\d+)')


def extract_sts(js):
    m = STS.search(js)
    return int(m.group(1)) if m else None


def extract_sig_expression(js):
    """`OUT(a,b,IN(c,d,INPUT))` when the file has EXACTLY ONE distinct `.s` call site; None when absent or ambiguous."""
    found = {m.group(0): m for m in SIG_CALL.finditer(js)}
    if len(found) != 1:
        return None
    m = next(iter(found.values()))
    return f"{m.group(1)}({m.group(2)},{m.group(3)},{m.group(4)}({m.group(5)},{m.group(6)},INPUT))"


def extract_n_class(js):
    names = {m.group(1) for m in N_CLASS.finditer(js)}
    return next(iter(names)) if len(names) == 1 else None


def n_expression(cls):
    return (
        "(function(n){try{var u=new g." + cls + "('https://x.googlevideo.com/videoplayback?n='+n,true);"
        "var t=u.get('n');return(t&&t!==n)?t:n;}catch(e){return n;}})(INPUT)"
    )


def build_candidate(player_hash, js):
    """The entry, or (None, why) when any piece cannot be found without guessing."""
    sts = extract_sts(js)
    if sts is None:
        return None, "signatureTimestamp not found"
    sig = extract_sig_expression(js)
    if sig is None:
        return None, "no single signature call site"
    cls = extract_n_class(js)
    if cls is None:
        return None, "no single n URL class"
    return {
        "hash": player_hash,
        "signatureTimestamp": sts,
        "sigFuncName": "_expr_sig",
        "sigJsExpression": sig,
        "nFuncName": "_expr_n",
        "nJsExpression": n_expression(cls),
    }, None
