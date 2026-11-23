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
  const seen = [];
  ws.addEventListener("message", (e) => {
    const env = JSON.parse(e.data);
    const m = { type: env.type, ...env.payload };
    seen.push(m);
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
  return { ws, next, status: 101, send, has: (pred) => inbox.some(pred), seen, close: () => ws.close() };
}
const is = (t) => (m) => m.type === t;

// `holds`: the app says it holds at a synchronized start (W2), as this app's handshake does; without it, an older app.
async function host(name = "Ana", holds = false) {
  const h = await open("/rooms/create");
  assert.equal(h.status, 101);
  if (holds) h.send("client_capabilities", { supportsProtobuf: true, supportsCompression: true, clientVersion: "AuraHiRes", syncStart: true });
  h.send("create_room", { username: name });
  const created = await h.next(is("room_created"));
  return { h, created };
}
async function guest(code, name = "Beto", h, holds = false) {
  const g = await open("/rooms/join?code=" + code);
  assert.equal(g.status, 101);
  if (holds) g.send("client_capabilities", { supportsProtobuf: true, supportsCompression: true, clientVersion: "AuraHiRes", syncStart: true });
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

test("W1: the room anchors the host's position where the host captured it, clamped to the last 5 s", async () => {
  const { anchorTime } = await import("../src/index.js");
  assert.equal(anchorTime(9_700, 10_000), 9_700, "a capture 300 ms ago anchors 300 ms ago");
  assert.equal(anchorTime(1_000, 10_000), 5_000, "older than 5 s is clamped to 5 s");
  assert.equal(anchorTime(12_000, 10_000), 10_000, "a capture in the future is now");
  assert.equal(anchorTime(0, 10_000), 10_000, "an uncalibrated host (0) is now");
  assert.equal(anchorTime(undefined, 10_000), 10_000, "an app that sends nothing is now");

  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  h.send("playback_action", { action: "play", trackId: "", position: 10000, capturedAtServerTime: Date.now() - 300 });
  const play = await g.next((m) => m.type === "sync_playback" && m.action === "play");
  assert.ok(play.position >= 10300 && play.position < 11500, "the guest no longer lags by the upload latency: " + play.position);
  assert.equal(play.capturedAtServerTime, play.serverTime, "the position goes out anchored to the moment it is sent");
  h.send("playback_action", { action: "play", trackId: "", position: 10000, capturedAtServerTime: Date.now() - 60_000 });
  const old = await g.next((m) => m.type === "sync_playback" && m.action === "play");
  assert.ok(old.position >= 15000 && old.position < 16500, "a stale capture moves it 5 s at most: " + old.position);
  h.send("playback_action", { action: "play", trackId: "", position: 10000, capturedAtServerTime: Date.now() + 60_000 });
  const future = await g.next((m) => m.type === "sync_playback" && m.action === "play");
  assert.ok(future.position >= 10000 && future.position < 11000, "a capture from the future never pushes it ahead: " + future.position);
  h.send("playback_action", { action: "pause", trackId: "", position: 20000, capturedAtServerTime: Date.now() - 300 });
  assert.equal((await g.next((m) => m.type === "sync_playback" && m.action === "pause")).position, 20000, "a paused room does not move");
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const isPlay = (m) => m.type === "sync_playback" && m.action === "play";

test("W2: an older app (no hostPlaying) keeps exactly the old barrier and the old start", async () => {
  const { h, created } = await host();
  const { g, ok } = await guest(created.roomCode, "Beto", h);
  h.send("playback_action", { action: "change_track", trackId: "old00000001", position: 0, trackInfo: song("old00000001") });
  const wait = await g.next(is("buffer_wait"));
  assert.deepEqual(wait.waitingFor, [ok.userId], "only the guests are waited for");
  assert.equal("syncStart" in wait, false, "no new field on the old barrier");
  const order = g.seen.map((m) => m.type);
  assert.ok(order.lastIndexOf("sync_playback") < order.lastIndexOf("buffer_wait"), "the command still goes out first");
  g.send("request_sync");
  assert.equal((await g.next(is("sync_state"))).isPlaying, true, "an older app's change_track still means playing");
  g.send("buffer_ready", { trackId: "whatever" });
  const go = await g.next(isPlay);
  assert.equal("startAtServerTime" in go, false, "no start time for an older app");
  assert.equal(go.capturedAtServerTime, go.serverTime);
});

test("W2 + P2-5: a paused host's track change leaves the room paused and opens no barrier", async () => {
  const { h, created } = await host();
  const { g } = await guest(created.roomCode, "Beto", h);
  h.send("playback_action", { action: "change_track", trackId: "pau00000001", position: 0, trackInfo: song("pau00000001"), hostPlaying: false });
  assert.equal((await g.next((m) => m.type === "sync_playback" && m.action === "change_track")).trackId, "pau00000001");
  g.send("request_sync");
  assert.equal((await g.next(is("sync_state"))).isPlaying, false);
  await sleep(200);
  assert.equal(g.has(is("buffer_wait")), false, "nothing to wait for in a paused room");
});

test("W2: a solo host never waits", async () => {
  const { h } = await host();
  h.send("playback_action", { action: "change_track", trackId: "solo0000001", position: 0, trackInfo: song("solo0000001"), hostPlaying: true });
  h.send("request_sync");
  assert.equal((await h.next(is("sync_state"))).isPlaying, true);
  assert.equal(h.has(is("buffer_wait")), false);
});

test("W2: a new track freezes the room, waits for the host too, and starts everyone at one future server time", async () => {
  const { h, created } = await host("Ana", true);
  const { g, ok } = await guest(created.roomCode, "Beto", h, true);
  h.send("playback_action", { action: "change_track", trackId: "syn00000001", position: 0, trackInfo: song("syn00000001"), hostPlaying: true, capturedAtServerTime: Date.now() });
  const wait = await g.next(is("buffer_wait"));
  assert.equal(wait.syncStart, true);
  assert.equal(wait.startPosition, 0);
  assert.equal(wait.trackId, "syn00000001");
  assert.deepEqual([...wait.waitingFor].sort(), [created.userId, ok.userId].sort(), "the host is waited for as well");
  const hostWait = await h.next(is("buffer_wait"));
  assert.ok(hostWait.waitingFor.includes(created.userId), "the host hears it is being waited for");
  const change = await g.next((m) => m.type === "sync_playback" && m.action === "change_track");
  assert.equal(change.position, 0);
  const order = g.seen.map((m) => m.type);
  assert.ok(order.lastIndexOf("buffer_wait") < order.lastIndexOf("sync_playback"), "the barrier goes out before the track");

  await sleep(300);
  g.send("request_sync");
  const frozen = await g.next(is("sync_state"));
  assert.equal(frozen.position, 0, "the room's clock does not run while everyone waits");
  assert.equal(frozen.isPlaying, false, "someone arriving now loads paused");

  g.send("buffer_ready", { trackId: "syn00000001" });
  h.send("playback_action", { action: "play", trackId: "", position: 80, hostPlaying: true, capturedAtServerTime: Date.now() });
  await sleep(300);
  assert.equal(g.has(isPlay), false, "the host's own PLAY during the wait is not sent on, and the guest alone does not close it");
  assert.equal(g.has(is("buffer_complete")), false);

  const before = Date.now();
  h.send("buffer_ready", { trackId: "syn00000001" });
  const go = await g.next(isPlay);
  const hostGo = await h.next(isPlay);
  assert.equal(go.position, 0, "everyone starts from the start position");
  assert.ok(go.startAtServerTime >= before + 300 && go.startAtServerTime <= Date.now() + 500, "about 400 ms ahead: " + (go.startAtServerTime - before));
  assert.equal(go.startAtServerTime - go.serverTime, 400);
  assert.equal(go.capturedAtServerTime, go.startAtServerTime, "an older guest reads the same timeline from capturedAtServerTime");
  assert.equal(hostGo.startAtServerTime, go.startAtServerTime, "the host gets the very same start");
  await g.next(is("buffer_complete"));
  const after = g.seen.map((m) => m.type);
  assert.ok(after.lastIndexOf("sync_playback") < after.lastIndexOf("buffer_complete"), "the start time arrives before the barrier ends");

  g.send("request_sync");
  const starting = await g.next(is("sync_state"));
  assert.equal(starting.isPlaying, true);
  assert.equal(starting.position, 0, "before the start time the room has not moved");
  await sleep(700);
  g.send("request_sync");
  const running = await g.next(is("sync_state"));
  assert.ok(running.position >= 200 && running.position < 1500, "after it, it runs from the start time: " + running.position);
});

test("W2: a new host's jump holds everyone at the new spot", async () => {
  const { h, created } = await host("Ana", true);
  const { g } = await guest(created.roomCode, "Beto", h, true);
  h.send("playback_action", { action: "play", trackId: "", position: 5000 });
  await g.next(isPlay);
  h.send("playback_action", { action: "seek", trackId: "", position: 30000, hostPlaying: true, capturedAtServerTime: Date.now() });
  const wait = await g.next(is("buffer_wait"));
  assert.equal(wait.syncStart, true);
  assert.equal(wait.startPosition, 30000);
  assert.equal((await g.next((m) => m.type === "sync_playback" && m.action === "seek")).position, 30000);
});

test("W2: the PLAY that starts a track the room had paused on opens the barrier and is not sent on", async () => {
  const { h, created } = await host("Ana", true);
  const { g } = await guest(created.roomCode, "Beto", h, true);
  h.send("playback_action", { action: "change_track", trackId: "lst00000001", position: 0, trackInfo: song("lst00000001"), hostPlaying: false });
  await g.next((m) => m.type === "sync_playback" && m.action === "change_track");
  h.send("playback_action", { action: "play", trackId: "", position: 120, hostPlaying: true });
  const wait = await g.next(is("buffer_wait"));
  assert.equal(wait.syncStart, true);
  assert.equal(wait.startPosition, 120);
  await sleep(200);
  assert.equal(g.has(isPlay), false);
  g.send("buffer_ready", { trackId: "lst00000001" });
  h.send("buffer_ready", { trackId: "lst00000001" });
  const go = await g.next(isPlay);
  assert.equal(go.position, 120);
  assert.ok(go.startAtServerTime > 0);
});

test("W2: a pause during a synchronized start ends it; a stale answer about another track does not count", async () => {
  const { h, created } = await host("Ana", true);
  const { g } = await guest(created.roomCode, "Beto", h, true);
  h.send("playback_action", { action: "change_track", trackId: "stl00000001", position: 0, trackInfo: song("stl00000001"), hostPlaying: true });
  await g.next(is("buffer_wait"));
  g.send("buffer_ready", { trackId: "stl00000001" });
  h.send("buffer_ready", { trackId: "the0previous" });
  await sleep(200);
  assert.equal(g.has(isPlay), false, "the host's answer about the previous track does not close it");
  h.send("playback_action", { action: "pause", trackId: "", position: 0 });
  assert.equal((await g.next((m) => m.type === "sync_playback" && m.action === "pause")).position, 0);
  assert.equal((await g.next(is("buffer_complete"))).trackId, "stl00000001");
  await sleep(200);
  assert.equal(g.has(isPlay), false, "a paused room does not start");
});

test("W2: with a guest on an older app the room keeps the old barrier and its clock runs", async () => {
  const { h, created } = await host("Ana", true);
  const { g: older, ok } = await guest(created.roomCode, "Beto", h);
  const { g: newer } = await guest(created.roomCode, "Caro", h, true);
  h.send("playback_action", { action: "change_track", trackId: "mix00000001", position: 0, trackInfo: song("mix00000001"), hostPlaying: true, capturedAtServerTime: Date.now() });
  const wait = await newer.next(is("buffer_wait"));
  assert.equal("syncStart" in wait, false, "no synchronized start while someone cannot hold");
  assert.equal(wait.waitingFor.includes(created.userId), false, "the host is not waited for, as before");
  assert.ok(wait.waitingFor.includes(ok.userId));
  await sleep(300);
  older.send("request_sync");
  const running = await older.next(is("sync_state"));
  assert.equal(running.isPlaying, true);
  assert.ok(running.position >= 200, "the room's clock runs during the old barrier: " + running.position);
});

test("W2: a reconnected socket keeps saying whether its app holds", async () => {
  const { h, created } = await host("Ana", true);
  const { g, ok } = await guest(created.roomCode, "Beto", h, true);
  g.close();
  await h.next(is("user_disconnected"));
  const back = await open("/rooms/join?code=" + created.roomCode);
  back.send("client_capabilities", { supportsProtobuf: true, supportsCompression: true, clientVersion: "AuraHiRes", syncStart: true });
  back.send("reconnect", { sessionToken: ok.sessionToken });
  await back.next(is("reconnected"));
  h.send("playback_action", { action: "change_track", trackId: "rec00000001", position: 0, trackInfo: song("rec00000001"), hostPlaying: true });
  assert.equal((await back.next(is("buffer_wait"))).syncStart, true);
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
