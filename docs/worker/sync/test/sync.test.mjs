// Runs the Worker for real (workerd via Miniflare): node --test docs/worker/sync/test
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { mergeShuffle } from "../src/merge.js";
import { SyncRoom } from "../src/index.js";

const here = path.dirname(fileURLToPath(import.meta.url));
const group = createHash("sha256").update("secret-one").digest("hex");
const otherGroup = createHash("sha256").update("secret-two").digest("hex");
let mf;

before(async () => {
  mf = new Miniflare({
    modules: true,
    scriptPath: path.join(here, "../src/index.js"),
    modulesRoot: path.join(here, "../src"),
    modulesRules: [{ type: "ESModule", include: ["**/*.js"], fallthrough: true }],
    compatibilityDate: "2024-11-01",
    durableObjects: { SYNC_ROOM: "SyncRoom" },
  });
});
after(async () => mf.dispose());

async function connect(g, device) {
  const res = await mf.dispatchFetch(`http://x/sync?g=${g}`, { headers: { Upgrade: "websocket" } });
  assert.equal(res.status, 101);
  const ws = res.webSocket;
  ws.accept();
  const inbox = [];
  const waiters = [];
  ws.addEventListener("message", (e) => {
    const m = JSON.parse(e.data);
    const i = waiters.findIndex((w) => w.pred(m));
    if (i >= 0) waiters.splice(i, 1)[0].resolve(m);
    else inbox.push(m);
  });
  const next = (pred, ms = 3000) =>
    new Promise((resolve, reject) => {
      const i = inbox.findIndex(pred);
      if (i >= 0) return resolve(inbox.splice(i, 1)[0]);
      const t = setTimeout(() => reject(new Error("timeout waiting for message")), ms);
      waiters.push({ pred, resolve: (m) => (clearTimeout(t), resolve(m)) });
    });
  const send = (o) => ws.send(JSON.stringify(o));
  if (device) send({ t: "hello", device });
  return { ws, send, next, inbox };
}

const phone = { id: "phone-0001", name: "Fold", kind: "phone" };
const tablet = { id: "tablet-0001", name: "Tab", kind: "tablet" };

test("health and bad requests", async () => {
  assert.equal((await mf.dispatchFetch("http://x/health")).status, 200);
  assert.equal((await mf.dispatchFetch("http://x/sync?g=zz", { headers: { Upgrade: "websocket" } })).status, 400);
  assert.equal((await mf.dispatchFetch(`http://x/sync?g=${group}`)).status, 426);
  assert.equal((await mf.dispatchFetch("http://x/nope")).status, 404);
});

test("devices see each other and a closed one disappears", async () => {
  const a = await connect(group, phone);
  const helloA = await a.next((m) => m.t === "hello_ok");
  assert.deepEqual(helloA.devices.map((d) => d.id), ["phone-0001"]);
  const b = await connect(group, tablet);
  const helloB = await b.next((m) => m.t === "hello_ok");
  assert.equal(helloB.devices.length, 2);
  const seen = await a.next((m) => m.t === "devices" && m.devices.length === 2);
  assert.ok(seen.devices.some((d) => d.id === "tablet-0001"));
  b.ws.close(1000, "bye");
  const gone = await a.next((m) => m.t === "devices" && m.devices.length === 1);
  assert.equal(gone.devices[0].id, "phone-0001");
  a.ws.close(1000, "bye");
});

test("shuffle progress: union within a cycle, a higher epoch resets, a stale one is corrected", async () => {
  const a = await connect(group, phone);
  const b = await connect(group, tablet);
  await a.next((m) => m.t === "hello_ok");
  await b.next((m) => m.t === "hello_ok");

  a.send({ t: "shuffle", ctx: "PL:abc", epoch: 0, add: ["s1", "s2"], last: "s2" });
  await a.next((m) => m.t === "shuffle_ack" && m.count === 2);
  const got = await b.next((m) => m.t === "shuffle" && m.ctx === "PL:abc");
  assert.deepEqual(got.add, ["s1", "s2"]);

  b.send({ t: "shuffle", ctx: "PL:abc", epoch: 0, add: ["s2", "s3"], last: "s3" });
  await b.next((m) => m.t === "shuffle_ack" && m.count === 3);
  const delta = await a.next((m) => m.t === "shuffle" && m.ctx === "PL:abc");
  assert.deepEqual(delta.add, ["s3"], "only the new id travels");

  // a cycle ended on the phone: epoch 1, a fresh set
  a.send({ t: "shuffle", ctx: "PL:abc", epoch: 1, add: ["n1"], last: "n1" });
  const reset = await b.next((m) => m.t === "shuffle" && m.reset === true);
  assert.equal(reset.epoch, 1);
  assert.deepEqual(reset.add, ["n1"]);

  // the tablet was offline: it still reports epoch 0 and is told the room's state
  b.send({ t: "shuffle", ctx: "PL:abc", epoch: 0, add: ["old"], last: "old" });
  const full = await b.next((m) => m.t === "shuffle_full");
  assert.equal(full.items[0].epoch, 1);
  assert.deepEqual(full.items[0].ids, ["n1"]);

  // a device that joins later can pull everything
  const c = await connect(group, { id: "pc-00000001", name: "PC", kind: "pc" });
  await c.next((m) => m.t === "hello_ok");
  c.send({ t: "shuffle_get" });
  const all = await c.next((m) => m.t === "shuffle_full");
  assert.equal(all.items.find((i) => i.ctx === "PL:abc").epoch, 1);
  for (const x of [a, b, c]) x.ws.close(1000, "bye");
});

test("now-playing is stored, shown to a device that joins, and commands are routed", async () => {
  const a = await connect(group, phone);
  await a.next((m) => m.t === "hello_ok");
  a.send({ t: "now", state: { queue: ["x1", "x2"], index: 1, positionMs: 42000, playing: true } });
  await new Promise((r) => setTimeout(r, 100));

  const b = await connect(group, tablet);
  const hello = await b.next((m) => m.t === "hello_ok");
  assert.equal(hello.now.index, 1);
  assert.equal(hello.now.deviceId, "phone-0001");

  b.send({ t: "cmd", to: "phone-0001", cmd: "transfer", args: { to: "tablet-0001" } });
  const cmd = await a.next((m) => m.t === "cmd");
  assert.equal(cmd.cmd, "transfer");
  assert.equal(cmd.from, "tablet-0001");

  b.send({ t: "cmd", to: "nobody-00001", cmd: "pause" });
  await b.next((m) => m.t === "cmd_fail");

  a.send({ t: "library", what: "likes" });
  const ping = await b.next((m) => m.t === "library");
  assert.equal(ping.what, "likes");
  for (const x of [a, b]) x.ws.close(1000, "bye");
});

const item = (id) => ({ id, t: "T", a: "A", c: "" });

test("«now» is kept per device: a paused queue on a device that has not played does not hide the one that plays", async () => {
  const g = createHash("sha256").update("now-per-device").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  const b = await connect(g, tablet);
  await b.next((m) => m.t === "hello_ok");
  const pc = await connect(g, { id: "pc-00000001", name: "PC", kind: "pc" });
  await pc.next((m) => m.t === "hello_ok");

  a.send({ t: "now", state: { items: [item("x1")], index: 0, positionMs: 1000, playing: true } });
  assert.equal((await pc.next((m) => m.t === "now")).state.deviceId, "phone-0001");
  // the tablet reconnects and publishes the queue it holds, paused, never played here: the room keeps it but tells nobody
  b.send({ t: "now", state: { items: [item("old")], index: 0, positionMs: 0, playing: false } });
  await new Promise((r) => setTimeout(r, 200));
  assert.equal(pc.inbox.some((m) => m.t === "now" && m.from === "tablet-0001"), false, "a stale paused report is not broadcast");

  // a device that joins now sees the phone, in `now` (older apps) and in `nows`
  const late = await connect(g, { id: "late-00000001", name: "Late", kind: "phone" });
  const hello = await late.next((m) => m.t === "hello_ok");
  assert.equal(hello.now.deviceId, "phone-0001");
  assert.equal(hello.now.playing, true);
  assert.deepEqual(hello.nows.map((n) => n.deviceId), ["phone-0001"]);
  for (const x of [a, b, pc, late]) x.ws.close(1000, "bye");
});

test("«now»: a device that was playing and pauses is still told to the others, and the newest PLAYING one is handed out", async () => {
  const g = createHash("sha256").update("now-pause-reaches").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  const b = await connect(g, tablet);
  await b.next((m) => m.t === "hello_ok");

  b.send({ t: "now", state: { items: [item("t1")], index: 0, positionMs: 0, playing: true } });
  await a.next((m) => m.t === "now" && m.from === "tablet-0001" && m.state.playing === true);
  a.send({ t: "now", state: { items: [item("p1")], index: 0, positionMs: 0, playing: true } });
  await b.next((m) => m.t === "now" && m.from === "phone-0001" && m.state.playing === true);
  // the phone pauses after playing: that is news for the others
  a.send({ t: "now", state: { items: [item("p1")], index: 0, positionMs: 500, playing: false } });
  const paused = await b.next((m) => m.t === "now" && m.from === "phone-0001" && m.state.playing === false);
  assert.equal(paused.state.stale, false);

  const late = await connect(g, { id: "late-00000002", name: "Late", kind: "phone" });
  const hello = await late.next((m) => m.t === "hello_ok");
  assert.equal(hello.now.deviceId, "tablet-0001", "the device that plays wins over a newer paused report");
  assert.deepEqual(hello.nows.map((n) => n.deviceId).sort(), ["phone-0001", "tablet-0001"]);
  for (const x of [a, b, late]) x.ws.close(1000, "bye");
});

test("«now»: a room written by the previous version (one shared `now`) still hands it out", async () => {
  const at = Date.now();
  const stored = new Map([["now", { items: [item("legacy")], index: 0, playing: true, deviceId: "phone-0001", deviceName: "Fold", at }]]);
  const storage = {
    get: async (k) => stored.get(k),
    list: async ({ prefix }) => new Map([...stored].filter(([k]) => k.startsWith(prefix))),
  };
  const room = new SyncRoom({ storage, getWebSockets: () => [] }, {});
  const nows = await room.loadNows();
  assert.deepEqual(Object.keys(nows), ["phone-0001"]);
  assert.equal(nows["phone-0001"].playedAt, at, "it was playing: it counts as played then");
  stored.set("now:tablet-0001", { deviceId: "tablet-0001", playing: false, at });
  assert.deepEqual(Object.keys(await room.loadNows()), ["tablet-0001"], "once devices report one by one, the old single one is history");
  stored.clear();
  assert.deepEqual(await room.loadNows(), {}, "an empty room has none");
});

test("a routed command is acknowledged to its sender with cmd_ok", async () => {
  const g = createHash("sha256").update("cmd-ack").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  const b = await connect(g, tablet);
  await b.next((m) => m.t === "hello_ok");
  b.send({ t: "cmd", to: "phone-0001", cmd: "pause" });
  await a.next((m) => m.t === "cmd" && m.cmd === "pause");
  const ok = await b.next((m) => m.t === "cmd_ok");
  assert.equal(ok.to, "phone-0001");
  assert.equal(ok.cmd, "pause");
  b.send({ t: "cmd", to: "nobody-00001", cmd: "pause" });
  await b.next((m) => m.t === "cmd_fail");
  assert.equal(b.inbox.some((m) => m.t === "cmd_ok"), false, "no ack for an order that went nowhere");
  for (const x of [a, b]) x.ws.close(1000, "bye");
});

test("a group never sees another group, and a device must say hello first", async () => {
  const a = await connect(group, phone);
  await a.next((m) => m.t === "hello_ok");
  const z = await connect(otherGroup, { id: "other-00001", name: "X", kind: "phone" });
  const hello = await z.next((m) => m.t === "hello_ok");
  assert.equal(hello.devices.length, 1);
  assert.equal(hello.now, null);

  const lurker = await connect(group, null);
  lurker.send({ t: "cmd", to: "phone-0001", cmd: "pause" });
  await new Promise((r) => setTimeout(r, 200));
  assert.equal(a.inbox.filter((m) => m.t === "cmd").length, 0);
  for (const x of [a, z, lurker]) x.ws.close(1000, "bye");
});

test("mergeShuffle rules", () => {
  const first = mergeShuffle(null, { epoch: 0, add: ["a", "b", "a"], last: "b" });
  assert.deepEqual(first.state.ids, ["a", "b"]);
  const same = mergeShuffle(first.state, { epoch: 0, add: ["a"], last: null });
  assert.equal(same.changed, false);
  const stale = mergeShuffle({ epoch: 2, ids: ["z"], last: "z" }, { epoch: 1, add: ["q"], last: "q" });
  assert.equal(stale.stale, true);
  const capped = mergeShuffle(null, { epoch: 0, add: Array.from({ length: 3500 }, (_, i) => "id" + i), last: null });
  assert.equal(capped.state.ids.length, 3000);
  assert.equal(capped.state.ids[0], "id500");
});

// ── library registers (row 536) ──────────────────────────────────────────────────────────────────────────────────────────────
const libGroup = createHash("sha256").update("library-room").digest("hex");

test("library: a push reaches the other device as a signal and a pull returns it; the sender gets an ack", async () => {
  const a = await connect(libGroup, phone);
  const b = await connect(libGroup, tablet);
  await a.next((m) => m.t === "hello_ok");
  await b.next((m) => m.t === "hello_ok");

  a.send({ t: "lib_push", ops: [["lk:song1", 1000, "1"], ["dk:song:bad1", 1000, "1"], ["pl:LPabc", 1000, '{"n":"Gym"}']] });
  const ack = await a.next((m) => m.t === "lib_ack");
  assert.equal(ack.accepted, 3);
  const signal = await b.next((m) => m.t === "lib_ops");
  assert.equal(signal.seq, 3);

  b.send({ t: "lib_pull", since: 0 });
  const state = await b.next((m) => m.t === "lib_state");
  assert.deepEqual(state.items.map((i) => i[0]).sort(), ["dk:song:bad1", "lk:song1", "pl:LPabc"]);
  assert.equal(state.more, false);
  assert.equal(state.seq, 3);
  a.ws.close();
  b.ws.close();
});

test("library: the latest timestamp wins, an older one is ignored, a tie settles on the larger value", async () => {
  const g = createHash("sha256").update("library-lww").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  a.send({ t: "lib_push", ops: [["lk:x", 1000, "1"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 1);
  // unlike later → accepted
  a.send({ t: "lib_push", ops: [["lk:x", 2000, "0"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 1);
  // a stale like (older than the unlike) → ignored
  a.send({ t: "lib_push", ops: [["lk:x", 1500, "1"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 0);
  // the same op again → ignored (idempotent)
  a.send({ t: "lib_push", ops: [["lk:x", 2000, "0"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 0);
  // a tie with a larger value wins
  a.send({ t: "lib_push", ops: [["lk:x", 2000, "1"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 1);

  a.send({ t: "lib_pull", since: 0 });
  const state = await a.next((m) => m.t === "lib_state");
  // one entry per item: the log keeps only the current value of lk:x
  assert.deepEqual(state.items, [["lk:x", 2000, "1"]]);
  a.ws.close();
});

test("library: pulling «since» returns only what changed, in pages, and a client ahead of the room starts over", async () => {
  const g = createHash("sha256").update("library-pages").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  const ops = Array.from({ length: 400 }, (_, i) => [`lk:s${i}`, 1000 + i, "1"]);
  a.send({ t: "lib_push", ops });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 400);
  a.send({ t: "lib_push", ops: [["lk:extra", 5000, "1"]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).seq, 401);

  a.send({ t: "lib_pull", since: 0 });
  const first = await a.next((m) => m.t === "lib_state");
  assert.equal(first.items.length, 400);
  assert.equal(first.more, true);
  a.send({ t: "lib_pull", since: first.seq });
  const second = await a.next((m) => m.t === "lib_state");
  assert.deepEqual(second.items, [["lk:extra", 5000, "1"]]);
  assert.equal(second.more, false);

  a.send({ t: "lib_pull", since: 999999 });
  const reset = await a.next((m) => m.t === "lib_state");
  assert.equal(reset.reset, true);
  assert.equal(reset.items.length, 400);
  a.ws.close();
});

test("library: bad input is dropped, rooms are isolated, and nothing works before hello", async () => {
  const g1 = createHash("sha256").update("library-iso-1").digest("hex");
  const g2 = createHash("sha256").update("library-iso-2").digest("hex");
  const a = await connect(g1, phone);
  const b = await connect(g2, tablet);
  await a.next((m) => m.t === "hello_ok");
  await b.next((m) => m.t === "hello_ok");
  a.send({ t: "lib_push", ops: [["lk:ok", 1, "1"], ["", 1, "1"], ["k".repeat(200), 1, "1"], ["lk:nan", "x", "1"], "junk", ["lk:long", 1, "v".repeat(900)]] });
  assert.equal((await a.next((m) => m.t === "lib_ack")).accepted, 1);
  b.send({ t: "lib_pull", since: 0 });
  assert.deepEqual((await b.next((m) => m.t === "lib_state")).items, []);
  const stranger = await connect(g1);
  stranger.send({ t: "lib_pull", since: 0 });
  stranger.send({ t: "ping" });
  assert.equal((await stranger.next((m) => m.t === "pong")).t, "pong");
  assert.equal(stranger.inbox.some((m) => m.t === "lib_state"), false);
  a.ws.close();
  b.ws.close();
  stranger.ws.close();
});

// ── row 900: each op is acknowledged on its own ──────────────────────────────────────────────────────────────────────────────

test("library: every op id the room is done with is acknowledged — applied, older, repeated or malformed", async () => {
  const g = createHash("sha256").update("library-per-op-ack").digest("hex");
  const a = await connect(g, phone);
  await a.next((m) => m.t === "hello_ok");
  a.send({ t: "lib_push", ops: [["lk:x", 2000, "1", "id-new"], ["lk:y", 1000, "1", "id-y"]] });
  const first = await a.next((m) => m.t === "lib_ack");
  assert.deepEqual(first.ids.sort(), ["id-new", "id-y"]);
  assert.equal(first.accepted, 2);
  // the same op again (a retry after a lost ack) and an older one: both done, neither applied
  a.send({ t: "lib_push", ops: [["lk:x", 2000, "1", "id-new"], ["lk:x", 1500, "0", "id-old"], ["", 1, "1", "id-bad"]] });
  const second = await a.next((m) => m.t === "lib_ack");
  assert.equal(second.accepted, 0);
  assert.deepEqual(second.ids.sort(), ["id-bad", "id-new", "id-old"]);
  assert.equal(second.seq, first.seq, "nothing new in the room");
  a.send({ t: "lib_pull", since: 0 });
  const state = await a.next((m) => m.t === "lib_state");
  assert.deepEqual(state.items.sort(), [["lk:x", 2000, "1"], ["lk:y", 1000, "1"]], "the log never carries the op ids");
  a.ws.close();
});

test("library: an app that sends three-element ops (an older version) is served exactly as before", async () => {
  const g = createHash("sha256").update("library-old-client").digest("hex");
  const oldApp = await connect(g, phone);
  const newApp = await connect(g, tablet);
  await oldApp.next((m) => m.t === "hello_ok");
  await newApp.next((m) => m.t === "hello_ok");
  oldApp.send({ t: "lib_push", ops: [["lk:old", 1000, "1"], ["pl:LPabc", 1000, '{"n":"Gym"}']] });
  const ack = await oldApp.next((m) => m.t === "lib_ack");
  assert.equal(ack.accepted, 2);
  assert.equal(ack.received, 2);
  assert.ok(Number.isInteger(ack.seq));
  assert.deepEqual(ack.ids, [], "no ids sent, none echoed");
  await newApp.next((m) => m.t === "lib_ops");
  newApp.send({ t: "lib_push", ops: [["lk:new", 1000, "1", "n1"]] });
  assert.deepEqual((await newApp.next((m) => m.t === "lib_ack")).ids, ["n1"]);
  await oldApp.next((m) => m.t === "lib_ops");
  oldApp.send({ t: "lib_pull", since: 0 });
  const state = await oldApp.next((m) => m.t === "lib_state");
  assert.ok(state.items.every((i) => i.length === 3), "an older app still gets [key, ts, value]");
  assert.deepEqual(state.items.map((i) => i[0]).sort(), ["lk:new", "lk:old", "pl:LPabc"]);
  oldApp.ws.close();
  newApp.ws.close();
});

// ── row 901: the room forgets the removals every device has pulled past ──────────────────────────────────────────────────────

const oldTs = 1000; // a removal from 1970: far older than the 30 days the cleanup waits
test("cleanup: nothing is forgotten while one device is behind, nor in the first retention window after the deploy", async () => {
  const g = createHash("sha256").update("library-prune").digest("hex");
  const a = await connect(g, phone);
  const b = await connect(g, tablet);
  await a.next((m) => m.t === "hello_ok");
  await b.next((m) => m.t === "hello_ok");
  a.send({
    t: "lib_push",
    ops: [
      ["lk:gone", oldTs, "0"],
      ["pm:LPabc:s1", oldTs, "0"],
      ["po:LPabc:s1", oldTs, "a01"],
      ["lk:keep", oldTs, "1"],
      ["pl:LPabc", oldTs, '{"n":"Gym"}'],
      ["lk:young", Date.now(), "0"],
    ],
  });
  const head = (await a.next((m) => m.t === "lib_ack")).seq;
  const pull = async (dev, since) => {
    dev.send({ t: "lib_pull", since });
    return dev.next((m) => m.t === "lib_state");
  };
  await pull(a, 0);
  await pull(b, 0);
  await pull(a, head); // the phone has everything; the tablet has not said so yet
  // (pulling from 0 again is what a device does after a restore: it needs everything, and holds the cleanup back until it says so)
  assert.equal((await pull(a, 0)).items.length, 6, "nothing forgotten while one device is behind");
  await pull(a, head);
  await pull(b, head); // now both have pulled past every entry
  // The room started noting devices just now: a device paired earlier may not have said where it is yet, so nothing goes before a
  // whole retention window has passed (the forgetting itself, with the clock moved, is in the tests below).
  const fresh = await connect(g, { id: "old-app-0001", name: "Old", kind: "phone" });
  await fresh.next((m) => m.t === "hello_ok");
  const all = await pull(fresh, 0);
  assert.ok(all.items.every((i) => i.length === 3), "an older app still gets [key, ts, value]");
  assert.equal(all.items.length, 6, "nothing forgotten in the first 180 days the room notes devices");
  a.ws.close();
  b.ws.close();
  fresh.ws.close();
});

/** A room on an in-memory storage, to move the clock: get/put/delete/list as the Durable Object storage does them. */
function memoryRoom() {
  const data = new Map();
  const storage = {
    get: async (k) => (Array.isArray(k) ? new Map(k.filter((x) => data.has(x)).map((x) => [x, data.get(x)])) : data.get(k)),
    put: async (k, v) => {
      if (typeof k === "string") data.set(k, v);
      else for (const [kk, vv] of Object.entries(k)) data.set(kk, vv);
    },
    delete: async (k) => (Array.isArray(k) ? k : [k]).forEach((x) => data.delete(x)),
    list: async ({ prefix, start, end, limit }) => {
      const keys = [...data.keys()].filter((k) => k.startsWith(prefix) && (!start || k >= start) && (!end || k < end)).sort();
      return new Map(keys.slice(0, limit ?? keys.length).map((k) => [k, data.get(k)]));
    },
  };
  const room = new SyncRoom({ storage, getWebSockets: () => [] }, {});
  const sent = [];
  const ws = { send: (t) => sent.push(JSON.parse(t)) };
  return { room, data, ws, sent };
}

/** [fn] with the Worker's clock at [t] (libPull reads Date.now()). */
async function atTime(t, fn) {
  const real = Date.now;
  Date.now = () => t;
  try {
    return await fn();
  } finally {
    Date.now = real;
  }
}

test("cleanup: a removal goes only once EVERY device pulled past it, with the position of the song a list lost", async () => {
  const day = 24 * 3600_000;
  const t0 = Date.UTC(2026, 10, 27);
  const { room, data, ws, sent } = memoryRoom();
  const pull = (id, since, t) => atTime(t, () => room.libPull(ws, { since }, { id })).then(() => sent.at(-1));
  await room.libPush(ws, {
    ops: [
      ["lk:gone", oldTs, "0"],
      ["pm:LPabc:s1", oldTs, "0"],
      ["po:LPabc:s1", oldTs, "a01"],
      ["lk:keep", oldTs, "1"],
      ["pl:LPabc", oldTs, '{"n":"Gym"}'],
    ],
  });
  const head = data.get("libseq");
  await pull("phone-0001", head, t0);
  await pull("tablet-0001", 0, t0 + 100 * day);
  await pull("phone-0001", head, t0 + 181 * day);
  assert.equal(data.has("k:lk:gone"), true, "the tablet has not pulled past it yet");
  await pull("tablet-0001", head, t0 + 182 * day);
  const all = await pull("new-app-0001", 0, t0 + 182 * day);
  assert.deepEqual(all.items.map((i) => i[0]).sort(), ["lk:keep", "pl:LPabc"], "old removals (and the lost song's position) are forgotten");
});

test("cleanup: a device paired before the room noted devices is waited for a whole retention window", async () => {
  const day = 24 * 3600_000;
  const t0 = Date.UTC(2026, 10, 27); // the day this Worker is deployed
  const { room, data, ws, sent } = memoryRoom();
  const pull = (id, since, t) => atTime(t, () => room.libPull(ws, { since }, { id })).then(() => sent.at(-1));
  // The tablet last pulled from the previous Worker 60 days before t0 (so it has no `ld:`); since then the phone removed a song.
  await room.libPush(ws, { ops: [["lk:x", t0 - 40 * day, "0"]] });
  const head = data.get("libseq");
  await pull("phone-0001", head, t0);
  assert.equal(data.has("k:lk:x"), true, "the first pull after the deploy forgets nothing");
  await pull("phone-0001", head, t0 + 100 * day);
  assert.equal(data.has("k:lk:x"), true, "nor any pull inside the window");
  const back = await pull("tablet-0001", 0, t0 + 110 * day); // back after 170 days: within the 180 promised
  assert.deepEqual(back.items.map((i) => i[0]), ["lk:x"], "the tablet still learns the removal");
  await pull("tablet-0001", head, t0 + 110 * day);
  await pull("phone-0001", head, t0 + 181 * day);
  assert.equal(data.has("k:lk:x"), false, "once every device pulled past it and the window passed, it goes");
});

test("cleanup: a device not seen for 180 days no longer holds it back, a removal younger than 30 days is kept, and it runs at most every 12 h", async () => {
  const day = 24 * 3600_000;
  const now = Date.UTC(2026, 10, 27);
  const { room, data, ws } = memoryRoom();
  data.set("libpruneSince", now - 200 * day); // the room has noted devices for longer than a retention window
  await room.libPush(ws, { ops: [["lk:old", now - 60 * day, "0"], ["lk:recent", now - 5 * day, "0"], ["lk:after", now - 60 * day, "0"]] });
  await room.noteDevice("phone-0001", 3, now);
  await room.noteDevice("lost-0001", 0, now - 200 * day); // paired once, never back
  assert.equal(await room.libPrune(now), 1, "only the old removal before the young one goes");
  assert.equal(data.has("k:lk:old"), false);
  assert.equal(data.has("k:lk:recent"), true, "too young: kept");
  assert.equal(data.has("k:lk:after"), true, "the pass stopped at the young removal; it is looked at again later");
  assert.equal(data.has("ld:lost-0001"), false, "the device gone for 200 days is forgotten too");
  assert.equal(await room.libPrune(now + 3600_000), 0, "not again within 12 h");
  assert.equal(await room.libPrune(now + 26 * day), 2, "once the young one is old enough, it and the rest go");
  assert.equal(data.get("libcount"), 0);
});

test("cleanup: an older op for a forgotten removal is turned down (and acknowledged), a newer one comes back", async () => {
  const day = 24 * 3600_000;
  const now = Date.UTC(2026, 10, 27);
  const { room, data, ws, sent } = memoryRoom();
  data.set("libpruneSince", now - 200 * day);
  await room.libPush(ws, { ops: [["lk:x", now - 60 * day, "0"], ["pm:LPabc:s1", now - 60 * day, "0"], ["po:LPabc:s1", now - 70 * day, "a01"]] });
  await room.noteDevice("phone-0001", data.get("libseq"), now);
  assert.equal(await room.libPrune(now), 3);
  assert.equal(data.get("libcount"), 0);
  // A tablet whose `ld:` was dropped comes back with a like it made offline BEFORE the removal, and an old position.
  await room.libPush(ws, { ops: [["lk:x", now - 90 * day, "1", "old-like"], ["po:LPabc:s1", now - 80 * day, "a02", "old-pos"]] });
  const refused = sent.at(-1);
  assert.equal(refused.accepted, 0, "the removal is newer: nothing comes back");
  assert.deepEqual(refused.ids.sort(), ["old-like", "old-pos"], "and the device is done with them");
  assert.equal(data.has("k:lk:x"), false);
  await room.libPush(ws, { ops: [["lk:x", now - day, "1", "new-like"]] });
  assert.equal(sent.at(-1).accepted, 1, "a like made after the removal is a new change");
  assert.equal(data.get("k:lk:x").v, "1");
  assert.equal(data.has("t:lk:x"), false, "its tombstone goes");
  assert.equal(data.get("libcount"), 1);
});

test("library: a full room says so in the ack and leaves the new key out of `ids`; a change to a key it has still lands", async () => {
  const { room, data, ws, sent } = memoryRoom();
  await room.libPush(ws, { ops: [["lk:have", 1000, "1", "have"]] });
  assert.equal(sent.at(-1).full, undefined, "a room with space says nothing new");
  data.set("libcount", 150000);
  await room.libPush(ws, { ops: [["lk:new", 2000, "1", "new"], ["lk:have", 2000, "0", "change"]] });
  const ack = sent.at(-1);
  assert.equal(ack.full, true);
  assert.deepEqual(ack.ids, ["change"], "the new key is not done with: the app keeps it");
  assert.equal(data.has("k:lk:new"), false);
  assert.equal(data.get("k:lk:have").v, "0");
});

test("cleanup: a device that is still behind holds everything after its place", async () => {
  const now = Date.UTC(2026, 10, 27);
  const { room, data, ws } = memoryRoom();
  data.set("libpruneSince", now - 200 * 24 * 3600_000);
  await room.libPush(ws, { ops: [["lk:a", 1000, "0"], ["lk:b", 1000, "0"]] });
  await room.noteDevice("phone-0001", 2, now);
  await room.noteDevice("tablet-0001", 1, now);
  assert.equal(await room.libPrune(now), 1);
  assert.equal(data.has("k:lk:a"), false);
  assert.equal(data.has("k:lk:b"), true, "the tablet has not pulled seq 2 yet");
});

test("health says which protocol this deployment speaks, and hello_ok repeats it", async () => {
  const res = await mf.dispatchFetch("http://x/health");
  const body = await res.json();
  assert.equal(body.ok, true);
  assert.ok(body.lib >= 2);
  const g = createHash("sha256").update("health-version").digest("hex");
  const a = await connect(g, phone);
  assert.equal((await a.next((m) => m.t === "hello_ok")).lib, body.lib);
  a.ws.close();
});

test("a device that reconnects is listed ONCE, never next to its own stale socket (the 2-3-2-3 counter)", async () => {
  const g = createHash("sha256").update("dedupe-devices").digest("hex");
  const tabletConn = await connect(g, tablet);
  await tabletConn.next((m) => m.t === "hello_ok");
  const first = await connect(g, phone);
  await first.next((m) => m.t === "hello_ok");
  // the phone reconnects (a new socket, the old one not closed by the client)
  const second = await connect(g, phone);
  const ok = await second.next((m) => m.t === "hello_ok");
  assert.deepEqual(ok.devices.map((d) => d.id).sort(), ["phone-0001", "tablet-0001"]);
  // and everyone who hears a `devices` update hears two, never three
  const heard = [];
  for (let i = 0; i < 3; i++) {
    try {
      const m = await tabletConn.next((x) => x.t === "devices", 400);
      heard.push(m.devices.length);
    } catch {
      break;
    }
  }
  assert.ok(heard.length > 0);
  assert.ok(heard.every((n) => n === 2), `device counts heard: ${heard}`);
  tabletConn.ws.close();
  first.ws.close();
  second.ws.close();
});

test("an order for a device that reconnected reaches its NEW connection, and the old one hears nothing", async () => {
  const g = createHash("sha256").update("order-to-newest").digest("hex");
  const tab = await connect(g, tablet);
  await tab.next((m) => m.t === "hello_ok");
  const oldPhone = await connect(g, phone);
  await oldPhone.next((m) => m.t === "hello_ok");
  // the phone reconnects with a new socket while the old one is still around
  const newPhone = await connect(g, phone);
  await newPhone.next((m) => m.t === "hello_ok");

  tab.send({ t: "cmd", to: "phone-0001", cmd: "handoff", args: { to: "tablet-0001" } });
  const got = await newPhone.next((m) => m.t === "cmd");
  assert.equal(got.cmd, "handoff");
  assert.equal(got.args.to, "tablet-0001");
  assert.equal(oldPhone.inbox.some((m) => m.t === "cmd"), false, "the stale socket must not get the order");
  tab.ws.close();
  oldPhone.ws.close();
  newPhone.ws.close();
});
