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
  const inbox = [];
  const waiters = [];
  ws.addEventListener("message", (e) => {
    const env = JSON.parse(e.data);
    const m = { type: env.type, ...env.payload };
    const i = waiters.findIndex((w) => w.pred(m));
    if (i >= 0) waiters.splice(i, 1)[0].resolve(m);
    else inbox.push(m);
  });
  ws.accept();
  const next = (pred, ms = 3000) => {
    const i = inbox.findIndex(pred);
    if (i >= 0) return Promise.resolve(inbox.splice(i, 1)[0]);
    return new Promise((resolve, reject) => {
      const w = { pred, resolve };
      waiters.push(w);
      setTimeout(() => { const j = waiters.indexOf(w); if (j >= 0) { waiters.splice(j, 1); reject(new Error("timeout waiting for message")); } }, ms);
    });
  };
  const send = (type, payload = {}) => ws.send(JSON.stringify({ type, payload }));
  return { ws, next, status: 101, send, has: (pred) => inbox.some(pred), close: () => ws.close() };
}
const is = (t) => (m) => m.type === t;

async function host(name = "Ana") {
  const h = await open("/rooms/create");
  assert.equal(h.status, 101);
  h.send("create_room", { username: name });
  const created = await h.next(is("room_created"));
  return { h, created };
}
async function guest(code, name = "Beto", h) {
  const g = await open("/rooms/join?code=" + code);
  assert.equal(g.status, 101);
  g.send("join_room", { roomCode: code, username: name });
  const req = await h.next(is("join_request"));
  h.send("approve_join", { userId: req.userId });
  const ok = await g.next(is("join_approved"));
  return { g, ok };
}
const song = (id) => ({ id, title: "Canción " + id, artist: "Artista", album: "Álbum", duration: 200000, thumbnail: "https://i.ytimg.com/" + id });

test("the server greets with its capabilities, JSON only", async () => {
  const h = await open("/rooms/create");
  const caps = await h.next(is("server_capabilities"));
  assert.equal(caps.supportsProtobuf, false);
  assert.equal(caps.supportsCompression, false);
  h.send("client_capabilities", { supportsProtobuf: true, supportsCompression: true, clientVersion: "AuraHiRes" });
  h.send("ping", { clientTime: 5, sequence: 1 });
  assert.equal((await h.next(is("pong"))).sequence, 1, "client_capabilities is accepted silently");
});

test("a host creates a room and a guest joins only after approval", async () => {
  const { h, created } = await host();
  assert.match(created.roomCode, /^[A-Z2-9]{6}$/);
  assert.ok(created.sessionToken.startsWith(created.userId + "."));
  const { ok } = await guest(created.roomCode, "Beto", h);
  assert.equal(ok.state.hostId, created.userId);
  assert.equal(ok.state.users.length, 2);
  assert.equal(ok.state.roomCode, created.roomCode);
  const joined = await h.next(is("user_joined"));
  assert.equal(joined.username, "Beto");
});

test("a rejected guest is told and cut off; a wrong code never opens a socket", async () => {
  const { h, created } = await host();
  const g = await open("/rooms/join?code=" + created.roomCode);
  g.send("join_room", { roomCode: created.roomCode, username: "Intruso" });
  const req = await h.next(is("join_request"));
  h.send("reject_join", { userId: req.userId, reason: "no" });
  assert.equal((await g.next(is("join_rejected"))).reason, "no");
  assert.equal((await open("/rooms/join?code=ZZZZZZ")).status, 404);
  assert.equal((await open("/rooms/join?code=ab")).status, 400);
});

test("only the host drives playback; guests receive it with a server clock", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  g.send("playback_action", { action: "pause" });
  const err = await g.next(is("error"));
  assert.equal(err.code, "host_only");
  assert.ok(err.message.length > 5, "the error carries text the app can show");
  h.send("playback_action", { action: "change_track", trackId: "abc12345678", position: 0, trackInfo: song("abc12345678"), queue: [song("q0000000001")] });
  const sync = await g.next(is("sync_playback"));
  assert.equal(sync.action, "change_track");
  assert.equal(sync.trackInfo.id, "abc12345678");
  assert.equal(sync.trackId, "abc12345678");
  assert.equal(sync.queue.length, 1, "the queue rides on the same message as the track");
  assert.ok(sync.serverTime > 0);
  assert.equal(sync.capturedAtServerTime, sync.serverTime);
  assert.ok(sync.revision >= 1);
});

test("a new track makes everyone wait for the slowest, then they start together", async () => {
  const { h, created } = await host();
  const { g, ok } = await guest(created.roomCode, "Beto", h);
  h.send("playback_action", { action: "change_track", trackId: "trk00000001", trackInfo: song("trk00000001") });
  const wait = await g.next(is("buffer_wait"));
  assert.deepEqual(wait.waitingFor, [ok.userId]);
  g.send("buffer_ready", { trackId: "trk00000001" });
  assert.equal((await h.next(is("buffer_complete"))).trackId, "trk00000001");
  const go = await h.next((m) => m.type === "sync_playback" && m.action === "play");
  assert.equal(go.trackId, "trk00000001");
  const goGuest = await g.next((m) => m.type === "sync_playback" && m.action === "play");
  assert.ok(goGuest.serverTime > 0);
});

test("position keeps moving while playing and a late joiner gets the right spot", async () => {
  const { h, created } = await host();
  h.send("playback_action", { action: "change_track", trackId: "trk00000002", position: 10000, trackInfo: song("trk00000002") });
  await new Promise((r) => setTimeout(r, 400));
  const { g, ok } = await guest(created.roomCode, "Tarde", h);
  assert.ok(ok.state.position >= 10300 && ok.state.position < 12000, "position " + ok.state.position);
  assert.equal(ok.state.isPlaying, true);
  g.send("request_sync");
  assert.ok((await g.next(is("sync_state"))).position >= ok.state.position);
  h.send("playback_action", { action: "pause" });
  await new Promise((r) => setTimeout(r, 300));
  g.send("request_sync");
  const paused = await g.next(is("sync_state"));
  assert.equal(paused.isPlaying, false);
});

test("suggestions go through the host; approved ones join the queue", async () => {
  const { h, created } = await host();
  const { g, ok } = await guest(created.roomCode, "Beto", h);
  g.send("suggest_track", { trackInfo: song("sug00000001") });
  const s = await h.next(is("suggestion_received"));
  assert.equal(s.trackInfo.suggestedBy, "Beto");
  assert.equal(s.fromUserId, ok.userId);
  assert.equal(s.fromUsername, "Beto");
  h.send("approve_suggestion", { suggestionId: s.suggestionId });
  assert.equal((await g.next(is("suggestion_approved"))).trackInfo.id, "sug00000001");
  g.send("request_sync");
  assert.equal((await g.next(is("sync_state"))).queue.length, 1);
});

test("kick, transfer and leave keep the room consistent", async () => {
  const { h, created } = await host();
  const { g: g1, ok: ok1 } = await guest(created.roomCode, "Uno", h);
  const { g: g2, ok: ok2 } = await guest(created.roomCode, "Dos", h);
  h.send("transfer_host", { newHostId: ok1.userId });
  const hc = await g2.next(is("host_changed"));
  assert.equal(hc.newHostId, ok1.userId);
  assert.equal(hc.newHostName, "Uno");
  assert.equal((await h.next(is("host_changed"))).newHostId, ok1.userId);
  h.send("kick_user", { userId: ok2.userId });
  assert.equal((await h.next(is("error"))).code, "host_only", "the old host can no longer kick");
  g1.send("kick_user", { userId: ok2.userId, reason: "fuera" });
  assert.equal((await g2.next(is("kicked"))).reason, "fuera");
  g1.send("leave_room");
  const left = await h.next(is("host_changed"));
  assert.equal(left.newHostId, created.userId, "the only one left becomes host");
});

test("a dropped guest can reconnect with its session token; a wrong token cannot", async () => {
  const { h, created } = await host();
  const { g, ok } = await guest(created.roomCode, "Beto", h);
  g.close();
  const gone = await h.next(is("user_disconnected"));
  assert.equal(gone.userId, ok.userId);
  assert.equal(gone.username, "Beto");
  const back = await open("/rooms/join?code=" + created.roomCode);
  back.send("reconnect", { sessionToken: ok.userId + ".wrong" });
  assert.equal((await back.next(is("error"))).code, "bad_token");
  back.send("reconnect", { sessionToken: ok.sessionToken });
  const r = await back.next(is("reconnected"));
  assert.equal(r.state.users.length, 2);
  assert.equal(r.isHost, false);
  assert.equal(r.roomCode, created.roomCode);
  assert.equal((await h.next(is("user_reconnected"))).userId, ok.userId);
});

test("ping answers with the server clock; junk and unknown messages are refused politely", async () => {
  const { h } = await host();
  h.send("ping", { clientTime: 123, sequence: 7 });
  const pong = await h.next(is("pong"));
  assert.equal(pong.clientTime, 123);
  assert.equal(pong.sequence, 7);
  assert.ok(Math.abs(pong.serverSendTime - Date.now()) < 5000);
  assert.ok(pong.serverReceiveTime <= pong.serverSendTime);
  h.send("do_evil");
  assert.equal((await h.next(is("error"))).code, "unknown_message_type");
  h.ws.send("not json");
  assert.equal((await h.next(is("error"))).code, "bad_json");
});

test("chat reaches everyone, clipped", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  g.send("chat", { text: "hola ".repeat(200) });
  const c = await h.next(is("chat"));
  assert.ok(c.text.length <= 300);
});

test("the wire layer maps the app's spelling both ways", async () => {
  const { fromWire, toWire } = await import("../src/index.js");
  assert.deepEqual(fromWire("transfer_host", { newHostId: "X" }), { t: "transfer_host", userId: "X" });
  assert.deepEqual(fromWire("reconnect", { sessionToken: "ABC.def.ghi" }), { t: "reconnect", userId: "ABC", token: "def.ghi" });
  assert.equal(toWire({ t: "host_changed", hostId: "H", username: "N" }).payload.newHostName, "N");
});
