// node --test docs/worker/rooms/test/tvlink.test.mjs  (row 907: the TV sign-in relay, for real on workerd via Miniflare)
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const TTL = 1500;
let mf;
before(async () => {
  mf = new Miniflare({
    modules: true,
    scriptPath: path.join(here, "../src/index.js"),
    compatibilityDate: "2024-11-01",
    durableObjects: { ROOM: "Room", TVLINK: "TvLink" },
    bindings: { TVLINK_TTL_MS: String(TTL) },
  });
});
after(async () => mf.dispose());

let n = 0;
const newId = () => (Date.now().toString(16) + (n++).toString(16)).padEnd(32, "a").slice(0, 32);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function tv(id) {
  const res = await mf.dispatchFetch(`http://x/tvlink/wait?id=${id}`, { headers: { Upgrade: "websocket" } });
  assert.equal(res.status, 101);
  const ws = res.webSocket;
  const inbox = [];
  const waiters = [];
  let closed = false;
  ws.addEventListener("message", (e) => {
    const env = JSON.parse(e.data);
    const m = { type: env.type, ...env.payload };
    const i = waiters.findIndex((w) => w.type === m.type);
    if (i >= 0) waiters.splice(i, 1)[0].resolve(m);
    else inbox.push(m);
  });
  ws.addEventListener("close", () => { closed = true; });
  ws.accept();
  const next = (type, ms = 3000) => {
    const i = inbox.findIndex((m) => m.type === type);
    if (i >= 0) return Promise.resolve(inbox.splice(i, 1)[0]);
    return new Promise((resolve, reject) => {
      const w = { type, resolve };
      waiters.push(w);
      setTimeout(() => { const j = waiters.indexOf(w); if (j >= 0) { waiters.splice(j, 1); reject(new Error("timeout waiting for " + type)); } }, ms);
    });
  };
  return { ws, next, inbox, isClosed: () => closed, hello: (blob) => ws.send(JSON.stringify({ type: "hello", payload: { hello: blob } })) };
}
const hello = (id) => mf.dispatchFetch(`http://x/tvlink/hello?id=${id}`);
const sendSession = (id, session) =>
  mf.dispatchFetch(`http://x/tvlink/send?id=${id}`, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ session }) });

test("the phone reads the TV's sealed hello, sends once, and the TV receives it once", async () => {
  const id = newId();
  const t = await tv(id);
  t.hello("SEALEDHELLO==");
  assert.ok((await t.next("waiting")).expiresAt > Date.now());
  const h = await hello(id);
  assert.equal(h.status, 200);
  assert.equal((await h.json()).hello, "SEALEDHELLO==");
  const s = await sendSession(id, "SEALEDSESSION==");
  assert.equal(s.status, 200);
  assert.deepEqual(await s.json(), { delivered: true });
  assert.equal((await t.next("session")).session, "SEALEDSESSION==");
  // One use: a second send is refused and the hello is gone.
  assert.equal((await sendSession(id, "OTHER==")).status, 409);
  assert.equal((await hello(id)).status, 404);
});

test("a session sent while the TV is reconnecting is handed to its next socket, and only that one", async () => {
  const id = newId();
  const t1 = await tv(id);
  t1.hello("H1==");
  await t1.next("waiting");
  t1.ws.close();
  await sleep(100);
  const s = await sendSession(id, "S1==");
  assert.equal(s.status, 200);
  assert.deepEqual(await s.json(), { delivered: false });
  const t2 = await tv(id);
  t2.hello("H1==");
  assert.equal((await t2.next("session")).session, "S1==");
  const t3 = await tv(id);
  t3.hello("H1==");
  assert.equal((await t3.next("error")).code, "already_used", "the blob was deleted after its single read");
});

test("a second hello never replaces the TV's, and the one delivery reaches every waiting socket", async () => {
  const id = newId();
  const a = await tv(id);
  a.hello("TVHELLO==");
  await a.next("waiting");
  const b = await tv(id);
  b.hello("OTHERHELLO==");
  await b.next("waiting");
  assert.equal((await (await hello(id)).json()).hello, "TVHELLO==", "the phone always sees the first hello (the TV's key)");
  assert.deepEqual(await (await sendSession(id, "S==")).json(), { delivered: true });
  assert.equal((await a.next("session")).session, "S==");
  assert.equal((await b.next("session")).session, "S==");
  assert.equal((await sendSession(id, "S2==")).status, 409);
});

test("nothing can be sent before the TV says hello", async () => {
  const id = newId();
  assert.equal((await hello(id)).status, 404);
  assert.equal((await sendSession(id, "S==")).status, 404);
});

test("everything expires: after the TTL there is no hello and no send", async () => {
  const id = newId();
  const t = await tv(id);
  t.hello("H==");
  await t.next("waiting");
  await sleep(TTL + 300);
  assert.equal((await hello(id)).status, 404);
  assert.equal((await sendSession(id, "S==")).status, 404);
  // A fresh hello with the same id after expiry starts over (the TV shows a new code anyway).
  const again = await tv(id);
  again.hello("H==");
  await again.next("waiting");
});

test("size and shape limits", async () => {
  const id = newId();
  const t = await tv(id);
  t.hello("x".repeat(3000));
  assert.equal((await t.next("error")).code, "bad_hello");
  t.hello("H==");
  await t.next("waiting");
  assert.equal((await sendSession(id, "A".repeat(32 * 1024 + 1))).status, 413);
  assert.equal((await sendSession(id, "not base64 {}")).status, 400);
  const bad = await mf.dispatchFetch(`http://x/tvlink/send?id=${id}`, { method: "POST", body: "{nope" });
  assert.equal(bad.status, 400);
  // The limits never consumed the single use.
  assert.equal((await sendSession(id, "A".repeat(32 * 1024))).status, 200);
});

test("bad ids, wrong methods and unknown paths are refused", async () => {
  assert.equal((await mf.dispatchFetch("http://x/tvlink/hello?id=XYZ")).status, 400);
  assert.equal((await mf.dispatchFetch("http://x/tvlink/hello")).status, 400);
  assert.equal((await mf.dispatchFetch(`http://x/tvlink/send?id=${newId()}`)).status, 405);
  assert.equal((await mf.dispatchFetch(`http://x/tvlink/other?id=${newId()}`)).status, 404);
  assert.equal((await mf.dispatchFetch(`http://x/tvlink/wait?id=${newId()}`)).status, 426);
});

test("old clients are unaffected: health and rooms answer as before", async () => {
  const health = await mf.dispatchFetch("http://x/health");
  assert.deepEqual(await health.json(), { ok: true, rooms: 1 });
  const res = await mf.dispatchFetch("http://x/rooms/create", { headers: { Upgrade: "websocket" } });
  assert.equal(res.status, 101);
  const ws = res.webSocket;
  const first = new Promise((resolve) => ws.addEventListener("message", (e) => resolve(JSON.parse(e.data))));
  ws.accept();
  assert.equal((await first).type, "server_capabilities");
  ws.close();
  assert.equal((await mf.dispatchFetch("http://x/rooms/join?code=AB")).status, 400);
  assert.equal((await mf.dispatchFetch("http://x/nothing")).status, 404);
});
