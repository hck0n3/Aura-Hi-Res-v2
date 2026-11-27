// Aura Hi-Res «Escuchar juntos»: one Durable Object per room, JSON text frames, hibernating WebSockets.
// Only between Aura clients (owner 2026-10-30). The wire is `{type, payload}` where `payload` has EXACTLY the field names of the
// app's own protocol classes (app/.../listentogether/Protocol.kt: CreateRoomPayload, PlaybackActionPayload, RoomState…), so the app
// only swaps its binary codec for JSON. Inside the room the logic keeps short internal names; `fromWire` / `toWire` are the only
// places that know the two spellings.
// The room holds ids and display metadata of tracks (videoId, title, artist…), never audio and never an account.

const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const MAX_USERS = 12;
const MAX_QUEUE = 200;
const MAX_FRAME = 64 * 1024;
const BUFFER_TIMEOUT_MS = 10_000;
/** How old a host's capturedAtServerTime may be and still anchor the room (W1); older, or in the future, is clamped. */
const CAPTURE_MAX_AGE_MS = 5_000;
/** How far ahead of the barrier's close everyone is told to start (W2): time for the `play` to reach every device. */
const SYNC_START_LEAD_MS = 400;
const HOST_GRACE_MS = 60_000;
const EMPTY_ROOM_MS = 5 * 60_000;

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
const rid = (n = 8) => [...crypto.getRandomValues(new Uint8Array(n))].map((b) => ALPHABET[b % ALPHABET.length]).join("");
const send = (ws, msg) => { try { ws.send(JSON.stringify(toWire(msg))); } catch { /* closing */ } };
const clip = (v, n) => String(v ?? "").slice(0, n);

const ERROR_TEXT = {
  no_such_room: "Esa sala no existe o ya terminó", room_full: "La sala está llena", host_only: "Solo el anfitrión puede hacer eso",
  not_in_room: "No estás en la sala", bad_token: "Tu sesión de la sala ya no es válida", bad_track: "Esa canción no es válida",
  too_big: "Mensaje demasiado grande", bad_json: "Mensaje ilegible", unknown_message_type: "Mensaje desconocido", bad_state: "No se puede hacer eso ahora",
  no_such_user: "Esa persona ya no está", no_such_request: "Esa solicitud ya no existe", no_such_suggestion: "Esa sugerencia ya no existe",
  unknown_action: "Acción desconocida", server: "Error del servidor",
};

/** App spelling → internal message. `p` is the payload object; unknown/absent fields fall back to harmless defaults. */
export function fromWire(type, p) {
  p = p && typeof p === "object" ? p : {};
  switch (type) {
    case "create_room": case "join_room": return { t: type, username: p.username };
    case "approve_join": return { t: type, userId: p.userId };
    case "reject_join": case "kick_user": return { t: type, userId: p.userId, reason: p.reason };
    case "transfer_host": return { t: type, userId: p.newHostId };
    case "playback_action": return { t: type, action: p.action, trackId: p.trackId, positionMs: p.position, track: p.trackInfo, queue: p.queue, insertNext: !!p.insertNext, volume: p.volume, capturedAt: p.capturedAtServerTime, hostPlaying: p.hostPlaying };
    case "buffer_ready": return { t: type, trackId: p.trackId };
    case "ping": return { t: type, clientTime: p.clientTime, sequence: p.sequence };
    case "suggest_track": return { t: type, track: p.trackInfo };
    case "approve_suggestion": return { t: type, suggestionId: p.suggestionId };
    case "reject_suggestion": return { t: type, suggestionId: p.suggestionId, reason: p.reason };
    case "reconnect": { const [userId, ...rest] = String(p.sessionToken || "").split("."); return { t: type, userId, token: rest.join(".") }; }
    case "client_capabilities": return { t: type, syncStart: p.syncStart === true };
    default: return { ...p, t: type };
  }
}

/** Internal message → app spelling `{type, payload}`. */
export function toWire(m) {
  const now = Date.now();
  switch (m.t) {
    case "server_capabilities": return { type: m.t, payload: { supportsProtobuf: false, supportsCompression: false, serverVersion: "aura-rooms-1" } };
    case "room_created": return { type: m.t, payload: { roomCode: m.roomCode, userId: m.userId, sessionToken: m.sessionToken } };
    case "join_request": case "user_joined": case "user_left": case "user_reconnected": case "user_disconnected":
      return { type: m.t, payload: { userId: m.userId, username: m.username || "" } };
    case "join_approved": return { type: m.t, payload: { roomCode: m.roomCode, userId: m.userId, sessionToken: m.sessionToken, state: m.state } };
    case "join_rejected": return { type: m.t, payload: { reason: m.reason } };
    case "host_changed": return { type: m.t, payload: { newHostId: m.hostId, newHostName: m.username || "" } };
    case "kicked": return { type: m.t, payload: { reason: m.reason } };
    case "buffer_wait": return { type: m.t, payload: { trackId: m.trackId || "", waitingFor: m.waitingFor || [], ...(m.syncStart ? { syncStart: true, startPosition: m.startPosition || 0 } : {}) } };
    case "buffer_complete": return { type: m.t, payload: { trackId: m.trackId || "" } };
    case "error": return { type: m.t, payload: { code: m.code, message: ERROR_TEXT[m.code] || m.code } };
    case "pong": return { type: m.t, payload: { clientTime: m.clientTime, serverReceiveTime: m.receivedAt, serverSendTime: now, sequence: m.sequence } };
    case "reconnected": return { type: m.t, payload: { roomCode: m.state.roomCode, userId: m.userId, state: m.state, isHost: m.userId === m.state.hostId } };
    case "sync_state": return { type: m.t, payload: { currentTrack: m.state.currentTrack, isPlaying: m.state.isPlaying, position: m.state.position, lastUpdate: m.state.lastUpdate, queue: m.state.queue, volume: m.state.volume, revision: m.state.revision } };
    case "sync_playback": return { type: m.t, payload: {
      action: m.action, trackId: m.trackId || (m.track && m.track.id) || "", position: m.positionMs || 0, trackInfo: m.track || null,
      insertNext: !!m.insertNext, queue: m.queue || [], queueTitle: "", volume: m.volume ?? 0, serverTime: m.serverTime || now, revision: m.revision || 0,
      capturedAtServerTime: m.capturedAt || m.serverTime || now,
      ...(m.startAt ? { startAtServerTime: m.startAt } : {}),
    } };
    case "suggestion_received": return { type: m.t, payload: { suggestionId: m.suggestionId, fromUserId: m.userId, fromUsername: m.username, trackInfo: m.track } };
    case "suggestion_approved": return { type: m.t, payload: { suggestionId: m.suggestionId, trackInfo: m.track } };
    case "suggestion_rejected": return { type: m.t, payload: { suggestionId: m.suggestionId, reason: m.reason || "" } };
    default: return { type: m.t, payload: m };
  }
}

/**
 * W1: the server time the room's position is anchored to — when the host CAPTURED the command (its capturedAtServerTime, on this
 * server's clock through the app's ServerClock), not when it arrived here; anchoring at arrival left every guest behind by the host's
 * upload latency. Clamped to the last CAPTURE_MAX_AGE_MS and never later than now; 0 or absent (uncalibrated clock) = now.
 */
export function anchorTime(capturedAt, now) {
  const c = Number(capturedAt) || 0;
  return c > 0 ? Math.min(now, Math.max(now - CAPTURE_MAX_AGE_MS, c)) : now;
}

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
    // The app's handshake expects the server's capabilities first; this server speaks JSON only, no compression.
    send(server, { t: "server_capabilities" });
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
    return Object.entries(this.room.users).map(([id, u]) => ({ userId: id, username: u.name, isHost: id === this.room.hostId, isConnected: !!u.connected }));
  }
  /** Frozen while everyone waits at a synchronized start; before that start's time it has not moved yet. */
  position(now = Date.now()) {
    const r = this.room;
    if (!r.playing || (r.buffering && r.buffering.sync)) return r.positionMs;
    return r.positionMs + Math.max(0, now - r.anchorAt);
  }
  publicState(now = Date.now()) {
    const r = this.room;
    return {
      // Someone arriving during a synchronized start loads paused and starts with everyone on the play that closes it.
      roomCode: r.code, hostId: r.hostId, users: this.publicUsers(), currentTrack: r.track, isPlaying: r.playing && !(r.buffering && r.buffering.sync),
      position: Math.max(0, Math.round(this.position(now))), lastUpdate: now, volume: r.volume, queue: r.queue, revision: r.revision || 0,
    };
  }

  // ── messages ──────────────────────────────────────────────────────────────────────────────────────────────────────────────
  async webSocketMessage(ws, data) {
    const text = typeof data === "string" ? data : new TextDecoder().decode(data);
    if (text.length > MAX_FRAME) return send(ws, { t: "error", code: "too_big" });
    const receivedAt = Date.now();
    let env;
    try { env = JSON.parse(text); } catch { return send(ws, { t: "error", code: "bad_json" }); }
    const a = ws.deserializeAttachment() || {};
    const msg = fromWire(String((env && env.type) || ""), env && env.payload);
    msg.receivedAt = receivedAt;
    const t = msg.t;
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

  /** Nothing to negotiate but one thing to note, per socket: whether this app holds at a synchronized start (W2). */
  async on_client_capabilities(ws, a, m) { ws.serializeAttachment({ ...a, syncStart: !!m.syncStart }); }

  async on_ping(ws, a, m) { send(ws, { t: "pong", clientTime: Number(m.clientTime) || 0, receivedAt: m.receivedAt, sequence: Number(m.sequence) || 0 }); }

  async on_create_room(ws, a, m) {
    if (a.mode !== "create" || this.room) return send(ws, { t: "error", code: "bad_state" });
    const token = rid(24);
    const now = Date.now();
    this.room = {
      code: a.code, hostId: a.id, createdAt: now, track: null, queue: [], playing: false, positionMs: 0, anchorAt: now, volume: 1, revision: 0,
      users: { [a.id]: { name: clip(m.username || "Anfitrión", 40), token, connected: true, joinedAt: now } },
      pending: {}, suggestions: {}, buffering: null, hostGraceAt: 0, dieAt: 0,
    };
    ws.serializeAttachment({ ...a, role: "member" });
    await this.save();
    send(ws, { t: "room_created", roomCode: a.code, userId: a.id, sessionToken: `${a.id}.${token}` });
  }

  async on_join_room(ws, a, m) {
    if (a.mode !== "join" || !this.room) return send(ws, { t: "error", code: "no_such_room" });
    if (Object.keys(this.room.users).length + Object.keys(this.room.pending).length >= MAX_USERS) return send(ws, { t: "error", code: "room_full" });
    this.room.pending[a.id] = { name: clip(m.username || "Invitado", 40), at: Date.now() };
    ws.serializeAttachment({ ...a, role: "pending" });
    await this.save();
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
    send(guest, { t: "join_approved", roomCode: this.room.code, userId: id, sessionToken: `${id}.${token}`, state: this.publicState() });
    this.broadcast({ t: "user_joined", userId: id, username: p.name }, id);
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
    const name = r.users[id].name;
    delete r.users[id];
    delete r.suggestions[id];
    this.broadcast({ t: "user_left", userId: id, username: name, reason: why });
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
    // A position the host gave is anchored where it captured it (W1); one the room computes itself is anchored now.
    const at = m.positionMs != null ? anchorTime(m.capturedAt, now) : now;
    // An app that says whether its host plays (hostPlaying) also waits at a synchronized-start barrier (W2); an older app says nothing
    // and keeps the room exactly as before.
    const syncHost = typeof m.hostPlaying === "boolean";
    const holding = !!(r.buffering && r.buffering.sync);
    // The host's own PLAY while everyone waits at the start: the room starts when the barrier closes, not now.
    if (action === "play" && holding) { r.playing = true; await this.save(); return; }
    const wasPlaying = r.playing;
    const previousTrackId = r.track ? r.track.id : "";
    r.revision = (r.revision || 0) + 1;
    const out = { t: "sync_playback", action, serverTime: now, revision: r.revision };
    switch (action) {
      case "play": r.positionMs = m.positionMs != null ? Math.max(0, Number(m.positionMs) || 0) : this.position(now); r.playing = true; r.anchorAt = at; break;
      case "pause": r.positionMs = m.positionMs != null ? Math.max(0, Number(m.positionMs) || 0) : this.position(now); r.playing = false; r.anchorAt = at; break;
      case "seek": r.positionMs = Math.max(0, Number(m.positionMs) || 0); r.anchorAt = anchorTime(m.capturedAt, now); if (syncHost) r.playing = m.hostPlaying; break;
      case "change_track": {
        const tr = cleanTrack(m.track);
        if (!tr) return send(ws, { t: "error", code: "bad_track" });
        // P2-5: playing only if the host is (an older app sends no flag: playing, as before).
        r.track = tr; r.positionMs = Math.max(0, Number(m.positionMs) || 0); r.anchorAt = anchorTime(m.capturedAt, now); r.playing = syncHost ? m.hostPlaying : true;
        // The queue travels on the SAME message as the track (the app relies on it): keep it and echo it.
        if (Array.isArray(m.queue) && m.queue.length) r.queue = m.queue.map(cleanTrack).filter(Boolean).slice(0, MAX_QUEUE);
        out.track = tr; out.queue = r.queue;
        break;
      }
      case "skip_next": case "skip_prev": break;
      case "queue_add": { const tr = cleanTrack(m.track); if (!tr || r.queue.length >= MAX_QUEUE) return send(ws, { t: "error", code: "bad_track" }); r.queue.push(tr); out.track = tr; out.insertNext = !!m.insertNext; break; }
      case "queue_remove": r.queue = r.queue.filter((x) => x.id !== String(m.trackId || "")); out.trackId = String(m.trackId || ""); break;
      case "queue_clear": r.queue = []; break;
      case "sync_queue": r.queue = (Array.isArray(m.queue) ? m.queue : []).map(cleanTrack).filter(Boolean).slice(0, MAX_QUEUE); out.queue = r.queue; break;
      case "set_volume": r.volume = Math.min(1, Math.max(0, Number(m.volume) || 0)); out.volume = r.volume; break;
      default: return send(ws, { t: "error", code: "unknown_action" });
    }
    // W2: a new track, a jump, or the PLAY that starts a track the room had paused on, from an app that waits at the barrier, with
    // someone else to wait for. The room's clock stays frozen at the start position until everyone (the host too) is ready.
    const others = Object.entries(r.users).filter(([id, u]) => id !== a.id && u.connected).map(([id]) => id);
    // Only when every other device holds too: an older app plays at once and the frozen clock would then pull it back.
    const everyoneHolds = others.every((id) => (this.socketOf(id)?.deserializeAttachment() || {}).syncStart === true);
    const startsTogether = syncHost && others.length > 0 && everyoneHolds && r.playing && (
      (action === "change_track" && r.track.id !== previousTrackId) || action === "seek" || (action === "play" && !wasPlaying && m.hostPlaying === true));
    let cancelled = null;
    if (startsTogether) {
      const waiting = Object.entries(r.users).filter(([, u]) => u.connected).map(([id]) => id);
      r.buffering = { waiting, until: now + BUFFER_TIMEOUT_MS, trackId: r.track ? r.track.id : "", all: waiting.slice(), sync: true };
      await this.ctx.storage.setAlarm(now + BUFFER_TIMEOUT_MS);
    } else if (holding && (action === "change_track" || action === "seek" || action === "pause")) {
      // The host moved on without a new synchronized start: that barrier is over.
      cancelled = r.buffering.trackId;
      r.buffering = null;
    }
    out.positionMs = Math.max(0, Math.round(this.position(now)));
    out.volume = r.volume;
    // A new track or a jump makes everyone fetch audio: wait for the slowest (or 10 s) before playing together.
    if (!startsTogether && (action === "change_track" || action === "seek") && r.playing) {
      const waiting = Object.entries(r.users).filter(([id, u]) => id !== a.id && u.connected).map(([id]) => id);
      if (waiting.length) {
        r.buffering = { waiting, until: now + BUFFER_TIMEOUT_MS, trackId: r.track ? r.track.id : "", all: waiting.slice() };
        await this.ctx.storage.setAlarm(now + BUFFER_TIMEOUT_MS);
        out.bufferWait = true;
      }
    }
    await this.save();
    // The barrier goes out BEFORE the command, so that a device that holds already knows it when the new track arrives.
    if (startsTogether) {
      this.broadcast({ t: "buffer_wait", trackId: r.buffering.trackId, waitingFor: r.buffering.waiting.slice(), syncStart: true, startPosition: Math.round(r.positionMs) });
    }
    // The PLAY that opened a barrier is not sent on: everyone plays when it closes.
    if (!(startsTogether && action === "play")) this.broadcast(out, a.id);
    if (out.bufferWait) this.broadcast({ t: "buffer_wait", trackId: r.buffering.trackId, waitingFor: r.buffering.waiting.slice() });
    // An old-style barrier that took its place already ended it (and a buffer_complete now would end that one too).
    if (cancelled !== null && !out.bufferWait) this.broadcast({ t: "buffer_complete", trackId: cancelled });
  }

  async maybeCompleteBuffer() {
    const r = this.room;
    if (!r || !r.buffering || r.buffering.waiting.length) return;
    const trackId = r.buffering.trackId;
    const sync = !!r.buffering.sync;
    r.buffering = null;
    r.revision = (r.revision || 0) + 1;
    const now = Date.now();
    if (sync) {
      // W2: the room's clock starts at the start position at one common moment, a little ahead so the play reaches everyone first.
      // The play goes out before buffer_complete: a device that holds learns the start time before it hears the barrier is over.
      const startAt = now + SYNC_START_LEAD_MS;
      r.anchorAt = startAt;
      await this.save();
      if (r.playing) {
        this.broadcast({ t: "sync_playback", action: "play", serverTime: now, capturedAt: startAt, startAt, revision: r.revision, positionMs: Math.round(r.positionMs), track: r.track });
      }
      this.broadcast({ t: "buffer_complete", trackId });
      return;
    }
    await this.save();
    this.broadcast({ t: "buffer_complete", trackId });
    // Everybody (the host too) starts from the same spot on the server's clock.
    if (r.playing) this.broadcast({ t: "sync_playback", action: "play", serverTime: now, revision: r.revision, positionMs: Math.round(this.position(now)), track: r.track });
  }

  async on_buffer_ready(ws, a, m) {
    if (!this.requireMember(ws, a) || !this.room.buffering) return;
    // A synchronized start waits for THIS track: a late answer about the previous one does not count.
    if (this.room.buffering.sync && m.trackId && String(m.trackId) !== this.room.buffering.trackId) return;
    this.room.buffering.waiting = this.room.buffering.waiting.filter((id) => id !== a.id);
    await this.maybeCompleteBuffer();
    await this.save();
  }

  async on_request_sync(ws, a) {
    if (!this.requireMember(ws, a)) return;
    send(ws, { t: "sync_state", state: this.publicState() });
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
    send(ws, { t: "reconnected", userId: id, state: this.publicState() });
    this.broadcast({ t: "user_reconnected", userId: id, username: u.name }, id);
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
    this.broadcast({ t: "user_disconnected", userId: a.id, username: u.name }, a.id);
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
    // Row 907: the TV sign-in relay lives under its own prefix; every room path below is untouched.
    const tvlink = await routeTvLink(req, env, url);
    if (tvlink) return tvlink;
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

// Aura Hi-Res «Iniciar sesión en la TV» (registry row 907): a one-shot relay between a TV and a phone of the same person.
// The TV draws a QR / short code that holds a random secret; the relay only ever sees `id` = a hash of that secret, never the
// secret itself, so it can neither read nor forge what goes through it. Both blobs are AES-GCM sealed on the devices:
//   hello   — sealed by the TV (which device is asking), shown on the phone before it agrees to send anything;
//   session — sealed by the phone (the Google session), handed to the TV exactly once and then forgotten.
// One Durable Object per id. Nothing outlives TTL_MS (2 minutes from the TV's hello); after a delivery only a tombstone stays,
// so the same id can never carry a second session.

const TVLINK_TTL_MS = 2 * 60_000;
const MAX_HELLO = 2 * 1024;
const MAX_SESSION = 32 * 1024;
const TV_MAX_FRAME = 4 * 1024;
const TV_ID_RE = /^[0-9a-f]{32}$/;
const TV_BLOB_RE = /^[A-Za-z0-9+/=_-]+$/;

const tvJson = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
const tvSend = (ws, type, payload = {}) => { try { ws.send(JSON.stringify({ type, payload })); } catch { /* closing */ } };

/** A well-formed relay id (32 lowercase hex), or "". */
function tvLinkId(raw) {
  const id = String(raw || "").toLowerCase();
  return TV_ID_RE.test(id) ? id : "";
}

/** A sealed blob as the devices send it: base64 text, 1..max characters. */
function validBlob(v, max) {
  return typeof v === "string" && v.length > 0 && v.length <= max && TV_BLOB_RE.test(v);
}

export class TvLink {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    const ttl = Number(env && env.TVLINK_TTL_MS);
    this.ttl = Number.isFinite(ttl) && ttl > 0 ? ttl : TVLINK_TTL_MS;
  }

  /** The live record, or null (none, or past its time: then everything is wiped first). */
  async record() {
    const r = (await this.ctx.storage.get("link")) || null;
    if (r && Date.now() >= r.expiresAt) { await this.wipe(); return null; }
    return r;
  }
  async wipe() {
    for (const s of this.ctx.getWebSockets()) { tvSend(s, "expired"); try { s.close(1000, "expired"); } catch { /* gone */ } }
    await this.ctx.storage.deleteAll();
  }
  liveSockets() {
    return this.ctx.getWebSockets().filter((s) => s.readyState === undefined || s.readyState === 1);
  }

  /** Hands the session to [ws] and leaves only the tombstone: it is never handed out again. */
  async deliver(ws, r) {
    tvSend(ws, "session", { session: r.session });
    await this.ctx.storage.put("link", { used: true, expiresAt: r.expiresAt });
    try { ws.close(1000, "delivered"); } catch { /* gone */ }
  }

  async fetch(req) {
    const url = new URL(req.url);
    const mode = url.searchParams.get("mode");
    if (mode === "wait") {
      if (req.headers.get("Upgrade") !== "websocket") return new Response("expected websocket", { status: 426 });
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);
      this.ctx.acceptWebSocket(server);
      return new Response(null, { status: 101, webSocket: client });
    }
    if (mode === "hello") {
      const r = await this.record();
      if (!r || r.used || !r.hello) return tvJson({ error: "no_such_link" }, 404);
      return tvJson({ hello: r.hello, expiresAt: r.expiresAt });
    }
    if (mode === "send") {
      const text = await req.text();
      if (text.length > MAX_SESSION + 256) return tvJson({ error: "too_big" }, 413);
      let body;
      try { body = JSON.parse(text); } catch { return tvJson({ error: "bad_json" }, 400); }
      const session = body && body.session;
      if (typeof session === "string" && session.length > MAX_SESSION) return tvJson({ error: "too_big" }, 413);
      if (!validBlob(session, MAX_SESSION)) return tvJson({ error: "bad_session" }, 400);
      const r = await this.record();
      if (r && (r.used || r.session)) return tvJson({ error: "already_used" }, 409);
      if (!r || !r.hello) return tvJson({ error: "no_such_link" }, 404);
      r.session = session;
      const tv = this.liveSockets()[0];
      if (tv) { await this.deliver(tv, r); return tvJson({ delivered: true }); }
      await this.ctx.storage.put("link", r);
      return tvJson({ delivered: false });
    }
    return tvJson({ error: "not found" }, 404);
  }

  async webSocketMessage(ws, data) {
    const text = typeof data === "string" ? data : new TextDecoder().decode(data);
    if (text.length > TV_MAX_FRAME) return tvSend(ws, "error", { code: "too_big" });
    let msg;
    try { msg = JSON.parse(text); } catch { return tvSend(ws, "error", { code: "bad_json" }); }
    if (!msg || msg.type !== "hello") return tvSend(ws, "error", { code: "unknown_message_type" });
    const hello = msg.payload && msg.payload.hello;
    if (!validBlob(hello, MAX_HELLO)) return tvSend(ws, "error", { code: "bad_hello" });
    let r = await this.record();
    if (r && r.used) { tvSend(ws, "error", { code: "already_used" }); try { ws.close(1000, "used"); } catch { /* gone */ } return; }
    if (r && r.session) return this.deliver(ws, r);
    if (!r) {
      r = { hello, expiresAt: Date.now() + this.ttl };
      await this.ctx.storage.put("link", r);
      await this.ctx.storage.setAlarm(r.expiresAt);
    }
    tvSend(ws, "waiting", { expiresAt: r.expiresAt });
  }
  async webSocketClose() { /* the record stays until it expires: the TV may reconnect with the same code */ }
  async webSocketError() {}

  async alarm() {
    const r = (await this.ctx.storage.get("link")) || null;
    if (!r) return;
    if (Date.now() >= r.expiresAt) await this.wipe();
    else await this.ctx.storage.setAlarm(r.expiresAt);
  }
}

/** Router for `/tvlink/*`; null for any other path, which the rooms router keeps handling exactly as before. */
async function routeTvLink(req, env, url) {
  if (!url.pathname.startsWith("/tvlink/")) return null;
  if (!env.TVLINK) return tvJson({ error: "not found" }, 404);
  const id = tvLinkId(url.searchParams.get("id"));
  if (!id) return tvJson({ error: "bad id" }, 400);
  const route = { "/tvlink/wait": ["wait", "GET"], "/tvlink/hello": ["hello", "GET"], "/tvlink/send": ["send", "POST"] }[url.pathname];
  if (!route) return tvJson({ error: "not found" }, 404);
  if (req.method !== route[1]) return tvJson({ error: "method" }, 405);
  const stub = env.TVLINK.get(env.TVLINK.idFromName(id));
  return stub.fetch(new Request(`https://tvlink/?mode=${route[0]}`, req));
}
