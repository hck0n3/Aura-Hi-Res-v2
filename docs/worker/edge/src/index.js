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

/** The public half of SIGNING_KEY as SPKI base64 — what the app embeds. Public by nature; derived so the owner never has to keep a copy. */
export async function publicKeySpki(env) {
  const priv = await crypto.subtle.importKey("pkcs8", unb64(env.SIGNING_KEY), { name: "ECDSA", namedCurve: "P-256" }, true, ["sign"]);
  const jwk = await crypto.subtle.exportKey("jwk", priv);
  const header = Uint8Array.from([0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00, 0x04]);
  const fromB64Url = (v) => unb64(v.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - (v.length % 4)) % 4));
  const out = new Uint8Array(header.length + 64);
  out.set(header, 0);
  out.set(fromB64Url(jwk.x), header.length);
  out.set(fromB64Url(jwk.y), header.length + 32);
  return b64(out);
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

/**
 * Sends the watcher's alert. Two ways, first that is configured wins:
 *  - Telegram (`TELEGRAM_TOKEN` + `TELEGRAM_CHAT` secrets): a bot the owner creates with @BotFather; no domain, no shared rate limit.
 *  - Cloudflare Email Routing (`ALERT` binding + `ALERT_FROM`): needs a domain on the account.
 *  - ntfy.sh (`NTFY_TOPIC` secret): no domain needed; ntfy forwards the message to [ALERT_TO] by e-mail. The topic is the secret, so
 *    make it long and random. The message carries no user data (only the old and new player ids of YouTube).
 */
export async function sendAlert(env, subject, text, doFetch = fetch, info = {}) {
  const to = env.ALERT_TO || "aurahires@gmail.com";
  // Secrets put through a PowerShell pipe keep a trailing line break: trim every one that goes into a URL or header.
  const token = String(env.TELEGRAM_TOKEN || "").trim();
  const chat = String(env.TELEGRAM_CHAT || "").trim();
  if (token && chat) {
    try {
      const res = await doFetch(`https://api.telegram.org/bot${token}/sendMessage`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ chat_id: chat, text: `${subject}\n\n${text}` }),
      });
      info.telegramStatus = res.status;
      if (!res.ok) {
        // Never the secret itself: only its shape and Telegram's own reason.
        const raw = String(env.TELEGRAM_TOKEN || "");
        info.tokenShape = { length: raw.length, trimmedLength: token.length, colonAt: token.indexOf(":"), nonPrintable: [...raw].filter((c) => c.charCodeAt(0) < 32 || c.charCodeAt(0) > 126).length };
        info.chatShape = { length: chat.length, digitsOnly: /^-?\d+$/.test(chat) };
        try { info.telegramReason = String((await res.json()).description || "").slice(0, 80); } catch { /* no body */ }
      }
      return res.ok;
    } catch {
      info.telegramStatus = "network-error";
      return false;
    }
  }
  if (env.ALERT && env.ALERT_FROM) {
    try {
      const { EmailMessage } = await import("cloudflare:email");
      const raw = `From: Aura watcher <${env.ALERT_FROM}>\r\nTo: ${to}\r\nSubject: ${subject}\r\nMessage-ID: <${crypto.randomUUID()}@aura>\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n${text}\r\n`;
      await env.ALERT.send(new EmailMessage(env.ALERT_FROM, to, raw));
      return true;
    } catch {
      return false;
    }
  }
  const topic = String(env.NTFY_TOPIC || "").trim();
  if (topic) {
    try {
      const ascii = (v) => v.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^\x20-\x7e]/g, "");
      const res = await doFetch("https://ntfy.sh/" + encodeURIComponent(topic), {
        method: "POST",
        headers: { Title: ascii(subject), Email: to, Priority: "high", Tags: "warning" },
        body: text,
      });
      info.ntfyStatus = res.status;
      return res.ok;
    } catch {
      info.ntfyStatus = "network-error";
      return false;
    }
  }
  return false; // not configured: the change stays pending and is retried
}

// ── 4) Last.fm style tags, shared ────────────────────────────────────────────────────────────────────────────────────────────

const TAGS_TTL = 30 * 24 * 3600;
const TAGS_EMPTY_TTL = 7 * 24 * 3600;
const TAGS_MISSES_PER_HOUR = 200; // a global ceiling on what the Worker will ask Last.fm (and write to KV) per hour

/** The same normalisation the app uses for its own artist keys: lower case, single spaces, bounded. */
export function normalizeArtist(name) {
  return String(name || "").toLowerCase().replace(/\s+/g, " ").trim().slice(0, 80);
}

/**
 * Tags of one artist: from KV when known; otherwise ONE Last.fm lookup (the key lives in the Worker, never in a response) that is
 * stored for everyone. Only the artist name travels — no user, no session. Returns {status, body}:
 * 200 {tags:[[name,count],…]} (an empty list is a definitive «Last.fm has none»), 502 when Last.fm failed (nothing is stored),
 * 429 when the hourly budget of misses is spent, 400 without a usable name, 503 when the Worker has no Last.fm key.
 */
export async function artistTags(env, artist, doFetch = fetch) {
  const key = normalizeArtist(artist);
  if (!key) return { status: 400, body: { error: "artist" } };
  const cached = await env.EDGE.get("tags:" + key);
  if (cached != null) return { status: 200, body: JSON.parse(cached) };
  if (!env.LASTFM_KEY) return { status: 503, body: { error: "no key" } };
  const hour = Math.floor(Date.now() / 3_600_000);
  const budgetKey = "rate:tags:" + hour;
  const used = Number((await env.EDGE.get(budgetKey)) || 0);
  if (used >= TAGS_MISSES_PER_HOUR) return { status: 429, body: { error: "slow down" } };
  await env.EDGE.put(budgetKey, String(used + 1), { expirationTtl: 3700 });
  let root;
  try {
    const url = "https://ws.audioscrobbler.com/2.0/?method=artist.getTopTags&autocorrect=1&format=json&artist=" + encodeURIComponent(key) + "&api_key=" + encodeURIComponent(String(env.LASTFM_KEY).trim());
    const res = await doFetch(url, { headers: { "user-agent": "AuraHiRes (https://github.com/hck0n3)" } });
    if (res.status >= 500) return { status: 502, body: { error: "upstream" } };
    root = await res.json();
  } catch {
    return { status: 502, body: { error: "upstream" } };
  }
  if (root && root.error != null && Number(root.error) !== 6) return { status: 502, body: { error: "upstream" } };
  const raw = root && root.toptags && root.toptags.tag;
  const list = Array.isArray(raw) ? raw : raw ? [raw] : [];
  const tags = list
    .map((t) => [String((t && t.name) || "").trim().slice(0, 40), Number(t && t.count) || 0])
    .filter((t) => t[0])
    .slice(0, 30);
  const body = { tags };
  await env.EDGE.put("tags:" + key, JSON.stringify(body), { expirationTtl: tags.length ? TAGS_TTL : TAGS_EMPTY_TTL });
  return { status: 200, body };
}

// ── 3) Shared animated covers ────────────────────────────────────────────────────────────────────────────────────────────────

const CANVAS_TTL = 7 * 24 * 3600;
const CANVAS_WRITES_PER_HOUR = 300;
const CANVAS_MAX_BODY = 4 * 1024;
// Where the app's providers serve motion covers from. A URL anywhere else is not shared (the app still uses it for itself).
const CANVAS_HOSTS = [".mzstatic.com", ".apple.com", ".tidal.com", ".monochrome.tf", ".boidu.dev"];

const normLetters = (v) => String(v || "").toLowerCase().replace(/[^\p{L}\p{N}]/gu, "");

/** Same key the app builds (CanvasAlbumMemo.key): `al|artist|album` or `so|artist|song`, letters and digits only. */
export function validCanvasKey(k) {
  return typeof k === "string" && k.length <= 160 && /^(al|so)\|[^|]+\|[^|]+$/u.test(k);
}

function safeAnimationUrl(u) {
  try {
    const url = new URL(String(u));
    if (url.protocol !== "https:" || String(u).length > 400) return false;
    return CANVAS_HOSTS.some((h) => url.hostname === h.slice(1) || url.hostname.endsWith(h));
  } catch {
    return false;
  }
}

/**
 * What a client may share: https on a known host, bounded, and the artwork must actually be about the key it is filed under
 * (its artist equals the key's artist and its album / title equals the key's) so one client cannot put someone else's cover under
 * an album. Returns the sanitized artwork or null.
 */
export function sanitizeCanvas(k, art) {
  if (!validCanvasKey(k) || !art || typeof art !== "object") return null;
  const [, keyArtist, keyThing] = k.split("|");
  if (normLetters(art.artist) !== keyArtist) return null;
  const subject = k.startsWith("al|") ? art.albumName || art.name : art.name;
  if (normLetters(subject) !== keyThing) return null;
  const out = {};
  for (const f of ["name", "artist", "albumId", "albumName"]) if (typeof art[f] === "string") out[f] = art[f].slice(0, 120);
  for (const f of ["static", "animated", "videoUrl"]) {
    if (art[f] == null) continue;
    if (!safeAnimationUrl(art[f])) return null;
    out[f] = String(art[f]);
  }
  if (!out.animated && !out.videoUrl) return null;
  return out;
}

export async function canvasGet(env, k) {
  if (!validCanvasKey(k)) return { status: 400, body: { error: "key" } };
  const v = await env.EDGE.get("canvas:" + k);
  return v == null ? { status: 404, body: { miss: true } } : { status: 200, body: { artwork: JSON.parse(v) } };
}

export async function canvasPut(env, k, art) {
  const clean = sanitizeCanvas(k, art);
  if (!clean) return { status: 400, body: { error: "rejected" } };
  if ((await env.EDGE.get("canvas:" + k)) != null) return { status: 200, body: { stored: false } }; // first answer wins until it expires
  const hour = Math.floor(Date.now() / 3_600_000);
  const budgetKey = "rate:canvas:" + hour;
  const used = Number((await env.EDGE.get(budgetKey)) || 0);
  if (used >= CANVAS_WRITES_PER_HOUR) return { status: 429, body: { error: "slow down" } };
  await env.EDGE.put(budgetKey, String(used + 1), { expirationTtl: 3700 });
  await env.EDGE.put("canvas:" + k, JSON.stringify(clean), { expirationTtl: CANVAS_TTL });
  return { status: 200, body: { stored: true } };
}

// ── 3b) Shared answers of "Pedir música" ─────────────────────────────────────────────────────────────────────────────────────

const ASK_TTL = 3 * 24 * 3600;
const ASK_WRITES_PER_HOUR = 200;
const ASK_MAX_BODY = 16 * 1024;
const ASK_MAX_TRACKS = 60;

/** The app files an answer under the SHA-256 of the normalized request: the Worker never sees what was typed. */
export function validAskKey(k) {
  return typeof k === "string" && /^[0-9a-f]{64}$/.test(k);
}

/** Only plain {title, artist} pairs, bounded; anything else (or fewer than 3 tracks) is not worth sharing. */
export function sanitizeAsk(tracks) {
  if (!Array.isArray(tracks)) return null;
  const out = [];
  for (const t of tracks.slice(0, ASK_MAX_TRACKS)) {
    if (!t || typeof t.title !== "string" || typeof t.artist !== "string") continue;
    const title = t.title.trim().slice(0, 120);
    const artist = t.artist.trim().slice(0, 120);
    if (title && artist) out.push({ title, artist });
  }
  return out.length >= 3 ? out : null;
}

export async function askGet(env, k) {
  if (!validAskKey(k)) return { status: 400, body: { error: "key" } };
  const v = await env.EDGE.get("ask:" + k);
  return v == null ? { status: 404, body: { miss: true } } : { status: 200, body: { tracks: JSON.parse(v) } };
}

export async function askPut(env, k, tracks) {
  const clean = validAskKey(k) ? sanitizeAsk(tracks) : null;
  if (!clean) return { status: 400, body: { error: "rejected" } };
  if ((await env.EDGE.get("ask:" + k)) != null) return { status: 200, body: { stored: false } }; // first answer wins until it expires
  const hour = Math.floor(Date.now() / 3_600_000);
  const budgetKey = "rate:ask:" + hour;
  const used = Number((await env.EDGE.get(budgetKey)) || 0);
  if (used >= ASK_WRITES_PER_HOUR) return { status: 429, body: { error: "slow down" } };
  await env.EDGE.put(budgetKey, String(used + 1), { expirationTtl: 3700 });
  await env.EDGE.put("ask:" + k, JSON.stringify(clean), { expirationTtl: ASK_TTL });
  return { status: 200, body: { stored: true } };
}

// ── 5) Encrypted cloud backup ────────────────────────────────────────────────────────────────────────────────────────────────

const BACKUP_MAX = 24 * 1024 * 1024; // under KV's 25 MiB value limit, so the same copy fits either store
const BACKUP_COPIES = 3;
const BACKUP_WRITES_PER_HOUR = 60;
const BACKUP_MAGIC = "AURAB1"; // the app's own header; the Worker never reads past it (everything after is ciphertext)
const BACKUP_ID = /^[0-9a-f]{64}$/;

// R2 when the account has it bound as BACKUPS, KV otherwise: same calls, nothing changes in the app.
async function bkPut(env, name, bytes) {
  if (env.BACKUPS) await env.BACKUPS.put(name, bytes);
  else await env.EDGE.put(name, bytes);
}
async function bkGet(env, name) {
  if (env.BACKUPS) {
    const o = await env.BACKUPS.get(name);
    return o ? await o.arrayBuffer() : null;
  }
  return await env.EDGE.get(name, "arrayBuffer");
}
async function bkDelete(env, name) {
  if (env.BACKUPS) await env.BACKUPS.delete(name);
  else await env.EDGE.delete(name);
}

async function bkIndex(env, id) {
  const raw = await env.EDGE.get("bki:" + id);
  try { return raw ? JSON.parse(raw) : []; } catch { return []; }
}

/** Stores one encrypted copy for [id] (newest first, at most BACKUP_COPIES; older ones are deleted for good). */
export async function backupPut(env, id, bytes) {
  if (!BACKUP_ID.test(String(id))) return { status: 400, body: { error: "id" } };
  if (!bytes || bytes.byteLength < 40) return { status: 400, body: { error: "empty" } };
  if (bytes.byteLength > BACKUP_MAX) return { status: 413, body: { error: "too big" } };
  const head = new TextDecoder().decode(new Uint8Array(bytes.slice(0, BACKUP_MAGIC.length)));
  if (head !== BACKUP_MAGIC) return { status: 400, body: { error: "not an Aura backup" } };
  const hour = Math.floor(Date.now() / 3_600_000);
  const budgetKey = "rate:backup:" + hour;
  const used = Number((await env.EDGE.get(budgetKey)) || 0);
  if (used >= BACKUP_WRITES_PER_HOUR) return { status: 429, body: { error: "slow down" } };
  await env.EDGE.put(budgetKey, String(used + 1), { expirationTtl: 3700 });
  const index = await bkIndex(env, id);
  const n = (index.length ? Math.max(...index.map((e) => e.n)) : 0) + 1;
  await bkPut(env, `bk:${id}:${n}`, bytes);
  index.unshift({ n, size: bytes.byteLength, at: Date.now() });
  const dropped = index.splice(BACKUP_COPIES);
  await env.EDGE.put("bki:" + id, JSON.stringify(index));
  for (const old of dropped) await bkDelete(env, `bk:${id}:${old.n}`);
  return { status: 200, body: { stored: true, copies: index.length, n } };
}

export async function backupList(env, id) {
  if (!BACKUP_ID.test(String(id))) return { status: 400, body: { error: "id" } };
  return { status: 200, body: { copies: await bkIndex(env, id) } };
}

/** The newest copy, or copy [n]. Returns {status, bytes?, body?}. */
export async function backupGet(env, id, n) {
  if (!BACKUP_ID.test(String(id))) return { status: 400, body: { error: "id" } };
  const index = await bkIndex(env, id);
  const entry = n ? index.find((e) => e.n === Number(n)) : index[0];
  if (!entry) return { status: 404, body: { error: "no backup" } };
  const bytes = await bkGet(env, `bk:${id}:${entry.n}`);
  return bytes ? { status: 200, bytes } : { status: 404, body: { error: "no backup" } };
}

export async function backupDelete(env, id) {
  if (!BACKUP_ID.test(String(id))) return { status: 400, body: { error: "id" } };
  for (const e of await bkIndex(env, id)) await bkDelete(env, `bk:${id}:${e.n}`);
  await env.EDGE.delete("bki:" + id);
  return { status: 200, body: { deleted: true } };
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    const p = url.pathname;
    try {
      if (req.method === "GET" && p === "/health") return json({ ok: true, edge: 1 });
      if (req.method === "GET" && p === "/v1/config") return await handleConfig(req, env);
      if (req.method === "GET" && p === "/v1/tags") {
        const r = await artistTags(env, url.searchParams.get("artist"));
        return json(r.body, r.status, r.status === 200 ? { "cache-control": "public, max-age=86400" } : {});
      }
      if (req.method === "GET" && p === "/v1/canvas") {
        const r = await canvasGet(env, url.searchParams.get("k"));
        return json(r.body, r.status, r.status === 200 ? { "cache-control": "public, max-age=3600" } : {});
      }
      if (req.method === "PUT" && p === "/v1/canvas") {
        const text = await readBody(req, CANVAS_MAX_BODY);
        if (text == null) return json({ error: "too big" }, 413);
        let input;
        try { input = JSON.parse(text); } catch { return json({ error: "bad json" }, 400); }
        const r = await canvasPut(env, input && input.k, input && input.artwork);
        return json(r.body, r.status);
      }
      if (req.method === "GET" && p === "/v1/ask") {
        const r = await askGet(env, url.searchParams.get("k"));
        return json(r.body, r.status, r.status === 200 ? { "cache-control": "public, max-age=600" } : {});
      }
      if (req.method === "PUT" && p === "/v1/ask") {
        const text = await readBody(req, ASK_MAX_BODY);
        if (text == null) return json({ error: "too big" }, 413);
        let input;
        try { input = JSON.parse(text); } catch { return json({ error: "bad json" }, 400); }
        const r = await askPut(env, input && input.k, input && input.tracks);
        return json(r.body, r.status);
      }
      const bk = /^\/v1\/backup\/([0-9a-f]{64})(\/list)?$/.exec(p);
      if (bk) {
        const id = bk[1];
        if (req.method === "PUT" && !bk[2]) {
          const len = Number(req.headers.get("content-length") || 0);
          if (len > BACKUP_MAX) return json({ error: "too big" }, 413);
          const r = await backupPut(env, id, await req.arrayBuffer());
          return json(r.body, r.status);
        }
        if (req.method === "GET" && bk[2]) {
          const r = await backupList(env, id);
          return json(r.body, r.status);
        }
        if (req.method === "GET") {
          const r = await backupGet(env, id, url.searchParams.get("n"));
          return r.bytes ? new Response(r.bytes, { headers: { "content-type": "application/octet-stream" } }) : json(r.body, r.status);
        }
        if (req.method === "DELETE") {
          const r = await backupDelete(env, id);
          return json(r.body, r.status);
        }
      }
      if (req.method === "GET" && p === "/v1/pubkey") return json({ publicKey: await publicKeySpki(env) });
      if (req.method === "POST" && p === "/v1/admin/test-alert") {
        if (!isAdmin(req, env)) return json({ error: "forbidden" }, 403);
        const info = {};
        const sent = await sendAlert(env, "Aviso de prueba de Aura", "Si lees esto, los avisos del vigilante de YouTube llegan.", fetch, info);
        return json({ sent, telegramSet: !!(env.TELEGRAM_TOKEN && env.TELEGRAM_CHAT), ntfyTopicSet: !!env.NTFY_TOPIC, emailBindingSet: !!(env.ALERT && env.ALERT_FROM), ...info });
      }
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
