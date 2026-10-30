// node --test docs/worker/edge/test  (runs the Worker for real on workerd via Miniflare)
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { webcrypto as c } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { extractPlayerId, sanitizeConfig, watchYoutube, sendAlert, FLAGS } from "../src/index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const b64 = (buf) => Buffer.from(buf).toString("base64");
let mf, pub, kv;
const ADMIN = "admin-token-for-tests";

before(async () => {
  const pair = await c.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  pub = pair.publicKey;
  mf = new Miniflare({
    modules: true,
    scriptPath: path.join(here, "../src/index.js"),
    compatibilityDate: "2024-11-01",
    kvNamespaces: ["EDGE"],
    bindings: { SIGNING_KEY: b64(await c.subtle.exportKey("pkcs8", pair.privateKey)), ADMIN_TOKEN: ADMIN },
  });
  kv = await mf.getKVNamespace("EDGE");
});
after(async () => mf.dispose());

const put = (body, token = ADMIN) =>
  mf.dispatchFetch("http://x/v1/admin/config", { method: "PUT", headers: { authorization: "Bearer " + token }, body: JSON.stringify(body) });

test("no config yet → 404 (the app then keeps every default)", async () => {
  assert.equal((await mf.dispatchFetch("http://x/v1/config")).status, 404);
});

test("only the admin can set the config", async () => {
  assert.equal((await put({ flags: { letras: false } }, "wrong")).status, 403);
  assert.equal((await mf.dispatchFetch("http://x/v1/admin/config", { method: "PUT", body: "{}" })).status, 403);
});

test("the config is signed and the signature verifies with the public key (as the app does)", async () => {
  const r = await put({ flags: { letras: false, inventada: false, cola_inteligente: "no" }, notice: { id: "n1", titulo: "Hola", texto: "Mantenimiento", hasta: 0 } });
  assert.equal(r.status, 200);
  const got = await (await mf.dispatchFetch("http://x/v1/config")).json();
  const ok = await c.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, pub, Buffer.from(got.sig, "base64"), new TextEncoder().encode(got.payload));
  assert.ok(ok, "signature verifies");
  const cfg = JSON.parse(got.payload);
  assert.deepEqual(cfg.flags, { letras: false }, "unknown flags and non-booleans are dropped");
  assert.equal(cfg.notice.id, "n1");
  const tampered = got.payload.replace("false", "true");
  assert.ok(!(await c.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, pub, Buffer.from(got.sig, "base64"), new TextEncoder().encode(tampered))), "a changed payload fails");
});

test("ETag: an unchanged config answers 304", async () => {
  const first = await mf.dispatchFetch("http://x/v1/config");
  const etag = first.headers.get("etag");
  const again = await mf.dispatchFetch("http://x/v1/config", { headers: { "if-none-match": etag } });
  assert.equal(again.status, 304);
});

test("playback and the licence have no flag", () => {
  for (const f of FLAGS) assert.ok(!/reproduc|playback|licen|licens|play/.test(f), f);
  assert.deepEqual(sanitizeConfig({ flags: { playback: false, license: false } }).flags, {});
});

test("diag: stores a log, returns a code, only the admin reads it", async () => {
  const log = "10-30 06:28:33 I/App: something\n".repeat(5);
  const r = await mf.dispatchFetch("http://x/v1/diag", { method: "POST", body: log });
  assert.equal(r.status, 200);
  const { code } = await r.json();
  assert.match(code, /^[A-Z2-9]{8}$/);
  assert.equal((await mf.dispatchFetch("http://x/v1/diag/" + code)).status, 403);
  const read = await mf.dispatchFetch("http://x/v1/diag/" + code, { headers: { authorization: "Bearer " + ADMIN } });
  assert.equal(await read.text(), log);
});

test("diag: too big and too many are refused", async () => {
  const big = await mf.dispatchFetch("http://x/v1/diag", { method: "POST", body: "x".repeat(1024 * 1024 + 5) });
  assert.equal(big.status, 413);
  const codes = [];
  for (let i = 0; i < 8; i++) codes.push((await mf.dispatchFetch("http://x/v1/diag", { method: "POST", body: "log line number " + i })).status);
  assert.ok(codes.includes(429), "rate limit kicks in: " + codes);
});

test("watcher: first run stores, same id is silent, a new id alerts once", async () => {
  const env = { EDGE: { m: new Map(), async get(k) { return this.m.get(k) ?? null; }, async put(k, v) { this.m.set(k, v); } } };
  const page = (id) => ({ ok: true, text: async () => `x https://www.youtube.com/s/player/${id}/player_ias.vflset/en_US/base.js y` });
  let alerts = 0;
  const notify = async () => { alerts++; return true; };
  assert.equal(await watchYoutube(env, async () => page("aaaaaaaa"), notify), "first");
  assert.equal(await watchYoutube(env, async () => page("aaaaaaaa"), notify), "unchanged");
  assert.equal(await watchYoutube(env, async () => page("bbbbbbbb"), notify), "changed");
  assert.equal(await watchYoutube(env, async () => page("bbbbbbbb"), notify), "unchanged");
  assert.equal(alerts, 1);
});

test("watcher: a failed alert is retried, an unreachable page is not a change", async () => {
  const env = { EDGE: { m: new Map([["yt:player", "aaaaaaaa"]]), async get(k) { return this.m.get(k) ?? null; }, async put(k, v) { this.m.set(k, v); } } };
  const page = { ok: true, text: async () => "https://www.youtube.com/s/player/cccccccc/base.js" };
  assert.equal(await watchYoutube(env, async () => page, async () => false), "changed");
  assert.equal(env.EDGE.m.get("yt:player"), "aaaaaaaa", "kept so the next run alerts again");
  assert.equal(await watchYoutube(env, async () => { throw new Error("down"); }, async () => true), "unreachable");
  assert.equal(extractPlayerId("nothing"), null);
});

test("/v1/pubkey returns exactly the public key that verifies the signatures", async () => {
  const { publicKey } = await (await mf.dispatchFetch("http://x/v1/pubkey")).json();
  assert.equal(publicKey, Buffer.from(await c.subtle.exportKey("spki", pub)).toString("base64"));
  await put({ flags: { letras: true } });
  const got = await (await mf.dispatchFetch("http://x/v1/config")).json();
  const key = await c.subtle.importKey("spki", Buffer.from(publicKey, "base64"), { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
  assert.ok(await c.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key, Buffer.from(got.sig, "base64"), new TextEncoder().encode(got.payload)));
});

test("alert through ntfy: topic in the URL, e-mail header, ASCII title; nothing configured = false", async () => {
  let seen;
  const fakeFetch = async (url, init) => { seen = { url, init }; return { ok: true }; };
  assert.equal(await sendAlert({}, "YouTube cambió su reproductor", "x", fakeFetch), false);
  assert.equal(await sendAlert({ NTFY_TOPIC: "secreto-largo" }, "YouTube cambió su reproductor", "Antes: a", fakeFetch), true);
  assert.equal(seen.url, "https://ntfy.sh/secreto-largo");
  assert.equal(seen.init.headers.Email, "aurahires@gmail.com");
  assert.equal(seen.init.headers.Title, "YouTube cambio su reproductor");
  assert.equal(await sendAlert({ NTFY_TOPIC: "t" }, "s", "x", async () => ({ ok: false })), false);
});

test("the test-alert route is admin only", async () => {
  assert.equal((await mf.dispatchFetch("http://x/v1/admin/test-alert", { method: "POST" })).status, 403);
  const r = await mf.dispatchFetch("http://x/v1/admin/test-alert", { method: "POST", headers: { authorization: "Bearer " + ADMIN } });
  assert.deepEqual(await r.json(), { sent: false });
});
