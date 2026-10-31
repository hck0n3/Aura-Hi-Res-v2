// node --test docs/worker/edge/test  (runs the Worker for real on workerd via Miniflare)
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { webcrypto as c } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { extractPlayerId, sanitizeConfig, watchYoutube, sendAlert, artistTags, normalizeArtist, sanitizeCanvas, canvasGet, canvasPut, validCanvasKey, askGet, askPut, sanitizeAsk, validAskKey, backupPut, backupGet, backupList, backupDelete, FLAGS } from "../src/index.js";

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
  assert.deepEqual(await r.json(), { sent: false, telegramSet: false, ntfyTopicSet: false, emailBindingSet: false });
});

test("alert through Telegram: bot URL, chat id, text; it wins over ntfy", async () => {
  let seen;
  const fakeFetch = async (url, init) => { seen = { url, body: JSON.parse(init.body) }; return { ok: true, status: 200 }; };
  const info = {};
  assert.equal(await sendAlert({ TELEGRAM_TOKEN: "123:abc", TELEGRAM_CHAT: "42", NTFY_TOPIC: "t" }, "Asunto", "Cuerpo", fakeFetch, info), true);
  assert.equal(seen.url, "https://api.telegram.org/bot123:abc/sendMessage");
  assert.equal(seen.body.chat_id, "42");
  assert.match(seen.body.text, /Asunto/);
  assert.equal(info.telegramStatus, 200);
  assert.equal(await sendAlert({ TELEGRAM_TOKEN: "x", TELEGRAM_CHAT: "1" }, "s", "b", async () => ({ ok: false, status: 401 })), false);
});

test("secrets with a trailing line break (PowerShell pipe) still build a clean URL", async () => {
  let seen;
  const fakeFetch = async (url, init) => { seen = { url, body: JSON.parse(init.body) }; return { ok: true, status: 200 }; };
  assert.equal(await sendAlert({ TELEGRAM_TOKEN: "123:abc\r\n", TELEGRAM_CHAT: "42\r\n" }, "A", "B", fakeFetch), true);
  assert.equal(seen.url, "https://api.telegram.org/bot123:abc/sendMessage");
  assert.equal(seen.body.chat_id, "42");
});

function memEnv(extra = {}) {
  const m = new Map();
  return { EDGE: { m, async get(k) { return m.get(k) ?? null; }, async put(k, v) { m.set(k, v); } }, LASTFM_KEY: "k", ...extra };
}
const lastfm = (tags) => async () => ({ status: 200, json: async () => ({ toptags: { tag: tags } }) });

test("tags: one Last.fm lookup per artist for everyone, then straight from KV", async () => {
  const env = memEnv();
  let calls = 0;
  const f = async (...a) => { calls++; return lastfm([{ name: "cumbia", count: 100 }, { name: "latin", count: "55" }])(...a); };
  const first = await artistTags(env, "  Los  Ángeles Azules ", f);
  assert.equal(first.status, 200);
  assert.deepEqual(first.body.tags, [["cumbia", 100], ["latin", 55]]);
  const again = await artistTags(env, "los ángeles azules", f);
  assert.deepEqual(again.body, first.body);
  assert.equal(calls, 1);
  assert.equal(normalizeArtist("  A   B "), "a b");
});

test("tags: a definitive miss is stored, a failure is not, no key / no name are refused", async () => {
  const env = memEnv();
  const notFound = async () => ({ status: 200, json: async () => ({ error: 6, message: "not found" }) });
  assert.deepEqual((await artistTags(env, "nadie", notFound)).body, { tags: [] });
  assert.ok(env.EDGE.m.has("tags:nadie"));
  const outage = async () => ({ status: 503, json: async () => ({}) });
  assert.equal((await artistTags(env, "otro", outage)).status, 502);
  assert.ok(!env.EDGE.m.has("tags:otro"));
  const transient = async () => ({ status: 200, json: async () => ({ error: 29 }) });
  assert.equal((await artistTags(env, "otro", transient)).status, 502);
  assert.equal((await artistTags(memEnv({ LASTFM_KEY: undefined }), "x", outage)).status, 503);
  assert.equal((await artistTags(env, "   ", outage)).status, 400);
});

test("tags: the hourly budget of lookups protects Last.fm and the KV writes", async () => {
  const env = memEnv();
  const f = lastfm([{ name: "rock", count: 1 }]);
  let last;
  for (let i = 0; i < 205; i++) last = await artistTags(env, "artista " + i, f);
  assert.equal(last.status, 429);
});

test("tags route answers JSON and never leaks the key", async () => {
  const r = await mf.dispatchFetch("http://x/v1/tags?artist=");
  assert.equal(r.status, 400);
  const body = await (await mf.dispatchFetch("http://x/v1/tags?artist=abc")).text();
  assert.ok(!/api_key|LASTFM/i.test(body));
});

const goodArt = { name: "Abbey Road", artist: "The Beatles", albumName: "Abbey Road", animated: "https://mvod.itunes.apple.com/x/y.m3u8", static: "https://is1-ssl.mzstatic.com/a.jpg" };
const goodKey = "al|thebeatles|abbeyroad";

test("canvas: a cover about the key, on a known host, is shared once and served to others", async () => {
  const env = memEnv();
  assert.equal((await canvasGet(env, goodKey)).status, 404);
  assert.deepEqual((await canvasPut(env, goodKey, goodArt)).body, { stored: true });
  assert.deepEqual((await canvasPut(env, goodKey, { ...goodArt, animated: "https://a.mzstatic.com/other.m3u8" })).body, { stored: false });
  const got = await canvasGet(env, goodKey);
  assert.equal(got.status, 200);
  assert.equal(got.body.artwork.animated, goodArt.animated);
});

test("canvas: nothing is shared that could hurt the next listener", () => {
  assert.ok(sanitizeCanvas(goodKey, goodArt));
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, animated: "http://mvod.itunes.apple.com/x.m3u8" }), null, "plain http");
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, animated: "https://evil.example.com/x.mp4" }), null, "unknown host");
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, animated: "https://apple.com.evil.io/x.mp4" }), null, "look-alike host");
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, artist: "Someone Else" }), null, "another artist's cover under this key");
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, albumName: "Let It Be", name: "Let It Be" }), null, "another album's cover");
  assert.equal(sanitizeCanvas(goodKey, { ...goodArt, animated: undefined }), null, "nothing animated to share");
  assert.equal(sanitizeCanvas("al|x", goodArt), null);
  assert.ok(!validCanvasKey("al|" + "a".repeat(200) + "|b"));
});

test("canvas: routes answer, refuse junk and respect the write budget", async () => {
  const miss = await mf.dispatchFetch("http://x/v1/canvas?k=" + encodeURIComponent("al|nadie|nada"));
  assert.equal(miss.status, 404);
  const bad = await mf.dispatchFetch("http://x/v1/canvas", { method: "PUT", body: JSON.stringify({ k: goodKey, artwork: { ...goodArt, animated: "https://evil.example.com/x" } }) });
  assert.equal(bad.status, 400);
  const env = memEnv();
  let last;
  for (let i = 0; i < 305; i++) last = await canvasPut(env, `al|artista${i}|album${i}`, { artist: "artista" + i, albumName: "album" + i, animated: "https://a.mzstatic.com/" + i + ".m3u8" });
  assert.equal(last.status, 429);
});

const bkId = "a".repeat(64);
const bkBytes = (tag) => new TextEncoder().encode("AURAB1" + tag.padEnd(60, "x")).buffer;
function r2Mock() {
  const m = new Map();
  return { m, async put(k, v) { m.set(k, v); }, async get(k) { return m.has(k) ? { arrayBuffer: async () => m.get(k) } : null; }, async delete(k) { m.delete(k); } };
}
function kvEnv() {
  const env = memEnv();
  const raw = env.EDGE;
  raw.get = async (k, type) => raw.m.get(k) ?? null; // arrayBuffer reads come back as stored
  raw.delete = async (k) => { raw.m.delete(k); };
  return env;
}

for (const [label, make] of [["KV", () => kvEnv()], ["R2", () => ({ ...kvEnv(), BACKUPS: r2Mock() })]]) {
  test(`backup (${label}): stores, keeps the last 3 and drops older ones for good`, async () => {
    const env = make();
    for (const tag of ["uno", "dos", "tres", "cuatro"]) assert.equal((await backupPut(env, bkId, bkBytes(tag))).status, 200);
    const list = (await backupList(env, bkId)).body.copies;
    assert.deepEqual(list.map((e) => e.n), [4, 3, 2]);
    const store = env.BACKUPS ? env.BACKUPS.m : env.EDGE.m;
    assert.ok(![...store.keys()].some((k) => k === `bk:${bkId}:1`), "the oldest copy is gone");
    const latest = await backupGet(env, bkId);
    assert.match(new TextDecoder().decode(latest.bytes), /^AURAB1cuatro/);
    assert.match(new TextDecoder().decode((await backupGet(env, bkId, 3)).bytes), /^AURAB1tres/);
    assert.equal((await backupDelete(env, bkId)).status, 200);
    assert.equal((await backupGet(env, bkId)).status, 404);
  });
}

test("backup: only Aura-shaped, bounded, well-addressed copies", async () => {
  const env = kvEnv();
  assert.equal((await backupPut(env, "short", bkBytes("a"))).status, 400);
  assert.equal((await backupPut(env, bkId, new TextEncoder().encode("PK-not-aura-" + "x".repeat(80)).buffer)).status, 400);
  assert.equal((await backupPut(env, bkId, new ArrayBuffer(10))).status, 400);
  assert.equal((await backupPut(env, bkId, new ArrayBuffer(25 * 1024 * 1024))).status, 413);
  assert.equal((await backupGet(env, "b".repeat(64))).status, 404);
});

test("backup: the write budget protects KV", async () => {
  const env = kvEnv();
  let last;
  for (let i = 0; i < 62; i++) last = await backupPut(env, bkId, bkBytes("n" + i));
  assert.equal(last.status, 429);
});

test("backup route: end to end on workerd", async () => {
  const id = "c".repeat(64);
  const body = new TextEncoder().encode("AURAB1" + "z".repeat(80));
  const put = await mf.dispatchFetch("http://x/v1/backup/" + id, { method: "PUT", body });
  assert.equal(put.status, 200);
  const got = await mf.dispatchFetch("http://x/v1/backup/" + id);
  assert.equal(got.status, 200);
  assert.equal(Buffer.from(await got.arrayBuffer()).toString(), Buffer.from(body).toString());
  const list = await (await mf.dispatchFetch("http://x/v1/backup/" + id + "/list")).json();
  assert.equal(list.copies.length, 1);
  assert.equal((await mf.dispatchFetch("http://x/v1/backup/" + id, { method: "DELETE" })).status, 200);
  assert.equal((await mf.dispatchFetch("http://x/v1/backup/not-an-id")).status, 404);
});

const askKey = "a".repeat(64);
const askTracks = [1, 2, 3].map((n) => ({ title: "Song " + n, artist: "Artist" }));

test("ask: an answer is filed under a hash, shared once and served to others", async () => {
  const env = memEnv();
  assert.equal((await askGet(env, askKey)).status, 404);
  assert.equal((await askGet(env, "not a hash")).status, 400);
  assert.deepEqual((await askPut(env, askKey, askTracks)).body, { stored: true });
  assert.deepEqual((await askPut(env, askKey, [...askTracks, { title: "x", artist: "y" }])).body, { stored: false });
  assert.deepEqual((await askGet(env, askKey)).body.tracks, askTracks);
});

test("ask: only plain bounded title/artist pairs are kept, tiny answers are not shared", () => {
  assert.ok(validAskKey(askKey));
  assert.equal(validAskKey("A".repeat(64)), false);
  assert.equal(sanitizeAsk([{ title: "a", artist: "b" }]), null, "fewer than 3");
  assert.equal(sanitizeAsk("nope"), null);
  const mixed = sanitizeAsk([...askTracks, null, { title: 5, artist: "x" }, { title: " ", artist: "x" }, { title: "T".repeat(500), artist: "A", extra: 1 }]);
  assert.equal(mixed.length, 4);
  assert.deepEqual(Object.keys(mixed[3]), ["title", "artist"]);
  assert.equal(mixed[3].title.length, 120);
});
