// Stage 3 of the semi-automatic YouTube repair: RUN a real player.js with a candidate config and judge the result.
//   node player_verify.mjs <player.js> <candidate.json>
// It does exactly what the app does (CipherWebView.loadPlayerJsFromFile): the candidate's expressions are injected just before the
// closing of the player's outer function, then called. The judge is stricter than the app's:
//  · SIGNATURE: a deciphered signature only REARRANGES the characters of the original. With 100 DISTINCT characters the output must be
//    a permutation of the input, the same permutation must apply to a second, different input of the same length (a real cipher is a
//    fixed rearrangement by position), it must differ from the input, and it must be repeatable.
//  · N PARAMETER: must return a different, plausible, repeatable string (the expression returns n untouched when it fails).
// Exit code 0 only when both pass. It never writes anything outside stdout.
import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";

const [playerPath, candidatePath] = process.argv.slice(2);
const js = readFileSync(playerPath, "utf8");
const cand = JSON.parse(readFileSync(candidatePath, "utf8"));

function inject(source, exports) {
  const code = "; " + exports.join(" ");
  const at = Math.max(source.lastIndexOf("})("), source.lastIndexOf("}).call("));
  return at === -1 ? source + "\n" + code : source.slice(0, at) + code + source.slice(at);
}

const exports = [];
if (cand.sigJsExpression) {
  exports.push(`window._cipherSigFunc = function(sig) { try { return ${cand.sigJsExpression.replaceAll("INPUT", "sig")}; } catch(e) { return null; } };`);
}
if (cand.nJsExpression) {
  exports.push(`window._nTransformFunc = function(n) { try { return ${cand.nJsExpression.replaceAll("INPUT", "n")}; } catch(e) { return n; } };`);
}

const dom = new JSDOM("<!DOCTYPE html><html><body></body></html>", { runScripts: "outside-only", pretendToBeVisual: true, url: "https://www.youtube.com/" });
const win = dom.window;
let loadError = null;
try {
  win.eval(inject(js, exports));
} catch (e) {
  loadError = String(e && e.message || e);
}

// 100 distinct characters that survive decodeURIComponent unchanged (no % and no backslash).
const POOL = [..."ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~!*'()"];
for (let c = 0xc0; c <= 0xff && POOL.length < 100; c++) if (c !== 0xd7 && c !== 0xf7) POOL.push(String.fromCharCode(c));
const A = POOL.slice(0, 100).join("");
const B = POOL.slice(0, 100).reverse().join("").split("").map((ch, i) => (i % 2 ? ch : ch)).join(""); // same characters, other order

function judgeSig() {
  const f = win._cipherSigFunc;
  if (typeof f !== "function") return "no _cipherSigFunc (the expression did not load" + (loadError ? `: ${loadError}` : "") + ")";
  const outA = f(A);
  const outA2 = f(A);
  const outB = f(B);
  if (typeof outA !== "string" || typeof outB !== "string") return "the result is not a string";
  if (outA !== outA2) return "not repeatable";
  if (outA === A) return "returned the signature unchanged";
  if (outA.length !== outB.length || outA.length < 80 || outA.length > A.length + 2) return `odd output length ${outA.length}`;
  const bag = new Set(A);
  if ([...outA].some((c) => !bag.has(c))) return "invented characters (not a rearrangement)";
  if (new Set(outA).size !== outA.length) return "repeats characters (not a permutation of distinct input)";
  // The same position map must apply to the second input: outA[i] = A[p(i)]  ⇒  outB[i] = B[p(i)].
  const pos = new Map([...A].map((c, i) => [c, i]));
  for (let i = 0; i < outA.length; i++) {
    const p = pos.get(outA[i]);
    if (outB[i] !== B[p]) return "not a fixed rearrangement by position";
  }
  return null;
}

function judgeN() {
  const f = win._nTransformFunc;
  if (typeof f !== "function") return "no _nTransformFunc (the expression did not load" + (loadError ? `: ${loadError}` : "") + ")";
  const input = "aB3_xY9-kQz";
  const out = f(input);
  if (typeof out !== "string" || out.length < 5) return "implausible result";
  if (out === input) return "returned n unchanged (the transform failed)";
  if (!/^[A-Za-z0-9_-]+$/.test(out)) return "result has characters a url cannot carry";
  if (f(input) !== out) return "not repeatable";
  return null;
}

const sig = cand.sigJsExpression ? judgeSig() : "no sig expression";
const n = cand.nJsExpression ? judgeN() : "no n expression";
console.log(JSON.stringify({ hash: cand.hash, sig: sig === null ? "PASS" : "FAIL: " + sig, n: n === null ? "PASS" : "FAIL: " + n, loadError }));
process.exit(sig === null && n === null ? 0 : 1);
