// Aura Hi-Res «edge» Worker. Three small jobs, no accounts, no user identifiers:
//   1) GET  /v1/config           signed remote switches + one notice (the app verifies the signature; unsigned = ignored)
//      PUT  /v1/admin/config     (ADMIN_TOKEN) set them
//   2) cron                      watches YouTube's player and e-mails you once when it changes
//   8) POST /v1/diag             the user pressed «Enviar registro»: stores the log 7 days, returns a short case code
//      GET  /v1/diag/<code>      (ADMIN_TOKEN) read it
// Nothing here downloads or runs code on the phones: a flag only turns on or off code the app already has.

export const FLAGS = [
  "cola_inteligente",
  "portadas_animadas",
  "pedir_musica",
  "letras",
  "sync_dispositivos",
  "sync_biblioteca",
  "radar_lanzamientos",
  "copia_nube",
]; // closed list. Playback and the licence have NO flag on purpose.

const MAX_CONFIG_BYTES = 8 * 1024;
const MAX_DIAG_BYTES = 1024 * 1024;
const DIAG_TTL = 7 * 24 * 3600;
const DIAG_PER_IP_PER_HOUR = 6;
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

const json = (obj, status = 200, headers = {}) =>
  new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json", ...headers } });

const b64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)));
const unb64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

async function signingKey(env) {
  return crypto.subtle.importKey("pkcs8", unb64(env.SIGNING_KEY), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
}

/** Signs [payload] (a string) → base64 of the raw r||s signature (what WebCrypto produces; the app converts it). */
export async function sign(env, payload) {
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, await signingKey(env), new TextEncoder().encode(payload));
  return b64(sig);
}

/** Keeps only what the app understands: known flags (booleans), one short notice, a minimum version code. */
export function sanitizeConfig(input) {
  const out = { v: 1, issuedAt: Date.now(), flags: {}, notice: null, minVersion: 0 };
  const flags = input && typeof input.flags === "object" && input.flags ? input.flags : {};
  for (const f of FLAGS) if (typeof flags[f] === "boolean") out.flags[f] = flags[f];
  const n = input && input.notice;
  if (n && typeof n === "object" && n.id && n.texto) {
    out.notice = {
      id: String(n.id).slice(0, 40),
      titulo: String(n.titulo || "").slice(0, 80),
      texto: String(n.texto).slice(0, 400),
      hasta: Number.isFinite(Number(n.hasta)) ? Number(n.hasta) : 0,
    };
  }
  if (Number.isFinite(Number(input && input.minVersion))) out.minVersion = Math.max(0, Math.floor(Number(input.minVersion)));
  return out;
}

const isAdmin = (req, env) => {
  const given = (req.headers.get("authorization") || "").replace(/^Bearer\s+/i, "");
  return !!env.ADMIN_TOKEN && given.length > 0 && given === env.ADMIN_TOKEN;
};

async function sha256hex(text) {
  const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function readBody(req, max) {
  const len = Number(req.headers.get("content-length") || 0);
  if (len > max) return null;
  const text = await req.text();
  return text.length > max ? null : text;
}

function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return [...bytes].map((b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

async function handleConfig(req, env) {
  const stored = await env.EDGE.get("config");
  if (!stored) return json({ error: "no config" }, 404);
  const etag = '"' + (await sha256hex(stored)).slice(0, 16) + '"';
  if (req.headers.get("if-none-match") === etag) return new Response(null, { status: 304, headers: { etag } });
  return new Response(stored, { headers: { "content-type": "application/json", etag, "cache-control": "public, max-age=300" } });
}

async function handleAdminConfig(req, env) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const text = await readBody(req, MAX_CONFIG_BYTES);
  if (text == null) return json({ error: "too big" }, 413);
  let input;
  try {
    input = JSON.parse(text);
  } catch {
    return json({ error: "bad json" }, 400);
  }
  const payload = JSON.stringify(sanitizeConfig(input));
  await env.EDGE.put("config", JSON.stringify({ payload, sig: await sign(env, payload) }));
  return json({ ok: true, payload: JSON.parse(payload) });
}

async function handleDiagPost(req, env) {
  const ip = req.headers.get("cf-connecting-ip") || "unknown";
  const hour = Math.floor(Date.now() / 3_600_000);
  const rateKey = "rate:diag:" + (await sha256hex(ip)).slice(0, 16) + ":" + hour;
  const used = Number((await env.EDGE.get(rateKey)) || 0);
  if (used >= DIAG_PER_IP_PER_HOUR) return json({ error: "slow down" }, 429);
  const text = await readBody(req, MAX_DIAG_BYTES);
  if (text == null) return json({ error: "too big" }, 413);
  if (text.length < 10) return json({ error: "empty" }, 400);
  await env.EDGE.put(rateKey, String(used + 1), { expirationTtl: 3700 });
  const code = newCode();
  await env.EDGE.put("diag:" + code, text, { expirationTtl: DIAG_TTL });
  return json({ code, days: 7 });
}

async function handleDiagGet(req, env, code) {
  if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
  const text = await env.EDGE.get("diag:" + code.toUpperCase());
  if (text == null) return json({ error: "not found" }, 404);
  return new Response(text, { headers: { "content-type": "text/plain; charset=utf-8" } });
}

// ── 2) YouTube watcher ───────────────────────────────────────────────────────────────────────────────────────────────────────

/** The player id YouTube serves right now (the 8 hex characters in the base.js path), or null when the page is not as expected. */
export function extractPlayerId(html) {
  const m = /\/s\/player\/([0-9a-f]{8})\//.exec(html || "");
  return m ? m[1] : null;
}

/**
 * One check. Returns "unchanged" | "first" | "changed" | "unreachable". [notify] is called once per change (never once per run).
 * The previous id is stored only after the alert went out, so a failed e-mail is retried by the next run instead of lost.
 */
export async function watchYoutube(env, doFetch = fetch, notify = sendAlert) {
  let html;
  try {
    const res = await doFetch("https://www.youtube.com/iframe_api", { headers: { "user-agent": "Mozilla/5.0" } });
    if (!res.ok) return "unreachable";
    html = await res.text();
  } catch {
    return "unreachable";
  }
  const id = extractPlayerId(html);
  if (!id) return "unreachable";
  const prev = await env.EDGE.get("yt:player");
  if (prev === id) return "unchanged";
  if (prev) {
    const ok = await notify(env, "YouTube cambió su reproductor", `Antes: ${prev}\nAhora: ${id}\n\nRevisa que sig/n sigan descifrando y, si no, verifica el base.js nuevo antes de publicar player_configs.json.`);
    if (!ok) return "changed";
  }
  await env.EDGE.put("yt:player", id);
  return prev ? "changed" : "first";
}

async function sendAlert(env, subject, text) {
  if (!env.ALERT || !env.ALERT_FROM) return false; // not configured: the change stays pending and is retried
  try {
    const { EmailMessage } = await import("cloudflare:email");
    const to = env.ALERT_TO || "aurahires@gmail.com";
    const raw = `From: Aura watcher <${env.ALERT_FROM}>\r\nTo: ${to}\r\nSubject: ${subject}\r\nMessage-ID: <${crypto.randomUUID()}@aura>\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n${text}\r\n`;
    await env.ALERT.send(new EmailMessage(env.ALERT_FROM, to, raw));
    return true;
  } catch {
    return false;
  }
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const p = url.pathname;
    try {
      if (req.method === "GET" && p === "/health") return json({ ok: true, edge: 1 });
      if (req.method === "GET" && p === "/v1/config") return await handleConfig(req, env);
      if (req.method === "PUT" && p === "/v1/admin/config") return await handleAdminConfig(req, env);
      if (req.method === "POST" && p === "/v1/diag") return await handleDiagPost(req, env);
      const m = /^\/v1\/diag\/([A-Za-z0-9]{8})$/.exec(p);
      if (req.method === "GET" && m) return await handleDiagGet(req, env, m[1]);
      return json({ error: "not found" }, 404);
    } catch {
      return json({ error: "server" }, 500);
    }
  },
  async scheduled(_event, env, ctx) {
    ctx.waitUntil(watchYoutube(env));
  },
};
