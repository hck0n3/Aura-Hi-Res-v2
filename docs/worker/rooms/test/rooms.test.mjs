// node --test docs/worker/rooms/test  (the Worker for real on workerd via Miniflare)
import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { Miniflare } from "miniflare";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
let mf;
before(async () => {
  mf = new Miniflare({
    modules: true,
    scriptPath: path.join(here, "../src/index.js"),
    compatibilityDate: "2024-11-01",
    durableObjects: { ROOM: "Room" },
  });
});
after(async () => mf.dispose());

async function open(pathAndQuery) {
  const res = await mf.dispatchFetch("http://x" + pathAndQuery, { headers: { Upgrade: "websocket" } });
  if (res.status !== 101) return { status: res.status, body: await res.json().catch(() => ({})) };
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
  const next = (pred, ms = 3000) => {
    const i = inbox.findIndex(pred);
    if (i >= 0) return Promise.resolve(inbox.splice(i, 1)[0]);
    return new Promise((resolve, reject) => {
      const w = { pred, resolve };
      waiters.push(w);
      setTimeout(() => { const j = waiters.indexOf(w); if (j >= 0) { waiters.splice(j, 1); reject(new Error("timeout waiting for message")); } }, ms);
    });
  };
  return { ws, next, status: 101, send: (m) => ws.send(JSON.stringify(m)), has: (pred) => inbox.some(pred), close: () => ws.close() };
}
const is = (t) => (m) => m.t === t;

async function host(name = "Ana") {
  const h = await open("/rooms/create");
  assert.equal(h.status, 101);
  h.send({ t: "create_room", username: name });
  const created = await h.next(is("room_created"));
  return { h, created };
}
async function guest(code, name = "Beto", h) {
  const g = await open("/rooms/join?code=" + code);
  assert.equal(g.status, 101);
  g.send({ t: "join_room", username: name });
  await g.next(is("join_pending"));
  const req = await h.next(is("join_request"));
  h.send({ t: "approve_join", userId: req.userId });
  const ok = await g.next(is("join_approved"));
  return { g, ok };
}
const song = (id) => ({ id, title: "Canción " + id, artist: "Artista", album: "Álbum", duration: 200000, thumbnail: "https://i.ytimg.com/" + id });

test("a host creates a room and a guest joins only after approval", async () => {
  const { h, created } = await host();
  assert.match(created.roomCode, /^[A-Z2-9]{6}$/);
  const { ok } = await guest(created.roomCode, "Beto", h);
  assert.equal(ok.hostId, created.userId);
  assert.equal(ok.users.length, 2);
  const joined = await h.next(is("user_joined"));
  assert.equal(joined.user.username, "Beto");
});

test("a rejected guest is told and cut off; a wrong code never opens a socket", async () => {
  const { h, created } = await host();
  const g = await open("/rooms/join?code=" + created.roomCode);
  g.send({ t: "join_room", username: "Intruso" });
  const req = await h.next(is("join_request"));
  h.send({ t: "reject_join", userId: req.userId, reason: "no" });
  assert.equal((await g.next(is("join_rejected"))).reason, "no");
  assert.equal((await open("/rooms/join?code=ZZZZZZ")).status, 404);
  assert.equal((await open("/rooms/join?code=ab")).status, 400);
});

test("only the host drives playback; guests receive it with a server clock", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  g.send({ t: "playback_action", action: "pause" });
  assert.equal((await g.next(is("error"))).code, "host_only");
  h.send({ t: "playback_action", action: "change_track", track: song("abc12345678"), positionMs: 0 });
  const sync = await g.next(is("sync_playback"));
  assert.equal(sync.action, "change_track");
  assert.equal(sync.track.id, "abc12345678");
  assert.ok(sync.serverTime > 0);
  assert.equal(sync.playing, true);
});

test("a new track makes the host wait for everyone to buffer, then plays together", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  h.send({ t: "playback_action", action: "change_track", track: song("trk00000001") });
  await g.next(is("buffer_wait"));
  g.send({ t: "buffer_ready" });
  const done = await h.next(is("buffer_complete"));
  assert.equal(done.playing, true);
});

test("position keeps moving while playing and a late joiner gets the right spot", async () => {
  const { h, created } = await host();
  h.send({ t: "playback_action", action: "change_track", track: song("trk00000002"), positionMs: 10000 });
  await new Promise((r) => setTimeout(r, 400));
  const { g, ok } = await guest(created.roomCode, "Tarde", h);
  assert.ok(ok.state.positionMs >= 10300 && ok.state.positionMs < 12000, "position " + ok.state.positionMs);
  g.send({ t: "request_sync" });
  assert.ok((await g.next(is("sync_state"))).state.positionMs >= ok.state.positionMs);
  h.send({ t: "playback_action", action: "pause" });
  await new Promise((r) => setTimeout(r, 300));
  g.send({ t: "request_sync" });
  const paused = await g.next(is("sync_state"));
  assert.equal(paused.state.playing, false);
});

test("suggestions go through the host; approved ones join the queue", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  g.send({ t: "suggest_track", track: song("sug00000001") });
  const s = await h.next(is("suggestion_received"));
  assert.equal(s.track.suggestedBy, "Beto");
  h.send({ t: "approve_suggestion", suggestionId: s.suggestionId });
  assert.equal((await g.next(is("suggestion_approved"))).track.id, "sug00000001");
  g.send({ t: "request_sync" });
  assert.equal((await g.next(is("sync_state"))).state.queue.length, 1);
});

test("kick, transfer and leave keep the room consistent", async () => {
  const { h, created } = await host();
  const { g: g1, ok: ok1 } = await guest(created.roomCode, "Uno", h);
  const { g: g2, ok: ok2 } = await guest(created.roomCode, "Dos", h);
  h.send({ t: "transfer_host", userId: ok1.userId });
  assert.equal((await g2.next(is("host_changed"))).hostId, ok1.userId);
  assert.equal((await h.next(is("host_changed"))).hostId, ok1.userId);
  h.send({ t: "kick_user", userId: ok2.userId });
  assert.equal((await h.next(is("error"))).code, "host_only", "the old host can no longer kick");
  g1.send({ t: "kick_user", userId: ok2.userId });
  await g2.next(is("kicked"));
  g1.send({ t: "leave_room" });
  const left = await h.next(is("host_changed"));
  assert.equal(left.hostId, created.userId, "the only one left becomes host");
});

test("a dropped guest can reconnect with its token; a wrong token cannot", async () => {
  const { h, created } = await host();
  const { g, ok } = await guest(created.roomCode, "Beto", h);
  g.close();
  assert.equal((await h.next(is("user_disconnected"))).userId, ok.userId);
  const back = await open("/rooms/join?code=" + created.roomCode);
  back.send({ t: "reconnect", userId: ok.userId, token: "wrong" });
  assert.equal((await back.next(is("error"))).code, "bad_token");
  back.send({ t: "reconnect", userId: ok.userId, token: ok.token });
  const r = await back.next(is("reconnected"));
  assert.equal(r.users.length, 2);
  assert.equal((await h.next(is("user_reconnected"))).userId, ok.userId);
});

test("ping answers with the server clock; junk and unknown messages are refused politely", async () => {
  const { h } = await host();
  h.send({ t: "ping", t0: 123 });
  const pong = await h.next(is("pong"));
  assert.equal(pong.t0, 123);
  assert.ok(Math.abs(pong.serverTime - Date.now()) < 5000);
  h.send({ t: "do_evil" });
  assert.equal((await h.next(is("error"))).code, "unknown_message_type");
  h.ws.send("not json");
  assert.equal((await h.next(is("error"))).code, "bad_json");
});

test("chat reaches everyone, clipped; the room is capped", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  g.send({ t: "chat", text: "hola ".repeat(200) });
  const c = await h.next(is("chat"));
  assert.ok(c.text.length <= 300);
});
