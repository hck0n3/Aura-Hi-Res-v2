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
