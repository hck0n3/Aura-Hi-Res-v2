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
 *   - «library changed» pings make the other devices refresh at once.
 * Nothing about the songs themselves is stored but their ids. Hibernating WebSockets: an idle room costs nothing.
 */

const MAX_MESSAGE_BYTES = 128 * 1024;
const MAX_NOW_BYTES = 24 * 1024;
const MAX_DEVICES = 8;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/health") return json({ ok: true });
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
    const now = (await this.ctx.storage.get("now")) || null;
    send(ws, { t: "hello_ok", serverTime: Date.now(), devices: this.devices(), now });
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
    const value = { ...state, deviceId: me.id, deviceName: me.name, at: Date.now() };
    await this.ctx.storage.put("now", value);
    this.broadcast({ t: "now", from: me.id, state: value }, ws);
  }

  command(ws, me, msg) {
    const target = String(msg.to || "");
    const cmd = String(msg.cmd || "").slice(0, 24);
    if (!target || !cmd) return;
    const dest = this.ctx.getWebSockets().find((s) => (s.deserializeAttachment() || {}).id === target);
    if (!dest) {
      send(ws, { t: "cmd_fail", to: target, cmd });
      return;
    }
    send(dest, { t: "cmd", from: me.id, fromName: me.name, cmd, args: msg.args && typeof msg.args === "object" ? msg.args : {} });
  }

  devices(exclude) {
    return this.ctx
      .getWebSockets()
      .filter((s) => s !== exclude)
      .map((s) => s.deserializeAttachment() || {})
      .filter((d) => d.id)
      .map((d) => ({ id: d.id, name: d.name, kind: d.kind, since: d.joinedAt }));
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

function send(ws, payload) {
  try {
    ws.send(JSON.stringify(payload));
  } catch {}
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
