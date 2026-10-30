// Aura Hi-Res «Escuchar juntos»: one Durable Object per room, JSON text frames, hibernating WebSockets.
// Only between Aura clients (owner 2026-10-30), so the protocol is plain JSON that keeps the message NAMES of the existing
// Listen Together protocol (create_room, join_request, sync_playback, buffer_wait…) so the app can map one onto the other.
// The room holds ids and display metadata of tracks (videoId, title, artist…), never audio and never an account.
//
// Client → server: create_room, join_room, approve_join, reject_join, leave_room, kick_user, transfer_host, playback_action,
//   buffer_ready, request_sync, reconnect, ping, chat, suggest_track, approve_suggestion, reject_suggestion.
// Server → client: room_created, join_pending, join_request, join_approved, join_rejected, user_joined, user_left, host_changed,
//   kicked, sync_playback, sync_state, buffer_wait, buffer_complete, reconnected, user_reconnected, user_disconnected,
//   suggestion_received, suggestion_approved, suggestion_rejected, chat, pong, error.

const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const MAX_USERS = 12;
const MAX_QUEUE = 200;
const MAX_FRAME = 64 * 1024;
const BUFFER_TIMEOUT_MS = 10_000;
const HOST_GRACE_MS = 60_000;
const EMPTY_ROOM_MS = 5 * 60_000;

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
const rid = (n = 8) => [...crypto.getRandomValues(new Uint8Array(n))].map((b) => ALPHABET[b % ALPHABET.length]).join("");
const send = (ws, msg) => { try { ws.send(JSON.stringify(msg)); } catch { /* closing */ } };
const clip = (v, n) => String(v ?? "").slice(0, n);

export function cleanTrack(t) {
  if (!t || typeof t !== "object" || !t.id) return null;
  return {
    id: clip(t.id, 20), title: clip(t.title, 120), artist: clip(t.artist, 120), album: clip(t.album, 120),
    duration: Math.max(0, Number(t.duration) || 0), thumbnail: clip(t.thumbnail, 300), suggestedBy: clip(t.suggestedBy, 40),
  };
}

export class Room {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    this.room = undefined;
  }

  async load() {
    if (this.room === undefined) this.room = (await this.ctx.storage.get("room")) || null;
    return this.room;
  }
  async save() {
    if (this.room) await this.ctx.storage.put("room", this.room);
    else await this.ctx.storage.delete("room");
  }

  // ── entry: the HTTP upgrade (the router already picked the room) ─────────────────────────────────────────────────────────
  async fetch(req) {
    if (req.headers.get("Upgrade") !== "websocket") return new Response("expected websocket", { status: 426 });
    const url = new URL(req.url);
    const mode = url.searchParams.get("mode");
    const code = url.searchParams.get("code") || "";
    const room = await this.load();
    if (mode === "create" && room) return json({ error: "taken" }, 409);
    if (mode === "join" && !room) return json({ error: "no such room" }, 404);
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ id: rid(8), code, mode, role: "new" });
    return new Response(null, { status: 101, webSocket: client });
  }

  // ── helpers over the live sockets ─────────────────────────────────────────────────────────────────────────────────────────
  sockets() {
    return this.ctx.getWebSockets().filter((s) => s.readyState === undefined || s.readyState === 1);
  }
  socketOf(userId) {
    return this.sockets().find((s) => (s.deserializeAttachment() || {}).id === userId && (s.deserializeAttachment() || {}).role === "member");
  }
  pendingSocketOf(userId) {
    return this.sockets().find((s) => (s.deserializeAttachment() || {}).id === userId && (s.deserializeAttachment() || {}).role === "pending");
  }
  broadcast(msg, exceptId) {
    for (const s of this.sockets()) {
      const a = s.deserializeAttachment() || {};
      if (a.role === "member" && a.id !== exceptId) send(s, msg);
    }
  }
  publicUsers() {
    return Object.entries(this.room.users).map(([id, u]) => ({ userId: id, username: u.name, isHost: id === this.room.hostId, connected: !!u.connected }));
  }
  position(now = Date.now()) {
    const r = this.room;
    return r.playing ? r.positionMs + (now - r.anchorAt) : r.positionMs;
  }
  publicState(now = Date.now()) {
    const r = this.room;
    return { track: r.track, queue: r.queue, playing: r.playing, positionMs: Math.max(0, Math.round(this.position(now))), volume: r.volume, serverTime: now };
  }

  // ── messages ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  async webSocketMessage(ws, data) {
    const text = typeof data === "string" ? data : new TextDecoder().decode(data);
    if (text.length > MAX_FRAME) return send(ws, { t: "error", code: "too_big" });
    let msg;
    try { msg = JSON.parse(text); } catch { return send(ws, { t: "error", code: "bad_json" }); }
    const a = ws.deserializeAttachment() || {};
    const t = msg && msg.t;
    await this.load();
    const handler = this["on_" + t];
    if (typeof handler !== "function" || !/^[a-z_]+$/.test(String(t))) return send(ws, { t: "error", code: "unknown_message_type" });
    try {
      await handler.call(this, ws, a, msg);
    } catch {
      send(ws, { t: "error", code: "server" });
    }
  }

  requireMember(ws, a) {
    if (!this.room || a.role !== "member" || !this.room.users[a.id]) { send(ws, { t: "error", code: "not_in_room" }); return false; }
    return true;
  }
  requireHost(ws, a) {
    if (!this.requireMember(ws, a)) return false;
    if (a.id !== this.room.hostId) { send(ws, { t: "error", code: "host_only" }); return false; }
    return true;
  }

  async on_ping(ws, a, m) { send(ws, { t: "pong", t0: Number(m.t0) || 0, serverTime: Date.now() }); }

  async on_create_room(ws, a, m) {
    if (a.mode !== "create" || this.room) return send(ws, { t: "error", code: "bad_state" });
    const token = rid(24);
    const now = Date.now();
    this.room = {
      code: a.code, hostId: a.id, createdAt: now, track: null, queue: [], playing: false, positionMs: 0, anchorAt: now, volume: 1,
      users: { [a.id]: { name: clip(m.username || "Anfitrión", 40), token, connected: true, joinedAt: now } },
      pending: {}, suggestions: {}, buffering: null, hostGraceAt: 0, dieAt: 0,
    };
    ws.serializeAttachment({ ...a, role: "member" });
    await this.save();
    send(ws, { t: "room_created", roomCode: a.code, userId: a.id, token, serverTime: now });
  }

  async on_join_room(ws, a, m) {
    if (a.mode !== "join" || !this.room) return send(ws, { t: "error", code: "no_such_room" });
    if (Object.keys(this.room.users).length + Object.keys(this.room.pending).length >= MAX_USERS) return send(ws, { t: "error", code: "room_full" });
    this.room.pending[a.id] = { name: clip(m.username || "Invitado", 40), at: Date.now() };
    ws.serializeAttachment({ ...a, role: "pending" });
    await this.save();
    send(ws, { t: "join_pending", userId: a.id });
    const host = this.socketOf(this.room.hostId);
    if (host) send(host, { t: "join_request", userId: a.id, username: this.room.pending[a.id].name });
  }

  async on_approve_join(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.userId || "");
    const p = this.room.pending[id];
    const guest = this.pendingSocketOf(id);
    if (!p || !guest) return send(ws, { t: "error", code: "no_such_request" });
    delete this.room.pending[id];
    const token = rid(24);
    this.room.users[id] = { name: p.name, token, connected: true, joinedAt: Date.now() };
    guest.serializeAttachment({ ...(guest.deserializeAttachment() || {}), role: "member" });
    this.room.dieAt = 0;
    await this.save();
    send(guest, { t: "join_approved", roomCode: this.room.code, userId: id, token, hostId: this.room.hostId, state: this.publicState(), users: this.publicUsers() });
    this.broadcast({ t: "user_joined", user: { userId: id, username: p.name, isHost: false, connected: true } }, id);
  }

  async on_reject_join(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.userId || "");
    const guest = this.pendingSocketOf(id);
    delete this.room.pending[id];
    await this.save();
    if (guest) { send(guest, { t: "join_rejected", reason: clip(m.reason || "rejected", 80) }); try { guest.close(1000, "rejected"); } catch { /* closed */ } }
  }

  async removeUser(id, why) {
    const r = this.room;
    if (!r || !r.users[id]) return;
    const wasHost = id === r.hostId;
    delete r.users[id];
    delete r.suggestions[id];
    this.broadcast({ t: "user_left", userId: id, reason: why });
    if (wasHost) await this.passHost();
    if (r.buffering) { r.buffering.waiting = r.buffering.waiting.filter((u) => u !== id); await this.maybeCompleteBuffer(); }
    if (!Object.keys(r.users).length) { this.room = null; }
  }

  /** The host is gone: the longest-present connected user takes over; nobody connected = the room ends after a while. */
  async passHost() {
    const r = this.room;
    const next = Object.entries(r.users).filter(([, u]) => u.connected).sort((x, y) => x[1].joinedAt - y[1].joinedAt)[0];
    if (!next) { r.hostId = Object.keys(r.users)[0] || ""; return; }
    r.hostId = next[0];
    r.hostGraceAt = 0;
    this.broadcast({ t: "host_changed", hostId: r.hostId, username: next[1].name });
  }

  async on_leave_room(ws, a) {
    if (a.role === "pending" && this.room) { delete this.room.pending[a.id]; await this.save(); return; }
    if (!this.requireMember(ws, a)) return;
    await this.removeUser(a.id, "left");
    ws.serializeAttachment({ ...a, role: "gone" });
    await this.save();
    try { ws.close(1000, "left"); } catch { /* closed */ }
  }

  async on_kick_user(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.userId || "");
    if (id === a.id || !this.room.users[id]) return send(ws, { t: "error", code: "no_such_user" });
    const target = this.socketOf(id);
    if (target) { send(target, { t: "kicked", reason: clip(m.reason || "kicked", 80) }); target.serializeAttachment({ ...(target.deserializeAttachment() || {}), role: "gone" }); try { target.close(1000, "kicked"); } catch { /* closed */ } }
    await this.removeUser(id, "kicked");
    await this.save();
  }

  async on_transfer_host(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.userId || "");
    if (!this.room.users[id]) return send(ws, { t: "error", code: "no_such_user" });
    this.room.hostId = id;
    await this.save();
    this.broadcast({ t: "host_changed", hostId: id, username: this.room.users[id].name });
  }

  async on_playback_action(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const r = this.room;
    const now = Date.now();
    const action = String(m.action || "");
    const out = { t: "sync_playback", action, serverTime: now };
    switch (action) {
      case "play": r.positionMs = m.positionMs != null ? Math.max(0, Number(m.positionMs) || 0) : this.position(now); r.playing = true; r.anchorAt = now; break;
      case "pause": r.positionMs = m.positionMs != null ? Math.max(0, Number(m.positionMs) || 0) : this.position(now); r.playing = false; r.anchorAt = now; break;
      case "seek": r.positionMs = Math.max(0, Number(m.positionMs) || 0); r.anchorAt = now; break;
      case "change_track": {
        const tr = cleanTrack(m.track);
        if (!tr) return send(ws, { t: "error", code: "bad_track" });
        r.track = tr; r.positionMs = Math.max(0, Number(m.positionMs) || 0); r.anchorAt = now; r.playing = m.play !== false;
        out.track = tr;
        break;
      }
      case "skip_next": case "skip_prev": break;
      case "queue_add": { const tr = cleanTrack(m.track); if (!tr || r.queue.length >= MAX_QUEUE) return send(ws, { t: "error", code: "bad_track" }); r.queue.push(tr); out.track = tr; break; }
      case "queue_remove": r.queue = r.queue.filter((x) => x.id !== String(m.trackId || "")); out.trackId = String(m.trackId || ""); break;
      case "queue_clear": r.queue = []; break;
      case "sync_queue": r.queue = (Array.isArray(m.queue) ? m.queue : []).map(cleanTrack).filter(Boolean).slice(0, MAX_QUEUE); out.queue = r.queue; break;
      case "set_volume": r.volume = Math.min(1, Math.max(0, Number(m.volume) || 0)); out.volume = r.volume; break;
      default: return send(ws, { t: "error", code: "unknown_action" });
    }
    out.positionMs = Math.max(0, Math.round(this.position(now)));
    out.playing = r.playing;
    // A new track or a jump makes everyone fetch audio: wait for the slowest (or 10 s) before playing together.
    if ((action === "change_track" || action === "seek") && r.playing) {
      const waiting = Object.entries(r.users).filter(([id, u]) => id !== a.id && u.connected).map(([id]) => id);
      if (waiting.length) {
        r.buffering = { waiting, until: now + BUFFER_TIMEOUT_MS, trackId: r.track ? r.track.id : "" };
        await this.ctx.storage.setAlarm(now + BUFFER_TIMEOUT_MS);
        out.bufferWait = true;
      }
    }
    await this.save();
    this.broadcast(out, a.id);
    if (out.bufferWait) this.broadcast({ t: "buffer_wait", trackId: r.buffering.trackId, waiting: r.buffering.waiting.length });
  }

  async maybeCompleteBuffer() {
    const r = this.room;
    if (!r || !r.buffering || r.buffering.waiting.length) return;
    r.buffering = null;
    await this.save();
    this.broadcast({ t: "buffer_complete", serverTime: Date.now(), positionMs: Math.round(this.position()), playing: r.playing });
  }

  async on_buffer_ready(ws, a) {
    if (!this.requireMember(ws, a) || !this.room.buffering) return;
    this.room.buffering.waiting = this.room.buffering.waiting.filter((id) => id !== a.id);
    await this.maybeCompleteBuffer();
    await this.save();
  }

  async on_request_sync(ws, a) {
    if (!this.requireMember(ws, a)) return;
    send(ws, { t: "sync_state", state: this.publicState(), users: this.publicUsers(), hostId: this.room.hostId });
  }

  async on_chat(ws, a, m) {
    if (!this.requireMember(ws, a)) return;
    const text = clip(m.text, 300).trim();
    if (!text) return;
    this.broadcast({ t: "chat", userId: a.id, username: this.room.users[a.id].name, text, at: Date.now() });
    send(ws, { t: "chat", userId: a.id, username: this.room.users[a.id].name, text, at: Date.now() });
  }

  async on_suggest_track(ws, a, m) {
    if (!this.requireMember(ws, a)) return;
    const tr = cleanTrack(m.track);
    if (!tr) return send(ws, { t: "error", code: "bad_track" });
    tr.suggestedBy = this.room.users[a.id].name;
    const id = rid(6);
    this.room.suggestions[id] = { by: a.id, track: tr };
    await this.save();
    const host = this.socketOf(this.room.hostId);
    if (host) send(host, { t: "suggestion_received", suggestionId: id, userId: a.id, username: tr.suggestedBy, track: tr });
  }

  async on_approve_suggestion(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.suggestionId || "");
    const s = this.room.suggestions[id];
    if (!s) return send(ws, { t: "error", code: "no_such_suggestion" });
    delete this.room.suggestions[id];
    if (this.room.queue.length < MAX_QUEUE) this.room.queue.push(s.track);
    await this.save();
    this.broadcast({ t: "suggestion_approved", suggestionId: id, track: s.track });
    send(ws, { t: "suggestion_approved", suggestionId: id, track: s.track });
  }

  async on_reject_suggestion(ws, a, m) {
    if (!this.requireHost(ws, a)) return;
    const id = String(m.suggestionId || "");
    const s = this.room.suggestions[id];
    if (!s) return;
    delete this.room.suggestions[id];
    await this.save();
    const who = this.socketOf(s.by);
    if (who) send(who, { t: "suggestion_rejected", suggestionId: id });
  }

  async on_reconnect(ws, a, m) {
    if (a.mode !== "join" || !this.room) return send(ws, { t: "error", code: "no_such_room" });
    const id = String(m.userId || "");
    const u = this.room.users[id];
    if (!u || u.token !== String(m.token || "")) return send(ws, { t: "error", code: "bad_token" });
    const old = this.socketOf(id);
    if (old) { old.serializeAttachment({ ...(old.deserializeAttachment() || {}), role: "gone" }); try { old.close(1000, "replaced"); } catch { /* closed */ } }
    ws.serializeAttachment({ ...a, id, role: "member" });
    u.connected = true;
    this.room.dieAt = 0;
    if (id === this.room.hostId) this.room.hostGraceAt = 0;
    await this.save();
    send(ws, { t: "reconnected", userId: id, hostId: this.room.hostId, state: this.publicState(), users: this.publicUsers() });
    this.broadcast({ t: "user_reconnected", userId: id }, id);
  }

  // ── sockets going away, and timed work ────────────────────────────────────────────────────────────────────────────────────
  async dropped(ws) {
    const a = ws.deserializeAttachment() || {};
    await this.load();
    if (!this.room) return;
    if (a.role === "pending") { delete this.room.pending[a.id]; await this.save(); return; }
    if (a.role !== "member" || !this.room.users[a.id]) return;
    const u = this.room.users[a.id];
    // A newer socket of the same user (a reconnect) already took over: nothing to do.
    if (this.socketOf(a.id) && this.socketOf(a.id) !== ws) return;
    u.connected = false;
    const now = Date.now();
    this.broadcast({ t: "user_disconnected", userId: a.id }, a.id);
    if (a.id === this.room.hostId) { this.room.hostGraceAt = now + HOST_GRACE_MS; await this.ctx.storage.setAlarm(now + HOST_GRACE_MS); }
    if (!Object.values(this.room.users).some((x) => x.connected)) { this.room.dieAt = now + EMPTY_ROOM_MS; await this.ctx.storage.setAlarm(now + EMPTY_ROOM_MS); }
    if (this.room.buffering) { this.room.buffering.waiting = this.room.buffering.waiting.filter((x) => x !== a.id); await this.maybeCompleteBuffer(); }
    await this.save();
  }
  async webSocketClose(ws) { await this.dropped(ws); }
  async webSocketError(ws) { await this.dropped(ws); }

  async alarm() {
    await this.load();
    const r = this.room;
    if (!r) return;
    const now = Date.now();
    if (r.buffering && now >= r.buffering.until) { r.buffering.waiting = []; await this.maybeCompleteBuffer(); }
    if (r.hostGraceAt && now >= r.hostGraceAt && !r.users[r.hostId]?.connected) { await this.passHost(); r.hostGraceAt = 0; await this.save(); }
    if (r.dieAt && now >= r.dieAt && !Object.values(r.users).some((x) => x.connected)) {
      this.room = null;
      await this.ctx.storage.deleteAll();
      return;
    }
    const nexts = [r.buffering && r.buffering.until, r.hostGraceAt, r.dieAt].filter((x) => x && x > now);
    if (nexts.length) await this.ctx.storage.setAlarm(Math.min(...nexts));
    await this.save();
  }
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === "/health") return json({ ok: true, rooms: 1 });
    const stubFor = (code) => env.ROOM.get(env.ROOM.idFromName(code));
    if (url.pathname === "/rooms/create") {
      // A free code, a handful of tries: a taken one answers 409 before any socket exists.
      for (let i = 0; i < 6; i++) {
        const code = rid(6);
        const res = await stubFor(code).fetch(new Request(`https://room/?mode=create&code=${code}`, req));
        if (res.status !== 409) return res;
      }
      return json({ error: "busy" }, 503);
    }
    if (url.pathname === "/rooms/join") {
      const code = (url.searchParams.get("code") || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      if (code.length !== 6) return json({ error: "bad code" }, 400);
      return stubFor(code).fetch(new Request(`https://room/?mode=join&code=${code}`, req));
    }
    return json({ error: "not found" }, 404);
  },
};
