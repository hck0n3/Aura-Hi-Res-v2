// Runs the Worker for real (workerd via Miniflare): node --test docs/worker/sync/test
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { mergeShuffle } from "../src/merge.js";

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
