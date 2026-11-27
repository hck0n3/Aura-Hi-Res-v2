import { mergeShuffle, MAX_CONTEXTS } from "./merge.js";

/**
 * Aura Hi-Res — device sync Worker.
 *
 * One Durable Object ("room") per sync group. The group id is the SHA-256 (hex) of a secret that only the user's devices know,
 * so the id itself is the capability: nobody who does not hold it can reach the room, and the Worker never sees the secret.
 * Devices connect with a WebSocket, say hello, and from then on:
 *   - shuffle progress (which songs of a shuffled list were already played) is merged and fanned out;
 *   - the playing device publishes what it plays ("now"); the others can see it and send commands to it (play, pause, next,
 *     transfer…), which the room only routes;
 *   - «library changed» pings make the other devices refresh at once;
 *   - library registers (likes, dislikes, playlists) are merged last-writer-wins and handed out «since N» (row 536).
 * Nothing about the songs themselves is stored but their ids. Hibernating WebSockets: an idle room costs nothing.
 */

const MAX_MESSAGE_BYTES = 128 * 1024;
const MAX_NOW_BYTES = 24 * 1024;
const MAX_DEVICES = 8;
// «Now playing» is kept PER DEVICE (rows 745/662): with one shared value, a device that published its paused queue hid the device that
// really plays until its next heartbeat (≤ 20 s). `hello_ok.nows` hands out the entries of the last NOW_RECENT_MS; `hello_ok.now` stays
// the single best one for apps that only know that field.
const NOW_RECENT_MS = 10 * 60_000; // the window the task named for «recent»; chosen, not measured
const NOW_FRESH_MS = 70_000; // a playing device heartbeats every 20 s: the app's RemotePlayback.FRESH_MS
const MAX_NOW_ENTRIES = 16;
// Library registers (row 536): likes, dislikes and playlists, one last-writer-wins register per item.
// Bumped whenever the library protocol changes. The app asks for it in `hello_ok` and says so when the deployed Worker is older.
// 3 (row 900): ops may carry an id and `lib_ack.ids` acknowledges each one; apps that check `lib >= 2` keep working.
const LIB_VERSION = 3;
const LIB_PAGE = 400; // items per `lib_state` page
const LIB_PUSH_MAX = 400; // ops per `lib_push`
const LIB_CHUNK = 50; // ops per storage write batch (each accepted op writes 2 keys; storage.put takes ≤128)
const LIB_MAX_KEY = 120;
const LIB_MAX_VALUE = 800;
const LIB_MAX_KEYS = 150000;
const LIB_MAX_ID = 40;
// Row 901 — cleanup: a removal ("0") that every device of the room has pulled past is forgotten, so the room does not grow for ever.
// A device «has pulled past» seq N when it asks `lib_pull since >= N` (its own acknowledgement of the log). The numbers are chosen,
// not measured.
const PRUNE_DEVICE_RETENTION_MS = 180 * 24 * 3600_000; // a device unseen this long no longer holds the cleanup back
const PRUNE_MIN_AGE_MS = 30 * 24 * 3600_000; // a removal younger than this is kept whatever the devices say
const PRUNE_EVERY_MS = 12 * 3600_000; // at most one cleanup pass per room every 12 h, and only when a pull moved a device on
const PRUNE_SCAN = 2000; // log entries read by one pass
const PRUNE_FAMILIES = /^(lk|dk|pl|pm|ab|pn):/; // the removals the app sends; nothing else is ever forgotten (`hv:` history is kept)
const DEVICE_NOTE_EVERY_MS = 3600_000; // a device's «pulled up to» is rewritten when it moves, or once an hour
const pad = (n) => String(n).padStart(12, "0");

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    // `lib` tells the app (and the owner, in a browser) whether this deployment understands the library registers (row 536).
    if (url.pathname === "/health") return json({ ok: true, lib: LIB_VERSION });
    if (url.pathname !== "/sync") return json({ error: "not_found" }, 404);
    const group = url.searchParams.get("g") || "";
    if (!/^[a-f0-9]{64}$/.test(group)) return json({ error: "bad_group" }, 400);
    if (request.headers.get("Upgrade") !== "websocket") return json({ error: "websocket_required" }, 426);
    const stub = env.SYNC_ROOM.get(env.SYNC_ROOM.idFromName(group));
    return stub.fetch(request);
  },
};

export class SyncRoom {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
  }

  async fetch(request) {
    if (this.ctx.getWebSockets().length >= MAX_DEVICES + 2) return json({ error: "room_full" }, 429);
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ id: null, name: "", kind: "", joinedAt: Date.now() });
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, message) {
    if (typeof message !== "string") return;
    if (message.length > MAX_MESSAGE_BYTES) {
      ws.close(1009, "message too big");
      return;
    }
    let msg;
    try {
      msg = JSON.parse(message);
    } catch {
      return;
    }
    const me = ws.deserializeAttachment() || {};
    switch (msg.t) {
      case "ping":
        send(ws, { t: "pong", time: Date.now() });
        return;
      case "hello":
        return this.hello(ws, msg);
      case "shuffle":
        if (!me.id) return;
        return this.shuffle(ws, me, msg);
      case "shuffle_get":
        if (!me.id) return;
        return this.shuffleGet(ws);
      case "now":
        if (!me.id) return;
        return this.now(ws, me, msg);
      case "cmd":
        if (!me.id) return;
        return this.command(ws, me, msg);
      case "lib_push":
        if (!me.id) return;
        return this.libPush(ws, msg);
      case "lib_pull":
        if (!me.id) return;
        return this.libPull(ws, msg, me);
      case "library":
        if (!me.id) return;
        this.broadcast({ t: "library", from: me.id, what: String(msg.what || "all").slice(0, 24) }, ws);
        return;
      default:
        return;
    }
  }

  async webSocketClose(ws) {
    this.broadcastDevices(ws);
  }

  async webSocketError(ws) {
    this.broadcastDevices(ws);
  }

  async hello(ws, msg) {
    const id = String(msg.device?.id || "").slice(0, 64);
    if (!/^[A-Za-z0-9_-]{8,64}$/.test(id)) {
      ws.close(1008, "bad device");
      return;
    }
    // One live socket per device: a reconnect replaces its own stale one.
    for (const other of this.ctx.getWebSockets()) {
      if (other !== ws && (other.deserializeAttachment() || {}).id === id) {
        // A reconnecting device leaves its old socket «closing» for a while (a dead peer answers the close late, or never). Until
        // then it still sat in getWebSockets(): orders for this device (pause, take over…) found IT first and went nowhere, and the
        // device list counted it twice (owner 2026-10-30: «continuar no detiene la canción en el otro dispositivo», «dice tres, luego
        // dos»). Strip its identity FIRST so every lookup ignores it, then close it.
        try {
          other.serializeAttachment({ id: null, name: "", kind: "", joinedAt: 0 });
        } catch {}
        try {
          other.close(1000, "replaced");
        } catch {}
      }
    }
    ws.serializeAttachment({
      id,
      name: String(msg.device?.name || "Dispositivo").slice(0, 40),
      kind: String(msg.device?.kind || "phone").slice(0, 12),
      joinedAt: Date.now(),
    });
    const nows = await this.loadNows();
    const serverTime = Date.now();
    const recent = Object.values(nows).filter((n) => !n.stale && serverTime - (n.at || 0) <= NOW_RECENT_MS);
    send(ws, { t: "hello_ok", serverTime, devices: this.devices(), now: pickNow(nows, serverTime), nows: recent, lib: LIB_VERSION });
    this.broadcastDevices(null, ws);
  }

  async shuffle(ws, me, msg) {
    const ctxId = String(msg.ctx || "").slice(0, 96);
    if (!ctxId) return;
    const key = "sh:" + ctxId;
    const stored = (await this.ctx.storage.get(key)) || null;
    const result = mergeShuffle(stored, { epoch: msg.epoch, add: msg.add, last: msg.last });
    if (result.stale) {
      send(ws, { t: "shuffle_full", items: [{ ctx: ctxId, ...result.state }] });
      return;
    }
    if (result.changed) {
      await this.ctx.storage.put(key, { ...result.state, updatedAt: Date.now() });
      await this.trimContexts();
      this.broadcast({ t: "shuffle", ctx: ctxId, from: me.id, ...result.delta }, ws);
    }
    send(ws, { t: "shuffle_ack", ctx: ctxId, epoch: result.state.epoch, count: result.state.ids.length });
  }

  async shuffleGet(ws) {
    const all = await this.ctx.storage.list({ prefix: "sh:" });
    const items = [];
    let budget = 20000;
    const rows = [...all.entries()].sort((a, b) => (b[1].updatedAt || 0) - (a[1].updatedAt || 0));
    for (const [key, value] of rows) {
      if (budget <= 0) break;
      const ids = value.ids.slice(-budget);
      budget -= ids.length;
      items.push({ ctx: key.slice(3), epoch: value.epoch, ids, last: value.last });
    }
    send(ws, { t: "shuffle_full", items });
  }

  async now(ws, me, msg) {
    const state = msg.state;
    if (!state || typeof state !== "object") return;
    const text = JSON.stringify(state);
    if (text.length > MAX_NOW_BYTES) return;
    const at = Date.now();
    const nows = await this.loadNows();
    const playing = state.playing === true;
    const playedAt = playing ? at : nows[me.id]?.playedAt || 0;
    // A paused report from a device that has not played lately, while another device plays, is not what the user listens to (a queue
    // restored or mirrored there, row 662). It is kept for that device but neither handed out nor broadcast: it cannot hide the one that plays.
    const stale = !playing && at - playedAt > NOW_RECENT_MS && Object.values(nows).some((n) => n.deviceId !== me.id && freshPlaying(n, at));
    nows[me.id] = { ...state, deviceId: me.id, deviceName: me.name, at, playedAt, stale };
    // One key per device («now:<id>», each under MAX_NOW_BYTES): one write per report, as with the single «now».
    await this.ctx.storage.put("now:" + me.id, nows[me.id]);
    const old = Object.values(nows).sort((a, b) => (b.at || 0) - (a.at || 0)).slice(MAX_NOW_ENTRIES);
    if (old.length) await this.ctx.storage.delete(old.map((n) => "now:" + n.deviceId));
    if (!stale) this.broadcast({ t: "now", from: me.id, state: nows[me.id] }, ws);
  }

  /** deviceId → its last «now». A room written by the previous version has only the single `now`: it becomes the first entry. */
  async loadNows() {
    const nows = {};
    for (const [key, value] of await this.ctx.storage.list({ prefix: "now:" })) nows[key.slice(4)] = value;
    if (Object.keys(nows).length) return nows;
    const legacy = await this.ctx.storage.get("now");
    return legacy?.deviceId ? { [legacy.deviceId]: { ...legacy, playedAt: legacy.playing === true ? legacy.at || 0 : 0 } } : {};
  }

  /**
   * Library registers. Two key families in the room's storage:
   *   k:<key>        → {ts, v, seq}   the current value of one item (last writer wins; a tie goes to the larger value so every
   *                                   replica settles on the same one)
   *   s:<seq padded> → [key, ts, v]   the change log, one entry per item (the entry of an item's previous value is deleted when it
   *                                   changes), so «everything that changed since N» is one ordered range read
   * `libseq` is the last sequence number handed out. Nothing but ids, the names of playlists and a song's title/artist/cover (to be
   * able to show a song the other device has never seen) is stored.
   */
  async libPush(ws, msg) {
    const raw = Array.isArray(msg.ops) ? msg.ops.slice(0, LIB_PUSH_MAX) : [];
    const ops = [];
    // Row 900: an op may carry its id as a 4th element (apps from lib 3 on). `ids` in the ack lists every op this room is done
    // with — applied, older than what it holds, or malformed — so the app clears exactly those and sends the rest again.
    // An app that sends no ids (older apps) gets the same ack as before and ignores `ids`.
    const ids = [];
    for (const op of raw) {
      if (!Array.isArray(op) || op.length < 3) continue;
      const id = typeof op[3] === "string" && op[3].length > 0 && op[3].length <= LIB_MAX_ID ? op[3] : null;
      const key = String(op[0]);
      const ts = Number(op[1]);
      const value = String(op[2]);
      if (!key || key.length > LIB_MAX_KEY || value.length > LIB_MAX_VALUE || !Number.isFinite(ts) || ts < 0) {
        if (id) ids.push(id);
        continue;
      }
      ops.push([key, ts, value, id]);
    }
    let seq = (await this.ctx.storage.get("libseq")) || 0;
    let count = (await this.ctx.storage.get("libcount")) || 0;
    const accepted = [];
    for (let i = 0; i < ops.length; i += LIB_CHUNK) {
      const chunk = ops.slice(i, i + LIB_CHUNK);
      const existing = await this.ctx.storage.get(chunk.map(([k]) => "k:" + k));
      const puts = {};
      const dels = [];
      for (const [key, ts, value, id] of chunk) {
        const cur = existing.get("k:" + key);
        if (cur && !(ts > cur.ts || (ts === cur.ts && value > cur.v))) {
          // Older than (or the same as) what the room holds: done, the same op twice is applied once.
          if (id) ids.push(id);
          continue;
        }
        // A full room is NOT done with it: no id, so the app keeps it and tries again once there is room.
        if (!cur && count >= LIB_MAX_KEYS) continue;
        if (id) ids.push(id);
        seq++;
        if (cur) dels.push("s:" + pad(cur.seq));
        else count++;
        const entry = { ts, v: value, seq };
        puts["k:" + key] = entry;
        puts["s:" + pad(seq)] = [key, ts, value];
        existing.set("k:" + key, entry);
        accepted.push([key, ts, value]);
      }
      if (Object.keys(puts).length) await this.ctx.storage.put(puts);
      if (dels.length) await this.ctx.storage.delete(dels);
    }
    if (accepted.length) await this.ctx.storage.put({ libseq: seq, libcount: count });
    send(ws, { t: "lib_ack", seq, accepted: accepted.length, received: ops.length, ids });
    // The others only need the signal: they pull from where they left off (idempotent, and never misses a gap).
    if (accepted.length) this.broadcast({ t: "lib_ops", seq }, ws);
  }

  async libPull(ws, msg, me) {
    const head = (await this.ctx.storage.get("libseq")) || 0;
    let since = Math.max(0, Math.floor(Number(msg.since) || 0));
    // A client that is AHEAD of this room (the room was emptied or recreated) starts over and re-sends what it has.
    const reset = since > head;
    if (reset) since = 0;
    // Row 901: what this device has acknowledged; the cleanup never forgets what a device has not pulled yet.
    if (me?.id && (await this.noteDevice(me.id, since, Date.now()))) await this.libPrune(Date.now());
    const page = await this.ctx.storage.list({ prefix: "s:", start: "s:" + pad(since + 1), limit: LIB_PAGE });
    const items = [];
    let last = since;
    for (const [k, v] of page) {
      items.push(v);
      last = Number(k.slice(2));
    }
    send(ws, { t: "lib_state", items, seq: last, head, more: items.length === LIB_PAGE && last < head, reset });
  }

  /** Row 901: remembers up to where device [id] has pulled. True when that moved (or an hour passed): a cleanup may follow. */
  async noteDevice(id, since, now) {
    const key = "ld:" + id;
    const seen = await this.ctx.storage.get(key);
    if (seen && seen.since === since && now - (seen.at || 0) < DEVICE_NOTE_EVERY_MS) return false;
    await this.ctx.storage.put(key, { since, at: now });
    return true;
  }

  /**
   * Row 901 — forgets the removals every device has pulled past. The floor is the lowest «pulled up to» among the devices seen in
   * the last PRUNE_DEVICE_RETENTION_MS (one that has not come back in that long no longer holds it back; it never loses anything
   * it has: a removal it missed simply never reaches it). Only a removal older than PRUNE_MIN_AGE_MS goes, with the position of the
   * song it took out of a list (`po:`); additions, names, history and everything that is not a removal stay. The log is walked
   * once, from a cursor, at most PRUNE_SCAN entries per pass, and the pass stops at the first removal still too young (it is looked
   * at again in a later pass). Returns how many registers were forgotten.
   */
  async libPrune(now) {
    const storage = this.ctx.storage;
    if (now - ((await storage.get("libprunedAt")) || 0) < PRUNE_EVERY_MS) return 0;
    let floor = Infinity;
    const gone = [];
    for (const [key, seen] of await storage.list({ prefix: "ld:" })) {
      if (now - (seen.at || 0) > PRUNE_DEVICE_RETENTION_MS) gone.push(key);
      else floor = Math.min(floor, seen.since || 0);
    }
    if (gone.length) await storage.delete(gone);
    const cursor = (await storage.get("libprunecur")) || 0;
    if (!Number.isFinite(floor) || floor <= cursor) return 0;
    const page = await storage.list({ prefix: "s:", start: "s:" + pad(cursor + 1), end: "s:" + pad(floor + 1), limit: PRUNE_SCAN });
    const dels = [];
    let reached = cursor;
    let young = false;
    for (const [logKey, entry] of page) {
      const [key, ts, value] = Array.isArray(entry) ? entry : [];
      if (value === "0" && typeof key === "string" && PRUNE_FAMILIES.test(key)) {
        if (now - Number(ts) < PRUNE_MIN_AGE_MS) {
          young = true;
          break;
        }
        dels.push(logKey, "k:" + key);
        if (key.startsWith("pm:")) {
          const position = await storage.get("k:po:" + key.slice(3));
          if (position) dels.push("k:po:" + key.slice(3), "s:" + pad(position.seq));
        }
      }
      reached = Number(logKey.slice(2));
    }
    if (!young && page.size < PRUNE_SCAN) reached = floor;
    for (let i = 0; i < dels.length; i += 128) await storage.delete(dels.slice(i, i + 128));
    const removed = dels.filter((k) => k.startsWith("k:")).length;
    const count = (await storage.get("libcount")) || 0;
    await storage.put({ libcount: Math.max(0, count - removed), libprunecur: reached, libprunedAt: now });
    return removed;
  }

  command(ws, me, msg) {
    const target = String(msg.to || "");
    const cmd = String(msg.cmd || "").slice(0, 24);
    if (!target || !cmd) return;
    // The newest OPEN socket of the target (never a stale one that is still closing).
    let dest = null;
    let destSince = -1;
    for (const s of this.ctx.getWebSockets()) {
      if (s.readyState !== undefined && s.readyState !== 1) continue;
      const a = s.deserializeAttachment() || {};
      if (a.id === target && (a.joinedAt || 0) >= destSince) {
        dest = s;
        destSince = a.joinedAt || 0;
      }
    }
    if (!dest) {
      send(ws, { t: "cmd_fail", to: target, cmd });
      return;
    }
    send(dest, { t: "cmd", from: me.id, fromName: me.name, cmd, args: msg.args && typeof msg.args === "object" ? msg.args : {} });
    // The sender learns the order reached the device's open connection (an older app ignores this message).
    send(ws, { t: "cmd_ok", to: target, cmd });
  }

  /**
   * The devices in the room, ONE per device id. A device that reconnects leaves its old socket «closing» for a moment next to the
   * new one; listing both made the count flip between N and N+1 (owner 2026-10-30: «dice tres, de la nada dos, y así alternando»).
   * A socket that is not open any more is not listed, and when two sockets carry the same id the newest one wins.
   */
  devices(exclude) {
    const byId = new Map();
    for (const s of this.ctx.getWebSockets()) {
      if (s === exclude) continue;
      if (s.readyState !== undefined && s.readyState !== 1) continue; // 1 = OPEN
      const d = s.deserializeAttachment() || {};
      if (!d.id) continue;
      const seen = byId.get(d.id);
      if (!seen || (d.joinedAt || 0) >= (seen.since || 0)) byId.set(d.id, { id: d.id, name: d.name, kind: d.kind, since: d.joinedAt });
    }
    return [...byId.values()];
  }

  /** Tell everyone who is in the room. [closing] is a socket on its way out (not listed); [skip] is not written to. */
  broadcastDevices(closing, skip) {
    this.broadcast({ t: "devices", devices: this.devices(closing) }, skip ?? closing);
  }

  broadcast(payload, except) {
    const text = JSON.stringify(payload);
    for (const s of this.ctx.getWebSockets()) {
      if (s === except) continue;
      if (!(s.deserializeAttachment() || {}).id) continue;
      try {
        s.send(text);
      } catch {}
    }
  }

  async trimContexts() {
    const all = await this.ctx.storage.list({ prefix: "sh:" });
    if (all.size <= MAX_CONTEXTS) return;
    const oldest = [...all.entries()].sort((a, b) => (a[1].updatedAt || 0) - (b[1].updatedAt || 0)).slice(0, all.size - MAX_CONTEXTS);
    await this.ctx.storage.delete(oldest.map(([k]) => k));
  }
}

function freshPlaying(n, at) {
  return !n.stale && n.playing === true && at - (n.at || 0) <= NOW_FRESH_MS;
}

/** The one «now» for an app that knows a single one: the newest device that plays, else the newest report. */
function pickNow(nows, at) {
  const all = Object.values(nows).filter((n) => !n.stale);
  const newest = (list) => list.reduce((best, n) => (!best || (n.at || 0) > (best.at || 0) ? n : best), null);
  return newest(all.filter((n) => freshPlaying(n, at))) || newest(all);
}

function send(ws, payload) {
  try {
    ws.send(JSON.stringify(payload));
  } catch {}
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
